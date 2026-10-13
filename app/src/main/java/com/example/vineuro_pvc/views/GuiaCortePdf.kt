package com.example.vineuro_pvc.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.example.vineuro_pvc.data.GuiaCorte
import com.example.vineuro_pvc.data.OrderItem
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PDF "Guía de corte" para el vendedor / taller:
 *  1) Por cada ventana: dibujo pequeño + piezas de perfil a cortar (medida, ángulo, cantidad, ubicación)
 *     + vidrios y paneles sándwich.
 *  2) Optimización de barras de cada perfil (cómo cortar cada barra de 6 m y cuánto sobra).
 *  3) Resumen de vidrios / paneles con los m2 totales.
 */
object GuiaCortePdf {

    data class Datos(
        val clienteNombre: String,
        val codigo: String,
        val items: List<OrderItem>,
        val parametros: GuiaCorte.Parametros = GuiaCorte.Parametros(),
        val fecha: Date = Date(),
    )

    private const val W = 595f
    private const val H = 842f
    private const val M = 26f
    private const val ABAJO = H - 34f
    private val AZUL = Color.parseColor("#1E3A5F")
    private val CELESTE = Color.parseColor("#DCEAF7")
    private val GRIS_FONDO = Color.parseColor("#F1F3F5")
    private val GRIS_BORDE = Color.parseColor("#9CA3AF")
    private val GRIS_TEXTO = Color.parseColor("#4B5563")
    private val ROJO = Color.parseColor("#B91C1C")
    private val COLORES_PIEZA = intArrayOf(
        Color.parseColor("#BFD7EA"), Color.parseColor("#C8E6C9"), Color.parseColor("#FFE0B2"), Color.parseColor("#E1BEE7"),
    )

    fun crear(context: Context, d: Datos, archivo: File): File {
        PdfFuentes.cargar(context)
        val p = d.parametros
        val ventanas = d.items.map { GuiaCorte.ventana(it, p) }
        val optimizacion = GuiaCorte.optimizar(ventanas, p)
        val fecha = SimpleDateFormat("d MMM yyyy", Locale("es", "PE")).format(d.fecha)

        val pdf = PdfDocument()
        var numPagina = 0
        lateinit var page: PdfDocument.Page
        lateinit var c: Canvas
        var y = 0f

        fun cerrar() {
            textoDerecha(c, "Guía de corte · ${d.codigo} · Página $numPagina", W - M, H - 18f, 7f, GRIS_BORDE)
            pdf.finishPage(page)
        }
        fun nueva() {
            if (numPagina > 0) cerrar()
            numPagina++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), numPagina).create())
            c = page.canvas
            y = M
            if (numPagina > 1) {
                texto(c, "GUÍA DE CORTE · Pedido ${d.codigo} (continuación)", M, y + 8f, 8f, GRIS_TEXTO, negrita = true)
                y += 16f
            }
        }
        fun asegurar(alto: Float) { if (y + alto > ABAJO) nueva() }

        // ===================== ENCABEZADO =====================
        nueva()
        rect(c, M, y, W - M, y + 26f, AZUL)
        texto(c, "GUÍA DE CORTE", M + 10f, y + 18f, 14f, Color.WHITE, negrita = true)
        textoDerecha(c, "Pedido ${d.codigo.ifBlank { "—" }}", W - M - 10f, y + 17f, 9.5f, Color.WHITE, negrita = true)
        y += 34f
        texto(c, "Cliente: ${d.clienteNombre}   ·   Fecha: $fecha   ·   Ventanas: ${d.items.size}   ·   Uso interno (taller / vendedor)",
            M, y + 8f, 8f, GRIS_TEXTO, ancho = W - 2 * M)
        y += 16f
        rect(c, M, y, W - M, y + 30f, GRIS_FONDO)
        borde(c, RectF(M, y, W - M, y + 30f))
        texto(c, "Descuentos usados: marco ${p.marcoVisto} mm · imposta ${p.impostaVisto} mm · hoja ${p.hojaVisto} mm · " +
            "traslape de hoja ${p.traslapeHoja} mm · soldadura ${p.soldadura} mm por extremo · vidrio = luz + ${2 * (p.galce - p.holguraVidrio)} mm",
            M + 6f, y + 12f, 7f, Color.BLACK, ancho = W - 2 * M - 12f)
        texto(c, "Barra de ${p.largoBarra} mm (despunte ${p.despunte} mm, sierra ${p.anchoSierra} mm). " +
            "Verificar estos descuentos con el sistema de perfiles antes de cortar.",
            M + 6f, y + 23f, 7f, ROJO, ancho = W - 2 * M - 12f)
        y += 38f

        // ===================== 1) PIEZAS POR VENTANA =====================
        val dibujante = WindowPreviewView(context).apply { modoImpresion = true }
        val k = context.resources.displayMetrics.density * 1.5f
        val anchoDibujo = 130f
        val xTabla = M + anchoDibujo + 8f
        val colsPieza = floatArrayOf(88f, 52f, 34f, 30f)
        val altoFila = 11f

        ventanas.forEach { v ->
            val item = v.item
            val filasTabla = v.piezas.size + 1 + (if (v.vidrios.isNotEmpty()) v.vidrios.size + 2 else 0)
            val altoSeccion = 30f + maxOf(116f, filasTabla * altoFila + 6f) + (if (v.aproximado) 12f else 0f)
            asegurar(minOf(altoSeccion, ABAJO - M - 20f))

            // Título de la ventana
            rect(c, M, y, W - M, y + 24f, CELESTE)
            borde(c, RectF(M, y, W - M, y + 24f))
            val nombre = item.opciones.nota.ifBlank { item.config.nombre }
            texto(c, "Ventana ${item.index} · $nombre", M + 6f, y + 10f, 8.5f, AZUL, negrita = true, ancho = W - 2 * M - 160f)
            textoDerecha(c, "${item.anchoTotalMm} × ${item.altoTotalMm} mm · cantidad ${maxOf(1, item.opciones.cantidad)}",
                W - M - 6f, y + 10f, 8f, Color.BLACK, negrita = true)
            val sub = listOfNotNull(item.opciones.sistemaVentana, item.opciones.color, item.opciones.tipoCristal)
                .joinToString("  ·  ").ifBlank { "Sin sistema / color elegido en el compás" }
            texto(c, sub, M + 6f, y + 20f, 7f, GRIS_TEXTO, ancho = W - 2 * M - 12f)
            y += 28f
            val inicio = y

            // Dibujo pequeño
            c.save()
            c.scale(1f / k, 1f / k)
            runCatching {
                dibujante.drawItem(c, RectF(M * k, y * k, (M + anchoDibujo) * k, (y + 110f) * k), item, showTechnicalDetails = true, isCompactMode = true)
            }
            c.restore()

            // Tabla de piezas
            val encabezados = listOf("Perfil", "Medida (mm)", "Corte", "Cant.", "Ubicación")
            y = filaTabla(c, xTabla, y, encabezados, colsPieza, altoFila, fondo = GRIS_FONDO, negrita = true)
            v.piezas.forEach { pz ->
                if (y + altoFila > ABAJO) { nueva(); y = filaTabla(c, xTabla, y, encabezados, colsPieza, altoFila, fondo = GRIS_FONDO, negrita = true) }
                y = filaTabla(c, xTabla, y, listOf(pz.perfil, "${pz.largo}", pz.angulo, "${pz.cantidad}", pz.ubicacion), colsPieza, altoFila)
            }
            if (v.vidrios.isNotEmpty()) {
                y += altoFila * 0.5f
                val encV = listOf("Vidrio / panel", "Ancho × alto", "m²", "Cant.", "Ubicación")
                y = filaTabla(c, xTabla, y, encV, colsPieza, altoFila, fondo = GRIS_FONDO, negrita = true)
                v.vidrios.forEach { vd ->
                    if (y + altoFila > ABAJO) { nueva(); y = filaTabla(c, xTabla, y, encV, colsPieza, altoFila, fondo = GRIS_FONDO, negrita = true) }
                    y = filaTabla(c, xTabla, y, listOf(
                        if (vd.panel) "Panel sándwich" else "Vidrio",
                        "${vd.ancho} × ${vd.alto}",
                        String.format(Locale.US, "%.2f", vd.m2),
                        "${vd.cantidad}",
                        vd.ubicacion,
                    ), colsPieza, altoFila)
                }
            }
            y = maxOf(y, inicio + 116f)
            if (v.aproximado) {
                texto(c, "* Diseño de puerta: la distribución se aproxima como cuadrícula. Revisar medidas en obra.", M, y + 8f, 6.8f, ROJO)
                y += 12f
            }
            y += 10f
        }

        // ===================== 2) OPTIMIZACIÓN DE BARRAS =====================
        asegurar(60f)
        y += 4f
        rect(c, M, y, W - M, y + 20f, AZUL)
        texto(c, "OPTIMIZACIÓN DE BARRAS (barra de ${p.largoBarra} mm)", M + 8f, y + 14f, 10f, Color.WHITE, negrita = true)
        y += 26f
        val xBarra = M + 46f
        val anchoBarra = W - M - xBarra - 60f
        val util = (p.largoBarra - 2 * p.despunte).toFloat()
        optimizacion.forEach { o ->
            asegurar(34f)
            texto(c, "${o.perfil}: ${o.barras.size} barra(s) · ${o.totalPiezas} piezas · " +
                String.format(Locale.US, "%.2f m de corte · desperdicio %.1f %%", o.metrosUsados, o.desperdicioPct),
                M, y + 9f, 8.5f, AZUL, negrita = true, ancho = W - 2 * M)
            y += 14f
            o.barras.forEachIndexed { i, b ->
                asegurar(18f)
                texto(c, "Barra ${i + 1}", M, y + 9f, 7f, Color.BLACK)
                // dibujo de la barra con sus cortes
                var x = xBarra
                b.cortes.forEachIndexed { j, largo ->
                    val wSeg = largo / util * anchoBarra
                    rect(c, x, y, x + wSeg, y + 13f, COLORES_PIEZA[j % COLORES_PIEZA.size])
                    borde(c, RectF(x, y, x + wSeg, y + 13f))
                    val t = "$largo"
                    if (paint(6.5f, Color.BLACK, false).measureText(t) < wSeg - 2f) textoCentro(c, t, x + wSeg / 2f, y + 9.3f, 6.5f, Color.BLACK)
                    x += wSeg + p.anchoSierra / util * anchoBarra
                }
                // sobrante
                val xFin = xBarra + anchoBarra
                if (x < xFin) {
                    rect(c, x, y, xFin, y + 13f, GRIS_FONDO)
                    borde(c, RectF(x, y, xFin, y + 13f))
                }
                textoDerecha(c, "sobra ${b.sobrante}", W - M, y + 9.3f, 7f, GRIS_TEXTO)
                y += 17f
            }
            y += 6f
        }

        // ===================== 3) RESUMEN DE VIDRIOS Y PANELES =====================
        val todos = ventanas.flatMap { it.vidrios }
        if (todos.isNotEmpty()) {
            asegurar(60f)
            y += 4f
            rect(c, M, y, W - M, y + 20f, AZUL)
            texto(c, "VIDRIOS Y PANELES", M + 8f, y + 14f, 10f, Color.WHITE, negrita = true)
            y += 24f
            val colsV = floatArrayOf(46f, 70f, 70f, 70f, 36f, 50f)
            val encV = listOf("Ventana", "Tipo", "Ancho (mm)", "Alto (mm)", "Cant.", "m²", "Ubicación")
            y = filaTabla(c, M, y, encV, colsV, 12f, fondo = GRIS_FONDO, negrita = true)
            todos.forEach { vd ->
                if (y + 12f > ABAJO) { nueva(); y = filaTabla(c, M, y, encV, colsV, 12f, fondo = GRIS_FONDO, negrita = true) }
                y = filaTabla(c, M, y, listOf("${vd.ventana}", if (vd.panel) "Panel sándwich" else "Vidrio",
                    "${vd.ancho}", "${vd.alto}", "${vd.cantidad}", String.format(Locale.US, "%.2f", vd.m2), vd.ubicacion), colsV, 12f)
            }
            asegurar(16f)
            val m2Vidrio = todos.filter { !it.panel }.sumOf { it.m2 }
            val m2Panel = todos.filter { it.panel }.sumOf { it.m2 }
            texto(c, String.format(Locale.US, "Total vidrio: %.2f m²   ·   Total panel sándwich: %.2f m²", m2Vidrio, m2Panel),
                M, y + 11f, 8.5f, Color.BLACK, negrita = true)
            y += 16f
        }

        cerrar()
        FileOutputStream(archivo).use { pdf.writeTo(it) }
        pdf.close()
        return archivo
    }

    // ===================== AYUDANTES =====================

    /** Fila de tabla: columnas de ancho fijo; la última ocupa hasta el margen derecho. */
    private fun filaTabla(
        c: Canvas, x: Float, y: Float, valores: List<String>, anchos: FloatArray, alto: Float,
        fondo: Int? = null, negrita: Boolean = false,
    ): Float {
        val xs = FloatArray(valores.size + 1)
        xs[0] = x
        for (i in valores.indices) xs[i + 1] = if (i < anchos.size) xs[i] + anchos[i] else W - M
        fondo?.let { rect(c, x, y, W - M, y + alto, it) }
        borde(c, RectF(x, y, W - M, y + alto))
        for (i in 1 until valores.size) c.drawLine(xs[i], y, xs[i], y + alto, linea())
        valores.forEachIndexed { i, v ->
            texto(c, v, xs[i] + 3f, y + alto - 3.2f, 6.8f, Color.BLACK, negrita = negrita, ancho = xs[i + 1] - xs[i] - 5f)
        }
        return y + alto
    }

    private fun linea() = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GRIS_BORDE; strokeWidth = 0.5f }

    private fun rect(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        c.drawRect(l, t, r, b, Paint().apply { this.color = color; style = Paint.Style.FILL })
    }

    private fun borde(c: Canvas, r: RectF) {
        c.drawRect(r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GRIS_BORDE; style = Paint.Style.STROKE; strokeWidth = 0.6f })
    }

    private fun paint(tam: Float, color: Int, negrita: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = tam
        this.color = color
        typeface = PdfFuentes.tipo(negrita)
    }

    private fun texto(c: Canvas, t: String, x: Float, y: Float, tam: Float, color: Int, negrita: Boolean = false, ancho: Float = Float.MAX_VALUE) {
        val p = paint(tam, color, negrita)
        var s = t
        if (p.measureText(s) > ancho) {
            while (s.length > 1 && p.measureText("$s…") > ancho) s = s.dropLast(1)
            s = "$s…"
        }
        c.drawText(s, x, y, p)
    }

    private fun textoDerecha(c: Canvas, t: String, xDerecha: Float, y: Float, tam: Float, color: Int, negrita: Boolean = false) {
        val p = paint(tam, color, negrita)
        c.drawText(t, xDerecha - p.measureText(t), y, p)
    }

    private fun textoCentro(c: Canvas, t: String, x: Float, y: Float, tam: Float, color: Int) {
        val p = paint(tam, color, false)
        c.drawText(t, x - p.measureText(t) / 2f, y, p)
    }
}
