package com.example.vineuro_pvc.data

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Bloqueo opcional de la aplicación (A5 del Plan de Seguridad Integral, medida M16).
 * Cuando está activo, al abrir la app y al volver a ella después de [TIEMPO_GRACIA_MS] se pide el PIN, patrón,
 * contraseña o huella del teléfono (el mismo bloqueo de pantalla de Android). La app no guarda ni conoce ese PIN.
 * Si el teléfono no tiene bloqueo de pantalla, el bloqueo de la app no se aplica para no dejar al usuario fuera.
 */
object BloqueoApp {
    private const val PREFS = "pref_seguridad"
    private const val KEY_ACTIVO = "bloqueo_activo"
    const val TIEMPO_GRACIA_MS = 60_000L

    /** Verdadero desde que el usuario se identificó hasta que la app queda un rato en segundo plano. */
    var desbloqueado = false
    private var salidaMs = 0L

    fun activo(ctx: Context): Boolean = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ACTIVO, false)

    fun guardar(ctx: Context, activo: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ACTIVO, activo).apply()
        desbloqueado = true
        salidaMs = 0L
    }

    /** El teléfono tiene PIN, patrón, contraseña o huella configurados. */
    fun telefonoProtegido(ctx: Context): Boolean =
        (ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure

    /** Anota el momento en que la app pasa a segundo plano. */
    fun marcarSalida() {
        salidaMs = SystemClock.elapsedRealtime()
    }

    /** Hay que pedir el desbloqueo: bloqueo activo, teléfono protegido y (primera vez o ausencia larga). */
    fun debePedir(ctx: Context): Boolean {
        if (!activo(ctx) || !telefonoProtegido(ctx)) return false
        if (!desbloqueado) return true
        if (salidaMs > 0L && SystemClock.elapsedRealtime() - salidaMs > TIEMPO_GRACIA_MS) {
            desbloqueado = false
            return true
        }
        return false
    }

    fun marcarDesbloqueado() {
        desbloqueado = true
        salidaMs = 0L
    }

    /** Pantalla del sistema que pide el PIN, patrón o huella; null si el teléfono no tiene bloqueo. */
    fun intentoDesbloqueo(ctx: Context, titulo: String, descripcion: String): Intent? =
        (ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
            .createConfirmDeviceCredentialIntent(titulo, descripcion)
}
