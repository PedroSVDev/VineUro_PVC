package com.example.vineuro_pvc.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sincronización automática: la app se usa normalmente sin Internet (todo se guarda en SQLite) y,
 * en cuanto el teléfono detecta conexión, pone al día la base local con Supabase
 * (sube lo pendiente y descarga lo más reciente). No requiere ninguna acción del usuario.
 */
object AutoSync {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val enCurso = AtomicBoolean(false)
    private var registrado = false

    /** Registra el detector de conexión (una sola vez) y sincroniza al arrancar si ya hay Internet. */
    fun iniciar(contexto: Context) {
        if (registrado) return
        val app = contexto.applicationContext
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        try {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    sincronizarAhora(app)
                }
            })
            registrado = true
        } catch (_: Exception) {
            // Sin permiso o sin servicio de red: la app sigue funcionando sin sincronización automática
        }
    }

    /** Sincroniza todo en segundo plano; si ya hay una sincronización en curso no hace nada. */
    fun sincronizarAhora(contexto: Context) {
        if (!enCurso.compareAndSet(false, true)) return
        val app = contexto.applicationContext
        scope.launch {
            try {
                runCatching { ClientRepository(app).sincronizarConSupabase() }
                runCatching { PedidoRepository(app).sincronizar() }
                runCatching { PreciosRepository(app).sincronizar() }
                runCatching { RequisitosRepository(app).sincronizar() }
                runCatching { TerminosRepository(app).sincronizarPlantilla() }
            } finally {
                enCurso.set(false)
            }
        }
    }
}
