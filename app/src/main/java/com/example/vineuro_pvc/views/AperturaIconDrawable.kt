package com.example.vineuro_pvc.views

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import com.example.vineuro_pvc.data.AperturasMulti

/** Icono esquemático de una apertura de varias hojas (corrediza múltiple, vertical o puerta francesa), dibujado con sus hojas. */
class AperturaIconDrawable(private val mec: String) : Drawable() {
    private val trazo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF222222.toInt(); strokeWidth = 2f }
    private val relleno = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xFFCDE7F5.toInt() }
    private val fija = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xFFB0B0B0.toInt() }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val lado = minOf(b.width(), b.height()) * 0.86f
        val ancho = 1.0
        val alto = 0.9
        val k = lado / ancho.toFloat()
        val ox = b.exactCenterX() - lado / 2f
        val oy = b.exactCenterY() - (alto * k).toFloat() / 2f
        trazo.strokeWidth = maxOf(1.5f, lado / 40f)
        canvas.drawRect(ox, oy, ox + lado, oy + (alto * k).toFloat(), trazo)
        for (h in AperturasMulti.hojas(mec, ancho, alto)) {
            val gl = ox + (h.x * k).toFloat(); val gt = oy + (h.y * k).toFloat()
            val gr = gl + (h.ancho * k).toFloat(); val gb = gt + (h.alto * k).toFloat()
            val pf = (h.perfil * k).toFloat()
            canvas.drawRect(gl, gt, gr, gb, if (h.movil) relleno else fija)
            canvas.drawRect(gl - pf, gt - pf, gr + pf, gb + pf, trazo)
            val cx = (gl + gr) / 2f; val cy = (gt + gb) / 2f
            when {
                AperturasMulti.grupo(mec) == AperturasMulti.Grupo.FRANCESA -> {
                    if (h.dir == 'R') { canvas.drawLine(gr, gt, gl, cy, trazo); canvas.drawLine(gr, gb, gl, cy, trazo) }
                    else { canvas.drawLine(gl, gt, gr, cy, trazo); canvas.drawLine(gl, gb, gr, cy, trazo) }
                }
                h.movil -> {
                    val (dx, dy) = when (h.dir) { 'L' -> -1f to 0f; 'R' -> 1f to 0f; 'U' -> 0f to -1f; else -> 0f to 1f }
                    val l = (if (dx != 0f) gr - gl else gb - gt) * 0.35f
                    val x2 = cx + dx * l; val y2 = cy + dy * l
                    canvas.drawLine(cx - dx * l, cy - dy * l, x2, y2, trazo)
                    val c = lado / 14f
                    canvas.drawLine(x2, y2, x2 - dx * c - dy * c, y2 - dy * c + dx * c, trazo)
                    canvas.drawLine(x2, y2, x2 - dx * c + dy * c, y2 - dy * c - dx * c, trazo)
                }
            }
        }
    }

    override fun setAlpha(alpha: Int) { trazo.alpha = alpha; relleno.alpha = alpha; fija.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { trazo.colorFilter = colorFilter; relleno.colorFilter = colorFilter; fija.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
