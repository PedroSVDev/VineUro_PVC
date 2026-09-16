package com.example.vineuro_pvc.data

/**
 * Modelo de datos para cada elemento / ventana agregada a la orden.
 */
data class OrderItem(
    var index: Int,
    var config: WindowConfig,
    var altoTotalMm: Int = 1800,
    var anchoTotalMm: Int = 1500,
    var sectionHeights: FloatArray = floatArrayOf(600f, 600f, 600f),
    var sectionWidths: FloatArray = floatArrayOf(1500f),
    val paneMechanisms: MutableMap<Int, String> = mutableMapOf(),
    var colorBgHex: String = "#42A5F5",
)
