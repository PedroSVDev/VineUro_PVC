package com.example.vineuro_pvc.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Servicio de conexión directa con la API REST de Supabase.
 */
class SupabaseService {

    // ===================== CLIENTES =====================
    // Se identifican por "id" (clave primaria). Ya no se usa DNI.

    suspend fun upsertCliente(cliente: Cliente): Boolean = withContext(Dispatchers.IO) {
        // Primero con el código corto; si la columna "codigo" aún no existe en Supabase, se envía sin ella
        enviarCliente(cliente, conCodigo = true) || enviarCliente(cliente, conCodigo = false)
    }

    private fun enviarCliente(cliente: Cliente, conCodigo: Boolean): Boolean {
        return try {
            val connection = abrirConexion("clientes?on_conflict=id", "POST").apply {
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal")
                doOutput = true
            }
            val payload = JSONObject().apply {
                put("id", cliente.id)
                put("nombres", cliente.nombres)
                put("apellidos", cliente.apellidos)
                put("telefono", cliente.telefono)
                put("email", cliente.email)
                put("direccion", cliente.direccion)
                put("nota", cliente.nota)
                put("fecha_registro", cliente.fechaRegistro)
                if (conCodigo) put("codigo", cliente.codigo)
            }
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val ok = connection.responseCode in 200..299
            if (!ok) android.util.Log.w("SupabaseService", "upsertCliente: ${leerError(connection)}")
            ok
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** Lista de clientes de la nube, o null si no se pudo conectar (para no borrar la copia local). */
    suspend fun fetchClientes(): List<Cliente>? = withContext(Dispatchers.IO) {
        try {
            val connection = abrirConexion("clientes?select=*&order=fecha_registro.desc", "GET")
            if (connection.responseCode !in 200..299) {
                android.util.Log.w("SupabaseService", "fetchClientes: ${leerError(connection)}")
                return@withContext null
            }
            val array = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            (0 until array.length()).mapNotNull { i ->
                val obj = array.getJSONObject(i)
                val id = obj.optString("id")
                if (id.isBlank()) return@mapNotNull null
                Cliente(
                    id = id,
                    nombres = obj.optString("nombres"),
                    apellidos = obj.optString("apellidos"),
                    telefono = textoONada(obj, "telefono"),
                    email = textoONada(obj, "email"),
                    direccion = textoONada(obj, "direccion"),
                    nota = textoONada(obj, "nota"),
                    fechaRegistro = obj.optLong("fecha_registro", System.currentTimeMillis()),
                    codigo = textoONada(obj, "codigo"),
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Elimina el cliente por id. Devuelve true si se borró (o ya no existía). */
    suspend fun deleteCliente(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val connection = abrirConexion("clientes?id=eq.$id", "DELETE")
            val ok = connection.responseCode in 200..299
            if (!ok) android.util.Log.w("SupabaseService", "deleteCliente: ${leerError(connection)}")
            ok
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** Las columnas vacías llegan como null en JSON; optString las convertiría en el texto "null". */
    private fun textoONada(obj: JSONObject, key: String): String =
        if (obj.isNull(key)) "" else obj.optString(key, "")

    // ===================== PEDIDOS =====================

    private fun abrirConexion(path: String, method: String): HttpURLConnection =
        (URL("${SupabaseConfig.REST_URL}/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("apikey", SupabaseConfig.API_KEY)
            setRequestProperty("Authorization", "Bearer ${SupabaseConfig.API_KEY}")
            setRequestProperty("Accept", "application/json")
        }

    private fun leerError(connection: HttpURLConnection): String {
        val body = runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
        val msg = runCatching { JSONObject(body ?: "").optString("message") }.getOrNull()
        return "HTTP ${connection.responseCode}: ${msg?.ifBlank { null } ?: body ?: "sin detalle"}"
    }

    /** Inserta o actualiza el pedido. Devuelve null si OK, o un mensaje de error. */
    suspend fun upsertPedido(pedido: Pedido): String? = withContext(Dispatchers.IO) {
        try {
            val connection = abrirConexion("pedidos?on_conflict=id", "POST").apply {
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal")
                doOutput = true
            }
            connection.outputStream.use {
                it.write(PedidoJson.pedidoToJson(pedido).toString().toByteArray(Charsets.UTF_8))
            }
            if (connection.responseCode in 200..299) null else leerError(connection)
        } catch (e: Exception) {
            e.printStackTrace()
            "Sin conexión: ${e.message}"
        }
    }

    /** Lista todos los pedidos. Devuelve null si no se pudo conectar (para no borrar la copia local). */
    suspend fun fetchPedidos(): List<Pedido>? = withContext(Dispatchers.IO) {
        try {
            val connection = abrirConexion("pedidos?select=*&order=fecha_actualizacion.desc", "GET")
            if (connection.responseCode !in 200..299) {
                android.util.Log.w("SupabaseService", "fetchPedidos: ${leerError(connection)}")
                return@withContext null
            }
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val array = JSONArray(json)
            (0 until array.length()).map { PedidoJson.pedidoFromJson(array.getJSONObject(it)) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Elimina el pedido por id. Devuelve null si OK, o un mensaje de error. */
    suspend fun deletePedido(id: String): String? = withContext(Dispatchers.IO) {
        try {
            val connection = abrirConexion("pedidos?id=eq.$id", "DELETE")
            if (connection.responseCode in 200..299) null else leerError(connection)
        } catch (e: Exception) {
            e.printStackTrace()
            "Sin conexión: ${e.message}"
        }
    }

    // ===================== PRECIOS =====================
    // Tabla "precios" con una sola fila (id = "general") que guarda todo el apartado de precios en la columna jsonb "data".

    /** Inserta o actualiza los precios. Devuelve null si OK, o un mensaje de error. */
    suspend fun upsertPrecios(precios: Precios): String? = withContext(Dispatchers.IO) {
        try {
            val connection = abrirConexion("precios?on_conflict=id", "POST").apply {
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal")
                doOutput = true
            }
            val payload = JSONObject().apply {
                put("id", PRECIOS_ID)
                put("data", PreciosJson.toJson(precios))
                put("fecha_actualizacion", precios.fechaActualizacion)
            }
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode in 200..299) {
                null
            } else {
                leerError(connection).also { android.util.Log.w("SupabaseService", "upsertPrecios: $it") }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Sin conexión: ${e.message}"
        }
    }

    /**
     * Descarga los precios.
     * - Result.success(precios) si existe la fila
     * - Result.success(null) si la tabla está vacía
     * - Result.failure si no hubo conexión o la tabla no existe (para no pisar la copia local)
     */
    suspend fun fetchPrecios(): Result<Precios?> = withContext(Dispatchers.IO) {
        try {
            val connection = abrirConexion("precios?id=eq.$PRECIOS_ID&select=*", "GET")
            if (connection.responseCode !in 200..299) {
                val err = leerError(connection)
                android.util.Log.w("SupabaseService", "fetchPrecios: $err")
                return@withContext Result.failure(Exception(err))
            }
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val array = JSONArray(json)
            if (array.length() == 0) return@withContext Result.success(null)

            val fila = array.getJSONObject(0)
            val data = when (val raw = fila.opt("data")) {
                is JSONObject -> raw
                is String -> JSONObject(raw)
                else -> return@withContext Result.success(null)
            }
            val precios = PreciosJson.fromJson(data)
            val fecha = fila.optLong("fecha_actualizacion", precios.fechaActualizacion)
            Result.success(precios.copy(fechaActualizacion = fecha))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    // ===================== CONFIGURACIÓN =====================
    // Tabla "configuracion": una fila por clave (p. ej. "requisitos_documento") con el contenido en la columna jsonb "data".

    /** Inserta o actualiza una configuración. Devuelve null si OK, o un mensaje de error. */
    suspend fun upsertConfiguracion(clave: String, data: JSONObject, fecha: Long): String? = withContext(Dispatchers.IO) {
        try {
            val connection = abrirConexion("configuracion?on_conflict=id", "POST").apply {
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal")
                doOutput = true
            }
            val payload = JSONObject().apply {
                put("id", clave)
                put("data", data)
                put("fecha_actualizacion", fecha)
            }
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode in 200..299) null else leerError(connection)
        } catch (e: Exception) {
            e.printStackTrace()
            "Sin conexión: ${e.message}"
        }
    }

    /** Lee una configuración: success(data) si existe, success(null) si no hay fila, failure si no hubo conexión. */
    suspend fun fetchConfiguracion(clave: String): Result<JSONObject?> = withContext(Dispatchers.IO) {
        try {
            val connection = abrirConexion("configuracion?id=eq.$clave&select=*", "GET")
            if (connection.responseCode !in 200..299) return@withContext Result.failure(Exception(leerError(connection)))
            val array = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            if (array.length() == 0) return@withContext Result.success(null)
            val fila = array.getJSONObject(0)
            val data = when (val raw = fila.opt("data")) {
                is JSONObject -> raw
                is String -> JSONObject(raw)
                else -> return@withContext Result.success(null)
            }
            data.put("fecha_actualizacion", fila.optLong("fecha_actualizacion", data.optLong("fecha_actualizacion", 0L)))
            Result.success(data)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    companion object {
        private const val PRECIOS_ID = "general"
    }
}
