package com.example.vineuro_pvc.data

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** Última interacción del usuario con la app; la actualiza MainActivity. */
object ActividadUsuario {
    @Volatile var ultimoToque = 0L
    /** false mientras hay un cuadro de diálogo abierto (sus toques no llegan a la pantalla principal). */
    @Volatile var ventanaConFoco = true

    fun registrarToque() { ultimoToque = SystemClock.elapsedRealtime() }

    fun registrarFoco(conFoco: Boolean) {
        ventanaConFoco = conFoco
        ultimoToque = SystemClock.elapsedRealtime()
    }
}

/**
 * Mide el tiempo que se trabaja de verdad en una cotización mientras su pantalla está abierta.
 * Se inicia en onResume y se pausa en onPause; solo suma mientras el usuario toca la pantalla (ver [TiempoActivo]).
 */
class CronometroCotizacion {
    private val nucleo = TiempoActivo()
    private val handler = Handler(Looper.getMainLooper())
    private var corriendo = false

    private val tic = object : Runnable {
        override fun run() {
            if (!corriendo) return
            marcar()
            handler.postDelayed(this, PASO_MS)
        }
    }

    private fun marcar() = nucleo.tick(
        SystemClock.elapsedRealtime(),
        ActividadUsuario.ultimoToque,
        if (ActividadUsuario.ventanaConFoco) TiempoActivo.INACTIVIDAD_MS else TiempoActivo.INACTIVIDAD_DIALOGO_MS,
    )

    fun iniciar() {
        if (corriendo) return
        corriendo = true
        nucleo.iniciar(SystemClock.elapsedRealtime())
        handler.postDelayed(tic, PASO_MS)
    }

    fun pausar() {
        if (!corriendo) return
        marcar()
        corriendo = false
        handler.removeCallbacks(tic)
        nucleo.pausar()
    }

    /** Entrega los milisegundos acumulados y los pone en cero. */
    fun retirar(): Long = nucleo.retirar()

    private companion object {
        const val PASO_MS = 5_000L
    }
}
