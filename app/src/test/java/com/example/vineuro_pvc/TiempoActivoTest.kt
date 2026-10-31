package com.example.vineuro_pvc

import com.example.vineuro_pvc.data.TiempoActivo
import org.junit.Assert.assertEquals
import org.junit.Test

/** Cronómetro de la cotización: solo suma el tiempo en que el usuario está trabajando. */
class TiempoActivoTest {

    @Test fun sumaElTiempoMientrasElUsuarioToca() {
        val t = TiempoActivo()
        t.iniciar(1_000)
        t.tick(6_000, ultimoToque = 5_900)
        t.tick(11_000, ultimoToque = 10_800)
        assertEquals(10_000L, t.acumuladoMs)
    }

    @Test fun sinTocarLaPantallaSoloSumaLaVentanaDeInactividad() {
        val t = TiempoActivo()
        t.iniciar(1_000)
        t.tick(300_000, ultimoToque = 1_000)      // 5 minutos sin tocar: solo cuentan los 2 minutos de gracia
        assertEquals(TiempoActivo.INACTIVIDAD_MS, t.acumuladoMs)
        t.tick(600_000, ultimoToque = 1_000)      // sigue sin tocar: no suma más
        assertEquals(TiempoActivo.INACTIVIDAD_MS, t.acumuladoMs)
    }

    @Test fun elTiempoConLaPantallaPausadaNoCuenta() {
        val t = TiempoActivo()
        t.iniciar(1_000)
        t.tick(6_000, ultimoToque = 5_900)
        t.pausar()
        t.tick(500_000, ultimoToque = 499_900)    // primer tick tras reanudar: solo fija el punto de partida
        t.tick(505_000, ultimoToque = 504_900)
        assertEquals(10_000L, t.acumuladoMs)
    }

    @Test fun conUnDialogoAbiertoSeToleraMasTiempo() {
        val t = TiempoActivo()
        t.iniciar(1_000)
        t.tick(400_000, ultimoToque = 1_000, inactividadMs = TiempoActivo.INACTIVIDAD_DIALOGO_MS)
        assertEquals(399_000L, t.acumuladoMs)
    }

    @Test fun retirarEntregaLoAcumuladoYLoPoneEnCero() {
        val t = TiempoActivo()
        t.iniciar(1_000)
        t.tick(6_000, ultimoToque = 5_900)
        assertEquals(5_000L, t.retirar())
        assertEquals(0L, t.acumuladoMs)
    }

    @Test fun sinNingunToqueNoSuma() {
        val t = TiempoActivo()
        t.iniciar(1_000)
        t.tick(6_000, ultimoToque = 0L)
        assertEquals(0L, t.acumuladoMs)
    }
}
