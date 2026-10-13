package com.example.vineuro_pvc

import com.example.vineuro_pvc.data.CalculadoraPrecios
import com.example.vineuro_pvc.data.ListadoMateriales
import com.example.vineuro_pvc.data.OptimizadorCorte
import com.example.vineuro_pvc.data.OptimizadorCorte.Parametros
import com.example.vineuro_pvc.data.OptimizadorCorte.PiezaAsignada
import com.example.vineuro_pvc.data.OrderItem
import com.example.vineuro_pvc.data.WindowConfig
import com.example.vineuro_pvc.data.WindowConfig.LayoutType
import org.junit.Assert.assertEquals
import org.junit.Test

/** Guía de corte: piezas por ventana (medidas verificables a mano) y reparto en varillas. */
class OptimizadorCorteTest {

    private fun item(ancho: Int, alto: Int, cols: Int = 1, indice: Int = 1): OrderItem {
        val cfg = WindowConfig(id = "t", nombre = "t", categoria = "t", layoutType = LayoutType.GRID, cols = cols, rows = 1)
        return OrderItem.create(indice, cfg, alto, ancho)
    }

    private fun pz(l: Int, v: Int = 1) = PiezaAsignada(v, 1, "p", l)

    private fun largos(item: OrderItem, perfil: String) =
        CalculadoraPrecios.piezasCorte(item).filter { it.perfil == perfil }.map { it.largoMm }.sorted()

    // ---------------- Piezas ----------------

    @Test fun ventanaFija_marcoYJunquillo() {
        val it = item(1500, 1800)
        assertEquals(listOf(1500, 1500, 1800, 1800), largos(it, "Marco"))
        // vidrio fijo = hueco = medida - 2 x 48 mm
        assertEquals(listOf(1404, 1404, 1704, 1704), largos(it, "Junquillo"))
        assertEquals(emptyList<Int>(), largos(it, "Imposta"))
        assertEquals(emptyList<Int>(), largos(it, "Hoja"))
    }

    @Test fun dosColumnas_unaImpostaDeLargoLibre() {
        val it = item(1500, 1800, cols = 2)
        assertEquals(listOf(1800 - 96), largos(it, "Imposta"))            // entre marco superior e inferior
    }

    @Test fun hojaAbatible_montaSobreElMarco() {
        val it = item(1500, 1800)
        it.paneMechanisms[0] = "4"
        assertEquals(listOf(1430, 1430, 1730, 1730), largos(it, "Hoja"))   // hueco + 13 mm por lado
        // vidrio de hoja = hueco - 2 x (62 - 13)
        assertEquals(listOf(1306, 1306, 1606, 1606), largos(it, "Junquillo"))
    }

    @Test fun soldadura_soloEnMarcoYHoja() {
        val it = item(1500, 1800, cols = 2)
        it.paneMechanisms[0] = "4"
        val p = CalculadoraPrecios.piezasCorte(it, soldaduraMm = 3)
        assertEquals(true, p.filter { it.perfil == "Marco" }.all { it.largoMm == 1503 || it.largoMm == 1803 })
        assertEquals(listOf(1704), p.filter { it.perfil == "Imposta" }.map { it.largoMm })   // imposta sin soldadura
    }

    // ---------------- Reparto en varillas ----------------

    @Test fun repartoExacto_sinSeparacion() {
        val g = OptimizadorCorte.empaquetar("S", "Marco", "c", listOf(3500, 2500, 3000, 3000, 2000, 1000).map { pz(it) }, Parametros(separacionMm = 0))
        assertEquals(3, g.varillas.size)                                   // 15 000 mm = 2.5 varillas -> 3, y todas llenas
        assertEquals(15000L, g.largoPiezasMm)
        assertEquals(3, g.minimoTeorico)
    }

    @Test fun separacionDeCorte_seDescuenta() {
        val piezas = listOf(4000, 2000).map { pz(it) }
        assertEquals(1, OptimizadorCorte.empaquetar("S", "M", "c", piezas, Parametros(separacionMm = 0)).varillas.size)
        assertEquals(2, OptimizadorCorte.empaquetar("S", "M", "c", piezas, Parametros(separacionMm = 5)).varillas.size)
        // 4000 + 5 + 1995 = 6000 justo
        val justo = listOf(4000, 1995).map { pz(it) }
        assertEquals(1, OptimizadorCorte.empaquetar("S", "M", "c", justo, Parametros(separacionMm = 5)).varillas.size)
    }

    @Test fun piezaMasLargaQueLaVarilla_seReporta() {
        val g = OptimizadorCorte.empaquetar("S", "M", "c", listOf(6500, 1000).map { pz(it) }, Parametros())
        assertEquals(1, g.sinCabida.size)
        assertEquals(1, g.varillas.size)
    }

    @Test fun ningunaVarillaSePasa_yNingunaPiezaSePierde() {
        val rnd = java.util.Random(7)
        val piezas = List(80) { pz(300 + rnd.nextInt(2400), it % 5 + 1) }
        val p = Parametros()
        val g = OptimizadorCorte.empaquetar("S", "M", "c", piezas, p)
        assertEquals(80, g.piezasTotal)
        g.varillas.forEach { assertEquals(true, it.usadoMm <= p.largoVarillaMm) }
        assertEquals(true, g.varillas.size >= g.minimoTeorico)
        assertEquals(true, g.varillas.size <= g.minimoTeorico + 1)         // muy cerca del óptimo
    }

    @Test fun resultadoDeterminista() {
        val piezas = List(40) { pz(500 + (it * 137) % 2000, it % 4 + 1) }
        val a = OptimizadorCorte.empaquetar("S", "M", "c", piezas, Parametros())
        val b = OptimizadorCorte.empaquetar("S", "M", "c", piezas, Parametros())
        assertEquals(a.varillas.map { v -> v.piezas.map { it.largoMm } }, b.varillas.map { v -> v.piezas.map { it.largoMm } })
    }

    @Test fun retazoUtil_noEsDesperdicio() {
        // una sola varilla con 2000 de piezas: sobran ~4000 (reutilizable) -> desperdicio 0
        val g = OptimizadorCorte.empaquetar("S", "M", "c", listOf(pz(2000)), Parametros(retazoUtilMm = 500))
        assertEquals(0L, g.desperdicioMm)
        assertEquals(listOf(4000), g.retazos)
        // con retazo útil > sobrante todo el sobrante cuenta como desperdicio
        val g2 = OptimizadorCorte.empaquetar("S", "M", "c", listOf(pz(5800)), Parametros(retazoUtilMm = 500))
        assertEquals(200L, g2.desperdicioMm)
    }

    // ---------------- Pedido completo ----------------

    @Test fun planificar_agrupaPorPerfilSistemaYColor_yRepiteSegunCantidad() {
        val a = item(1500, 1800, indice = 1); a.opciones.cantidad = 2; a.opciones.sistemaVentana = "S1"; a.opciones.color = "blanco"
        val b = item(1000, 1000, indice = 2); b.opciones.sistemaVentana = "S1"; b.opciones.color = "negro"
        val plan = OptimizadorCorte.planificar(listOf(a, b))
        val marcos = plan.grupos.filter { it.perfil == "Marco" }
        assertEquals(2, marcos.size)                                       // blanco y negro no se mezclan
        assertEquals(8, marcos.first { it.color == "blanco" }.piezasTotal) // 4 piezas x 2 unidades
        assertEquals(4, marcos.first { it.color == "negro" }.piezasTotal)
    }

    @Test fun listadoMateriales_vidriosYUnidades() {
        val a = item(1500, 1800); a.opciones.cantidad = 3; a.opciones.tipoCristal = "templado"
        val l = ListadoMateriales.generar(listOf(a))
        assertEquals(3, l.unidades)
        assertEquals(1, l.vidrios.size)
        assertEquals(3, l.vidrios[0].cantidad)
        assertEquals(1404, l.vidrios[0].anchoMm)
        assertEquals(1704, l.vidrios[0].altoMm)
    }
}
