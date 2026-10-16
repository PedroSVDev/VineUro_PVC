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
 *  - ventanas:    37 columnas (ver COL_*): perfiles marco/imposta/hoja/hoja puerta, sándwich, shtapik, caucho,
 *                 glazing bars y conector, refuerzos, kits de herraje por tipo de apertura, esquineros,
 *                 conector de imposta, perfiles de asiento/dorsill/jamba, juntas, huecos y extensores 15..100
 *  - cristal:     [1..3] vidrio 1..3 /m2, [4] película/m2, [5] spacer/m, [6] secante/m,
 *                 [7] butilo/m, [8] sellado/m, [9] gas/m2
 *  - umbral ext/int: [1] cuerpo /m, [2] tapón /pc
 *  - muebles:     [1] manija regular /pc, [2] manija con cerradura /pc, [3] manija balcón /pc
 *  - mosquitera:  [1] red/m2, [2] perfil/m, [3] sellador/m, [4] esquina/pc, [5] soporte/pc, [6] tirador/pc
 *  - laminación:  [1] /m2
 *  - extras / trabajos: precio y unidad (0 pieza, 1 área, 2 perímetro, 3 pendientes, 4 ancho, 5 alto)
 *
 * GEOMETRÍA: se calcula desde los paños REALES que dibuja WindowPreviewView (medidas de cada sección,
 * puertas, travesaños, subdivisiones), no desde conteos del catálogo. Se usan las medidas de perfil de la
 * original: marco 48 mm, imposta 52 mm, hoja 62 mm, hoja de puerta 94 mm, solape de hoja 13 mm.
 *
 * PRECIO (misma secuencia que la app original):
 *   concepto = factor x cantidad x precio, con factor materiales = (1 + margen mat/100) x tarifa
 *   y factor trabajos = tarifa x (1 + margen trab/100).
 *   precio de ventana = suma de conceptos, o el PRECIO PROPIO (que reemplaza esa suma);
 *   luego descuento (x (1 - desc/100)) y finalmente el impuesto sobre el total del pedido.
 */
object CalculadoraPrecios {

    /** Mecanismo "5" = paño fijo (no lleva hoja ni manija). */
    private const val MECANISMO_FIJO = "5"
    /** Hoja fija ("OPEN_FIXED" de la original): lleva perfil de hoja pero no kit de herraje ni manija. */
    private const val HOJA_FIJA = "10"

    // Medidas del sistema verificadas en la app original (en metros)
    private const val ANCHO_MARCO_M = 0.048     // perfil de marco visible
    private const val ANCHO_IMPOSTA_M = 0.052   // perfil de imposta (las secciones se miden a ejes)
    private const val PERFIL_HOJA_M = 0.062     // perfil de hoja
    private const val PERFIL_PUERTA_M = 0.094   // perfil de hoja de puerta
    private const val SOLAPE_HOJA_M = 0.013     // la hoja (y la red) montan 13 mm sobre el marco por lado

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
        val anchoM: Double,
        val altoM: Double,
        val vidrioPerimM: Double,     // perímetro de vidrio (paños que no son sándwich)
        val mosqAreaM2: Double,       // área de red de los paños con mosquitero
        val mosqPerimM: Double,       // perímetro de los paños con mosquitero
        val impostaN: Int,            // nº de piezas de imposta (cada una lleva 2 conectores)
        val barrasM: Double = 0.0,    // metros de barras de vidrio (glazing bars)
        val barrasCruces: Int = 0,    // cruces de barras (llevan conector)
        val hojasDetalle: List<HojaCalc>,
        val lamInternaM2: Double,     // superficie de perfil a laminar, cara interior
        val lamExternaM2: Double,     // superficie de perfil a laminar, cara exterior
        /** Bases (marcos rectangulares): una por bloque en las ventanas L / T, una en las demás. */
        val bases: List<ReglasBases.BaseDim> = emptyList(),
        /** Metros de perfiles adicionales y anchos de umbral, ya con las reglas por base de la original. */
        val adic: ReglasBases.Metros? = null,
    ) {
        /** Esquineros de refuerzo del marco: 4 por base. */
        val esquinerosMarco: Int get() = 4 * bases.size.coerceAtLeast(1)
    }

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

    /**
     * Una columna de la "tabla en vivo" de la original (A..X): cantidad física del concepto en UNA ventana y su costo.
     * [unidad]: 0 = metros, 1 = m², 2 = piezas. [costo] y [precio] son de UNA ventana, en la moneda de resultado
     * (costo sin margen ni impuesto; precio con margen, descuento e impuesto).
     */
    data class Linea(val codigo: String, val nombre: String, val cantidad: Double, val unidad: Int, val costo: Double, val precio: Double)

    data class Resultado(
        val cantidades: Cantidades,
        val desglose: List<Linea> = emptyList(),
        val cantidad: Int,            // nº de ventanas iguales (opción "cantidad" del compás)
        val costoUnitario: Costos,    // costo de UNA ventana, sin margen ni impuesto
        val precioUnitario: Costos,   // venta de UNA ventana por concepto (con margen, descuento e impuesto)
        val precioUnitarioTotal: Double, // total de venta de UNA ventana (incluye el caso de precio propio)
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

        // Sin fila elegida => esa partida cuesta 0 (la original tampoco inventa un sistema/vidrio).
        val sistema = filaElegida(precios.ventanas, op.sistemaVentana)
        val cristal = filaElegida(precios.cristal, op.tipoCristal)
        val plan = op.planFinanciacion?.let { fila(precios.planes, it) }
        val impuestoPct = num(precios.impuesto.firstOrNull(), 1)
        val tarifa = num(precios.moneda.firstOrNull(), 1).takeIf { it > 0 } ?: 1.0
        val simbolo = precios.moneda.firstOrNull()?.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "S/"

        // ---- Sistema de ventanas (misma estructura que la pestaña "sistema" de la original) ----
        // Columnas de precios.ventanas: ver COL_* al final del objeto.
        fun s(c: Int) = num(sistema, c)

        // Marco: perímetro x (perfil + refuerzo) + 4 esquineros de refuerzo
        val marco = q.marcoM * (s(COL_MARCO) + s(COL_REFUERZO_MARCO)) + q.esquinerosMarco * s(COL_ESQUINERO_MARCO)

        // Imposta: longitud x (perfil + refuerzo) + 2 conectores por pieza
        val imposta = q.impostaM * (s(COL_IMPOSTA) + s(COL_REFUERZO_IMPOSTA)) + q.impostaN * s(COL_CONECTOR_IMPOSTA) * 2

        // Hojas: perímetro x (perfil + refuerzo) + 4 esquineros + herraje según el tipo de apertura
        fun herraje(mecanismo: String): Double = when (mecanismo) {
            "1", "3" -> s(COL_KIT_CORREDERA) + s(COL_GUIAS_CORREDERA)      // corrediza
            "2", "9" -> s(COL_KIT_HORIZONTAL)                               // proyectante (abatible superior/inferior)
            "4", "6" -> s(COL_KIT_VERTICAL)                                 // practicable (giro lateral)
            "7", "8" -> s(COL_KIT_ROTACION)                                 // oscilobatiente
            else -> when (AperturasMulti.grupo(mecanismo)) {                 // aperturas de varias hojas (original)
                AperturasMulti.Grupo.CORREDIZA -> s(COL_KIT_CORREDERA)
                AperturasMulti.Grupo.CORREDIZA_VERT -> s(COL_KIT_CORREDERA_VERT)
                AperturasMulti.Grupo.FRANCESA -> s(COL_KIT_FRANCESA)
                null -> 0.0                                                 // fijo
            }
        }
        var hoja = 0.0
        var hojaPuerta = 0.0
        q.hojasDetalle.forEach { h ->
            val parcial = if (h.esPuerta) {
                h.perimetroM * (s(COL_HOJA_PUERTA) + s(COL_REFUERZO_PUERTA))
            } else {
                h.perimetroM * (s(COL_HOJA) + s(COL_REFUERZO_HOJA))
            } + 4 * s(COL_ESQUINERO_HOJA) + herraje(h.mecanismo) * h.kits + h.impostaFrancesaM * s(COL_IMPOSTA_FRANCESA)
            if (h.esPuerta) hojaPuerta += parcial else hoja += parcial
        }

        // Barras de vidrio: largo x precio/m + cruces x conector (como pk_s3.d.D de la original); se suman a la partida de hojas
        hoja += q.barrasM * s(COL_BARRAS) + q.barrasCruces * s(COL_CONECTOR_BARRAS)

        val glazing = q.glazingM * s(COL_SHTAPIK)
        val caucho = q.cauchoM * s(COL_CAUCHO)

        // ---- Zona: vidrio + panel sándwich + laminación ----
        // Vidrio (regla de la original): por m2 = vidrio 1+2+3 + película + gas;
        // por metro = secante + sellador + mantequilla + espaciador, donde la mantequilla va x2
        // (x4 si hay 3 vidrios con precio) y el espaciador x2 solo con 3 vidrios.
        val v1 = num(cristal, 1); val v2 = num(cristal, 2); val v3 = num(cristal, 3)
        val triple = v1 != 0.0 && v2 != 0.0 && v3 != 0.0
        val vidrioPorM2 = v1 + v2 + v3 + num(cristal, 4) + num(cristal, 9)
        val butilo = num(cristal, 7) * (if (triple) 4 else 2)
        val espaciador = num(cristal, 5) * (if (triple) 2 else 1)
        val vidrioPorM = espaciador + butilo + num(cristal, 8) + num(cristal, 6)
        val cVidrio = q.vidrioM2 * vidrioPorM2 + q.vidrioPerimM * vidrioPorM
        val cSandwich = q.sandwichM2 * s(COL_SANDWICH)
        var zona = cVidrio + cSandwich
        // La laminación es película sobre los PERFILES (no sobre el vidrio): se cobra la superficie de perfil.
        var cLamInt = 0.0
        var cLamExt = 0.0
        op.laminacionInterna?.let { cLamInt = q.lamInternaM2 * num(fila(precios.laminacion, it), 1) }
        op.laminacionExterna?.let { cLamExt = q.lamExternaM2 * num(fila(precios.laminacion, it), 1) }
        zona += cLamInt + cLamExt

        // ---- Accesorios ----
        val anchoM = item.anchoTotalMm / 1000.0
        var accesorios = 0.0
        var cUmbExt = 0.0; var qUmbExt = 0.0
        var cUmbInt = 0.0; var qUmbInt = 0.0
        val anchosUmbral = q.adic?.anchosUmbral?.takeIf { it.isNotEmpty() } ?: listOf(anchoM)
        op.umbralesExternos.forEach { n -> fila(precios.umbralExt, n)?.let {
            val c = precioUmbral(it, anchosUmbral); cUmbExt += c; accesorios += c
            qUmbExt += piezasUmbral(it, anchosUmbral).sumOf { p -> p.first * p.second } } }
        op.umbralesInternos.forEach { n -> fila(precios.umbralInt, n)?.let {
            val c = precioUmbral(it, anchosUmbral); cUmbInt += c; accesorios += c
            qUmbInt += piezasUmbral(it, anchosUmbral).sumOf { p -> p.first * p.second } } }
        var cMueble = 0.0
        op.mueble?.let { n ->
            // La original (pk_e3.b) cobra UNA manija "regular" (columna 1) por cada hoja que no sea fija, puertas incluidas;
            // las columnas "con cerradura" y "balcón" existen en la tabla pero su cálculo no las usa.
            fila(precios.muebles, n)?.let { cMueble = q.hojasDetalle.sumOf { h -> if (h.mecanismo != MECANISMO_FIJO && h.mecanismo != HOJA_FIJA) h.manijas else 0 } * num(it, 1) }
        }
        accesorios += cMueble
        var cMosq = 0.0
        if (q.panosMosquitero > 0) {
            val mosq = filaElegida(precios.mosquitera, op.mosquitero)
            cMosq = q.mosqAreaM2 * num(mosq, 1) +
                q.mosqPerimM * (num(mosq, 2) + num(mosq, 3)) +
                q.panosMosquitero * (4 * num(mosq, 4) + 4 * num(mosq, 5) + 2 * num(mosq, 6))
        }
        accesorios += cMosq
        // Perfiles adicionales (selección del panel de la ventana)
        var cAsiento = 0.0; var cDorsill = 0.0; var cJamba = 0.0
        val am = q.adic ?: ReglasBases.metros(listOf(ReglasBases.BaseDim(q.anchoM, q.altoM)), op)
        cAsiento = am.asiento * s(COL_PERFIL_ASIENTO)
        cDorsill = am.dorsill * s(COL_DORSILL)
        cJamba = am.jamba * s(COL_JAMBA_ESQUINA)
        accesorios += cAsiento + cDorsill + cJamba
        // Costo y metros por tipo de perfil de lado (juntas, 90°, 135°, huecos y extensores 15..100)
        val cPerfil = mutableMapOf<String, Double>()
        val qPerfil = mutableMapOf<String, Double>()
        // Metros por tipo según las reglas por base (ver ReglasBases): juntas, 90°, 135°, huecos y extensores 15..100
        val colTipo = mapOf(
            "juntas" to COL_JUNTA, "juntas90" to COL_JUNTA_90, "juntas135" to COL_JUNTA_135, "huecos" to COL_HUECO,
            "ext15" to COL_EXT_15, "ext20" to COL_EXT_20, "ext40" to COL_EXT_40, "ext60" to COL_EXT_60,
            "ext80" to COL_EXT_80, "ext100" to COL_EXT_100,
        )
        for ((tipo, col) in colTipo) {
            val m = am.porTipo[tipo] ?: 0.0
            if (m == 0.0) continue
            accesorios += m * s(col)
            cPerfil[tipo] = m * s(col)
            qPerfil[tipo] = m
        }
        var cExtras = 0.0
        op.extras.forEach { n -> precios.extras.find { it.nombre == n }?.let { cExtras += precioSeleccion(it, q) } }
        accesorios += cExtras

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

        // Precio propio (en la moneda de resultado): reemplaza la SUMA de conceptos de UNA ventana y
        // después recibe el descuento y el impuesto, igual que en la original.
        val precioPropio = op.precioPropio.replace(",", ".").toDoubleOrNull()?.takeIf { it > 0 }
        val unitarioBase = if (precioPropio != null) precioPropio / tarifa * fDesc * fImp else precio.total
        if (precioPropio != null && precio.total > 0) {
            precio = precio * (unitarioBase / precio.total)
        }

        val cantidad = max(1, op.cantidad)
        val totalVenta = unitarioBase * cantidad * tarifa
        val totalCosto = costo.total * cantidad * tarifa
        val margenEfectivo = if (costo.total > 0) ((unitarioBase / fImp / fDesc) / costo.total - 1) * 100 else 0.0

        // Desglose por concepto (columnas A..X de la "tabla en vivo" de la original), de UNA ventana.
        val cBarras = q.barrasM * s(COL_BARRAS) + q.barrasCruces * s(COL_CONECTOR_BARRAS)
        fun lin(cod: String, nom: String, cant: Double, un: Int, c: Double, trabajo: Boolean = false): Linea {
            val f = if (trabajo) fTrab else fMat
            return Linea(cod, nom, cant, un, c * tarifa, c * f * tarifa)
        }
        val desgloseBruto = listOf(
            lin("A", "marcos", q.marcoM, 0, marco),
            lin("B", "impostas", q.impostaM, 0, imposta),
            lin("C", "junquillos", q.glazingM, 0, glazing),
            lin("D", "caucho", q.cauchoM, 0, caucho),
            lin("E", "hojas", q.hojaM, 0, hoja - cBarras),
            lin("E1", "hojas puerta", q.hojaPuertaM, 0, hojaPuerta),
            lin("F", "vidrio", q.vidrioM2, 1, cVidrio),
            lin("F1", "sándwich", q.sandwichM2, 1, cSandwich),
            lin("F1a", "barras vidrio", q.barrasM, 0, cBarras),
            lin("F2", "lamin. int.", q.lamInternaM2, 1, cLamInt),
            lin("F3", "lamin. ext.", q.lamExternaM2, 1, cLamExt),
            lin("G", "trabajos", 0.0, 2, trabajos, true),
            lin("H", "umbral int.", qUmbInt, 0, cUmbInt),
            lin("I", "umbral ext.", qUmbExt, 0, cUmbExt),
            lin("K", "muebles", 0.0, 2, cMueble),
            lin("L", "mosquitero", q.panosMosquitero.toDouble(), 2, cMosq),
            lin("M", "extras", 0.0, 2, cExtras),
            lin("N", "perfil asiento", am.asiento, 0, cAsiento),
            lin("O", "dorsill", am.dorsill, 0, cDorsill),
            lin("P", "jamba esquina", am.jamba, 0, cJamba),
            lin("Q", "perf. juntas", qPerfil["juntas"] ?: 0.0, 0, cPerfil["juntas"] ?: 0.0),
            lin("Q1", "juntas 90°", qPerfil["juntas90"] ?: 0.0, 0, cPerfil["juntas90"] ?: 0.0),
            lin("Q2", "juntas 135°", qPerfil["juntas135"] ?: 0.0, 0, cPerfil["juntas135"] ?: 0.0),
            lin("R", "perf. huecos", qPerfil["huecos"] ?: 0.0, 0, cPerfil["huecos"] ?: 0.0),
        ) + listOf("15" to "S", "20" to "S1", "40" to "T", "60" to "U", "80" to "V", "100" to "X").map { (med, cod) ->
            lin(cod, "extensor $med", qPerfil["ext$med"] ?: 0.0, 0, cPerfil["ext$med"] ?: 0.0)
        }
        // Con precio propio los conceptos se reescalan para que sumen el precio final de la ventana.
        val sumaPrecios = desgloseBruto.sumOf { it.precio }
        val escala = if (precioPropio != null && sumaPrecios > 0) unitarioBase * tarifa / sumaPrecios else 1.0
        val desglose = if (escala == 1.0) desgloseBruto else desgloseBruto.map { it.copy(precio = it.precio * escala) }

        return Resultado(
            cantidades = q,
            desglose = desglose,
            cantidad = cantidad,
            costoUnitario = costo * tarifa,
            precioUnitario = precio * tarifa,
            precioUnitarioTotal = unitarioBase * tarifa,
            descuentoPct = descuentoPct,
            margenPct = margenEfectivo,
            impuestoPct = impuestoPct,
            totalVenta = totalVenta,
            totalCosto = totalCosto,
            precioPropioAplicado = precioPropio != null,
            simbolo = simbolo,
        )
    }

    /** Total de venta (con impuesto) de todo el pedido. */
    fun totalPedido(items: List<OrderItem>, precios: Precios): Double = items.sumOf { calcular(it, precios).totalVenta }

    /**
     * Desglose del pedido como en la original: el descuento ya está aplicado a cada ventana y el
     * impuesto se aplica UNA vez sobre el total descontado del pedido.
     */
    data class ResumenPedido(val neto: Double, val impuesto: Double, val total: Double, val impuestoPct: Double)

    fun resumenPedido(resultados: List<Resultado>): ResumenPedido {
        val pct = resultados.firstOrNull()?.impuestoPct ?: 0.0
        val total = resultados.sumOf { it.totalVenta }
        val neto = total / (1 + pct / 100.0)
        return ResumenPedido(neto = neto, impuesto = total - neto, total = total, impuestoPct = pct)
    }

    /**
     * Piezas de umbral a comprar según la fila de Precios (columna 3, "1500x2 800x1" = mm x cantidad), como el
     * "override" de la original (pk_j5.c). Vacío o "-" = una sola pieza a la medida del ancho de la ventana.
     */
    fun piezasUmbral(fila: List<String>?, anchoM: Double): List<Pair<Double, Int>> = piezasUmbral(fila, listOf(anchoM))

    /**
     * Con medidas fijas en la fila, esas son las piezas (no dependen de la ventana). Si no, una pieza por cada ancho de
     * [anchos] (una por base con umbral en las ventanas L / T).
     */
    fun piezasUmbral(fila: List<String>?, anchos: List<Double>): List<Pair<Double, Int>> {
        val texto = fila?.getOrNull(3)?.trim().orEmpty()
        val piezas = texto.split(" ", ";", ",").mapNotNull { t ->
            val p = t.trim().lowercase().split("x")
            val mm = p.getOrNull(0)?.replace(",", ".")?.toDoubleOrNull() ?: return@mapNotNull null
            val n = if (p.size > 1) p[1].toIntOrNull() ?: return@mapNotNull null else 1
            if (mm > 0 && n > 0) mm / 1000.0 to n else null
        }
        return piezas.ifEmpty { anchos.map { it to 1 } }
    }

    /** Precio de un umbral: largo total x precio por metro + 2 tapones por cada pieza. */
    private fun precioUmbral(fila: List<String>, anchos: List<Double>): Double {
        val piezas = piezasUmbral(fila, anchos)
        return piezas.sumOf { it.first * it.second } * num(fila, 1) + piezas.sumOf { it.second } * 2 * num(fila, 2)
    }

    // ===================== GEOMETRÍA =====================

    /**
     * Paño real (ya subdividido). [clave] es la que usan los mapas del OrderItem; [padre] el paño sin subdividir.
     * Las medidas son las del paño NOMINAL; el hueco real (dentro de los perfiles) y el vidrio se calculan aparte.
     */
    data class Pano(
        val clave: Int, val padre: Int, val anchoM: Double, val altoM: Double,
        val izq: Boolean, val arr: Boolean, val der: Boolean, val aba: Boolean, // true = lado apoyado en el marco
    ) {
        val areaM2: Double get() = anchoM * altoM
        val perimetroM: Double get() = 2 * (anchoM + altoM)
        private fun inset(marco: Boolean) = if (marco) ANCHO_MARCO_M else ANCHO_IMPOSTA_M / 2
        /** Hueco libre entre perfiles. */
        val huecoAncho: Double get() = (anchoM - inset(izq) - inset(der)).coerceAtLeast(0.0)
        val huecoAlto: Double get() = (altoM - inset(arr) - inset(aba)).coerceAtLeast(0.0)
    }

    /** [impostaCortes]: longitud de CORTE (m) de cada pieza de imposta (entre caras de marco/imposta), para la guía de corte. */
    data class Geometria(
        val panos: List<Pano>, val impostaM: Double, val impostaN: Int, val impostaCortes: List<Double> = emptyList(),
        /** Marcos (bases) de la ventana; vacío = uno solo del tamaño total. */
        val bases: List<ReglasBases.BaseDim> = emptyList(),
    )

    /** Hoja calculada: perímetro EXTERIOR de la hoja (hueco + 13 mm por lado) y su tipo. */
    data class HojaCalc(
        val perimetroM: Double, val altoM: Double, val esPuerta: Boolean, val mecanismo: String,
        /** Kits de herraje (hojas móviles; en la puerta francesa es 1 por ventana). */
        val kits: Int = 1,
        /** Manijas que se cobran (hojas con manija). */
        val manijas: Int = 1,
        /** Metros de imposta francesa (perfil central) que se cobran con la columna COL_IMPOSTA_FRANCESA. */
        val impostaFrancesaM: Double = 0.0,
        /** Hojas móviles para el conteo de "hojas" (la francesa cuenta sus 2). */
        val hojasMoviles: Int = 1,
    )

    /** Reparte [totalMm] según las proporciones de [valores] (igual que getScaledActive* del dibujo). */
    private fun escalar(valores: FloatArray, n: Int, totalMm: Int): DoubleArray {
        val suma = valores.sumOf { it.toDouble() }
        return if (valores.size == n && suma > 0.0) DoubleArray(n) { valores[it].toDouble() / suma * totalMm / 1000.0 }
        else DoubleArray(n) { totalMm / 1000.0 / n }
    }

    private fun filasActivas(c: WindowConfig): Int = c.filasActivas()

    private fun colsActivas(c: WindowConfig): Int = c.colsActivas()

    private class Base(val idx: Int, val w: Double, val h: Double, val l: Boolean, val t: Boolean, val r: Boolean, val b: Boolean)

    /**
     * Paños e impostas tal como los dibuja WindowPreviewView. Los índices de paño coinciden con los
     * del dibujo (los mismos con los que se guardan mecanismo, puerta, sándwich, mosquitero y subdivisión).
     */
    fun geometria(item: OrderItem): Geometria {
        val cfg = item.config
        val W = item.anchoTotalMm / 1000.0
        val H = item.altoTotalMm / 1000.0
        val base = mutableListOf<Base>()
        var imposta = 0.0
        var piezas = 0
        val cortes = mutableListOf<Double>()
        val basesVentana = mutableListOf<ReglasBases.BaseDim>()
        /** Largo libre de una pieza de imposta: total - 48 mm si apoya en marco, - 26 mm (media imposta) si apoya en otra imposta. */
        fun hueco(total: Double, iniMarco: Boolean, finMarco: Boolean) =
            (total - (if (iniMarco) ANCHO_MARCO_M else ANCHO_IMPOSTA_M / 2) - (if (finMarco) ANCHO_MARCO_M else ANCHO_IMPOSTA_M / 2)).coerceAtLeast(0.0)

        when (cfg.layoutType) {
            WindowConfig.LayoutType.GRID, WindowConfig.LayoutType.ASYMMETRIC_SPLIT -> {
                val rows = filasActivas(cfg); val cols = colsActivas(cfg)
                val hs = escalar(item.sectionHeights, rows, item.altoTotalMm)
                val ws = escalar(item.sectionWidths, cols, item.anchoTotalMm)
                for (r in 0 until rows) for (c in 0 until cols)
                    base += Base(r * cols + c, ws[c], hs[r], c == 0, r == 0, c == cols - 1, r == rows - 1)
                imposta = (cols - 1) * H + (rows - 1) * W
                piezas = (cols - 1) + (rows - 1)
                // verticales continuas; horizontales cortadas por columna
                repeat(cols - 1) { cortes += hueco(H, true, true) }
                repeat(rows - 1) { for (c in 0 until cols) cortes += hueco(ws[c], c == 0, c == cols - 1) }
            }
            WindowConfig.LayoutType.TOP_TRANSOM -> {
                val cols = colsActivas(cfg); val hs = escalar(item.sectionHeights, 2, item.altoTotalMm)
                base += Base(0, W, hs[0], true, true, true, false)
                for (c in 0 until cols) base += Base(c + 1, W / cols, hs[1], c == 0, false, c == cols - 1, true)
                imposta = W + (cols - 1) * hs[1]; piezas = 1 + (cols - 1)
                cortes += hueco(W, true, true); repeat(cols - 1) { cortes += hueco(hs[1], false, true) }
            }
            WindowConfig.LayoutType.BOTTOM_TRANSOM -> {
                val cols = colsActivas(cfg); val hs = escalar(item.sectionHeights, 2, item.altoTotalMm)
                for (c in 0 until cols) base += Base(c, W / cols, hs[0], c == 0, true, c == cols - 1, false)
                base += Base(cols, W, hs[1], true, false, true, true)
                imposta = W + (cols - 1) * hs[0]; piezas = 1 + (cols - 1)
                cortes += hueco(W, true, true); repeat(cols - 1) { cortes += hueco(hs[0], true, false) }
            }
            WindowConfig.LayoutType.TOP_BOTTOM_TRANSOM -> {
                val cols = colsActivas(cfg); val hs = escalar(item.sectionHeights, 3, item.altoTotalMm)
                base += Base(0, W, hs[0], true, true, true, false)
                for (c in 0 until cols) base += Base(c + 1, W / cols, hs[1], c == 0, false, c == cols - 1, false)
                base += Base(cols + 1, W, hs[2], true, false, true, true)
                imposta = 2 * W + (cols - 1) * hs[1]; piezas = 2 + (cols - 1)
                repeat(2) { cortes += hueco(W, true, true) }; repeat(cols - 1) { cortes += hueco(hs[1], false, false) }
            }
            WindowConfig.LayoutType.DOOR_L_LEFT, WindowConfig.LayoutType.DOOR_L_RIGHT,
            WindowConfig.LayoutType.DOOR_L_LEFT_TRANSOM, WindowConfig.LayoutType.DOOR_L_RIGHT_TRANSOM -> {
                val izquierda = cfg.layoutType == WindowConfig.LayoutType.DOOR_L_LEFT ||
                    cfg.layoutType == WindowConfig.LayoutType.DOOR_L_LEFT_TRANSOM
                val conTravesano = cfg.layoutType == WindowConfig.LayoutType.DOOR_L_LEFT_TRANSOM ||
                    cfg.layoutType == WindowConfig.LayoutType.DOOR_L_RIGHT_TRANSOM
                val altoVentana = escalar(item.sectionHeights, 2, item.altoTotalMm)[0] // la parte baja de un lado queda libre
                val anchoPuerta = 0.45 * W
                val anchoVent = 0.55 * W
                val izq = if (izquierda) anchoPuerta to H else anchoVent to altoVentana
                val der = if (izquierda) anchoVent to altoVentana else anchoPuerta to H
                if (conTravesano) {
                    val t = 0.22 * H
                    base += Base(0, izq.first, t, true, true, false, false)
                    base += Base(1, izq.first, izq.second - t, true, false, false, true)
                    base += Base(2, der.first, t, false, true, true, false)
                    base += Base(3, der.first, der.second - t, false, false, true, true)
                    imposta = W + altoVentana; piezas = 3
                    cortes += hueco(W, true, true); cortes += hueco(t, true, false); cortes += hueco(altoVentana - t, false, true)
                } else {
                    base += Base(0, izq.first, izq.second, true, true, false, true)
                    base += Base(1, der.first, der.second, false, true, true, true)
                    imposta = altoVentana; piezas = 1
                    cortes += hueco(altoVentana, true, true)
                }
            }
            WindowConfig.LayoutType.DOOR_T_CENTER -> {
                val altoVentana = escalar(item.sectionHeights, 2, item.altoTotalMm)[0]
                base += Base(0, 0.33 * W, altoVentana, true, true, false, true)
                base += Base(1, 0.34 * W, H, false, true, false, true)
                base += Base(2, 0.33 * W, altoVentana, false, true, true, true)
                imposta = 2 * altoVentana; piezas = 2
                repeat(2) { cortes += hueco(altoVentana, true, true) }
            }
            WindowConfig.LayoutType.ARBOL -> {
                // Modelos de la original: paños, impostas y apoyos en el marco salen de la estructura decodificada
                val mo = cfg.modeloOriginal
                if (mo != null) {
                    val pl = ModelosOriginales.plano(mo, item.anchoTotalMm.toDouble(), item.altoTotalMm.toDouble(), item.sectionWidths, item.sectionHeights)
                    if (pl.bases.size > 1) pl.bases.forEach { basesVentana += ReglasBases.BaseDim(it.ancho / 1000.0, it.alto / 1000.0) }
                    pl.celdas.forEachIndexed { i, c -> base += Base(i, c.ancho / 1000.0, c.alto / 1000.0, c.izq, c.arr, c.der, c.aba) }
                    for (d in pl.divisiones) {
                        imposta += d.largo / 1000.0; piezas++
                        cortes += hueco(d.largo / 1000.0, d.iniMarco, d.finMarco)
                    }
                }
            }
            WindowConfig.LayoutType.DOOR_T_CENTER_TRANSOM -> {
                val altoVentana = escalar(item.sectionHeights, 2, item.altoTotalMm)[0]
                base += Base(0, W, altoVentana, true, true, true, false)
                base += Base(1, 0.33 * W, H - altoVentana, true, false, false, true)
                base += Base(2, 0.34 * W, H - altoVentana, false, false, false, true)
                base += Base(3, 0.33 * W, H - altoVentana, false, false, true, true)
                imposta = W + 2 * (H - altoVentana); piezas = 3
                cortes += hueco(W, true, true); repeat(2) { cortes += hueco(H - altoVentana, false, true) }
            }
        }

        // Subdivisión de un paño en n columnas iguales (travesaños verticales dentro del paño)
        val panos = mutableListOf<Pano>()
        for (b in base) {
            val n = (item.paneCols[b.idx] ?: 1).let { if (it in 2..4) it else 1 }
            if (n > 1) {
                for (sc in 0 until n) {
                    panos += Pano(b.idx * 10 + sc, b.idx, b.w / n, b.h, b.l && sc == 0, b.t, b.r && sc == n - 1, b.b)
                }
                imposta += (n - 1) * b.h
                piezas += n - 1
                repeat(n - 1) { cortes += hueco(b.h, b.t, b.b) }
            } else {
                panos += Pano(b.idx, b.idx, b.w, b.h, b.l, b.t, b.r, b.b)
            }
        }
        return Geometria(panos, imposta, piezas, cortes, basesVentana)
    }

    /** Vidrio (o panel sándwich) individual dentro de una hoja o paño. */
    private class Vid(val ancho: Double, val alto: Double, val sandwich: Boolean, val barH: Int = 0, val barV: Int = 0) {
        val perimetro: Double get() = 2 * (ancho + alto)
        val area: Double get() = ancho * alto
        /** Barras de vidrio: las horizontales miden el ancho del vidrio y las verticales su alto. */
        val barrasM: Double get() = barH * ancho + barV * alto
        /** Cruces (conectores): barras horizontales x barras verticales. */
        val cruces: Int get() = barH * barV
    }

    /**
     * Detalle de cada paño según sus opciones (hoja/fijo, puerta, sándwich, mosquitero).
     * [imp] = imposta interior de la hoja (como la original): 0 ninguna, 1 horizontal x1, 2 horizontal x2, 3 vertical x1, 4 vertical x2.
     * La hoja sigue siendo UNA (un solo perímetro y un solo kit de herraje) y se parte en 2 o 3 vidrios.
     * [subMask] = bits de sándwich por vidrio (bit 0 = arriba/izquierda); -1 = usar [sandwich] para todo.
     */
    private class Detalle(
        val pano: Pano, val hoja: Boolean, val puerta: Boolean, val sandwich: Boolean, val mosq: Boolean, val mec: String,
        val imp: Int = 0, val subMask: Int = -1, val barras: String? = null, val impOff: String? = null,
    ) {
        /** Perfil visible de la hoja: 62 mm (94 mm en puerta). */
        val perfilHoja: Double get() = if (puerta) PERFIL_PUERTA_M else PERFIL_HOJA_M
        /** Vidrio real: fijo = hueco; hoja = hueco reducido en (perfil - 13 mm) por lado. */
        val vidrioAncho: Double get() = if (hoja) (pano.huecoAncho - 2 * (perfilHoja - SOLAPE_HOJA_M)).coerceAtLeast(0.0) else pano.huecoAncho
        val vidrioAlto: Double get() = if (hoja) (pano.huecoAlto - 2 * (perfilHoja - SOLAPE_HOJA_M)).coerceAtLeast(0.0) else pano.huecoAlto
        val hojaAncho: Double get() = pano.huecoAncho + 2 * SOLAPE_HOJA_M
        val hojaAlto: Double get() = pano.huecoAlto + 2 * SOLAPE_HOJA_M

        /** Hojas de las aperturas múltiples (corredizas, verticales, francesa); null si es una hoja simple. */
        val multi: List<AperturasMulti.Hoja>? by lazy {
            if (AperturasMulti.esMulti(mec)) AperturasMulti.hojas(mec, pano.huecoAncho, pano.huecoAlto, puerta) else null
        }

        fun hojaCalc(): HojaCalc {
            val m = multi ?: return HojaCalc(2 * (hojaAncho + hojaAlto), hojaAlto, puerta, mec)
            return HojaCalc(
                perimetroM = m.sumOf { it.perimetroExterior }, altoM = pano.huecoAlto, esPuerta = puerta, mecanismo = mec,
                kits = AperturasMulti.kits(mec, m), manijas = AperturasMulti.manijas(mec, m),
                impostaFrancesaM = AperturasMulti.impostaFrancesaM(mec, m),
                hojasMoviles = m.count { it.movil },
            )
        }

        private fun bit(i: Int) = if (subMask >= 0) ((subMask shr i) and 1) == 1 else sandwich

        /** "HxV HxV HxV" -> barras horizontales y verticales de cada vidrio (0 si no hay dato). */
        private val barrasPorVidrio: List<Pair<Int, Int>> by lazy {
            barras?.trim()?.split(" ")?.filter { it.isNotBlank() }?.map { t ->
                val p = t.split("x").map { it.toIntOrNull() ?: 0 }
                (p.getOrNull(0) ?: 0).coerceAtLeast(0) to (p.getOrNull(1) ?: 0).coerceAtLeast(0)
            } ?: emptyList()
        }
        private fun bh(i: Int) = barrasPorVidrio.getOrNull(i)?.first ?: 0
        private fun bv(i: Int) = barrasPorVidrio.getOrNull(i)?.second ?: 0

        /**
         * Vidrios de la hoja con las fórmulas de la original (pk_v4.a): imposta a 1/2 (o 1/3) del hueco,
         * vidrio de borde = posición - 26 mm (media imposta) - perfil de hoja, vidrio restante = lo que queda menos la(s) imposta(s) de 52 mm.
         */
        val piezas: List<Vid> by lazy {
            val media = ANCHO_IMPOSTA_M / 2
            // Posición de las impostas (mm desde el borde indicado, como los offsets de pk_r4.a); sin dato = reparto igual
            val offs = impOff?.split(",")?.map { it.trim().toDoubleOrNull()?.div(1000.0) } ?: emptyList()
            fun borde(total: Double, n: Int, k: Int, prof: Double) = ((offs.getOrNull(k) ?: (total / n)) - media - prof).coerceAtLeast(0.0)
            /** Vidrios de UNA hoja de vidrio [gw] x [gh] con la imposta interior [imp] (la misma para cada hoja, como la original). */
            fun partir(gw: Double, gh: Double, prof: Double): List<Vid> = when (imp) {
                // 1 = horizontal x1 (offset desde abajo), 2 = horizontal x2 (desde arriba y desde abajo)
                1 -> { val fb = borde(pano.huecoAlto, 2, 0, prof); listOf(Vid(gw, (gh - fb - ANCHO_IMPOSTA_M).coerceAtLeast(0.0), bit(0), bh(0), bv(0)), Vid(gw, fb, bit(1), bh(1), bv(1))) }
                2 -> { val ft = borde(pano.huecoAlto, 3, 0, prof); val fb = borde(pano.huecoAlto, 3, 1, prof); listOf(Vid(gw, ft, bit(0), bh(0), bv(0)), Vid(gw, (gh - ft - fb - 2 * ANCHO_IMPOSTA_M).coerceAtLeast(0.0), bit(1), bh(1), bv(1)), Vid(gw, fb, bit(2), bh(2), bv(2))) }
                // 3 = vertical x1 (offset desde la izquierda), 4 = vertical x2 (desde izquierda y desde derecha)
                3 -> { val fb = borde(pano.huecoAncho, 2, 0, prof); listOf(Vid(fb, gh, bit(0), bh(0), bv(0)), Vid((gw - fb - ANCHO_IMPOSTA_M).coerceAtLeast(0.0), gh, bit(1), bh(1), bv(1))) }
                4 -> { val fl = borde(pano.huecoAncho, 3, 0, prof); val fr = borde(pano.huecoAncho, 3, 1, prof); listOf(Vid(fl, gh, bit(0), bh(0), bv(0)), Vid((gw - fl - fr - 2 * ANCHO_IMPOSTA_M).coerceAtLeast(0.0), gh, bit(1), bh(1), bv(1)), Vid(fr, gh, bit(2), bh(2), bv(2))) }
                else -> listOf(Vid(gw, gh, sandwich, bh(0), bv(0)))
            }
            multi?.let { m ->
                // Aperturas de varias hojas: sin imposta, un vidrio por hoja (sándwich por hoja); con imposta (horizontal en
                // corredizas y francesas, vertical en corredizas verticales) cada hoja se parte igual y los datos son por vidrio.
                return@lazy if (imp > 0) m.flatMap { h -> partir(h.ancho, h.alto, h.perfil) }
                else m.mapIndexed { i, h -> Vid(h.ancho, h.alto, bit(i), bh(i), bv(i)) }
            }
            partir(vidrioAncho, vidrioAlto, if (hoja) perfilHoja else 0.0)
        }

        /** Largo de cada imposta interior: ancho del vidrio (horizontales) o alto del vidrio (verticales); una serie por hoja. */
        val impostasInternas: List<Double> get() {
            val n = when (imp) { 1, 3 -> 1; 2, 4 -> 2; else -> return emptyList() }
            val horizontal = imp <= 2
            val hs = multi
            return if (hs == null) List(n) { if (horizontal) vidrioAncho else vidrioAlto }
            else hs.flatMap { h -> List(n) { if (horizontal) h.ancho else h.alto } }
        }
    }

    private fun detalles(item: OrderItem, g: Geometria): List<Detalle> = g.panos.map { p ->
        val puerta = item.panePuerta[p.clave] ?: item.panePuerta[p.padre] ?: false
        val mec = item.paneMechanisms[p.clave] ?: item.paneMechanisms[p.padre] ?: MECANISMO_FIJO
        // Códigos 11..14 de paneCols = imposta interior de la hoja (11 hor.x1, 12 hor.x2, 13 ver.x1, 14 ver.x2)
        val imp = if (p.clave == p.padre) (item.paneCols[p.padre] ?: 0).let { if (it in 11..14) it - 10 else 0 }.takeIf { AperturasMulti.impostaPermitida(mec, it) } ?: 0 else 0
        Detalle(
            p, hoja = puerta || mec != MECANISMO_FIJO, puerta = puerta,
            sandwich = item.paneSandwich[p.clave] ?: item.paneSandwich[p.padre] ?: false,
            mosq = item.paneMosqNet[p.clave] ?: item.paneMosqNet[p.padre] ?: false,
            mec = mec, imp = imp, subMask = if (imp > 0 || AperturasMulti.esMulti(mec)) (item.paneSandwichSub[p.padre] ?: -1) else -1,
            barras = item.paneBars[p.clave] ?: item.paneBars[p.padre],
            impOff = item.paneImpOff[p.padre],
        )
    }

    /** Redondeo a 4 decimales de las cantidades agregadas (m, m²), como `g.a(x, 4)` de la original. */
    private fun r4(x: Double) = Math.round(x * 10000.0) / 10000.0

    fun cantidades(item: OrderItem): Cantidades {
        val g = geometria(item)
        val d = detalles(item, g)
        val altoM = item.altoTotalMm / 1000.0
        val anchoM = item.anchoTotalMm / 1000.0
        val area = item.areaM2
        val bases = g.bases.ifEmpty { listOf(ReglasBases.BaseDim(anchoM, altoM)) }
        // Cada base es un marco propio: perímetro = suma de los perímetros de sus bases
        val perimetroMarco = bases.sumOf { 2 * (it.anchoM + it.altoM) }

        val hojas = d.filter { it.hoja }.map { it.hojaCalc() }
        val piezas = d.flatMap { it.piezas }
        val sandwiches = piezas.filter { it.sandwich }
        val vidrios = piezas.filter { !it.sandwich }
        val impInternas = d.flatMap { it.impostasInternas }
        val mosqs = d.filter { it.mosq }

        // Red del mosquitero: hueco + 13 mm por lado
        val mosqAreas = mosqs.map { (it.pano.huecoAncho + 2 * SOLAPE_HOJA_M) * (it.pano.huecoAlto + 2 * SOLAPE_HOJA_M) }
        val mosqPerims = mosqs.map { 2 * ((it.pano.huecoAncho + 2 * SOLAPE_HOJA_M) + (it.pano.huecoAlto + 2 * SOLAPE_HOJA_M)) }

        // Superficie laminable (película sobre el perfil). Cara interior = ancho de perfil;
        // cara exterior = ancho + 27 mm (sobre el marco/imposta) o + 14 mm (hoja), como en la original.
        val hojasNormales = hojas.filter { !it.esPuerta }
        val hojasPuerta = hojas.filter { it.esPuerta }
        // Quirks de la original: la imposta interior de hoja no suma superficie interior (ancho 0) y por fuera cuenta largo x 54 mm;
        // los junquillos (shtapik) suman 27 mm por la cara interior (pk_q4).
        val glazing = piezas.sumOf { it.perimetro }
        val lamInterna = glazing * 0.027 + perimetroMarco * ANCHO_MARCO_M + g.impostaM * ANCHO_IMPOSTA_M +
            hojasNormales.sumOf { it.perimetroM } * (PERFIL_HOJA_M + 0.012) +
            hojasPuerta.sumOf { it.perimetroM } * (PERFIL_PUERTA_M + 0.012)
        val lamExterna = impInternas.sum() * 0.054 + perimetroMarco * (ANCHO_MARCO_M + 0.027) + g.impostaM * (ANCHO_IMPOSTA_M + 0.054) +
            hojasNormales.sumOf { it.perimetroM } * (PERFIL_HOJA_M + 0.014) +
            hojasPuerta.sumOf { it.perimetroM } * (PERFIL_PUERTA_M + 0.014)

        val hojaPerim = hojasNormales.sumOf { it.perimetroM }
        val puertaPerim = hojasPuerta.sumOf { it.perimetroM }

        return Cantidades(
            areaM2 = r4(area),
            marcoM = r4(perimetroMarco),
            impostaM = r4(g.impostaM + impInternas.sum()),
            glazingM = r4(glazing),
            cauchoM = r4(glazing + hojaPerim + puertaPerim), // junta del vidrio + junta de las hojas
            hojaM = r4(hojaPerim),
            hojaPuertaM = r4(puertaPerim),
            vidrioM2 = r4(vidrios.sumOf { it.area }),
            sandwichM2 = r4(sandwiches.sumOf { it.area }),
            hojas = hojasNormales.sumOf { if (it.mecanismo != MECANISMO_FIJO && it.mecanismo != HOJA_FIJA) it.hojasMoviles else 0 },
            hojasPuerta = hojasPuerta.size,
            panosMosquitero = mosqs.size,
            anchoM = anchoM,
            altoM = altoM,
            vidrioPerimM = r4(vidrios.sumOf { it.perimetro }),
            mosqAreaM2 = r4(mosqAreas.sum()),
            mosqPerimM = r4(mosqPerims.sum()),
            impostaN = g.impostaN + impInternas.size,
            barrasM = r4(piezas.sumOf { it.barrasM }),
            barrasCruces = piezas.sumOf { it.cruces },
            hojasDetalle = hojas,
            lamInternaM2 = r4(lamInterna),
            lamExternaM2 = r4(lamExterna),
            bases = bases,
            adic = ReglasBases.metros(bases, item.opciones),
        )
    }

    // ===================== PIEZAS PARA LA GUÍA DE CORTE =====================

    /** Pieza de perfil a cortar: [perfil] = "Marco", "Imposta", "Hoja", "Hoja puerta" o "Junquillo". */
    data class PiezaCorte(val perfil: String, val etiqueta: String, val largoMm: Int)

    /** Pieza de vidrio (o panel sándwich) a pedir/cortar, en mm. */
    data class PiezaVidrio(val etiqueta: String, val anchoMm: Int, val altoMm: Int, val sandwich: Boolean)

    private fun mm(m: Double) = Math.round(m * 1000.0).toInt()

    /**
     * Piezas de perfil de UNA ventana (largo de corte, medido en la punta larga de los cortes a 45°).
     *  - Marco: 2 x ancho + 2 x alto de la ventana. Hoja: 2 x ancho + 2 x alto de cada hoja (hueco + 13 mm por lado).
     *  - Imposta: largo libre entre marco/imposta (corte a escuadra, sin soldadura).
     *  - Junquillo (shtapik): 4 piezas por paño, con las medidas del vidrio.
     * [soldaduraMm] se suma a cada pieza soldada (marco y hoja) para compensar el material que consume la soldadura.
     */
    fun piezasCorte(item: OrderItem, soldaduraMm: Int = 0): List<PiezaCorte> {
        val g = geometria(item)
        val d = detalles(item, g)
        val out = mutableListOf<PiezaCorte>()
        fun add(perfil: String, etiqueta: String, largoM: Double, n: Int, extraMm: Int = 0) {
            val l = mm(largoM) + extraMm
            if (l > 0) repeat(n) { out += PiezaCorte(perfil, etiqueta, l) }
        }
        g.bases.ifEmpty { listOf(ReglasBases.BaseDim(item.anchoTotalMm / 1000.0, item.altoTotalMm / 1000.0)) }.forEach {
            add("Marco", "marco ancho", it.anchoM, 2, soldaduraMm)
            add("Marco", "marco alto", it.altoM, 2, soldaduraMm)
        }
        g.impostaCortes.forEach { add("Imposta", "imposta", it, 1) }
        d.filter { it.hoja }.forEach {
            val perfil = if (it.puerta) "Hoja puerta" else "Hoja"
            val m = it.multi
            if (m == null) {
                add(perfil, "hoja ancho", it.hojaAncho, 2, soldaduraMm)
                add(perfil, "hoja alto", it.hojaAlto, 2, soldaduraMm)
            } else {
                m.forEach { h ->
                    add(perfil, "hoja ancho", h.anchoExterior, 2, soldaduraMm)
                    add(perfil, "hoja alto", h.altoExterior, 2, soldaduraMm)
                }
                val fr = AperturasMulti.impostaFrancesaM(it.mec, m)
                if (fr > 0) add("Imposta", "imposta francesa", fr, 1)
            }
        }
        // Impostas interiores de hoja: corte a escuadra, largo = medida del vidrio
        d.forEach { det -> det.impostasInternas.forEach { add("Imposta", "imposta interior", it, 1) } }
        d.flatMap { it.piezas }.forEach {
            add("Junquillo", "junquillo ancho", it.ancho, 2)
            add("Junquillo", "junquillo alto", it.alto, 2)
            add("Barra de vidrio", "barra horizontal", it.ancho, it.barH)
            add("Barra de vidrio", "barra vertical", it.alto, it.barV)
        }
        return out
    }

    /** Vidrios (y paneles sándwich) de UNA ventana, con la medida real que se corta. */
    fun piezasVidrio(item: OrderItem): List<PiezaVidrio> =
        detalles(item, geometria(item)).flatMap { det ->
            det.piezas.filter { it.ancho > 0 && it.alto > 0 }.map {
                PiezaVidrio(if (det.hoja) "hoja" else "fijo", mm(it.ancho), mm(it.alto), it.sandwich)
            }
        }

    // ===================== ADVERTENCIAS =====================

    /**
     * Problemas que harían que la ventana se cotice por MENOS de lo que cuesta (precios faltantes o en cero,
     * materiales elegidos que ya no existen en Precios). La original avisa "this materials not found in prices".
     */
    fun advertencias(item: OrderItem, precios: Precios): List<String> {
        val op = item.opciones
        val out = mutableListOf<String>()
        val q = cantidades(item)

        fun sinPrecios(f: List<String>?) = f == null || f.drop(1).none { (it.replace(",", ".").toDoubleOrNull() ?: 0.0) > 0 }

        // Sistema de ventanas
        val sis = op.sistemaVentana?.let { fila(precios.ventanas, it) }
        when {
            op.sistemaVentana == null -> out += "No hay sistema de ventanas elegido: perfiles y herrajes se cotizan en 0."
            sis == null -> out += "El sistema \"${op.sistemaVentana}\" ya no existe en Precios."
            sinPrecios(sis) -> out += "El sistema \"${op.sistemaVentana}\" no tiene ningún precio cargado."
            else -> {
                if (num(sis, COL_MARCO) == 0.0) out += "El sistema no tiene precio de perfil de marco."
                if (q.impostaM > 0 && num(sis, COL_IMPOSTA) == 0.0) out += "La ventana lleva impostas pero el sistema no tiene precio de imposta."
                if (q.hojaM > 0 && num(sis, COL_HOJA) == 0.0) out += "La ventana lleva hojas pero el sistema no tiene precio de perfil de hoja."
                if (q.hojaPuertaM > 0 && num(sis, COL_HOJA_PUERTA) == 0.0) out += "La ventana lleva hoja de puerta pero el sistema no tiene precio de perfil de hoja puerta."
                if (q.barrasM > 0 && num(sis, COL_BARRAS) == 0.0) out += "Hay barras de vidrio (glazing bars) pero el sistema no tiene precio de barras."
                if (q.sandwichM2 > 0 && num(sis, COL_SANDWICH) == 0.0) out += "Hay paños sándwich pero el sistema no tiene precio de panel sándwich."
                q.hojasDetalle.map { it.mecanismo }.distinct().forEach { m ->
                    val (col, nombre) = when (m) {
                        "1", "3" -> COL_KIT_CORREDERA to "corredizo"
                        "2", "9" -> COL_KIT_HORIZONTAL to "oscilante"
                        "4", "6" -> COL_KIT_VERTICAL to "practicable"
                        "7", "8" -> COL_KIT_ROTACION to "oscilobatiente"
                        else -> when (AperturasMulti.grupo(m)) {
                            AperturasMulti.Grupo.CORREDIZA -> COL_KIT_CORREDERA to "corrediza múltiple"
                            AperturasMulti.Grupo.CORREDIZA_VERT -> COL_KIT_CORREDERA_VERT to "corrediza vertical"
                            AperturasMulti.Grupo.FRANCESA -> COL_KIT_FRANCESA to "puerta francesa"
                            null -> 0 to ""
                        }
                    }
                    if (col != 0 && num(sis, col) == 0.0) out += "Hay hojas con apertura $nombre pero el sistema no tiene precio de ese kit de herraje."
                }
            }
        }

        // Cristal
        val cri = op.tipoCristal?.let { fila(precios.cristal, it) }
        when {
            op.tipoCristal == null -> if (q.vidrioM2 > 0) out += "No hay tipo de cristal elegido: el vidrio se cotiza en 0."
            cri == null -> out += "El cristal \"${op.tipoCristal}\" ya no existe en Precios."
            num(cri, 1) + num(cri, 2) + num(cri, 3) == 0.0 -> out += "El cristal \"${op.tipoCristal}\" no tiene precio de vidrio (columnas vidrio 1, 2, 3)."
        }

        // Elecciones que ya no existen en las tablas
        op.planFinanciacion?.let { if (fila(precios.planes, it) == null) out += "El plan \"$it\" ya no existe en Precios (se cotiza sin descuento ni márgenes)." }
        if (op.planFinanciacion == null) out += "No hay plan de financiamiento: no se aplican márgenes (se vende al costo)."
        op.mueble?.let { if (fila(precios.muebles, it) == null) out += "El mueble \"$it\" ya no existe en Precios." }
        op.umbralesExternos.filter { fila(precios.umbralExt, it) == null }.forEach { out += "El umbral exterior \"$it\" ya no existe en Precios." }
        op.umbralesInternos.filter { fila(precios.umbralInt, it) == null }.forEach { out += "El umbral interior \"$it\" ya no existe en Precios." }
        op.extras.filter { n -> precios.extras.none { it.nombre == n } }.forEach { out += "El extra \"$it\" ya no existe en Precios." }
        op.trabajos.filter { n -> precios.empleos.none { it.nombre == n } }.forEach { out += "El trabajo \"$it\" ya no existe en Precios." }
        op.laminacionInterna?.let { if (fila(precios.laminacion, it) == null) out += "La laminación interior \"$it\" ya no existe en Precios." }
        op.laminacionExterna?.let { if (fila(precios.laminacion, it) == null) out += "La laminación exterior \"$it\" ya no existe en Precios." }
        op.color?.let { c -> if (precios.colores.none { it.nombre == c }) out += "El color \"$c\" ya no existe en Precios." }

        // Mosquitero
        if (q.panosMosquitero > 0) {
            val m = op.mosquitero?.let { fila(precios.mosquitera, it) }
            when {
                op.mosquitero == null -> out += "Hay paños con mosquitero pero no elegiste el sistema de mosquitero: se cotiza en 0."
                m == null -> out += "El mosquitero \"${op.mosquitero}\" ya no existe en Precios."
                sinPrecios(m) -> out += "El mosquitero \"${op.mosquitero}\" no tiene precios cargados."
            }
        }

        // Dimensiones imposibles
        val g = geometria(item)
        if (g.panos.any { it.huecoAncho <= 0.0 || it.huecoAlto <= 0.0 }) out += "Alguna sección es demasiado pequeña para los perfiles (el hueco es 0 o negativo)."
        if (g.panos.any { d -> val h = item.paneMechanisms[d.clave] ?: item.paneMechanisms[d.padre]; h != null && h != MECANISMO_FIJO && (d.huecoAncho < 0.25 || d.huecoAlto < 0.25) }) {
            out += "Hay una hoja practicable/corrediza con menos de 25 cm de hueco: revisa las medidas."
        }
        return out
    }

    // ===================== AYUDANTES =====================

    // Columnas de Precios.ventanas (índice 0 = nombre). Mismo orden que la pestaña "sistema" de la original.
    const val COL_MARCO = 1; const val COL_IMPOSTA = 2; const val COL_HOJA = 3; const val COL_HOJA_PUERTA = 4
    const val COL_SANDWICH = 5; const val COL_SHTAPIK = 6; const val COL_CAUCHO = 7
    const val COL_BARRAS = 8; const val COL_CONECTOR_BARRAS = 9
    const val COL_REFUERZO_MARCO = 10; const val COL_REFUERZO_IMPOSTA = 11
    const val COL_REFUERZO_HOJA = 12; const val COL_REFUERZO_PUERTA = 13
    const val COL_KIT_ROTACION = 14; const val COL_KIT_HORIZONTAL = 15; const val COL_KIT_VERTICAL = 16
    const val COL_KIT_CORREDERA = 17; const val COL_KIT_CORREDERA_VERT = 18
    const val COL_KIT_FRANCESA = 19; const val COL_IMPOSTA_FRANCESA = 20; const val COL_GUIAS_CORREDERA = 21
    const val COL_ESQUINERO_MARCO = 22; const val COL_ESQUINERO_HOJA = 23; const val COL_CONECTOR_IMPOSTA = 24
    const val COL_PERFIL_ASIENTO = 25; const val COL_DORSILL = 26; const val COL_JAMBA_ESQUINA = 27
    const val COL_JUNTA = 28; const val COL_JUNTA_90 = 29; const val COL_JUNTA_135 = 30; const val COL_HUECO = 31
    const val COL_EXT_15 = 32; const val COL_EXT_20 = 33; const val COL_EXT_40 = 34
    const val COL_EXT_60 = 35; const val COL_EXT_80 = 36; const val COL_EXT_100 = 37
    /** Cantidad de columnas numéricas del sistema (sin el nombre). */
    const val COLUMNAS_SISTEMA = 37


    /**
     * Unidad de extras/trabajos, en el orden de los 6 botones (igual que la original):
     * 0 pieza · 1 área · 2 perímetro · 3 pendientes (W + 2H, inferido) · 4 ancho · 5 alto.
     */
    private fun precioSeleccion(f: FilaSeleccion, q: Cantidades): Double {
        val p = f.precio.replace(",", ".").toDoubleOrNull() ?: 0.0
        return when (f.unidad) {
            1 -> p * q.areaM2
            2 -> p * q.marcoM
            3 -> p * (q.anchoM + 2 * q.altoM)
            4 -> p * q.anchoM
            5 -> p * q.altoM
            else -> p // 0 = por pieza
        }
    }

    private fun fila(tabla: List<List<String>>, nombre: String): List<String>? =
        tabla.find { it.firstOrNull()?.trim() == nombre.trim() }

    /** Fila elegida en el compás; si no se eligió nada devuelve null (la partida vale 0). */
    private fun filaElegida(tabla: List<List<String>>, nombre: String?): List<String>? =
        nombre?.let { fila(tabla, it) }

    private fun num(fila: List<String>?, i: Int): Double =
        fila?.getOrNull(i)?.replace(",", ".")?.trim()?.toDoubleOrNull() ?: 0.0
}
