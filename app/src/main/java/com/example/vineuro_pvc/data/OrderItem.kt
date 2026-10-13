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
    /** 1..4 = subdivisión en hojas separadas (antiguo); 11..14 = imposta interior de la hoja (11 hor.x1, 12 hor.x2, 13 ver.x1, 14 ver.x2). */
    val paneCols: MutableMap<Int, Int> = mutableMapOf(),
    /** Bits de sándwich por vidrio de la hoja con imposta interior (bit 0 = arriba/izquierda). */
    val paneSandwichSub: MutableMap<Int, Int> = mutableMapOf(),
    /** Barras de vidrio (glazing bars) por paño: "HxV HxV HxV" (barras horizontales x verticales de cada vidrio, como la original). */
    val paneBars: MutableMap<Int, String> = mutableMapOf(),
    val paneImpOff: MutableMap<Int, String> = mutableMapOf(),
    var colorBgHex: String = "#42A5F5",
    /** Opciones elegidas en el menú lateral "configuración de la ventana". */
    var opciones: OpcionesVentana = OpcionesVentana(),
) {
    /** Copia independiente de la ventana (diseño, aperturas y todas las opciones del compás). */
    fun copiar(nuevoIndex: Int): OrderItem = OrderItem(
        index = nuevoIndex,
        config = config,
        altoTotalMm = altoTotalMm,
        anchoTotalMm = anchoTotalMm,
        sectionHeights = sectionHeights.copyOf(),
        sectionWidths = sectionWidths.copyOf(),
        paneMechanisms = paneMechanisms.toMutableMap(),
        paneMosqNet = paneMosqNet.toMutableMap(),
        panePuerta = panePuerta.toMutableMap(),
        paneSandwich = paneSandwich.toMutableMap(),
        paneCols = paneCols.toMutableMap(),
        paneSandwichSub = paneSandwichSub.toMutableMap(),
        paneBars = paneBars.toMutableMap(),
        paneImpOff = paneImpOff.toMutableMap(),
        colorBgHex = colorBgHex,
        opciones = opciones.copy(
            umbralesExternos = opciones.umbralesExternos.toMutableSet(),
            umbralesInternos = opciones.umbralesInternos.toMutableSet(),
            extras = opciones.extras.toMutableSet(),
            trabajos = opciones.trabajos.toMutableSet(),
            adicionales = opciones.adicionales.toMutableSet(),
            perfiles = opciones.perfiles.toMutableSet(),
        ),
    )

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
