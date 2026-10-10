package com.example.vineuro_pvc.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Repositorio de clientes: base de datos local SQLite (tabla "clientes") + Supabase.
 * Los clientes se identifican por su id.
 *
 * Sin Internet, las operaciones con la nube quedan registradas en la tabla "pendientes_sync"
 * y se reintentan al sincronizar.
 */
class ClientRepository(context: Context) {

    private val db = VineuroDatabase.get(context)
    private val supabaseService = SupabaseService()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ---------- Local (SQLite) ----------

    fun obtenerClientes(): List<Cliente> = consultar(null, null)

    fun obtenerClientePorId(id: String): Cliente? = consultar("id = ?", arrayOf(id)).firstOrNull()

    fun buscarClientes(query: String): List<Cliente> {
        if (query.isBlank()) return obtenerClientes()
        val q = "%${query.trim()}%"
        return consultar(
            "(nombres || ' ' || apellidos) LIKE ? OR telefono LIKE ? OR email LIKE ? OR direccion LIKE ?",
            arrayOf(q, q, q, q),
        )
    }

    private fun consultar(where: String?, args: Array<String>?): List<Cliente> {
        val lista = mutableListOf<Cliente>()
        db.readableDatabase.query(
            VineuroDatabase.T_CLIENTES, null, where, args, null, null, "fecha_registro DESC",
        ).use { c -> while (c.moveToNext()) lista.add(leer(c)) }
        return lista
    }

    private fun leer(c: Cursor) = Cliente(
        id = c.getString(c.getColumnIndexOrThrow("id")),
        nombres = c.getString(c.getColumnIndexOrThrow("nombres")),
        apellidos = c.getString(c.getColumnIndexOrThrow("apellidos")),
        telefono = c.getString(c.getColumnIndexOrThrow("telefono")),
        email = c.getString(c.getColumnIndexOrThrow("email")),
        direccion = c.getString(c.getColumnIndexOrThrow("direccion")),
        nota = c.getString(c.getColumnIndexOrThrow("nota")),
        fechaRegistro = c.getLong(c.getColumnIndexOrThrow("fecha_registro")),
    )

    private fun valores(cliente: Cliente) = ContentValues().apply {
        put("id", cliente.id)
        put("nombres", cliente.nombres)
        put("apellidos", cliente.apellidos)
        put("telefono", cliente.telefono)
        put("email", cliente.email)
        put("direccion", cliente.direccion)
        put("nota", cliente.nota)
        put("fecha_registro", cliente.fechaRegistro)
    }

    private fun guardarLocal(cliente: Cliente): Boolean =
        db.writableDatabase.insertWithOnConflict(
            VineuroDatabase.T_CLIENTES, null, valores(cliente), SQLiteDatabase.CONFLICT_REPLACE,
        ) != -1L

    // ---------- Local + Nube ----------

    /** Guarda (o actualiza) en SQLite y lo sube a Supabase en segundo plano. */
    fun guardarCliente(cliente: Cliente): Boolean {
        val localOk = guardarLocal(cliente)
        db.marcarPendiente(VineuroDatabase.PEND_CLIENTE_SUBIR, cliente.id, true)
        scope.launch {
            if (supabaseService.upsertCliente(cliente)) {
                db.marcarPendiente(VineuroDatabase.PEND_CLIENTE_SUBIR, cliente.id, false)
            }
        }
        return localOk
    }

    /** Elimina de SQLite al instante y de Supabase (si no hay Internet, se reintenta al sincronizar). */
    suspend fun eliminarCliente(id: String): Boolean {
        db.writableDatabase.delete(VineuroDatabase.T_CLIENTES, "id = ?", arrayOf(id))
        db.marcarPendiente(VineuroDatabase.PEND_CLIENTE_SUBIR, id, false)
        db.marcarPendiente(VineuroDatabase.PEND_CLIENTE_ELIMINAR, id, true)
        val ok = supabaseService.deleteCliente(id)
        if (ok) db.marcarPendiente(VineuroDatabase.PEND_CLIENTE_ELIMINAR, id, false)
        return ok
    }

    /**
     * 1) Reintenta borrados y subidas pendientes.
     * 2) Descarga la lista de la nube y la toma como referencia (más los pendientes de subir).
     * Si no hay conexión, devuelve la lista local.
     */
    suspend fun sincronizarConSupabase(): List<Cliente> {
        db.pendientes(VineuroDatabase.PEND_CLIENTE_ELIMINAR).forEach { id ->
            if (supabaseService.deleteCliente(id)) db.marcarPendiente(VineuroDatabase.PEND_CLIENTE_ELIMINAR, id, false)
        }
        db.pendientes(VineuroDatabase.PEND_CLIENTE_SUBIR).forEach { id ->
            val c = obtenerClientePorId(id)
            if (c == null || supabaseService.upsertCliente(c)) db.marcarPendiente(VineuroDatabase.PEND_CLIENTE_SUBIR, id, false)
        }

        val nube = supabaseService.fetchClientes() ?: return obtenerClientes()
        val eliminados = db.pendientes(VineuroDatabase.PEND_CLIENTE_ELIMINAR)
        val pendientes = db.pendientes(VineuroDatabase.PEND_CLIENTE_SUBIR)

        val w = db.writableDatabase
        w.beginTransaction()
        try {
            // Se conservan solo los clientes locales que aún no se pudieron subir
            if (pendientes.isEmpty()) {
                w.delete(VineuroDatabase.T_CLIENTES, null, null)
            } else {
                val marcas = pendientes.joinToString(",") { "?" }
                w.delete(VineuroDatabase.T_CLIENTES, "id NOT IN ($marcas)", pendientes.toTypedArray())
            }
            nube.filter { it.id !in eliminados && it.id !in pendientes }.forEach {
                w.insertWithOnConflict(VineuroDatabase.T_CLIENTES, null, valores(it), SQLiteDatabase.CONFLICT_REPLACE)
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        return obtenerClientes()
    }
}
