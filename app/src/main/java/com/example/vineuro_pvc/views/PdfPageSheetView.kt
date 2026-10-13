package com.example.vineuro_pvc.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.example.vineuro_pvc.data.OrderItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Custom View que dibuja una hoja A4 completa con el formato oficial de la orden de proyectos:
 * - Encabezado (Ejecutante, Logo, Cliente con formateo anti-solapamiento)
 * - Cuadrícula 2x3 para hasta 6 proyectos por página con sus cotas de medidas
 * - Pie de página con resumen de toda la orden, firmas y fecha.
 */
class PdfPageSheetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private fun dpToPx(dp: Float): Float = dp * context.resources.displayMetrics.density

    // Paints
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1f)
    }

    private val gridBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAAAAA")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(0.8f)
    }

    private val textSecondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#444444")
        textSize = dpToPx(7.5f)
    }

    private val textBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = dpToPx(8f)
        isFakeBoldText = true
    }

    private val headerGradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EBF5FA")
        style = Paint.Style.FILL
    }

    private var pageIndex: Int = 0
    private var totalPages: Int = 1
    private var pageItems: List<OrderItem> = emptyList()
    private var clientName: String = "Kafe Swan"
    private var clientAddress: String = "5 Green Avenue"
    private var clientPhone: String = "123-123-123"
    private var clientEmail: String = "aistmail@mail.com"
    private var offerId: String = ""
    private var totalOrderCount: Int = 0
    private var totalOrderAreaM2: Double = 0.0
    private var requisitos: com.example.vineuro_pvc.data.RequisitosDocumento = com.example.vineuro_pvc.data.RequisitosDocumento()
    private var conDetalles: Boolean = false

    private val windowPreviewHelper = WindowPreviewView(context)

    /** Logo de la empresa (se carga una sola vez). */
    private val logoBitmap: android.graphics.Bitmap by lazy {
        com.example.vineuro_pvc.data.LogoEmpresa.cargar(context)
    }
    private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun bindData(
        pageIndex: Int,
        totalPages: Int,
        pageItems: List<OrderItem>,
        clientName: String,
        clientAddress: String = "5 Green Avenue",
        clientPhone: String = "123-123-123",
        clientEmail: String = "aistmail@mail.com",
        offerId: String = "",
        totalOrderCount: Int = 0,
        totalOrderAreaM2: Double = 0.0,
        requisitos: com.example.vineuro_pvc.data.RequisitosDocumento = com.example.vineuro_pvc.data.RequisitosDocumento(),
        conDetalles: Boolean = false,
    ) {
        this.conDetalles = conDetalles
        this.requisitos = requisitos
        this.pageIndex = pageIndex
        this.totalPages = totalPages
        this.pageItems = pageItems
        this.clientName = clientName
        this.clientAddress = clientAddress
        this.clientPhone = clientPhone
        this.clientEmail = clientEmail
        this.offerId = offerId
        this.totalOrderCount = totalOrderCount
        this.totalOrderAreaM2 = totalOrderAreaM2
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        // Proporción A4 aproximada 1 : 1.414
        val targetHeight = (width * 1.414f).toInt()
        setMeasuredDimension(width, targetHeight)
    }

    private fun drawTruncatedText(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint, maxW: Float) {
        if (paint.measureText(text) <= maxW) {
            canvas.drawText(text, x, y, paint)
        } else {
            var sub = text
            while (sub.length > 2 && paint.measureText("$sub...") > maxW) {
                sub = sub.substring(0, sub.length - 1)
            }
            canvas.drawText("$sub...", x, y, paint)
        }
    }

    /** Lista de propiedades de la ventana (categoría sombreada + valores), recortada a su recuadro. */
    private fun dibujarPropiedades(canvas: Canvas, item: OrderItem, caja: RectF) {
        val cats = com.example.vineuro_pvc.data.TextoPropiedades.categorias(item)
        if (cats.isEmpty()) return
        val tam = dpToPx(5.6f)
        val pCat = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = tam; isFakeBoldText = true }
        val pTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = tam }
        val fondo = Paint().apply { color = Color.parseColor("#DCDCDD"); style = Paint.Style.FILL }
        val alto = tam * 1.25f
        canvas.save()
        canvas.clipRect(caja)
        var y = caja.top
        for (c in cats) {
            if (y + alto > caja.bottom) break
            canvas.drawRect(caja.left, y, caja.right, y + alto, fondo)
            drawTruncatedText(canvas, c.titulo, caja.left + dpToPx(2f), y + tam, pCat, caja.width() - dpToPx(4f))
            y += alto
            for (l in c.lineas) {
                if (y + alto > caja.bottom) break
                drawTruncatedText(canvas, "$l", caja.left + dpToPx(4f), y + tam, pTxt, caja.width() - dpToPx(6f))
                y += alto
            }
        }
        canvas.restore()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // 1. Fondo de la hoja blanca y borde exterior
        val pageRect = RectF(0f, 0f, w, h)
        canvas.drawRect(pageRect, bgPaint)
        canvas.drawRect(pageRect, borderPaint)

        val margin = dpToPx(8f)
        val contentW = w - (margin * 2)

        // 2. Sección Encabezado (Header)
        val headerH = dpToPx(78f)
        val headerRect = RectF(margin, margin, margin + contentW, margin + headerH)
        
        canvas.drawRect(headerRect, headerGradientPaint)
        canvas.drawRect(headerRect, gridBorderPaint)

        val colW = contentW / 3f
        val maxHeaderTextW = colW - dpToPx(10f)
        val lineH = dpToPx(12.5f)

        // Izquierda: EJECUTANTE
        var yPos = margin + dpToPx(13f)
        val leftX = margin + dpToPx(6f)

        textBoldPaint.textSize = dpToPx(7.5f)
        textSecondaryPaint.textSize = dpToPx(7.5f)

        // Datos del ejecutante: salen de "Configurar requisitos" (editables por la empresa)
        fun v(s: String) = s.ifBlank { "......" }
        val lineEj = dpToPx(10.8f)
        drawTruncatedText(canvas, "EJECUTANTE : ${v(requisitos.ejecutante)}", leftX, yPos, textBoldPaint, maxHeaderTextW)
        yPos += lineEj
        drawTruncatedText(canvas, "RUC : ${v(requisitos.ruc)}", leftX, yPos, textSecondaryPaint, maxHeaderTextW)
        yPos += lineEj
        drawTruncatedText(canvas, "dirección : ${v(requisitos.direccion)}", leftX, yPos, textSecondaryPaint, maxHeaderTextW)
        yPos += lineEj
        drawTruncatedText(canvas, "teléfono : ${v(requisitos.telefono)}", leftX, yPos, textSecondaryPaint, maxHeaderTextW)
        yPos += lineEj
        drawTruncatedText(canvas, "correo electrónico : ${v(requisitos.correo)}", leftX, yPos, textSecondaryPaint, maxHeaderTextW)
        yPos += lineEj
        drawTruncatedText(canvas, "página web : ${v(requisitos.paginaWeb)}", leftX, yPos, textSecondaryPaint, maxHeaderTextW)

        // Centro: Logo de VINEURO PVC (imagen res/drawable-nodpi/logo_vineuro.png)
        val logoCenterX = margin + colW * 1.5f
        val logoMaxW = colW - dpToPx(8f)
        val logoMaxH = headerH - dpToPx(22f) // deja espacio para la cabecera debajo
        val logoW: Float
        val logoH: Float
        if (logoBitmap.width / logoBitmap.height.toFloat() > logoMaxW / logoMaxH) {
            logoW = logoMaxW; logoH = logoMaxW * logoBitmap.height / logoBitmap.width
        } else {
            logoH = logoMaxH; logoW = logoMaxH * logoBitmap.width / logoBitmap.height
        }
        val logoTop = margin + dpToPx(4f)
        val logoRect = RectF(logoCenterX - logoW / 2f, logoTop, logoCenterX + logoW / 2f, logoTop + logoH)
        canvas.drawBitmap(logoBitmap, null, logoRect, logoPaint)

        // Cabecera del documento (p. ej. "OFERTA COMERCIAL") debajo del logo
        val tituloPaint = Paint(textBoldPaint).apply {
            textSize = dpToPx(8.5f)
            textAlign = Paint.Align.CENTER
            color = Color.parseColor("#1E3A5F")
        }
        var titulo = requisitos.cabecera.ifBlank { "Oferta comercial" }.uppercase(Locale("es", "PE"))
        while (titulo.length > 3 && tituloPaint.measureText(titulo) > colW - dpToPx(6f)) titulo = titulo.dropLast(1)
        canvas.drawText(titulo, logoCenterX, margin + headerH - dpToPx(7f), tituloPaint)

        // Derecha: CLIENTE
        val rightX = margin + colW * 2f + dpToPx(6f)
        var yPosRight = margin + dpToPx(13f)

        drawTruncatedText(canvas, "CLIENTE : $clientName", rightX, yPosRight, textBoldPaint, maxHeaderTextW)
        yPosRight += lineH
        drawTruncatedText(canvas, "dirección : $clientAddress", rightX, yPosRight, textSecondaryPaint, maxHeaderTextW)
        yPosRight += lineH
        drawTruncatedText(canvas, "teléfono : $clientPhone", rightX, yPosRight, textSecondaryPaint, maxHeaderTextW)
        yPosRight += lineH
        drawTruncatedText(canvas, "correo electrónico : $clientEmail", rightX, yPosRight, textSecondaryPaint, maxHeaderTextW)
        yPosRight += lineH
        drawTruncatedText(canvas, "ID de oferta : ${offerId.ifEmpty { "......" }}", rightX, yPosRight, textSecondaryPaint, maxHeaderTextW)

        // 3. Sección Cuadrícula de Proyectos (2 Columnas x 3 Filas = 6 por página)
        val gridTop = margin + headerH + dpToPx(6f)
        val footerH = dpToPx(85f)
        val gridH = h - gridTop - footerH - margin

        val cellW = contentW / 2f
        val cellH = gridH / 3f

        for (row in 0 until 3) {
            for (col in 0 until 2) {
                val itemIndexInPage = row * 2 + col
                val cellLeft = margin + col * cellW
                val cellTop = gridTop + row * cellH
                val cellRight = cellLeft + cellW
                val cellBottom = cellTop + cellH

                val cellRect = RectF(cellLeft, cellTop, cellRight, cellBottom)
                canvas.drawRect(cellRect, gridBorderPaint)

                if (itemIndexInPage < pageItems.size) {
                    val item = pageItems[itemIndexInPage]

                    // Número del ítem (1, 2, 3...)
                    val numX = cellLeft + dpToPx(8f)
                    val numY = cellTop + dpToPx(15f)
                    textBoldPaint.textSize = dpToPx(11f)
                    canvas.drawText("${item.index}", numX, numY, textBoldPaint)

                    // Cantidad de piezas del pedido (solo si es 2 o más, como en el original)
                    textSecondaryPaint.textSize = dpToPx(8f)
                    if (item.opciones.cantidad >= 2) {
                        val countText = "cantidad ${item.opciones.cantidad}pc"
                        val countX = cellRight - dpToPx(8f) - textSecondaryPaint.measureText(countText)
                        canvas.drawText(countText, countX, numY, textSecondaryPaint)
                    }

                    // Dibujo técnico de la ventana
                    val drawMarginH = dpToPx(3f)
                    val drawMarginV = dpToPx(14f)
                    val drawBounds = RectF(
                        cellLeft + drawMarginH,
                        cellTop + drawMarginV,
                        cellRight - drawMarginH,
                        cellBottom - dpToPx(2f)
                    )
                    // Con detalles: el dibujo ocupa la parte alta y debajo va la lista de propiedades
                    if (conDetalles) drawBounds.bottom = cellTop + (cellBottom - cellTop) * 0.52f

                    windowPreviewHelper.drawItem(canvas, drawBounds, item, showTechnicalDetails = true, isCompactMode = true)

                    if (conDetalles) {
                        val top = drawBounds.bottom + dpToPx(2f)
                        dibujarPropiedades(canvas, item, RectF(cellLeft + dpToPx(3f), top, cellRight - dpToPx(3f), cellBottom - dpToPx(2f)))
                    }
                    // Nota de la ventana (el original la muestra si no está vacía)
                    if (item.opciones.nota.isNotBlank()) {
                        textSecondaryPaint.textSize = dpToPx(6.5f)
                        drawTruncatedText(canvas, item.opciones.nota, cellLeft + dpToPx(22f), numY, textSecondaryPaint, cellW - dpToPx(90f))
                    }
                }
            }
        }

        // 4. Sección Pie de Página (Footer Table & Signatures)
        val footerTop = h - footerH - margin
        val footerRect = RectF(margin, footerTop, margin + contentW, footerTop + footerH)
        canvas.drawRect(footerRect, gridBorderPaint)

        // Fila 1 del footer: Encabezado de la tabla de totales
        val headerTableH = dpToPx(16f)
        val tableHeaderRect = RectF(margin, footerTop, margin + contentW, footerTop + headerTableH)
        canvas.drawRect(tableHeaderRect, headerGradientPaint)
        canvas.drawLine(margin, footerTop + headerTableH, margin + contentW, footerTop + headerTableH, gridBorderPaint)

        // Columnas del footer
        val footCol1W = contentW * 0.42f
        val footCol2W = contentW * 0.33f

        val x1 = margin
        val x2 = x1 + footCol1W
        val x3 = x2 + footCol2W

        // Dibujar divisiones verticales del footer
        canvas.drawLine(x2, footerTop, x2, footerTop + footerH, gridBorderPaint)
        canvas.drawLine(x3, footerTop, x3, footerTop + footerH, gridBorderPaint)

        // Textos del encabezado del footer
        textBoldPaint.textSize = dpToPx(8f)
        textSecondaryPaint.textSize = dpToPx(7.5f)

        canvas.drawText("toda la orden", x1 + dpToPx(6f), footerTop + dpToPx(11.5f), textBoldPaint)
        canvas.drawText("Firmas", x2 + dpToPx(6f), footerTop + dpToPx(11.5f), textBoldPaint)

        val currentDateStr = SimpleDateFormat("dd MMM yyyy", Locale("es", "ES")).format(Date())
        canvas.drawText(currentDateStr, x3 + dpToPx(6f), footerTop + dpToPx(11.5f), textSecondaryPaint)

        // Contenido Columna 1: Totales de la Orden
        val row1Y = footerTop + headerTableH + dpToPx(16f)
        canvas.drawText("cantidad", x1 + dpToPx(6f), row1Y, textSecondaryPaint)
        canvas.drawText("${totalOrderCount.coerceAtLeast(pageItems.size)}", x1 + footCol1W * 0.65f, row1Y, textBoldPaint)
        canvas.drawText("pc", x1 + footCol1W * 0.85f, row1Y, textSecondaryPaint)

        val lineY1 = footerTop + headerTableH + dpToPx(23f)
        canvas.drawLine(x1, lineY1, x2, lineY1, gridBorderPaint)

        val row2Y = lineY1 + dpToPx(16f)
        canvas.drawText("toda el área de productos", x1 + dpToPx(6f), row2Y, textSecondaryPaint)
        val areaFormatted = String.format(Locale.US, "%.2f", totalOrderAreaM2)
        canvas.drawText(areaFormatted, x1 + footCol1W * 0.65f, row2Y, textBoldPaint)
        canvas.drawText("m2", x1 + footCol1W * 0.85f, row2Y, textSecondaryPaint)

        // Contenido Columna 2: Firmas
        val sig1Y = footerTop + headerTableH + dpToPx(18f)
        canvas.drawText("Ejecutante", x2 + dpToPx(6f), sig1Y, textSecondaryPaint)

        val sigLine1Y = footerTop + headerTableH + dpToPx(28f)
        canvas.drawLine(x2 + dpToPx(55f), sigLine1Y, x3 - dpToPx(10f), sigLine1Y, gridBorderPaint)

        val sig2Y = sigLine1Y + dpToPx(20f)
        canvas.drawText("Cliente", x2 + dpToPx(6f), sig2Y, textSecondaryPaint)

        val sigLine2Y = sig2Y + dpToPx(10f)
        canvas.drawLine(x2 + dpToPx(55f), sigLine2Y, x3 - dpToPx(10f), sigLine2Y, gridBorderPaint)
    }
}
