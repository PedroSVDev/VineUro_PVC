package com.example.vineuro_pvc.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/**
 * TextView personalizado que permite dibujar un borde (stroke) alrededor del texto.
 * Ideal para el efecto de texto blanco con delineado negro.
 */
class StrokeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var strokeWidth = 4f
    private var strokeColor = Color.BLACK
    private var textColor = Color.WHITE

    override fun onDraw(canvas: Canvas) {
        val states = textColors
        
        // 1. Dibujar el borde (Stroke)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        setTextColor(strokeColor)
        super.onDraw(canvas)

        // 2. Dibujar el texto interior (Fill)
        paint.style = Paint.Style.FILL
        setTextColor(textColor)
        super.onDraw(canvas)
    }
}
