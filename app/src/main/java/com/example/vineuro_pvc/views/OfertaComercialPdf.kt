package com.example.vineuro_pvc.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.vineuro_pvc.R
import com.example.vineuro_pvc.data.CalculadoraPrecios
import com.example.vineuro_pvc.data.Clausula
import com.example.vineuro_pvc.data.OrderItem
import com.example.vineuro_pvc.data.RequisitosDocumento
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * PDF de la "Oferta comercial" (hoja A4, medidas en puntos), en este orden:
 *  1) Proyectos: cliente y dibujos de las ventanas (la misma hoja de "PDF solo proyectos").
 *  2) Anexo A: detalle de cada ventana (dibujo, descripción, cantidad, precio unitario y subtotal).
 *  3) Cotización y condiciones generales: encabezado de la empresa, N° de cotización, tipo de cambio,
 *     totales en US$ y S/, condiciones y firma (mismo formato que el Excel de VINEURO).
 */
object OfertaComercialPdf {

    data class Datos(
        val requisitos: RequisitosDocumento,
        val clienteNombre: String,
        val clienteDireccion: String,
        val clienteTelefono: String,
        val clienteCorreo: String = "",
        val numero: String,
        val fecha: String,
        val tipoCambio: Double,
        val totalUsd: Double,
        val totalPen: Double,
        val clausulas: List<Clausula>,          // ya con los campos automáticos reemplazados
        val items: List<OrderItem>,
        val resultados: List<CalculadoraPrecios.Resultado>,
        val simbolo: String,                     // moneda en que están los precios de las ventanas
    )

    private const val W = 595f
    private const val H = 842f
    private const val M = 34f // margen
    private val AZUL = Color.parseColor("#1E3A5F")
    private val AZUL_BARRA = Color.parseColor("#2E75B6")
    private val NARANJA = Color.parseColor("#F4B183")
    private val CELESTE = Color.parseColor("#BDD7EE")
    private val AMARILLO = Color.parseColor("#FFFF66")
    private val ROJO = Color.parseColor("#C00000")
    private val GRIS_BORDE = Color.parseColor("#9CA3AF")
    private val GRIS_TEXTO = Color.parseColor("#4B5563")

    fun crear(context: Context, d: Datos): File {
        val pdf = PdfDocument()
        var numPagina = 0

        fun nuevaPagina(): PdfDocument.Page {
            numPagina++
            return pdf.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), numPagina).create())
        }

        val lineaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GRIS_BORDE; strokeWidth = 0.7f }
        lateinit var page: PdfDocument.Page
        lateinit var c: Canvas
        var y = 0f

        // ===================== 1) PROYECTOS: cliente + dibujos (misma hoja que "PDF solo proyectos") =====================
        run {
            val porHoja = 6
            val hojas = ((d.items.size + porHoja - 1) / porHoja).coerceAtLeast(1)
            // La hoja dibuja en dp: se dibuja a ~400 dp de ancho y se reduce a la hoja A4
            val anchoV = (400f * context.resources.displayMetrics.density).toInt()
            val altoV = (anchoV * H / W).toInt()
            val esc = W / anchoV
            val area = d.items.sumOf { (it.altoTotalMm / 1000.0) * (it.anchoTotalMm / 1000.0) }
            val cantidad = d.items.sumOf { it.paneCols.size.coerceAtLeast(1) }
            fun v(t: String) = t.ifBlank { "......" }
            for (h in 0 until hojas) {
                val pg = nuevaPagina()
                val desde = h * porHoja
                val sub = if (desde < d.items.size) d.items.subList(desde, minOf(desde + porHoja, d.items.size)) else emptyList()
                val hoja = PdfPageSheetView(context)
                hoja.layout(0, 0, anchoV, altoV)
                hoja.bindData(
                    pageIndex = h,
                    totalPages = hojas,
                    pageItems = sub,
                    clientName = d.clienteNombre,
                    clientAddress = v(d.clienteDireccion),
                    clientPhone = v(d.clienteTelefono),
                    clientEmail = v(d.clienteCorreo),
                    offerId = d.numero,
                    totalOrderCount = cantidad,
                    totalOrderAreaM2 = area,
                    requisitos = d.requisitos,
                )
                pg.canvas.save()
                pg.canvas.scale(esc, esc)
                hoja.draw(pg.canvas)
                pg.canvas.restore()
                pdf.finishPage(pg)
            }
        }

        // ===================== 2) ANEXO A: DETALLE DE VENTANAS =====================
        if (d.items.isNotEmpty()) {
            val dibujante = WindowPreviewView(context)
            // px virtuales por punto: el dibujo usa medidas en dp; se dibuja más grande y se reduce para que las cotas no tapen la ventana
            val k = context.resources.displayMetrics.density * 400f / W * 1.8f
            val anchoTabla = W - 2 * M
            // columnas: N°, diseño, descripción, cant., p. unit, subtotal
            val cols = floatArrayOf(22f, 118f, 0f, 34f, 70f, 76f)
            cols[2] = anchoTabla - cols.sum()
            val xs = FloatArray(cols.size + 1).also { a -> a[0] = M; for (i in cols.indices) a[i + 1] = a[i] + cols[i] }
            val altoFila = 104f

            fun inicioAnexo(): Float {
                page = nuevaPagina(); c = page.canvas
                var yy = M
                rect(c, M, yy, W - M, yy + 22f, AZUL)
                texto(c, "ANEXO A  —  DETALLE DE LAS VENTANAS", M + 8f, yy + 15f, 11f, Color.WHITE, negrita = true)
                yy += 30f
                texto(c, "Cotización N° ${d.numero}   ·   Cliente: ${d.clienteNombre}   ·   ${d.fecha}", M, yy + 8f, 8.5f, GRIS_TEXTO, maxAncho = anchoTabla)
                yy += 16f
                // encabezado de la tabla
                rect(c, M, yy, W - M, yy + 18f, CELESTE)
                val titulos = listOf("N°", "Diseño", "Descripción", "Cant.", "P. unit. (${d.simbolo})", "Subtotal (${d.simbolo})")
                titulos.forEachIndexed { i, t -> texto(c, t, xs[i] + 4f, yy + 12.5f, 7.6f, AZUL, negrita = true, maxAncho = cols[i] - 6f) }
                borde(c, M, yy, W - M, yy + 18f)
                return yy + 18f
            }

            y = inicioAnexo()
            d.items.forEachIndexed { idx, item ->
                if (y + altoFila > H - 70f) {
                    pie(c, numPagina); pdf.finishPage(page)
                    y = inicioAnexo()
                }
                val r = d.resultados[idx]
                // bordes de la fila
                for (i in 0..cols.size) c.drawLine(xs[i], y, xs[i], y + altoFila, lineaPaint)
                c.drawLine(M, y + altoFila, W - M, y + altoFila, lineaPaint)

                texto(c, "${item.index}", xs[0] + 6f, y + 14f, 10f, Color.BLACK, negrita = true)

                // dibujo de la ventana (se dibuja en "px virtuales" y se reduce a puntos)
                val caja = RectF(xs[1] + 4f, y + 4f, xs[2] - 4f, y + altoFila - 4f)
                c.save()
                c.scale(1f / k, 1f / k)
                runCatching {
                    dibujante.drawItem(c, RectF(caja.left * k, caja.top * k, caja.right * k, caja.bottom * k), item, true, true)
                }
                c.restore()

                // descripción
                val desc = descripcion(item)
                val lay = parrafo(desc, cols[2] - 8f, 7.6f, Color.BLACK)
                c.save(); c.translate(xs[2] + 4f, y + 5f)
                c.clipRect(0f, 0f, cols[2] - 8f, altoFila - 8f)
                lay.draw(c); c.restore()

                val unit = if (r.cantidad > 0) r.totalVenta / r.cantidad else r.totalVenta
                textoDerecha(c, "${r.cantidad}", xs[4] - 6f, y + 14f, 9f)
                textoDerecha(c, num(unit), xs[5] - 6f, y + 14f, 9f)
                textoDerecha(c, num(r.totalVenta), xs[6] - 6f, y + 14f, 9f, negrita = true)
                y += altoFila
            }

            // Totales del anexo
            if (y + 60f > H - 40f) { pie(c, numPagina); pdf.finishPage(page); y = inicioAnexo() }
            val totalPrincipal = d.resultados.sumOf { it.totalVenta }
            y += 6f
            rect(c, xs[4], y, W - M, y + 18f, CELESTE)
            texto(c, "TOTAL (${d.simbolo})", xs[4] + 4f, y + 12.5f, 8f, AZUL, negrita = true)
            textoDerecha(c, num(totalPrincipal), W - M - 6f, y + 12.5f, 9.5f, negrita = true)
            borde(c, xs[4], y, W - M, y + 18f)
            y += 24f
            texto(c, String.format(Locale.US, "Equivale a:  US$ %,.2f   ·   S/ %,.2f   (T.C. S/ %.3f)", d.totalUsd, d.totalPen, d.tipoCambio),
                M, y + 9f, 8.5f, GRIS_TEXTO)
            pie(c, numPagina)
            pdf.finishPage(page)
        }

        // ===================== 3) COTIZACIÓN + CONDICIONES GENERALES (al final) =====================
        page = nuevaPagina()
        c = page.canvas
        y = encabezado(context, c, d)

        // Barra "CONDICIONES GENERALES"
        y += 6f
        rect(c, M, y, M + 200f, y + 16f, AZUL_BARRA)
        texto(c, "CONDICIONES GENERALES :", M + 5f, y + 11.5f, 9f, Color.WHITE, negrita = true, cursiva = true)
        y += 24f

        val colTitulo = 98f
        val anchoTexto = W - 2 * M - colTitulo
        for (cl in d.clausulas) {
            val layout = parrafo(cl.texto, anchoTexto, 8.6f, Color.BLACK)
            val alto = maxOf(layout.height.toFloat(), 11f)
            if (y + alto > H - 70f) {
                pie(c, numPagina)
                pdf.finishPage(page)
                page = nuevaPagina(); c = page.canvas
                y = M + 10f
            }
            texto(c, cl.titulo.uppercase(Locale("es", "PE")), M, y + 8.5f, 7.2f, GRIS_TEXTO, negrita = true, maxAncho = colTitulo - 4f)
            c.save(); c.translate(M + colTitulo, y); layout.draw(c); c.restore()
            y += alto + 4.5f
        }

        // Despedida y firma
        if (y + 70f > H - 40f) {
            pie(c, numPagina); pdf.finishPage(page)
            page = nuevaPagina(); c = page.canvas; y = M + 10f
        }
        y += 8f
        texto(c, "Gracias por su interés", M, y + 10f, 11f, GRIS_TEXTO)
        y += 40f
        c.drawLine(M, y, M + 170f, y, lineaPaint)
        texto(c, d.requisitos.firmaNombre, M, y + 12f, 10f, Color.BLACK, negrita = true, cursiva = true)
        texto(c, d.requisitos.firmaCargo, M, y + 25f, 9.5f, Color.BLACK, negrita = true)
        pie(c, numPagina)
        pdf.finishPage(page)

        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val archivo = File(dir, "Oferta_${d.numero.ifBlank { "SN" }}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(archivo).use { pdf.writeTo(it) }
        pdf.close()
        return archivo
    }

    // ===================== ENCABEZADO (como el Excel) =====================

    private fun encabezado(context: Context, c: Canvas, d: Datos): Float {
        val r = d.requisitos
        var y = M

        // Logo (izquierda)
        val logo: Bitmap? = runCatching { BitmapFactory.decodeResource(context.resources, R.drawable.logo_vineuro) }.getOrNull()
        val cajaLogo = RectF(M, y, M + 190f, y + 72f)
        borde(c, cajaLogo.left, cajaLogo.top, cajaLogo.right, cajaLogo.bottom)
        logo?.let {
            val esc = minOf((cajaLogo.width() - 8f) / it.width, (cajaLogo.height() - 8f) / it.height)
            val w = it.width * esc; val h = it.height * esc
            val l = cajaLogo.centerX() - w / 2; val t = cajaLogo.centerY() - h / 2
            c.drawBitmap(it, null, RectF(l, t, l + w, t + h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        }

        // Datos de contacto (recuadro azul)
        val cajaDatos = RectF(cajaLogo.right, y, cajaLogo.right + 150f, y + 72f)
        rect(c, cajaDatos.left, cajaDatos.top, cajaDatos.right, cajaDatos.bottom, AZUL)
        var yy = y + 13f
        listOfNotNull(
            r.ruc.takeIf { it.isNotBlank() }?.let { "RUC: $it" },
            r.telefono.takeIf { it.isNotBlank() }?.let { "Cel. $it" },
            r.telefonoFijo.takeIf { it.isNotBlank() }?.let { "TF. $it" },
            r.correo.takeIf { it.isNotBlank() }?.let { "Email: $it" },
            r.paginaWeb.takeIf { it.isNotBlank() }?.let { "Web: $it" },
        ).take(5).forEach { linea ->
            texto(c, linea, cajaDatos.left + 6f, yy, 8f, Color.WHITE, negrita = true, maxAncho = cajaDatos.width() - 10f)
            yy += 12f
        }

        // Título del documento (derecha)
        val xTit = cajaDatos.right + 10f
        texto(c, r.cabecera.ifBlank { "Oferta comercial" }.uppercase(Locale("es", "PE")), xTit, y + 22f, 12f, AZUL, negrita = true, maxAncho = W - M - xTit)
        texto(c, "Fecha: ${d.fecha}", xTit, y + 40f, 8.5f, GRIS_TEXTO, maxAncho = W - M - xTit)
        texto(c, r.ejecutante, xTit, y + 54f, 7.5f, GRIS_TEXTO, maxAncho = W - M - xTit)
        y += 76f

        // Barra negra con dirección y Facebook
        rect(c, M, y, W - M, y + 14f, Color.BLACK)
        val dir = buildString {
            if (r.direccion.isNotBlank()) append("Direcc.: ${r.direccion}")
            if (r.facebook.isNotBlank()) append("   Facebook: / ${r.facebook} /")
        }
        texto(c, dir, M + 4f, y + 10f, 7.6f, Color.WHITE, negrita = true, maxAncho = W - 2 * M - 8f)
        y += 18f

        // Lemas
        if (r.lema1.isNotBlank()) { texto(c, r.lema1, M, y + 10f, 10f, Color.BLACK, negrita = true, maxAncho = W - 2 * M); y += 14f }
        if (r.lema2.isNotBlank()) { texto(c, r.lema2, M, y + 9f, 8.6f, ROJO, negrita = true, maxAncho = W - 2 * M); y += 14f }
        y += 4f

        // Fila 1: Cotización N° | TOTAL DÓLARES
        val xTot = W - M - 210f
        rect(c, M, y, M + 200f, y + 17f, NARANJA); borde(c, M, y, M + 200f, y + 17f)
        texto(c, "COTIZACIÓN N° :   ${d.numero}", M + 5f, y + 12f, 9.5f, Color.BLACK, negrita = true)
        rect(c, xTot, y, xTot + 120f, y + 17f, CELESTE); borde(c, xTot, y, W - M, y + 17f)
        texto(c, "TOTAL DÓLARES  US$", xTot + 5f, y + 12f, 8.6f, Color.BLACK, negrita = true)
        textoDerecha(c, num(d.totalUsd), W - M - 5f, y + 12f, 10f, negrita = true)
        y += 19f

        // Fila 2: Cliente / tipo de cambio | TOTAL SOLES
        texto(c, "Tipo de cambio  S/  ${String.format(Locale.US, "%.3f", d.tipoCambio)}", xTot - 150f, y + 12f, 8.6f, Color.BLACK, negrita = true, cursiva = true)
        rect(c, xTot, y, xTot + 120f, y + 17f, AMARILLO); borde(c, xTot, y, W - M, y + 17f)
        texto(c, "TOTAL SOLES  S/", xTot + 5f, y + 12f, 8.6f, Color.BLACK, negrita = true)
        textoDerecha(c, num(d.totalPen), W - M - 5f, y + 12f, 10f, negrita = true)
        y += 21f

        // Cliente
        borde(c, M, y, W - M, y + 30f)
        texto(c, "CLIENTE :  ${d.clienteNombre}", M + 5f, y + 12f, 9f, Color.BLACK, negrita = true, maxAncho = W - 2 * M - 10f)
        val extra = listOf(d.clienteDireccion, d.clienteTelefono.takeIf { it.isNotBlank() }?.let { "Tel. $it" } ?: "")
            .filter { it.isNotBlank() && it != "......" }.joinToString("   ·   ")
        if (extra.isNotBlank()) texto(c, extra, M + 5f, y + 24f, 8f, GRIS_TEXTO, maxAncho = W - 2 * M - 10f)
        return y + 32f
    }

    private fun descripcion(item: OrderItem): String {
        val op = item.opciones
        return buildString {
            append(item.config.nombre).append("\n")
            append("Ancho x Alto: ${item.anchoTotalMm} x ${item.altoTotalMm} mm\n")
            op.sistemaVentana?.let { append("Sistema: $it\n") }
            op.tipoCristal?.let { append("Cristal: $it\n") }
            op.color?.let { append("Color: $it\n") }
            op.mosquitero?.let { append("Mosquitero: $it\n") }
            if (op.extras.isNotEmpty()) append("Extras: ${op.extras.sorted().joinToString(", ")}\n")
            if (op.trabajos.isNotEmpty()) append("Trabajos: ${op.trabajos.sorted().joinToString(", ")}\n")
            if (op.nota.isNotBlank()) append("Nota: ${op.nota}")
        }.trimEnd()
    }

    // ===================== AYUDANTES DE DIBUJO =====================

    private fun num(v: Double) = String.format(Locale.US, "%,.2f", v)

    private fun rect(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        c.drawRect(l, t, r, b, Paint().apply { this.color = color; style = Paint.Style.FILL })
    }

    private fun borde(c: Canvas, l: Float, t: Float, r: Float, b: Float) {
        c.drawRect(l, t, r, b, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GRIS_BORDE; style = Paint.Style.STROKE; strokeWidth = 0.7f })
    }

    private fun paint(size: Float, color: Int, negrita: Boolean, cursiva: Boolean) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(
            Typeface.SANS_SERIF,
            when {
                negrita && cursiva -> Typeface.BOLD_ITALIC
                negrita -> Typeface.BOLD
                cursiva -> Typeface.ITALIC
                else -> Typeface.NORMAL
            },
        )
    }

    private fun texto(
        c: Canvas, t: String, x: Float, y: Float, size: Float, color: Int,
        negrita: Boolean = false, cursiva: Boolean = false, maxAncho: Float = Float.MAX_VALUE,
    ) {
        val p = paint(size, color, negrita, cursiva)
        var s = t
        if (p.measureText(s) > maxAncho) {
            while (s.length > 1 && p.measureText("$s…") > maxAncho) s = s.dropLast(1)
            s = "$s…"
        }
        c.drawText(s, x, y, p)
    }

    private fun textoDerecha(c: Canvas, t: String, xDerecha: Float, y: Float, size: Float, negrita: Boolean = false) {
        val p = paint(size, Color.BLACK, negrita, false)
        c.drawText(t, xDerecha - p.measureText(t), y, p)
    }

    private fun parrafo(t: String, ancho: Float, size: Float, color: Int): StaticLayout =
        StaticLayout.Builder.obtain(t, 0, t.length, paint(size, color, false, false), ancho.toInt().coerceAtLeast(10))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(1.2f, 1f)
            .setIncludePad(false)
            .build()

    private fun pie(c: Canvas, pagina: Int) {
        val p = paint(7f, GRIS_BORDE, false, false)
        val t = "VINEURO PVC  ·  Página $pagina"
        c.drawText(t, W - M - p.measureText(t), H - 18f, p)
    }
}
