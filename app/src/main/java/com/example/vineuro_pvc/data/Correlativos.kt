package com.example.vineuro_pvc.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Contadores correlativos (0001, 0002…) guardados en la tabla "configuracion" (teléfono + Supabase).
 * - "correlativo_cotizacion": N° de pedido / cotización (el mismo número para los dos).
 * - "correlativo_clientes":   código corto de cliente (CLI-0001…).
 * El contador nunca baja: entre el teléfono y la nube siempre se queda con el mayor.
 */
class Correlativos(context: Context) {

    private val db = VineuroDatabase.get(context)
    private val supabase = SupabaseService()

    fun ultimo(clave: String): Int =
        db.leerConfiguracion(clave)?.let { runCatching { JSONObject(it).optInt("ultimo", 0) }.getOrNull() } ?: 0

    /** Trae el número de la nube y se queda con el mayor. Sin conexión no hace nada. */
    suspend fun sincronizar(clave: String) {
        val nube = supabase.fetchConfiguracion(clave).getOrElse { return } // sin conexión
        val enNube = nube?.optInt("ultimo", 0) ?: 0
        val local = ultimo(clave)
        when {
            enNube > local -> guardarLocal(clave, enNube)
            local > enNube -> subir(clave, local)
        }
    }

    /** Siguiente número sin esperar a la nube; la nube se actualiza en segundo plano. */
    fun siguiente(clave: String): Int {
        val n = ultimo(clave) + 1
        guardarLocal(clave, n)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { subir(clave, n) } }
        return n
    }

    /** Si se usó un número más alto (escrito a mano o ya existente), el contador sigue desde ahí. */
    fun registrar(clave: String, numero: Int) {
        if (numero > ultimo(clave)) {
            guardarLocal(clave, numero)
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { subir(clave, numero) } }
        }
    }

    private fun guardarLocal(clave: String, n: Int) {
        val ahora = System.currentTimeMillis()
        db.guardarConfiguracion(clave, JSONObject().put("ultimo", n).put("fecha_actualizacion", ahora).toString(), ahora)
    }

    private suspend fun subir(clave: String, n: Int) {
        val ahora = System.currentTimeMillis()
        supabase.upsertConfiguracion(clave, JSONObject().put("ultimo", n).put("fecha_actualizacion", ahora), ahora)
    }

    companion object {
        const val COTIZACION = "correlativo_cotizacion"
        const val CLIENTES = "correlativo_clientes"

        fun formato(n: Int) = n.toString().padStart(4, '0')
        fun codigoCliente(n: Int) = "CLI-${formato(n)}"
    }
}
