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

    /** Área real de la ventana (m²): en los modelos en L / T de la original no cuenta el hueco vacío. */
    val areaM2: Double
        get() {
            val mo = config.modeloOriginal
            if (mo != null) {
                val pl = ModelosOriginales.plano(mo, anchoTotalMm.toDouble(), altoTotalMm.toDouble(), sectionWidths, sectionHeights)
                return pl.celdas.sumOf { it.ancho * it.alto } / 1_000_000.0
            }
            return (altoTotalMm / 1000.0) * (anchoTotalMm / 1000.0)
        }

    companion object {
        fun create(
            index: Int,
            config: WindowConfig,
            altoMm: Int? = null,
            anchoMm: Int? = null,
            colorBgHex: String = "#42A5F5"
        ): OrderItem {
            // Los modelos de la original traen su propio tamaño inicial y sus secciones (según los pesos de su estructura)
            val modelo = config.modeloOriginal
            val alto = altoMm ?: modelo?.altoMm ?: 1800
            val ancho = anchoMm ?: modelo?.anchoMm ?: 1500
            if (modelo != null) {
                val (anchos, altos) = ModelosOriginales.seccionesPorDefecto(modelo, ancho.toFloat(), alto.toFloat())
                return OrderItem(
                    index = index, config = config, altoTotalMm = alto, anchoTotalMm = ancho,
                    sectionHeights = altos, sectionWidths = anchos, colorBgHex = colorBgHex,
                )
            }

            val rows = config.filasActivas()
            val cols = config.colsActivas()

            val defaultH = alto.toFloat() / rows
            val defaultW = ancho.toFloat() / cols

            return OrderItem(
                index = index,
                config = config,
                altoTotalMm = alto,
                anchoTotalMm = ancho,
                sectionHeights = FloatArray(rows) { defaultH },
                sectionWidths = FloatArray(cols) { defaultW },
                colorBgHex = colorBgHex
            )
        }
    }
}
