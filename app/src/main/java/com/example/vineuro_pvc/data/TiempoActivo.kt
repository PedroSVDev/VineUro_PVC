package com.example.vineuro_pvc.data

/**
 * Tiempo que el usuario realmente trabaja en una cotización (diseño del pedido + oferta comercial),
 * guardado por pedido. No es el tiempo entre la fecha de creación y la de cierre: no cuenta las pausas.
 *
 * @param ms milisegundos de trabajo activo acumulados.
 * @param desdeInicio true si se midió desde que se creó el pedido (el pedido nació con el cronómetro funcionando);
 *                    los pedidos anteriores solo tendrían una parte del tiempo y no entran en el TPC.
 */
data class TiempoActivoPedido(val ms: Long, val desdeInicio: Boolean) {
    val minutos: Double get() = ms / 60_000.0
}

/**
 * Acumulador del tiempo activo (sin dependencias de Android para probarlo con JUnit).
 * Se llama a [tick] cada pocos segundos mientras la pantalla está abierta: solo suma el tramo en que el usuario
 * tocó la pantalla hace menos de [inactividadMs]. Si deja el teléfono quieto más tiempo, no suma.
 */
class TiempoActivo {
    var acumuladoMs: Long = 0L
        private set
    private var ultimoTick = 0L

    /** Empieza (o reanuda) la cuenta: lo que pasó antes no se suma. */
    fun iniciar(ahora: Long) { ultimoTick = ahora }

    /** Suma el tramo desde el último tick que cae dentro de la ventana de actividad. */
    fun tick(ahora: Long, ultimoToque: Long, inactividadMs: Long = INACTIVIDAD_MS) {
        if (ultimoTick == 0L) { ultimoTick = ahora; return }
        val desde = ultimoTick
        ultimoTick = ahora
        if (ultimoToque <= 0L || ahora <= desde) return
        val finActividad = ultimoToque + inactividadMs
        acumuladoMs += (minOf(ahora, finActividad) - desde).coerceIn(0L, ahora - desde)
    }

    /** Detiene la cuenta (la pantalla se cierra o pasa a segundo plano). */
    fun pausar() { ultimoTick = 0L }

    /** Entrega lo acumulado y lo pone en cero. */
    fun retirar(): Long = acumuladoMs.also { acumuladoMs = 0L }

    companion object {
        /** Sin tocar la pantalla durante este tiempo se considera que el usuario dejó de trabajar en la cotización. */
        const val INACTIVIDAD_MS = 2 * 60 * 1000L
        /** Con un cuadro de diálogo abierto (menús de opciones de la ventana) los toques no llegan a la pantalla: se tolera más. */
        const val INACTIVIDAD_DIALOGO_MS = 10 * 60 * 1000L
    }
}
