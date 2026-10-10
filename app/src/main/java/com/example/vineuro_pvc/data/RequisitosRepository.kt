package com.example.vineuro_pvc.data

import org.json.JSONObject
import android.content.Context

/**
 * Guarda los "requisitos" del documento en SQLite (tabla configuracion) y en Supabase (tabla configuracion).
 * Al sincronizar gana la versión más reciente.
 */
class RequisitosRepository(context: Context) {

    private val db = VineuroDatabase.get(context)
    private val supabase = SupabaseService()

    fun obtener(): RequisitosDocumento =
        db.leerConfiguracion(RequisitosDocumento.CLAVE)
            ?.let { runCatching { RequisitosDocumento.fromJson(JSONObject(it)) }.getOrNull() }
            ?: RequisitosDocumento()

    /** Guarda en el teléfono y lo sube a la nube. Devuelve null si se subió, o el mensaje de error. */
    suspend fun guardar(r: RequisitosDocumento): String? {
        val conFecha = r.copy(fechaActualizacion = System.currentTimeMillis())
        db.guardarConfiguracion(RequisitosDocumento.CLAVE, conFecha.toJson().toString(), conFecha.fechaActualizacion)
        return supabase.upsertConfiguracion(RequisitosDocumento.CLAVE, conFecha.toJson(), conFecha.fechaActualizacion)
    }

    /** Trae la versión de la nube si es más reciente que la del teléfono (o sube la local si es más nueva). */
    suspend fun sincronizar(): RequisitosDocumento {
        val local = obtener()
        val nube = supabase.fetchConfiguracion(RequisitosDocumento.CLAVE).getOrElse { return local }
            ?.let { runCatching { RequisitosDocumento.fromJson(it) }.getOrNull() }
        return when {
            nube == null -> {
                if (local.fechaActualizacion > 0) {
                    supabase.upsertConfiguracion(RequisitosDocumento.CLAVE, local.toJson(), local.fechaActualizacion)
                }
                local
            }
            nube.fechaActualizacion > local.fechaActualizacion -> {
                db.guardarConfiguracion(RequisitosDocumento.CLAVE, nube.toJson().toString(), nube.fechaActualizacion)
                nube
            }
            local.fechaActualizacion > nube.fechaActualizacion -> {
                supabase.upsertConfiguracion(RequisitosDocumento.CLAVE, local.toJson(), local.fechaActualizacion)
                local
            }
            else -> local
        }
    }
}
