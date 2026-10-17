package com.example.vineuro_pvc.data

import org.json.JSONObject
import java.util.Random

/**
 * Módulo 4: optimización de corte y listado de materiales (RF10, RF11, RF12).
 *
 * Toma las piezas de perfil de TODAS las ventanas del pedido (CalculadoraPrecios.piezasCorte), las agrupa por
 * sistema + perfil + color (barras de distinto sistema o color no se mezclan) y las reparte en varillas de largo
 * estándar minimizando el número de varillas (primero el mejor ajuste decreciente y luego mejoras por
 * reordenamiento). Cada corte consume [Parametros.separacionMm] (disco de la sierra y corte a 45°).
 *
 * Todo en milímetros enteros; sin dependencias de Android para poder probarlo con JUnit.
 */
object OptimizadorCorte {

    data class Parametros(
        /** Largo de la varilla de perfil que se compra. Estándar de perfil de PVC: 6 m. */
        val largoVarillaMm: Int = 6000,
        /** Material que se pierde en cada corte (disco + ángulo). Se descuenta entre piezas consecutivas. */
        val separacionMm: Int = 5,
        /** Se suma a cada pieza soldada (marco y hoja) para compensar el material que consume la soldadura. */
        val soldaduraMm: Int = 0,
        /** Un sobrante igual o mayor a esto se considera retazo reutilizable y no cuenta como desperdicio. */
        val retazoUtilMm: Int = 500,
    ) {
        fun toJson() = JSONObject().apply {
            put("largo_varilla_mm", largoVarillaMm); put("separacion_mm", separacionMm)
            put("soldadura_mm", soldaduraMm); put("retazo_util_mm", retazoUtilMm)
        }

        companion object {
            fun fromJson(o: JSONObject?): Parametros {
                val d = Parametros()
                if (o == null) return d
                return Parametros(
                    largoVarillaMm = o.optInt("largo_varilla_mm", d.largoVarillaMm).coerceAtLeast(100),
                    separacionMm = o.optInt("separacion_mm", d.separacionMm).coerceAtLeast(0),
                    soldaduraMm = o.optInt("soldadura_mm", d.soldaduraMm).coerceAtLeast(0),
                    retazoUtilMm = o.optInt("retazo_util_mm", d.retazoUtilMm).coerceAtLeast(0),
                )
            }
        }
    }

    /** [ventana] = N° de la ventana en el pedido; [copia] = 1..cantidad cuando la ventana se repite. */
    data class PiezaAsignada(val ventana: Int, val copia: Int, val etiqueta: String, val largoMm: Int)

    data class Varilla(val piezas: List<PiezaAsignada>, val largoMm: Int, val separacionMm: Int) {
        /** Material consumido: piezas + un corte entre cada par. */
        val usadoMm: Int get() = piezas.sumOf { it.largoMm } + separacionMm * (piezas.size - 1).coerceAtLeast(0)
        val sobranteMm: Int get() = largoMm - usadoMm
    }

    data class Grupo(
        val sistema: String,
        val perfil: String,
        val color: String,
        val varillas: List<Varilla>,
        /** Piezas más largas que la varilla (no se pueden cortar; hay que revisar medidas o el largo de varilla). */
        val sinCabida: List<PiezaAsignada>,
        val largoVarillaMm: Int,
        val retazoUtilMm: Int,
        /** Mínimo teórico de varillas (cota inferior) para saber qué tan cerca del óptimo quedó. */
        val minimoTeorico: Int,
    ) {
        val clave: String get() = "$perfil · $sistema · $color"
        val piezasTotal: Int get() = varillas.sumOf { it.piezas.size }
        val largoPiezasMm: Long get() = varillas.sumOf { v -> v.piezas.sumOf { it.largoMm }.toLong() }
        val largoCompradoMm: Long get() = varillas.size.toLong() * largoVarillaMm
        /** Sobrantes chicos (< retazo útil): material que se bota. */
        val desperdicioMm: Long get() = varillas.sumOf { v -> (v.largoMm - v.piezas.sumOf { it.largoMm }).let { s -> if (v.sobranteMm >= retazoUtilMm) 0 else s }.toLong() }
        /** Retazos grandes que se pueden reutilizar. */
        val retazos: List<Int> get() = varillas.map { it.sobranteMm }.filter { it >= retazoUtilMm }.sortedDescending()
        val desperdicioPct: Double get() = if (largoCompradoMm == 0L) 0.0 else desperdicioMm * 100.0 / largoCompradoMm
        val aprovechamientoPct: Double get() = if (largoCompradoMm == 0L) 0.0 else largoPiezasMm * 100.0 / largoCompradoMm
    }

    data class Plan(val parametros: Parametros, val grupos: List<Grupo>) {
        val varillasTotal: Int get() = grupos.sumOf { it.varillas.size }
        val desperdicioPct: Double get() {
            val comprado = grupos.sumOf { it.largoCompradoMm }
            return if (comprado == 0L) 0.0 else grupos.sumOf { it.desperdicioMm } * 100.0 / comprado
        }
        val aprovechamientoPct: Double get() {
            val comprado = grupos.sumOf { it.largoCompradoMm }
            return if (comprado == 0L) 0.0 else grupos.sumOf { it.largoPiezasMm } * 100.0 / comprado
        }
        val hayErrores: Boolean get() = grupos.any { it.sinCabida.isNotEmpty() }
    }

    /** Plan de corte de un pedido. [colorDe] devuelve el color de una ventana (vacío = sin color elegido). */
    fun planificar(items: List<OrderItem>, params: Parametros = Parametros()): Plan {
        val porGrupo = linkedMapOf<Triple<String, String, String>, MutableList<PiezaAsignada>>()
        // El orden de los perfiles en el listado es el del proceso de fabricación
        val orden = listOf("Marco", "Imposta", "Hoja", "Hoja puerta", "Junquillo", "Barra de vidrio")
        for (item in items) {
            val sistema = item.opciones.sistemaVentana ?: "(sin sistema)"
            val color = item.opciones.color ?: "(sin color)"
            val piezas = CalculadoraPrecios.piezasCorte(item, params.soldaduraMm)
            val cant = maxOf(1, item.opciones.cantidad)
            for (copia in 1..cant) for (p in piezas) {
                porGrupo.getOrPut(Triple(p.perfil, sistema, color)) { mutableListOf() }
                    .add(PiezaAsignada(item.index, copia, p.etiqueta, p.largoMm))
            }
        }
        val grupos = porGrupo.entries
            .sortedWith(compareBy({ orden.indexOf(it.key.first).let { i -> if (i < 0) 99 else i } }, { it.key.second }, { it.key.third }))
            .map { (k, piezas) -> empaquetar(k.second, k.first, k.third, piezas, params) }
        return Plan(params, grupos)
    }

    /** Reparte las piezas en varillas. Determinista (semilla fija) para que el mismo pedido dé siempre la misma guía. */
    fun empaquetar(sistema: String, perfil: String, color: String, piezas: List<PiezaAsignada>, p: Parametros): Grupo {
        val (cabe, sinCabida) = piezas.partition { it.largoMm <= p.largoVarillaMm }
        val cap = p.largoVarillaMm + p.separacionMm          // cada pieza consume largo + separación
        val consumo = { x: PiezaAsignada -> x.largoMm + p.separacionMm }
        val minimo = if (cabe.isEmpty()) 0 else Math.ceil(cabe.sumOf { consumo(it).toLong() } / cap.toDouble()).toInt()

        var mejor: List<MutableList<PiezaAsignada>>? = null
        var mejorClave: Pair<Int, Long>? = null
        val rnd = Random(20260912L)
        val base = cabe.sortedByDescending { it.largoMm }
        for (intento in 0 until 400) {
            val orden = if (intento == 0) base else perturbar(base, rnd)
            val barras = mejorAjuste(orden, cap, consumo)
            // criterio: menos varillas; con empate, la solución con el sobrante concentrado (suma de cuadrados mayor)
            val concentracion = barras.sumOf { b -> val libre = (cap - b.sumOf { consumo(it) }).toLong(); libre * libre }
            val clave = barras.size to -concentracion
            if (mejorClave == null || clave.first < mejorClave.first || (clave.first == mejorClave.first && clave.second < mejorClave.second)) {
                mejor = barras; mejorClave = clave
            }
            if (barras.size == minimo && intento >= 40) break
        }
        val varillas = (mejor ?: emptyList()).map { Varilla(it.sortedByDescending { x -> x.largoMm }, p.largoVarillaMm, p.separacionMm) }
            .sortedWith(compareByDescending<Varilla> { it.usadoMm }.thenBy { it.piezas.first().ventana })
        return Grupo(sistema, perfil, color, varillas, sinCabida, p.largoVarillaMm, p.retazoUtilMm, minimo)
    }

    private fun mejorAjuste(orden: List<PiezaAsignada>, cap: Int, consumo: (PiezaAsignada) -> Int): List<MutableList<PiezaAsignada>> {
        val barras = mutableListOf<MutableList<PiezaAsignada>>()
        val libre = mutableListOf<Int>()
        for (pz in orden) {
            val c = consumo(pz)
            var idx = -1
            var menor = Int.MAX_VALUE
            for (i in libre.indices) if (libre[i] >= c && libre[i] - c < menor) { menor = libre[i] - c; idx = i }
            if (idx < 0) { barras += mutableListOf(pz); libre += cap - c } else { barras[idx].add(pz); libre[idx] -= c }
        }
        return barras
    }

    /** Variación del orden decreciente: intercambia piezas de longitud parecida para explorar otras combinaciones. */
    private fun perturbar(base: List<PiezaAsignada>, rnd: Random): List<PiezaAsignada> {
        val l = base.toMutableList()
        repeat(maxOf(2, l.size / 6)) {
            val i = rnd.nextInt(l.size)
            val j = (i + 1 + rnd.nextInt(minOf(6, maxOf(1, l.size - 1)))).coerceAtMost(l.size - 1)
            val t = l[i]; l[i] = l[j]; l[j] = t
        }
        return l
    }
}
