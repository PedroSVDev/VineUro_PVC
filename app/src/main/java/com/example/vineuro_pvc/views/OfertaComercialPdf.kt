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
import com.example.vineuro_pvc.data.Unidades
import com.example.vineuro_pvc.data.RequisitosDocumento
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * PDF de la "Oferta comercial" (hoja A4, medidas en puntos), en este orden:
 *  1) Proyectos: cliente y dibujos de las ventanas (la misma hoja de "PDF solo proyectos").
 *  2) Detalle de las ventanas: cada ventana (dibujo, descripción, cantidad, precio unitario y subtotal).
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
        /** 1 = "Oferta comercial" (tabla compacta). 2 = "Oferta comercial 2" (una ventana por página, dibujo grande). */
        val variante: Int = 1,
        val notaPedido: String = "",
        /** Página de portada al inicio (como "cover page" de la original). */
        val portada: Boolean = false,
        /** Página de contrato al final, con las condiciones comerciales y espacio de firmas ("contract page" de la original). */
        val contrato: Boolean = false,
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

        if (d.portada) {
            val pg = nuevaPagina()
            paginaPortada(context, pg.canvas, d)
            pdf.finishPage(pg)
        }

        // ===================== 1) PÁGINAS DE LA OFERTA (formato de la original) =====================
        // "Oferta comercial" = 2 ventanas por hoja; "Oferta comercial 2" = 1 por hoja. Encabezado con el título solo en la
        // primera hoja; cada celda lleva el dibujo, la lista de propiedades (arriba a la derecha) y la tabla de precios
        // (abajo a la derecha); el pie con los totales de toda la orden y las firmas va solo en la última hoja.
        val area = d.items.sumOf { it.areaM2 * it.opciones.cantidad.coerceAtLeast(1) }
        val cantidad = d.items.sumOf { it.opciones.cantidad.coerceAtLeast(1) }

        fun totales(yIni: Float) {
            var yy = yIni
            val totalPrincipal = d.resultados.sumOf { it.totalVenta }
            texto(c, "TOTAL (${d.simbolo})", M, yy + 10f, 10f, AZUL, negrita = true)
            textoDerecha(c, num(totalPrincipal), W - M, yy + 10f, 11f, negrita = true)
            yy += 24f
            texto(c, String.format(Locale.US, "Equivale a:  US$ %,.2f   ·   S/ %,.2f   (T.C. S/ %.3f)", d.totalUsd, d.totalPen, d.tipoCambio), M, yy + 9f, 8.5f, GRIS_TEXTO)
            yy += 14f
            texto(c, textoTarjeta(d), M, yy + 9f, 8.5f, GRIS_TEXTO)
            if (d.notaPedido.isNotBlank()) {
                yy += 24f
                val l = parrafo("Nota: ${d.notaPedido}", W - 2 * M, 8.6f, Color.BLACK)
                c.save(); c.translate(M, yy); l.draw(c); c.restore()
            }
        }

        if (d.items.isNotEmpty()) {
            val porHoja = if (d.variante == 2) 1 else 2
            val hojas = (d.items.size + porHoja - 1) / porHoja
            // La hoja dibuja en "unidades de hoja" (400 = ancho): se pinta a ~400 dp de ancho y se reduce a la hoja A4
            val anchoV = (400f * context.resources.displayMetrics.density).toInt()
            val altoV = (anchoV * H / W).toInt()
            val esc = W / anchoV
            fun v(t: String) = t.ifBlank { "......" }
            fun par(t: String) = t.substringBefore(' ') to t.substringAfter(' ', "")

            val tablas = d.items.indices.map { i ->
                val item = d.items[i]; val r = d.resultados[i]
                val f = 1.0 - r.descuentoPct / 100.0
                val hayDesc = r.descuentoPct > 0.0 && f > 0.0
                val unit = r.precioUnitarioTotal
                val unitSin = if (hayDesc) unit / f else unit
                val ar = par(Unidades.areaM2(item.areaM2))
                buildList {
                    add(FilaTabla("área de productos", ar.first, ar.second))
                    add(FilaTabla("precio", num(unitSin), d.simbolo))
                    if (hayDesc) add(FilaTabla("precio c/ dto. (-${String.format(Locale.US, "%.1f", r.descuentoPct)}%)", num(unit), d.simbolo))
                    add(FilaTabla("cantidad", "${r.cantidad}", "pc"))
                    if (hayDesc) add(FilaTabla("costo", num(unitSin * r.cantidad), d.simbolo))
                    add(FilaTabla(if (hayDesc) "costo c/ dto." else "costo", num(r.totalVenta), d.simbolo))
                }
            }
            val costoSin = d.items.indices.sumOf { i ->
                val r = d.resultados[i]; val f = 1.0 - r.descuentoPct / 100.0
                (if (r.descuentoPct > 0.0 && f > 0.0) r.precioUnitarioTotal / f else r.precioUnitarioTotal) * r.cantidad
            }
            val totalVenta = d.resultados.sumOf { it.totalVenta }
            val descGlobal = if (costoSin > 0.0) (1.0 - totalVenta / costoSin) * 100.0 else 0.0
            val ar = par(Unidades.areaM2(area))
            val totalesTabla = listOf(
                FilaTabla("cantidad", "$cantidad", "pc"),
                FilaTabla("toda el área de productos", ar.first, ar.second),
                FilaTabla("costo", num(costoSin), d.simbolo),
                FilaTabla("precio con descuento (-${String.format(Locale.US, "%.0f", descGlobal.coerceAtLeast(0.0))}%)", num(totalVenta), d.simbolo, grande = true),
            )
            for (h in 0 until hojas) {
                val pg = nuevaPagina()
                val desde = h * porHoja
                val sub = d.items.subList(desde, minOf(desde + porHoja, d.items.size))
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
                    ofertaPorPagina = porHoja,
                    tablasCelda = tablas.subList(desde, minOf(desde + porHoja, d.items.size)),
                    tablaTotales = totalesTabla,
                    tituloOferta = d.requisitos.cabecera.ifBlank { "Oferta comercial" },
                )
                pg.canvas.save()
                pg.canvas.scale(esc, esc)
                hoja.draw(pg.canvas)
                pg.canvas.restore()
                pdf.finishPage(pg)
            }
        }

        // ===================== 2) RESUMEN DE LA COTIZACIÓN (total, equivalencia en US$ / S/, tarjeta y nota) =====================
        run {
            page = nuevaPagina(); c = page.canvas
            rect(c, M, M, W - M, M + 22f, AZUL)
            texto(c, "TOTAL DEL PEDIDO", M + 8f, M + 15f, 11f, Color.WHITE, negrita = true)
            texto(c, "Cotización N° ${d.numero}   ·   Cliente: ${d.clienteNombre}   ·   ${d.fecha}", M, M + 38f, 8.5f, GRIS_TEXTO, maxAncho = W - 2 * M)
            totales(M + 52f)
            pie(c, numPagina)
            pdf.finishPage(page)
        }

        // ===================== 3) COTIZACIÓN + CONDICIONES GENERALES (al final) =====================
        page = nuevaPagina()
        c = page.canvas
        y = encabezado(context, c, d)

        // Barra "CONDICIONES GENERALES"
        y += 6f
        rect(c, M, y, M + 250f, y + 16f, AZUL_BARRA)
        texto(c, "ANEXO A  ·  CONDICIONES GENERALES :", M + 5f, y + 11.5f, 9f, Color.WHITE, negrita = true, cursiva = true)
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

        if (d.contrato) {
            val pg = nuevaPagina()
            paginaContrato(pg.canvas, d)
            pie(pg.canvas, numPagina)
            pdf.finishPage(pg)
        }

        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val archivo = File(dir, "Oferta${if (d.variante == 2) "2" else ""}_${d.numero.ifBlank { "SN" }}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(archivo).use { pdf.writeTo(it) }
        pdf.close()
        return archivo
    }

    // ===================== PORTADA Y CONTRATO =====================

    /** Recargo de pago con tarjeta que indica el Anexo A del cliente (solo informativo: no cambia los totales). */
    private const val RECARGO_TARJETA_PCT = 5.0

    private fun textoTarjeta(d: Datos): String = String.format(
        Locale.US, "Pago con tarjeta (+%.0f%%):  %s %,.2f", RECARGO_TARJETA_PCT, d.simbolo,
        d.resultados.sumOf { it.totalVenta } * (1 + RECARGO_TARJETA_PCT / 100.0),
    )

    private fun paginaPortada(context: Context, c: Canvas, d: Datos) {
        val r = d.requisitos
        rect(c, 0f, 0f, W, 250f, AZUL)
        val logo: Bitmap? = runCatching { com.example.vineuro_pvc.data.LogoEmpresa.cargar(context) }.getOrNull()
        val caja = RectF(W / 2 - 110f, 40f, W / 2 + 110f, 130f)
        rect(c, caja.left - 6f, caja.top - 6f, caja.right + 6f, caja.bottom + 6f, Color.WHITE)
        logo?.let {
            val esc = minOf(caja.width() / it.width, caja.height() / it.height)
            val w = it.width * esc; val h = it.height * esc
            val l = caja.centerX() - w / 2; val t = caja.centerY() - h / 2
            c.drawBitmap(it, null, RectF(l, t, l + w, t + h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        }
        fun centrado(t: String, y: Float, size: Float, color: Int, negrita: Boolean = false) {
            val p = paint(size, color, negrita, false)
            c.drawText(t, (W - p.measureText(t)) / 2f, y, p)
        }
        centrado(r.ejecutante.substringBefore(" - ").ifBlank { "VINEURO PVC" }.uppercase(Locale("es", "PE")), 170f, 20f, Color.WHITE, true)
        if (r.lema1.isNotBlank()) centrado(r.lema1, 195f, 9f, Color.WHITE)
        if (r.lema2.isNotBlank()) centrado(r.lema2, 210f, 8f, Color.WHITE)

        centrado("COTIZACIÓN DE VENTANAS DE PVC", 310f, 22f, AZUL, true)
        centrado("N° ${d.numero}   ·   ${d.fecha}", 336f, 12f, GRIS_TEXTO)
        c.drawLine(W / 2 - 80f, 352f, W / 2 + 80f, 352f, Paint().apply { color = AZUL_BARRA; strokeWidth = 2f })

        centrado("Preparada para", 392f, 10f, GRIS_TEXTO)
        centrado(d.clienteNombre.ifBlank { "......" }, 418f, 18f, Color.BLACK, true)
        val dirLinea = d.clienteDireccion.takeIf { it.isNotBlank() && it != "......" }
        if (dirLinea != null) centrado(dirLinea, 438f, 10f, GRIS_TEXTO)

        val unidades = d.items.sumOf { it.opciones.cantidad.coerceAtLeast(1) }
        val area = d.items.sumOf { it.areaM2 * it.opciones.cantidad.coerceAtLeast(1) }
        val y0 = 490f
        rect(c, M + 30f, y0, W - M - 30f, y0 + 92f, Color.parseColor("#F3F6FA"))
        borde(c, M + 30f, y0, W - M - 30f, y0 + 92f)
        val colW = (W - 2 * M - 60f) / 3f
        val datos = listOf(
            "${d.items.size}" to "ventanas distintas",
            "$unidades" to "unidades",
            Unidades.areaM2(area) to "superficie total",
        )
        datos.forEachIndexed { i, (v, e) ->
            val cx = M + 30f + colW * i + colW / 2f
            val pv = paint(18f, AZUL, true, false); val pe = paint(8.5f, GRIS_TEXTO, false, false)
            c.drawText(v, cx - pv.measureText(v) / 2f, y0 + 38f, pv)
            c.drawText(e, cx - pe.measureText(e) / 2f, y0 + 56f, pe)
        }
        val totalTxt = String.format(Locale.US, "TOTAL  US$ %,.2f   ·   S/ %,.2f", d.totalUsd, d.totalPen)
        val pt = paint(12f, Color.BLACK, true, false)
        c.drawText(totalTxt, (W - pt.measureText(totalTxt)) / 2f, y0 + 80f, pt)

        // Pie con contacto
        rect(c, 0f, H - 70f, W, H, AZUL)
        val contacto = listOfNotNull(
            r.direccion.takeIf { it.isNotBlank() },
            listOfNotNull(r.telefono.takeIf { it.isNotBlank() }?.let { "Cel. $it" }, r.correo.takeIf { it.isNotBlank() }).joinToString("  ·  ").ifBlank { null },
        )
        contacto.forEachIndexed { i, t ->
            val p = paint(8.5f, Color.WHITE, false, false)
            c.drawText(t, (W - p.measureText(t)) / 2f, H - 40f + i * 14f, p)
        }
    }

    /** Contrato de suministro e instalación. Es un modelo base: las condiciones salen de "Requisitos" (forma de pago, plazo, garantía). */
    private fun paginaContrato(c: Canvas, d: Datos) {
        val r = d.requisitos
        var y = M
        rect(c, M, y, W - M, y + 24f, AZUL)
        texto(c, "CONTRATO DE SUMINISTRO E INSTALACIÓN DE VENTANAS DE PVC", M + 8f, y + 16f, 11f, Color.WHITE, negrita = true)
        y += 32f
        texto(c, "Cotización N° ${d.numero}   ·   ${d.fecha}", M, y + 8f, 8.5f, GRIS_TEXTO)
        y += 20f
        val ancho = W - 2 * M
        fun bloque(titulo: String, cuerpo: String) {
            texto(c, titulo, M, y + 9f, 9f, AZUL, negrita = true)
            y += 13f
            val lay = parrafo(cuerpo, ancho, 8.8f, Color.BLACK)
            c.save(); c.translate(M, y); lay.draw(c); c.restore()
            y += lay.height + 9f
        }
        val unidades = d.items.sumOf { it.opciones.cantidad.coerceAtLeast(1) }
        val area = d.items.sumOf { it.areaM2 * it.opciones.cantidad.coerceAtLeast(1) }
        bloque(
            "PARTES",
            "EL CONTRATISTA: ${r.ejecutante}, RUC ${r.ruc}, con domicilio en ${r.direccion}, representado por ${r.firmaNombre} (${r.firmaCargo}).\n" +
                "EL CLIENTE: ${d.clienteNombre.ifBlank { "......" }}, domicilio: ${d.clienteDireccion.ifBlank { "......" }}, teléfono: ${d.clienteTelefono.ifBlank { "......" }}.",
        )
        bloque(
            "PRIMERA · OBJETO",
            "EL CONTRATISTA fabricará, suministrará e instalará ${d.items.size} tipo(s) de ventana de PVC (${unidades} unidad(es), " +
                Unidades.areaM2(area) + ") con las medidas, aperturas, vidrios y accesorios detallados en el detalle de ventanas de la cotización N° ${d.numero}.",
        )
        bloque(
            "SEGUNDA · PRECIO",
            String.format(Locale.US, "El precio total es de US$ %,.2f (S/ %,.2f al tipo de cambio de S/ %.3f), incluidos los impuestos de ley.", d.totalUsd, d.totalPen, d.tipoCambio),
        )
        bloque("TERCERA · FORMA DE PAGO", r.formaPago.ifBlank { "A convenir entre las partes." })
        bloque("CUARTA · PLAZO DE ENTREGA", r.plazoEntrega.ifBlank { "A convenir entre las partes." })
        bloque("QUINTA · GARANTÍA", r.garantia.ifBlank { "Según condiciones generales." })
        bloque(
            "SEXTA · VALIDEZ Y CONDICIONES",
            "La oferta tiene una validez de ${r.validezDias.ifBlank { "15" }} días. Forman parte de este contrato las condiciones generales de la cotización N° ${d.numero}. " +
                "Cualquier cambio de medidas, materiales o alcance requiere acuerdo escrito y puede modificar el precio y el plazo.",
        )
        if (r.notas.isNotBlank()) bloque("NOTAS", r.notas)

        // Firmas
        y = maxOf(y + 40f, H - 150f)
        val lineaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = 0.8f }
        c.drawLine(M, y, M + 200f, y, lineaPaint)
        c.drawLine(W - M - 200f, y, W - M, y, lineaPaint)
        texto(c, "EL CONTRATISTA", M, y + 12f, 8.5f, Color.BLACK, negrita = true)
        texto(c, r.firmaNombre, M, y + 24f, 8.5f, Color.BLACK)
        texto(c, "EL CLIENTE", W - M - 200f, y + 12f, 8.5f, Color.BLACK, negrita = true)
        texto(c, d.clienteNombre.ifBlank { "......" }, W - M - 200f, y + 24f, 8.5f, Color.BLACK, maxAncho = 200f)
        texto(c, "DNI / RUC: ..............................", W - M - 200f, y + 36f, 8.5f, GRIS_TEXTO)
    }

    // ===================== ENCABEZADO (como el Excel) =====================

    private fun encabezado(context: Context, c: Canvas, d: Datos): Float {
        val r = d.requisitos
        var y = M

        // Logo (izquierda)
        val logo: Bitmap? = runCatching { com.example.vineuro_pvc.data.LogoEmpresa.cargar(context) }.getOrNull()
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

    /** Descripción de la ventana: las mismas propiedades que lista la original (categoría: valores), más la nota. */
    private fun descripcion(item: OrderItem): String {
        val op = item.opciones
        return buildString {
            append(item.config.nombre).append("\n")
            append("Ancho x Alto: ${com.example.vineuro_pvc.data.Unidades.dimensiones(item.anchoTotalMm, item.altoTotalMm)}\n")
            for (cat in com.example.vineuro_pvc.data.TextoPropiedades.categorias(item)) {
                append("● ").append(cat.titulo).append(": ").append(cat.lineas.joinToString(", ")).append("\n")
            }
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
