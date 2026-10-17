package com.example.vineuro_pvc.views

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * Ventanita de la imposta interior, como IconsGroupLeafImposts: marco blanco y vidrios azules.
 * [imp]: 0 ninguna, 1 horizontal x1 (2 vidrios apilados), 2 horizontal x2 (3), 3 vertical x1 (2 lado a lado), 4 vertical x2 (3).
 * [mascara]: bit i = vidrio i es panel sándwich (se dibuja gris con rayas). [barras] = icono del botón glazing bars (rejilla).
 */
class ImpostaIconDrawable(
    private val imp: Int,
    private val mascara: Int = 0,
    private val barras: Boolean = false,
) : Drawable() {
    private val marco = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xFFFFFFFF.toInt() }
    private val vidrio = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xFF4FC3F7.toInt() }
    private val sandwich = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xFFCFD8DC.toInt() }
    private val trazo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF37474F.toInt() }
    private val rayas = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF90A4AE.toInt() }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val lado = minOf(b.width(), b.height()) * 0.9f
        val ox = b.exactCenterX() - lado / 2f
        val oy = b.exactCenterY() - lado / 2f
        val e = lado / 40f
        trazo.strokeWidth = maxOf(1.5f, e); rayas.strokeWidth = maxOf(1f, e * 0.7f)
        val ext = RectF(ox, oy, ox + lado, oy + lado)
        canvas.drawRect(ext, marco); canvas.drawRect(ext, trazo)
        val m = lado * 0.12f
        val inn = RectF(ext.left + m, ext.top + m, ext.right - m, ext.bottom - m)
        if (barras) {
            canvas.drawRect(inn, vidrio); canvas.drawRect(inn, trazo)
            for (k in 1..2) {
                val x = inn.left + inn.width() * k / 3f; val y = inn.top + inn.height() * k / 3f
                canvas.drawLine(x, inn.top, x, inn.bottom, trazo); canvas.drawLine(inn.left, y, inn.right, y, trazo)
            }
            return
        }
        val n = when (imp) { 1, 3 -> 2; 2, 4 -> 3; else -> 1 }
        val horiz = imp == 1 || imp == 2 // impostas horizontales => vidrios apilados
        val gap = if (n > 1) lado * 0.07f else 0f
        for (i in 0 until n) {
            val r = if (horiz) {
                val h = (inn.height() - gap * (n - 1)) / n
                RectF(inn.left, inn.top + i * (h + gap), inn.right, inn.top + i * (h + gap) + h)
            } else {
                val w = (inn.width() - gap * (n - 1)) / n
                RectF(inn.left + i * (w + gap), inn.top, inn.left + i * (w + gap) + w, inn.bottom)
            }
            val sand = ((mascara shr i) and 1) == 1
            canvas.drawRect(r, if (sand) sandwich else vidrio)
            if (sand) {
                canvas.save(); canvas.clipRect(r)
                var d = -r.height()
                while (d < r.width()) { canvas.drawLine(r.left + d, r.bottom, r.left + d + r.height(), r.top, rayas); d += lado / 7f }
                canvas.restore()
            }
            canvas.drawRect(r, trazo)
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
