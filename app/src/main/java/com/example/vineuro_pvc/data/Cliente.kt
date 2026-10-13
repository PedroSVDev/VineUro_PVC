package com.example.vineuro_pvc.data

import java.util.UUID

/**
 * Modelo de datos para representar a un Cliente.
 * Se identifica por [id] (UUID generado en el teléfono), que también es la clave primaria en Supabase.
 */
data class Cliente(
    val id: String = UUID.randomUUID().toString(),
    val nombres: String,
    val apellidos: String,
    val telefono: String = "",
    val email: String = "",
    val direccion: String = "",
    val nota: String = "",
    val fechaRegistro: Long = System.currentTimeMillis(),
    /** Código corto visible (CLI-0001…). Se asigna solo al guardar; el [id] largo sigue siendo la clave interna. */
    val codigo: String = "",
) {
    val nombreCompleto: String
        get() = "$nombres $apellidos".trim()
}
