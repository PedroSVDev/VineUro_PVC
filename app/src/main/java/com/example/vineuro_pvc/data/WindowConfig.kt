package com.example.vineuro_pvc.data

/**
 * Modelo de datos completo para las 73 configuraciones de ventanas y puertas PVC.
 */
data class WindowConfig(
    val id: String,
    val nombre: String,
    val categoria: String,
    val layoutType: LayoutType,
    val cols: Int = 1,
    val rows: Int = 1,
    val marcosCount: Int = 4,
    val hojasCount: Int = 1,
    val cauchoCount: Int = 2,
    val glazBeadsCount: Int = 4,
    val precioEstimado: Double = 150.0,
    val costoEstimado: Double = 100.0,
    /** Id del modelo de la original (ver [ModelosOriginales]); solo con [LayoutType.ARBOL]. */
    val modelo: String? = null,
) {
    enum class LayoutType {
        GRID,
        TOP_TRANSOM,
        BOTTOM_TRANSOM,
        TOP_BOTTOM_TRANSOM,
        DOOR_L_LEFT,
        DOOR_L_RIGHT,
        DOOR_T_CENTER,
        DOOR_L_LEFT_TRANSOM,
        DOOR_L_RIGHT_TRANSOM,
        DOOR_T_CENTER_TRANSOM,
        ASYMMETRIC_SPLIT,
        /** Estructura exacta de la original: divisiones decodificadas de su fórmula (ver [ModelosOriginales]). */
        ARBOL,
    }

    /** Modelo de la original de esta configuración (null si es un modelo antiguo). */
    val modeloOriginal: ModelosOriginales.M? get() = if (layoutType == LayoutType.ARBOL) ModelosOriginales.buscar(modelo) else null

    /** Número de secciones de alto editables. */
    fun filasActivas(): Int = modeloOriginal?.let { ModelosOriginales.nAlto(it) } ?: when (layoutType) {
        LayoutType.TOP_TRANSOM, LayoutType.BOTTOM_TRANSOM,
        LayoutType.DOOR_L_LEFT, LayoutType.DOOR_L_RIGHT, LayoutType.DOOR_T_CENTER,
        LayoutType.DOOR_L_LEFT_TRANSOM, LayoutType.DOOR_L_RIGHT_TRANSOM, LayoutType.DOOR_T_CENTER_TRANSOM -> 2
        LayoutType.TOP_BOTTOM_TRANSOM -> 3
        else -> maxOf(1, rows)
    }

    /** Número de secciones de ancho editables. */
    fun colsActivas(): Int = modeloOriginal?.let { ModelosOriginales.nAncho(it) } ?: when (layoutType) {
        LayoutType.DOOR_T_CENTER, LayoutType.DOOR_T_CENTER_TRANSOM -> maxOf(3, cols)
        LayoutType.DOOR_L_LEFT, LayoutType.DOOR_L_RIGHT, LayoutType.DOOR_L_LEFT_TRANSOM, LayoutType.DOOR_L_RIGHT_TRANSOM -> maxOf(2, cols)
        else -> maxOf(1, cols)
    }
}
