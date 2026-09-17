package com.example.vineuro_pvc.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed class DniResult {
    data class Success(
        val nombres: String,
        val apellidos: String,
    ) : DniResult()

    data class Error(val message: String) : DniResult()
}

class DniRepository {

    var apiToken: String? = null

    suspend fun buscarDni(dni: String): DniResult = withContext(Dispatchers.IO) {
        if ((dni.length != 8) || !dni.all { it.isDigit() }) {
            return@withContext DniResult.Error("El DNI debe tener exactamente 8 dígitos numéricos.")
        }

        // Intento 1: API Apis.net.pe
        try {
            val urlString = "https://api.apis.net.pe/v1/dni?numero=$dni"
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "VineUroApp/1.0")
                apiToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val result = procesarJson(jsonStr)
                if (result != null) return@withContext result
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Intento 2: API DniRuc.dev
        try {
            val urlString2 = "https://dniruc.dev/api/dni/$dni"
            val url2 = URL(urlString2)
            val connection2 = (url2.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Accept", "application/json")
            }

            if (connection2.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonStr = connection2.inputStream.bufferedReader().use { it.readText() }
                val result = procesarJson(jsonStr)
                if (result != null) return@withContext result
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Intento 3: API Peru Dev (Endpoint alternativo público)
        try {
            val urlString3 = "https://apiperu.dev/api/dni/$dni"
            val url3 = URL(urlString3)
            val connection3 = (url3.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Accept", "application/json")
                apiToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            }

            if (connection3.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonStr = connection3.inputStream.bufferedReader().use { it.readText() }
                val result = procesarJson(jsonStr)
                if (result != null) return@withContext result
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext DniResult.Error(
            "No se encontraron datos automáticos para el DNI $dni en los servidores gratuitos. " +
                    "Esto ocurre cuando el DNI es muy reciente o las APIs públicas agotan sus consultas libres. " +
                    "Puedes ingresar los nombres y apellidos manualmente.",
        )
    }

    private fun procesarJson(jsonStr: String): DniResult.Success? {
        return try {
            val json = JSONObject(jsonStr)
            if (json.has("data")) {
                val data = json.optJSONObject("data")
                if (data != null) return extraerCamposJson(data)
            }
            extraerCamposJson(json)
        } catch (_: Exception) {
            null
        }
    }

    private fun extraerCamposJson(json: JSONObject): DniResult.Success? {
        val nombres = json.optString("nombres", "").ifEmpty {
            json.optString("nombre", "").ifEmpty {
                json.optString("first_name", "")
            }
        }

        val paterno = json.optString("apellidoPaterno", "").ifEmpty {
            json.optString("paterno", "").ifEmpty {
                json.optString("apellido_paterno", "")
            }
        }

        val materno = json.optString("apellidoMaterno", "").ifEmpty {
            json.optString("materno", "").ifEmpty {
                json.optString("apellido_materno", "")
            }
        }

        val apellidos = if (paterno.isNotEmpty() || materno.isNotEmpty()) {
            "$paterno $materno".trim()
        } else {
            json.optString("apellidos", "").ifEmpty {
                json.optString("surname", "")
            }
        }

        if (nombres.isEmpty() || apellidos.isEmpty()) {
            val nombreCompleto = json.optString("nombreCompleto", "").ifEmpty {
                json.optString("full_name", "").ifEmpty {
                    json.optString("nombre_completo", "")
                }
            }

            if (nombreCompleto.isNotEmpty()) {
                val partes = nombreCompleto.trim().split("\\s+".toRegex())
                if (partes.size >= 3) {
                    val pPaterno = partes[0]
                    val pMaterno = partes[1]
                    val pNombres = partes.subList(2, partes.size).joinToString(" ")
                    return DniResult.Success(nombres = pNombres, apellidos = "$pPaterno $pMaterno")
                } else if (partes.size == 2) {
                    return DniResult.Success(nombres = partes[1], apellidos = partes[0])
                }
            }
        }

        if (nombres.isNotEmpty() || apellidos.isNotEmpty()) {
            return DniResult.Success(nombres = nombres, apellidos = apellidos)
        }

        return null
    }
}
