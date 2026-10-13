package com.example.vineuro_pvc.views

import android.content.Context
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
import com.example.vineuro_pvc.data.CalculadoraPrecios
import com.example.vineuro_pvc.data.CalculadoraPrecios.Costos
import com.example.vineuro_pvc.data.OrderItem
import com.example.vineuro_pvc.data.Unidades
import com.example.vineuro_pvc.data.Precios
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * "PDF con detalles" (uso interno del taller/gerencia): por cada ventana, el dibujo, las opciones elegidas,
 * las cantidades de material y el costo/precio por concepto; al final el consolidado de materiales de todo
 * el pedido, los totales y las advertencias de precios faltantes.
 * No es el documento para el cliente (ese es la Oferta comercial).
 */
object DetallePedidoPdf {

    private const val W = 595f
    private const val H = 842f
    private const val M = 34f
    private val AZUL = Color.parseColor("#1E3A5F")
    private val CELESTE = Color.parseColor("#BDD7EE")
    private val GRIS_BORDE = Color.parseColor("#9CA3AF")
    private val GRIS_TEXTO = Color.parseColor("#4B5563")
    private val ROJO = Color.parseColor("#C00000")
    private val AMBAR = Color.parseColor("#FFF4CE")

    fun crear(
        context: Context,
        codigo: String,
        cliente: String,
        fecha: String,
        items: List<OrderItem>,
        precios: Precios,
    ): File {
        val simbolo = precios.moneda.firstOrNull()?.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "US$"
        val resultados = items.map { CalculadoraPrecios.calcular(it, precios) }
        val pdf = PdfDocument()
        var numPagina = 0
        lateinit var page: PdfDocument.Page
        lateinit var c: Canvas
        var y = 0f

        fun nueva(titulo: String): Float {
            numPagina++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), numPagina).create())
            c = page.canvas
            rect(c, M, M, W - M, M + 22f, AZUL)
            texto(c, titulo, M + 8f, M + 15f, 11f, Color.WHITE, true)
            texto(c, "Pedido $codigo   ·   Cliente: $cliente   ·   $fecha", M, M + 36f, 8.5f, GRIS_TEXTO, maxAncho = W - 2 * M)
            return M + 46f
        }

        fun cerrar() {
            val p = paint(7f, GRIS_BORDE, false)
            val t = "VINEURO PVC  ·  Detalle interno  ·  Página $numPagina"
            c.drawText(t, W - M - p.measureText(t), H - 18f, p)
            pdf.finishPage(page)
        }

        val dibujante = WindowPreviewView(context)
        val k = context.resources.displayMetrics.density * 400f / W * 1.8f
        val altoBloque = 236f

        // ===================== VENTANAS =====================
        y = nueva("DETALLE DE LAS VENTANAS")
        items.forEachIndexed { idx, item ->
            if (y + altoBloque > H - 40f) { cerrar(); y = nueva("DETALLE DE LAS VENTANAS (cont.)") }
            val r = resultados[idx]
            val q = r.cantidades
            val op = item.opciones

            rect(c, M, y, W - M, y + 16f, CELESTE)
            texto(
                c, "Ventana ${item.index}  ·  ${item.config.nombre}  ·  ${com.example.vineuro_pvc.data.Unidades.dimensiones(item.anchoTotalMm, item.altoTotalMm)}  ·  Cantidad: ${r.cantidad}",
                M + 5f, y + 11.5f, 8.6f, AZUL, true, maxAncho = W - 2 * M - 10f,
            )
            val top = y + 20f

            // dibujo
            val caja = RectF(M, top, M + 150f, top + 150f)
            borde(c, caja.left, caja.top, caja.right, caja.bottom)
            c.save()
            c.scale(1f / k, 1f / k)
            runCatching { dibujante.drawItem(c, RectF(caja.left * k, caja.top * k, caja.right * k, caja.bottom * k), item, true, true) }
            c.restore()

            // opciones
            val opciones = buildString {
                append("Sistema: ${op.sistemaVentana ?: "—"}\n")
                append("Cristal: ${op.tipoCristal ?: "—"}\n")
                append("Plan: ${op.planFinanciacion ?: "—"}\n")
                op.color?.let { append("Color: $it\n") }
                op.mueble?.let { append("Mueble: $it\n") }
                op.mosquitero?.let { append("Mosquitero: $it\n") }
                if (op.umbralesExternos.isNotEmpty()) append("Umbral ext.: ${op.umbralesExternos.sorted().joinToString(", ")}\n")
                if (op.umbralesInternos.isNotEmpty()) append("Umbral int.: ${op.umbralesInternos.sorted().joinToString(", ")}\n")
                op.laminacionInterna?.let { append("Lamin. int.: $it\n") }
                op.laminacionExterna?.let { append("Lamin. ext.: $it\n") }
                if (op.adicionales.isNotEmpty()) append("Adicionales: ${op.adicionales.sorted().joinToString(", ")}\n")
                if (op.perfiles.isNotEmpty()) append("Perfiles: ${op.perfiles.sorted().joinToString(", ")}\n")
                if (op.extras.isNotEmpty()) append("Extras: ${op.extras.sorted().joinToString(", ")}\n")
                if (op.trabajos.isNotEmpty()) append("Trabajos: ${op.trabajos.sorted().joinToString(", ")}\n")
                if (r.precioPropioAplicado) append("Precio propio aplicado\n")
                if (op.nota.isNotBlank()) append("Nota: ${op.nota}")
            }.trimEnd()
            val xOp = caja.right + 10f
            val anchoOp = W - M - xOp
            val lay = parrafo(opciones, anchoOp, 7.4f, Color.BLACK)
            c.save(); c.translate(xOp, top); c.clipRect(0f, 0f, anchoOp, 150f); lay.draw(c); c.restore()

            // tabla de conceptos
            var yy = top + 156f
            val xs = floatArrayOf(M, M + 150f, M + 255f, M + 365f, W - M)
            rect(c, M, yy, W - M, yy + 12f, Color.parseColor("#E5E7EB"))
            listOf("Concepto", "Cantidad", "Costo ($simbolo)", "Precio ($simbolo)").forEachIndexed { i, t ->
                texto(c, t, xs[i] + 3f, yy + 9f, 7f, AZUL, true, maxAncho = xs[i + 1] - xs[i] - 4f)
            }
            yy += 12f
            val co = r.costoUnitario
            val pr = r.precioUnitario
            fun fila(nombre: String, cantidad: String, costo: Double, precio: Double) {
                if (cantidad == "-" && costo == 0.0 && precio == 0.0) return
                texto(c, nombre, xs[0] + 3f, yy + 8f, 7f, Color.BLACK)
                texto(c, cantidad, xs[1] + 3f, yy + 8f, 7f, Color.BLACK)
                textoDerecha(c, num(costo), xs[3] - 4f, yy + 8f, 7f)
                textoDerecha(c, num(precio), xs[4] - 4f, yy + 8f, 7f)
                yy += 10f
            }
            fila("Vidrio / paneles / laminación", m2(q.vidrioM2 + q.sandwichM2), co.zona, pr.zona)
            fila("Marco", m(q.marcoM), co.marco, pr.marco)
            fila("Impostas", m(q.impostaM), co.imposta, pr.imposta)
            fila("Hojas", "${q.hojas} u · ${m(q.hojaM)}", co.hoja, pr.hoja)
            fila("Hojas de puerta", "${q.hojasPuerta} u · ${m(q.hojaPuertaM)}", co.hojaPuerta, pr.hojaPuerta)
            fila("Shtapik (junquillo)", m(q.glazingM), co.glazing, pr.glazing)
            fila("Caucho / sellador", m(q.cauchoM), co.caucho, pr.caucho)
            fila("Accesorios, mosquitero, perfiles", "-", co.accesorios, pr.accesorios)
            fila("Trabajos", "-", co.trabajos, pr.trabajos)
            c.drawLine(M, yy, W - M, yy, Paint().apply { color = GRIS_BORDE; strokeWidth = 0.7f })
            texto(c, "TOTAL de UNA ventana", xs[0] + 3f, yy + 9f, 7.4f, Color.BLACK, true)
            textoDerecha(c, num(co.total), xs[3] - 4f, yy + 9f, 7.4f, true)
            textoDerecha(c, num(r.precioUnitarioTotal), xs[4] - 4f, yy + 9f, 7.4f, true)
            y += altoBloque
        }
        if (items.isEmpty()) texto(c, "El pedido no tiene ventanas.", M, y + 12f, 9f, GRIS_TEXTO)
        cerrar()

        // ===================== CONSOLIDADO =====================
        y = nueva("CONSOLIDADO DE MATERIALES Y TOTALES")
        fun suma(f: (CalculadoraPrecios.Cantidades) -> Double) = resultados.sumOf { f(it.cantidades) * it.cantidad }
        val costoPedido = resultados.fold(Costos.CERO) { acc, r -> acc + r.costoUnitario * r.cantidad.toDouble() }
        val resumen = CalculadoraPrecios.resumenPedido(resultados)
        val lineas = listOf(
            "Área de las ventanas" to m2(suma { it.areaM2 }),
            "Vidrio (con medidas reales de vidrio)" to m2(suma { it.vidrioM2 }),
            "Paneles sándwich" to m2(suma { it.sandwichM2 }),
            "Perfil de marco" to m(suma { it.marcoM }),
            "Perfil de imposta" to m(suma { it.impostaM }),
            "Perfil de hoja" to m(suma { it.hojaM }),
            "Perfil de hoja de puerta" to m(suma { it.hojaPuertaM }),
            "Shtapik (junquillo)" to m(suma { it.glazingM }),
            "Caucho / sellador" to m(suma { it.cauchoM }),
            "Hojas (unidades)" to "${suma { it.hojas.toDouble() }.toInt()}",
            "Hojas de puerta (unidades)" to "${suma { it.hojasPuerta.toDouble() }.toInt()}",
            "Piezas de imposta (conectores x2)" to "${suma { it.impostaN.toDouble() }.toInt()}",
            "Paños con mosquitero" to "${suma { it.panosMosquitero.toDouble() }.toInt()}",
            "Superficie a laminar, cara interior" to m2(suma { it.lamInternaM2 }),
            "Superficie a laminar, cara exterior" to m2(suma { it.lamExternaM2 }),
        )
        lineas.forEach { (n, v) ->
            c.drawLine(M, y + 13f, W - M, y + 13f, Paint().apply { color = Color.parseColor("#E5E7EB"); strokeWidth = 0.5f })
            texto(c, n, M + 3f, y + 10f, 8.4f, Color.BLACK)
            textoDerecha(c, v, W - M - 4f, y + 10f, 8.4f, true)
            y += 14f
        }
        y += 10f
        rect(c, M, y, W - M, y + 14f, CELESTE)
        texto(c, "TOTALES DEL PEDIDO ($simbolo)", M + 5f, y + 10.5f, 8.6f, AZUL, true)
        y += 18f
        val ventaSinImp = resumen.neto
        val margen = if (costoPedido.total > 0) (ventaSinImp / costoPedido.total - 1) * 100 else 0.0
        listOf(
            "Costo de materiales y trabajos (sin margen)" to num(costoPedido.total),
            "Venta neta (con margen y descuento, sin impuesto)" to num(resumen.neto),
            "Impuesto (${String.format(Locale.US, "%.2f", resumen.impuestoPct)} %)" to num(resumen.impuesto),
            "TOTAL a cobrar" to num(resumen.total),
            "Margen sobre el costo" to String.format(Locale.US, "%.1f %%", margen),
        ).forEach { (n, v) ->
            texto(c, n, M + 3f, y + 10f, 8.8f, Color.BLACK, n.startsWith("TOTAL"))
            textoDerecha(c, v, W - M - 4f, y + 10f, 8.8f, true)
            y += 14f
        }

        // Advertencias de precios
        val avisos = items.flatMap { it -> CalculadoraPrecios.advertencias(it, precios).map { m -> "Ventana ${it.index}: $m" } }
        if (avisos.isNotEmpty()) {
            y += 12f
            rect(c, M, y, W - M, y + 14f, ROJO)
            texto(c, "ADVERTENCIAS: el costo podría estar subestimado", M + 5f, y + 10.5f, 8.6f, Color.WHITE, true)
            y += 18f
            avisos.forEach { a ->
                val l = parrafo("• $a", W - 2 * M - 8f, 7.8f, Color.BLACK)
                if (y + l.height > H - 40f) { cerrar(); y = nueva("ADVERTENCIAS (cont.)") }
                rect(c, M, y - 1f, W - M, y + l.height + 1f, AMBAR)
                c.save(); c.translate(M + 4f, y); l.draw(c); c.restore()
                y += l.height + 3f
            }
        }
        cerrar()

        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val archivo = File(dir, "Detalle_${codigo.ifBlank { "SN" }}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(archivo).use { pdf.writeTo(it) }
        pdf.close()
        return archivo
    }

    // ===================== AYUDANTES =====================

    private fun num(v: Double) = if (v == 0.0) "-" else String.format(Locale.US, "%,.2f", v)
    private fun m(v: Double) = if (v > 0.0001) Unidades.largoM(v) else "-"
    private fun m2(v: Double) = if (v > 0.0001) Unidades.areaM2(v) else "-"

    private fun rect(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        c.drawRect(l, t, r, b, Paint().apply { this.color = color; style = Paint.Style.FILL })
    }

    private fun borde(c: Canvas, l: Float, t: Float, r: Float, b: Float) {
        c.drawRect(l, t, r, b, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GRIS_BORDE; style = Paint.Style.STROKE; strokeWidth = 0.7f })
    }

    private fun paint(size: Float, color: Int, negrita: Boolean) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.SANS_SERIF, if (negrita) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun texto(c: Canvas, t: String, x: Float, y: Float, size: Float, color: Int, negrita: Boolean = false, maxAncho: Float = Float.MAX_VALUE) {
        val p = paint(size, color, negrita)
        var s = t
        if (p.measureText(s) > maxAncho) {
            while (s.length > 1 && p.measureText("$s…") > maxAncho) s = s.dropLast(1)
            s = "$s…"
        }
        c.drawText(s, x, y, p)
    }

    private fun textoDerecha(c: Canvas, t: String, xDerecha: Float, y: Float, size: Float, negrita: Boolean = false) {
        val p = paint(size, Color.BLACK, negrita)
        c.drawText(t, xDerecha - p.measureText(t), y, p)
    }

    private fun parrafo(t: String, ancho: Float, size: Float, color: Int): StaticLayout =
        StaticLayout.Builder.obtain(t, 0, t.length, paint(size, color, false), ancho.toInt().coerceAtLeast(10))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(1.2f, 1f)
            .setIncludePad(false)
            .build()
}
