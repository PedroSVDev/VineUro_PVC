package com.example.vineuro_pvc

import com.example.vineuro_pvc.data.AsesorDiseno
import com.example.vineuro_pvc.data.AsesorDiseno.Ambiente
import com.example.vineuro_pvc.data.AsesorDiseno.Necesidad
import com.example.vineuro_pvc.data.AsesorDiseno.Prioridad
import com.example.vineuro_pvc.data.Precios
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AsesorDisenoTest {
    private val precios = Precios.porDefecto()

    private fun nec(
        amb: Ambiente = Ambiente.SALA, calle: Boolean = false, ancho: Int = 1500, alto: Int = 1500,
        pri: Prioridad = Prioridad.EQUILIBRADO,
    ) = Necesidad(amb, calle, ancho, alto, pri)

    @Test
    fun elNumeroDePanosDependeDelAncho() {
        assertEquals("A", AsesorDiseno.proponerPorReglas(nec(ancho = 800), precios).modeloId)
        assertEquals("werAB", AsesorDiseno.proponerPorReglas(nec(ancho = 1500), precios).modeloId)
        assertEquals("werABC", AsesorDiseno.proponerPorReglas(nec(ancho = 2400), precios).modeloId)
        assertEquals("werABCD", AsesorDiseno.proponerPorReglas(nec(ancho = 3200), precios).modeloId)
    }

    @Test
    fun siDaALaCalleUsaElCristalConMasCapas() {
        val p = AsesorDiseno.proponerPorReglas(nec(calle = true), precios)
        assertEquals("common glass 4-6-4-6-4", p.cristal)
        val interior = AsesorDiseno.proponerPorReglas(nec(calle = false), precios)
        assertEquals("common glass 4-6-4", interior.cristal)
    }

    @Test
    fun conRuidoUnaHojaOscilobatienteYElRestoFijo() {
        val p = AsesorDiseno.proponerPorReglas(nec(calle = true, ancho = 1500), precios)
        assertEquals("8", p.aperturas[0]); assertEquals("10", p.aperturas[1])
    }

    @Test
    fun equilibradoUsaCorredizasEnLosExtremos() {
        val p = AsesorDiseno.proponerPorReglas(nec(ancho = 2400), precios)
        assertEquals("1", p.aperturas[0]); assertEquals("10", p.aperturas[1]); assertEquals("3", p.aperturas[2])
    }

    @Test
    fun banoUsaAbatibleSuperior() {
        assertEquals("9", AsesorDiseno.proponerPorReglas(nec(amb = Ambiente.BANO, ancho = 600, alto = 600), precios).aperturas[0])
    }

    @Test
    fun soloElige_loQueExisteEnLaTablaDePrecios() {
        val p = AsesorDiseno.proponerPorReglas(nec(calle = true, pri = Prioridad.VENTILACION, amb = Ambiente.DORMITORIO), precios)
        assertTrue(precios.cristal.any { it[0] == p.cristal })
        assertTrue(precios.ventanas.any { it[0] == p.sistema })
        p.mosquitero?.let { m -> assertTrue(precios.mosquitera.any { it[0] == m }) }
    }

    @Test
    fun validarDescartaModelosInventadosYCorrigeLoDemas() {
        val buena = AsesorDiseno.proponerPorReglas(nec(), precios)
        assertNull(AsesorDiseno.validar(buena.copy(modeloId = "ventana_magica"), precios))
        assertNull(AsesorDiseno.validar(buena.copy(anchoMm = 99999), precios))
        val v = AsesorDiseno.validar(buena.copy(cristal = "cristal de oro", aperturas = mapOf(0 to "99")), precios)
        assertNotNull(v)
        assertEquals("common glass 4-6-4", v!!.cristal)
        assertEquals("10", v.aperturas[0])
        assertEquals("10", v.aperturas[1])
    }

    @Test
    fun laPropuestaSeConvierteEnUnaVentanaDelPedido() {
        val p = AsesorDiseno.validar(AsesorDiseno.proponerPorReglas(nec(ancho = 1500, alto = 1200), precios), precios)!!
        val item = AsesorDiseno.aOrderItem(p, 3)
        assertEquals(3, item.index)
        assertEquals(1500, item.anchoTotalMm); assertEquals(1200, item.altoTotalMm)
        assertEquals(2, item.sectionWidths.size)
        assertEquals(p.cristal, item.opciones.tipoCristal)
        assertEquals(p.aperturas, item.paneMechanisms.toMap())
    }

    @Test
    fun medidasFueraDeRangoSeRechazan() {
        assertNotNull(AsesorDiseno.errorMedidas(100, 1500))
        assertNotNull(AsesorDiseno.errorMedidas(1500, 9000))
        assertNull(AsesorDiseno.errorMedidas(1500, 1500))
    }

    @Test
    fun unBanoGrandeGeneraAvisoYUnoPequenoNo() {
        val grande = nec(amb = Ambiente.BANO, ancho = 1500, alto = 1500, calle = true)
        assertNotNull(AsesorDiseno.advertenciaMedidas(grande))
        assertTrue(AsesorDiseno.proponerPorReglas(grande, precios).advertencias.isNotEmpty())
        val pequeno = nec(amb = Ambiente.BANO, ancho = 700, alto = 500, calle = true)
        assertNull(AsesorDiseno.advertenciaMedidas(pequeno))
        assertTrue(AsesorDiseno.proponerPorReglas(pequeno, conEsmerilado).advertencias.isEmpty())
    }

    @Test
    fun lasMedidasSugeridasDeCadaAmbienteSonHabituales() {
        for (a in Ambiente.values()) {
            val t = AsesorDiseno.tipicas(a)
            assertNull(AsesorDiseno.advertenciaMedidas(nec(amb = a, ancho = t.sugAncho, alto = t.sugAlto)))
        }
    }

    @Test
    fun banoPequenoUsaUnPanoAbatibleSuperior() {
        val p = AsesorDiseno.proponerPorReglas(nec(amb = Ambiente.BANO, ancho = 700, alto = 500, calle = true), precios)
        assertEquals("A", p.modeloId)
        assertEquals("9", p.aperturas[0])
    }

    private val conEsmerilado = precios.copy(cristal = precios.cristal + listOf(listOf("esmerilado 6") + precios.cristal[0].drop(1)))

    private fun propuestaIa(modelo: String, aperturas: Map<Int, String>, cristal: String?, razones: List<String>, mosq: Set<Int> = emptySet()) =
        AsesorDiseno.Propuesta(
            modeloId = modelo, anchoMm = 700, altoMm = 500, aperturas = aperturas, panosConMosquitero = mosq,
            sistema = precios.ventanas[0][0], cristal = cristal, mosquitero = if (mosq.isEmpty()) null else precios.mosquitera[0][0],
            razones = razones, origen = "ia",
        )

    @Test
    fun banoUsaCristalEsmeriladoSiExisteEnLaTabla() {
        val p = AsesorDiseno.proponerPorReglas(nec(amb = Ambiente.BANO, ancho = 700, alto = 500), conEsmerilado)
        assertEquals("esmerilado 6", p.cristal)
        assertTrue(p.advertencias.none { it.contains("esmerilado") })
    }

    @Test
    fun banoSinCristalDePrivacidadAvisaQueHayQueAgregarlo() {
        val p = AsesorDiseno.proponerPorReglas(nec(amb = Ambiente.BANO, ancho = 700, alto = 500), precios)
        assertTrue(p.advertencias.any { it.contains("esmerilado") })
    }

    @Test
    fun siLaIaPoneCorrederasEnUnBanoSeCorrige() {
        val ia = propuestaIa("werAB", mapOf(0 to "1", 1 to "3"), precios.cristal[0][0], listOf("Dos corredizas dan ventilación."), setOf(0, 1))
        val p = AsesorDiseno.ajustarLogica(ia, nec(amb = Ambiente.BANO, ancho = 700, alto = 500), conEsmerilado)
        assertEquals("9", p.aperturas[0]); assertEquals("10", p.aperturas[1])
        assertEquals("esmerilado 6", p.cristal)
        assertTrue(p.panosConMosquitero.isEmpty()); assertNull(p.mosquitero)
        assertTrue(p.razones.none { it.contains("corredi") && !it.startsWith("Ajuste") })
    }

    @Test
    fun siLaIaPoneCorrederasAUnaVentanaALaCalleSeCambiaPorOscilobatiente() {
        val ia = propuestaIa("werAB", mapOf(0 to "1", 1 to "3"), precios.cristal[0][0], listOf("Corredizas prácticas."), setOf(0))
        val p = AsesorDiseno.ajustarLogica(ia, nec(calle = true, ancho = 1500), precios)
        assertEquals("8", p.aperturas[0]); assertEquals("10", p.aperturas[1])
        assertEquals("common glass 4-6-4-6-4", p.cristal)
        assertTrue(p.panosConMosquitero.isEmpty())
    }

    @Test
    fun unaPropuestaCoherenteNoSeToca() {
        val ia = propuestaIa("werAB", mapOf(0 to "1", 1 to "3"), precios.cristal[0][0], listOf("Corredizas en los extremos."), setOf(0))
        val p = AsesorDiseno.ajustarLogica(ia, nec(ancho = 1500), precios)
        assertEquals(ia.aperturas, p.aperturas); assertEquals(ia.cristal, p.cristal); assertEquals(setOf(0), p.panosConMosquitero)
        assertEquals(listOf("Corredizas en los extremos."), p.razones)
    }

    @Test
    fun seQuitanLasPromesasQueLaAppNoPuedeComprobar() {
        val ia = propuestaIa("werAB", mapOf(0 to "1", 1 to "3"), precios.cristal[0][0],
            listOf("Excelente calidad-precio.", "Corredizas en los extremos.", "Garantía de 10 años."))
        val p = AsesorDiseno.ajustarLogica(ia, nec(ancho = 1500), precios)
        assertEquals(listOf("Corredizas en los extremos."), p.razones)
    }

    @Test
    fun elComentarioSeLimpiaDeCorreosYTelefonos() {
        val t = AsesorDiseno.limpiarComentario("Tiene niños, escribir a juan@correo.com o llamar al 987654321 por favor")
        assertTrue(!t.contains("@")); assertTrue(!t.contains("987654321")); assertTrue(t.contains("niños"))
        assertEquals(300, AsesorDiseno.limpiarComentario("a".repeat(500)).length)
    }

    @Test
    fun lasAlternativasSonDistintasYLaEconomicaNoCuestaMasQueLaRecomendada() {
        val lista = AsesorDiseno.proponerAlternativas(nec(amb = Ambiente.SALA, ancho = 2400, alto = 1500, pri = Prioridad.VENTILACION), precios)
        assertTrue(lista.size >= 2)
        assertEquals(lista.size, lista.map { AsesorDiseno.firma(it) }.toSet().size)
        assertTrue(lista.all { it.textoCliente.isNotBlank() })
        val eco = lista.first { it.etiqueta == "economica" }; val rec = lista.first { it.etiqueta == "equilibrada" }
        assertTrue(AsesorDiseno.precioDe(eco, precios) <= AsesorDiseno.precioDe(rec, precios))
        assertNull(eco.mosquitero)
    }

    @Test
    fun enUnBanoLaEconomicaConservaElCristalDePrivacidad() {
        val lista = AsesorDiseno.proponerAlternativas(nec(amb = Ambiente.BANO, ancho = 700, alto = 500), conEsmerilado)
        assertTrue(lista.all { it.cristal == "esmerilado 6" })
        assertTrue(lista.all { it.aperturas[0] == "9" })
    }

    @Test
    fun siSuperaElPresupuestoLoAvisa() {
        val n = nec(ancho = 2400, alto = 1500).copy(presupuesto = 1.0)
        val lista = AsesorDiseno.conPresupuesto(AsesorDiseno.proponerAlternativas(n, precios), n, precios)
        assertTrue(lista.all { p -> p.advertencias.any { it.contains("presupuesto") } })
        val sinTope = AsesorDiseno.conPresupuesto(AsesorDiseno.proponerAlternativas(nec(), precios), nec(), precios)
        assertTrue(sinTope.all { p -> p.advertencias.none { it.contains("presupuesto") } })
    }

    @Test
    fun elTextoParaElClienteSoloUsaDatosDeLaPropuesta() {
        val n = nec(amb = Ambiente.BANO, ancho = 700, alto = 500)
        val p = AsesorDiseno.proponerPorReglas(n, conEsmerilado)
        assertTrue(p.textoCliente.contains("700 × 500"))
        assertTrue(p.textoCliente.contains("abatible superior"))
        assertTrue(p.textoCliente.contains("no deja ver"))
    }

    @Test
    fun lasMedidasHabitualesSiempreSonMedidasPermitidas() {
        for (a in Ambiente.values()) {
            val t = AsesorDiseno.tipicas(a)
            assertNull(AsesorDiseno.errorMedidas(t.anchoMin, t.altoMin))
            assertNull(AsesorDiseno.errorMedidas(t.anchoMax, t.altoMax))
            assertTrue(AsesorDiseno.proponerAlternativas(nec(amb = a, ancho = t.anchoMin, alto = t.altoMin), precios).isNotEmpty())
        }
    }

    @Test
    fun enUnBanoCualquierHojaQueNoSeaAbatibleSuperiorSeCorrige() {
        val ia = propuestaIa("werAB", mapOf(0 to "6", 1 to "9"), precios.cristal[0][0], listOf("Dos hojas para ventilar."))
        val p = AsesorDiseno.ajustarLogica(ia, nec(amb = Ambiente.BANO, ancho = 1000, alto = 700), precios)
        assertEquals("9", p.aperturas[0]); assertEquals("10", p.aperturas[1])
    }

    @Test
    fun aUnaVentanaALaCalleLeOfreceMasVentilacionConDosHojasOscilobatientes() {
        val lista = AsesorDiseno.proponerAlternativas(nec(amb = Ambiente.SALA, calle = true, ancho = 1200, alto = 1500), precios)
        val rec = lista.first { it.etiqueta == "equilibrada" }
        val vent = lista.first { it.etiqueta == "ventilacion" }
        assertEquals("8", rec.aperturas[0]); assertEquals("10", rec.aperturas[1])
        assertEquals("8", vent.aperturas[0]); assertEquals("7", vent.aperturas[1])
        assertTrue(AsesorDiseno.precioDe(vent, precios) > AsesorDiseno.precioDe(rec, precios))
        assertTrue(lista.size <= 3)
        // todas se distinguen de la recomendada de verdad
        lista.filter { it !== rec }.forEach { p ->
            val dif = Math.abs(AsesorDiseno.precioDe(p, precios) - AsesorDiseno.precioDe(rec, precios)) / AsesorDiseno.precioDe(rec, precios)
            assertTrue(p.aperturas != rec.aperturas || dif >= 0.10)
        }
    }

    @Test
    fun unBanoNoOfreceMasHojasQueSeAbren() {
        val lista = AsesorDiseno.proponerAlternativas(nec(amb = Ambiente.BANO, ancho = 1000, alto = 700), conEsmerilado)
        assertTrue(lista.none { it.etiqueta == "ventilacion" })
        assertTrue(lista.all { p -> p.aperturas.values.all { it == "9" || it == "10" } })
    }
}
