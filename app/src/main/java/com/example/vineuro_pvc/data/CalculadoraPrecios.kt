package com.example.vineuro_pvc.data

import kotlin.math.max

/**
 * Motor de cálculo de la cotización.
 *
 * Toma una ventana (OrderItem + sus OpcionesVentana del compás) y la lista de Precios,
 * y devuelve cantidades de material, costo y precio de venta.
 *
 * Columnas del apartado Precios que se usan (índice 0 = nombre de la fila):
 *  - moneda:      [1] tarifa (tipo de cambio), [2] símbolo del resultado
 *  - impuesto:    [1] impuesto %
 *  - planes:      [1] descuento %, [2] margen construcción y materiales %, [3] margen trabajos %
 *  - ventanas:    [1] marco/m, [2] imposta/m, [3] hoja/m, [4] hoja puerta/m, [5] panel sándwich/m2,
 *                 [6] glazing beads/m, [7] caucho/m, [8] glazing bars/m, [9] conector glazing bars/pc
 *  - cristal:     [1..3] vidrio 1..3 /m2, [4] película/m2, [5] spacer/m, [6] secante/m,
 *                 [7] butilo/m, [8] sellado/m, [9] gas/m2
 *  - umbral ext/int: [1] cuerpo /m, [2] tapón /pc
 *  - muebles:     [1] manija regular /pc, [2] manija con cerradura /pc, [3] manija balcón /pc
 *  - mosquitera:  [1] red/m2, [2] perfil/m, [3] sellador/m, [4] esquina/pc, [5] soporte/pc, [6] tirador/pc
 *  - laminación:  [1] /m2
 *  - extras / trabajos: precio y unidad (0 = pieza, 1 = m2, 2..5 = metro lineal)
 *
 * NOTA: la geometría de cada paño se aproxima dividiendo la ventana en filas x columnas iguales.
 */
object CalculadoraPrecios {

    /** Mecanismo "5" = paño fijo (no lleva hoja ni manija). */
    private const val MECANISMO_FIJO = "5"

    data class Cantidades(
        val areaM2: Double,
        val marcoM: Double,
        val impostaM: Double,
        val glazingM: Double,
        val cauchoM: Double,
        val hojaM: Double,
        val hojaPuertaM: Double,
        val vidrioM2: Double,
        val sandwichM2: Double,
        val hojas: Int,
        val hojasPuerta: Int,
        val panosMosquitero: Int,
        val areaPanoM2: Double,
        val perimetroPanoM: Double,
    )

    /** Costos (sin margen) por concepto, en la moneda base de la lista de precios. */
    data class Costos(
        val zona: Double,       // vidrio + panel sándwich + laminación
        val marco: Double,
        val imposta: Double,
        val glazing: Double,
        val caucho: Double,
        val hoja: Double,
        val hojaPuerta: Double,
        val accesorios: Double, // umbrales, manijas, mosquitero, extras
        val trabajos: Double,   // mano de obra / servicios
    ) {
        val materiales: Double get() = zona + marco + imposta + glazing + caucho + hoja + hojaPuerta + accesorios
        val total: Double get() = materiales + trabajos

        operator fun times(f: Double) = Costos(
            zona * f, marco * f, imposta * f, glazing * f, caucho * f, hoja * f, hojaPuerta * f, accesorios * f, trabajos * f,
        )

        operator fun plus(o: Costos) = Costos(
            zona + o.zona, marco + o.marco, imposta + o.imposta, glazing + o.glazing, caucho + o.caucho,
            hoja + o.hoja, hojaPuerta + o.hojaPuerta, accesorios + o.accesorios, trabajos + o.trabajos,
        )

        companion object {
            val CERO = Costos(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        }
    }

    data class Resultado(
        val cantidades: Cantidades,
        val cantidad: Int,            // nº de ventanas iguales (opción "cantidad" del compás)
        val costoUnitario: Costos,    // costo de UNA ventana, sin margen ni impuesto
        val precioUnitario: Costos,   // venta de UNA ventana por concepto (con margen, descuento e impuesto)
        val descuentoPct: Double,
        val margenPct: Double,        // margen promedio efectivo sobre el costo
        val impuestoPct: Double,
        val totalVenta: Double,       // precio final x cantidad (convertido a la moneda de resultado)
        val totalCosto: Double,       // costo x cantidad (convertido a la moneda de resultado)
        val precioPropioAplicado: Boolean,
        val simbolo: String,
    )

    // ===================================================================================

    fun calcular(item: OrderItem, precios: Precios): Resultado {
        val op = item.opciones
        val q = cantidades(item)

        val sistema = filaElegida(precios.ventanas, op.sistemaVentana)
        val cristal = filaElegida(precios.cristal, op.tipoCristal)
        val plan = op.planFinanciacion?.let { fila(precios.planes, it) }
        val impuestoPct = num(precios.impuesto.firstOrNull(), 1)
        val tarifa = num(precios.moneda.firstOrNull(), 1).takeIf { it > 0 } ?: 1.0
        val simbolo = precios.moneda.firstOrNull()?.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "S/"

        // ---- Perfiles (sistema de ventanas) ----
        val marco = q.marcoM * num(sistema, 1)
        val imposta = q.impostaM * num(sistema, 2)
        val hoja = q.hojaM * num(sistema, 3)
        val hojaPuerta = q.hojaPuertaM * num(sistema, 4)
        val glazing = q.glazingM * num(sistema, 6)
        val caucho = q.cauchoM * num(sistema, 7)

        // ---- Zona: vidrio (por m2 y por perímetro) + panel sándwich + laminación ----
        val vidrioPorM2 = (1..4).sumOf { num(cristal, it) } + num(cristal, 9)
        val vidrioPorM = (5..8).sumOf { num(cristal, it) }
        val perimetroVidrio = if (q.areaM2 > 0) q.glazingM * (q.vidrioM2 / q.areaM2) else 0.0
        var zona = q.vidrioM2 * vidrioPorM2 + perimetroVidrio * vidrioPorM + q.sandwichM2 * num(sistema, 5)
        op.laminacionInterna?.let { zona += q.areaM2 * num(fila(precios.laminacion, it), 1) }
        op.laminacionExterna?.let { zona += q.areaM2 * num(fila(precios.laminacion, it), 1) }

        // ---- Accesorios ----
        val anchoM = item.anchoTotalMm / 1000.0
        var accesorios = 0.0
        op.umbralesExternos.forEach { n -> fila(precios.umbralExt, n)?.let { accesorios += anchoM * num(it, 1) + 2 * num(it, 2) } }
        op.umbralesInternos.forEach { n -> fila(precios.umbralInt, n)?.let { accesorios += anchoM * num(it, 1) + 2 * num(it, 2) } }
        op.mueble?.let { n ->
            fila(precios.muebles, n)?.let { accesorios += q.hojas * num(it, 1) + q.hojasPuerta * num(it, 2) }
        }
        if (q.panosMosquitero > 0) {
            val mosq = filaElegida(precios.mosquitera, op.mosquitero)
            accesorios += q.panosMosquitero * (
                q.areaPanoM2 * num(mosq, 1) +
                    q.perimetroPanoM * (num(mosq, 2) + num(mosq, 3)) +
                    4 * num(mosq, 4) + 4 * num(mosq, 5) + 2 * num(mosq, 6)
                )
        }
        op.extras.forEach { n -> precios.extras.find { it.nombre == n }?.let { accesorios += precioSeleccion(it, q) } }

        // ---- Trabajos ----
        var trabajos = 0.0
        op.trabajos.forEach { n -> precios.empleos.find { it.nombre == n }?.let { trabajos += precioSeleccion(it, q) } }

        val costo = Costos(zona, marco, imposta, glazing, caucho, hoja, hojaPuerta, accesorios, trabajos)

        // ---- Margen, descuento, impuesto ----
        val descuentoPct = num(plan, 1)
        val margenMat = num(plan, 2)
        val margenTrab = num(plan, 3)
        val fDesc = 1 - descuentoPct / 100.0
        val fImp = 1 + impuestoPct / 100.0
        val fMat = (1 + margenMat / 100.0) * fDesc * fImp
        val fTrab = (1 + margenTrab / 100.0) * fDesc * fImp

        var precio = Costos(
            zona * fMat, marco * fMat, imposta * fMat, glazing * fMat, caucho * fMat,
            hoja * fMat, hojaPuerta * fMat, accesorios * fMat, trabajos * fTrab,
        )

        // Precio propio (escrito a mano en el compás): reemplaza el total de UNA ventana
        val precioPropio = op.precioPropio.replace(",", ".").toDoubleOrNull()?.takeIf { it > 0 }
        if (precioPropio != null && precio.total > 0) {
            precio = precio * (precioPropio / tarifa / precio.total)
        }

        val cantidad = max(1, op.cantidad)
        val totalVenta = (precioPropio?.let { it / tarifa } ?: precio.total) * cantidad * tarifa
        val totalCosto = costo.total * cantidad * tarifa
        val margenEfectivo = if (costo.total > 0) ((precio.total / fImp) / costo.total - 1) * 100 else 0.0

        return Resultado(
            cantidades = q,
            cantidad = cantidad,
            costoUnitario = costo * tarifa,
            precioUnitario = precio * tarifa,
            descuentoPct = descuentoPct,
            margenPct = margenEfectivo,
            impuestoPct = impuestoPct,
            totalVenta = totalVenta,
            totalCosto = totalCosto,
            precioPropioAplicado = precioPropio != null,
            simbolo = simbolo,
        )
    }

    /** Total de venta de todo el pedido. */
    fun totalPedido(items: List<OrderItem>, precios: Precios): Double = items.sumOf { calcular(it, precios).totalVenta }

    // ===================== GEOMETRÍA =====================

    fun cantidades(item: OrderItem): Cantidades {
        val altoM = item.altoTotalMm / 1000.0
        val anchoM = item.anchoTotalMm / 1000.0
        val filas = max(1, item.sectionHeights.size)
        val cols = max(1, item.sectionWidths.size)

        // Paños: los del catálogo + subdivisiones hechas con "impostas" dentro de un paño
        val panosBase = max(1, max(item.config.hojasCount, filas * cols))
        val subdivisionesExtra = item.paneCols.values.sumOf { max(0, it - 1) }
        val totalPanos = panosBase + subdivisionesExtra

        val panoW = anchoM / cols
        val panoH = altoM / filas
        val areaPano = (anchoM * altoM) / totalPanos
        val perimPano = 2 * (panoW + panoH)

        val area = altoM * anchoM
        val marco = 2 * (altoM + anchoM)
        val imposta = (cols - 1) * altoM + (filas - 1) * anchoM + subdivisionesExtra * panoH

        val panosPuerta = item.panePuerta.count { it.value }
        val panosQueAbren = item.paneMechanisms.count { (idx, mec) -> mec != MECANISMO_FIJO && item.panePuerta[idx] != true }
        val panosSandwich = item.paneSandwich.count { it.value }

        val glazing = totalPanos * perimPano
        val hoja = panosQueAbren * perimPano
        val hojaPuerta = panosPuerta * perimPano
        val sandwich = (panosSandwich * areaPano).coerceAtMost(area)

        return Cantidades(
            areaM2 = area,
            marcoM = marco,
            impostaM = imposta,
            glazingM = glazing,
            cauchoM = glazing + hoja + hojaPuerta, // junta del vidrio + junta de las hojas
            hojaM = hoja,
            hojaPuertaM = hojaPuerta,
            vidrioM2 = (area - sandwich).coerceAtLeast(0.0),
            sandwichM2 = sandwich,
            hojas = panosQueAbren,
            hojasPuerta = panosPuerta,
            panosMosquitero = item.paneMosqNet.count { it.value },
            areaPanoM2 = areaPano,
            perimetroPanoM = perimPano,
        )
    }

    // ===================== AYUDANTES =====================

    private fun precioSeleccion(f: FilaSeleccion, q: Cantidades): Double {
        val p = f.precio.replace(",", ".").toDoubleOrNull() ?: 0.0
        return when (f.unidad) {
            0 -> p                 // por pieza
            1 -> p * q.areaM2      // por m2
            else -> p * q.marcoM   // por metro lineal (perímetro de la ventana)
        }
    }

    private fun fila(tabla: List<List<String>>, nombre: String): List<String>? =
        tabla.find { it.firstOrNull()?.trim() == nombre.trim() }

    /**
     * Fila elegida en el compás; si no se eligió nada, la primera fila que tenga algún precio cargado
     * (así la ventana ya muestra un precio aproximado sin configurar todo).
     */
    private fun filaElegida(tabla: List<List<String>>, nombre: String?): List<String>? =
        nombre?.let { fila(tabla, it) }
            ?: tabla.firstOrNull { f -> f.drop(1).any { (it.replace(",", ".").toDoubleOrNull() ?: 0.0) > 0 } }

    private fun num(fila: List<String>?, i: Int): Double =
        fila?.getOrNull(i)?.replace(",", ".")?.trim()?.toDoubleOrNull() ?: 0.0
}
