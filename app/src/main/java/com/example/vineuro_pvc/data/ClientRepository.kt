package com.example.vineuro_pvc.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Repositorio híbrido de clientes (SharedPreferences + Supabase). Los clientes se identifican por su id.
 *
 * Para trabajar sin Internet se guardan dos listas de pendientes:
 *  - pendientes de subir: clientes guardados que aún no llegaron a la nube
 *  - pendientes de eliminar: clientes borrados que aún no se borraron en la nube
 */
class ClientRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("pref_clientes", Context.MODE_PRIVATE)
    private val supabaseService = SupabaseService()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ---------- Local ----------

    fun obtenerClientes(): List<Cliente> {
        val jsonString = prefs.getString(KEY_LISTA, null) ?: return emptyList()
        val clientesList = mutableListOf<Cliente>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id")
                if (id.isBlank()) continue
                clientesList.add(
                    Cliente(
                        id = id,
                        nombres = obj.optString("nombres"),
                        apellidos = obj.optString("apellidos"),
                        telefono = obj.optString("telefono"),
                        email = obj.optString("email"),
                        direccion = obj.optString("direccion"),
                        nota = obj.optString("nota"),
                        fechaRegistro = obj.optLong("fechaRegistro", System.currentTimeMillis()),
                    ),
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return clientesList
    }

    fun obtenerClientePorId(id: String): Cliente? = obtenerClientes().find { it.id == id }

    fun buscarClientes(query: String): List<Cliente> {
        val todos = obtenerClientes()
        if (query.isBlank()) return todos
        val q = query.trim().lowercase()
        return todos.filter { c ->
            c.nombreCompleto.lowercase().contains(q) ||
                c.telefono.lowercase().contains(q) ||
                c.email.lowercase().contains(q) ||
                c.direccion.lowercase().contains(q)
        }
    }

    // ---------- Local + Nube ----------

    /** Guarda (o actualiza) en el teléfono y lo sube a Supabase en segundo plano. */
    fun guardarCliente(cliente: Cliente): Boolean {
        val lista = obtenerClientes().toMutableList()
        val index = lista.indexOfFirst { it.id == cliente.id }
        if (index >= 0) lista[index] = cliente else lista.add(0, cliente)
        val localOk = guardarLista(lista)

        marcar(KEY_PEND_SUBIR, cliente.id, true)
        scope.launch {
            if (supabaseService.upsertCliente(cliente)) marcar(KEY_PEND_SUBIR, cliente.id, false)
        }
        return localOk
    }

    /** Elimina del teléfono al instante y de Supabase (si no hay Internet, se reintenta al sincronizar). */
    suspend fun eliminarCliente(id: String): Boolean {
        guardarLista(obtenerClientes().filterNot { it.id == id })
        marcar(KEY_PEND_SUBIR, id, false)
        marcar(KEY_PEND_ELIMINAR, id, true)
        val ok = supabaseService.deleteCliente(id)
        if (ok) marcar(KEY_PEND_ELIMINAR, id, false)
        return ok
    }

    /**
     * 1) Reintenta borrados y subidas pendientes.
     * 2) Descarga la lista de la nube y la toma como referencia (más los pendientes de subir).
     * Si no hay conexión, devuelve la lista local.
     */
    suspend fun sincronizarConSupabase(): List<Cliente> {
        // 1. Pendientes
        leer(KEY_PEND_ELIMINAR).forEach { id ->
            if (supabaseService.deleteCliente(id)) marcar(KEY_PEND_ELIMINAR, id, false)
        }
        val locales = obtenerClientes()
        leer(KEY_PEND_SUBIR).forEach { id ->
            val c = locales.find { it.id == id }
            if (c == null || supabaseService.upsertCliente(c)) marcar(KEY_PEND_SUBIR, id, false)
        }

        // 2. Descargar
        val nube = supabaseService.fetchClientes() ?: return obtenerClientes()
        val eliminados = leer(KEY_PEND_ELIMINAR)
        val pendientes = leer(KEY_PEND_SUBIR)

        val resultado = LinkedHashMap<String, Cliente>()
        nube.filter { it.id !in eliminados }.forEach { resultado[it.id] = it }
        // Lo que aún no se pudo subir se conserva tal cual está en el teléfono
        obtenerClientes().filter { it.id in pendientes }.forEach { resultado[it.id] = it }

        val lista = resultado.values.sortedByDescending { it.fechaRegistro }
        guardarLista(lista)
        return lista
    }

    // ---------- Ayudantes ----------

    private fun guardarLista(lista: List<Cliente>): Boolean {
        return try {
            val jsonArray = JSONArray()
            for (cliente in lista) {
                jsonArray.put(
                    JSONObject().apply {
                        put("id", cliente.id)
                        put("nombres", cliente.nombres)
                        put("apellidos", cliente.apellidos)
                        put("telefono", cliente.telefono)
                        put("email", cliente.email)
                        put("direccion", cliente.direccion)
                        put("nota", cliente.nota)
                        put("fechaRegistro", cliente.fechaRegistro)
                    },
                )
            }
            prefs.edit { putString(KEY_LISTA, jsonArray.toString()) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun leer(key: String): Set<String> = prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()

    @Synchronized
    private fun marcar(key: String, id: String, agregar: Boolean) {
        val set = leer(key).toMutableSet()
        if (agregar) set.add(id) else set.remove(id)
        prefs.edit { putStringSet(key, set) }
    }

    companion object {
        private const val KEY_LISTA = "lista_clientes"
        private const val KEY_PEND_SUBIR = "pendientes_subir"
        private const val KEY_PEND_ELIMINAR = "pendientes_eliminar"
    }
}
