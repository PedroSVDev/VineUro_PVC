package com.example.vineuro_pvc

import com.example.vineuro_pvc.data.OpcionesVentana
import com.example.vineuro_pvc.data.ReglasBases
import com.example.vineuro_pvc.data.ReglasBases.BaseDim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglasBasesTest {
    private val D = 1e-9

    @Test
    fun unaBaseUsaLoMarcado() {
        val op = OpcionesVentana()
        op.perfiles.addAll(listOf("juntas:0", "juntas:1", "extensor:40:3"))
        op.adicionales.addAll(listOf("perfil del asiento", "jamba de la esquina"))
        val m = ReglasBases.metros(listOf(BaseDim(1.5, 1.2)), op)
        assertEquals(1.2 + 1.5, m.porTipo["juntas"]!!, D)
        assertEquals(1.2, m.porTipo["ext40"]!!, D)
        assertEquals(1.5, m.asiento, D)
        assertEquals(1.2, m.jamba, D)
        assertEquals(0.0, m.dorsill, D)
        assertEquals(listOf(1.5), m.anchosUmbral)
    }

    @Test
    fun ventanaEnL_juntaSiempreEnLadoQueToca_yUmbralSoloEnLaBaja() {
        // "o" (baja, izquierda) + "Q" (alta, derecha)
        val bases = listOf(BaseDim(0.8, 1.3), BaseDim(0.9, 2.2))
        val m = ReglasBases.metros(bases, OpcionesVentana())
        // Sin marcar nada: la junta sale SIEMPRE en el lado derecho de la baja (alto 1.3) y nada en la alta
        assertEquals(1.3, m.porTipo["juntas"]!!, D)
        // Solo la base baja lleva umbral; la más alta lleva el dorsill
        assertEquals(listOf(0.8), m.anchosUmbral)
        val op = OpcionesVentana(); op.adicionales.addAll(listOf("perfil del asiento", "dorsill"))
        val m2 = ReglasBases.metros(bases, op)
        assertEquals(0.8, m2.asiento, D)
        assertEquals(0.9, m2.dorsill, D)
    }

    @Test
    fun reglasPorPosicion() {
        val r = ReglasBases.reglas(2, 1, 1)           // última y más alta
        assertEquals(ReglasBases.R.V, r.umbral)
        assertEquals(ReglasBases.R.N, r.dorsill)
        val p = ReglasBases.reglas(2, 0, 1)           // primera, baja: junta siempre a la derecha
        assertEquals(ReglasBases.R.A, p.junta[3])
        assertTrue(ReglasBases.reglas(1, 0, 0).junta.all { it == ReglasBases.R.N })
    }
}
