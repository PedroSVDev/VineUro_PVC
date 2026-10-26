package com.example.vineuro_pvc.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Vista previa de una hoja A4 de PDF. No tiene diseño propio: recibe la MISMA función que dibuja la hoja en el
 * archivo (en puntos, 595 x 842) y la dibuja escalada al ancho de la pantalla, así la vista previa y el PDF
 * son idénticos. La hoja se pinta una vez en un bitmap y se reutiliza en cada redibujado.
 */
class PdfPaginaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var dibujante: ((Canvas) -> Unit)? = null
    private var hoja: Bitmap? = null
    private val bordePaint = Paint().apply { color = Color.parseColor("#9CA3AF"); style = Paint.Style.STROKE; strokeWidth = 1f }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /** Muestra la hoja que dibuja [dibujar] (en coordenadas de la hoja: 595 x 842 puntos). */
    fun mostrar(dibujar: (Canvas) -> Unit) {
        dibujante = dibujar
        liberar()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val ancho = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(ancho, (ancho * ProyectosPdf.H / ProyectosPdf.W).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        liberar()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val dib = dibujante ?: return
        val bmp = hoja ?: runCatching {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { b ->
                val c = Canvas(b)
                val escala = width / ProyectosPdf.W
                c.scale(escala, escala)
                dib(c)
            }
        }.onFailure { it.printStackTrace() }.getOrNull()?.also { hoja = it } ?: return
        canvas.drawBitmap(bmp, 0f, 0f, bitmapPaint)
        canvas.drawRect(0.5f, 0.5f, width - 0.5f, height - 0.5f, bordePaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        liberar()
    }

    private fun liberar() {
        hoja?.recycle()
        hoja = null
    }
}
