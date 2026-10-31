package com.example.vineuro_pvc.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Ajustes locales de la IA: clave, modelo y pausa por cuota. Solo viven en este teléfono (no se suben a la nube). */
object GeminiConfig {
    private const val PREFS = "pref_ia"
    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun clave(ctx: Context): String = p(ctx).getString("clave", "").orEmpty().trim()
    fun modelo(ctx: Context): String = p(ctx).getString("modelo", "").orEmpty().trim().ifBlank { GeminiAsesor.MODELO_POR_DEFECTO }

    fun guardar(ctx: Context, clave: String, modelo: String) {
        p(ctx).edit().putString("clave", clave.trim()).putString("modelo", modelo.trim()).apply()
        limpiarPausa(ctx) // otra clave u otro modelo pueden tener cupo
    }

    fun pausar(ctx: Context, fallo: GeminiAsesor.Fallo) {
        if (fallo.bloquearHastaMs <= 0L) return
        p(ctx).edit().putLong("pausa_hasta", fallo.bloquearHastaMs).putString("pausa_tipo", fallo.tipo.name).apply()
    }

    fun limpiarPausa(ctx: Context) { p(ctx).edit().remove("pausa_hasta").remove("pausa_tipo").apply() }

    /** Fallo vigente por pausa (cuota o saturación), o null si ya se puede intentar. */
    fun pausaVigente(ctx: Context, ahoraMs: Long = System.currentTimeMillis()): GeminiAsesor.Fallo? {
        val hasta = p(ctx).getLong("pausa_hasta", 0L)
        if (hasta <= ahoraMs) return null
        val tipo = runCatching { GeminiAsesor.TipoFallo.valueOf(p(ctx).getString("pausa_tipo", "").orEmpty()) }.getOrNull() ?: return null
        val msg = when (tipo) {
            GeminiAsesor.TipoFallo.CUOTA_DIA -> "Se agotó la cuota diaria gratuita de Gemini. Se renueva a las ${GeminiAsesor.horaLima(hasta)} (hora de Lima)."
            GeminiAsesor.TipoFallo.CUOTA_MINUTO -> "Límite por minuto de Gemini alcanzado. Disponible de nuevo en ${GeminiAsesor.duracion(((hasta - ahoraMs) / 1000).toInt().coerceAtLeast(1))}."
            else -> "Gemini está saturado; se reintentará en ${GeminiAsesor.duracion(((hasta - ahoraMs) / 1000).toInt().coerceAtLeast(1))}."
        }
        return GeminiAsesor.Fallo(tipo, msg, hasta)
    }
}

/** Resumen corto y dinámico del estado de la IA para mostrarlo bajo el botón. */
data class EstadoIA(val nivel: Nivel, val texto: String) {
    enum class Nivel { LISTA, AVISO, PAUSA }
}

sealed class ResultadoIA {
    data class Ok(val propuestas: List<AsesorDiseno.Propuesta>) : ResultadoIA()
    data class Error(val fallo: GeminiAsesor.Fallo) : ResultadoIA()
}

object GeminiClient {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models"

    fun hayInternet(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** Qué mostrar bajo el botón, sin hacer ninguna llamada a Gemini. */
    fun estado(ctx: Context): EstadoIA {
        if (GeminiConfig.clave(ctx).isBlank()) return EstadoIA(EstadoIA.Nivel.AVISO, "IA sin configurar: toca aquí para agregar tu clave (mientras tanto se usan reglas)")
        GeminiConfig.pausaVigente(ctx)?.let { return EstadoIA(EstadoIA.Nivel.PAUSA, "IA en pausa: ${it.mensaje.removeSuffix(".")} · se usan reglas") }
        if (!hayInternet(ctx)) return EstadoIA(EstadoIA.Nivel.AVISO, "Sin Internet: se usan reglas")
        return EstadoIA(EstadoIA.Nivel.LISTA, "IA lista (${GeminiConfig.modelo(ctx)})")
    }

    /** Tres alternativas en una sola consulta. */
    suspend fun proponer(ctx: Context, n: AsesorDiseno.Necesidad, precios: Precios): ResultadoIA =
        consultar(ctx, { GeminiAsesor.cuerpoAlternativas(n, precios, it) }) { GeminiAsesor.parsearAlternativas(it, n, precios) }

    /** Modifica una propuesta según una frase del vendedor. */
    suspend fun ajustar(ctx: Context, n: AsesorDiseno.Necesidad, precios: Precios, actual: AsesorDiseno.Propuesta, instruccion: String): ResultadoIA =
        consultar(ctx, { GeminiAsesor.cuerpoAjuste(n, precios, actual, instruccion, it) }) {
            listOfNotNull(GeminiAsesor.parsear(it, n, precios)?.copy(etiqueta = actual.etiqueta))
        }

    private suspend fun consultar(
        ctx: Context,
        cuerpo: (Boolean) -> org.json.JSONObject,
        interpretar: (String) -> List<AsesorDiseno.Propuesta>,
    ): ResultadoIA = withContext(Dispatchers.IO) {
        val clave = GeminiConfig.clave(ctx)
        if (clave.isBlank()) return@withContext ResultadoIA.Error(GeminiAsesor.Fallo(GeminiAsesor.TipoFallo.SIN_CLAVE, "Falta la clave de Gemini."))
        GeminiConfig.pausaVigente(ctx)?.let { return@withContext ResultadoIA.Error(it) }
        if (!hayInternet(ctx)) return@withContext ResultadoIA.Error(GeminiAsesor.Fallo(GeminiAsesor.TipoFallo.SIN_INTERNET, "Sin conexión a Internet."))

        val modelo = GeminiConfig.modelo(ctx)
        var respuesta = llamar(clave, modelo, cuerpo(true).toString())
        // Si Google no acepta el esquema, se reintenta una vez pidiendo el JSON solo en el texto
        if (respuesta is Respuesta.Error && respuesta.fallo.tipo == GeminiAsesor.TipoFallo.DESCONOCIDO && respuesta.codigo == 400) {
            respuesta = llamar(clave, modelo, cuerpo(false).toString())
        }
        when (respuesta) {
            is Respuesta.Error -> {
                GeminiConfig.pausar(ctx, respuesta.fallo)
                ResultadoIA.Error(respuesta.fallo)
            }
            is Respuesta.Texto -> {
                val propuestas = GeminiAsesor.textoDeRespuesta(respuesta.cuerpo)?.let(interpretar).orEmpty()
                if (propuestas.isNotEmpty()) ResultadoIA.Ok(propuestas)
                else ResultadoIA.Error(GeminiAsesor.Fallo(GeminiAsesor.TipoFallo.RESPUESTA, "La IA propuso algo que no coincide con tu catálogo."))
            }
        }
    }

    /** Prueba mínima de clave y modelo. null = todo bien; si no, el motivo exacto. */
    suspend fun probar(ctx: Context): GeminiAsesor.Fallo? = withContext(Dispatchers.IO) {
        val clave = GeminiConfig.clave(ctx)
        if (clave.isBlank()) return@withContext GeminiAsesor.Fallo(GeminiAsesor.TipoFallo.SIN_CLAVE, "Falta la clave de Gemini.")
        if (!hayInternet(ctx)) return@withContext GeminiAsesor.Fallo(GeminiAsesor.TipoFallo.SIN_INTERNET, "Sin conexión a Internet.")
        val cuerpo = org.json.JSONObject()
            .put("contents", org.json.JSONArray().put(org.json.JSONObject().put("parts", org.json.JSONArray().put(org.json.JSONObject().put("text", "Responde solo: ok")))))
            .put("generationConfig", org.json.JSONObject().put("maxOutputTokens", 64))
        when (val r = llamar(clave, GeminiConfig.modelo(ctx), cuerpo.toString())) {
            is Respuesta.Error -> { GeminiConfig.pausar(ctx, r.fallo); r.fallo }
            is Respuesta.Texto -> { GeminiConfig.limpiarPausa(ctx); null }
        }
    }

    private sealed class Respuesta {
        class Texto(val cuerpo: String) : Respuesta()
        class Error(val codigo: Int, val fallo: GeminiAsesor.Fallo) : Respuesta()
    }

    private fun llamar(clave: String, modelo: String, cuerpo: String): Respuesta {
        return try {
            val con = (URL("$BASE/$modelo:generateContent").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-goog-api-key", clave)
            }
            con.outputStream.use { it.write(cuerpo.toByteArray(Charsets.UTF_8)) }
            val codigo = con.responseCode
            val texto = (if (codigo in 200..299) con.inputStream else con.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (codigo in 200..299) Respuesta.Texto(texto)
            else Respuesta.Error(codigo, GeminiAsesor.interpretarError(codigo, texto, System.currentTimeMillis(), modelo))
        } catch (e: java.io.IOException) {
            Respuesta.Error(0, GeminiAsesor.Fallo(GeminiAsesor.TipoFallo.SIN_INTERNET, "No se pudo conectar con Gemini (revisa tu conexión)."))
        } catch (e: Exception) {
            Respuesta.Error(0, GeminiAsesor.Fallo(GeminiAsesor.TipoFallo.DESCONOCIDO, "Error inesperado al consultar la IA."))
        }
    }
}
