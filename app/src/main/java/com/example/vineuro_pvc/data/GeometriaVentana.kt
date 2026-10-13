package com.example.vineuro_pvc.data

import com.example.vineuro_pvc.data.WindowConfig.LayoutType

/**
 * Geometría real de una ventana en milímetros, a partir del alto/ancho total y de las
 * secciones que se editan en el dibujo (cotas). Se usa en la guía de corte y en el Anexo A.
 *
 * - Celda: un paño (con el mismo número de paño que usa el dibujo), medido de eje a eje.
 * - Division: una imposta (vertical u horizontal) entre [desde] y [hasta].
 *
 * Los diseños de puerta (L / T) se aproximan como cuadrícula (filas x columnas): [aproximado] = true.
 */
object GeometriaVentana {

    data class Celda(val indice: Int, val x0: Float, val y0: Float, val x1: Float, val y1: Float)
    data class Division(val vertical: Boolean, val pos: Float, val desde: Float, val hasta: Float)
    data class Resultado(
        val ancho: Float,
        val alto: Float,
        val celdas: List<Celda>,
        val impostas: List<Division>,
        val aproximado: Boolean,
    )

    fun calcular(item: OrderItem): Resultado {
        val w = item.anchoTotalMm.toFloat().coerceAtLeast(1f)
        val h = item.altoTotalMm.toFloat().coerceAtLeast(1f)
        val cols = item.sectionWidths.size.coerceAtLeast(1)
        val filas = item.sectionHeights.size.coerceAtLeast(1)
        val xs = acumulado(item.sectionWidths, w)
        val ys = acumulado(item.sectionHeights, h)
        val celdas = mutableListOf<Celda>()
        val impostas = mutableListOf<Division>()

        fun verticales(y0: Float, y1: Float) {
            for (c in 1 until cols) impostas.add(Division(true, xs[c], y0, y1))
        }

        return when (item.config.layoutType) {
            LayoutType.TOP_TRANSOM -> {
                val y1 = ys.getOrElse(1) { h / 3f }
                celdas.add(Celda(0, 0f, 0f, w, y1))
                for (c in 0 until cols) celdas.add(Celda(c + 1, xs[c], y1, xs[c + 1], h))
                impostas.add(Division(false, y1, 0f, w))
                verticales(y1, h)
                Resultado(w, h, celdas, impostas, false)
            }
            LayoutType.BOTTOM_TRANSOM -> {
                val y1 = ys.getOrElse(1) { h * 2f / 3f }
                for (c in 0 until cols) celdas.add(Celda(c, xs[c], 0f, xs[c + 1], y1))
                celdas.add(Celda(cols, 0f, y1, w, h))
                impostas.add(Division(false, y1, 0f, w))
                verticales(0f, y1)
                Resultado(w, h, celdas, impostas, false)
            }
            LayoutType.TOP_BOTTOM_TRANSOM -> {
                val y1 = ys.getOrElse(1) { h / 4f }
                val y2 = ys.getOrElse(2) { h * 3f / 4f }
                celdas.add(Celda(0, 0f, 0f, w, y1))
                for (c in 0 until cols) celdas.add(Celda(c + 1, xs[c], y1, xs[c + 1], y2))
                celdas.add(Celda(cols + 1, 0f, y2, w, h))
                impostas.add(Division(false, y1, 0f, w))
                impostas.add(Division(false, y2, 0f, w))
                verticales(y1, y2)
                Resultado(w, h, celdas, impostas, false)
            }
            else -> {
                // Cuadrícula: verticales de arriba a abajo, horizontales por tramo de columna
                for (r in 0 until filas) for (c in 0 until cols) {
                    celdas.add(Celda(r * cols + c, xs[c], ys[r], xs[c + 1], ys[r + 1]))
                }
                verticales(0f, h)
                for (r in 1 until filas) for (c in 0 until cols) impostas.add(Division(false, ys[r], xs[c], xs[c + 1]))
                Resultado(w, h, celdas, impostas, item.config.layoutType != LayoutType.GRID)
            }
        }
    }

    /** Posiciones acumuladas de las secciones, escaladas para que sumen [total]. */
    private fun acumulado(secciones: FloatArray, total: Float): FloatArray {
        val suma = secciones.sum().takeIf { it > 0f } ?: total
        val res = FloatArray(secciones.size + 1)
        for (i in secciones.indices) res[i + 1] = res[i] + secciones[i] * total / suma
        res[secciones.size] = total
        return res
    }
}
