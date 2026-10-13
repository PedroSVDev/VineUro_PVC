package com.example.vineuro_pvc.data

/** Textos en español para describir una ventana (aperturas, paños, opciones). */
object TextosVentana {

    fun apertura(id: String?): String = when (id?.trim()) {
        "1" -> "Deslizante →"
        "2" -> "Oscilobatiente superior"
        "3" -> "Deslizante ←"
        "4" -> "Practicable izquierda"
        "5", null, "" -> "Fijo"
        "6" -> "Practicable derecha"
        "7" -> "Oscilobatiente izquierda"
        "8" -> "Oscilobatiente derecha"
        "9" -> "Proyectante"
        "10" -> "Guillotina (sube)"
        "11" -> "Guillotina (baja)"
        else -> "Apertura $id"
    }

    /** Descripción de un paño: apertura (o aperturas por parte), divisiones y opciones. */
    fun pano(item: OrderItem, idx: Int): String {
        val filas = item.paneRows[idx] ?: 1
        val cols = item.paneCols[idx] ?: 1
        val partes = item.panePartMechanisms[idx]?.split(",")?.map { it.trim() }
            ?.takeIf { it.size == filas * cols && it.size > 1 }
        return buildString {
            if (partes != null) {
                append("${partes.size} partes (").append(partes.joinToString(" | ") { apertura(it) }).append(")")
            } else {
                append(apertura(item.paneMechanisms[idx]))
                if (filas * cols > 1) append(" · dividido ${filas}x$cols")
            }
            val mascara = item.paneSandwichMask[idx] ?: if (item.paneSandwich[idx] == true) (1 shl (filas * cols)) - 1 else 0
            if (mascara != 0) append(" · sándwich en ${Integer.bitCount(mascara)} parte(s)")
            val barras = item.paneBarras[idx] ?: 0
            if (barras > 0) append(" · barrotillos ${barras / 100}x${barras % 100}")
            if (item.panePuerta[idx] == true) append(" · puerta")
            if (item.paneMosqNet[idx] == true) append(" · mosquitero")
        }
    }
}
