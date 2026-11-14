package com.example.vineuro_pvc

import com.example.vineuro_pvc.data.FilaColor
import com.example.vineuro_pvc.data.Precios
import org.junit.Assert.assertEquals
import org.junit.Test

/** Los 6 colores del gerente van primero y en su orden; lo demás se conserva. */
class PreciosColoresTest {

    private val orden = listOf("Antracita", "Blanco", "Negro", "Nogal", "Roble Dorado", "Silver")

    @Test fun porDefecto_empiezaConLosSeisDelGerente() {
        assertEquals(orden, Precios.porDefecto().colores.take(6).map { it.nombre })
    }

    @Test fun reordena_conservandoElRestoYElTonoExistente() {
        val antes = listOf(FilaColor("alder", "#fccfae"), FilaColor("silver", "#111111"), FilaColor("Negro", "#222222"), FilaColor("rojo", "#ff0000"))
        val r = Precios.conColoresDelGerente(antes)
        assertEquals(orden, r.take(6).map { it.nombre })
        assertEquals("#111111", r[5].colorHex)               // Silver ya existía: conserva su tono
        assertEquals("#222222", r[2].colorHex)
        assertEquals(listOf("alder", "rojo"), r.drop(6).map { it.nombre })
        assertEquals(r, Precios.conColoresDelGerente(r))     // repetirlo no cambia nada
    }
}
