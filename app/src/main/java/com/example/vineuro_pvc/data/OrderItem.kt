package com.example.vineuro_pvc.data

/**
 * Modelo de datos para cada elemento / ventana agregada a la orden.
 */
data class OrderItem(
    var index: Int,
    var config: WindowConfig,
    var altoTotalMm: Int = 1800,
    var anchoTotalMm: Int = 1500,
    var sectionHeights: FloatArray = floatArrayOf(1800f),
    var sectionWidths: FloatArray = floatArrayOf(1500f),
    val paneMechanisms: MutableMap<Int, String> = mutableMapOf(),
    val paneMosqNet: MutableMap<Int, Boolean> = mutableMapOf(),
    val panePuerta: MutableMap<Int, Boolean> = mutableMapOf(),
    val paneSandwich: MutableMap<Int, Boolean> = mutableMapOf(),
    val paneCols: MutableMap<Int, Int> = mutableMapOf(),
    var colorBgHex: String = "#42A5F5",
    /** Opciones elegidas en el menú lateral "configuración de la ventana". */
    var opciones: OpcionesVentana = OpcionesVentana(),
) {
    companion object {
        fun create(
            index: Int,
            config: WindowConfig,
            altoMm: Int = 1800,
            anchoMm: Int = 1500,
            colorBgHex: String = "#42A5F5"
        ): OrderItem {
            val rows = when (config.layoutType) {
                WindowConfig.LayoutType.TOP_TRANSOM,
                WindowConfig.LayoutType.BOTTOM_TRANSOM,
                WindowConfig.LayoutType.DOOR_L_LEFT,
                WindowConfig.LayoutType.DOOR_L_RIGHT,
                WindowConfig.LayoutType.DOOR_T_CENTER,
                WindowConfig.LayoutType.DOOR_L_LEFT_TRANSOM,
                WindowConfig.LayoutType.DOOR_L_RIGHT_TRANSOM,
                WindowConfig.LayoutType.DOOR_T_CENTER_TRANSOM -> 2

                WindowConfig.LayoutType.TOP_BOTTOM_TRANSOM -> 3

                else -> maxOf(1, config.rows)
            }

            val cols = when (config.layoutType) {
                WindowConfig.LayoutType.DOOR_T_CENTER,
                WindowConfig.LayoutType.DOOR_T_CENTER_TRANSOM -> maxOf(3, config.cols)

                WindowConfig.LayoutType.DOOR_L_LEFT,
                WindowConfig.LayoutType.DOOR_L_RIGHT,
                WindowConfig.LayoutType.DOOR_L_LEFT_TRANSOM,
                WindowConfig.LayoutType.DOOR_L_RIGHT_TRANSOM -> maxOf(2, config.cols)

                else -> maxOf(1, config.cols)
            }

            val defaultH = altoMm.toFloat() / rows
            val defaultW = anchoMm.toFloat() / cols

            return OrderItem(
                index = index,
                config = config,
                altoTotalMm = altoMm,
                anchoTotalMm = anchoMm,
                sectionHeights = FloatArray(rows) { defaultH },
                sectionWidths = FloatArray(cols) { defaultW },
                colorBgHex = colorBgHex
            )
        }
    }
}
