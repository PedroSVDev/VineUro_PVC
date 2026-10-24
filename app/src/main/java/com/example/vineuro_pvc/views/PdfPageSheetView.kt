package com.example.vineuro_pvc.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.example.vineuro_pvc.data.OrderItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Fila de una tabla de importes del PDF: etiqueta | valor | unidad ("área de productos | 2.25 | m2"). */
data class FilaTabla(val etiqueta: String, val valor: String, val unidad: String, val grande: Boolean = false)

/**
 * Hoja A4 con el formato:
 *  - Proyectos / con detalles: 6 celdas por página (2 x 3).
 *  - Oferta comercial: 2 celdas por página, con lista de propiedades arriba a la derecha y tabla de precios abajo a la derecha.
 *  - Oferta comercial 2: 1 celda por página.
 * Encabezado solo en la primera página (etiquetas alineadas a la derecha, banner con el logo) y pie solo en la última
 * (toda la orden, firmas y fecha). Todas las medidas se expresan en "unidades de hoja" (400 = ancho de la hoja).
 */
class PdfPageSheetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var pageIndex: Int = 0
    private var totalPages: Int = 1
    private var pageItems: List<OrderItem> = emptyList()
    private var clientName: String = ""
    private var clientAddress: String = ""
    private var clientPhone: String = ""
    private var clientEmail: String = ""
    private var offerId: String = ""
    private var totalOrderCount: Int = 0
    private var totalOrderAreaM2: Double = 0.0
    private var requisitos: com.example.vineuro_pvc.data.RequisitosDocumento = com.example.vineuro_pvc.data.RequisitosDocumento()
    private var conDetalles: Boolean = false
    /** 0 = proyectos (6 por hoja), 2 = oferta comercial (2 por hoja), 1 = oferta comercial 2 (1 por hoja). */
    private var ofertaPorPagina: Int = 0
    private var tablasCelda: List<List<FilaTabla>> = emptyList()
    private var tablaTotales: List<FilaTabla>? = null
    private var tituloOferta: String = "Oferta comercial"

    private val windowPreviewHelper = WindowPreviewView(context)

    /** Logo de la empresa (se carga una sola vez). */
    private var logoCache: android.graphics.Bitmap? = null
    private val logoBitmap: android.graphics.Bitmap
        get() = logoCache ?: com.example.vineuro_pvc.data.LogoEmpresa.cargar(context).also { logoCache = it }

    /** Vuelve a leer el logo (por si se cambió) y redibuja la hoja. */
    fun recargarLogo() { logoCache = null; invalidate() }
    private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val bgPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#888888"); style = Paint.Style.STROKE }
    private val celdaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#999999"); style = Paint.Style.STROKE }
    private val fondoGris = Paint().apply { color = Color.parseColor("#EEEEEE"); style = Paint.Style.FILL }
    private val fondoCat = Paint().apply { color = Color.parseColor("#DCDCDD"); style = Paint.Style.FILL }
    private val lineaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#777777"); style = Paint.Style.STROKE }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }

    fun bindData(
        pageIndex: Int,
        totalPages: Int,
        pageItems: List<OrderItem>,
        clientName: String,
        clientAddress: String = "",
        clientPhone: String = "",
        clientEmail: String = "",
        offerId: String = "",
        totalOrderCount: Int = 0,
        totalOrderAreaM2: Double = 0.0,
        requisitos: com.example.vineuro_pvc.data.RequisitosDocumento = com.example.vineuro_pvc.data.RequisitosDocumento(),
        conDetalles: Boolean = false,
        ofertaPorPagina: Int = 0,
        tablasCelda: List<List<FilaTabla>> = emptyList(),
        tablaTotales: List<FilaTabla>? = null,
        tituloOferta: String = "Oferta comercial",
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
        this.ofertaPorPagina = ofertaPorPagina
        this.tablasCelda = tablasCelda
        this.tablaTotales = tablaTotales
        this.tituloOferta = tituloOferta
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, (width * 1.414f).toInt()) // proporción A4
    }

    private fun recortado(text: String, p: Paint, maxW: Float): String {
        if (p.measureText(text) <= maxW) return text
        var sub = text
        while (sub.length > 2 && p.measureText("$sub...") > maxW) sub = sub.dropLast(1)
        return "$sub..."
    }

    private fun tamano(p: Paint, u: Float, size: Float, bold: Boolean = false) { p.textSize = size * u; p.isFakeBoldText = bold }

    /** Propiedades de la ventana alineadas a la derecha: categoría con fondo gris y valores terminados en "●". */
    private fun dibujarPropiedades(canvas: Canvas, item: OrderItem, caja: RectF, u: Float, size: Float) {
        val cats = com.example.vineuro_pvc.data.TextoPropiedades.categorias(item)
        if (cats.isEmpty()) return
        tamano(txt, u, size)
        val alto = size * u * 1.3f
        val tam = size * u
        canvas.save(); canvas.clipRect(caja)
        var y = caja.top
        for (c in cats) {
            if (y + alto > caja.bottom) break
            val t = recortado(c.titulo, txt, caja.width() - 4 * u)
            val w = txt.measureText(t) + 3 * u
            canvas.drawRect(caja.right - w, y, caja.right, y + alto, fondoCat)
            txt.textAlign = Paint.Align.RIGHT
            canvas.drawText(t, caja.right - 1.5f * u, y + tam, txt)
            y += alto
            for (l in c.lineas) {
                if (y + alto > caja.bottom) break
                canvas.drawText(recortado("$l", txt, caja.width() - 4 * u) + "●", caja.right - 0.5f * u, y + tam, txt)
                y += alto
            }
            txt.textAlign = Paint.Align.LEFT
        }
        txt.textAlign = Paint.Align.LEFT
        canvas.restore()
    }

    /** Tabla con borde de 3 columnas (etiqueta | valor | unidad) anclada abajo a la derecha de [caja]. */
    private fun dibujarTabla(canvas: Canvas, filas: List<FilaTabla>, left: Float, top: Float, width: Float, rowH: Float, u: Float, size: Float) {
        val wEt = width * 0.56f; val wVal = width * 0.28f
        filas.forEachIndexed { i, f ->
            val t = top + i * rowH
            canvas.drawRect(left, t, left + width, t + rowH, celdaPaint)
            canvas.drawLine(left + wEt, t, left + wEt, t + rowH, celdaPaint)
            canvas.drawLine(left + wEt + wVal, t, left + wEt + wVal, t + rowH, celdaPaint)
            val s = if (f.grande) size * 1.05f else size
            tamano(txt, u, s, f.grande)
            val base = t + rowH / 2f + txt.textSize * 0.35f
            canvas.drawText(recortado(f.etiqueta, txt, wEt - 3 * u), left + 1.8f * u, base, txt)
            txt.textAlign = Paint.Align.CENTER
            canvas.drawText(f.valor, left + wEt + wVal / 2f, base, txt)
            tamano(txt, u, size)
            canvas.drawText(f.unidad, left + wEt + wVal + (width - wEt - wVal) / 2f, base, txt)
            txt.textAlign = Paint.Align.LEFT
        }
        tamano(txt, u, size)
    }

    private fun dibujarEncabezado(canvas: Canvas, m: Float, top: Float, cw: Float, hh: Float, u: Float) {
        // Banner degradado centrado con el logo (como la imagen de cabecera)
        val bx0 = m + cw * 0.27f; val bx1 = m + cw * 0.73f
        val g = Paint().apply {
            shader = LinearGradient(
                bx0, 0f, bx1, 0f,
                intArrayOf(0x00FFFFFF, 0xFFC4D1DB.toInt(), 0xFFE6EDF2.toInt(), 0xFFC4D1DB.toInt(), 0x00FFFFFF),
                floatArrayOf(0f, 0.22f, 0.5f, 0.78f, 1f), Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(bx0, top, bx1, top + hh, g)
        val ola = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x66FFFFFF; strokeWidth = 1.6f * u }
        for (k in 0 until 3) {
            val p = Path()
            val y0 = top + hh * (0.25f + 0.22f * k)
            p.moveTo(bx0 + (bx1 - bx0) * 0.05f, y0)
            p.cubicTo(bx0 + (bx1 - bx0) * 0.3f, y0 - hh * 0.35f, bx0 + (bx1 - bx0) * 0.6f, y0 + hh * 0.3f, bx1 - (bx1 - bx0) * 0.05f, y0 - hh * 0.1f)
            canvas.drawPath(p, ola)
        }
        val lw = (cw * 0.17f); val lh = hh - 6 * u
        val esc = minOf(lw / logoBitmap.width, lh / logoBitmap.height)
        val w = logoBitmap.width * esc; val h = logoBitmap.height * esc
        val cx = m + cw * 0.53f
        canvas.drawBitmap(logoBitmap, null, RectF(cx - w / 2f, top + (hh - h) / 2f, cx + w / 2f, top + (hh + h) / 2f), logoPaint)

        // Textos: etiqueta alineada a la derecha contra los dos puntos; valor a la izquierda
        val size = 6.4f
        val interl = 8.8f * u
        fun v(s: String) = s.ifBlank { "......" }
        fun bloque(colonX: Float, maxX: Float, filas: List<Pair<String, String>>) {
            tamano(txt, u, size)
            var y = top + 8.2f * u
            for ((et, valor) in filas) {
                txt.textAlign = Paint.Align.RIGHT
                canvas.drawText("$et :", colonX, y, txt)
                txt.textAlign = Paint.Align.LEFT
                canvas.drawText(recortado(valor, txt, maxX - colonX - 3 * u), colonX + 3 * u, y, txt)
                y += interl
            }
        }
        val ejec = buildList {
            add("EJECUTANTE" to v(requisitos.ejecutante))
            if (requisitos.ruc.isNotBlank()) add("RUC" to requisitos.ruc)
            add("dirección" to v(requisitos.direccion))
            add("teléfono" to v(requisitos.telefono))
            add("correo electrónico" to v(requisitos.correo))
            add("página web" to v(requisitos.paginaWeb))
        }
        bloque(m + 70 * u, m + cw * 0.41f, ejec)
        bloque(m + cw - 84 * u, m + cw, listOf(
            "CLIENTE" to clientName.ifBlank { "......" },
            "dirección" to v(clientAddress),
            "teléfono" to v(clientPhone),
            "correo electrónico" to v(clientEmail),
            "ID de oferta" to offerId,
        ))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val u = w / 400f
        borderPaint.strokeWidth = 1f * u; celdaPaint.strokeWidth = 0.9f * u; lineaPaint.strokeWidth = 0.7f * u

        canvas.drawRect(0f, 0f, w, h, bgPaint)
        canvas.drawRect(0f, 0f, w, h, borderPaint)

        val m = 10f * u
        val cw = w - 2 * m
        val conCabecera = pageIndex == 0
        val conPie = pageIndex >= totalPages - 1
        val esOferta = ofertaPorPagina > 0

        var y = m
        // Título "Oferta comercial" solo en las ofertas (primera página)
        if (esOferta && conCabecera) {
            canvas.drawRect(m, y, m + cw, y + 11f * u, fondoGris)
            tamano(txt, u, 7.8f); txt.textAlign = Paint.Align.CENTER
            canvas.drawText(tituloOferta, m + cw / 2f, y + 8.2f * u, txt)
            txt.textAlign = Paint.Align.LEFT
            y += 11f * u
        }
        if (conCabecera) {
            val filasEj = 5 + (if (requisitos.ruc.isNotBlank()) 1 else 0)
            val hh = maxOf(46f, 8.8f * filasEj + 5f) * u
            dibujarEncabezado(canvas, m, y, cw, hh, u)
            y += hh + 3f * u
        }

        // Pie: filas de la tabla de "toda la orden"
        val filasTotal: List<FilaTabla> = tablaTotales ?: listOf(
            FilaTabla("cantidad", "${totalOrderCount.coerceAtLeast(pageItems.size)}", "pc"),
            com.example.vineuro_pvc.data.Unidades.areaM2(totalOrderAreaM2).let { FilaTabla("toda el área de productos", it.substringBefore(' '), it.substringAfter(' ', "")) },
        )
        val rowF = 8.8f * u
        val footerH = if (conPie) (1.2f + 9f) * u + filasTotal.size * rowF + 3f * u else 0f
        val gridTop = y
        val gridBottom = h - m - footerH - (if (conPie) 3f * u else 0f)
        val cols = if (esOferta) 1 else 2
        val rows = when (ofertaPorPagina) { 2 -> 2; 1 -> 1; else -> 3 }
        val cellW = cw / cols; val cellH = (gridBottom - gridTop) / rows
        val gap = 2f * u

        for (idx in 0 until cols * rows) {
            val row = idx / cols; val col = idx % cols
            val l = m + col * cellW + gap; val t = gridTop + row * cellH + gap
            val r = m + (col + 1) * cellW - gap; val b = gridTop + (row + 1) * cellH - gap
            canvas.drawRect(l, t, r, b, celdaPaint)
            if (idx >= pageItems.size) continue
            val item = pageItems[idx]
            val cW = r - l

            tamano(txt, u, 9f)
            val numY = t + 11.5f * u
            canvas.drawText("${item.index}", l + 6f * u, numY, txt)
            var xTxt = l + 6f * u + txt.measureText("${item.index}") + 8f * u

            tamano(txt, u, 6.4f)
            if (!esOferta && item.opciones.cantidad >= 2) {
                val t2 = "cantidad ${item.opciones.cantidad}pc"
                canvas.drawText(t2, xTxt, numY, txt); xTxt += txt.measureText(t2) + 8f * u
            }
            if (item.opciones.nota.isNotBlank()) {
                tamano(txt, u, 5.6f)
                canvas.drawText(recortado(item.opciones.nota, txt, (if (esOferta) cW * 0.5f else cW * 0.42f) - (xTxt - l)), xTxt, numY, txt)
            }

            val conLista = conDetalles || esOferta
            val drawBounds = RectF(l + 3f * u, t + 13f * u, if (conLista) r - cW * 0.24f else r - 3f * u, b - 2f * u)
            if (esOferta) { drawBounds.left = l + 8f * u; drawBounds.top = t + 16f * u; drawBounds.bottom = b - 6f * u }
            windowPreviewHelper.drawItem(canvas, drawBounds, item, showTechnicalDetails = true, isCompactMode = true)

            if (conLista) {
                val alto = if (esOferta) {
                    val n = tablasCelda.getOrNull(idx)?.size ?: 0
                    (b - t) - (n * 8.8f + 10f) * u
                } else (b - t) - 6f * u
                dibujarPropiedades(canvas, item, RectF(r - cW * 0.3f, t + 4f * u, r - 2f * u, t + 4f * u + alto.coerceAtLeast(20f * u)), u, if (esOferta) 4.8f else 4.5f)
            }
            if (esOferta) tablasCelda.getOrNull(idx)?.let { filas ->
                val ancho = cW * 0.23f
                dibujarTabla(canvas, filas, r - ancho - 3f * u, b - 3f * u - filas.size * 8.8f * u, ancho, 8.8f * u, u, 4.8f)
            }
        }

        if (!conPie) return
        val top = h - m - footerH
        val x2 = m + cw * 0.46f
        canvas.drawRect(m, top, m + cw, top + 1.2f * u, Paint().apply { color = Color.parseColor("#999999") })
        canvas.drawRect(m, top + 1.2f * u, m + cw, top + 10.2f * u, fondoGris)
        tamano(txt, u, 6.6f)
        val by = top + 8f * u
        canvas.drawText("toda la orden", m + 4f * u, by, txt)
        canvas.drawText("Firmas", x2, by, txt)
        txt.textAlign = Paint.Align.RIGHT
        canvas.drawText(SimpleDateFormat("dd MMM yyyy", Locale("es", "ES")).format(Date()).replace(".", ""), m + cw - 4f * u, by, txt)
        txt.textAlign = Paint.Align.LEFT
        val tTop = top + 10.2f * u
        dibujarTabla(canvas, filasTotal, m, tTop, cw * 0.43f, rowF, u, 5.4f)
        // Firmas: línea al pie de la fila 2 (Ejecutante) y de la última (Cliente)
        val filaEj = if (filasTotal.size >= 4) 1 else 0
        val filaCl = filaEj + 1 + (if (filasTotal.size >= 4) 1 else 0)
        tamano(txt, u, 5.4f)
        for ((et, k) in listOf("Ejecutante" to filaEj, "Cliente" to filaCl.coerceAtMost(filasTotal.size - 1))) {
            val yl = tTop + (k + 1) * rowF
            canvas.drawText(et, x2, yl - 2.4f * u, txt)
            canvas.drawLine(x2, yl, m + cw, yl, lineaPaint)
        }
    }
}
