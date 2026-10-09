package com.example.vineuro_pvc.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject

/**
 * Repositorio híbrido de Precios: copia local (SharedPreferences) + fila única en la tabla "precios" de Supabase.
 * La copia local permite trabajar sin Internet; al sincronizar gana la versión más reciente.
 */
class PreciosRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("pref_precios", Context.MODE_PRIVATE)
    private val supabaseService = SupabaseService()

    fun tieneDatosLocales(): Boolean = prefs.contains(KEY)

    /** Precios guardados en el teléfono (o los valores por defecto si aún no hay nada). */
    fun obtener(): Precios {
        val json = prefs.getString(KEY, null) ?: return Precios.porDefecto()
        return try {
            PreciosJson.fromJson(JSONObject(json))
        } catch (e: Exception) {
            e.printStackTrace()
            Precios.porDefecto()
        }
    }

    /** Guarda en el teléfono marcando la fecha de modificación actual. */
    fun guardarLocal(precios: Precios): Precios {
        val conFecha = precios.copy(fechaActualizacion = System.currentTimeMillis())
        escribir(conFecha)
        return conFecha
    }

    private fun escribir(precios: Precios) {
        prefs.edit { putString(KEY, PreciosJson.toJson(precios).toString()) }
    }

    /** Sube a Supabase lo que hay en el teléfono. Devuelve null si OK o el mensaje de error. */
    suspend fun subir(): String? = supabaseService.upsertPrecios(obtener())

    /**
     * Fusiona teléfono y nube (gana el más reciente) y devuelve el resultado.
     * Si no hay conexión, devuelve lo local sin tocar nada.
     */
    suspend fun sincronizar(): Precios {
        val resultado = supabaseService.fetchPrecios()
        if (resultado.isFailure) return obtener()

        val nube = resultado.getOrNull()
        val hayLocal = tieneDatosLocales()
        val local = obtener()

        return when {
            nube == null -> {
                // La nube está vacía: se sube lo del teléfono (o los valores por defecto)
                if (!hayLocal) escribir(local)
                supabaseService.upsertPrecios(local)
                local
            }
            !hayLocal || nube.fechaActualizacion > local.fechaActualizacion -> {
                escribir(nube)
                nube
            }
            local.fechaActualizacion > nube.fechaActualizacion -> {
                supabaseService.upsertPrecios(local)
                local
            }
            else -> local
        }
    }

    companion object {
        private const val KEY = "precios_json"
    }
}
