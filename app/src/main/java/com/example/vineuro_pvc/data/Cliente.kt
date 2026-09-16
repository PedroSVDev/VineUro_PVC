package com.example.vineuro_pvc.data

import java.util.UUID

/**
 * Modelo de datos para representar a un Cliente.
 */
data class Cliente(
    val id: String = UUID.randomUUID().toString(),
    val dni: String,
    val nombres: String,
    val apellidos: String,
    val telefono: String = "",
    val email: String = "",
    val direccion: String = "",
    val nota: String = "",
    val fechaRegistro: Long = System.currentTimeMillis(),
) {
    val nombreCompleto: String
        get() = "$nombres $apellidos".trim()

    val codigoCliente: String
        get() = "CLI-$dni"
}
