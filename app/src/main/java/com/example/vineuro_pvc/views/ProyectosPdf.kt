package com.example.vineuro_pvc.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.example.vineuro_pvc.data.CalculadoraPrecios
import com.example.vineuro_pvc.data.OrderItem
import com.example.vineuro_pvc.data.RequisitosDocumento
import com.example.vineuro_pvc.data.TextoPropiedades
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PDF de proyectos (cotización con los dibujos de las ventanas), en dos formatos:
 *
 * - SOLO_PROYECTOS: hasta 6 diseños por hoja (2 columnas x 3 filas). Cada diseño con su número, el dibujo con
 *   sus cotas, a la derecha el nombre y los parámetros, y abajo a la derecha una tabla pequeña con área, precio y
 *   cantidad. Si el pedido tiene 1 o 2 ventanas, cada una va en un bloque horizontal ancho. Marco blanco.
 * - CON_DETALLES: 2 diseños por hoja en bloques anchos (dibujo 2/3, datos 1/3), el dibujo pintado con el color
 *   elegido, los parámetros en recuadros grises y la tabla completa de precios.
 *
 * Al final de los dos formatos va "toda la orden" (totales, descuento, IGV) con las firmas y la fecha, SIEMPRE en
 * la última hoja junto con los últimos diseños (nunca sola en una hoja en blanco).
 *
 * Todo se dibuja en puntos de una hoja A4 (595 x 842) con [dibujarPagina]: la misma función sirve para el archivo
 * y para la vista previa de la app, así que las dos muestran exactamente lo mismo.
 */
object ProyectosPdf {

    enum class Modo { SOLO_PROYECTOS, CON_DETALLES }

    data class Datos(
        val requisitos: RequisitosDocumento,
        val clienteNombre: String,
        val clienteDireccion: String,
        val clienteTelefono: String,
        val clienteCorreo: String,
        /** Se imprime como "ID de oferta" (N° del pedido). */
        val idOferta: String,
        val items: List<OrderItem>,
        /** Resultado de la calculadora de cada ventana (mismo orden que [items]; null = no se pudo calcular). */
        val resultados: List<CalculadoraPrecios.Resultado?>,
        /** Nombre del color (tabla "colores" de Precios) -> color para pintar el perfil. */
        val colores: Map<String, Int> = emptyMap(),
        /** Logo de la empresa (null = sin logo). */
        val logo: Bitmap? = null,
        val impuestoNombre: String = "IGV",
        val fecha: Date = Date(),
    )

    /** Rectángulo en puntos de la hoja (sin dependencias de Android, para poder probar la paginación con JUnit). */
    data class Caja(val l: Float, val t: Float, val r: Float, val b: Float) {
        val ancho: Float get() = r - l
        val alto: Float get() = b - t
        fun rect() = RectF(l, t, r, b)
    }

    sealed class Elemento {
        data class Encabezado(val caja: Caja) : Elemento()
        data class Bloque(val indice: Int, val caja: Caja, val compacto: Boolean) : Elemento()
        data class Pie(val caja: Caja) : Elemento()
    }

    const val W = 595f
    const val H = 842f
    const val M = 20f
    const val ALTO_ENCABEZADO = 112f
    const val ALTO_PIE = 112f
    const val SEPARACION = 6f
    /** Alto del bloque ancho (2 por hoja) y de la celda de la cuadrícula (2 x 3 por hoja). */
    const val ALTO_BLOQUE = 334f
    const val ALTO_CELDA = 224f
    /** Límite inferior del contenido: debajo queda el "Página x de y". */
    const val ABAJO = H - 26f

    // Colores como enteros ARGB (sin Color.parseColor, para que el objeto cargue también en las pruebas JUnit)
    private const val GRIS_BORDE = 0xFF9CA3AF.toInt()
    private const val GRIS_FONDO = 0xFFE5E7EB.toInt()
    private const val GRIS_ETIQUETA = 0xFFD9D9D9.toInt()
    private const val GRIS_TEXTO = 0xFF4B5563.toInt()
    private const val NEGRO = 0xFF000000.toInt()
    private const val BLANCO = 0xFFFFFFFF.toInt()

    // ===================== PAGINACIÓN =====================

    fun paginar(d: Datos, modo: Modo): List<List<Elemento>> = paginar(d.items.size, modo)

    /**
     * Reparte [cantidad] diseños en hojas. Primero se calcula cuántas hojas hacen falta (2 o 6 diseños por hoja) y
     * en la última se reserva el espacio de "toda la orden" + firmas: el pie SIEMPRE queda junto con los últimos
     * diseños, nunca solo en una hoja en blanco. Si no entra, los diseños de esa hoja se achican.
     */
    fun paginar(cantidad: Int, modo: Modo): List<List<Elemento>> {
        val n = cantidad.coerceAtLeast(0)
        val compacto = modo == Modo.SOLO_PROYECTOS && n > 2
        val porHoja = if (compacto) 6 else 2
        val totalHojas = ((n + porHoja - 1) / porHoja).coerceAtLeast(1)
        val paginas = mutableListOf<List<Elemento>>()

        for (h in 0 until totalHojas) {
            val elementos = mutableListOf<Elemento>()
            var top = M
            if (h == 0) {
                elementos.add(Elemento.Encabezado(Caja(M, M, W - M, M + ALTO_ENCABEZADO)))
                top = M + ALTO_ENCABEZADO + SEPARACION
            }
            val ultima = h == totalHojas - 1
            val fondo = if (ultima) ABAJO - ALTO_PIE - SEPARACION else ABAJO
            val desde = h * porHoja
            val indices = (desde until minOf(desde + porHoja, n)).toList()

            if (compacto) {
                // En las hojas intermedias siempre se reservan 3 filas; en la última, solo las que hay
                val filas = ((indices.size + 1) / 2).coerceAtLeast(1)
                val altoFila = minOf(ALTO_CELDA, (fondo - top) / (if (ultima) filas else 3))
                val ancho = (W - 2 * M) / 2f
                indices.forEachIndexed { k, i ->
                    val l = M + (k % 2) * ancho
                    val t = top + (k / 2) * altoFila
                    elementos.add(Elemento.Bloque(i, Caja(l, t, l + ancho, t + altoFila), true))
                }
            } else {
                val altoBloque = minOf(ALTO_BLOQUE, (fondo - top) / (if (ultima) indices.size.coerceAtLeast(1) else 2))
                indices.forEachIndexed { k, i ->
                    val t = top + k * altoBloque
                    elementos.add(Elemento.Bloque(i, Caja(M, t, W - M, t + altoBloque), false))
                }
            }
            if (ultima) elementos.add(Elemento.Pie(Caja(M, ABAJO - ALTO_PIE, W - M, ABAJO)))
            paginas.add(elementos)
        }
        return paginas
    }

    // ===================== DIBUJO DE UNA HOJA =====================

    /**
     * Dibuja la hoja [n] en coordenadas de la hoja A4 (595 x 842). La usan el archivo PDF y la vista previa
     * (que solo escala el lienzo al ancho de la pantalla).
     */
    fun dibujarPagina(context: Context, c: Canvas, d: Datos, modo: Modo, paginas: List<List<Elemento>>, n: Int) {
        PdfFuentes.cargar(context)
        c.drawRect(0f, 0f, W, H, relleno(BLANCO))
        val dibujante = WindowPreviewView(context).apply { modoImpresion = true }
        paginas.getOrNull(n)?.forEach { e ->
            when (e) {
                is Elemento.Encabezado -> encabezado(c, d, e.caja)
                is Elemento.Bloque -> bloque(context, c, d, modo, dibujante, e)
                is Elemento.Pie -> pie(c, d, e.caja)
            }
        }
        if (paginas.size > 1) {
            textoDerecha(c, "Página ${n + 1} de ${paginas.size}", W - M, H - 12f, 6.5f, GRIS_TEXTO)
        }
    }

    /** Genera el archivo PDF completo (una página del documento por cada hoja de [paginar]). */
    fun crearArchivo(context: Context, d: Datos, modo: Modo, archivo: File): File {
        val paginas = paginar(d, modo)
        val pdf = PdfDocument()
        try {
            paginas.indices.forEach { n ->
                val pg = pdf.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), n + 1).create())
                dibujarPagina(context, pg.canvas, d, modo, paginas, n)
                pdf.finishPage(pg)
            }
            FileOutputStream(archivo).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return archivo
    }

    // ===================== ENCABEZADO =====================

    private fun encabezado(c: Canvas, d: Datos, r: Caja) {
        val req = d.requisitos
        // Banda gris con el título
        val banda = 14f
        rect(c, r.l, r.t, r.r, r.t + banda, GRIS_FONDO)
        textoCentro(c, "COTIZACIÓN", (r.l + r.r) / 2f, base(r.t, banda, 9.5f), 9.5f, NEGRO, negrita = true)

        // Tres columnas: empresa | logo | cliente
        val colLat = r.ancho * 0.37f
        val izq = Caja(r.l, r.t + banda, r.l + colLat, r.b)
        val centro = Caja(izq.r, r.t + banda, r.r - colLat, r.b)
        val der = Caja(r.r - colLat, r.t + banda, r.r, r.b)

        filasDatos(c, izq, listOf(
            "EJECUTANTE" to req.ejecutante,
            "RUC" to req.ruc,
            "dirección" to req.direccion,
            "teléfono" to req.telefono,
            "correo electrónico" to req.correo,
            "página web" to req.paginaWeb,
        ))

        // Logo escalado sin deformarse
        d.logo?.takeIf { it.width > 0 && it.height > 0 }?.let { logo ->
            val caja = Caja(centro.l + 6f, centro.t + 4f, centro.r - 6f, centro.b - 4f)
            val esc = minOf(caja.ancho / logo.width, caja.alto / logo.height)
            val w = logo.width * esc
            val h = logo.height * esc
            val l = caja.l + (caja.ancho - w) / 2f
            val t = caja.t + (caja.alto - h) / 2f
            c.drawBitmap(logo, null, RectF(l, t, l + w, t + h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        }

        filasDatos(c, der, listOf(
            "CLIENTE" to d.clienteNombre,
            "dirección" to d.clienteDireccion,
            "teléfono" to d.clienteTelefono,
            "correo electrónico" to d.clienteCorreo,
            "ID de oferta" to d.idOferta,
        ))
    }

    /**
     * Filas "etiqueta : valor": la etiqueta alineada a la derecha contra los dos puntos y el valor a la izquierda.
     * El valor puede ocupar 2 líneas mientras quede espacio para las filas que siguen; si no, se recorta con "…".
     */
    private fun filasDatos(c: Canvas, caja: Caja, filas: List<Pair<String, String>>) {
        val tam = 7f
        val linea = 8.4f   // alto de una línea de valor
        val paso = 9.8f    // alto de una fila de una línea
        val pEtq = paint(tam, GRIS_TEXTO, false)
        val pVal = paint(tam, NEGRO, false)
        val anchoEtq = filas.maxOf { pEtq.measureText("${it.first} :") }
        val xDosPuntos = caja.l + 3f + anchoEtq
        val xValor = xDosPuntos + 3f
        val anchoValor = caja.r - 3f - xValor
        var y = caja.t + 10f  // línea base de la primera fila
        filas.forEachIndexed { i, (etq, valor) ->
            val restantes = filas.size - i - 1
            val cabenDos = y + linea + restantes * paso <= caja.b - 2f
            val lineas = partir(valor.ifBlank { "......" }, anchoValor, pVal, if (cabenDos) 2 else 1)
            textoDerecha(c, "$etq :", xDosPuntos, y, tam, GRIS_TEXTO)
            lineas.forEachIndexed { k, l -> texto(c, l, xValor, y + k * linea, tam, NEGRO) }
            y += paso + (lineas.size - 1) * linea
        }
    }

    // ===================== BLOQUE DE UNA VENTANA =====================

    /** Importes de una ventana, sacados del total con descuento e impuesto de la calculadora. */
    private data class Importes(
        val area: Double,          // m2 de UNA ventana
        val cantidad: Int,
        val unitario: Double,      // precio de una ventana sin descuento ni impuesto
        val unitarioDesc: Double,  // con descuento, sin impuesto
        val descuentoPct: Double,
        val impuestoPct: Double,
        val totalConImpuesto: Double,
        val simbolo: String,
    ) {
        val costo: Double get() = unitario * cantidad
        val conDescuento: Double get() = unitarioDesc * cantidad
    }

    private fun importes(item: OrderItem, res: CalculadoraPrecios.Resultado?): Importes {
        val area = item.areaM2
        val cantidad = (res?.cantidad ?: item.opciones.cantidad).coerceAtLeast(1)
        if (res == null) return Importes(area, cantidad, 0.0, 0.0, 0.0, 0.0, 0.0, "S/")
        val fImp = 1 + res.impuestoPct / 100.0
        val fDesc = 1 - res.descuentoPct / 100.0
        // precio unitario con descuento = total / cantidad / (1 + IGV); sin descuento = con descuento / (1 - descuento)
        val unitDesc = res.totalVenta / cantidad / fImp
        val unit = if (fDesc > 0.0001) unitDesc / fDesc else unitDesc
        return Importes(area, cantidad, unit, unitDesc, res.descuentoPct, res.impuestoPct, res.totalVenta, res.simbolo)
    }

    /** Parámetros elegidos en el compás que tienen valor: (título, valor). */
    private fun parametros(item: OrderItem): List<Pair<String, String>> {
        val op = item.opciones
        val lista = mutableListOf<Pair<String, String>>()
        fun add(t: String, v: String?) { if (!v.isNullOrBlank()) lista.add(t to v.trim()) }
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
        // "adicionales" incluye los perfiles por lado (juntas, huecos, extensores) tal como los describe la app
        val adicionales = TextoPropiedades.categorias(item).firstOrNull { it.titulo == "Adiciones por tamaño" }?.lineas.orEmpty()
        if (adicionales.isNotEmpty()) lista.add("adicionales" to adicionales.joinToString(", "))
        return lista
    }

    /** Color del perfil: el elegido; si no hay, el de la laminación externa; si tampoco, blanco. */
    private fun colorPerfil(item: OrderItem, d: Datos): Int =
        item.opciones.color?.trim()?.let { d.colores[it] }
            ?: item.opciones.laminacionExterna?.trim()?.let { d.colores[it] }
            ?: BLANCO

    private fun bloque(context: Context, c: Canvas, d: Datos, modo: Modo, dib: WindowPreviewView, e: Elemento.Bloque) {
        val item = d.items.getOrNull(e.indice) ?: return
        val imp = importes(item, d.resultados.getOrNull(e.indice))
        val r = e.caja
        val comp = e.compacto
        val detalles = modo == Modo.CON_DETALLES
        borde(c, r)

        // Número del diseño (arriba a la izquierda)
        val tamNum = if (comp) 11f else 13f
        texto(c, "${item.index}", r.l + 6f, r.t + 4f + tamNum * 0.85f, tamNum, NEGRO, negrita = true)

        // Columna derecha: (cantidad), nombre, parámetros y tabla
        val panelW = if (comp) r.ancho * 0.44f else r.ancho / 3f
        val pr = r.r - 6f
        val pl = pr - panelW
        var y = r.t + 5f   // borde superior de la siguiente línea de texto

        if (detalles && imp.cantidad > 1) {
            val tamC = 11f
            val pc = "${imp.cantidad}pc"
            val pC = paint(tamC, NEGRO, true)
            val yb = y + tamC * 0.85f
            c.drawText(pc, pr - pC.measureText(pc), yb, pC)
            textoDerecha(c, "cantidad ", pr - pC.measureText(pc), yb, 7f, NEGRO)
            y += tamC + 2f
        }

        val titulo = item.opciones.nota.trim().ifBlank { item.config.nombre }
        val tamTit = if (comp) 6.8f else 8f
        val pTit = paint(tamTit, NEGRO, true)
        partir(titulo, panelW, pTit, 2).forEach { l ->
            y += tamTit + 1.6f
            textoDerecha(c, l, pr, y - 1.6f, tamTit, NEGRO, negrita = true)
        }
        y += 4f

        // Tabla de importes (abajo a la derecha)
        val s = imp.simbolo
        val filas = mutableListOf<Triple<String, String, String>>()
        val resaltar = mutableSetOf<Int>()
        filas.add(Triple("área de productos", num(imp.area), "m2"))
        filas.add(Triple("precio", num(imp.unitario), s))
        if (detalles) {
            if (imp.descuentoPct > 0) filas.add(Triple("Precio descontado", "${num(imp.unitarioDesc)}(-${pct(imp.descuentoPct)}%)", s))
            filas.add(Triple("cantidad", "${imp.cantidad}", "pc"))
            filas.add(Triple("costo", num(imp.costo), s))
            if (imp.descuentoPct > 0) {
                resaltar.add(filas.size)
                filas.add(Triple("precio con descuento", num(imp.conDescuento), s))
            }
        } else {
            filas.add(Triple("cantidad", "${imp.cantidad}", "pc"))
        }
        val altoFila = if (comp) 10f else 11.5f
        val tamTabla = if (comp) 5.8f else 6.4f
        val tablaTop = r.b - 5f - filas.size * altoFila
        tabla(c, filas, pl, tablaTop, pr, altoFila, tamTabla, resaltar)

        // Parámetros: título en recuadro gris alineado a la derecha y debajo el valor terminado en "●".
        // Solo los que tienen valor y los que entran antes de la tabla.
        val tamP = if (comp) 5.2f else 6.4f
        val altoEtq = tamP + 2.8f
        val lineaV = tamP + 2.2f
        val pP = paint(tamP, NEGRO, false)
        val anchoBola = pP.measureText("●")
        for ((etq, valor) in parametros(item)) {
            val lineas = partir(valor, panelW - anchoBola, pP, 2)
            val alto = altoEtq + lineas.size * lineaV + 2.5f
            if (y + alto > tablaTop - 3f) break
            val etqTxt = recortar(etq, panelW - 4f, pP)
            val we = pP.measureText(etqTxt) + 4f
            rect(c, pr - we, y, pr, y + altoEtq, GRIS_ETIQUETA)
            textoDerecha(c, etqTxt, pr - 2f, base(y, altoEtq, tamP), tamP, NEGRO)
            y += altoEtq
            lineas.forEachIndexed { k, l ->
                val txt = if (k == lineas.lastIndex) "$l●" else l
                textoDerecha(c, txt, pr, base(y, lineaV, tamP), tamP, NEGRO)
                y += lineaV
            }
            y += 2.5f
        }

        // Dibujo de la ventana con sus cotas (a la izquierda). El dibujo trabaja en dp: se dibuja k veces más grande
        // y se reduce, para que las cotas tengan buen tamaño en la hoja.
        val area = Caja(r.l + 4f, r.t + (if (comp) 16f else 21f), pl - 4f, r.b - 4f)
        if (area.ancho <= 4f || area.alto <= 4f) return
        val k = context.resources.displayMetrics.density * (if (comp) 1.25f else 1.0f)
        val color = if (detalles) colorPerfil(item, d) else BLANCO
        c.save()
        c.clipRect(area.rect())
        c.scale(1f / k, 1f / k)
        dib.drawItem(
            c, RectF(area.l * k, area.t * k, area.r * k, area.b * k), item,
            showTechnicalDetails = true, isCompactMode = comp, colorMarco = color,
        )
        c.restore()
    }

    // ===================== PIE: TODA LA ORDEN + FIRMAS =====================

    private fun pie(c: Canvas, d: Datos, r: Caja) {
        val lista = d.items.mapIndexed { i, it -> importes(it, d.resultados.getOrNull(i)) }
        val cantidad = lista.sumOf { it.cantidad }
        val area = lista.sumOf { it.area * it.cantidad }
        val costo = lista.sumOf { it.costo }
        val conDesc = lista.sumOf { it.conDescuento }
        val total = lista.sumOf { it.totalConImpuesto }
        val impuesto = total - conDesc
        val s = lista.firstOrNull()?.simbolo ?: "S/"
        val impPct = lista.firstOrNull()?.impuestoPct ?: 0.0
        // Descuento: el de las ventanas si es el mismo en todas; si no, el promedio efectivo
        val descuentos = lista.map { it.descuentoPct }.distinct()
        val descPct = when {
            descuentos.size == 1 -> descuentos[0]
            costo > 0 -> (1 - conDesc / costo) * 100
            else -> 0.0
        }
        val txtDesc = if (descPct > 0.0001) " (-${pct(descPct)}%)" else ""

        val colIzq = r.l + r.ancho * 0.45f
        val cab = 14f
        val tamCab = 8f
        // Encabezados grises
        rect(c, r.l, r.t, colIzq, r.t + cab, GRIS_FONDO)
        rect(c, colIzq + 8f, r.t, r.r, r.t + cab, GRIS_FONDO)
        texto(c, "toda la orden", r.l + 5f, base(r.t, cab, tamCab), tamCab, NEGRO, negrita = true)
        texto(c, "Firmas", colIzq + 13f, base(r.t, cab, tamCab), tamCab, NEGRO, negrita = true)
        textoDerecha(c, fecha(d.fecha), r.r - 5f, base(r.t, cab, tamCab), tamCab, NEGRO)

        val filas = listOf(
            Triple("cantidad", "$cantidad", "pc"),
            Triple("toda el área de productos", num(area), "m2"),
            Triple("costo", num(costo), s),
            Triple("precio con descuento$txtDesc", num(conDesc), s),
            Triple("el impuesto: ${d.impuestoNombre} ${pct(impPct)}%", num(impuesto), s),
            Triple("precio con descuento + ${d.impuestoNombre}", num(total), s),
        )
        val altoFila = (r.alto - cab) / filas.size
        tabla(c, filas, r.l, r.t + cab, colIzq, altoFila, 7.4f, resaltar = setOf(3, 5))

        // Firmas: "Ejecutante" y "Cliente" con su línea
        val tamF = 7.6f
        val xF = colIzq + 13f
        val xLinea = xF + paint(tamF, NEGRO, false).measureText("Ejecutante") + 8f
        val y1 = r.t + cab + (r.alto - cab) * 0.45f
        val y2 = r.b - 8f
        for ((etq, yl) in listOf("Ejecutante" to y1, "Cliente" to y2)) {
            texto(c, etq, xF, yl, tamF, NEGRO)
            c.drawLine(xLinea, yl + 1f, r.r - 8f, yl + 1f, linea(0.7f))
        }
    }

    // ===================== AYUDANTES =====================

    /** Tabla de 3 columnas (concepto | valor | unidad) con bordes; las filas de [resaltar] van en negrita. */
    private fun tabla(
        c: Canvas, filas: List<Triple<String, String, String>>, l: Float, t: Float, r: Float, altoFila: Float,
        tam: Float, resaltar: Set<Int> = emptySet(),
    ) {
        val ancho = r - l
        val x1 = l + ancho * 0.56f
        val x2 = l + ancho * 0.86f
        val borde = linea(0.6f)
        filas.forEachIndexed { i, (concepto, valor, unidad) ->
            val top = t + i * altoFila
            val yb = base(top, altoFila, tam)
            val negrita = i in resaltar
            c.drawRect(l, top, r, top + altoFila, Paint(borde).apply { style = Paint.Style.STROKE })
            c.drawLine(x1, top, x1, top + altoFila, borde)
            c.drawLine(x2, top, x2, top + altoFila, borde)
            texto(c, recortar(concepto, x1 - l - 5f, paint(tam, NEGRO, negrita)), l + 3f, yb, tam, NEGRO, negrita)
            textoCentro(c, recortar(valor, x2 - x1 - 3f, paint(tam, NEGRO, negrita)), (x1 + x2) / 2f, yb, tam, NEGRO, negrita)
            textoCentro(c, recortar(unidad, r - x2 - 2f, paint(tam, NEGRO, negrita)), (x2 + r) / 2f, yb, tam, NEGRO, negrita)
        }
    }

    /** Línea base para centrar verticalmente un texto de tamaño [tam] en una franja [top, top + alto]. */
    private fun base(top: Float, alto: Float, tam: Float) = top + alto / 2f + tam * 0.34f

    /** Números con 2 decimales y punto decimal. */
    fun num(v: Double): String = String.format(Locale.US, "%.2f", v)

    /** Porcentaje sin decimales si es entero ("10"), si no con uno ("12.5"). */
    fun pct(v: Double): String {
        val r = Math.round(v * 10) / 10.0
        return if (r % 1.0 == 0.0) r.toLong().toString() else String.format(Locale.US, "%.1f", r)
    }

    /** Fecha como "13 oct 2026". */
    fun fecha(f: Date): String =
        SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("es-PE")).format(f).replace(".", "").lowercase(Locale.ROOT)

    private fun linea(grosor: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GRIS_BORDE; strokeWidth = grosor }

    private fun relleno(color: Int) = Paint().apply { this.color = color; style = Paint.Style.FILL }

    private fun rect(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) = c.drawRect(l, t, r, b, relleno(color))

    private fun borde(c: Canvas, r: Caja) {
        c.drawRect(r.rect(), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GRIS_BORDE; style = Paint.Style.STROKE; strokeWidth = 0.7f })
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

    /** Recorta con "…" un texto que no entra en [ancho]. */
    private fun recortar(t: String, ancho: Float, p: Paint): String {
        if (p.measureText(t) <= ancho) return t
        var s = t
        while (s.isNotEmpty() && p.measureText("${s.trimEnd()}…") > ancho) s = s.dropLast(1)
        return "${s.trimEnd()}…"
    }

    /** Parte un texto en líneas que entren en [ancho] (máximo [maxLineas]; la última se recorta con "…"). */
    private fun partir(t: String, ancho: Float, p: Paint, maxLineas: Int): List<String> {
        val lineas = mutableListOf<String>()
        var actual = ""
        for (palabra in t.replace('\n', ' ').split(" ").filter { it.isNotEmpty() }) {
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
        return corte.map { recortar(it, ancho, p) }
    }
}
