package com.example.vineuro_pvc.data

import android.content.Context
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Unidades de medida (como "units" de la original): métrico (mm) o imperial (pulgadas con fracciones de 1/16).
 * Todo se calcula y guarda en milímetros; esto solo cambia cómo se MUESTRAN y se ESCRIBEN las medidas.
 */
object Unidades {
    @Volatile var imperial: Boolean = false
        private set

    private const val PREFS = "pref_ui"
    private const val KEY = "unidades_imperial"

    fun cargar(ctx: Context) {
        imperial = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
    }

    fun guardar(ctx: Context, imperial: Boolean) {
        this.imperial = imperial
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, imperial).apply()
    }

    /** "mm" o "in". */
    fun sufijo(): String = if (imperial) "in" else "mm"

    /** Medida para mostrar: "1500" (mm) o `59 1/16"` (pulgadas, redondeado a 1/16). */
    fun mostrar(mm: Int): String = mostrar(mm.toDouble(), imperial)

    fun mostrar(mm: Double, imperial: Boolean): String {
        if (!imperial) return mm.roundToInt().toString()
        val sixteenths = (mm / 25.4 * 16).roundToInt()
        val pulg = sixteenths / 16
        var resto = sixteenths % 16
        if (resto == 0) return "$pulg\""
        var den = 16
        while (resto % 2 == 0) { resto /= 2; den /= 2 }
        return if (pulg == 0) "$resto/$den\"" else "$pulg $resto/$den\""
    }

    /** Con unidad: "1500 mm" o `59 1/16"`. */
    fun mostrarConUnidad(mm: Int): String = if (imperial) mostrar(mm) else "$mm mm"

    /** "1500 x 1800 mm" o `59" x 71"`. */
    fun dimensiones(anchoMm: Int, altoMm: Int): String =
        if (imperial) "${mostrar(anchoMm)} x ${mostrar(altoMm)}" else "$anchoMm x $altoMm mm"

    /** Lo que se escribe en el teclado para editar un valor (decimal en pulgadas). */
    fun paraEditar(mm: Int): String =
        if (imperial) String.format(Locale.US, "%.3f", mm / 25.4).trimEnd('0').trimEnd('.') else mm.toString()

    /**
     * Lee una medida escrita por el usuario y la devuelve en mm. Acepta "59.5", "59 1/2", "1/2" (pulgadas)
     * o un entero en mm cuando el modo es métrico. Null si no se entiende.
     */
    fun aMm(texto: String): Int? {
        val t = texto.trim().replace("\"", "").replace(",", ".")
        if (t.isEmpty()) return null
        if (!imperial) return t.toDoubleOrNull()?.roundToInt()
        val partes = t.split(" ").filter { it.isNotBlank() }
        var pulgadas = 0.0
        for (p in partes) {
            val f = p.split("/")
            pulgadas += if (f.size == 2) {
                val n = f[0].toDoubleOrNull() ?: return null
                val d = f[1].toDoubleOrNull()?.takeIf { it != 0.0 } ?: return null
                n / d
            } else p.toDoubleOrNull() ?: return null
        }
        return (pulgadas * 25.4).roundToInt()
    }

    /** Longitud en metros para mostrar: "3.20 m" o, en imperial, "10.50 ft" (como units de la original). */
    fun largoM(m: Double): String =
        if (imperial) String.format(Locale.US, "%.2f ft", m * 3.28084) else String.format(Locale.US, "%.2f m", m)

    /** Área en m² para mostrar: "2.40 m²" o "25.83 sq.ft". */
    fun areaM2(m2: Double): String =
        if (imperial) String.format(Locale.US, "%.1f sq.ft", m2 * 10.7639) else String.format(Locale.US, "%.2f m²", m2)
}
