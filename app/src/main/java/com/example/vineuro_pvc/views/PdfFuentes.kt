package com.example.vineuro_pvc.views

import android.content.Context
import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan

/**
 * Letra de los PDF para el cliente: Carlito (libre, licencia SIL OFL), con las mismas medidas
 * que Calibri, la letra habitual de las cotizaciones hechas en Excel / Word.
 * Los archivos están en assets/fonts. Si no se pudieran leer, se usa la letra del sistema.
 */
object PdfFuentes {

    private var normal: Typeface? = null
    private var negrita: Typeface? = null
    private var cursiva: Typeface? = null

    fun cargar(context: Context) {
        if (normal != null) return
        val assets = context.applicationContext.assets
        fun leer(archivo: String): Typeface? = runCatching { Typeface.createFromAsset(assets, "fonts/$archivo") }.getOrNull()
        normal = leer("Carlito-Regular.ttf") ?: Typeface.SANS_SERIF
        negrita = leer("Carlito-Bold.ttf") ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        cursiva = leer("Carlito-Italic.ttf") ?: Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
    }

    fun tipo(esNegrita: Boolean, esCursiva: Boolean = false): Typeface = when {
        esNegrita && esCursiva -> Typeface.create(negrita ?: Typeface.SANS_SERIF, Typeface.ITALIC)
        esNegrita -> negrita ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        esCursiva -> cursiva ?: Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
        else -> normal ?: Typeface.SANS_SERIF
    }

    /** Para poner en negrita una parte de un párrafo con la misma letra. */
    class NegritaSpan : MetricAffectingSpan() {
        override fun updateDrawState(tp: TextPaint) { tp.typeface = tipo(true) }
        override fun updateMeasureState(tp: TextPaint) { tp.typeface = tipo(true) }
    }
}
