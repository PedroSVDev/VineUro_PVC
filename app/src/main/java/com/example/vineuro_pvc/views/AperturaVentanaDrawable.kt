package com.example.vineuro_pvc.views

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import com.example.vineuro_pvc.data.AperturasMulti

/**
 * Icono de apertura dibujado.
 * Mismos colores (hoja #F5F5F5, vidrio #CDDBEB, trazo #333333 / #111111), mismas proporciones
 * (marco = lado menor / 14, perfil de hoja = lado menor / 10) y mismos símbolos.
 *
 * [mec] = id de mecanismo de la app: "1".."10" (básicas) o "F:/V:/S:<clave>" (francesas, corredizas verticales y corredizas).
 */
class AperturaVentanaDrawable(private val mec: String, densidad: Float = 2f) : Drawable() {

    private enum class Simbolo { FIJO, IZQ, DER, ARRIBA, ABAJO, ARRIBA_IZQ, ARRIBA_DER, DESLIZ_IZQ_E, DESLIZ_DER_E, DESLIZ_IZQ, DESLIZ_DER, DESLIZ_ARRIBA, DESLIZ_ABAJO, IZQ_FIJA, DER_FIJA }

    private val k = (densidad / 2f).coerceAtLeast(1f)
    private val hoja = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xFFF5F5F5.toInt() }
    private val vidrio = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xFFCDDBEB.toInt() }
    private val contorno = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF333333.toInt(); strokeWidth = 1.4f * k }
    private val contornoFino = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF333333.toInt(); strokeWidth = 1.0f * k }
    private val simbolo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF111111.toInt(); strokeWidth = 2.2f * k }
    private val fijadores = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF111111.toInt(); strokeWidth = 3.52f * k }
    private val brazoMax = 25f * densidad
    private val todos = listOf(hoja, vidrio, contorno, contornoFino, simbolo, fijadores)

    private fun rect(c: Canvas, r: RectF, relleno: Paint, borde: Paint) { c.drawRect(r, relleno); c.drawRect(r, borde) }

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        c.save(); c.translate(b.left.toFloat(), b.top.toFloat())
        val w = b.width().toFloat(); val h = b.height().toFloat()
        val m = minOf(w, h)
        val hm = m / 14f         // marco exterior de la hoja
        val g = m / 10f          // perfil de la hoja
        val kk = g + hm          // inicio del vidrio
        val exterior = RectF(hm, hm, w - hm, h - hm)
        rect(c, exterior, hoja, contorno)

        when (AperturasMulti.grupo(mec)) {
            AperturasMulti.Grupo.FRANCESA -> francesa(c, w, h, hm, g, kk)
            AperturasMulti.Grupo.CORREDIZA, AperturasMulti.Grupo.CORREDIZA_VERT -> corredizas(c, w, h, hm, g)
            null -> {
                val vid = RectF(kk, kk, w - kk, h - kk)
                if (mec == "5") rect(c, RectF(kk * 0.51f, kk * 0.51f, w - kk * 0.51f, h - kk * 0.51f), vidrio, contornoFino)
                else rect(c, vid, vidrio, contornoFino)
                dibujarSimbolo(c, exterior, vid, simboloBasico())
            }
        }
        c.restore()
    }

    private fun simboloBasico(): Simbolo = when (mec) {
        "1" -> Simbolo.DESLIZ_DER_E
        "2" -> Simbolo.ABAJO
        "3" -> Simbolo.DESLIZ_IZQ_E
        "4" -> Simbolo.IZQ
        "6" -> Simbolo.DER
        "7" -> Simbolo.ARRIBA_IZQ
        "8" -> Simbolo.ARRIBA_DER
        "9" -> Simbolo.ARRIBA
        else -> Simbolo.FIJO              // "5" sin apertura y "10" hoja fija: cruz
    }

    // ---------- francesas ----------
    private fun francesa(c: Canvas, w: Float, h: Float, hm: Float, g: Float, kk: Float) {
        val mitad = w / 2f
        val vidIzq = RectF(kk, kk, mitad - g, h - kk)
        val vidDer = RectF(mitad + g, kk, w - kk, h - kk)
        rect(c, vidIzq, vidrio, contornoFino); rect(c, vidDer, vidrio, contornoFino)
        c.drawLine(mitad, kk - g, mitad, h - kk + g, contornoFino)
        val hojaIzq = RectF(hm, hm, mitad, h - hm)
        val hojaDer = RectF(mitad, hm, w - hm, h - hm)
        val (si, sd) = when (AperturasMulti.clave(mec)) {
            "LCF_RC" -> Simbolo.DER_FIJA to Simbolo.IZQ
            "LC_RCF" -> Simbolo.DER to Simbolo.IZQ_FIJA
            "LCF_RT" -> Simbolo.DER_FIJA to Simbolo.ARRIBA_IZQ
            else -> Simbolo.ARRIBA_DER to Simbolo.IZQ_FIJA        // "LT_RCF"
        }
        dibujarSimbolo(c, hojaIzq, vidIzq, si)
        dibujarSimbolo(c, hojaDer, vidDer, sd)
    }

    // ---------- corredizas ----------
    private fun corredizas(c: Canvas, w: Float, h: Float, hm: Float, g: Float) {
        val hojas = AperturasMulti.hojas(mec, (w - 2 * hm).toDouble(), (h - 2 * hm).toDouble(), perfil = g.toDouble())
        for (hj in hojas.sortedBy { it.capa }) {       // orden estable por capa: las capas altas tapan a las bajas
            val vid = RectF(hm + hj.x.toFloat(), hm + hj.y.toFloat(), hm + (hj.x + hj.ancho).toFloat(), hm + (hj.y + hj.alto).toFloat())
            val ext = RectF(vid.left - g, vid.top - g, vid.right + g, vid.bottom + g)
            rect(c, ext, hoja, contorno)
            rect(c, vid, vidrio, contorno)
            val s = when (hj.dir) {
                'L' -> Simbolo.DESLIZ_IZQ; 'R' -> Simbolo.DESLIZ_DER
                'U' -> Simbolo.DESLIZ_ARRIBA; 'D' -> Simbolo.DESLIZ_ABAJO
                else -> Simbolo.FIJO
            }
            dibujarSimbolo(c, ext, vid, s)
        }
    }

    // ---------- símbolos ----------
    private fun poli(c: Canvas, vararg p: Pair<Float, Float>) {
        val path = Path(); path.moveTo(p[0].first, p[0].second)
        for (q in p) path.lineTo(q.first, q.second)
        c.drawPath(path, simbolo)
    }

    private fun dibujarSimbolo(c: Canvas, hoja: RectF, v: RectF, s: Simbolo) {
        val l = v.left; val t = v.top; val r = v.right; val b = v.bottom
        val cx = v.centerX(); val cy = v.centerY()
        val m = minOf(v.width(), v.height()) / 8f
        val lineaH = floatArrayOf(cx - 3 * m, cy, cx + 3 * m, cy)
        val lineaV = floatArrayOf(cx, cy - 3 * m, cx, cy + 3 * m)
        val flechaIzq = floatArrayOf(cx - m, cy - 2 * m, cx - 3 * m, cy, cx - 3 * m, cy, cx - m, cy + 2 * m)
        val flechaDer = floatArrayOf(cx + m, cy - 2 * m, cx + 3 * m, cy, cx + 3 * m, cy, cx + m, cy + 2 * m)
        val flechaArr = floatArrayOf(cx - 2 * m, cy - m, cx, cy - 3 * m, cx, cy - 3 * m, cx + 2 * m, cy - m)
        val flechaAba = floatArrayOf(cx - 2 * m, cy + m, cx, cy + 3 * m, cx, cy + 3 * m, cx + 2 * m, cy + m)
        fun pintar(vararg a: FloatArray) { for (x in a) c.drawLines(x, simbolo) }
        when (s) {
            Simbolo.FIJO -> {
                var a = brazoMax; val mn = minOf(v.width(), v.height()) * 0.7f
                if (2 * a >= mn) a = mn / 2f
                c.drawLine(cx, cy - a, cx, cy + a, simbolo); c.drawLine(cx - a, cy, cx + a, cy, simbolo)
            }
            Simbolo.IZQ -> poli(c, r to t, l to cy, r to b)
            Simbolo.DER -> poli(c, l to t, r to cy, l to b)
            Simbolo.ARRIBA -> poli(c, l to b, cx to t, r to b)
            Simbolo.ABAJO -> poli(c, l to t, cx to b, r to t)
            Simbolo.ARRIBA_IZQ -> { poli(c, l to b, cx to t, r to b); poli(c, r to t, l to cy, r to b) }
            Simbolo.ARRIBA_DER -> { poli(c, l to b, cx to t, r to b); poli(c, l to t, r to cy, l to b) }
            Simbolo.DESLIZ_IZQ_E -> pintar(flechaIzq, lineaH, floatArrayOf(cx + 3 * m, cy, cx + 3 * m, cy + 2 * m))
            Simbolo.DESLIZ_DER_E -> pintar(flechaDer, lineaH, floatArrayOf(cx - 3 * m, cy, cx - 3 * m, cy + 2 * m))
            Simbolo.DESLIZ_IZQ -> pintar(flechaIzq, lineaH)
            Simbolo.DESLIZ_DER -> pintar(flechaDer, lineaH)
            Simbolo.DESLIZ_ARRIBA -> pintar(flechaArr, lineaV)
            Simbolo.DESLIZ_ABAJO -> pintar(flechaAba, lineaV)
            Simbolo.IZQ_FIJA -> { poli(c, r to t, l to cy, r to b); c.drawLines(fijadoresFrancesa(hoja, v, false), fijadores) }
            Simbolo.DER_FIJA -> { poli(c, l to t, r to cy, l to b); c.drawLines(fijadoresFrancesa(hoja, v, true), fijadores) }
        }
    }

    /** Los "pestillos" de la hoja pasiva de la puerta francesa. */
    private fun fijadoresFrancesa(hoja: RectF, v: RectF, derecha: Boolean): FloatArray {
        val f12 = v.left - hoja.left
        val f13 = 0.25f * f12
        val f14 = 1.4f * f12
        val f15 = hoja.left + f14
        val f16 = hoja.right - f14
        val f17 = 0.3f * f12
        val f18 = hoja.top + f17
        val f19 = f12 * 1.6f
        val f20 = hoja.top + f19
        val f21 = hoja.bottom - f19
        val f22 = hoja.bottom - f17
        return if (!derecha) {
            val f23 = f15 + f13
            floatArrayOf(f15, f18, f15, f20, f15, f21, f15, f22, f15, f20, f23, f20, f15, f21, f23, f21)
        } else {
            val f24 = f16 - f13
            floatArrayOf(f16, f18, f16, f20, f16, f21, f16, f22, f24, f20, f16, f20, f24, f21, f16, f21)
        }
    }

    override fun setAlpha(alpha: Int) { for (p in todos) p.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { for (p in todos) p.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
