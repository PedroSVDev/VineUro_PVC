package com.example.vineuro_pvc

import com.example.vineuro_pvc.data.Clausula
import com.example.vineuro_pvc.data.CotizacionPedido
import com.example.vineuro_pvc.data.IndicadoresBi
import com.example.vineuro_pvc.data.IndicadoresBi.Cotizacion
import com.example.vineuro_pvc.data.IndicadoresBi.Modelo
import com.example.vineuro_pvc.data.OptimizadorCorte
import com.example.vineuro_pvc.data.OrderItem
import com.example.vineuro_pvc.data.Pedido
import com.example.vineuro_pvc.data.TerminosCondiciones
import com.example.vineuro_pvc.data.TiempoActivoPedido
import com.example.vineuro_pvc.data.WindowConfig
import com.example.vineuro_pvc.data.WindowConfig.LayoutType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** Indicadores de gestión (tablero BI): TPC, NEC y PDM, con filtro por periodo y por modelo (RF13 y RF14). */
class IndicadoresBiTest {

    private fun item(mec: String?, indice: Int = 1, ancho: Int = 1500, alto: Int = 1800): OrderItem {
        val cfg = WindowConfig(id = "t", nombre = "t", categoria = "t", layoutType = LayoutType.GRID, cols = 1, rows = 1)
        return OrderItem.create(indice, cfg, alto, ancho).also { if (mec != null) it.paneMechanisms[0] = mec }
    }

    private fun pedido(items: List<OrderItem>, creado: Long = 1_000_000L) =
        Pedido(clienteId = "c", clienteNombre = "n", items = items, fechaCreacion = creado, fechaActualizacion = creado)

    private fun oferta(p: Pedido, clausulas: List<Clausula> = TerminosCondiciones.porDefecto(), numero: String = "0001") =
        CotizacionPedido(p.id, numero, 3.8, "manual", 50, 3, clausulas, fechaActualizacion = p.fechaCreacion + 60_000L)

    /** Cotización con oferta y [minutos] de trabajo activo medidos desde que se creó el pedido (null = sin tiempo medido). */
    private fun cot(
        p: Pedido,
        minutos: Double? = null,
        conOferta: Boolean = true,
        clausulas: List<Clausula> = TerminosCondiciones.porDefecto(),
        desdeInicio: Boolean = true,
    ) = Cotizacion(
        p,
        if (conOferta) oferta(p, clausulas) else null,
        minutos?.let { TiempoActivoPedido((it * 60_000).toLong(), desdeInicio) },
    )

    // ---------------- Modelo ----------------

    @Test fun modelo_segunElTipoDeApertura() {
        assertEquals(Modelo.CORREDERA, IndicadoresBi.modeloDe(item("1")))
        assertEquals(Modelo.BATIENTE, IndicadoresBi.modeloDe(item("4")))
        assertEquals(Modelo.OSCILOBATIENTE, IndicadoresBi.modeloDe(item("7")))
        assertEquals(Modelo.PARALELA, IndicadoresBi.modeloDe(item("2")))
        assertEquals(Modelo.CORREDERA, IndicadoresBi.modeloDe(item("S:1R_2L")))
        assertEquals(Modelo.BATIENTE, IndicadoresBi.modeloDe(item("F:LCF_RC")))
    }

    @Test fun modelo_fijaOSinAperturaEsOtros() {
        assertEquals(Modelo.OTROS, IndicadoresBi.modeloDe(item("10")))
        assertEquals(Modelo.OTROS, IndicadoresBi.modeloDe(item(null)))
    }

    @Test fun modelo_gestaElTipoMasUsadoEnLasHojas() {
        val v = item("1")
        v.paneMechanisms[1] = "4"
        v.paneMechanisms[2] = "4"
        assertEquals(Modelo.BATIENTE, IndicadoresBi.modeloDe(v))
    }

    // ---------------- TPC (tiempo de trabajo real) ----------------

    @Test fun tpc_esElPromedioDelTiempoDeTrabajo() {
        val a = pedido(listOf(item("1"))); val b = pedido(listOf(item("1")))
        val r = IndicadoresBi.calcular(listOf(cot(a, 10.0), cot(b, 20.0)))
        assertEquals(15.0, r.tpcMin!!, 0.001)
        assertEquals(2, r.conTiempo)
        assertEquals(0, r.sinTiempo)
    }

    @Test fun tpc_noUsaLasFechasDelPedido() {
        // La oferta se guardó mucho después de crear el pedido, pero solo se trabajó 12 minutos
        val p = pedido(listOf(item("1")), creado = 1_000L)
        val o = oferta(p).copy(fechaActualizacion = 1_000L + 3L * 24 * 3_600_000)
        val r = IndicadoresBi.calcular(listOf(Cotizacion(p, o, TiempoActivoPedido(12 * 60_000L, true))))
        assertEquals(12.0, r.tpcMin!!, 0.001)
    }

    @Test fun tpc_dejaFueraLasSinTiempoCompletoYLasSinOferta() {
        val a = pedido(listOf(item("1"))); val b = pedido(listOf(item("1")))
        val c = pedido(listOf(item("1"))); val d = pedido(listOf(item("1")))
        val r = IndicadoresBi.calcular(
            listOf(cot(a, 12.0), cot(b, 5.0, desdeInicio = false), cot(c, null), cot(d, 30.0, conOferta = false)),
        )
        assertEquals(4, r.cotizaciones)
        assertEquals(3, r.conOferta)
        assertEquals(12.0, r.tpcMin!!, 0.001)
        assertEquals(1, r.conTiempo)
        assertEquals(2, r.sinTiempo)
    }

    // ---------------- NEC ----------------

    @Test fun nec_ofertasCompletasEntreOfertasEvaluadas() {
        val a = pedido(listOf(item("1"))); val b = pedido(listOf(item("1"))); val c = pedido(listOf(item("1")))
        val sinGarantia = TerminosCondiciones.porDefecto().filter { it.titulo != "GARANTÍA" }
        val textoVacio = TerminosCondiciones.porDefecto().map { if (it.titulo == "ENTREGA") it.copy(texto = " ") else it }
        val r = IndicadoresBi.calcular(listOf(cot(a, 5.0), cot(b, 5.0, clausulas = sinGarantia), cot(c, 5.0, clausulas = textoVacio)))
        assertEquals(1, r.completas)
        assertEquals(3, r.conOferta)
        assertEquals(1.0 / 3.0, r.nec!!, 1e-9)
        assertEquals(2, r.incompletas.size)
    }

    @Test fun nec_indicaPorQueUnaOfertaNoCumple() {
        val p = pedido(listOf(item("1")))
        val sinGarantia = TerminosCondiciones.porDefecto().filter { it.titulo != "GARANTÍA" }
        val vacia = TerminosCondiciones.porDefecto().map { if (it.titulo == "ENTREGA") it.copy(texto = "") else it }
        assertEquals(listOf("falta la cláusula GARANTÍA del Anexo A"), IndicadoresBi.motivosIncompleta(oferta(p, sinGarantia)))
        assertEquals(listOf("la cláusula ENTREGA está vacía"), IndicadoresBi.motivosIncompleta(oferta(p, vacia)))
        assertEquals(listOf("sin número de cotización"), IndicadoresBi.motivosIncompleta(oferta(p, numero = "")))
        assertTrue(IndicadoresBi.esOfertaCompleta(oferta(p)))
        assertFalse(IndicadoresBi.esOfertaCompleta(oferta(p, numero = "")))
    }

    // ---------------- PDM ----------------

    @Test fun pdm_coincideConLaGuiaDeCorte() {
        val items = listOf(item("1", 1), item("4", 2, 1200, 1100))
        val plan = OptimizadorCorte.planificar(items)
        val r = IndicadoresBi.calcular(listOf(cot(pedido(items), 5.0)))
        assertEquals(plan.desperdicioPct, r.pdmPct!!, 1e-9)
        assertEquals(plan.grupos.sumOf { it.largoCompradoMm }, r.compradoMm)
        assertTrue(r.pdmPct!! in 0.0..100.0)
    }

    @Test fun pdm_contandoLosRetazosNuncaEsMenorYCoincideConElAprovechamiento() {
        val items = listOf(item("1", 1), item("4", 2, 1200, 1100))
        val plan = OptimizadorCorte.planificar(items)
        val r = IndicadoresBi.calcular(listOf(cot(pedido(items), 5.0)))
        assertTrue(r.pdmConRetazosPct!! >= r.pdmPct!! - 1e-9)
        assertEquals(100.0 - plan.aprovechamientoPct, r.pdmConRetazosPct!!, 1e-9)
        assertEquals(plan.grupos.sumOf { g -> g.retazos.sumOf { it.toLong() } }, r.retazosMm)
    }

    @Test fun pdm_conUnModeloSoloUsaLasVentanasDeEseModelo() {
        val corredera = item("1", 1); val batiente = item("4", 2, 1200, 1100)
        val r = IndicadoresBi.calcular(listOf(cot(pedido(listOf(corredera, batiente)), 5.0)), modelo = Modelo.CORREDERA)
        assertEquals(OptimizadorCorte.planificar(listOf(corredera)).desperdicioPct, r.pdmPct!!, 1e-9)
    }

    @Test fun pdm_conCacheDaElMismoResultado() {
        val items = listOf(item("1", 1), item("4", 2, 1200, 1100))
        val lista = listOf(cot(pedido(items), 5.0))
        val cache = HashMap<String, OptimizadorCorte.Plan>()
        val a = IndicadoresBi.calcular(lista, cache = cache)
        val b = IndicadoresBi.calcular(lista, cache = cache)
        assertEquals(a.pdmPct, b.pdmPct)
        assertTrue(cache.isNotEmpty())
    }

    // ---------------- Filtros (RF14) ----------------

    @Test fun filtro_porPeriodo() {
        val a = pedido(listOf(item("1")), creado = 1_000L)
        val b = pedido(listOf(item("1")), creado = 2_000L)
        val c = pedido(listOf(item("1")), creado = 3_000L)
        val todas = listOf(cot(a, 1.0), cot(b, 1.0), cot(c, 1.0))
        assertEquals(3, IndicadoresBi.calcular(todas).cotizaciones)
        assertEquals(1, IndicadoresBi.calcular(todas, desde = 1_500L, hasta = 2_500L).cotizaciones)
        assertEquals(2, IndicadoresBi.calcular(todas, desde = 2_000L).cotizaciones)   // los extremos se incluyen
    }

    @Test fun filtro_porModelo() {
        val a = pedido(listOf(item("1"))); val b = pedido(listOf(item("4"))); val c = pedido(listOf(item("1"), item("4")))
        val todas = listOf(cot(a, 1.0), cot(b, 1.0), cot(c, 1.0))
        assertEquals(2, IndicadoresBi.calcular(todas, modelo = Modelo.CORREDERA).cotizaciones)
        assertEquals(2, IndicadoresBi.calcular(todas, modelo = Modelo.BATIENTE).cotizaciones)
        assertEquals(0, IndicadoresBi.calcular(todas, modelo = Modelo.PARALELA).cotizaciones)
    }

    @Test fun porModelo_soloListaLosQueTienenCotizaciones() {
        val todas = listOf(cot(pedido(listOf(item("1"))), 1.0), cot(pedido(listOf(item("7"))), 1.0))
        assertEquals(listOf(Modelo.CORREDERA, Modelo.OSCILOBATIENTE), IndicadoresBi.porModelo(todas).map { it.modelo })
    }

    // ---------------- Evolución mensual ----------------

    private fun fecha(anio: Int, mes: Int, dia: Int = 10): Long =
        Calendar.getInstance().apply { clear(); set(anio, mes, dia, 12, 0, 0) }.timeInMillis

    @Test fun porMes_agrupaPorMesDeCreacionYOrdenaDelMasAntiguoAlReciente() {
        val ene1 = pedido(listOf(item("1")), fecha(2026, Calendar.JANUARY)); val ene2 = pedido(listOf(item("1")), fecha(2026, Calendar.JANUARY, 20))
        val mar = pedido(listOf(item("1")), fecha(2026, Calendar.MARCH))
        val meses = IndicadoresBi.porMes(listOf(cot(mar, 30.0), cot(ene1, 10.0), cot(ene2, 20.0)))
        assertEquals(listOf("ene 2026", "mar 2026"), meses.map { it.etiqueta })
        assertEquals(2, meses[0].resultado.cotizaciones)
        assertEquals(15.0, meses[0].resultado.tpcMin!!, 0.001)
        assertEquals(30.0, meses[1].resultado.tpcMin!!, 0.001)
    }

    @Test fun porMes_soloLosUltimosMeses() {
        val lista = (0 until 8).map { cot(pedido(listOf(item("1")), fecha(2026, it)), 5.0) }
        assertEquals(6, IndicadoresBi.porMes(lista).size)
        assertEquals("mar 2026", IndicadoresBi.porMes(lista).first().etiqueta)
    }

    // ---------------- Lectura para decidir ----------------

    @Test fun lectura_avisaElModeloQueMasTarda() {
        val lista = listOf(
            cot(pedido(listOf(item("1"))), 10.0), cot(pedido(listOf(item("1"))), 12.0),
            cot(pedido(listOf(item("2"))), 60.0), cot(pedido(listOf(item("2"))), 70.0),
        )
        val r = IndicadoresBi.calcular(lista)
        val frases = IndicadoresBi.lecturas(r, IndicadoresBi.porModelo(lista), IndicadoresBi.porMes(lista))
        assertTrue(frases.any { it.startsWith("Paralela es el modelo que más tarda") })
    }

    @Test fun lectura_avisaLaTendenciaDelTiempo() {
        val lista = listOf(
            cot(pedido(listOf(item("1")), fecha(2026, Calendar.JANUARY)), 40.0),
            cot(pedido(listOf(item("1")), fecha(2026, Calendar.FEBRUARY)), 20.0),
        )
        val r = IndicadoresBi.calcular(lista)
        val frases = IndicadoresBi.lecturas(r, IndicadoresBi.porModelo(lista), IndicadoresBi.porMes(lista))
        assertTrue(frases.any { it.startsWith("El tiempo por cotización bajó 50.0 %") })
    }

    @Test fun lectura_avisaLasOfertasIncompletas() {
        val sinGarantia = TerminosCondiciones.porDefecto().filter { it.titulo != "GARANTÍA" }
        val lista = listOf(cot(pedido(listOf(item("1"))), 5.0, clausulas = sinGarantia), cot(pedido(listOf(item("1"))), 5.0))
        val r = IndicadoresBi.calcular(lista)
        val frases = IndicadoresBi.lecturas(r, IndicadoresBi.porModelo(lista), IndicadoresBi.porMes(lista))
        assertTrue(frases.any { it.startsWith("1 de 2 ofertas no salieron completas") })
    }

    @Test fun lectura_sinTiemposExplicaQueSeMideConLasNuevas() {
        val lista = listOf(cot(pedido(listOf(item("1"))), null))
        val r = IndicadoresBi.calcular(lista)
        val frases = IndicadoresBi.lecturas(r, IndicadoresBi.porModelo(lista), IndicadoresBi.porMes(lista))
        assertTrue(frases.any { it.contains("cotizaciones nuevas") })
    }

    // ---------------- Sin datos ----------------

    @Test fun sinCotizaciones_losIndicadoresQuedanVacios() {
        val r = IndicadoresBi.calcular(emptyList())
        assertEquals(0, r.cotizaciones)
        assertNull(r.tpcMin); assertNull(r.nec); assertNull(r.pdmPct)
        assertEquals(listOf("Todavía no hay cotizaciones en este filtro."), IndicadoresBi.lecturas(r, emptyList(), emptyList()))
    }

    @Test fun pedidoSinOferta_noEntraEnTpcNiNecPeroSiEnPdm() {
        val r = IndicadoresBi.calcular(listOf(cot(pedido(listOf(item("1"))), conOferta = false)))
        assertNull(r.tpcMin); assertNull(r.nec)
        assertTrue(r.pdmPct != null)
    }
}
