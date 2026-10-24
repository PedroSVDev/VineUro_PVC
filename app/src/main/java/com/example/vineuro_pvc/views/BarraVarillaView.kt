package com.example.vineuro_pvc.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.example.vineuro_pvc.data.OptimizadorCorte

/**
 * Dibuja una varilla de perfil a escala: cada pieza con su medida (mm) y el N° de ventana, y al final el sobrante.
 * El color de cada pieza identifica a su ventana (ver [colorVentana]).
 */
class BarraVarillaView(context: Context) : View(context) {

    private val dp = resources.displayMetrics.density
    private var varilla: OptimizadorCorte.Varilla? = null

    private val relleno = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borde = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f * dp; color = Color.parseColor("#6B7280") }
    private val medida = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; textSize = 11f * dp; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    private val numero = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#374151"); textSize = 9f * dp; textAlign = Paint.Align.CENTER
    }

    fun mostrar(v: OptimizadorCorte.Varilla) {
        varilla = v
        requestLayout(); invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize(suggestedMinimumWidth, widthMeasureSpec), (40 * dp).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val v = varilla ?: return
        val pad = 1f * dp
        val ancho = width - 2 * pad
        val alto = height - 2 * pad
        val esc = ancho / v.largoMm
        var x = pad
        for (pz in v.piezas) {
            val w = pz.largoMm * esc
            val r = RectF(x, pad, x + w, pad + alto)
            relleno.color = colorVentana(pz.ventana)
            canvas.drawRoundRect(r, 3f * dp, 3f * dp, relleno)
            canvas.drawRoundRect(r, 3f * dp, 3f * dp, borde)
            etiqueta(canvas, "${pz.largoMm}", "V${pz.ventana}${if (pz.copia > 1) "·${pz.copia}" else ""}", r)
            x += w + v.separacionMm * esc
        }
        if (x < pad + ancho - 1f) {
            val r = RectF(x, pad, pad + ancho, pad + alto)
            val util = v.sobranteMm >= 500
            relleno.color = if (util) Color.parseColor("#DCFCE7") else Color.parseColor("#F3F4F6")
            canvas.drawRoundRect(r, 3f * dp, 3f * dp, relleno)
            canvas.drawRoundRect(r, 3f * dp, 3f * dp, borde)
            etiqueta(canvas, "sobra ${v.sobranteMm}", null, r)
        }
    }

    private fun etiqueta(canvas: Canvas, arriba: String, abajo: String?, r: RectF) {
        val cx = r.centerX()
        if (medida.measureText(arriba) + 4 * dp <= r.width()) {
            canvas.drawText(arriba, cx, r.centerY() - (if (abajo != null) 1f * dp else -4f * dp), medida)
            if (abajo != null && numero.measureText(abajo) + 2 * dp <= r.width()) canvas.drawText(abajo, cx, r.bottom - 5f * dp, numero)
        } else if (abajo != null && numero.measureText(abajo) + 2 * dp <= r.width()) {
            canvas.drawText(abajo, cx, r.centerY() + 3f * dp, numero)
        }
    }

    companion object {
        private val PALETA: IntArray = listOf(
            "#9FC5E8", "#B6D7A8", "#F9CB9C", "#D5A6BD", "#FFE599", "#B4A7D6", "#A2C4C9", "#EA9999",
        ).map { Color.parseColor(it) }.toIntArray()

        /** Mismo color para todas las piezas de una ventana (se repite cada 8). */
        fun colorVentana(ventana: Int): Int = PALETA[(ventana - 1).coerceAtLeast(0) % PALETA.size]
    }
}
