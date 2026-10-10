package com.example.vineuro_pvc.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray

/**
 * Repositorio de pedidos: base de datos local SQLite (tabla "pedidos") + tabla "pedidos" de Supabase.
 * La copia local permite trabajar sin Internet; la nube es la copia compartida.
 */
class PedidoRepository(context: Context) {

    private val db = VineuroDatabase.get(context)
    private val supabaseService = SupabaseService()

    // ---------- Local (SQLite) ----------

    fun obtenerPedidos(): List<Pedido> = consultar(null, null)

    fun obtenerPedido(id: String): Pedido? = consultar("id = ?", arrayOf(id)).firstOrNull()

    fun obtenerUltimoPedido(): Pedido? = consultar(null, null, limite = "1").firstOrNull()

    fun buscarPedidos(query: String): List<Pedido> {
        if (query.isBlank()) return obtenerPedidos()
        val q = "%${query.trim()}%"
        return consultar("codigo LIKE ? OR cliente_nombre LIKE ?", arrayOf(q, q))
    }

    private fun consultar(where: String?, args: Array<String>?, limite: String? = null): List<Pedido> {
        val lista = mutableListOf<Pedido>()
        db.readableDatabase.query(
            VineuroDatabase.T_PEDIDOS, null, where, args, null, null, "fecha_actualizacion DESC", limite,
        ).use { c ->
            while (c.moveToNext()) {
                try {
                    lista.add(leer(c))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return lista
    }

    private fun leer(c: Cursor): Pedido {
        val items = runCatching { JSONArray(c.getString(c.getColumnIndexOrThrow("items"))) }.getOrDefault(JSONArray())
        return Pedido(
            id = c.getString(c.getColumnIndexOrThrow("id")),
            codigo = c.getString(c.getColumnIndexOrThrow("codigo")),
            clienteId = c.getString(c.getColumnIndexOrThrow("cliente_id")),
            clienteNombre = c.getString(c.getColumnIndexOrThrow("cliente_nombre")),
            items = PedidoJson.itemsFromJson(items),
            estado = c.getString(c.getColumnIndexOrThrow("estado")),
            fechaCreacion = c.getLong(c.getColumnIndexOrThrow("fecha_creacion")),
            fechaActualizacion = c.getLong(c.getColumnIndexOrThrow("fecha_actualizacion")),
        )
    }

    private fun guardarLocal(pedido: Pedido) {
        db.writableDatabase.insertWithOnConflict(
            VineuroDatabase.T_PEDIDOS, null, VineuroDatabase.pedidoToValues(pedido), SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    // ---------- Local + Nube ----------

    /**
     * Guarda en SQLite y luego en Supabase.
     * @return null si se guardó en la nube; si falló, el mensaje de error (el pedido igual queda guardado localmente).
     */
    suspend fun guardarPedido(pedido: Pedido): String? {
        guardarLocal(pedido)
        return supabaseService.upsertPedido(pedido)
    }

    /** Elimina local y en la nube. Devuelve null si se eliminó correctamente, o el mensaje de error de la nube. */
    suspend fun eliminarPedido(id: String): String? {
        db.writableDatabase.delete(VineuroDatabase.T_PEDIDOS, "id = ?", arrayOf(id))
        return supabaseService.deletePedido(id)
    }

    /**
     * Descarga los pedidos de Supabase y los fusiona con los locales (gana el más reciente).
     * Los pedidos que solo existen en el teléfono, o son más recientes, se suben a la nube.
     */
    suspend fun sincronizar(): List<Pedido> {
        val nube = supabaseService.fetchPedidos() ?: return obtenerPedidos()
        val locales = obtenerPedidos().associateBy { it.id }

        // Subir lo que el teléfono tiene más reciente (o que la nube no tiene)
        for (local in locales.values) {
            val remoto = nube.find { it.id == local.id }
            if (remoto == null || local.fechaActualizacion > remoto.fechaActualizacion) {
                supabaseService.upsertPedido(local)
            }
        }

        // Bajar lo que la nube tiene más reciente
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            for (remoto in nube) {
                val local = locales[remoto.id]
                if (local == null || remoto.fechaActualizacion > local.fechaActualizacion) {
                    w.insertWithOnConflict(
                        VineuroDatabase.T_PEDIDOS, null, VineuroDatabase.pedidoToValues(remoto), SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        return obtenerPedidos()
    }
}
