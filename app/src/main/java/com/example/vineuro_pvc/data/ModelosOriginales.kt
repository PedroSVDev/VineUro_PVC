package com.example.vineuro_pvc.data

/**
 * Los 63 modelos de ventana del selector de la original (WinStudio V41.8, pk_d4.i), en el mismo orden de su rejilla,
 * con el mismo tamaño inicial y con su estructura exacta, que se decodifica de la misma "fórmula" que usa la original
 * (pk_i2.b / pk_i2.f):
 *
 *  - "werABC"   columnas A | B | C (divisiones verticales);  "horAB"   filas A / B (divisiones horizontales).
 *  - "(n)" tras una letra = peso relativo de esa parte (por defecto 1): "horA(2)B" -> A mide el doble que B.
 *  - "_Aab(2)c" = la parte A se divide, en el sentido contrario, en a, b (peso 2) y c.
 *  - "block_o(8,13:9)Q(9,)_A_horAab(4)" = ventana con bloques "o" (ventana baja: 13:9 -> 13/(13+9) de la altura) y "Q" (altura
 *    completa), de ancho relativo 8 y 9; cada bloque lleva su propia fórmula. El hueco bajo una "o" queda vacío (ventana en L / T).
 */
object ModelosOriginales {
    class M(val id: String, val formula: String, val anchoMm: Int, val altoMm: Int)

    /** Orden de la rejilla de la original (dialog__select_win_type_v2). */
    val TODOS: List<M> = listOf(
        M("A", "A", 1500, 1500),
        M("werAB", "werAB", 1500, 1500),
        M("werABC", "werABC", 1800, 1500),
        M("werABCD", "werABCD", 1960, 1600),
        M("horAB", "horAB", 1500, 1500),
        M("horABC", "horABC", 1500, 1800),
        M("oQ", "block_o(8,13:9)Q(9,)", 1700, 2200),
        M("oQo", "block_o(6,13:9)Q(8,)o(6,13:9)", 2000, 2200),
        M("Qo", "block_Q(9,)o(8,13:9)", 1700, 2200),
        M("oQ_A_horAab", "block_o(8,16:9)Q(9,)_A_horAab(4)", 1700, 2500),
        M("oQo_A_horAB_A", "block_o(6,15:10)Q(8)o(6,15:10)_A_horAB(4)_A", 2000, 2500),
        M("Qo_horAB_A", "block_Q(8,)o(9,16:9)_horAB(4)_A", 1700, 2500),
        M("oQ_Aab_A", "block_o(14,16:9)Q(9,)_Aab_A", 2300, 2500),
        M("oQo_Aab_A_Aab", "block_o(10,16:9)Q(8,)o(10,16:9)_Aab_A_Aab", 2800, 2500),
        M("Qo_A_Aab", "block_Q(9,)o(14,16:9)_A_Aab", 2300, 2500),
        M("oQ_horAab_A", "block_o(14,16:9)Q(9,)_horAab(2.2)_A", 2300, 2500),
        M("oQo_horAab_A_horAab", "block_o(10,16:9)Q(8)o(10,16:9)_horAab(2.2)_A_horAab(2.2)", 2800, 2500),
        M("Qo_A_horAab", "block_Q(9,)o(14,16:9)_A_horAab(2.2)", 2300, 2500),
        M("horABCD", "horABCD", 1500, 1800),
        M("werABC_Aab", "werABC_Aab(2)", 1500, 1500),
        M("werABC_Cab", "werABC_Cab(2)", 1500, 1500),
        M("werAB_Aabc", "werAB_Aab(2)c", 1500, 2000),
        M("werABC_Aabc_Cabc", "werABC_Aabc_Cabc", 1500, 1500),
        M("werAB_Babc", "werAB_Bab(2)c", 1500, 2000),
        M("horAB_Aab", "horA(2)B_Aab", 1500, 1500),
        M("horAB_Aabc", "horA(2)B_Aabc", 1500, 1500),
        M("horAB_Aabcd", "horA(2)B_Aabcd", 1800, 1500),
        M("horAB_Aabcde", "horA(2)B_Aabcde", 2100, 1500),
        M("horAB_Aabcde_Bab", "horA(2)B_Aabcde_Bab", 2100, 1500),
        M("horAB_Aabcde_Babc", "horA(2)B_Aabcde_Babc", 2100, 1500),
        M("horAB_Bab", "horAB(2)_Bab", 1500, 1500),
        M("werAB_Aab", "werAB_Aab(2)", 1500, 1500),
        M("werAB_Bab", "werAB_Bab(2)", 1500, 1500),
        M("horAB_Aab_Babc", "horAB(2)_Aab_Babc", 1800, 1800),
        M("werABC_Bab", "werABC_Bab(2)", 1500, 1500),
        M("werABC_Aab_Bab_Cab", "werABC_Aab(2)_Bab(2)_Cab(2)", 1500, 1500),
        M("werAB_Aab_Bab", "werAB_Aab(2)_Bab(2)", 1500, 1800),
        M("werABC_Aab_Cab", "werABC_Aab(2)_Cab(2)", 1500, 1500),
        M("horAB_Babc", "horAB(2)_Babc", 1800, 1800),
        M("werABCDE", "werABCDE", 2250, 1600),
        M("werABCDEF", "werABCDEF", 2700, 1600),
        M("werABCDEFG", "werABCDEFG", 3150, 1600),
        M("werABCD_Aab_Bab_Cab_Dab", "werABCD_Aab(2)_Bab(2)_Cab(2)_Dab(2)", 2000, 1500),
        M("werABCDE_Aab_Bab_Cab_Dab_Eab", "werABCDE_Aab(2)_Bab(2)_Cab(2)_Dab(2)_Eab(2)", 2500, 1500),
        M("werABCDEF_Aab_Bab_Cab_Dab_Eab_Fab", "werABCDEF_Aab(2)_Bab(2)_Cab(2)_Dab(2)_Eab(2)_Fab(2)", 3000, 1500),
        M("werABCD_Aabc_Babc_Cabc_Dabc", "werABCD_Aab(2)c_Bab(2)c_Cab(2)c_Dab(2)c", 2800, 2500),
        M("werABCDE_Aabc_Babc_Cabc_Dabc_Eabc", "werABCDE_Aab(2)c_Bab(2)c_Cab(2)c_Dab(2)c_Eab(2)c", 3500, 2500),
        M("werABCDEF_Aabc_Babc_Cabc_Dabc_Eabc_Fabc", "werABCDEF_Aab(2)c_Bab(2)c_Cab(2)c_Dab(2)c_Eab(2)c_Fab(2)c", 4200, 2500),
        M("horAB_Babcd", "horAB(2)_Babcd", 2400, 1800),
        M("horAB_Babcde", "horAB(3)_Babcde", 3000, 2400),
        M("horAB_Babcdef", "horAB(3)_Babcdef", 3600, 2400),
        M("horAB_Aab_Babcd", "horAB(2)_Aab_Babcd", 2400, 1800),
        M("horAB_Aabc_Babcd", "horAB(2)_Aabc_Babcd", 2400, 1800),
        M("horAB_Aabc_Babcde", "horAB(2)_Aabc_Babcde", 2700, 1800),
        M("horAB_Babcdefg", "horAB(3)_Babcdefg", 4200, 2400),
        M("werAB_Aabc_Babc", "werAB_Aab(2)c_Bab(2)c", 1400, 2500),
        M("horABC_Bab", "horAB(2)C_Bab", 1200, 2400),
        M("werABC_Aabc_Babc_Cabc", "werABC_Aab(2)c_Bab(2)c_Cab(2)c", 2100, 2500),
        M("horABC_Babc", "horAB(2)C_Babc", 1800, 2400),
        M("horABC_Babcd", "horAB(2)C_Babcd", 2400, 2400),
        M("horABC_Babcde", "horAB(2)C_Babcde", 3000, 2400),
        M("horABC_Babcdef", "horAB(2)C_Babcdef", 3600, 2400),
        M("horABC_Babcdefg", "horAB(2)C_Babcdefg", 4200, 2400),
    )

    private val porId = TODOS.associateBy { it.id }
    fun buscar(id: String?): M? = if (id == null) null else porId[id]

    // ===================== ESTRUCTURA (árbol de divisiones) =====================

    sealed class Nodo
    object Hoja : Nodo()
    /** [columnas] = true: hijos uno al lado del otro (divisiones verticales); false: apilados (divisiones horizontales). */
    class Div(val columnas: Boolean, val pesos: List<Double>, val hijos: List<Nodo>) : Nodo()
    /** Bloque de la ventana: [kx] = ancho relativo, [fy] = fracción de la altura que ocupa (1 = completa), [raiz] = su fórmula. */
    class Parte(val kx: Double, val fy: Double, val raiz: Nodo)
    class Estructura(val bloque: Boolean, val partes: List<Parte>)

    private val cache = HashMap<String, Estructura>()

    fun estructura(m: M): Estructura = cache.getOrPut(m.id) {
        if (m.formula.startsWith("block_")) parsearBloque(m.formula.removePrefix("block_"))
        else Estructura(false, listOf(Parte(1.0, 1.0, parsearFormula(m.formula))))
    }

    /** "AB(2)C" -> [(A,1),(B,2),(C,1)]. */
    private fun letras(s: String): List<Pair<Char, Double>> {
        val limpio = s.replace(Regex("\\((.+?)\\)"), "")
        return limpio.map { ch ->
            ch to (s.substringAfter("$ch(", "").substringBefore(")", "1").toDoubleOrNull() ?: 1.0)
        }
    }

    private fun parsearFormula(fs: String): Nodo {
        val horizontal = fs.contains("hor")
        val s = fs.replace("hor", "").replace("ver", "").replace("wer", "")
        val segs = s.split("_")
        val prim = letras(segs[0])
        val subs = segs.drop(1)
        if (prim.size <= 1 && subs.isEmpty()) return Hoja
        val hijos = prim.map { (ch, _) ->
            val seg = subs.firstOrNull { it.contains(ch) }
            if (seg == null) Hoja else {
                val sl = letras(seg.substring(1))
                if (sl.size <= 1) Hoja else Div(columnas = horizontal, pesos = sl.map { it.second }, hijos = sl.map { Hoja })
            }
        }
        return Div(columnas = !horizontal, pesos = prim.map { it.second }, hijos = hijos)
    }

    private fun ratio(s: String): Double {
        if (!s.contains(':')) return s.toDoubleOrNull() ?: 1.0
        val p = s.split(":").map { it.toDoubleOrNull() ?: 1.0 }
        return p[0] / (p[1] + p[0])
    }

    private fun parsearBloque(body: String): Estructura {
        val cabeza = body.substringBefore("_")
        val resto = if (body.contains("_")) body.substringAfter("_") else ""
        val toks = Regex("([oQ])\\(([^)]*)\\)").findAll(cabeza).toList()
        var segs = resto.split("_").filter { it.isNotBlank() }
        if (segs.size != toks.size) segs = toks.map { "A" }
        val partes = toks.mapIndexed { i, t ->
            val v = t.groupValues[2].split(",")
            val kx = v.getOrNull(0)?.toDoubleOrNull() ?: 1.0
            val fy = if (t.groupValues[1] == "o") ratio(v.getOrNull(1) ?: "1") else 1.0
            val seg = segs[i]
            val pref = Regex("hor|ver|wer").find(seg)?.value?.take(3) ?: ""
            val norm = pref + seg.replace(pref, "").uppercase().replace("AA", "A")
            Parte(kx, fy, parsearFormula(norm))
        }
        return Estructura(true, partes)
    }

    // ===================== SECCIONES EDITABLES =====================

    /** Número de secciones de ancho / alto que se pueden editar (las del primer nivel de la estructura). */
    fun nAncho(m: M): Int {
        val e = estructura(m)
        if (e.bloque) return e.partes.size
        val r = e.partes[0].raiz
        return if (r is Div && r.columnas) r.hijos.size else 1
    }

    fun nAlto(m: M): Int {
        val e = estructura(m)
        if (e.bloque) return 1
        val r = e.partes[0].raiz
        return if (r is Div && !r.columnas) r.hijos.size else 1
    }

    /** Medidas iniciales de las secciones para un tamaño total [ancho] x [alto] (mm). */
    fun seccionesPorDefecto(m: M, ancho: Float, alto: Float): Pair<FloatArray, FloatArray> {
        val e = estructura(m)
        val anchos: FloatArray; val altos: FloatArray
        if (e.bloque) {
            val s = e.partes.sumOf { it.kx }
            anchos = FloatArray(e.partes.size) { (e.partes[it].kx / s * ancho).toFloat() }
            altos = floatArrayOf(alto)
        } else {
            val r = e.partes[0].raiz
            if (r is Div) {
                val s = r.pesos.sum()
                val v = FloatArray(r.pesos.size) { (r.pesos[it] / s).toFloat() }
                if (r.columnas) { anchos = FloatArray(v.size) { v[it] * ancho }; altos = floatArrayOf(alto) }
                else { altos = FloatArray(v.size) { v[it] * alto }; anchos = floatArrayOf(ancho) }
            } else { anchos = floatArrayOf(ancho); altos = floatArrayOf(alto) }
        }
        return anchos to altos
    }

    // ===================== NOMBRES =====================

    fun nombre(m: M): String {
        val e = estructura(m)
        if (e.bloque) {
            val tok = Regex("([oQ])\\(").findAll(m.formula.removePrefix("block_").substringBefore("_")).joinToString("+") { it.groupValues[1] }
            val extra = e.partes.count { it.raiz !is Hoja }
            return "Bloque $tok" + (if (extra > 0) " con divisiones" else "")
        }
        val r = e.partes[0].raiz
        if (r !is Div) return "1 paño"
        val n = r.hijos.size
        val partes = r.hijos.withIndex().filter { it.value is Div }
        val base = "$n paneles " + if (r.columnas) "V." else "H."
        if (partes.isEmpty()) return base
        return base + " · " + partes.joinToString(" ") { (i, nodo) -> "${'A' + i}÷${(nodo as Div).hijos.size}" }
    }

    // ===================== PLANO (rectángulos, impostas y apoyos en el marco) =====================

    class Celda(val x0: Double, val y0: Double, val x1: Double, val y1: Double) {
        var izq = false; var arr = false; var der = false; var aba = false
        /** Índice del bloque (marco propio) al que pertenece; 0 en las ventanas de un solo marco. */
        var bloque = 0
        val ancho get() = x1 - x0
        val alto get() = y1 - y0
    }

    /** Imposta: línea vertical en [pos] (x) entre [desde] y [hasta] (y), u horizontal en [pos] (y) entre x. Medidas en mm. */
    class Division(val vertical: Boolean, val pos: Double, val desde: Double, val hasta: Double) {
        var iniMarco = true; var finMarco = true
        var bloque = 0
        val largo get() = hasta - desde
    }

    /** "Base" de la original (pk_g4.a): un marco rectangular. Una ventana en L / T tiene una base por bloque; las demás, una. */
    class BaseRect(val x0: Double, val y0: Double, val x1: Double, val y1: Double) {
        val ancho get() = x1 - x0
        val alto get() = y1 - y0
    }

    class Plano(val celdas: List<Celda>, val divisiones: List<Division>, val bases: List<BaseRect> = emptyList())

    private fun reparto(pesos: List<Double>, total: Double): DoubleArray {
        val s = pesos.sum().coerceAtLeast(1e-9)
        return DoubleArray(pesos.size) { pesos[it] / s * total }
    }

    private fun escalar(v: FloatArray, n: Int, total: Double): DoubleArray? {
        val s = v.sumOf { it.toDouble() }
        return if (v.size == n && s > 0.0) DoubleArray(n) { v[it] / s * total } else null
    }

    /** Plano de [m] para un tamaño [ancho] x [alto] (mm) con las secciones editables [secW] / [secH]. */
    fun plano(m: M, ancho: Double, alto: Double, secW: FloatArray, secH: FloatArray): Plano {
        val e = estructura(m)
        val celdas = ArrayList<Celda>()
        val divs = ArrayList<Division>()
        val bases = ArrayList<BaseRect>()
        var bq = 0

        fun recorrer(n: Nodo, x0: Double, y0: Double, x1: Double, y1: Double, tamanos: DoubleArray?) {
            when (n) {
                is Hoja -> celdas += Celda(x0, y0, x1, y1).also { it.bloque = bq }
                is Div -> {
                    val total = if (n.columnas) x1 - x0 else y1 - y0
                    val t = tamanos ?: reparto(n.pesos, total)
                    var p = if (n.columnas) x0 else y0
                    for (i in n.hijos.indices) {
                        val q = p + t[i]
                        if (i > 0) divs += (if (n.columnas) Division(true, p, y0, y1) else Division(false, p, x0, x1)).also { it.bloque = bq }
                        if (n.columnas) recorrer(n.hijos[i], p, y0, q, y1, null) else recorrer(n.hijos[i], x0, p, x1, q, null)
                        p = q
                    }
                }
            }
        }

        if (e.bloque) {
            val w = escalar(secW, e.partes.size, ancho) ?: reparto(e.partes.map { it.kx }, ancho)
            var x = 0.0
            for ((i, p) in e.partes.withIndex()) {
                val y1 = p.fy * alto
                // Cada bloque es un marco propio: la unión entre bloques la cubre el perfil de junta (no hay imposta)
                bq = i
                bases += BaseRect(x, 0.0, x + w[i], y1)
                recorrer(p.raiz, x, 0.0, x + w[i], y1, null)
                x += w[i]
            }
        } else {
            bases += BaseRect(0.0, 0.0, ancho, alto)
            val raiz = e.partes[0].raiz
            val tam = (raiz as? Div)?.let { r -> if (r.columnas) escalar(secW, r.hijos.size, ancho) else escalar(secH, r.hijos.size, alto) }
            recorrer(raiz, 0.0, 0.0, ancho, alto, tam)
        }

        fun dentro(b: Int, x: Double, y: Double) = celdas.any { it.bloque == b && x >= it.x0 - 1e-6 && x <= it.x1 + 1e-6 && y >= it.y0 - 1e-6 && y <= it.y1 + 1e-6 }
        val eps = 1.0
        val ks = doubleArrayOf(0.1, 0.3, 0.5, 0.7, 0.9)
        for (c in celdas) {
            c.izq = ks.count { !dentro(c.bloque, c.x0 - eps, c.y0 + it * c.alto) } >= 3
            c.der = ks.count { !dentro(c.bloque, c.x1 + eps, c.y0 + it * c.alto) } >= 3
            c.arr = ks.count { !dentro(c.bloque, c.x0 + it * c.ancho, c.y0 - eps) } >= 3
            c.aba = ks.count { !dentro(c.bloque, c.x0 + it * c.ancho, c.y1 + eps) } >= 3
        }
        for (d in divs) {
            // El extremo de una imposta apoya en el marco si en alguno de sus dos lados ya no hay ventana
            if (d.vertical) {
                d.iniMarco = !dentro(d.bloque, d.pos - eps, d.desde - eps) || !dentro(d.bloque, d.pos + eps, d.desde - eps)
                d.finMarco = !dentro(d.bloque, d.pos - eps, d.hasta + eps) || !dentro(d.bloque, d.pos + eps, d.hasta + eps)
            } else {
                d.iniMarco = !dentro(d.bloque, d.desde - eps, d.pos - eps) || !dentro(d.bloque, d.desde - eps, d.pos + eps)
                d.finMarco = !dentro(d.bloque, d.hasta + eps, d.pos - eps) || !dentro(d.bloque, d.hasta + eps, d.pos + eps)
            }
        }
        return Plano(celdas, divs, bases)
    }
}
