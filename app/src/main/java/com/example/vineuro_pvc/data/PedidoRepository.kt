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

    /** Pedidos de un cliente (el más reciente primero). */
    fun obtenerPedidosDeCliente(clienteId: String): List<Pedido> = consultar("cliente_id = ?", arrayOf(clienteId))

    fun obtenerUltimoPedido(): Pedido? = consultar(null, null, limite = "1").firstOrNull()

    fun buscarPedidos(query: String): List<Pedido> {
        if (query.isBlank()) return obtenerPedidos()
        // Se filtra en memoria porque los ítems (JSON) y los campos de texto se comparan juntos
        val q = query.trim().lowercase()
        return obtenerPedidos().filter {
            it.codigo.lowercase().contains(q) || it.clienteNombre.lowercase().contains(q) ||
                it.direccion.lowercase().contains(q) || it.idOferta.lowercase().contains(q) || it.nota.lowercase().contains(q)
        }
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
            direccion = c.getString(c.getColumnIndexOrThrow("direccion")) ?: "",
            idOferta = c.getString(c.getColumnIndexOrThrow("id_oferta")) ?: "",
            nota = c.getString(c.getColumnIndexOrThrow("nota")) ?: "",
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
        db.borrarConfiguracion(CotizacionPedido.clave(id))
        db.borrarConfiguracion(TerminosRepository.claveTiempo(id))
        // Se anota como pendiente: si no hay Internet, se borrará de la nube al sincronizar (y no reaparece)
        db.marcarPendiente(VineuroDatabase.PEND_PEDIDO_ELIMINAR, id, true)
        val error = supabaseService.deletePedido(id)
        if (error == null) db.marcarPendiente(VineuroDatabase.PEND_PEDIDO_ELIMINAR, id, false)
        return error
    }

    /**
     * Descarga los pedidos de Supabase y los fusiona con los locales (gana el más reciente).
     * Los pedidos que solo existen en el teléfono, o son más recientes, se suben a la nube.
     */
    suspend fun sincronizar(): List<Pedido> {
        // 1) Reintenta los borrados que quedaron pendientes por falta de Internet
        db.pendientes(VineuroDatabase.PEND_PEDIDO_ELIMINAR).forEach { id ->
            if (supabaseService.deletePedido(id) == null) db.marcarPendiente(VineuroDatabase.PEND_PEDIDO_ELIMINAR, id, false)
        }
        val eliminados = db.pendientes(VineuroDatabase.PEND_PEDIDO_ELIMINAR)

        val nube = supabaseService.fetchPedidos()?.filter { it.id !in eliminados } ?: return obtenerPedidos()
        val locales = obtenerPedidos().associateBy { it.id }

        // Subir lo que el teléfono tiene más reciente (o que la nube no tiene)
        for (local in locales.values) {
            val remoto = nube.find { it.id == local.id }
            val extrasSinSubir = remoto != null && remoto.direccion.isEmpty() && remoto.idOferta.isEmpty() && remoto.nota.isEmpty() &&
                (local.direccion.isNotEmpty() || local.idOferta.isNotEmpty() || local.nota.isNotEmpty())
            if (remoto == null || local.fechaActualizacion > remoto.fechaActualizacion || extrasSinSubir) {
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
