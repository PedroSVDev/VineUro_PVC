package com.example.vineuro_pvc.data

import java.util.Calendar

/**
 * Indicadores de gestión (tablero BI, RF13 y RF14 – SCC-002).
 *
 * Calcula con las cotizaciones registradas (pedidos + oferta comercial + tiempo de trabajo) los tres indicadores del estudio:
 *  - TPC: tiempo promedio de cotización = (Σ Ti) / N, en minutos. Ti = tiempo activo que se trabajó en la cotización
 *         (diseño del pedido + oferta comercial), sin contar las pausas.
 *  - NEC: nivel de estandarización = CE / CT. CE = ofertas con condiciones generales y Anexo A completos;
 *         CT = ofertas evaluadas.
 *  - PDM: porcentaje de desperdicio de material = ((ML - MU) / ML) × 100, calculado con el plan de corte de la guía de corte.
 *         Se informa con dos criterios: sin contar los retazos reutilizables (el de la guía) y contándolos como pérdida.
 *
 * Se pueden filtrar por periodo (fecha de creación del pedido) y por modelo de ventana, y se resumen por modelo y por mes
 * junto con lecturas en lenguaje simple para tomar decisiones. Sin dependencias de Android para probarlo con JUnit.
 */
object IndicadoresBi {

    /** Una cotización = el pedido, su oferta comercial (null si todavía no se generó) y el tiempo activo medido (null si no hay). */
    data class Cotizacion(val pedido: Pedido, val oferta: CotizacionPedido?, val tiempo: TiempoActivoPedido? = null)

    /** Modelo de ventana según su tipo de apertura. */
    enum class Modelo(val etiqueta: String) {
        CORREDERA("Corredera"),
        BATIENTE("Batiente"),
        OSCILOBATIENTE("Oscilobatiente"),
        PARALELA("Paralela"),
        OTROS("Otros"),
    }

    /** Cláusulas del Anexo A (garantía, material, modalidades de pago, entrega, precios y métodos de pago). */
    private val ANEXO_A = listOf("GARANTÍA", "MATERIAL", "PAGO LIMA", "PAGO PROVINCIAS", "ENTREGA", "PRECIOS", "MÉTODOS DE PAGO")

    private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

    /** Oferta que no cumple la estandarización y por qué. */
    data class OfertaIncompleta(val codigo: String, val cliente: String, val motivos: List<String>)

    data class Resultado(
        /** Cotizaciones (pedidos) que cumplen el filtro. */
        val cotizaciones: Int,
        /** CT: cotizaciones con oferta comercial generada. */
        val conOferta: Int,
        /** TPC en minutos de trabajo activo; null si no hay ninguna cotización con tiempo medido. */
        val tpcMin: Double?,
        /** N: cotizaciones que entraron en el TPC. */
        val conTiempo: Int,
        /** Cotizaciones con oferta que no tienen tiempo medido completo (hechas antes del cronómetro). */
        val sinTiempo: Int,
        /** NEC entre 0 y 1; null si no hay ofertas. */
        val nec: Double?,
        /** CE: ofertas completas y estandarizadas. */
        val completas: Int,
        /** Ofertas que no cumplen, con el motivo. */
        val incompletas: List<OfertaIncompleta>,
        /** PDM en %, sin contar los retazos reutilizables; null si no hay material comprado. */
        val pdmPct: Double?,
        /** PDM en %, contando también los retazos reutilizables como pérdida. */
        val pdmConRetazosPct: Double?,
        /** ML: largo de varillas compradas (mm). */
        val compradoMm: Long,
        /** Desperdicio (mm): sobrantes que no se pueden reutilizar. */
        val desperdicioMm: Long,
        /** Retazos reutilizables que deja el plan de corte (mm). */
        val retazosMm: Long,
    )

    data class FilaModelo(val modelo: Modelo, val resultado: Resultado)
    data class FilaMes(val etiqueta: String, val resultado: Resultado)

    // ---------- Modelo de una ventana ----------

    /** Tipo de apertura de un mecanismo; null si la hoja es fija u otra apertura que no es uno de los cuatro modelos. */
    private fun familia(mec: String): Modelo? = when {
        mec == "1" || mec == "3" -> Modelo.CORREDERA
        mec == "4" || mec == "6" -> Modelo.BATIENTE
        mec == "7" || mec == "8" -> Modelo.OSCILOBATIENTE
        mec == "2" || mec == "9" -> Modelo.PARALELA
        AperturasMulti.grupo(mec) == AperturasMulti.Grupo.CORREDIZA -> Modelo.CORREDERA
        AperturasMulti.grupo(mec) == AperturasMulti.Grupo.FRANCESA -> Modelo.BATIENTE
        else -> null
    }

    /** Modelo de la ventana: el tipo de apertura más usado en sus hojas; "Otros" si es fija o no encaja. */
    fun modeloDe(item: OrderItem): Modelo =
        item.paneMechanisms.values.mapNotNull { familia(it) }
            .groupingBy { it }.eachCount()
            .entries.maxWithOrNull(compareBy<Map.Entry<Modelo, Int>> { it.value }.thenBy { -it.key.ordinal })
            ?.key ?: Modelo.OTROS

    // ---------- Estandarización ----------

    /** Qué le falta a la oferta para estar completa y estandarizada (lista vacía = completa). */
    fun motivosIncompleta(oferta: CotizacionPedido): List<String> {
        val m = mutableListOf<String>()
        if (oferta.numero.isBlank()) m.add("sin número de cotización")
        if (oferta.vigenciaDias <= 0) m.add("sin vigencia")
        if (oferta.clausulas.isEmpty()) {
            m.add("sin condiciones generales")
            return m
        }
        val titulos = oferta.clausulas.map { it.titulo.trim().uppercase() }.toSet()
        ANEXO_A.filter { it !in titulos }.forEach { m.add("falta la cláusula $it del Anexo A") }
        oferta.clausulas.filter { it.texto.isBlank() }.forEach { m.add("la cláusula ${it.titulo.trim().uppercase()} está vacía") }
        return m
    }

    /** Oferta con condiciones generales y Anexo A completos. */
    fun esOfertaCompleta(oferta: CotizacionPedido): Boolean = motivosIncompleta(oferta).isEmpty()

    // ---------- Cálculo ----------

    private fun filtrar(cotizaciones: List<Cotizacion>, desde: Long?, hasta: Long?, modelo: Modelo?): List<Cotizacion> =
        cotizaciones.filter { c ->
            (desde == null || c.pedido.fechaCreacion >= desde) &&
                (hasta == null || c.pedido.fechaCreacion <= hasta) &&
                (modelo == null || c.pedido.items.any { modeloDe(it) == modelo })
        }

    private fun resumir(
        filtradas: List<Cotizacion>,
        modelo: Modelo?,
        params: OptimizadorCorte.Parametros,
        cache: MutableMap<String, OptimizadorCorte.Plan>?,
    ): Resultado {
        // TPC y NEC: sobre las cotizaciones con oferta generada
        val conOferta = filtradas.filter { it.oferta != null }
        val tiempos = mutableListOf<Double>()
        var sinTiempo = 0
        val incompletas = mutableListOf<OfertaIncompleta>()
        for (c in conOferta) {
            val t = c.tiempo
            if (t != null && t.desdeInicio && t.ms > 0) tiempos.add(t.minutos) else sinTiempo++
            val motivos = motivosIncompleta(c.oferta!!)
            if (motivos.isNotEmpty()) incompletas.add(OfertaIncompleta(c.pedido.codigo, c.pedido.clienteNombre, motivos))
        }
        val completas = conOferta.size - incompletas.size

        // PDM: plan de corte de cada pedido (solo con las ventanas del modelo elegido)
        var comprado = 0L
        var desperdicio = 0L
        var piezas = 0L
        var retazos = 0L
        for (c in filtradas) {
            val items = if (modelo == null) c.pedido.items else c.pedido.items.filter { modeloDe(it) == modelo }
            if (items.isEmpty()) continue
            val clave = c.pedido.id + "|" + (modelo?.name ?: "*") + "|" + c.pedido.fechaActualizacion
            val plan = cache?.getOrPut(clave) { OptimizadorCorte.planificar(items, params) } ?: OptimizadorCorte.planificar(items, params)
            comprado += plan.grupos.sumOf { it.largoCompradoMm }
            desperdicio += plan.grupos.sumOf { it.desperdicioMm }
            piezas += plan.grupos.sumOf { it.largoPiezasMm }
            retazos += plan.grupos.sumOf { g -> g.retazos.sumOf { it.toLong() } }
        }

        return Resultado(
            cotizaciones = filtradas.size,
            conOferta = conOferta.size,
            tpcMin = if (tiempos.isEmpty()) null else tiempos.sum() / tiempos.size,
            conTiempo = tiempos.size,
            sinTiempo = sinTiempo,
            nec = if (conOferta.isEmpty()) null else completas.toDouble() / conOferta.size,
            completas = completas,
            incompletas = incompletas,
            pdmPct = if (comprado == 0L) null else desperdicio * 100.0 / comprado,
            pdmConRetazosPct = if (comprado == 0L) null else (comprado - piezas) * 100.0 / comprado,
            compradoMm = comprado,
            desperdicioMm = desperdicio,
            retazosMm = retazos,
        )
    }

    /**
     * @param desde,hasta periodo (fecha de creación del pedido, en milisegundos, ambos incluidos); null = sin límite.
     * @param modelo solo los pedidos que incluyen ese modelo; el desperdicio se calcula solo con las ventanas de ese modelo.
     * @param cache guarda los planes de corte ya calculados para no repetirlos entre filtros (opcional).
     */
    fun calcular(
        cotizaciones: List<Cotizacion>,
        desde: Long? = null,
        hasta: Long? = null,
        modelo: Modelo? = null,
        params: OptimizadorCorte.Parametros = OptimizadorCorte.Parametros(),
        cache: MutableMap<String, OptimizadorCorte.Plan>? = null,
    ): Resultado = resumir(filtrar(cotizaciones, desde, hasta, modelo), modelo, params, cache)

    /** Un resultado por modelo (solo los que tienen al menos una cotización en el periodo). */
    fun porModelo(
        cotizaciones: List<Cotizacion>,
        desde: Long? = null,
        hasta: Long? = null,
        params: OptimizadorCorte.Parametros = OptimizadorCorte.Parametros(),
        cache: MutableMap<String, OptimizadorCorte.Plan>? = null,
    ): List<FilaModelo> = Modelo.values()
        .map { FilaModelo(it, calcular(cotizaciones, desde, hasta, it, params, cache)) }
        .filter { it.resultado.cotizaciones > 0 }

    /** Resultado por mes (fecha de creación del pedido), del más antiguo al más reciente; solo los últimos [maxMeses] con cotizaciones. */
    fun porMes(
        cotizaciones: List<Cotizacion>,
        desde: Long? = null,
        hasta: Long? = null,
        modelo: Modelo? = null,
        params: OptimizadorCorte.Parametros = OptimizadorCorte.Parametros(),
        cache: MutableMap<String, OptimizadorCorte.Plan>? = null,
        maxMeses: Int = 6,
    ): List<FilaMes> {
        fun clave(t: Long): Int = Calendar.getInstance().apply { timeInMillis = t }.let { it.get(Calendar.YEAR) * 100 + it.get(Calendar.MONTH) }
        return filtrar(cotizaciones, desde, hasta, modelo)
            .groupBy { clave(it.pedido.fechaCreacion) }
            .toSortedMap()
            .entries.toList().takeLast(maxMeses)
            .map { (k, lista) -> FilaMes("${MESES[k % 100]} ${k / 100}", resumir(lista, modelo, params, cache)) }
    }

    // ---------- Lectura para decidir ----------

    /** Frases cortas que dicen qué muestran los números y qué conviene revisar. */
    fun lecturas(r: Resultado, modelos: List<FilaModelo>, meses: List<FilaMes>): List<String> {
        val l = mutableListOf<String>()
        fun f1(x: Double) = String.format(java.util.Locale.US, "%.1f", x)

        if (r.cotizaciones == 0) return listOf("Todavía no hay cotizaciones en este filtro.")

        // Tiempo
        if (r.conOferta > 0 && r.conTiempo == 0) {
            l.add("El tiempo de cotización se empieza a medir con las cotizaciones nuevas; las anteriores no tienen tiempo registrado.")
        }
        val promedio = r.tpcMin
        if (promedio != null) {
            val lentos = modelos.filter { it.resultado.conTiempo >= 2 && it.resultado.tpcMin != null }
            val mas = lentos.maxByOrNull { it.resultado.tpcMin!! }
            if (lentos.size >= 2 && mas != null && mas.resultado.tpcMin!! > promedio * 1.25) {
                l.add("${mas.modelo.etiqueta} es el modelo que más tarda: ${f1(mas.resultado.tpcMin!!)} min frente a ${f1(promedio)} min en promedio. Conviene dejar preconfiguradas sus medidas y opciones más usadas.")
            }
        }

        // Tendencia mensual del tiempo
        val conTpc = meses.filter { it.resultado.tpcMin != null }
        if (conTpc.size >= 2) {
            val a = conTpc[conTpc.size - 2]; val b = conTpc.last()
            val pa = a.resultado.tpcMin!!; val pb = b.resultado.tpcMin!!
            if (pa > 0) {
                val cambio = (pb - pa) / pa * 100
                l.add(
                    if (cambio <= -5) "El tiempo por cotización bajó ${f1(-cambio)} % de ${a.etiqueta} (${f1(pa)} min) a ${b.etiqueta} (${f1(pb)} min)."
                    else if (cambio >= 5) "El tiempo por cotización subió ${f1(cambio)} % de ${a.etiqueta} (${f1(pa)} min) a ${b.etiqueta} (${f1(pb)} min): conviene revisar qué lo está demorando."
                    else "El tiempo por cotización se mantiene estable entre ${a.etiqueta} y ${b.etiqueta} (${f1(pa)} y ${f1(pb)} min).",
                )
            }
        }

        // Estandarización
        if (r.conOferta > 0) {
            if (r.incompletas.isEmpty()) {
                l.add("Todas las ofertas del periodo salieron con condiciones generales y Anexo A completos.")
            } else {
                l.add("${r.incompletas.size} de ${r.conOferta} ofertas no salieron completas (toca el NEC para ver cuáles y por qué). Corregir la plantilla de términos evita repetir el ajuste en cada cotización.")
            }
        }

        // Material
        if (r.compradoMm > 0) {
            if (r.retazosMm > 0) {
                l.add("El plan de corte deja ${f1(r.retazosMm / 1000.0)} m en retazos reutilizables: conviene revisarlos antes de comprar más perfil.")
            }
            val pdm = r.pdmPct
            if (pdm != null) {
                val peor = modelos.filter { it.resultado.pdmPct != null && it.resultado.compradoMm >= 6000 }.maxByOrNull { it.resultado.pdmPct!! }
                if (peor != null && modelos.size >= 2 && peor.resultado.pdmPct!! > pdm * 1.25 && peor.resultado.pdmPct!! - pdm >= 0.5) {
                    l.add("${peor.modelo.etiqueta} es el modelo con más desperdicio de perfil: ${f1(peor.resultado.pdmPct!!)} % frente a ${f1(pdm)} % en promedio.")
                }
            }
        }
        return l
    }
}
