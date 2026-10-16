package com.example.vineuro_pvc.data

/**
 * Reglas de perfiles adicionales por "base" (marco rectangular) de la original: pk_h4.a/b/c/d y pk_j5.b.
 *
 * Cada base lleva, para cada adicional, una regla por lado: NORMAL (vale lo que el usuario marcó), SIEMPRE o NUNCA.
 * En una ventana de un solo marco todo es NORMAL. En las de varios bloques (L / T) la regla depende de la posición de la
 * base y de si es la más alta (pk_h4.d.b): las juntas van SIEMPRE en el lado donde un bloque toca al siguiente, y el
 * asiento / umbral solo van en las bases que no son la más alta (la más alta lleva el dorsill).
 */
object ReglasBases {
    enum class R { N, A, V }

    /** Reglas de una base. Lados en orden: izquierda, arriba, abajo, derecha. */
    class Reglas(
        var umbral: R = R.N, var asiento: R = R.N, var dorsill: R = R.N, var jamba: R = R.N,
        var hueco: Array<R> = arrayOf(R.N, R.N, R.N, R.N),
        var junta: Array<R> = arrayOf(R.N, R.N, R.N, R.N),
        var junta90: Array<R> = arrayOf(R.N, R.N, R.N, R.N),
        var junta135: Array<R> = arrayOf(R.N, R.N, R.N, R.N),
        var extensor: Array<R> = arrayOf(R.N, R.N, R.N, R.N),
    ) {
        fun copia() = Reglas(umbral, asiento, dorsill, jamba, hueco.copyOf(), junta.copyOf(), junta90.copyOf(), junta135.copyOf(), extensor.copyOf())
    }

    private val N = R.N; private val A = R.A; private val V = R.V
    private fun l(a: R, b: R, c: R, d: R) = arrayOf(a, b, c, d)

    // Preajustes de pk_h4.c (nombres por su posición en el bloque)
    private val medio: Reglas = Reglas(
        umbral = N, asiento = N, dorsill = V, jamba = V, hueco = l(V, N, N, V),
        junta = l(V, N, V, A), junta90 = l(V, N, V, V), junta135 = l(V, N, V, V), extensor = l(V, N, V, V),
    )
    private val primeroBajo: Reglas = medio.copia().apply {
        jamba = N; junta = l(N, N, V, A); junta90 = l(N, N, V, V); junta135 = l(N, N, V, V); extensor = l(N, N, V, V)
    }
    private val ultimoBajo: Reglas = medio.copia().apply {
        jamba = V; junta = l(V, N, V, N); junta90 = l(V, N, V, N); junta135 = l(V, N, V, N); extensor = l(V, N, V, N)
    }
    private val medioTrasAlta: Reglas = medio.copia().apply { jamba = V; junta = l(A, N, V, A) }
    private val ultimoTrasAlta: Reglas = ultimoBajo.copia().apply { jamba = V; junta = l(A, N, V, N) }
    private val altaMedio: Reglas = medio.copia().apply {
        umbral = V; asiento = V; dorsill = N; jamba = V; hueco = l(N, N, N, N)
        junta = l(V, N, N, V); junta90 = l(V, N, N, V); junta135 = l(V, N, N, V); extensor = l(V, N, N, V)
    }
    private val altaPrimera: Reglas = altaMedio.copia().apply {
        jamba = N; junta = l(N, N, N, V); junta90 = l(N, N, N, V); junta135 = l(N, N, N, V); extensor = l(N, N, N, V)
    }
    private val altaUltima: Reglas = altaMedio.copia().apply {
        jamba = V; junta = l(V, N, N, N); junta90 = l(V, N, N, N); junta135 = l(V, N, N, N); extensor = l(V, N, N, N)
    }

    /** Reglas de la base [idx] de [n] bases, siendo [alta] el índice de la base más alta (pk_h4.d.b). */
    fun reglas(n: Int, idx: Int, alta: Int): Reglas {
        if (n <= 1) return Reglas()
        val ultimo = n - 1
        val noAlta = idx != alta
        val trasAlta = idx == alta + 1
        val r = if (!noAlta) {
            when (idx) { 0 -> altaPrimera; ultimo -> altaUltima; else -> altaMedio }
        } else {
            when {
                idx == 0 -> primeroBajo
                trasAlta && idx == ultimo -> ultimoTrasAlta
                !trasAlta || idx == 0 -> if (idx == ultimo) ultimoBajo else medio
                else -> medioTrasAlta
            }
        }
        return r.copia()
    }

    class BaseDim(val anchoM: Double, val altoM: Double)

    /** Índice de la base más alta (la primera si hay empate), como la original. */
    fun indiceAlta(bases: List<BaseDim>): Int {
        var best = 0
        for (i in 1 until bases.size) if (bases[best].altoM < bases[i].altoM) best = i
        return best
    }

    private fun aplica(sel: Boolean, r: R) = when (r) { R.A -> true; R.V -> false; R.N -> sel }

    /** Suma por lados (izq, arriba, abajo, der): izq/der miden el alto de la base y arriba/abajo su ancho. */
    private fun lados(b: BaseDim, sel: BooleanArray, reglas: Array<R>): Double {
        var t = 0.0
        if (aplica(sel[0], reglas[0])) t += b.altoM
        if (aplica(sel[1], reglas[1])) t += b.anchoM
        if (aplica(sel[2], reglas[2])) t += b.anchoM
        if (aplica(sel[3], reglas[3])) t += b.altoM
        return t
    }

    /** Metros de cada adicional de toda la ventana. Claves de [porTipo]: juntas, juntas90, juntas135, huecos, ext15..ext100. */
    class Metros(
        val asiento: Double, val dorsill: Double, val jamba: Double,
        val porTipo: Map<String, Double>,
        /** Anchos de las bases que llevan umbral (alféizar). */
        val anchosUmbral: List<Double>,
    )

    fun metros(bases: List<BaseDim>, op: OpcionesVentana): Metros {
        val n = bases.size
        val alta = indiceAlta(bases)
        fun sel(tipo: String, med: String? = null): BooleanArray = BooleanArray(4) { lado ->
            val k = if (med == null) "$tipo:$lado" else "$tipo:$med:$lado"
            k in op.perfiles
        }
        val meds = listOf("15", "20", "40", "60", "80", "100")
        var asiento = 0.0; var dorsill = 0.0; var jamba = 0.0
        val tipos = mutableMapOf<String, Double>()
        fun suma(k: String, v: Double) { tipos[k] = (tipos[k] ?: 0.0) + v }
        val umbral = ArrayList<Double>()
        val selAsiento = "perfil del asiento" in op.adicionales
        val selDorsill = "dorsill" in op.adicionales
        val selJamba = "jamba de la esquina" in op.adicionales
        for ((i, b) in bases.withIndex()) {
            val r = reglas(n, i, alta)
            if (r.umbral != V) umbral += b.anchoM
            if (aplica(selAsiento, r.asiento)) asiento += b.anchoM
            if (aplica(selDorsill, r.dorsill)) dorsill += b.anchoM
            if (aplica(selJamba, r.jamba)) jamba += b.altoM
            suma("juntas", lados(b, sel("juntas"), r.junta))
            suma("juntas90", lados(b, sel("juntas90"), r.junta90))
            suma("juntas135", lados(b, sel("juntas135"), r.junta135))
            suma("huecos", lados(b, sel("huecos"), r.hueco))
            for (m in meds) suma("ext$m", lados(b, sel("extensor", m), r.extensor))
        }
        return Metros(asiento, dorsill, jamba, tipos, umbral)
    }
}
