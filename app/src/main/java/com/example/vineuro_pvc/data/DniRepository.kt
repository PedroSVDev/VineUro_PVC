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
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)

                val nombres = json.optString("nombres", "").ifEmpty {
                    json.optString("nombre", "")
                }

                val paterno = json.optString("apellidoPaterno", "")
                val materno = json.optString("apellidoMaterno", "")
                val apellidos = if (paterno.isNotEmpty() || materno.isNotEmpty()) {
                    "$paterno $materno".trim()
                } else {
                    json.optString("apellidos", "")
                }

                if (nombres.isNotEmpty() || apellidos.isNotEmpty()) {
                    return@withContext DniResult.Success(
                        nombres = nombres,
                        apellidos = apellidos,
                    )
                }
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
                val json = JSONObject(jsonStr)
                val nombres = json.optString("nombres", "")
                val paterno = json.optString("apellidoPaterno", "")
                val materno = json.optString("apellidoMaterno", "")
                val apellidos = "$paterno $materno".trim()

                if (nombres.isNotEmpty()) {
                    return@withContext DniResult.Success(nombres = nombres, apellidos = apellidos)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext DniResult.Error("No se pudieron obtener datos automáticos para el DNI $dni. Puedes ingresar los nombres manualmente.")
    }
}
