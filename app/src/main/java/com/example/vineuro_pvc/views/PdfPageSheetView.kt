package com.example.vineuro_pvc.views

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

/**
 * Vista previa de una hoja A4 del PDF de proyectos.
 * Dibuja exactamente lo mismo que el archivo (ProyectosPdf), escalado al ancho de la pantalla.
 */
class PdfPageSheetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var datos: ProyectosPdf.Datos? = null
    private var modo = ProyectosPdf.Modo.SOLO_PROYECTOS
    private var paginas: List<List<ProyectosPdf.Elemento>> = emptyList()
    private var pagina = 0

    fun mostrar(datos: ProyectosPdf.Datos, modo: ProyectosPdf.Modo, paginas: List<List<ProyectosPdf.Elemento>>, pagina: Int) {
        this.datos = datos
        this.modo = modo
        this.paginas = paginas
        this.pagina = pagina
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val ancho = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(ancho, (ancho * ProyectosPdf.H / ProyectosPdf.W).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = datos ?: return
        val escala = width / ProyectosPdf.W
        canvas.save()
        canvas.scale(escala, escala)
        ProyectosPdf.dibujarPagina(context, canvas, d, modo, paginas, pagina)
        canvas.restore()
    }
}
