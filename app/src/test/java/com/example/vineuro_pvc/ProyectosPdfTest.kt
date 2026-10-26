package com.example.vineuro_pvc

import com.example.vineuro_pvc.views.ProyectosPdf
import com.example.vineuro_pvc.views.ProyectosPdf.Elemento
import com.example.vineuro_pvc.views.ProyectosPdf.Modo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Paginación del PDF de proyectos: hojas necesarias, diseños por hoja y el pie siempre junto a los últimos diseños. */
class ProyectosPdfTest {

    private fun bloques(p: List<Elemento>) = p.filterIsInstance<Elemento.Bloque>()

    @Test
    fun soloProyectos_hasta6PorHoja_y_bloquesAnchosCon1o2() {
        assertEquals(1, ProyectosPdf.paginar(1, Modo.SOLO_PROYECTOS).size)
        assertTrue(bloques(ProyectosPdf.paginar(2, Modo.SOLO_PROYECTOS)[0]).all { !it.compacto })
        assertTrue(bloques(ProyectosPdf.paginar(3, Modo.SOLO_PROYECTOS)[0]).all { it.compacto })
        assertEquals(1, ProyectosPdf.paginar(6, Modo.SOLO_PROYECTOS).size)
        assertEquals(2, ProyectosPdf.paginar(7, Modo.SOLO_PROYECTOS).size)
        assertEquals(listOf(6, 6, 1), ProyectosPdf.paginar(13, Modo.SOLO_PROYECTOS).map { bloques(it).size })
    }

    @Test
    fun conDetalles_2PorHoja() {
        assertEquals(listOf(2, 2, 1), ProyectosPdf.paginar(5, Modo.CON_DETALLES).map { bloques(it).size })
        assertEquals(listOf(2, 2), ProyectosPdf.paginar(4, Modo.CON_DETALLES).map { bloques(it).size })
    }

    @Test
    fun pieSiempreEnLaUltimaHojaConDisenos_yNadaSeSale() {
        for (modo in Modo.values()) for (n in 0..20) {
            val paginas = ProyectosPdf.paginar(n, modo)
            // Encabezado solo en la primera; pie solo en la última
            assertTrue(paginas[0].any { it is Elemento.Encabezado })
            paginas.drop(1).forEach { p -> assertTrue(p.none { it is Elemento.Encabezado }) }
            assertTrue(paginas.last().any { it is Elemento.Pie })
            paginas.dropLast(1).forEach { p -> assertTrue(p.none { it is Elemento.Pie }) }
            // El pie nunca queda solo en una hoja: si hay diseños, la última hoja tiene al menos uno
            if (n > 0) assertTrue("modo=$modo n=$n", bloques(paginas.last()).isNotEmpty())
            // Todos los diseños aparecen una sola vez y en orden
            assertEquals((0 until n).toList(), paginas.flatMap { bloques(it) }.map { it.indice })
            // Nada se superpone ni se sale de la zona útil de la hoja
            paginas.forEach { p ->
                val cajas = p.map {
                    when (it) {
                        is Elemento.Encabezado -> it.caja
                        is Elemento.Bloque -> it.caja
                        is Elemento.Pie -> it.caja
                    }
                }
                cajas.forEach { c ->
                    assertTrue(c.l >= ProyectosPdf.M - 0.01f && c.r <= ProyectosPdf.W - ProyectosPdf.M + 0.01f)
                    assertTrue(c.t >= ProyectosPdf.M - 0.01f && c.b <= ProyectosPdf.ABAJO + 0.01f)
                    assertTrue("bloque demasiado chico: modo=$modo n=$n", c.alto >= 100f)
                }
                for (i in cajas.indices) for (j in i + 1 until cajas.size) {
                    val a = cajas[i]; val b = cajas[j]
                    val solapan = a.l < b.r - 0.01f && b.l < a.r - 0.01f && a.t < b.b - 0.01f && b.t < a.b - 0.01f
                    assertTrue("se superponen: modo=$modo n=$n", !solapan)
                }
            }
        }
    }

    @Test
    fun formatos() {
        assertEquals("1234.50", ProyectosPdf.num(1234.5))
        assertEquals("10", ProyectosPdf.pct(10.0))
        assertEquals("12.5", ProyectosPdf.pct(12.5))
    }
}
