package com.example.vineuro_pvc.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

/**
 * Base de datos local SQLite del aplicativo (archivo "vineuro.db" en el teléfono).
 *
 * Tablas:
 *  - clientes            : un registro por cliente
 *  - pedidos             : un registro por pedido; las ventanas (items) se guardan como texto JSON,
 *                          igual que la columna jsonb "items" de Supabase
 *  - precios             : una sola fila (id = "general") con todo el apartado de Precios en JSON
 *  - pendientes_sync     : operaciones con Supabase que quedaron pendientes por falta de Internet
 *
 * La primera vez que se crea, copia automáticamente los datos que existían en el almacenamiento
 * anterior (SharedPreferences en JSON) para no perder nada.
 */
class VineuroDatabase private constructor(private val appContext: Context) :
    SQLiteOpenHelper(appContext, DB_NOMBRE, null, DB_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $T_CLIENTES (
                id             TEXT PRIMARY KEY NOT NULL,
                nombres        TEXT NOT NULL,
                apellidos      TEXT NOT NULL,
                telefono       TEXT NOT NULL DEFAULT '',
                email          TEXT NOT NULL DEFAULT '',
                direccion      TEXT NOT NULL DEFAULT '',
                nota           TEXT NOT NULL DEFAULT '',
                fecha_registro INTEGER NOT NULL,
                codigo         TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE $T_PEDIDOS (
                id                  TEXT PRIMARY KEY NOT NULL,
                codigo              TEXT NOT NULL,
                cliente_id          TEXT NOT NULL DEFAULT '',
                cliente_nombre      TEXT NOT NULL DEFAULT '',
                items               TEXT NOT NULL DEFAULT '[]',
                total_ventanas      INTEGER NOT NULL DEFAULT 0,
                area_total_m2       REAL NOT NULL DEFAULT 0,
                estado              TEXT NOT NULL DEFAULT 'borrador',
                fecha_creacion      INTEGER NOT NULL,
                fecha_actualizacion INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_pedidos_cliente ON $T_PEDIDOS(cliente_id)")
        db.execSQL("CREATE INDEX idx_pedidos_fecha ON $T_PEDIDOS(fecha_actualizacion)")
        db.execSQL(
            """
            CREATE TABLE $T_PRECIOS (
                id                  TEXT PRIMARY KEY NOT NULL,
                data                TEXT NOT NULL,
                fecha_actualizacion INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE $T_PENDIENTES (
                tipo TEXT NOT NULL,
                id   TEXT NOT NULL,
                PRIMARY KEY (tipo, id)
            )
            """.trimIndent(),
        )

        crearTablaConfiguracion(db)
        migrarDesdeSharedPreferences(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Versión 2: tabla "configuracion" (requisitos del documento, etc.)
        if (oldVersion < 2) crearTablaConfiguracion(db)
        // Versión 3: código corto del cliente (CLI-0001…)
        if (oldVersion < 3) {
            runCatching { db.execSQL("ALTER TABLE $T_CLIENTES ADD COLUMN codigo TEXT NOT NULL DEFAULT ''") }
        }
    }

    private fun crearTablaConfiguracion(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $T_CONFIGURACION (
                clave               TEXT PRIMARY KEY NOT NULL,
                data                TEXT NOT NULL,
                fecha_actualizacion INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    // ===================== CONFIGURACIÓN (clave -> JSON) =====================

    fun leerConfiguracion(clave: String): String? =
        readableDatabase.query(T_CONFIGURACION, arrayOf("data"), "clave = ?", arrayOf(clave), null, null, null)
            .use { c -> if (c.moveToFirst()) c.getString(0) else null }

    fun guardarConfiguracion(clave: String, json: String, fecha: Long) {
        val cv = ContentValues().apply {
            put("clave", clave)
            put("data", json)
            put("fecha_actualizacion", fecha)
        }
        writableDatabase.insertWithOnConflict(T_CONFIGURACION, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ===================== MIGRACIÓN DESDE EL ALMACENAMIENTO ANTERIOR =====================

    private fun migrarDesdeSharedPreferences(db: SQLiteDatabase) {
        try {
            migrarClientes(db)
            migrarPedidos(db)
            migrarPrecios(db)
        } catch (e: Exception) {
            // Si algo falla, la app sigue funcionando: los datos se vuelven a bajar de Supabase
            e.printStackTrace()
        }
    }

    private fun migrarClientes(db: SQLiteDatabase) {
        val prefs = appContext.getSharedPreferences("pref_clientes", Context.MODE_PRIVATE)
        prefs.getString("lista_clientes", null)?.let { json ->
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isBlank()) continue
                val cv = ContentValues().apply {
                    put("id", id)
                    put("nombres", o.optString("nombres"))
                    put("apellidos", o.optString("apellidos"))
                    put("telefono", o.optString("telefono"))
                    put("email", o.optString("email"))
                    put("direccion", o.optString("direccion"))
                    put("nota", o.optString("nota"))
                    put("fecha_registro", o.optLong("fechaRegistro", System.currentTimeMillis()))
                }
                db.insertWithOnConflict(T_CLIENTES, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
        prefs.getStringSet("pendientes_subir", null)?.forEach { insertarPendiente(db, PEND_CLIENTE_SUBIR, it) }
        prefs.getStringSet("pendientes_eliminar", null)?.forEach { insertarPendiente(db, PEND_CLIENTE_ELIMINAR, it) }
        prefs.edit().clear().apply()
    }

    private fun migrarPedidos(db: SQLiteDatabase) {
        val prefs = appContext.getSharedPreferences("pref_pedidos", Context.MODE_PRIVATE)
        prefs.getString("lista_pedidos", null)?.let { json ->
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val pedido = PedidoJson.pedidoFromJson(o)
                if (pedido.id.isBlank()) continue
                db.insertWithOnConflict(T_PEDIDOS, null, pedidoToValues(pedido), SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
        prefs.edit().clear().apply()
    }

    private fun migrarPrecios(db: SQLiteDatabase) {
        val prefs = appContext.getSharedPreferences("pref_precios", Context.MODE_PRIVATE)
        prefs.getString("precios_json", null)?.let { json ->
            val o = JSONObject(json)
            val cv = ContentValues().apply {
                put("id", PRECIOS_ID)
                put("data", json)
                put("fecha_actualizacion", o.optLong("fecha_actualizacion", System.currentTimeMillis()))
            }
            db.insertWithOnConflict(T_PRECIOS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
        prefs.edit().clear().apply()
    }

    private fun insertarPendiente(db: SQLiteDatabase, tipo: String, id: String) {
        val cv = ContentValues().apply { put("tipo", tipo); put("id", id) }
        db.insertWithOnConflict(T_PENDIENTES, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    // ===================== PENDIENTES DE SINCRONIZACIÓN =====================

    fun marcarPendiente(tipo: String, id: String, pendiente: Boolean) {
        if (pendiente) {
            insertarPendiente(writableDatabase, tipo, id)
        } else {
            writableDatabase.delete(T_PENDIENTES, "tipo = ? AND id = ?", arrayOf(tipo, id))
        }
    }

    fun pendientes(tipo: String): Set<String> {
        val set = mutableSetOf<String>()
        readableDatabase.query(T_PENDIENTES, arrayOf("id"), "tipo = ?", arrayOf(tipo), null, null, null).use { c ->
            while (c.moveToNext()) set.add(c.getString(0))
        }
        return set
    }

    companion object {
        private const val DB_NOMBRE = "vineuro.db"
        private const val DB_VERSION = 3

        const val T_CLIENTES = "clientes"
        const val T_PEDIDOS = "pedidos"
        const val T_PRECIOS = "precios"
        const val T_PENDIENTES = "pendientes_sync"
        const val T_CONFIGURACION = "configuracion"

        const val PRECIOS_ID = "general"
        const val PEND_CLIENTE_SUBIR = "cliente_subir"
        const val PEND_CLIENTE_ELIMINAR = "cliente_eliminar"

        @Volatile
        private var instancia: VineuroDatabase? = null

        fun get(context: Context): VineuroDatabase =
            instancia ?: synchronized(this) {
                instancia ?: VineuroDatabase(context.applicationContext).also { instancia = it }
            }

        /** Convierte un Pedido a una fila de la tabla pedidos. */
        fun pedidoToValues(p: Pedido) = ContentValues().apply {
            put("id", p.id)
            put("codigo", p.codigo)
            put("cliente_id", p.clienteId)
            put("cliente_nombre", p.clienteNombre)
            put("items", PedidoJson.itemsToJson(p.items).toString())
            put("total_ventanas", p.totalVentanas)
            put("area_total_m2", Math.round(p.areaTotalM2 * 100) / 100.0)
            put("estado", p.estado)
            put("fecha_creacion", p.fechaCreacion)
            put("fecha_actualizacion", p.fechaActualizacion)
        }
    }
}
