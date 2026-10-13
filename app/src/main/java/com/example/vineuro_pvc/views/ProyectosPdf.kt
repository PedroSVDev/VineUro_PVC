package com.example.vineuro_pvc.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.example.vineuro_pvc.R
import com.example.vineuro_pvc.data.CalculadoraPrecios
import com.example.vineuro_pvc.data.OrderItem
import com.example.vineuro_pvc.data.RequisitosDocumento
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PDF de proyectos (cotización con los dibujos de las ventanas), en dos formatos:
 *
 * - SOLO_PROYECTOS: hasta 6 diseños por hoja (2 columnas x 3 filas). Cada diseño con sus cotas,
 *   el nombre, los parámetros y debajo área, precio y cantidad. Si el pedido tiene 1 o 2 ventanas,
 *   cada una va en un bloque horizontal ancho (dibujo a la izquierda, datos a la derecha).
 * - CON_DETALLES: 2 diseños por hoja en bloques horizontales anchos, el dibujo pintado con el color
 *   elegido (marco negro, nogal, etc.), todos los parámetros y la tabla completa de precios.
 *
 * Al final siempre va "toda la orden" (totales, descuento, IGV) con las firmas y la fecha.
 * Todo se dibuja en puntos de una hoja A4 (595 x 842), igual para la vista previa y para el archivo.
 */
object ProyectosPdf {

    enum class Modo { SOLO_PROYECTOS, CON_DETALLES }

    data class Datos(
        val requisitos: RequisitosDocumento,
        val clienteNombre: String,
        val clienteDireccion: String,
        val clienteTelefono: String,
        val clienteCorreo: String,
        val codigo: String,
        val items: List<OrderItem>,
        val resultados: List<CalculadoraPrecios.Resultado>,
        /** nombre del color (tabla "colores" de Precios) -> color para pintar el perfil. */
        val colores: Map<String, Int> = emptyMap(),
        val impuestoNombre: String = "IGV",
        val fecha: Date = Date(),
    )

    sealed class Elemento {
        data class Encabezado(val rect: RectF) : Elemento()
        data class Bloque(val indice: Int, val rect: RectF, val compacto: Boolean) : Elemento()
        data class Pie(val rect: RectF) : Elemento()
    }

    const val W = 595f
    const val H = 842f
    private const val M = 20f
    private const val ALTO_ENCABEZADO = 112f
    private const val ALTO_PIE = 112f
    private const val ALTO_BLOQUE = 334f   // formato ancho: 2 por hoja
    private const val ALTO_CELDA = 224f    // formato compacto: 2 x 3 por hoja
    private const val ABAJO = H - M - 12f  // deja lugar para "Página x de y"

    private val GRIS_BORDE = Color.parseColor("#9CA3AF")
    private val GRIS_FONDO = Color.parseColor("#E5E7EB")
    private val GRIS_ETIQUETA = Color.parseColor("#D9D9D9")
    private val GRIS_TEXTO = Color.parseColor("#4B5563")

    // ===================== PAGINACIÓN =====================

    /**
     * Reparte los diseños en hojas. "toda la orden" + firmas SIEMPRE va en la última hoja,
     * junto con los últimos diseños (nunca sola en una hoja en blanco): si no entra, los diseños
     * de esa hoja se achican un poco para dejarle espacio.
     */
    fun paginar(d: Datos, modo: Modo): List<List<Elemento>> {
        val compacto = modo == Modo.SOLO_PROYECTOS && d.items.size > 2
        val porHoja = if (compacto) 6 else 2
        val n = d.items.size
        val totalHojas = ((n + porHoja - 1) / porHoja).coerceAtLeast(1)
        val paginas = mutableListOf<List<Elemento>>()

        for (h in 0 until totalHojas) {
            val elementos = mutableListOf<Elemento>()
            var top = M
            if (h == 0) {
                elementos.add(Elemento.Encabezado(RectF(M, M, W - M, M + ALTO_ENCABEZADO)))
                top = M + ALTO_ENCABEZADO + 6f
            }
            val ultima = h == totalHojas - 1
            val fondo = if (ultima) ABAJO - ALTO_PIE - 6f else ABAJO
            val desde = h * porHoja
            val indices = (desde until minOf(desde + porHoja, n)).toList()

            if (compacto) {
                val filas = ((indices.size + 1) / 2).coerceAtLeast(1)
                val altoFila = minOf(ALTO_CELDA, (fondo - top) / maxOf(filas, if (ultima) 1 else 3))
                val ancho = (W - 2 * M) / 2f
                indices.forEachIndexed { k, i ->
                    val l = M + (k % 2) * ancho
                    val t = top + (k / 2) * altoFila
                    elementos.add(Elemento.Bloque(i, RectF(l, t, l + ancho, t + altoFila), true))
                }
            } else {
                val altoBloque = minOf(ALTO_BLOQUE, (fondo - top) / maxOf(indices.size, 1))
                indices.forEachIndexed { k, i ->
                    val t = top + k * altoBloque
                    elementos.add(Elemento.Bloque(i, RectF(M, t, W - M, t + altoBloque), false))
                }
            }
            if (ultima) elementos.add(Elemento.Pie(RectF(M, ABAJO - ALTO_PIE, W - M, ABAJO)))
            paginas.add(elementos)
        }
        return paginas
    }

    // ===================== DIBUJO DE UNA HOJA =====================

    fun dibujarPagina(context: Context, c: Canvas, d: Datos, modo: Modo, paginas: List<List<Elemento>>, n: Int) {
        PdfFuentes.cargar(context)
        c.drawRect(0f, 0f, W, H, Paint().apply { color = Color.WHITE })
        val dibujante = WindowPreviewView(context).apply { modoImpresion = true }
        paginas.getOrNull(n)?.forEach { e ->
            when (e) {
                is Elemento.Encabezado -> encabezado(context, c, d, e.rect)
                is Elemento.Bloque -> bloque(context, c, d, modo, dibujante, e)
                is Elemento.Pie -> pie(c, d, e.rect)
            }
        }
        if (paginas.size > 1) {
            textoDerecha(c, "Página ${n + 1} de ${paginas.size}", W - M, H - M + 4f, 6.5f, GRIS_BORDE)
        }
    }

    /** Genera el archivo PDF completo. */
    fun crearArchivo(context: Context, d: Datos, modo: Modo, archivo: File): File {
        val paginas = paginar(d, modo)
        val pdf = PdfDocument()
        paginas.indices.forEach { n ->
            val pg = pdf.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), n + 1).create())
            dibujarPagina(context, pg.canvas, d, modo, paginas, n)
            pdf.finishPage(pg)
        }
        FileOutputStream(archivo).use { pdf.writeTo(it) }
        pdf.close()
        return archivo
    }

    // ===================== ENCABEZADO =====================

    private var logoCache: Bitmap? = null

    private fun encabezado(context: Context, c: Canvas, d: Datos, r: RectF) {
        val req = d.requisitos
        // Banda gris con el título
        rect(c, r.left, r.top, r.right, r.top + 14f, GRIS_FONDO)
        textoCentro(c, "COTIZACIÓN", r.centerX(), r.top + 10.5f, 9f, Color.BLACK, negrita = false)

        val colW = r.width() / 3f
        fun v(t: String) = t.ifBlank { "......" }

        // Izquierda: ejecutante (etiqueta alineada a la derecha + valor)
        val xEtq = r.left + 72f
        var y = r.top + 26f
        y = fila(c, "EJECUTANTE :", v(req.ejecutante), xEtq, y, colW + 40f - 76f)
        y = fila(c, "RUC :", v(req.ruc), xEtq, y, colW + 40f - 76f)
        y = fila(c, "dirección :", v(req.direccion), xEtq, y, colW + 40f - 76f)
        y = fila(c, "teléfono :", v(req.telefono), xEtq, y, colW + 40f - 76f)
        y = fila(c, "correo electrónico :", v(req.correo), xEtq, y, colW + 40f - 76f)
        fila(c, "página web :", v(req.paginaWeb), xEtq, y, colW + 40f - 76f)

        // Centro: logo
        val logo = logoCache ?: runCatching { BitmapFactory.decodeResource(context.resources, R.drawable.logo_vineuro) }
            .getOrNull()?.also { logoCache = it }
        logo?.let {
            val caja = RectF(r.left + colW + 40f, r.top + 18f, r.right - colW + 10f, r.bottom - 4f)
            val esc = minOf(caja.width() / it.width, caja.height() / it.height)
            val w = it.width * esc; val h = it.height * esc
            val l = caja.centerX() - w / 2f; val t = caja.centerY() - h / 2f
            c.drawBitmap(it, null, RectF(l, t, l + w, t + h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        }

        // Derecha: cliente
        val xEtqC = r.right - colW + 70f
        var yc = r.top + 26f
        val anchoC = r.right - xEtqC - 4f
        yc = fila(c, "CLIENTE :", v(d.clienteNombre), xEtqC, yc, anchoC)
        yc = fila(c, "dirección :", v(d.clienteDireccion), xEtqC, yc, anchoC)
        yc = fila(c, "teléfono :", v(d.clienteTelefono), xEtqC, yc, anchoC)
        yc = fila(c, "correo electrónico :", v(d.clienteCorreo), xEtqC, yc, anchoC)
        fila(c, "ID de oferta :", v(d.codigo), xEtqC, yc, anchoC)
    }

    /** "etiqueta : valor" con la etiqueta alineada a la derecha; el valor puede ocupar 2 líneas. */
    private fun fila(c: Canvas, etiqueta: String, valor: String, xDosPuntos: Float, y: Float, anchoValor: Float): Float {
        textoDerecha(c, etiqueta, xDosPuntos, y, 7f, GRIS_TEXTO)
        val lineas = partir(valor, anchoValor, paint(7f, Color.BLACK, false), 2)
        lineas.forEachIndexed { i, l -> texto(c, l, xDosPuntos + 3f, y + i * 8.6f, 7f, Color.BLACK) }
        return y + 9.6f + (lineas.size - 1) * 8.6f
    }

    // ===================== BLOQUE DE UNA VENTANA =====================

    private data class Importes(
        val area: Double,         // m2 de UNA ventana
        val cantidad: Int,
        val unitario: Double,     // precio de una ventana sin descuento ni impuesto
        val unitarioDesc: Double, // con descuento, sin impuesto
        val descuentoPct: Double,
        val impuestoPct: Double,
        val totalConImpuesto: Double,
        val simbolo: String,
    ) {
        val costo get() = unitario * cantidad
        val conDescuento get() = unitarioDesc * cantidad
    }

    private fun importes(item: OrderItem, res: CalculadoraPrecios.Resultado?): Importes {
        val area = (item.altoTotalMm / 1000.0) * (item.anchoTotalMm / 1000.0)
        if (res == null) return Importes(area, 1, 0.0, 0.0, 0.0, 0.0, 0.0, "S/")
        val cant = res.cantidad.coerceAtLeast(1)
        val fImp = 1 + res.impuestoPct / 100.0
        val fDesc = 1 - res.descuentoPct / 100.0
        val unitDesc = res.totalVenta / cant / fImp
        val unit = if (fDesc > 0.0001) unitDesc / fDesc else unitDesc
        return Importes(area, cant, unit, unitDesc, res.descuentoPct, res.impuestoPct, res.totalVenta, res.simbolo)
    }

    /** Parámetros elegidos en el compás: (título, valor). */
    private fun parametros(item: OrderItem): List<Pair<String, String>> {
        val op = item.opciones
        val lista = mutableListOf<Pair<String, String>>()
        fun add(t: String, v: String?) { if (!v.isNullOrBlank()) lista.add(t to v) }
        fun add(t: String, v: Collection<String>) { if (v.isNotEmpty()) lista.add(t to v.sorted().joinToString(", ")) }
        add("Tipo de sistema de ventanas", op.sistemaVentana)
        add("tipo de cristal", op.tipoCristal)
        add("Tipo de umbral externo", op.umbralesExternos)
        add("Tipo de umbral interno", op.umbralesInternos)
        add("mueble", op.mueble)
        add("extras", op.extras)
        add("trabajos", op.trabajos)
        add("mosquitero", op.mosquitero)
        add("laminación interna", op.laminacionInterna)
        add("laminación externa", op.laminacionExterna)
        add("color", op.color)
        add("adicionales", op.adicionales)
        return lista
    }

    private fun colorPerfil(item: OrderItem, d: Datos): Int? =
        item.opciones.color?.let { d.colores[it] }
            ?: item.opciones.laminacionExterna?.let { d.colores[it] }

    private fun bloque(context: Context, c: Canvas, d: Datos, modo: Modo, dib: WindowPreviewView, e: Elemento.Bloque) {
        val item = d.items[e.indice]
        val imp = importes(item, d.resultados.getOrNull(e.indice))
        val r = e.rect
        val comp = e.compacto
        borde(c, r)

        // Número y cantidad
        texto(c, "${item.index}", r.left + 7f, r.top + (if (comp) 14f else 17f), if (comp) 11f else 13f, Color.BLACK)

        // Columna derecha: nombre, parámetros y tabla
        val panelW = if (comp) r.width() * 0.44f else r.width() * 0.33f
        val pr = r.right - 6f
        val pl = pr - panelW
        var y = r.top + 10f
        if (imp.cantidad > 1) {
            val p = paint(if (comp) 9f else 11f, Color.BLACK, false)
            val t2 = "${imp.cantidad}pc"
            c.drawText(t2, pr - p.measureText(t2), y + 4f, p)
            textoDerecha(c, "cantidad ", pr - p.measureText(t2), y + 4f, 6.5f, Color.BLACK)
            y += 10f
        }
        val titulo = item.opciones.nota.ifBlank { item.config.nombre }
        val tamTit = if (comp) 6.3f else 7f
        partir(titulo, panelW, paint(tamTit, Color.BLACK, false), 2).forEach { l ->
            y += tamTit + 1.5f
            textoDerecha(c, l, pr, y, tamTit, Color.BLACK)
        }
        y += 4f

        // Tabla de importes (abajo a la derecha)
        val filas = mutableListOf<Triple<String, String, String>>()
        val s = imp.simbolo
        filas.add(Triple("área de productos", num(imp.area), "m2"))
        filas.add(Triple("precio", num(imp.unitario), s))
        if (!comp && imp.descuentoPct > 0) {
            filas.add(Triple("Precio descontado", "${num(imp.unitarioDesc)}(-${pct(imp.descuentoPct)}%)", s))
        }
        filas.add(Triple("cantidad", "${imp.cantidad}", "pc"))
        if (!comp) {
            filas.add(Triple("costo", num(imp.costo), s))
            if (imp.descuentoPct > 0) filas.add(Triple("precio con descuento", num(imp.conDescuento), s))
        }
        val altoFila = if (comp) 10f else 11.5f
        val tablaTop = r.bottom - 5f - filas.size * altoFila
        tabla(c, filas, pl, tablaTop, pr, altoFila, if (comp) 5.6f else 6.2f, resaltarUltima = !comp && imp.descuentoPct > 0)

        // Parámetros (entre el título y la tabla)
        val tamP = if (comp) 4.9f else 5.6f
        val pasoEtq = tamP + 3.2f
        for ((etq, valor) in parametros(item)) {
            if (y + pasoEtq * 2 > tablaTop - 3f) break
            // título del parámetro en recuadro gris
            val pe = paint(tamP, Color.BLACK, false)
            val we = pe.measureText(etq) + 4f
            rect(c, pr - we, y, pr, y + tamP + 2f, GRIS_ETIQUETA)
            textoDerecha(c, etq, pr - 2f, y + tamP + 0.3f, tamP, Color.BLACK)
            y += pasoEtq
            textoDerecha(c, recortar("$valor●", panelW, pe), pr, y + tamP - 1f, tamP, Color.BLACK)
            y += pasoEtq - 1f
        }

        // Dibujo de la ventana con sus cotas (a la izquierda)
        val area = RectF(r.left + 6f, r.top + (if (comp) 16f else 20f), pl - 6f, r.bottom - 6f)
        if (comp) area.bottom = r.bottom - 4f
        dib.colorPerfil = if (modo == Modo.CON_DETALLES) colorPerfil(item, d) else null
        // El dibujo trabaja en dp: se dibuja más grande y se reduce para que las cotas tengan buen tamaño
        val k = context.resources.displayMetrics.density * (if (comp) 1.25f else 1.0f)
        c.save()
        c.scale(1f / k, 1f / k)
        dib.drawItem(c, RectF(area.left * k, area.top * k, area.right * k, area.bottom * k), item, showTechnicalDetails = true, isCompactMode = comp)
        c.restore()
    }

    // ===================== PIE: TODA LA ORDEN + FIRMAS =====================

    private fun pie(c: Canvas, d: Datos, r: RectF) {
        val lista = d.items.mapIndexed { i, it -> importes(it, d.resultados.getOrNull(i)) }
        val cantidad = lista.sumOf { it.cantidad }
        val area = lista.sumOf { it.area * it.cantidad }
        val costo = lista.sumOf { it.costo }
        val conDesc = lista.sumOf { it.conDescuento }
        val total = lista.sumOf { it.totalConImpuesto }
        val impuesto = total - conDesc
        val s = lista.firstOrNull()?.simbolo ?: "S/"
        val descuentos = lista.map { it.descuentoPct }.distinct()
        val txtDesc = if (descuentos.size == 1) "(-${pct(descuentos[0])}%)" else ""
        val impPct = lista.firstOrNull()?.impuestoPct ?: 0.0

        val colIzq = r.left + r.width() * 0.45f
        val cab = 14f
        // Encabezados grises
        rect(c, r.left, r.top, colIzq, r.top + cab, GRIS_FONDO)
        rect(c, colIzq + 6f, r.top, r.right, r.top + cab, GRIS_FONDO)
        texto(c, "toda la orden", r.left + 5f, r.top + 10f, 8f, Color.BLACK)
        texto(c, "Firmas", colIzq + 11f, r.top + 10f, 8f, Color.BLACK)
        textoDerecha(c, SimpleDateFormat("d MMM yyyy", Locale("es", "PE")).format(d.fecha), r.right - 5f, r.top + 10f, 8f, Color.BLACK)

        val filas = listOf(
            Triple("cantidad", "$cantidad", "pc"),
            Triple("toda el área de productos", num(area), "m2"),
            Triple("costo", num(costo), s),
            Triple("precio con descuento $txtDesc", num(conDesc), s),
            Triple("el impuesto: ${d.impuestoNombre} ${pct(impPct)}%", num(impuesto), s),
            Triple("precio con descuento + ${d.impuestoNombre}", num(total), s),
        )
        val altoFila = (r.height() - cab) / filas.size
        tabla(c, filas, r.left, r.top + cab, colIzq, altoFila, 7.6f, resaltar = setOf(3, 5))

        // Firmas
        val mitad = r.top + cab + (r.height() - cab) / 2f
        texto(c, "Ejecutante", colIzq + 11f, mitad - 6f, 7.6f, Color.BLACK)
        c.drawLine(colIzq + 70f, mitad - 6f, r.right - 10f, mitad - 6f, linea())
        texto(c, "Cliente", colIzq + 11f, r.bottom - 6f, 7.6f, Color.BLACK)
        c.drawLine(colIzq + 70f, r.bottom - 6f, r.right - 10f, r.bottom - 6f, linea())
    }

    // ===================== AYUDANTES =====================

    /** Tabla de 3 columnas (concepto | valor | unidad) con bordes. */
    private fun tabla(
        c: Canvas, filas: List<Triple<String, String, String>>, l: Float, t: Float, r: Float, altoFila: Float,
        tam: Float, resaltarUltima: Boolean = false, resaltar: Set<Int> = emptySet(),
    ) {
        val ancho = r - l
        val x1 = l + ancho * 0.56f
        val x2 = l + ancho * 0.86f
        filas.forEachIndexed { i, (concepto, valor, unidad) ->
            val top = t + i * altoFila
            val base = top + altoFila / 2f + tam * 0.36f
            val negrita = i in resaltar || (resaltarUltima && i == filas.lastIndex)
            borde(c, RectF(l, top, r, top + altoFila))
            c.drawLine(x1, top, x1, top + altoFila, linea())
            c.drawLine(x2, top, x2, top + altoFila, linea())
            texto(c, recortar(concepto, x1 - l - 4f, paint(tam, Color.BLACK, false)), l + 3f, base, tam, Color.BLACK)
            textoCentro(c, recortar(valor, x2 - x1 - 2f, paint(tam, Color.BLACK, negrita)), (x1 + x2) / 2f, base, tam, Color.BLACK, negrita)
            textoCentro(c, unidad, (x2 + r) / 2f, base, tam, Color.BLACK, negrita)
        }
    }

    private fun num(v: Double) = String.format(Locale.US, "%.2f", v)
    private fun pct(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)

    private fun linea() = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GRIS_BORDE; strokeWidth = 0.6f }

    private fun rect(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        c.drawRect(l, t, r, b, Paint().apply { this.color = color; style = Paint.Style.FILL })
    }

    private fun borde(c: Canvas, r: RectF) {
        c.drawRect(r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GRIS_BORDE; style = Paint.Style.STROKE; strokeWidth = 0.7f })
    }

    private fun paint(tam: Float, color: Int, negrita: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = tam
        this.color = color
        typeface = PdfFuentes.tipo(negrita)
    }

    private fun texto(c: Canvas, t: String, x: Float, y: Float, tam: Float, color: Int, negrita: Boolean = false) {
        c.drawText(t, x, y, paint(tam, color, negrita))
    }

    private fun textoDerecha(c: Canvas, t: String, xDerecha: Float, y: Float, tam: Float, color: Int, negrita: Boolean = false) {
        val p = paint(tam, color, negrita)
        c.drawText(t, xDerecha - p.measureText(t), y, p)
    }

    private fun textoCentro(c: Canvas, t: String, x: Float, y: Float, tam: Float, color: Int, negrita: Boolean = false) {
        val p = paint(tam, color, negrita)
        c.drawText(t, x - p.measureText(t) / 2f, y, p)
    }

    private fun recortar(t: String, ancho: Float, p: Paint): String {
        if (p.measureText(t) <= ancho) return t
        var s = t
        while (s.length > 1 && p.measureText("$s…") > ancho) s = s.dropLast(1)
        return "$s…"
    }

    /** Parte un texto en líneas que entren en [ancho] (máximo [maxLineas]; la última se recorta). */
    private fun partir(t: String, ancho: Float, p: Paint, maxLineas: Int): List<String> {
        val lineas = mutableListOf<String>()
        var actual = ""
        for (palabra in t.split(" ").filter { it.isNotEmpty() }) {
            val prueba = if (actual.isEmpty()) palabra else "$actual $palabra"
            if (p.measureText(prueba) <= ancho || actual.isEmpty()) {
                actual = prueba
            } else {
                lineas.add(actual)
                actual = palabra
            }
        }
        if (actual.isNotEmpty()) lineas.add(actual)
        if (lineas.isEmpty()) return listOf("")
        if (lineas.size <= maxLineas) return lineas.map { recortar(it, ancho, p) }
        val corte = lineas.take(maxLineas).toMutableList()
        corte[maxLineas - 1] = recortar(lineas.drop(maxLineas - 1).joinToString(" "), ancho, p)
        return corte
    }
}
