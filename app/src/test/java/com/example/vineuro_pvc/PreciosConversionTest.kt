package com.example.vineuro_pvc

import com.example.vineuro_pvc.data.FilaSeleccion
import com.example.vineuro_pvc.data.Precios
import org.junit.Assert.assertEquals
import org.junit.Test

class PreciosConversionTest {

    private fun base(): Precios {
        val sis = MutableList(Precios.COLUMNAS_SISTEMA + 1) { "0" }
        sis[0] = "S"; sis[1] = "10.00"; sis[37] = "3.7"
        return Precios.porDefecto().copy(
            moneda = listOf(listOf("S/", "1.00", "S/")),
            planes = listOf(listOf("P", "10", "20", "10")),
            impuesto = listOf(listOf("IGV", "18")),
            ventanas = listOf(sis),
            cristal = listOf(listOf("V", "37", "0", "0", "0", "0", "0", "0", "0", "0")),
            extras = listOf(FilaSeleccion("E", "37", 2)),
            empleos = listOf(FilaSeleccion("J", "abc", 1)),
        )
    }

    @Test fun conviertePreciosPeroNoPorcentajes() {
        val c = base().convertirPrecios(0.27)
        assertEquals("2.70", c.ventanas[0][1])
        assertEquals("0.999", c.ventanas[0][37])                          // 3.7 x 0.27
        assertEquals("9.99", c.cristal[0][1])
        assertEquals("9.99", c.extras[0].precio)
        assertEquals(2, c.extras[0].unidad)                               // la unidad no cambia
        assertEquals("abc", c.empleos[0].precio)                          // texto no numérico queda igual
        assertEquals(listOf("P", "10", "20", "10"), c.planes[0])          // porcentajes intactos
        assertEquals(listOf("IGV", "18"), c.impuesto[0])
        assertEquals("S", c.ventanas[0][0])                               // nombres intactos
    }

    @Test fun idaYVuelta_recuperaElPrecio() {
        val c = base().convertirPrecios(0.27).convertirPrecios(1 / 0.27)
        assertEquals(10.0, c.ventanas[0][1].toDouble(), 0.01)
    }
}
