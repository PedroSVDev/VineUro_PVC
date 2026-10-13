package com.example.vineuro_pvc.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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
        PdfFuentes.cargar(context)
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
            val dp = ProyectosPdf.Datos(
                requisitos = d.requisitos,
                clienteNombre = d.clienteNombre,
                clienteDireccion = d.clienteDireccion,
                clienteTelefono = d.clienteTelefono,
                clienteCorreo = d.clienteCorreo,
                codigo = d.numero,
                items = d.items,
                resultados = d.resultados,
            )
            val modo = ProyectosPdf.Modo.SOLO_PROYECTOS
            val hojas = ProyectosPdf.paginar(dp, modo)
            hojas.indices.forEach { n ->
                val pg = nuevaPagina()
                ProyectosPdf.dibujarPagina(context, pg.canvas, dp, modo, hojas, n)
                pdf.finishPage(pg)
            }
        }

        // ===================== 2) ANEXO A: DETALLE DE VENTANAS =====================
        // Cada ventana en una tarjeta grande: dibujo con cotas, medidas, paños, materiales y precio.
        if (d.items.isNotEmpty()) {
            val dibujante = WindowPreviewView(context).apply { modoImpresion = true }
            // el dibujo trabaja en dp: se dibuja más grande y se reduce para que las cotas se lean bien
            val k = context.resources.displayMetrics.density * 1.6f
            val anchoTabla = W - 2 * M
            val anchoDibujo = 220f
            val xTexto = M + anchoDibujo + 12f
            val anchoTexto = W - M - xTexto - 8f
            val xs = floatArrayOf(M, M, M, M, W - M - 230f, W - M - 120f, W - M)
            val GRIS_CLARO = Color.parseColor("#F3F4F6")

            fun inicioAnexo(): Float {
                page = nuevaPagina(); c = page.canvas
                var yy = M
                rect(c, M, yy, W - M, yy + 22f, AZUL)
                texto(c, "ANEXO A  —  DETALLE DE LAS VENTANAS", M + 8f, yy + 15f, 11f, Color.WHITE, negrita = true)
                yy += 30f
                texto(c, "Cotización N° ${d.numero}   ·   Cliente: ${d.clienteNombre}   ·   ${d.fecha}", M, yy + 8f, 8.5f, GRIS_TEXTO, maxAncho = anchoTabla)
                return yy + 18f
            }

            y = inicioAnexo()
            d.items.forEachIndexed { idx, item ->
                val r = d.resultados[idx]
                val lay = parrafo(detalleAnexo(item, r), anchoTexto, 7.8f, Color.BLACK)
                val altoCuerpo = maxOf(210f, lay.height + 14f)
                val altoTarjeta = 22f + altoCuerpo + 22f
                if (y + altoTarjeta > H - 50f) {
                    pie(c, numPagina); pdf.finishPage(page)
                    y = inicioAnexo()
                }

                // Barra de título
                rect(c, M, y, W - M, y + 22f, CELESTE)
                borde(c, M, y, W - M, y + 22f)
                texto(c, "VENTANA ${item.index}", M + 8f, y + 15f, 10f, AZUL, negrita = true)
                val nombre = item.opciones.nota.ifBlank { item.config.nombre }
                texto(c, nombre, M + 84f, y + 15f, 9f, Color.BLACK, maxAncho = W - 2 * M - 210f)
                textoDerecha(c, "${item.anchoTotalMm} × ${item.altoTotalMm} mm", W - M - 8f, y + 15f, 9f, negrita = true)

                // Cuerpo: dibujo (izquierda) + detalle (derecha)
                val top = y + 22f
                borde(c, M, top, W - M, top + altoCuerpo)
                c.drawLine(xTexto - 6f, top, xTexto - 6f, top + altoCuerpo, lineaPaint)
                val altoDibujo = minOf(altoCuerpo, 240f) - 8f
                c.save()
                c.scale(1f / k, 1f / k)
                runCatching {
                    dibujante.drawItem(
                        c,
                        RectF((M + 4f) * k, (top + 4f) * k, (M + anchoDibujo) * k, (top + 4f + altoDibujo) * k),
                        item, true, false,
                    )
                }
                c.restore()
                c.save(); c.translate(xTexto, top + 7f); lay.draw(c); c.restore()

                // Franja de precio
                val ps = top + altoCuerpo
                rect(c, M, ps, W - M, ps + 22f, GRIS_CLARO)
                borde(c, M, ps, W - M, ps + 22f)
                val unit = if (r.cantidad > 0) r.totalVenta / r.cantidad else r.totalVenta
                texto(c, "Cantidad:  ${r.cantidad}", M + 8f, ps + 14.5f, 9f, Color.BLACK, negrita = true)
                texto(c, "Precio unitario:  ${d.simbolo} ${num(unit)}", xs[4], ps + 14.5f, 9f, Color.BLACK)
                textoDerecha(c, "Subtotal:  ${d.simbolo} ${num(r.totalVenta)}", W - M - 8f, ps + 14.5f, 9.5f, negrita = true)
                y = ps + 22f + 10f
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

    /** Texto completo del Anexo A para una ventana (títulos en negrita). */
    private fun detalleAnexo(item: OrderItem, r: CalculadoraPrecios.Resultado): CharSequence {
        val sb = android.text.SpannableStringBuilder()
        fun titulo(t: String) {
            if (sb.isNotEmpty()) sb.append("\n")
            val ini = sb.length
            sb.append(t).append("\n")
            sb.setSpan(PdfFuentes.NegritaSpan(), ini, sb.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(android.text.style.ForegroundColorSpan(AZUL), ini, sb.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        fun linea(etq: String, v: String?) {
            if (v.isNullOrBlank()) return
            val ini = sb.length
            sb.append(etq).append(": ")
            sb.setSpan(PdfFuentes.NegritaSpan(), ini, sb.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append(v).append("\n")
        }
        val op = item.opciones
        val q = r.cantidades

        titulo("MEDIDAS Y DISEÑO")
        linea("Ancho × alto", "${item.anchoTotalMm} × ${item.altoTotalMm} mm   ·   Área: ${String.format(Locale.US, "%.2f", q.areaM2)} m²")
        linea("Diseño", item.config.nombre)
        com.example.vineuro_pvc.data.GeometriaVentana.calcular(item).celdas.forEach { celda ->
            val anchoP = (celda.x1 - celda.x0).toInt()
            val altoP = (celda.y1 - celda.y0).toInt()
            linea("Paño ${celda.indice + 1} (${anchoP} × ${altoP})", com.example.vineuro_pvc.data.TextosVentana.pano(item, celda.indice))
        }
        val hojasTotal = q.hojas + q.hojasPuerta
        val textoHojas = when {
            hojasTotal == 0 -> "ninguna (todo fijo)"
            q.hojasPuerta > 0 -> "$hojasTotal (${q.hojasPuerta} de puerta)"
            else -> "$hojasTotal"
        }
        linea("Hojas que abren", textoHojas)

        titulo("MATERIALES Y OPCIONES")
        linea("Sistema de ventanas", op.sistemaVentana)
        linea("Tipo de cristal", op.tipoCristal)
        linea("Color", op.color)
        linea("Laminación interna", op.laminacionInterna)
        linea("Laminación externa", op.laminacionExterna)
        linea("Umbral externo", op.umbralesExternos.sorted().joinToString(", "))
        linea("Umbral interno", op.umbralesInternos.sorted().joinToString(", "))
        linea("Mueble (manijas / herrajes)", op.mueble)
        linea("Mosquitero", op.mosquitero)
        linea("Extras", op.extras.sorted().joinToString(", "))
        linea("Trabajos", op.trabajos.sorted().joinToString(", "))
        linea("Adicionales", op.adicionales.sorted().joinToString(", "))
        if (listOf(op.sistemaVentana, op.tipoCristal, op.color, op.mueble).all { it.isNullOrBlank() }) {
            sb.append("(Sin opciones elegidas en el compás)\n")
        }
        if (op.nota.isNotBlank()) {
            titulo("NOTA")
            sb.append(op.nota).append("\n")
        }
        while (sb.isNotEmpty() && sb[sb.length - 1] == '\n') sb.delete(sb.length - 1, sb.length)
        return sb
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
        typeface = PdfFuentes.tipo(negrita, cursiva)
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

    private fun parrafo(t: CharSequence, ancho: Float, size: Float, color: Int): StaticLayout =
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
