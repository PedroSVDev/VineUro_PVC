package com.example.vineuro_pvc

import com.example.vineuro_pvc.data.Precios
import com.example.vineuro_pvc.data.PreciosJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los precios del gerente: forma correcta de cada tabla y valores clave tomados de sus capturas. */
class PreciosGerenteTest {

    private val p = Precios.delGerente()

    @Test fun moneda_dolar_eImpuesto18() {
        assertEquals("US$", p.moneda.first()[0]); assertEquals("1.00", p.moneda.first()[1])
        assertEquals("18.00", p.impuesto.first()[1])
    }

    @Test fun tablas_tienenElNumeroDeColumnasQueLaCalculadoraEspera() {
        assertEquals(30, p.ventanas.size)
        assertTrue(p.ventanas.all { it.size == Precios.COLUMNAS_SISTEMA + 1 })
        assertTrue(p.cristal.all { it.size == 10 })
        assertTrue(p.umbralExt.all { it.size == 3 } && p.umbralInt.all { it.size == 3 })
        assertTrue(p.muebles.all { it.size == 4 })
        assertTrue(p.mosquitera.all { it.size == 7 })
        assertTrue(p.laminacion.all { it.size == 2 })
        assertTrue(p.planes.all { it.size == 4 })
    }

    @Test fun nombres_sinRepetidos() {
        for (t in listOf(p.ventanas, p.cristal, p.muebles, p.mosquitera, p.laminacion, p.planes)) {
            val n = t.map { it[0].trim() }
            assertEquals(n.size, n.distinct().size)
        }
        assertEquals(p.colores.size, p.colores.map { it.nombre.trim().lowercase() }.distinct().size)
    }

    @Test fun todosLosNumerosSonValidos() {
        val tablas = listOf(p.ventanas, p.cristal, p.umbralExt, p.umbralInt, p.muebles, p.mosquitera, p.laminacion, p.planes)
        for (t in tablas) for (f in t) f.drop(1).forEach { assertTrue("$f", it.toDoubleOrNull() != null) }
        (p.extras + p.empleos).forEach { assertTrue(it.precio.toDoubleOrNull() != null) }
    }

    @Test fun valoresClaveDeLasCapturas() {
        assertEquals(listOf("Batiente Exterior blanco", "25.43", "25.42", "33.90"), p.ventanas.first().take(4))
        assertEquals(listOf("Oscilobatiente, fijo _ blanco", "21.18", "25.42", "25.42"), p.ventanas.first { it[0].startsWith("Oscilobatiente, fijo") }.take(4))
        assertEquals("140.00", p.cristal.first { it[0] == "Termopanel TVH 32mm" }[1])
        assertEquals(listOf("Descuento especial 12%", "12.00", "12.00"), p.planes.first { it[0].startsWith("Descuento especial") }.take(3))
        assertEquals(3, p.empleos.first { it.nombre == "finishing slopes" }.unidad)
        assertEquals(listOf("Antracita", "Blanco", "Negro", "Nogal", "Roble Dorado", "Silver"), p.colores.take(6).map { it.nombre })
    }

    @Test fun conservaLasOpcionesQueUsanLasPruebasDelAsesor() {
        assertTrue(p.cristal.any { it[0] == "common glass 4-6-4" } && p.cristal.any { it[0] == "common glass 4-6-4-6-4" })
    }

    @Test fun ida_yVuelta_aJson() {
        val j = PreciosJson.fromJson(PreciosJson.toJson(p))
        assertEquals(p.ventanas, j.ventanas); assertEquals(p.empleos, j.empleos); assertEquals(p.colores, j.colores)
    }
}
