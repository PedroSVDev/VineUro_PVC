package com.example.vineuro_pvc.data

/**
 * Aperturas de varias hojas de la original (WinStudio V41.8):
 *  - Corredizas múltiples (OPEN_SLIDE_*): "S:<clave>", p. ej. "S:1R_2L" (pk_e5.b/pk_e5.a).
 *  - Corredizas verticales (OPEN_SLIDE_1X_2U ...): "V:<clave>".
 *  - Puertas francesas (OPEN_FRENCH_*): "F:<clave>", p. ej. "F:LCF_RC" (pk_b5.a).
 *
 * Clave de corrediza = hojas separadas por "_"; cada hoja = capa (1..4) + dirección (L,R,U,D = se mueve, X = fija)
 * + peso opcional ("2d0" = 2.0). Dos hojas consecutivas con la MISMA capa comparten riel (2 perfiles de hoja);
 * con capa distinta, 1 perfil. La hoja se reparte el hueco según su peso.
 */
object AperturasMulti {
    enum class Grupo { CORREDIZA, CORREDIZA_VERT, FRANCESA }

    const val PERFIL_NORMAL_M = 0.062
    const val PERFIL_PUERTA_M = 0.094

    val CORREDIZAS = listOf(
        "1X_2L", "2R_1X", "1R_2L", "2R_1L", "1R_2L_3L", "3R_2R_1L", "1X_2L_3L", "3R_2R_1X",
        "1X_2L_2X", "2X_2R_1X", "1X_2L_2R_1X", "1R_2L_2R_1L", "1X_2L_3L_4L", "4R_3R_2R_1X",
        "1R_2L_3L_4L", "4R_3R_2R_1L", "1R_2L_3L_3R_2R_1L", "1X_2L_3L_3R_2R_1X",
        "1R_2L_3L_4L_4R_3R_2R_1L", "1X_2L_3L_4L_4R_3R_2R_1X",
    )
    val VERTICALES = listOf("1X_2U", "1D_2U", "1D_2U2d0", "1D2d0_2U")
    val FRANCESAS = listOf("LCF_RC", "LC_RCF", "LT_RCF", "LCF_RT")

    /** Todos los ids de mecanismo de las aperturas múltiples, en el orden de la rejilla de la original. */
    val TODAS: List<String> = FRANCESAS.map { "F:$it" } + VERTICALES.map { "V:$it" } + CORREDIZAS.map { "S:$it" }

    fun grupo(mec: String?): Grupo? = when {
        mec == null || mec.length < 3 || mec[1] != ':' -> null
        mec[0] == 'S' -> Grupo.CORREDIZA
        mec[0] == 'V' -> Grupo.CORREDIZA_VERT
        mec[0] == 'F' -> Grupo.FRANCESA
        else -> null
    }

    fun esMulti(mec: String?) = grupo(mec) != null

    /**
     * Impostas interiores permitidas según la apertura (pk_r2.z de la original): en las aperturas normales todas;
     * en corredizas y puertas francesas solo las horizontales (1 = x1, 2 = x2); en corredizas verticales solo las verticales (3, 4).
     * [imp]: 0 ninguna, 1 hor. x1, 2 hor. x2, 3 ver. x1, 4 ver. x2.
     */
    fun impostaPermitida(mec: String?, imp: Int): Boolean = when (grupo(mec)) {
        null -> true
        Grupo.CORREDIZA, Grupo.FRANCESA -> imp == 0 || imp == 1 || imp == 2
        Grupo.CORREDIZA_VERT -> imp == 0 || imp == 3 || imp == 4
    }
    fun clave(mec: String) = mec.substring(2)

    fun nombre(mec: String): String = when (grupo(mec)) {
        Grupo.CORREDIZA -> "Corrediza ${clave(mec).replace("_", " · ")}"
        Grupo.CORREDIZA_VERT -> "Corrediza vertical ${clave(mec).replace("_", " · ")}"
        Grupo.FRANCESA -> "Puerta francesa ${clave(mec).replace("_", " · ")}"
        null -> mec
    }

    /**
     * Una hoja. [x],[y],[ancho],[alto] = VIDRIO de la hoja en coordenadas del hueco (m, origen arriba-izquierda);
     * el contorno de la hoja es el vidrio + [perfil] por lado. [capa]/[dir] para dibujar.
     */
    data class Hoja(
        val x: Double, val y: Double, val ancho: Double, val alto: Double, val perfil: Double,
        val movil: Boolean, val manija: Boolean, val capa: Int, val dir: Char,
    ) {
        val perimetroExterior: Double get() = 2 * ((ancho + 2 * perfil) + (alto + 2 * perfil))
        val anchoExterior: Double get() = ancho + 2 * perfil
        val altoExterior: Double get() = alto + 2 * perfil
    }

    private data class Tok(val capa: Int, val dir: Char, val peso: Double)

    private fun parsear(clave: String): List<Tok> = clave.split("_").filter { it.length >= 2 }.map { t ->
        Tok(t[0].digitToIntOrNull() ?: 1, t[1], t.substring(2).replace('d', '.').toDoubleOrNull() ?: 1.0)
    }

    /** Hojas del mecanismo [mec] dentro de un hueco de [huecoAncho] x [huecoAlto] (m). */
    fun hojas(mec: String, huecoAncho: Double, huecoAlto: Double, puerta: Boolean = false, perfil: Double? = null): List<Hoja> {
        val g = grupo(mec) ?: return emptyList()
        val f8 = perfil ?: if (puerta) PERFIL_PUERTA_M else PERFIL_NORMAL_M
        val clave = clave(mec)
        return if (g == Grupo.FRANCESA) francesa(clave, huecoAncho, huecoAlto, f8) else corredizas(parsear(clave), g == Grupo.CORREDIZA_VERT, huecoAncho, huecoAlto, f8)
    }

    private fun corredizas(t: List<Tok>, vertical: Boolean, ancho: Double, alto: Double, f8: Double): List<Hoja> {
        if (t.isEmpty()) return emptyList()
        val apilado = if (vertical) alto else ancho
        val cruzado = if (vertical) ancho else alto
        val mismaCapaPrev = t.indices.map { i -> i > 0 && t[i - 1].capa == t[i].capa }
        val capaDistintaPrev = t.indices.map { i -> i > 0 && t[i - 1].capa != t[i].capa }
        val mismaCapaSig = t.indices.map { i -> i < t.size - 1 && t[i + 1].capa == t[i].capa }
        val total = apilado - ((capaDistintaPrev.count { it } + mismaCapaPrev.count { it } * 2 + 2) * f8)
        val unidad = (total / t.sumOf { it.peso }).coerceAtLeast(0.0)
        // Manijas (pk_e5.b.d)
        val manija = BooleanArray(t.size)
        if (t.first().dir == 'R' || t.first().dir == 'D') manija[0] = true
        if (t.last().dir == 'L' || t.last().dir == 'U') manija[t.size - 1] = true
        for (i in t.indices) {
            if (mismaCapaPrev[i] && t[i].dir != 'X') manija[i] = true
            if (mismaCapaSig[i] && t[i + 1].dir == 'X') manija[i] = true
        }
        var previos = 0.0
        return t.indices.map { i ->
            val nE = (0..i).count { mismaCapaPrev[it] }
            val nJ = (0..i).count { capaDistintaPrev[it] }
            val inicio = (nE * 2 + 1 + nJ) * f8 + previos
            val largo = t[i].peso * unidad
            previos += largo
            val across = (cruzado - 2 * f8).coerceAtLeast(0.0)
            Hoja(
                x = if (vertical) f8 else inicio, y = if (vertical) inicio else f8,
                ancho = if (vertical) across else largo, alto = if (vertical) largo else across,
                perfil = f8, movil = t[i].dir != 'X', manija = manija[i], capa = t[i].capa, dir = t[i].dir,
            )
        }
    }

    /** Dos hojas de (hueco + 13 mm por lado)/2 de ancho; la "F" es la hoja pasiva y la otra lleva la manija. */
    private fun francesa(clave: String, ancho: Double, alto: Double, f8: Double): List<Hoja> {
        val solape = 0.013
        val anchoHoja = (ancho + 2 * solape) / 2
        val altoHoja = alto + 2 * solape
        val izqPasiva = clave.startsWith("LCF")
        fun hoja(izq: Boolean, activa: Boolean) = Hoja(
            x = (if (izq) -solape else -solape + anchoHoja) + f8, y = -solape + f8,
            ancho = (anchoHoja - 2 * f8).coerceAtLeast(0.0), alto = (altoHoja - 2 * f8).coerceAtLeast(0.0), perfil = f8,
            movil = true, manija = activa, capa = 1, dir = if (izq) 'R' else 'L',
        )
        return listOf(hoja(true, !izqPasiva), hoja(false, izqPasiva))
    }

    /** ¿La hoja de la derecha (o la izquierda) de la francesa es la basculante (T = oscilobatiente)? */
    fun francesaBasculante(clave: String): Char? = when {
        clave.endsWith("_RT") -> 'R'
        clave.startsWith("LT_") -> 'L'
        else -> null
    }

    /** Hojas móviles (cuentan para el kit de herraje; en la francesa el kit es uno por ventana). */
    fun kits(mec: String, hojas: List<Hoja>): Int = if (grupo(mec) == Grupo.FRANCESA) 1 else hojas.count { it.movil }

    fun manijas(mec: String, hojas: List<Hoja>): Int = if (grupo(mec) == Grupo.FRANCESA) 1 else hojas.count { it.manija }

    /** Imposta francesa: perfil central del largo de la hoja (pk_s3.d.M: V x alto de la primera hoja). */
    fun impostaFrancesaM(mec: String, hojas: List<Hoja>): Double =
        if (grupo(mec) == Grupo.FRANCESA) hojas.firstOrNull()?.altoExterior ?: 0.0 else 0.0
}
