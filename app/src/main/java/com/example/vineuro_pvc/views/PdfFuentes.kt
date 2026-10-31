package com.example.vineuro_pvc.views

import android.content.Context
import android.graphics.Typeface

/**
 * Letras de los PDF: Carlito (licencia libre OFL, mismas medidas que Calibri), incluida en assets/fonts
 * en sus versiones Regular, Bold e Italic, para que las negritas sean negritas reales (no "falsas").
 * Si por algún motivo no se puede cargar, se usa la letra del sistema.
 */
object PdfFuentes {

    private const val CARPETA = "fonts"

    @Volatile private var cargado = false
    private var regular: Typeface? = null
    private var negrita: Typeface? = null
    private var cursiva: Typeface? = null

    /** Carga las letras una sola vez (las siguientes llamadas no hacen nada). */
    fun cargar(context: Context) {
        if (cargado) return
        synchronized(this) {
            if (cargado) return
            val assets = context.applicationContext.assets
            fun leer(archivo: String): Typeface? =
                runCatching { Typeface.createFromAsset(assets, "$CARPETA/$archivo") }.getOrNull()
            regular = leer("Carlito-Regular.ttf")
            negrita = leer("Carlito-Bold.ttf")
            cursiva = leer("Carlito-Italic.ttf")
            cargado = true
        }
    }

    /** Letra a usar; si Carlito no cargó, la del sistema (con su negrita / cursiva). */
    fun tipo(negrita: Boolean = false, cursiva: Boolean = false): Typeface = when {
        cursiva -> this.cursiva ?: Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
        negrita -> this.negrita ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        else -> regular ?: Typeface.SANS_SERIF
    }
}
