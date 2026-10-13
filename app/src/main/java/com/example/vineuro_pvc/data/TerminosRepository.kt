package com.example.vineuro_pvc.data

import android.content.Context
import org.json.JSONObject

/**
 * Guarda la plantilla de Términos y condiciones, las cotizaciones de cada pedido y el correlativo
 * de cotizaciones. Todo va en la tabla "configuracion" (SQLite + Supabase), una fila por clave.
 */
class TerminosRepository(context: Context) {

    private val db = VineuroDatabase.get(context)
    private val supabase = SupabaseService()

    // ===================== PLANTILLA =====================

    fun plantilla(): TerminosCondiciones =
        leer(TerminosCondiciones.CLAVE)?.let { runCatching { TerminosCondiciones.fromJson(it) }.getOrNull() }
            ?: TerminosCondiciones()

    suspend fun guardarPlantilla(t: TerminosCondiciones): String? {
        val conFecha = t.copy(fechaActualizacion = System.currentTimeMillis())
        return escribir(TerminosCondiciones.CLAVE, conFecha.toJson(), conFecha.fechaActualizacion)
    }

    suspend fun sincronizarPlantilla(): TerminosCondiciones {
        sincronizarClave(TerminosCondiciones.CLAVE)
        return plantilla()
    }

    // ===================== COTIZACIÓN DE UN PEDIDO =====================

    fun cotizacion(pedidoId: String): CotizacionPedido? =
        leer(CotizacionPedido.clave(pedidoId))?.let { runCatching { CotizacionPedido.fromJson(it) }.getOrNull() }

    suspend fun guardarCotizacion(c: CotizacionPedido): String? {
        val conFecha = c.copy(fechaActualizacion = System.currentTimeMillis())
        return escribir(CotizacionPedido.clave(c.pedidoId), conFecha.toJson(), conFecha.fechaActualizacion)
    }

    suspend fun sincronizarCotizacion(pedidoId: String): CotizacionPedido? {
        sincronizarClave(CotizacionPedido.clave(pedidoId))
        return cotizacion(pedidoId)
    }

    /**
     * Siguiente N° de cotización (0405, 0406…). Toma el mayor entre el teléfono y la nube
     * para que dos teléfonos no repitan número mientras haya Internet.
     */
    suspend fun siguienteNumero(): String {
        sincronizarClave(CLAVE_CORRELATIVO)
        val ultimo = leer(CLAVE_CORRELATIVO)?.optInt("ultimo", 0) ?: 0
        val siguiente = ultimo + 1
        escribir(CLAVE_CORRELATIVO, JSONObject().put("ultimo", siguiente), System.currentTimeMillis())
        return siguiente.toString().padStart(4, '0')
    }

    /** Si el titular escribe un número a mano (p. ej. para continuar desde 0404), el contador sigue desde ahí. */
    suspend fun registrarNumeroManual(numero: String) {
        val n = numero.trim().toIntOrNull() ?: return
        val ultimo = leer(CLAVE_CORRELATIVO)?.optInt("ultimo", 0) ?: 0
        if (n > ultimo) escribir(CLAVE_CORRELATIVO, JSONObject().put("ultimo", n), System.currentTimeMillis())
    }

    // ===================== AYUDANTES =====================

    private fun leer(clave: String): JSONObject? =
        db.leerConfiguracion(clave)?.let { runCatching { JSONObject(it) }.getOrNull() }

    /** Guarda local y en la nube. Devuelve null si la nube respondió OK, o el mensaje de error. */
    private suspend fun escribir(clave: String, data: JSONObject, fecha: Long): String? {
        data.put("fecha_actualizacion", fecha)
        db.guardarConfiguracion(clave, data.toString(), fecha)
        return supabase.upsertConfiguracion(clave, data, fecha)
    }

    /** Gana la versión más reciente (teléfono o nube). Sin conexión no hace nada. */
    private suspend fun sincronizarClave(clave: String) {
        val local = leer(clave)
        val fechaLocal = local?.optLong("fecha_actualizacion", 0L) ?: 0L
        val nube = supabase.fetchConfiguracion(clave).getOrElse { return } // sin conexión
        val fechaNube = nube?.optLong("fecha_actualizacion", 0L) ?: -1L
        when {
            nube != null && fechaNube > fechaLocal -> db.guardarConfiguracion(clave, nube.toString(), fechaNube)
            local != null && fechaLocal > fechaNube -> supabase.upsertConfiguracion(clave, local, fechaLocal)
        }
        // El correlativo nunca debe bajar: se queda con el mayor
        if (clave == CLAVE_CORRELATIVO && nube != null && local != null) {
            val mayor = maxOf(nube.optInt("ultimo", 0), local.optInt("ultimo", 0))
            if (mayor != local.optInt("ultimo", 0)) {
                db.guardarConfiguracion(clave, JSONObject().put("ultimo", mayor).put("fecha_actualizacion", fechaNube).toString(), fechaNube)
            }
        }
    }

    companion object {
        private const val CLAVE_CORRELATIVO = "correlativo_cotizacion"
    }
}
