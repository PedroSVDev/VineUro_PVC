package com.example.vineuro_pvc.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Tipo de cambio oficial del BCRP (Banco Central de Reserva del Perú). API pública, sin token.
 *
 * Series usadas (venta, en soles):
 *  - PD04640PD: dólar, sistema bancario SBS (es el mismo que publica SUNAT)
 *  - PD04648PD: euro
 *
 * El BCRP no publica fines de semana ni feriados (aparece "n.d."), por eso se piden los
 * últimos días y se toma el último valor disponible de cada moneda.
 */
class TipoCambioService(context: Context) {

    data class TipoCambio(
        val usdPen: Double,      // S/ por 1 US$
        val eurPen: Double,      // S/ por 1 €
        val fechaUsd: String,    // fecha del dato, p. ej. "07/10/2026"
        val fechaEur: String,
        val consultadoEn: Long,  // cuándo se consultó (para el caché)
    ) {
        /** Soles que vale 1 unidad de la moneda (símbolo "S/", "US$" o "€"). null si no se conoce. */
        fun solesPor(simbolo: String): Double? = when (simbolo.trim()) {
            "S/" -> 1.0
            "US$", "$" -> usdPen
            "€" -> eurPen
            else -> null
        }

        /** Tarifa para convertir precios en [desde] a resultados en [hacia]: 1 desde = X hacia. */
        fun tarifa(desde: String, hacia: String): Double? {
            val a = solesPor(desde) ?: return null
            val b = solesPor(hacia) ?: return null
            return a / b
        }

        /** Fecha del dato relevante para el par de monedas elegido. */
        fun fechaPara(desde: String, hacia: String): String {
            val usaEuro = desde.trim() == "€" || hacia.trim() == "€"
            val usaDolar = desde.trim() in setOf("US$", "$") || hacia.trim() in setOf("US$", "$")
            return when {
                usaEuro && usaDolar -> if (fechaUsd == fechaEur) fechaUsd else "$fechaUsd / $fechaEur"
                usaEuro -> fechaEur
                else -> fechaUsd
            }
        }
    }

    private val prefs = context.applicationContext.getSharedPreferences("pref_tipo_cambio", Context.MODE_PRIVATE)

    /** Último tipo de cambio guardado en el teléfono (puede no existir). */
    fun guardado(): TipoCambio? {
        val json = prefs.getString(KEY, null) ?: return null
        return runCatching {
            val o = JSONObject(json)
            TipoCambio(
                o.getDouble("usd_pen"), o.getDouble("eur_pen"),
                o.getString("fecha_usd"), o.getString("fecha_eur"), o.getLong("consultado_en"),
            )
        }.getOrNull()
    }

    /**
     * Consulta al BCRP. Si ya se consultó hoy, devuelve lo guardado sin usar Internet
     * (a menos que [forzar] sea true).
     */
    suspend fun obtener(forzar: Boolean = false): Result<TipoCambio> = withContext(Dispatchers.IO) {
        val cache = guardado()
        if (!forzar && cache != null && esHoy(cache.consultadoEn)) return@withContext Result.success(cache)

        try {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val fin = Calendar.getInstance()
            val inicio = (fin.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -15) }
            val url = URL("$BASE/$SERIE_USD-$SERIE_EUR/json/${fmt.format(inicio.time)}/${fmt.format(fin.time)}")

            val con = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/json")
            }
            if (con.responseCode !in 200..299) {
                return@withContext Result.failure(Exception("El BCRP respondió HTTP ${con.responseCode}"))
            }
            val body = con.inputStream.bufferedReader().use { it.readText() }
            val periodos = JSONObject(body).getJSONArray("periods")

            var usd: Double? = null; var fUsd = ""
            var eur: Double? = null; var fEur = ""
            // Recorrer del más reciente al más antiguo y quedarse con el primer valor válido de cada serie
            for (i in periodos.length() - 1 downTo 0) {
                val p = periodos.getJSONObject(i)
                val valores = p.getJSONArray("values")
                val fecha = formatearFecha(p.getString("name"))
                if (usd == null) valores.optString(0).toDoubleOrNull()?.let { usd = it; fUsd = fecha }
                if (eur == null) valores.optString(1).toDoubleOrNull()?.let { eur = it; fEur = fecha }
                if (usd != null && eur != null) break
            }
            if (usd == null || eur == null) {
                return@withContext Result.failure(Exception("El BCRP no tiene datos recientes"))
            }

            val tc = TipoCambio(usd!!, eur!!, fUsd, fEur, System.currentTimeMillis())
            prefs.edit {
                putString(KEY, JSONObject().apply {
                    put("usd_pen", tc.usdPen); put("eur_pen", tc.eurPen)
                    put("fecha_usd", tc.fechaUsd); put("fecha_eur", tc.fechaEur)
                    put("consultado_en", tc.consultadoEn)
                }.toString())
            }
            Result.success(tc)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("Sin conexión con el BCRP"))
        }
    }

    private fun esHoy(millis: Long): Boolean {
        val a = Calendar.getInstance().apply { timeInMillis = millis }
        val b = Calendar.getInstance()
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    /** "07.Oct.26" -> "07/10/2026" */
    private fun formatearFecha(nombre: String): String {
        val partes = nombre.split(".")
        if (partes.size != 3) return nombre
        val mes = MESES[partes[1].lowercase(Locale.ROOT).take(3)] ?: return nombre
        return "${partes[0]}/$mes/20${partes[2]}"
    }

    companion object {
        private const val BASE = "https://estadisticas.bcrp.gob.pe/estadisticas/series/api"
        private const val SERIE_USD = "PD04640PD"
        private const val SERIE_EUR = "PD04648PD"
        private const val KEY = "ultimo_tipo_cambio"
        private val MESES = mapOf(
            "ene" to "01", "feb" to "02", "mar" to "03", "abr" to "04", "may" to "05", "jun" to "06",
            "jul" to "07", "ago" to "08", "set" to "09", "sep" to "09", "oct" to "10", "nov" to "11", "dic" to "12",
        )
    }
}
