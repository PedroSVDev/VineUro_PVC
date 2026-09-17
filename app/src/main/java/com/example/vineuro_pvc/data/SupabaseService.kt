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

    suspend fun upsertCliente(cliente: Cliente): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("${SupabaseConfig.REST_URL}/clientes")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("apikey", SupabaseConfig.API_KEY)
                setRequestProperty("Authorization", "Bearer ${SupabaseConfig.API_KEY}")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", "resolution=merge-duplicates,return=representation")
                doOutput = true
            }

            val payload = JSONObject().apply {
                put("id", cliente.id)
                put("dni", cliente.dni)
                put("nombres", cliente.nombres)
                put("apellidos", cliente.apellidos)
                put("telefono", cliente.telefono)
                put("email", cliente.email)
                put("direccion", cliente.direccion)
                put("nota", cliente.nota)
                put("fecha_registro", cliente.fechaRegistro)
            }

            connection.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            return@withContext (code in 200..299)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    suspend fun fetchClientes(): List<Cliente> = withContext(Dispatchers.IO) {
        val clientes = mutableListOf<Cliente>()
        try {
            val url = URL("${SupabaseConfig.REST_URL}/clientes?select=*&order=fecha_registro.desc")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("apikey", SupabaseConfig.API_KEY)
                setRequestProperty("Authorization", "Bearer ${SupabaseConfig.API_KEY}")
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode in 200..299) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val cliente = Cliente(
                        id = obj.optString("id"),
                        dni = obj.optString("dni"),
                        nombres = obj.optString("nombres"),
                        apellidos = obj.optString("apellidos"),
                        telefono = obj.optString("telefono", ""),
                        email = obj.optString("email", ""),
                        direccion = obj.optString("direccion", ""),
                        nota = obj.optString("nota", ""),
                        fechaRegistro = obj.optLong("fecha_registro", System.currentTimeMillis()),
                    )
                    clientes.add(cliente)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext clientes
    }

    suspend fun deleteCliente(dni: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("${SupabaseConfig.REST_URL}/clientes?dni=eq.$dni")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("apikey", SupabaseConfig.API_KEY)
                setRequestProperty("Authorization", "Bearer ${SupabaseConfig.API_KEY}")
            }

            val code = connection.responseCode
            return@withContext (code in 200..299)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
}
