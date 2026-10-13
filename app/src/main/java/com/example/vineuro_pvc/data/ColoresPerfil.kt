package com.example.vineuro_pvc.data

/**
 * Colores de perfil de la tabla de Precios (nombre -> #RRGGBB), disponibles para el dibujo de la ventana
 * ("vista previa de color" del original, en pantalla y en los PDF). Lo actualiza PreciosRepository.
 */
object ColoresPerfil {
    @Volatile private var mapa: Map<String, String> = emptyMap()

    fun actualizar(colores: List<FilaColor>) {
        mapa = colores.associate { it.nombre.trim() to it.colorHex }
    }

    /** #RRGGBB del color elegido, o null si no hay color o ya no existe en Precios (se dibuja blanco). */
    fun hexDe(nombre: String?): String? = nombre?.trim()?.let { mapa[it] }
}
