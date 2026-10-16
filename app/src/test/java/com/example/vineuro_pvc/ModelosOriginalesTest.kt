package com.example.vineuro_pvc

import com.example.vineuro_pvc.data.ModelosOriginales
import com.example.vineuro_pvc.data.OrderItem
import com.example.vineuro_pvc.data.WindowConfigCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los 63 modelos del selector de la original, con la estructura decodificada de su fórmula (pk_i2). */
class ModelosOriginalesTest {
    private val D = 1e-6

    private fun plano(id: String): ModelosOriginales.Plano {
        val m = ModelosOriginales.buscar(id)!!
        val (aw, ah) = ModelosOriginales.seccionesPorDefecto(m, m.anchoMm.toFloat(), m.altoMm.toFloat())
        return ModelosOriginales.plano(m, m.anchoMm.toDouble(), m.altoMm.toDouble(), aw, ah)
    }

    @Test
    fun hay63ModelosUnicosYSuCatalogo() {
        assertEquals(63, ModelosOriginales.TODOS.size)
        assertEquals(63, ModelosOriginales.TODOS.map { it.id }.toSet().size)
        assertEquals(63, WindowConfigCatalog.originales().size)
        // las configuraciones antiguas siguen resolviéndose por su id para abrir pedidos guardados
        assertTrue(WindowConfigCatalog.obtenerTodas().size > 63)
    }

    @Test
    fun cantidadDePanosDeAlgunosModelos() {
        assertEquals(1, plano("A").celdas.size)
        assertEquals(2, plano("werAB").celdas.size)
        assertEquals(7, plano("werABCDEFG").celdas.size)
        assertEquals(4, plano("horABCD").celdas.size)
        assertEquals(4, plano("werABC_Aab").celdas.size)          // A dividida en 2 + B + C
        assertEquals(9, plano("horABC_Babcdefg").celdas.size)     // banda + 7 columnas + banda
        assertEquals(2, plano("oQ").celdas.size)
        assertEquals(3, plano("oQo").celdas.size)
    }

    @Test
    fun losPanosNoSeSolapanYSumanElAreaEsperada() {
        for (m in ModelosOriginales.TODOS) {
            val c = plano(m.id).celdas
            for (i in c.indices) for (j in i + 1 until c.size) {
                val w = minOf(c[i].x1, c[j].x1) - maxOf(c[i].x0, c[j].x0)
                val h = minOf(c[i].y1, c[j].y1) - maxOf(c[i].y0, c[j].y0)
                assertTrue("${m.id}: paños $i y $j se solapan", w <= 1e-6 || h <= 1e-6)
            }
            val total = c.sumOf { it.ancho * it.alto }
            if (!m.formula.startsWith("block_")) assertEquals("${m.id}: área", m.anchoMm.toDouble() * m.altoMm, total, 1e-3)
        }
    }

    @Test
    fun pesosDeLaFormula() {
        // werAB_Aab(2): la columna A se divide en 1 : 2 -> 500 mm y 1000 mm de 1500
        val c = plano("werAB_Aab").celdas.filter { it.x0 < 1.0 }
        assertEquals(2, c.size)
        assertEquals(500.0, c[0].alto, D)
        assertEquals(1000.0, c[1].alto, D)
        // horA(2)B_Aab: filas 2 : 1 de 1500 -> 1000 / 500
        val h = plano("horAB_Aab").celdas
        assertEquals(1000.0, h[0].alto, D)
        assertEquals(500.0, h.last().alto, D)
    }

    @Test
    fun bloqueOQ_ventanaEnL() {
        val c = plano("oQ").celdas              // o(8, 13:9) Q(9) de 1700 x 2200
        assertEquals(800.0, c[0].ancho, D)
        assertEquals(2200.0 * 13.0 / 22.0, c[0].alto, 1e-6)   // la "o" ocupa 13/(13+9) de la altura
        assertEquals(900.0, c[1].ancho, D)
        assertEquals(2200.0, c[1].alto, D)
        // cada bloque es un marco propio: la unión entre bloques son dos marcos (perfil de junta), no una imposta
        assertTrue(c[0].izq && c[0].arr && c[0].aba && c[0].der)
        assertTrue(c[1].izq && c[1].der && c[1].arr && c[1].aba)
        val pl = plano("oQ")
        assertEquals(2, pl.bases.size)
        assertTrue(pl.divisiones.isEmpty())
    }

    @Test
    fun areaRealDeLasVentanasEnL() {
        val cfg = WindowConfigCatalog.originales().first { it.modelo == "oQ" }
        val it = OrderItem.create(1, cfg)
        assertEquals(1700, it.anchoTotalMm); assertEquals(2200, it.altoTotalMm)    // medidas iniciales de la original
        val esperado = (800.0 * 2200.0 * 13.0 / 22.0 + 900.0 * 2200.0) / 1e6
        assertEquals(esperado, it.areaM2, 1e-6)
    }

    @Test
    fun seccionesEditablesPorModelo() {
        val a = OrderItem.create(1, WindowConfigCatalog.originales().first { it.modelo == "werABC" })
        assertEquals(3, a.sectionWidths.size); assertEquals(1, a.sectionHeights.size)
        assertEquals(1800.0, a.sectionWidths.sumOf { it.toDouble() }, 1e-3)
        val b = OrderItem.create(1, WindowConfigCatalog.originales().first { it.modelo == "horAB_Aab" })
        assertEquals(1, b.sectionWidths.size); assertEquals(2, b.sectionHeights.size)
        assertEquals(1000.0, b.sectionHeights[0].toDouble(), 1e-3)
        val o = OrderItem.create(1, WindowConfigCatalog.originales().first { it.modelo == "oQo" })
        assertEquals(3, o.sectionWidths.size)
        assertEquals(listOf(600.0, 800.0, 600.0), o.sectionWidths.map { it.toDouble() })
    }
}
