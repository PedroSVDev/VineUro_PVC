package com.example.vineuro_pvc

import com.example.vineuro_pvc.data.AperturasMulti
import com.example.vineuro_pvc.data.CalculadoraPrecios
import com.example.vineuro_pvc.data.OrderItem
import com.example.vineuro_pvc.data.WindowConfig
import com.example.vineuro_pvc.data.WindowConfig.LayoutType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Impostas interiores en aperturas de varias hojas (regla de pk_r2.z y pk_l4/pk_v4 de la original). */
class ImpostasMultiHojaTest {
    private val D = 1e-6
    private fun item(ancho: Int, alto: Int) =
        OrderItem.create(0, WindowConfig(id = "t", nombre = "t", categoria = "t", layoutType = LayoutType.GRID, cols = 1, rows = 1), alto, ancho)

    @Test fun reglaDeImpostasPorApertura() {
        // normales: todas
        for (i in 0..4) assertTrue(AperturasMulti.impostaPermitida("4", i))
        // corredizas y francesas: solo horizontales (1, 2)
        for (m in listOf("S:1R_2L", "F:LCF_RC")) {
            assertTrue(AperturasMulti.impostaPermitida(m, 1)); assertTrue(AperturasMulti.impostaPermitida(m, 2))
            assertTrue(!AperturasMulti.impostaPermitida(m, 3)); assertTrue(!AperturasMulti.impostaPermitida(m, 4))
        }
        // corredizas verticales: solo verticales (3, 4)
        assertTrue(!AperturasMulti.impostaPermitida("V:1X_2U", 1)); assertTrue(AperturasMulti.impostaPermitida("V:1X_2U", 3))
    }

    @Test fun corredizaConImpostaHorizontalPartePorHoja() {
        val it = item(1500, 1800)
        it.paneMechanisms[0] = "S:1R_2L"
        it.paneCols[0] = 11                          // imposta horizontal x1
        val v = CalculadoraPrecios.piezasVidrio(it)
        assertEquals(4, v.size)                      // 2 hojas x 2 vidrios
        val q = CalculadoraPrecios.cantidades(it)
        assertEquals(2, q.impostaN)                  // una imposta por hoja
        // largo de cada imposta = ancho del vidrio de la hoja (609 mm)
        assertEquals(2 * 0.609, q.impostaM, D)
        // por hoja: vidrio alto 1580 = sup + 52 + inf
        assertEquals(1580.0, (v[0].altoMm + v[1].altoMm + 52).toDouble(), 1.0)
    }

    @Test fun impostaNoPermitidaSeIgnora() {
        val it = item(1500, 1800)
        it.paneMechanisms[0] = "S:1R_2L"
        it.paneCols[0] = 13                          // vertical: no la admite una corrediza
        assertEquals(2, CalculadoraPrecios.piezasVidrio(it).size)
        assertEquals(0, CalculadoraPrecios.cantidades(it).impostaN)
    }

    @Test fun corredizaVerticalConImpostaVertical() {
        val it = item(1500, 1800)
        it.paneMechanisms[0] = "V:1X_2U"
        it.paneCols[0] = 13
        val v = CalculadoraPrecios.piezasVidrio(it)
        assertEquals(4, v.size)
        assertEquals(2, CalculadoraPrecios.cantidades(it).impostaN)
    }

    @Test fun francesaConImpostaHorizontalX2() {
        val it = item(1500, 2100)
        it.paneMechanisms[0] = "F:LCF_RC"
        it.paneCols[0] = 12                          // 3 vidrios por hoja
        assertEquals(6, CalculadoraPrecios.piezasVidrio(it).size)
        // 2 hojas x 2 impostas + la imposta central de la francesa
        val cortes = CalculadoraPrecios.piezasCorte(it).count { c -> c.perfil == "Imposta" && c.etiqueta == "imposta interior" }
        assertEquals(4, cortes)
    }

    @Test fun sandwichPorVidrioSeAplicaIgualEnCadaHoja() {
        val it = item(1500, 1800)
        it.paneMechanisms[0] = "S:1R_2L"
        it.paneCols[0] = 12
        it.paneSandwich[0] = true
        it.paneSandwichSub[0] = 2                    // solo el vidrio del medio, en cada hoja
        val v = CalculadoraPrecios.piezasVidrio(it)
        assertEquals(listOf(false, true, false, false, true, false), v.map { it.sandwich })
    }
}
