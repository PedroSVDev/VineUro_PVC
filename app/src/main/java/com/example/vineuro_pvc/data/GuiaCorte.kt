package com.example.vineuro_pvc.data

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Guía de corte: lista de piezas de perfil (marco, impostas, hojas, junquillos, barrotillos,
 * mosquitero), vidrios / paneles y la optimización de barras para todo el pedido.
 *
 * Las medidas salen de la geometría real de cada ventana (GeometriaVentana) y de los
 * DESCUENTOS de [Parametros]. Son valores genéricos de perfilería PVC: se deben revisar con
 * el sistema de perfiles que se use (cada fabricante tiene sus propios descuentos).
 */
object GuiaCorte {

    data class Parametros(
        val marcoVisto: Int = 60,        // ancho del marco que se ve (mm)
        val impostaVisto: Int = 80,      // ancho de la imposta (mm), se descuenta la mitad a cada lado
        val hojaVisto: Int = 75,         // ancho de la hoja que se ve (mm)
        val traslapeHoja: Int = 8,       // cuánto monta la hoja sobre el marco/imposta por lado (mm)
        val soldadura: Int = 3,          // pérdida por soldadura en cada extremo a 45° (mm)
        val galce: Int = 20,             // cuánto entra el vidrio en el perfil por lado (mm)
        val holguraVidrio: Int = 5,      // holgura del vidrio por lado (mm)
        val largoBarra: Int = 6000,      // largo de la barra de perfil (mm)
        val despunte: Int = 10,          // lo que se descarta en cada punta de la barra (mm)
        val anchoSierra: Int = 4,        // material que se come cada corte (mm)
    )

    /** Una pieza de perfil a cortar. */
    data class Pieza(
        val ventana: Int,
        val perfil: String,
        val largo: Int,
        val angulo: String,     // "45°" (soldado) o "90°"
        val cantidad: Int,
        val ubicacion: String,
    )

    /** Un vidrio o panel sándwich. */
    data class Vidrio(
        val ventana: Int,
        val ubicacion: String,
        val ancho: Int,
        val alto: Int,
        val cantidad: Int,
        val panel: Boolean,
    ) {
        val m2: Double get() = ancho / 1000.0 * alto / 1000.0 * cantidad
    }

    data class Ventana(
        val item: OrderItem,
        val piezas: List<Pieza>,
        val vidrios: List<Vidrio>,
        val aproximado: Boolean,
    )

    /** Una barra con sus cortes. */
    data class Barra(val cortes: List<Int>, val sobrante: Int)

    data class Optimizacion(val perfil: String, val barras: List<Barra>, val totalPiezas: Int, val metrosUsados: Double) {
        val desperdicioPct: Double
            get() {
                val total = barras.sumOf { it.cortes.sum() + it.sobrante }.toDouble()
                return if (total > 0) barras.sumOf { it.sobrante }.toDouble() / total * 100 else 0.0
            }
    }

    const val MARCO = "Marco"
    const val IMPOSTA = "Imposta"
    const val HOJA = "Hoja"
    const val HOJA_PUERTA = "Hoja de puerta"
    const val JUNQUILLO = "Junquillo"
    const val BARROTILLO = "Barrotillo"
    const val MOSQUITERO = "Marco de mosquitero"

    // ===================== PIEZAS DE UNA VENTANA =====================

    fun ventana(item: OrderItem, p: Parametros = Parametros()): Ventana {
        val g = GeometriaVentana.calcular(item)
        val cant = max(1, item.opciones.cantidad)
        val piezas = mutableListOf<Pieza>()
        val vidrios = mutableListOf<Vidrio>()
        val n = item.index
        val sold = 2 * p.soldadura
        fun r(v: Float) = v.roundToInt()

        // 1) Marco: 2 horizontales (ancho) + 2 verticales (alto), a 45°
        piezas.add(Pieza(n, MARCO, r(g.ancho) + sold, "45°", 2 * cant, "arriba y abajo"))
        piezas.add(Pieza(n, MARCO, r(g.alto) + sold, "45°", 2 * cant, "izquierda y derecha"))

        // Descuento en cada borde: marco si es el borde exterior, media imposta si es interior
        fun bordeX(x: Float) = if (x <= 0.5f || x >= g.ancho - 0.5f) p.marcoVisto.toFloat() else p.impostaVisto / 2f
        fun bordeY(y: Float) = if (y <= 0.5f || y >= g.alto - 0.5f) p.marcoVisto.toFloat() else p.impostaVisto / 2f

        // 2) Impostas principales, a 90°
        g.impostas.forEach { d ->
            val largo = if (d.vertical) d.hasta - d.desde - bordeY(d.desde) - bordeY(d.hasta)
            else d.hasta - d.desde - bordeX(d.desde) - bordeX(d.hasta)
            if (largo > 0) piezas.add(Pieza(n, IMPOSTA, r(largo), "90°", cant, if (d.vertical) "vertical" else "horizontal"))
        }

        // 3) Cada paño
        g.celdas.forEach { c ->
            val luzW = c.x1 - c.x0 - bordeX(c.x0) - bordeX(c.x1)
            val luzH = c.y1 - c.y0 - bordeY(c.y0) - bordeY(c.y1)
            if (luzW <= 0 || luzH <= 0) return@forEach
            val idx = c.indice
            val nombrePano = "paño ${idx + 1}"
            val filas = item.paneRows[idx] ?: 1
            val columnas = item.paneCols[idx] ?: 1
            val totalPartes = filas * columnas
            val mascara = item.paneSandwichMask[idx]
                ?: if (item.paneSandwich[idx] == true) (1 shl totalPartes) - 1 else 0
            val barras = item.paneBarras[idx] ?: 0
            val puerta = item.panePuerta[idx] == true
            val mosq = item.paneMosqNet[idx] == true
            val partes = item.panePartMechanisms[idx]?.split(",")?.map { it.trim() }
                ?.takeIf { totalPartes > 1 && it.size == totalPartes }

            if (mosq) {
                piezas.add(Pieza(n, MOSQUITERO, r(luzW), "45°", 2 * cant, nombrePano))
                piezas.add(Pieza(n, MOSQUITERO, r(luzH), "45°", 2 * cant, nombrePano))
            }

            if (partes != null) {
                // Apertura combinada: impostas dentro del paño y cada parte es una hoja o un fijo
                val zonas = dividir(luzW, luzH, filas, columnas, p.impostaVisto.toFloat())
                impostasInternas(piezas, n, luzW, luzH, filas, columnas, p, cant, nombrePano)
                zonas.forEachIndexed { i, (zw, zh) ->
                    val bit = ((mascara shr i) and 1) == 1
                    hojaYVidrio(piezas, vidrios, n, zw, zh, partes[i] != "5", puerta, 1, 1, if (bit) 1 else 0, barras, p, cant,
                        "$nombrePano, parte ${i + 1}")
                }
            } else {
                hojaYVidrio(piezas, vidrios, n, luzW, luzH, (item.paneMechanisms[idx] ?: "5") != "5", puerta,
                    filas, columnas, mascara, barras, p, cant, nombrePano)
            }
        }
        return Ventana(item, agrupar(piezas), agruparVidrios(vidrios), g.aproximado)
    }

    /** Hoja (si abre o es puerta) + impostas internas del vidrio + junquillos + vidrios/paneles + barrotillos. */
    private fun hojaYVidrio(
        piezas: MutableList<Pieza>, vidrios: MutableList<Vidrio>, n: Int,
        luzW: Float, luzH: Float, abre: Boolean, puerta: Boolean,
        filas: Int, columnas: Int, mascara: Int, barras: Int, p: Parametros, cant: Int, donde: String,
    ) {
        fun r(v: Float) = v.roundToInt()
        var zonaW = luzW
        var zonaH = luzH
        if (abre || puerta) {
            val hw = luzW + 2 * p.traslapeHoja
            val hh = luzH + 2 * p.traslapeHoja
            val perfil = if (puerta) HOJA_PUERTA else HOJA
            piezas.add(Pieza(n, perfil, r(hw) + 2 * p.soldadura, "45°", 2 * cant, donde))
            piezas.add(Pieza(n, perfil, r(hh) + 2 * p.soldadura, "45°", 2 * cant, donde))
            zonaW = hw - 2 * p.hojaVisto
            zonaH = hh - 2 * p.hojaVisto
        }
        if (zonaW <= 0 || zonaH <= 0) return
        impostasInternas(piezas, n, zonaW, zonaH, filas, columnas, p, cant, donde)
        val bf = barras / 100
        val bc = barras % 100
        dividir(zonaW, zonaH, filas, columnas, p.impostaVisto.toFloat()).forEachIndexed { i, (vw, vh) ->
            val parte = if (filas * columnas > 1) "$donde, parte ${i + 1}" else donde
            // junquillos del vidrio
            piezas.add(Pieza(n, JUNQUILLO, r(vw), "45°", 2 * cant, parte))
            piezas.add(Pieza(n, JUNQUILLO, r(vh), "45°", 2 * cant, parte))
            val extra = 2 * (p.galce - p.holguraVidrio)
            val esPanel = ((mascara shr i) and 1) == 1
            vidrios.add(Vidrio(n, parte, r(vw) + extra, r(vh) + extra, cant, esPanel))
            if (!esPanel && barras > 0) {
                if (bc > 1) piezas.add(Pieza(n, BARROTILLO, r(vh), "90°", (bc - 1) * cant, parte))
                if (bf > 1) piezas.add(Pieza(n, BARROTILLO, r(vw), "90°", (bf - 1) * cant, parte))
            }
        }
    }

    /** Impostas que dividen una zona en filas x columnas (verticales enteras, horizontales por tramo). */
    private fun impostasInternas(
        piezas: MutableList<Pieza>, n: Int, w: Float, h: Float, filas: Int, columnas: Int, p: Parametros, cant: Int, donde: String,
    ) {
        val i = p.impostaVisto.toFloat()
        if (columnas > 1) piezas.add(Pieza(n, IMPOSTA, h.roundToInt(), "90°", (columnas - 1) * cant, "$donde (vertical)"))
        if (filas > 1) {
            val tramo = (w - i * (columnas - 1)) / columnas
            piezas.add(Pieza(n, IMPOSTA, tramo.roundToInt(), "90°", (filas - 1) * columnas * cant, "$donde (horizontal)"))
        }
    }

    private fun dividir(w: Float, h: Float, filas: Int, columnas: Int, imposta: Float): List<Pair<Float, Float>> {
        val pw = (w - imposta * (columnas - 1)) / columnas
        val ph = (h - imposta * (filas - 1)) / filas
        return List(filas * columnas) { pw to ph }
    }

    /** Junta piezas iguales (mismo perfil, largo, ángulo y ubicación). */
    private fun agrupar(lista: List<Pieza>): List<Pieza> =
        lista.groupBy { listOf(it.perfil, it.largo, it.angulo, it.ubicacion) }
            .map { (_, g) -> g.first().copy(cantidad = g.sumOf { it.cantidad }) }
            .sortedWith(compareBy({ ordenPerfil(it.perfil) }, { -it.largo }))

    private fun agruparVidrios(lista: List<Vidrio>): List<Vidrio> =
        lista.groupBy { listOf(it.ancho, it.alto, it.panel, it.ubicacion) }
            .map { (_, g) -> g.first().copy(cantidad = g.sumOf { it.cantidad }) }

    fun ordenPerfil(perfil: String) = listOf(MARCO, IMPOSTA, HOJA, HOJA_PUERTA, JUNQUILLO, BARROTILLO, MOSQUITERO)
        .indexOf(perfil).let { if (it < 0) 99 else it }

    // ===================== OPTIMIZACIÓN DE BARRAS =====================

    /**
     * Acomoda todas las piezas de cada perfil en barras (primero las más largas, cada una en la
     * primera barra donde entre). Así se usan menos barras y se ve el sobrante de cada una.
     */
    fun optimizar(ventanas: List<Ventana>, p: Parametros = Parametros()): List<Optimizacion> {
        val util = p.largoBarra - 2 * p.despunte
        return ventanas.flatMap { it.piezas }
            .groupBy { it.perfil }
            .toSortedMap(compareBy { ordenPerfil(it) })
            .map { (perfil, piezas) ->
                val largos = piezas.flatMap { pz -> List(pz.cantidad) { pz.largo } }.sortedDescending()
                val barras = mutableListOf<MutableList<Int>>()
                val libre = mutableListOf<Int>()
                largos.forEach { l ->
                    val consumo = l + p.anchoSierra
                    val i = libre.indexOfFirst { it >= consumo }
                    if (i >= 0) {
                        barras[i].add(l); libre[i] -= consumo
                    } else {
                        barras.add(mutableListOf(l)); libre.add(util - consumo)
                    }
                }
                Optimizacion(
                    perfil = perfil,
                    barras = barras.mapIndexed { i, cortes -> Barra(cortes, libre[i].coerceAtLeast(0)) },
                    totalPiezas = largos.size,
                    metrosUsados = largos.sum() / 1000.0,
                )
            }
    }
}
