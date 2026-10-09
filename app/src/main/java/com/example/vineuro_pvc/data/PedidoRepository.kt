package com.example.vineuro_pvc.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray

/**
 * Repositorio híbrido de pedidos: copia local (SharedPreferences) + tabla "pedidos" en Supabase.
 * La copia local permite trabajar sin Internet; la nube es la fuente compartida.
 */
class PedidoRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("pref_pedidos", Context.MODE_PRIVATE)
    private val supabaseService = SupabaseService()

    // ---------- Local ----------

    fun obtenerPedidos(): List<Pedido> {
        val json = prefs.getString(KEY_LISTA, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { PedidoJson.pedidoFromJson(arr.getJSONObject(it)) }
                .sortedByDescending { it.fechaActualizacion }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun obtenerPedido(id: String): Pedido? = obtenerPedidos().find { it.id == id }

    fun obtenerUltimoPedido(): Pedido? = obtenerPedidos().maxByOrNull { it.fechaActualizacion }

    fun buscarPedidos(query: String): List<Pedido> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return obtenerPedidos()
        return obtenerPedidos().filter {
            it.codigo.lowercase().contains(q) ||
                it.clienteNombre.lowercase().contains(q) ||
                it.clienteDni.lowercase().contains(q)
        }
    }

    private fun guardarLocal(pedido: Pedido) {
        val lista = obtenerPedidos().toMutableList()
        val idx = lista.indexOfFirst { it.id == pedido.id }
        if (idx >= 0) lista[idx] = pedido else lista.add(0, pedido)
        guardarLista(lista)
    }

    private fun guardarLista(lista: List<Pedido>) {
        val arr = JSONArray()
        lista.forEach { arr.put(PedidoJson.pedidoToJson(it)) }
        prefs.edit { putString(KEY_LISTA, arr.toString()) }
    }

    // ---------- Local + Nube ----------

    /**
     * Guarda en el teléfono y luego en Supabase.
     * @return null si se guardó en la nube; si falló, el mensaje de error (el pedido igual queda guardado localmente).
     */
    suspend fun guardarPedido(pedido: Pedido): String? {
        guardarLocal(pedido)
        return supabaseService.upsertPedido(pedido)
    }

    /** Elimina local y en la nube. Devuelve null si se eliminó correctamente, o el mensaje de error de la nube. */
    suspend fun eliminarPedido(id: String): String? {
        guardarLista(obtenerPedidos().filterNot { it.id == id })
        return supabaseService.deletePedido(id)
    }

    /**
     * Descarga los pedidos de Supabase y los fusiona con los locales (gana el más reciente).
     * Los pedidos que solo existen en el teléfono se suben a la nube.
     */
    suspend fun sincronizar(): List<Pedido> {
        val nube = supabaseService.fetchPedidos() ?: return obtenerPedidos()
        val locales = obtenerPedidos()
        val mapa = LinkedHashMap<String, Pedido>()
        nube.forEach { mapa[it.id] = it }

        for (local in locales) {
            val remoto = mapa[local.id]
            if (remoto == null || local.fechaActualizacion > remoto.fechaActualizacion) {
                mapa[local.id] = local
                supabaseService.upsertPedido(local) // subir lo pendiente
            }
        }
        val fusion = mapa.values.sortedByDescending { it.fechaActualizacion }
        guardarLista(fusion)
        return fusion
    }

    companion object {
        private const val KEY_LISTA = "lista_pedidos"
    }
}
