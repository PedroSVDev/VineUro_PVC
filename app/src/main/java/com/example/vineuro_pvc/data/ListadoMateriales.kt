package com.example.vineuro_pvc.data

/**
 * Listado consolidado de materiales de un pedido (RF11): perfiles (varillas), vidrios, junta de caucho,
 * hojas por tipo de apertura y mosquiteros. Las cantidades salen de las mismas reglas que el precio.
 */
object ListadoMateriales {

    data class LineaVidrio(val tipo: String, val anchoMm: Int, val altoMm: Int, val sandwich: Boolean, val cantidad: Int) {
        val areaM2: Double get() = anchoMm / 1000.0 * altoMm / 1000.0 * cantidad
    }

    data class Listado(
        val vidrios: List<LineaVidrio>,
        val cauchoM: Double,
        val hojasPorApertura: Map<String, Int>,
        val mosquiteros: Int,
        val ventanas: Int,
        val unidades: Int,
        val areaM2: Double,
    ) {
        val vidrioM2: Double get() = vidrios.filter { !it.sandwich }.sumOf { it.areaM2 }
        val sandwichM2: Double get() = vidrios.filter { it.sandwich }.sumOf { it.areaM2 }
    }

    fun nombreApertura(mec: String) = when (mec) {
        "1", "3" -> "Corredera"
        "2", "9" -> "Proyectante / oscilante (horizontal)"
        "10" -> "Hoja fija"
        "4", "6" -> "Abatible (practicable)"
        "7", "8" -> "Oscilobatiente"
        else -> if (AperturasMulti.esMulti(mec)) AperturasMulti.nombre(mec) else "Otra ($mec)"
    }

    fun generar(items: List<OrderItem>): Listado {
        val vid = linkedMapOf<List<Any>, Int>()
        var caucho = 0.0
        val hojas = linkedMapOf<String, Int>()
        var mosq = 0
        var unidades = 0
        var area = 0.0
        for (it in items) {
            val cant = maxOf(1, it.opciones.cantidad)
            unidades += cant
            area += it.areaM2 * cant
            val q = CalculadoraPrecios.cantidades(it)
            caucho += q.cauchoM * cant
            mosq += q.panosMosquitero * cant
            val tipo = it.opciones.tipoCristal ?: "(sin tipo de cristal)"
            for (v in CalculadoraPrecios.piezasVidrio(it)) {
                val k = listOf(if (v.sandwich) "Panel sándwich" else tipo, v.anchoMm, v.altoMm, v.sandwich)
                vid[k] = (vid[k] ?: 0) + cant
            }
            for (h in q.hojasDetalle) {
                val n = if (h.esPuerta) "Puerta" else nombreApertura(h.mecanismo)
                hojas[n] = (hojas[n] ?: 0) + cant
            }
        }
        val lineas = vid.map { (k, n) -> LineaVidrio(k[0] as String, k[1] as Int, k[2] as Int, k[3] as Boolean, n) }
            .sortedWith(compareBy({ it.sandwich }, { it.tipo }, { -it.anchoMm }, { -it.altoMm }))
        return Listado(lineas, caucho, hojas, mosq, items.size, unidades, area)
    }
}
