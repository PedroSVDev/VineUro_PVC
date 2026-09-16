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
    }
}
