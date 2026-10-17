package com.example.vineuro_pvc.data

import com.example.vineuro_pvc.data.WindowConfig.LayoutType

/**
 * Catálogo de modelos de ventana.
 *  - [catalogo]: los 63 modelos del selector, en su orden y con su estructura exacta (los que se ofrecen).
 *  - [antiguas]: las configuraciones de versiones anteriores de la app; ya no se ofrecen, pero se conservan para poder abrir
 *    los pedidos guardados con ellas.
 *  - [obtenerTodas]: ambas (para resolver un pedido guardado por su id).
 */
object WindowConfigCatalog {

    const val PREFIJO_MODELO = "orig_"

    fun catalogo(): List<WindowConfig> = ModelosVentana.TODOS.map { m ->
        WindowConfig(
            id = PREFIJO_MODELO + m.id, nombre = ModelosVentana.nombre(m), categoria = "Modelos",
            layoutType = WindowConfig.LayoutType.ARBOL, modelo = m.id,
        )
    }

    fun obtenerTodas(): List<WindowConfig> = catalogo() + antiguas()

    fun antiguas(): List<WindowConfig> {
        val list = mutableListOf<WindowConfig>()

        // IMAGEN 1: Configuraciones Básicas y Puertas L / T (1 a 9)
        list.add(WindowConfig("1", "1 Paño Fijo", "Estándar", LayoutType.GRID, cols = 1, rows = 1, marcosCount = 4, hojasCount = 1, cauchoCount = 2, glazBeadsCount = 4, precioEstimado = 120.0, costoEstimado = 80.0))
        list.add(WindowConfig("2", "2 Paneles V.", "Estándar", LayoutType.GRID, cols = 2, rows = 1, marcosCount = 4, hojasCount = 2, cauchoCount = 4, glazBeadsCount = 6, precioEstimado = 220.0, costoEstimado = 150.0))
        list.add(WindowConfig("3", "3 Paneles V.", "Estándar", LayoutType.GRID, cols = 3, rows = 1, marcosCount = 4, hojasCount = 3, cauchoCount = 6, glazBeadsCount = 8, precioEstimado = 310.0, costoEstimado = 210.0))
        list.add(WindowConfig("4", "4 Paneles V.", "Estándar", LayoutType.GRID, cols = 4, rows = 1, marcosCount = 4, hojasCount = 4, cauchoCount = 8, glazBeadsCount = 10, precioEstimado = 400.0, costoEstimado = 280.0))
        list.add(WindowConfig("5", "2 Paneles H.", "Estándar", LayoutType.GRID, cols = 1, rows = 2, marcosCount = 4, hojasCount = 2, cauchoCount = 4, glazBeadsCount = 6, precioEstimado = 210.0, costoEstimado = 140.0))
        list.add(WindowConfig("6", "3 Paneles H.", "Estándar", LayoutType.GRID, cols = 1, rows = 3, marcosCount = 4, hojasCount = 3, cauchoCount = 6, glazBeadsCount = 8, precioEstimado = 300.0, costoEstimado = 200.0))
        list.add(WindowConfig("7", "Puerta Ventana L (Izq)", "Puerta-Ventana", LayoutType.DOOR_L_LEFT, cols = 2, rows = 1, marcosCount = 6, hojasCount = 2, cauchoCount = 6, glazBeadsCount = 8, precioEstimado = 450.0, costoEstimado = 320.0))
        list.add(WindowConfig("8", "Puerta Ventana T (Centro)", "Puerta-Ventana", LayoutType.DOOR_T_CENTER, cols = 3, rows = 1, marcosCount = 8, hojasCount = 3, cauchoCount = 8, glazBeadsCount = 10, precioEstimado = 580.0, costoEstimado = 410.0))
        list.add(WindowConfig("9", "Puerta Ventana L (Der)", "Puerta-Ventana", LayoutType.DOOR_L_RIGHT, cols = 2, rows = 1, marcosCount = 6, hojasCount = 2, cauchoCount = 6, glazBeadsCount = 8, precioEstimado = 450.0, costoEstimado = 320.0))

        // IMAGEN 2: Puertas L / T con Bandeja Superior (10 a 18)
        list.add(WindowConfig("10", "Puerta L (Izq) + Fijo Sup.", "Puerta-Ventana", LayoutType.DOOR_L_LEFT_TRANSOM, cols = 2, rows = 2, marcosCount = 7, hojasCount = 3, cauchoCount = 7, glazBeadsCount = 9, precioEstimado = 520.0, costoEstimado = 360.0))
        list.add(WindowConfig("11", "Puerta T (Centro) + Fijo Sup.", "Puerta-Ventana", LayoutType.DOOR_T_CENTER_TRANSOM, cols = 3, rows = 2, marcosCount = 9, hojasCount = 4, cauchoCount = 9, glazBeadsCount = 11, precioEstimado = 650.0, costoEstimado = 460.0))
        list.add(WindowConfig("12", "Puerta L (Der) + Fijo Sup.", "Puerta-Ventana", LayoutType.DOOR_L_RIGHT_TRANSOM, cols = 2, rows = 2, marcosCount = 7, hojasCount = 3, cauchoCount = 7, glazBeadsCount = 9, precioEstimado = 520.0, costoEstimado = 360.0))
        list.add(WindowConfig("13", "Puerta L (Izq) 2 Ventanas", "Puerta-Ventana", LayoutType.DOOR_L_LEFT, cols = 3, rows = 1, marcosCount = 7, hojasCount = 3, cauchoCount = 7, glazBeadsCount = 9, precioEstimado = 540.0, costoEstimado = 380.0))
        list.add(WindowConfig("14", "Puerta T (Centro) 4 Ventanas", "Puerta-Ventana", LayoutType.DOOR_T_CENTER, cols = 5, rows = 1, marcosCount = 10, hojasCount = 5, cauchoCount = 10, glazBeadsCount = 12, precioEstimado = 720.0, costoEstimado = 510.0))
        list.add(WindowConfig("15", "Puerta L (Der) 2 Ventanas", "Puerta-Ventana", LayoutType.DOOR_L_RIGHT, cols = 3, rows = 1, marcosCount = 7, hojasCount = 3, cauchoCount = 7, glazBeadsCount = 9, precioEstimado = 540.0, costoEstimado = 380.0))
        list.add(WindowConfig("16", "Puerta L (Izq) + Fijo H.", "Puerta-Ventana", LayoutType.DOOR_L_LEFT_TRANSOM, cols = 2, rows = 3, marcosCount = 8, hojasCount = 3, cauchoCount = 8, glazBeadsCount = 10, precioEstimado = 560.0, costoEstimado = 390.0))
        list.add(WindowConfig("17", "Puerta T (Centro) + Fijo H.", "Puerta-Ventana", LayoutType.DOOR_T_CENTER_TRANSOM, cols = 3, rows = 3, marcosCount = 10, hojasCount = 4, cauchoCount = 10, glazBeadsCount = 12, precioEstimado = 680.0, costoEstimado = 480.0))
        list.add(WindowConfig("18", "Puerta L (Der) + Fijo H.", "Puerta-Ventana", LayoutType.DOOR_L_RIGHT_TRANSOM, cols = 2, rows = 3, marcosCount = 8, hojasCount = 3, cauchoCount = 8, glazBeadsCount = 10, precioEstimado = 560.0, costoEstimado = 390.0))

        // IMAGEN 3: Fijos Horizontales y Combinaciones (19 a 27)
        list.add(WindowConfig("19", "4 Paneles Horizontales", "Estándar", LayoutType.GRID, cols = 1, rows = 4, marcosCount = 4, hojasCount = 4, cauchoCount = 8, glazBeadsCount = 10, precioEstimado = 390.0, costoEstimado = 270.0))
        list.add(WindowConfig("20", "3 Paneles V. Split Izq.", "Compuestas", LayoutType.GRID, cols = 3, rows = 2, marcosCount = 6, hojasCount = 4, cauchoCount = 6, glazBeadsCount = 8, precioEstimado = 350.0, costoEstimado = 240.0))
        list.add(WindowConfig("21", "3 Paneles V. Split Der.", "Compuestas", LayoutType.GRID, cols = 3, rows = 2, marcosCount = 6, hojasCount = 4, cauchoCount = 6, glazBeadsCount = 8, precioEstimado = 350.0, costoEstimado = 240.0))
        list.add(WindowConfig("22", "2 Paneles V. Split Med.", "Compuestas", LayoutType.GRID, cols = 2, rows = 3, marcosCount = 5, hojasCount = 4, cauchoCount = 6, glazBeadsCount = 8, precioEstimado = 330.0, costoEstimado = 230.0))
        list.add(WindowConfig("23", "3 Paneles V. Rejilla Central", "Compuestas", LayoutType.GRID, cols = 3, rows = 3, marcosCount = 8, hojasCount = 7, cauchoCount = 10, glazBeadsCount = 12, precioEstimado = 480.0, costoEstimado = 340.0))
        list.add(WindowConfig("24", "2 Paneles V. Split Sup.", "Compuestas", LayoutType.GRID, cols = 2, rows = 3, marcosCount = 5, hojasCount = 4, cauchoCount = 6, glazBeadsCount = 8, precioEstimado = 330.0, costoEstimado = 230.0))
        list.add(WindowConfig("25", "2 Paneles V. + Fijo Inf.", "Con Fijo", LayoutType.BOTTOM_TRANSOM, cols = 2, rows = 1, marcosCount = 5, hojasCount = 3, cauchoCount = 5, glazBeadsCount = 7, precioEstimado = 270.0, costoEstimado = 180.0))
        list.add(WindowConfig("26", "3 Paneles V. + Fijo Inf.", "Con Fijo", LayoutType.BOTTOM_TRANSOM, cols = 3, rows = 1, marcosCount = 5, hojasCount = 4, cauchoCount = 6, glazBeadsCount = 8, precioEstimado = 360.0, costoEstimado = 250.0))
        list.add(WindowConfig("27", "4 Paneles V. + Fijo Inf.", "Con Fijo", LayoutType.BOTTOM_TRANSOM, cols = 4, rows = 1, marcosCount = 5, hojasCount = 5, cauchoCount = 7, glazBeadsCount = 9, precioEstimado = 440.0, costoEstimado = 310.0))

        // IMAGEN 4: Fijos Sup. / Inf. con Múltiples Paneles (28 a 36)
        list.add(WindowConfig("28", "5 Paneles V. + Fijo Inf. (1)", "Con Fijo", LayoutType.BOTTOM_TRANSOM, cols = 5, rows = 1, marcosCount = 5, hojasCount = 6, cauchoCount = 8, glazBeadsCount = 10, precioEstimado = 520.0, costoEstimado = 370.0))
        list.add(WindowConfig("29", "5 Paneles V. + Fijo Inf. (2)", "Con Fijo", LayoutType.BOTTOM_TRANSOM, cols = 5, rows = 1, marcosCount = 5, hojasCount = 7, cauchoCount = 9, glazBeadsCount = 11, precioEstimado = 540.0, costoEstimado = 380.0))
        list.add(WindowConfig("30", "5 Paneles V. + Fijo Inf. (3)", "Con Fijo", LayoutType.BOTTOM_TRANSOM, cols = 5, rows = 1, marcosCount = 5, hojasCount = 8, cauchoCount = 10, glazBeadsCount = 12, precioEstimado = 560.0, costoEstimado = 390.0))
        list.add(WindowConfig("31", "2 Paneles + Fijo Sup. Split", "Con Fijo", LayoutType.TOP_TRANSOM, cols = 2, rows = 2, marcosCount = 5, hojasCount = 3, cauchoCount = 5, glazBeadsCount = 7, precioEstimado = 280.0, costoEstimado = 190.0))
        list.add(WindowConfig("32", "2 Paneles Asimétricos L", "Estándar", LayoutType.GRID, cols = 2, rows = 2, marcosCount = 4, hojasCount = 3, cauchoCount = 5, glazBeadsCount = 7, precioEstimado = 260.0, costoEstimado = 180.0))
        list.add(WindowConfig("33", "2 Paneles Asimétricos R", "Estándar", LayoutType.GRID, cols = 2, rows = 2, marcosCount = 4, hojasCount = 3, cauchoCount = 5, glazBeadsCount = 7, precioEstimado = 260.0, costoEstimado = 180.0))
        list.add(WindowConfig("34", "3 Paneles + Fijo Sup. Asim.", "Con Fijo", LayoutType.TOP_TRANSOM, cols = 3, rows = 2, marcosCount = 5, hojasCount = 4, cauchoCount = 6, glazBeadsCount = 8, precioEstimado = 340.0, costoEstimado = 230.0))
        list.add(WindowConfig("35", "3 Paneles + Fijo Sup. Central", "Con Fijo", LayoutType.TOP_TRANSOM, cols = 3, rows = 2, marcosCount = 5, hojasCount = 4, cauchoCount = 6, glazBeadsCount = 8, precioEstimado = 340.0, costoEstimado = 230.0))
        list.add(WindowConfig("36", "3 Paneles + Fijo Sup. Continuo", "Con Fijo", LayoutType.TOP_TRANSOM, cols = 3, rows = 2, marcosCount = 5, hojasCount = 4, cauchoCount = 6, glazBeadsCount = 8, precioEstimado = 350.0, costoEstimado = 240.0))

        // IMAGEN 5: Fijos Dobles / Múltiples (37 a 45)
        list.add(WindowConfig("37", "2 Paneles + Fijo Sup. Doble", "Con Fijo", LayoutType.TOP_TRANSOM, cols = 2, rows = 2, marcosCount = 5, hojasCount = 4, cauchoCount = 6, glazBeadsCount = 8, precioEstimado = 300.0, costoEstimado = 200.0))
        list.add(WindowConfig("38", "3 Paneles + Fijo Sup. Doble", "Con Fijo", LayoutType.TOP_TRANSOM, cols = 3, rows = 2, marcosCount = 5, hojasCount = 5, cauchoCount = 7, glazBeadsCount = 9, precioEstimado = 380.0, costoEstimado = 260.0))
        list.add(WindowConfig("39", "3 Paneles + Fijo Sup. Triple", "Con Fijo", LayoutType.TOP_TRANSOM, cols = 3, rows = 2, marcosCount = 5, hojasCount = 6, cauchoCount = 8, glazBeadsCount = 10, precioEstimado = 400.0, costoEstimado = 280.0))
        list.add(WindowConfig("40", "5 Paneles Verticales P.", "Estándar", LayoutType.GRID, cols = 5, rows = 1, marcosCount = 4, hojasCount = 5, cauchoCount = 10, glazBeadsCount = 12, precioEstimado = 480.0, costoEstimado = 340.0))
        list.add(WindowConfig("41", "6 Paneles Verticales P.", "Estándar", LayoutType.GRID, cols = 6, rows = 1, marcosCount = 4, hojasCount = 6, cauchoCount = 12, glazBeadsCount = 14, precioEstimado = 560.0, costoEstimado = 390.0))
        list.add(WindowConfig("42", "7 Paneles Verticales P.", "Estándar", LayoutType.GRID, cols = 7, rows = 1, marcosCount = 4, hojasCount = 7, cauchoCount = 14, glazBeadsCount = 16, precioEstimado = 640.0, costoEstimado = 450.0))
        list.add(WindowConfig("43", "4 Paneles + Fijo Inf. A", "Con Fijo", LayoutType.BOTTOM_TRANSOM, cols = 4, rows = 1, marcosCount = 5, hojasCount = 5, cauchoCount = 7, glazBeadsCount = 9, precioEstimado = 430.0, costoEstimado = 300.0))
        list.add(WindowConfig("44", "5 Paneles + Fijo Inf. B", "Con Fijo", LayoutType.BOTTOM_TRANSOM, cols = 5, rows = 1, marcosCount = 5, hojasCount = 6, cauchoCount = 8, glazBeadsCount = 10, precioEstimado = 510.0, costoEstimado = 360.0))
        list.add(WindowConfig("45", "6 Paneles + Fijo Inf. C", "Con Fijo", LayoutType.BOTTOM_TRANSOM, cols = 6, rows = 1, marcosCount = 5, hojasCount = 7, cauchoCount = 9, glazBeadsCount = 11, precioEstimado = 590.0, costoEstimado = 410.0))

        // IMAGEN 6: Matrices Complejas y Rejillas (46 a 54)
        list.add(WindowConfig("46", "Matriz 4x3 Especial A", "Compuestas", LayoutType.GRID, cols = 4, rows = 3, marcosCount = 6, hojasCount = 12, cauchoCount = 16, glazBeadsCount = 20, precioEstimado = 680.0, costoEstimado = 470.0))
        list.add(WindowConfig("47", "Matriz 5x3 Especial B", "Compuestas", LayoutType.GRID, cols = 5, rows = 3, marcosCount = 6, hojasCount = 15, cauchoCount = 20, glazBeadsCount = 24, precioEstimado = 780.0, costoEstimado = 540.0))
        list.add(WindowConfig("48", "Matriz 6x3 Especial C", "Compuestas", LayoutType.GRID, cols = 6, rows = 3, marcosCount = 6, hojasCount = 18, cauchoCount = 24, glazBeadsCount = 28, precioEstimado = 880.0, costoEstimado = 610.0))
        list.add(WindowConfig("49", "Fijo Sup. + 4 Paneles", "Con Fijo", LayoutType.TOP_TRANSOM, cols = 4, rows = 1, marcosCount = 5, hojasCount = 5, cauchoCount = 7, glazBeadsCount = 9, precioEstimado = 450.0, costoEstimado = 310.0))
        list.add(WindowConfig("50", "Fijo Sup. + 5 Paneles", "Con Fijo", LayoutType.TOP_TRANSOM, cols = 5, rows = 1, marcosCount = 5, hojasCount = 6, cauchoCount = 8, glazBeadsCount = 10, precioEstimado = 530.0, costoEstimado = 370.0))
        list.add(WindowConfig("51", "Fijo Sup. + 6 Paneles", "Con Fijo", LayoutType.TOP_TRANSOM, cols = 6, rows = 1, marcosCount = 5, hojasCount = 7, cauchoCount = 9, glazBeadsCount = 11, precioEstimado = 610.0, costoEstimado = 420.0))
        list.add(WindowConfig("52", "Fijo Sup. y Inf. + 2 Paneles", "Con Fijo", LayoutType.TOP_BOTTOM_TRANSOM, cols = 2, rows = 1, marcosCount = 6, hojasCount = 4, cauchoCount = 6, glazBeadsCount = 8, precioEstimado = 320.0, costoEstimado = 220.0))
        list.add(WindowConfig("53", "Fijo Sup. y Inf. + 3 Paneles", "Con Fijo", LayoutType.TOP_BOTTOM_TRANSOM, cols = 3, rows = 1, marcosCount = 6, hojasCount = 5, cauchoCount = 7, glazBeadsCount = 9, precioEstimado = 410.0, costoEstimado = 280.0))
        list.add(WindowConfig("54", "Fijo Sup. y Inf. + 4 Paneles", "Con Fijo", LayoutType.TOP_BOTTOM_TRANSOM, cols = 4, rows = 1, marcosCount = 6, hojasCount = 6, cauchoCount = 8, glazBeadsCount = 10, precioEstimado = 490.0, costoEstimado = 340.0))

        // IMAGEN 7: Fijos Complejos y Rejillas Triples (55 a 73)
        list.add(WindowConfig("55", "Fijo Sup. y Inf. + 6 Paneles", "Con Fijo", LayoutType.TOP_BOTTOM_TRANSOM, cols = 6, rows = 1, marcosCount = 6, hojasCount = 8, cauchoCount = 10, glazBeadsCount = 12, precioEstimado = 650.0, costoEstimado = 450.0))
        list.add(WindowConfig("56", "2 Paneles + 3 Fijos H.", "Con Fijo", LayoutType.GRID, cols = 2, rows = 3, marcosCount = 5, hojasCount = 6, cauchoCount = 8, glazBeadsCount = 10, precioEstimado = 370.0, costoEstimado = 250.0))
        list.add(WindowConfig("57", "2 Paneles + 2 Fijos H.", "Con Fijo", LayoutType.GRID, cols = 2, rows = 3, marcosCount = 5, hojasCount = 5, cauchoCount = 7, glazBeadsCount = 9, precioEstimado = 340.0, costoEstimado = 230.0))
        list.add(WindowConfig("58", "3 Paneles + Fijos Múltiples A", "Compuestas", LayoutType.GRID, cols = 3, rows = 3, marcosCount = 6, hojasCount = 9, cauchoCount = 12, glazBeadsCount = 15, precioEstimado = 520.0, costoEstimado = 360.0))
        list.add(WindowConfig("59", "3 Paneles + Fijos Múltiples B", "Compuestas", LayoutType.GRID, cols = 3, rows = 3, marcosCount = 6, hojasCount = 9, cauchoCount = 12, glazBeadsCount = 15, precioEstimado = 520.0, costoEstimado = 360.0))
        list.add(WindowConfig("60", "4 Paneles + Fijos Múltiples C", "Compuestas", LayoutType.GRID, cols = 4, rows = 3, marcosCount = 6, hojasCount = 12, cauchoCount = 15, glazBeadsCount = 18, precioEstimado = 620.0, costoEstimado = 430.0))
        list.add(WindowConfig("61", "5 Paneles + Fijos Múltiples D", "Compuestas", LayoutType.GRID, cols = 5, rows = 3, marcosCount = 6, hojasCount = 15, cauchoCount = 18, glazBeadsCount = 21, precioEstimado = 720.0, costoEstimado = 500.0))
        list.add(WindowConfig("62", "6 Paneles + Fijos Múltiples E", "Compuestas", LayoutType.GRID, cols = 6, rows = 3, marcosCount = 6, hojasCount = 18, cauchoCount = 21, glazBeadsCount = 24, precioEstimado = 820.0, costoEstimado = 570.0))
        list.add(WindowConfig("63", "7 Paneles + Fijos Múltiples F", "Compuestas", LayoutType.GRID, cols = 7, rows = 3, marcosCount = 6, hojasCount = 21, cauchoCount = 24, glazBeadsCount = 27, precioEstimado = 920.0, costoEstimado = 640.0))
        list.add(WindowConfig("64", "Matriz 2x2 Fijo Completo", "Compuestas", LayoutType.GRID, cols = 2, rows = 2, marcosCount = 4, hojasCount = 4, cauchoCount = 6, glazBeadsCount = 8, precioEstimado = 330.0, costoEstimado = 220.0))
        list.add(WindowConfig("65", "Matriz 3x2 Fijo Completo", "Compuestas", LayoutType.GRID, cols = 3, rows = 2, marcosCount = 4, hojasCount = 6, cauchoCount = 8, glazBeadsCount = 10, precioEstimado = 430.0, costoEstimado = 290.0))
        list.add(WindowConfig("66", "Matriz 4x2 Fijo Completo", "Compuestas", LayoutType.GRID, cols = 4, rows = 2, marcosCount = 4, hojasCount = 8, cauchoCount = 10, glazBeadsCount = 12, precioEstimado = 530.0, costoEstimado = 360.0))
        list.add(WindowConfig("67", "Matriz 5x2 Fijo Completo", "Compuestas", LayoutType.GRID, cols = 5, rows = 2, marcosCount = 4, hojasCount = 10, cauchoCount = 12, glazBeadsCount = 14, precioEstimado = 630.0, costoEstimado = 430.0))
        list.add(WindowConfig("68", "Matriz 6x2 Fijo Completo", "Compuestas", LayoutType.GRID, cols = 6, rows = 2, marcosCount = 4, hojasCount = 12, cauchoCount = 14, glazBeadsCount = 16, precioEstimado = 730.0, costoEstimado = 500.0))
        list.add(WindowConfig("69", "Matriz 7x2 Fijo Completo", "Compuestas", LayoutType.GRID, cols = 7, rows = 2, marcosCount = 4, hojasCount = 14, cauchoCount = 16, glazBeadsCount = 18, precioEstimado = 830.0, costoEstimado = 570.0))
        list.add(WindowConfig("70", "Configuración Modular 4x3 A", "Compuestas", LayoutType.GRID, cols = 4, rows = 3, marcosCount = 6, hojasCount = 12, cauchoCount = 14, glazBeadsCount = 16, precioEstimado = 660.0, costoEstimado = 450.0))
        list.add(WindowConfig("71", "Configuración Modular 5x3 B", "Compuestas", LayoutType.GRID, cols = 5, rows = 3, marcosCount = 6, hojasCount = 15, cauchoCount = 17, glazBeadsCount = 19, precioEstimado = 760.0, costoEstimado = 520.0))
        list.add(WindowConfig("72", "Configuración Modular 6x3 C", "Compuestas", LayoutType.GRID, cols = 6, rows = 3, marcosCount = 6, hojasCount = 18, cauchoCount = 20, glazBeadsCount = 22, precioEstimado = 860.0, costoEstimado = 590.0))
        list.add(WindowConfig("73", "Configuración Modular 7x3 D", "Compuestas", LayoutType.GRID, cols = 7, rows = 3, marcosCount = 6, hojasCount = 21, cauchoCount = 23, glazBeadsCount = 25, precioEstimado = 960.0, costoEstimado = 660.0))

        return list
    }
}
