package com.example.vineuro_pvc

import com.example.vineuro_pvc.data.AsesorDiseno
import com.example.vineuro_pvc.data.AsesorDiseno.Ambiente
import com.example.vineuro_pvc.data.AsesorDiseno.Prioridad
import com.example.vineuro_pvc.data.GeminiAsesor
import com.example.vineuro_pvc.data.GeminiAsesor.TipoFallo
import com.example.vineuro_pvc.data.Precios
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiAsesorTest {
    private val precios = Precios.porDefecto()
    private val necesidad = AsesorDiseno.Necesidad(Ambiente.DORMITORIO, true, 1500, 1500, Prioridad.SILENCIO)
    private val interior = AsesorDiseno.Necesidad(Ambiente.DORMITORIO, false, 1500, 1500, Prioridad.EQUILIBRADO)
    private val ahora = 1_760_000_000_000L

    private fun json(paneles: Int = 2, aperturas: String = "\"oscilobatiente_derecha\",\"fija\"", cristal: String = "common glass 4-6-4-6-4") =
        """{"paneles":$paneles,"aperturas":[$aperturas],"cristal":"$cristal","sistema":"Custom PVC system 1","mosquitero":"ninguno","panos_con_mosquitero":[],"razones":["Por el ruido de la calle"]}"""

    @Test
    fun respuestaCorrectaSeConvierteEnPropuestaDeIA() {
        val p = GeminiAsesor.parsear(json(), necesidad, precios)
        assertNotNull(p)
        assertEquals("ia", p!!.origen)
        assertEquals("werAB", p.modeloId)
        assertEquals("8", p.aperturas[0]); assertEquals("10", p.aperturas[1])
        assertEquals("common glass 4-6-4-6-4", p.cristal)
    }

    @Test
    fun respuestasFueraDeLoRazonableSeDescartan() {
        assertNull(GeminiAsesor.parsear("no es json", necesidad, precios))
        assertNull(GeminiAsesor.parsear(json(paneles = 4, aperturas = "\"fija\",\"fija\",\"fija\",\"fija\""), necesidad, precios))
        assertNull(GeminiAsesor.parsear(json(aperturas = "\"fija\",\"fija\""), necesidad, precios))            // nada se abre
        assertNull(GeminiAsesor.parsear(json(aperturas = "\"portal_magico\",\"fija\""), necesidad, precios))   // apertura inventada
        assertNull(GeminiAsesor.parsear(json(aperturas = "\"fija\""), necesidad, precios))                     // no coincide con paneles
    }

    @Test
    fun unCristalInventadoSeCorrigeConLaTablaDePrecios() {
        val p = GeminiAsesor.parsear(json(cristal = "cristal de diamante"), interior, precios)
        assertEquals("common glass 4-6-4", p!!.cristal)
    }

    @Test
    fun aceptaElJsonDentroDeUnBloqueDeCodigo() {
        assertNotNull(GeminiAsesor.parsear("```json\n" + json() + "\n```", necesidad, precios))
    }

    @Test
    fun extraeElTextoDeLaRespuestaDeGemini() {
        val cuerpo = """{"candidates":[{"content":{"parts":[{"text":"hola"},{"text":" mundo"}]}}]}"""
        assertEquals("hola mundo", GeminiAsesor.textoDeRespuesta(cuerpo))
        assertNull(GeminiAsesor.textoDeRespuesta("""{"candidates":[]}"""))
    }

    @Test
    fun cuotaDiariaIndicaCuandoSeRenueva() {
        val cuerpo = """{"error":{"code":429,"status":"RESOURCE_EXHAUSTED","details":[{"violations":[{"quotaId":"GenerateRequestsPerDayPerProjectPerModel-FreeTier"}]}]}}"""
        val f = GeminiAsesor.interpretarError(429, cuerpo, ahora)
        assertEquals(TipoFallo.CUOTA_DIA, f.tipo)
        assertTrue(f.bloquearHastaMs > ahora && f.bloquearHastaMs <= ahora + 25 * 3600_000L)
        assertTrue(f.mensaje.contains("hora de Lima"))
    }

    @Test
    fun cuotaPorMinutoUsaElTiempoQueIndicaGoogle() {
        val cuerpo = """{"error":{"code":429,"details":[{"violations":[{"quotaId":"GenerateRequestsPerMinutePerProjectPerModel-FreeTier"}]},{"retryDelay":"34s"}]}}"""
        val f = GeminiAsesor.interpretarError(429, cuerpo, ahora)
        assertEquals(TipoFallo.CUOTA_MINUTO, f.tipo)
        assertEquals(ahora + 34_000L, f.bloquearHastaMs)
        assertTrue(f.mensaje.contains("34 s"))
    }

    @Test
    fun otrosErroresTienenSuPropioMotivo() {
        assertEquals(TipoFallo.CLAVE_INVALIDA, GeminiAsesor.interpretarError(400, """{"error":{"message":"API key not valid. Please pass a valid API key."}}""", ahora).tipo)
        assertEquals(TipoFallo.CLAVE_INVALIDA, GeminiAsesor.interpretarError(403, "{}", ahora).tipo)
        assertEquals(TipoFallo.MODELO, GeminiAsesor.interpretarError(404, "{}", ahora, "x").tipo)
        assertEquals(TipoFallo.SATURADO, GeminiAsesor.interpretarError(503, "{}", ahora).tipo)
        assertEquals(TipoFallo.DESCONOCIDO, GeminiAsesor.interpretarError(400, """{"error":{"message":"invalid schema"}}""", ahora).tipo)
    }

    @Test
    fun elCuerpoSoloOfreceLoQueHayEnElCatalogo() {
        val cuerpo = GeminiAsesor.cuerpo(necesidad, precios).toString()
        assertTrue(cuerpo.contains("common glass 4-6-4-6-4"))
        assertTrue(cuerpo.contains("responseSchema"))
        assertTrue(!GeminiAsesor.cuerpo(necesidad, precios, false).toString().contains("responseSchema"))
        // no viaja ningún dato personal: solo ambiente, medidas y prioridad
        assertTrue(cuerpo.contains("Dormitorio") && cuerpo.contains("1500"))
    }

    @Test
    fun descartaRazonesQueNombranAperturasQueNoSeEligieron() {
        val corredizas = setOf("1", "3")
        assertTrue(!GeminiAsesor.coherente("Permite excelente ventilación gracias a sus dos paños practicables.", corredizas))
        assertTrue(GeminiAsesor.coherente("Las correderas ventilan sin ocupar espacio al abrir.", corredizas))
        assertTrue(!GeminiAsesor.coherente("La hoja oscilobatiente cierra mejor.", corredizas))
        assertTrue(GeminiAsesor.coherente("Da a la calle: conviene reducir el ruido.", corredizas))
    }

    @Test
    fun laPropuestaDeIAConservaElAvisoDeMedidas() {
        val bano = AsesorDiseno.Necesidad(Ambiente.BANO, true, 1500, 1500, Prioridad.VENTILACION)
        val texto = json(paneles = 2, aperturas = "\"corredera_derecha\",\"corredera_izquierda\"").replace("Por el ruido de la calle", "Las correderas ventilan")
        val p = GeminiAsesor.parsear(texto, bano, precios)
        assertNotNull(p)
        assertTrue(p!!.advertencias.isNotEmpty())
    }

    @Test
    fun laIAFiltraLasRazonesIncoherentesPeroConservaLasBuenas() {
        val texto = """{"paneles":2,"aperturas":["corredera_derecha","corredera_izquierda"],"cristal":"common glass 4-6-4","sistema":"Custom PVC system 1","mosquitero":"ninguno","panos_con_mosquitero":[],"razones":["Dos paños practicables ventilan mejor","Las correderas no ocupan espacio"]}"""
        val p = GeminiAsesor.parsear(texto, interior, precios)!!
        assertEquals(listOf("Las correderas no ocupan espacio"), p.razones)
    }

    @Test
    fun siLaIaPoneCorrederasEnUnaVentanaALaCalleElAsesorLasCorrige() {
        val texto = """{"paneles":2,"aperturas":["corredera_derecha","corredera_izquierda"],"cristal":"common glass 4-6-4","sistema":"Custom PVC system 1","mosquitero":"ninguno","panos_con_mosquitero":[],"razones":["Las correderas no ocupan espacio"]}"""
        val p = GeminiAsesor.parsear(texto, necesidad, precios)!!
        assertEquals("8", p.aperturas[0]); assertEquals("10", p.aperturas[1])
        assertEquals("common glass 4-6-4-6-4", p.cristal)
        assertTrue(p.razones.all { it.startsWith("Ajuste del asesor") })
    }

    private fun alt(et: String, ap: String, cristal: String, extra: String = "") =
        """{"etiqueta":"$et","paneles":2,"aperturas":$ap,"cristal":"$cristal","sistema":"Custom PVC system 1","mosquitero":"ninguno","panos_con_mosquitero":[],"razones":["Elegida para este ambiente"]$extra}"""

    @Test
    fun interpretaTresAlternativasOrdenadasYSinRepetidas() {
        val texto = """{"alternativas":[""" +
            alt("silencio", """["oscilobatiente_derecha","fija"]""", "common glass 4-6-4-6-4") + "," +
            alt("economica", """["corredera_derecha","corredera_izquierda"]""", "common glass 4-6-4") + "," +
            alt("equilibrada", """["corredera_derecha","corredera_izquierda"]""", "common glass 4-6-4") + "]}"
        val lista = GeminiAsesor.parsearAlternativas(texto, interior, precios)
        // la económica era igual a la recomendada: queda la recomendada, y la de silencio (otras aperturas) se conserva
        assertTrue(lista.map { it.etiqueta }.containsAll(listOf("equilibrada", "silencio")))
        assertTrue(lista.size <= 3)
        assertEquals(lista.size, lista.map { AsesorDiseno.firma(it) }.toSet().size)
        assertTrue(lista.all { it.textoCliente.isNotBlank() })
    }

    @Test
    fun sinAlternativasAceptaUnaSolaVentana() {
        assertEquals(1, GeminiAsesor.parsearAlternativas(json(), interior, precios).size)
        assertTrue(GeminiAsesor.parsearAlternativas("no es json", interior, precios).isEmpty())
    }

    @Test
    fun elTextoDelClienteConPreciosOPromesasSeReemplazaPorUnoVerificable() {
        val conPrecio = alt("equilibrada", """["corredera_derecha","corredera_izquierda"]""", "common glass 4-6-4",
            ""","texto_cliente":"Solo cuesta 120.50 dólares con garantía total" """)
        val p = GeminiAsesor.parsearAlternativas("""{"alternativas":[$conPrecio]}""", interior, precios).first { it.etiqueta == "equilibrada" }
        assertTrue(!p.textoCliente.contains("120") && !p.textoCliente.contains("garant"))
        assertTrue(p.textoCliente.contains("1500 × 1500"))
        val bueno = alt("equilibrada", """["corredera_derecha","corredera_izquierda"]""", "common glass 4-6-4",
            ""","texto_cliente":"Ventana con dos hojas corredizas que dejan entrar aire sin ocupar espacio." """)
        assertTrue(GeminiAsesor.parsearAlternativas("""{"alternativas":[$bueno]}""", interior, precios).first { it.etiqueta == "equilibrada" }.textoCliente.startsWith("Ventana con dos hojas"))
    }

    @Test
    fun elMensajeIncluyeElComentarioLimpioYElPresupuesto() {
        val n = interior.copy(comentario = "Tiene niños, mi correo a@b.com", presupuesto = 350.0)
        val m = GeminiAsesor.mensajeUsuario(n, precios, varias = true)
        assertTrue(m.contains("Tiene niños")); assertTrue(!m.contains("a@b.com"))
        assertTrue(m.contains("350.00")); assertTrue(m.contains("alternativas") && m.contains("ventilacion"))
        assertTrue(!GeminiAsesor.mensajeUsuario(interior, precios).contains("Lo que dijo el cliente"))
    }

    @Test
    fun laPeticionDeAjusteLlevaLaVentanaActualYLaFrase() {
        val actual = AsesorDiseno.proponerPorReglas(interior, precios)
        val cuerpo = GeminiAsesor.cuerpoAjuste(interior, precios, actual, "hazla más barata", true).toString()
        assertTrue(cuerpo.contains("Ventana actual")); assertTrue(cuerpo.contains("hazla más barata"))
        assertTrue(GeminiAsesor.cuerpoAlternativas(interior, precios, true).toString().contains("alternativas"))
        assertTrue(!GeminiAsesor.cuerpoAlternativas(interior, precios, false).toString().contains("responseSchema"))
    }

    @Test
    fun laEconomicaSiempreEsLaMasBarataDeLaTablaYNuncaCuestaMasQueLaRecomendada() {
        val rec = GeminiAsesor.parsear(alt("equilibrada", """["corredera_derecha","corredera_izquierda"]""", "common glass 4-6-4-6-4"), interior, precios)!!
        val eco = AsesorDiseno.abaratar(rec, interior, precios)
        val minimo = precios.ventanas.map { it[0] }.flatMap { si -> precios.cristal.map { it[0] }.map { cr -> AsesorDiseno.precioDe(eco.copy(sistema = si, cristal = cr), precios) } }.min()
        assertEquals(minimo, AsesorDiseno.precioDe(eco, precios), 0.001)
        assertTrue(AsesorDiseno.precioDe(eco, precios) <= AsesorDiseno.precioDe(rec, precios))
        assertNull(eco.mosquitero)
        assertEquals(1, eco.aperturas.values.count { it != "10" }) // solo una hoja se abre
        val lista = GeminiAsesor.parsearAlternativas("""{"alternativas":[""" + alt("equilibrada", """["corredera_derecha","corredera_izquierda"]""", "common glass 4-6-4-6-4") + "]}", interior, precios)
        val e = lista.firstOrNull { it.etiqueta == "economica" }
        if (e != null) assertTrue(AsesorDiseno.precioDe(e, precios) <= AsesorDiseno.precioDe(lista.first { it.etiqueta == "equilibrada" }, precios))
    }

    @Test
    fun siLasAlternativasSonCasiIgualesSoloQuedaLaRecomendadaOLasQueDeVerdadCambian() {
        val ap = """["oscilobatiente_derecha","fija"]"""
        val texto = """{"alternativas":[""" + alt("equilibrada", ap, "common glass 4-6-4-6-4") + "," +
            alt("silencio", ap, "common glass 4-6-4-6-4") + "]}"
        val lista = GeminiAsesor.parsearAlternativas(texto, necesidad, precios)
        val rec = lista.first { it.etiqueta == "equilibrada" }
        assertTrue(lista.none { it.etiqueta == "silencio" }) // misma ventana: no aporta
        lista.filter { it !== rec }.forEach { p ->
            val dif = Math.abs(AsesorDiseno.precioDe(p, precios) - AsesorDiseno.precioDe(rec, precios)) / AsesorDiseno.precioDe(rec, precios)
            assertTrue(p.aperturas != rec.aperturas || dif >= 0.10)
        }
    }
}
