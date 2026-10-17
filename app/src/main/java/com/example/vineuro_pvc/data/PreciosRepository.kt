package com.example.vineuro_pvc.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject

/**
 * Repositorio de Precios: base de datos local SQLite (tabla "precios") + fila única en la tabla "precios" de Supabase.
 * La copia local permite trabajar sin Internet; al sincronizar gana la versión más reciente.
 */
class PreciosRepository(context: Context) {

    private val db = VineuroDatabase.get(context)
    private val supabaseService = SupabaseService()

    private fun leerJsonLocal(): String? =
        db.readableDatabase.query(
            VineuroDatabase.T_PRECIOS, arrayOf("data"), "id = ?", arrayOf(VineuroDatabase.PRECIOS_ID),
            null, null, null,
        ).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    fun tieneDatosLocales(): Boolean = leerJsonLocal() != null

    /** Precios guardados en el teléfono (o los valores por defecto si aún no hay nada). */
    fun obtener(): Precios {
        val json = leerJsonLocal() ?: return Precios.porDefecto().also { ColoresPerfil.actualizar(it.colores) }
        return try {
            PreciosJson.fromJson(JSONObject(json)).also { ColoresPerfil.actualizar(it.colores) }
        } catch (e: Exception) {
            e.printStackTrace()
            Precios.porDefecto()
        }
    }

    /** Guarda en el teléfono marcando la fecha de modificación actual. */
    fun guardarLocal(precios: Precios): Precios {
        val conFecha = precios.copy(fechaActualizacion = System.currentTimeMillis())
        escribir(conFecha)
        ColoresPerfil.actualizar(conFecha.colores)
        return conFecha
    }

    /** Reemplaza lo guardado conservando la fecha de modificación de [precios] (para importar desde un archivo). */
    fun reemplazar(precios: Precios) = escribir(precios)

    private fun escribir(precios: Precios) {
        val cv = ContentValues().apply {
            put("id", VineuroDatabase.PRECIOS_ID)
            put("data", PreciosJson.toJson(precios).toString())
            put("fecha_actualizacion", precios.fechaActualizacion)
        }
        db.writableDatabase.insertWithOnConflict(VineuroDatabase.T_PRECIOS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
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
}
