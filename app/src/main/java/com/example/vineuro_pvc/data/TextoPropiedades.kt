package com.example.vineuro_pvc.data

/**
 * Lista de propiedades de una ventana tal como se imprime en "PDF con los detalles":
 * categorías (encabezado) y, debajo, lo elegido. Orden:
 * sistema, cristal, umbrales exteriores, umbrales interiores, herrajes, extras, trabajos, mosquitero,
 * laminación interior, laminación exterior, color y "adiciones por tamaño" (perfiles).
 * Los planes de financiación no se imprimen (solo se muestran cuando falta material).
 */
object TextoPropiedades {
    data class Categoria(val titulo: String, val lineas: List<String>)

    private val LADOS = listOf("izquierda", "arriba", "abajo", "derecha")

    fun categorias(item: OrderItem): List<Categoria> {
        val op = item.opciones
        val res = mutableListOf<Categoria>()
        fun uno(titulo: String, v: String?) { if (!v.isNullOrBlank()) res += Categoria(titulo, listOf(v)) }
        fun varios(titulo: String, s: Set<String>) { if (s.isNotEmpty()) res += Categoria(titulo, s.sorted()) }

        uno("Sistema", op.sistemaVentana)
        uno("Cristal", op.tipoCristal)
        varios("Umbral exterior", op.umbralesExternos)
        varios("Umbral interior", op.umbralesInternos)
        uno("Herrajes", op.mueble)
        varios("Extras", op.extras)
        varios("Trabajos", op.trabajos)
        uno("Mosquitero", op.mosquitero)
        uno("Laminación interior", op.laminacionInterna)
        uno("Laminación exterior", op.laminacionExterna)
        uno("Color", op.color)

        // Adiciones por tamaño: perfil del asiento / dorsill / jamba + juntas, huecos y extensores por lado
        val adic = mutableListOf<String>()
        adic += op.adicionales.sorted()
        val nombres = linkedMapOf("juntas" to "Perfiles de juntas", "juntas90" to "Perfiles de juntas 90°",
            "juntas135" to "Perfiles de juntas 135°", "huecos" to "Perfiles huecos")
        val grupos = linkedMapOf<String, MutableList<String>>()
        nombres.values.forEach { grupos[it] = mutableListOf() }
        val ext = linkedMapOf<Int, MutableList<String>>()
        op.perfiles.sorted().forEach { clave ->
            val p = clave.split(":")
            val lado = p.last().toIntOrNull()?.let { LADOS.getOrNull(it) } ?: return@forEach
            when {
                p.size == 3 && p[0] == "extensor" -> p[1].toIntOrNull()?.let { ext.getOrPut(it) { mutableListOf() }.add(lado) }
                p.size == 2 && nombres.containsKey(p[0]) -> grupos[nombres.getValue(p[0])]!!.add(lado)
            }
        }
        grupos.forEach { (t, l) -> if (l.isNotEmpty()) adic += "$t: ${l.joinToString(", ")}" }
        ext.toSortedMap().forEach { (mm, l) -> adic += "Extensor $mm mm: ${l.joinToString(", ")}" }
        if (adic.isNotEmpty()) res += Categoria("Adiciones por tamaño", adic)
        return res
    }
}
