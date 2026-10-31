package com.example.vineuro_pvc.data

/**
 * Asesor de diseño: a partir de lo que el cliente necesita (ambiente, si da a la calle, medidas del vano y qué
 * prioriza) propone UNA ventana elegida solo entre lo que ya existe en la app: los modelos del catálogo y las filas
 * de la tabla de Precios (sistema, cristal, mosquitero). No inventa productos ni precios.
 *
 * [proponerPorReglas] funciona sin Internet y siempre devuelve una propuesta. Una IA externa puede producir la misma
 * [Propuesta]; en ese caso debe pasar por [validar], que descarta cualquier cosa que no esté en el catálogo.
 *
 * Sin dependencias de Android, para probarlo con JUnit.
 */
object AsesorDiseno {

    enum class Ambiente(val etiqueta: String) {
        DORMITORIO("Dormitorio"), SALA("Sala / comedor"), COCINA("Cocina"), BANO("Baño"), OFICINA("Oficina / otro")
    }

    enum class Prioridad(val etiqueta: String) {
        SILENCIO("Menos ruido"), VENTILACION("Ventilación"), SEGURIDAD("Seguridad"), EQUILIBRADO("Equilibrado")
    }

    data class Necesidad(
        val ambiente: Ambiente,
        val daALaCalle: Boolean,
        val anchoMm: Int,
        val altoMm: Int,
        val prioridad: Prioridad,
        /** Lo que dijo el cliente, en texto libre (sin datos personales). */
        val comentario: String = "",
        /** Presupuesto máximo en la moneda de la tabla de Precios; null = sin límite. */
        val presupuesto: Double? = null,
    )

    data class Propuesta(
        /** id del modelo en [ModelosVentana] (sin el prefijo del catálogo). */
        val modeloId: String,
        val anchoMm: Int,
        val altoMm: Int,
        /** índice de paño -> id de apertura (los mismos ids del selector de aperturas). */
        val aperturas: Map<Int, String>,
        val panosConMosquitero: Set<Int>,
        val sistema: String?,
        val cristal: String?,
        val mosquitero: String?,
        val razones: List<String>,
        /** Avisos que el vendedor debe ver (p. ej. medidas poco comunes para el ambiente). */
        val advertencias: List<String> = emptyList(),
        /** "reglas" o "ia": se muestra tal cual al vendedor. */
        val origen: String = "reglas",
        /** "economica", "equilibrada" o "silencio". */
        val etiqueta: String = "equilibrada",
        /** Frase para explicarle la ventana al cliente, en lenguaje sencillo. */
        val textoCliente: String = "",
    )

    val ORDEN_ETIQUETAS = listOf("economica", "equilibrada", "ventilacion", "silencio")

    fun nombreEtiqueta(e: String) = when (e) {
        "economica" -> "Económica"
        "ventilacion" -> "Más ventilación"
        "silencio" -> "Más silencio y seguridad"
        else -> "Recomendada"
    }

    /** Quita correos y números largos (teléfonos, DNI) del comentario antes de enviarlo a la IA; máximo 300 caracteres. */
    fun limpiarComentario(texto: String): String =
        texto.replace(Regex("[\\w.+-]+@[\\w-]+\\.[\\w.]+"), " ").replace(Regex("\\d{6,}"), " ")
            .replace(Regex("\\s+"), " ").trim().take(300)

    /** Lo que identifica a una propuesta: dos con la misma firma son la misma ventana. */
    fun firma(p: Propuesta): List<Any?> = listOf(p.modeloId, p.aperturas.toSortedMap().toList(), p.cristal, p.sistema, p.mosquitero, p.panosConMosquitero.sorted())

    fun precioDe(p: Propuesta, precios: Precios): Double = CalculadoraPrecios.calcular(aOrderItem(p, 1), precios).totalVenta

    /** Avisa en cada propuesta que supera el presupuesto indicado (si lo hay). */
    fun conPresupuesto(lista: List<Propuesta>, n: Necesidad, precios: Precios): List<Propuesta> {
        val tope = n.presupuesto ?: return lista
        return lista.map { p ->
            val exceso = precioDe(p, precios) - tope
            if (exceso > 0.005) p.copy(advertencias = p.advertencias + "Supera tu presupuesto en ${String.format(java.util.Locale.US, "%.2f", exceso)}.") else p
        }
    }

    /** Explicación breve para el cliente, armada solo con datos de la propuesta. */
    fun textoClienteReglas(p: Propuesta, n: Necesidad): String {
        val operables = p.aperturas.values.filter { it != HOJA_FIJA }.distinct().map { nombreApertura(it) }
        val fijas = p.aperturas.values.count { it == HOJA_FIJA }
        val aperturas = operables.joinToString(" y ") + when {
            fijas == 0 -> ""
            fijas == 1 -> " y un paño fijo"
            else -> " y $fijas paños fijos"
        }
        val silencio = n.daALaCalle || n.prioridad == Prioridad.SILENCIO
        return buildString {
            append("Para su ${n.ambiente.etiqueta.lowercase().substringBefore(" /")} le proponemos una ventana de ${p.anchoMm} × ${p.altoMm} mm con $aperturas")
            p.cristal?.let { append(" y cristal $it") }
            append(".")
            if (n.ambiente == Ambiente.BANO && p.cristal != null && esPrivacidad(p.cristal)) append(" El cristal no deja ver hacia adentro.")
            else if (silencio && p.cristal != null && capas(p.cristal) > 1) append(" El cristal de varias capas ayuda a reducir el ruido.")
            if (p.mosquitero != null) append(" Incluye mosquitero.")
        }
    }

    /**
     * Versión más barata de la misma ventana: prueba todas las combinaciones de sistema y cristal de tu tabla y deja la
     * de menor precio (en un baño, solo con cristal de privacidad si existe), sin mosquitero. El precio lo decide la app.
     */
    fun abaratar(p: Propuesta, n: Necesidad, precios: Precios): Propuesta {
        val sistemas = precios.ventanas.mapNotNull { it.firstOrNull() }.filter { it.isNotBlank() }.ifEmpty { listOfNotNull(p.sistema) }
        var cristales = precios.cristal.mapNotNull { it.firstOrNull() }.filter { it.isNotBlank() }.ifEmpty { listOfNotNull(p.cristal) }
        if (n.ambiente == Ambiente.BANO && cristales.any { esPrivacidad(it) }) cristales = cristales.filter { esPrivacidad(it) }
        // Menos hojas que se abren = menos herrajes: se deja solo la primera y el resto queda fijo
        val operables = p.aperturas.filter { it.value != HOJA_FIJA }.keys.sorted()
        val unaHoja = operables.size > 1
        val aperturas = if (unaHoja) p.aperturas.mapValues { (i, v) -> if (i == operables.first()) v else HOJA_FIJA } else p.aperturas
        var mejor = p.copy(aperturas = aperturas, panosConMosquitero = emptySet(), mosquitero = null)
        var precioMejor = precioDe(mejor, precios)
        for (si in sistemas) for (cr in cristales) {
            val cand = mejor.copy(sistema = si, cristal = cr)
            val pr = precioDe(cand, precios)
            if (pr < precioMejor - 0.005) { mejor = cand; precioMejor = pr }
        }
        val masSimple = p.cristal != null && mejor.cristal != null && capas(mejor.cristal!!) < capas(p.cristal)
        return mejor.copy(
            etiqueta = "economica",
            razones = listOf("Opción económica: " + (if (unaHoja) "solo una hoja se abre y el resto queda fijo, " else "") +
                "con el sistema y el cristal más baratos de tu tabla y sin mosquitero.") +
                p.razones.filter { r -> !r.lowercase().let { it.contains("cristal") || it.contains("sistema") } }.take(2),
            advertencias = p.advertencias + listOfNotNull(if (masSimple && n.daALaCalle) "Cristal más simple: deja pasar más ruido de la calle." else null),
        ).let { it.copy(textoCliente = textoClienteReglas(it, n)) }
    }

    /** Opción con más hojas que se abren (más aire, más herrajes). Solo tiene sentido si la recomendada deja hojas fijas. */
    fun alternativaVentilacion(base: Propuesta, n: Necesidad, precios: Precios): Propuesta? {
        val panos = base.aperturas.size
        if (panos < 2 || n.ambiente == Ambiente.BANO) return null
        if (base.aperturas.values.any { esCorredera(it) }) return null // las corredizas en los extremos ya ventilan al máximo
        val aperturas = (0 until panos).associateWith { i ->
            when (i) { 0 -> OSCILOBAT_DER; panos - 1 -> OSCILOBAT_IZQ; else -> HOJA_FIJA }
        }
        if (aperturas == base.aperturas) return null
        return base.copy(
            etiqueta = "ventilacion", aperturas = aperturas, panosConMosquitero = emptySet(), mosquitero = null,
            razones = listOf("Opción con ventilación: las dos hojas de los extremos se abren (oscilobatientes), entra más aire; " +
                "lleva más herrajes, cuesta más y deja pasar algo más de ruido que con una sola hoja."),
        ).let { it.copy(textoCliente = textoClienteReglas(it, n)) }
    }

    private fun candidatasPorReglas(n: Necesidad, precios: Precios): List<Propuesta> {
        val base = validar(proponerPorReglas(n, precios), precios) ?: return emptyList()
        val silencio = validar(proponerPorReglas(n.copy(prioridad = Prioridad.SILENCIO), precios), precios)?.copy(etiqueta = "silencio")
        return listOfNotNull(base, abaratar(base, n, precios), alternativaVentilacion(base, n, precios), silencio)
            .map { it.copy(textoCliente = if (it.textoCliente.isBlank()) textoClienteReglas(it, n) else it.textoCliente) }
    }

    /**
     * Deja solo las opciones que de verdad se distinguen de la recomendada (otras aperturas o al menos 10 % de diferencia
     * de precio), completa con las de las reglas si faltan y se queda con tres como máximo. Si ninguna alternativa vale
     * la pena, queda solo la recomendada.
     */
    fun depurar(lista: List<Propuesta>, n: Necesidad, precios: Precios): List<Propuesta> {
        val rec = lista.firstOrNull { it.etiqueta == "equilibrada" } ?: return lista.take(3)
        val precioRec = precioDe(rec, precios)
        fun distinta(p: Propuesta) = p === rec || p.modeloId != rec.modeloId || p.aperturas != rec.aperturas ||
            Math.abs(precioDe(p, precios) - precioRec) >= 0.10 * precioRec
        val prioridad = listOf("equilibrada", "economica", "ventilacion", "silencio")
        val vistos = mutableSetOf<List<Any?>>()
        var res = lista.filter { distinta(it) }.sortedBy { prioridad.indexOf(it.etiqueta) }.filter { vistos.add(firma(it)) }
        if (res.size < 3) {
            for (r in candidatasPorReglas(n, precios).sortedBy { prioridad.indexOf(it.etiqueta) }) {
                if (res.size < 3 && r.etiqueta != "equilibrada" && res.none { it.etiqueta == r.etiqueta } && distinta(r) && vistos.add(firma(r))) res = res + r
            }
        }
        return res.take(3).sortedBy { ORDEN_ETIQUETAS.indexOf(it.etiqueta) }
    }

    /** Recomendada más las alternativas que de verdad cambian algo (ver [depurar]); armadas con las reglas. */
    fun proponerAlternativas(n: Necesidad, precios: Precios): List<Propuesta> = depurar(candidatasPorReglas(n, precios), n, precios)

    const val ANCHO_MIN = 400
    const val ANCHO_MAX = 4200
    const val ALTO_MIN = 400
    const val ALTO_MAX = 2500

    // Ids de apertura (ver el selector de aperturas del diseñador)
    private const val DESLIZ_DER = "1"
    private const val DESLIZ_IZQ = "3"
    private const val PRACTICABLE_DER = "6"
    private const val OSCILOBAT_DER = "8"
    private const val OSCILOBAT_IZQ = "7"
    private const val ABAT_SUPERIOR = "9"
    private const val HOJA_FIJA = "10"

    /** Modelos de columnas simples que usa el asesor: 1, 2, 3 y 4 paños. */
    private val MODELOS_POR_PANOS = mapOf(1 to "A", 2 to "werAB", 3 to "werABC", 4 to "werABCD")

    /** Rango de medidas habituales (mm) de un ambiente y una medida sugerida para empezar. */
    data class MedidasTipicas(val anchoMin: Int, val anchoMax: Int, val altoMin: Int, val altoMax: Int, val sugAncho: Int, val sugAlto: Int)

    fun tipicas(a: Ambiente): MedidasTipicas = when (a) {
        Ambiente.BANO -> MedidasTipicas(400, 1100, ALTO_MIN, 800, 700, 500)
        Ambiente.COCINA -> MedidasTipicas(600, 2000, 500, 1300, 1200, 900)
        Ambiente.DORMITORIO -> MedidasTipicas(800, 2600, 900, 1600, 1500, 1200)
        Ambiente.SALA -> MedidasTipicas(1000, 3600, 1000, 2400, 2000, 1500)
        Ambiente.OFICINA -> MedidasTipicas(800, 3000, 800, 2000, 1500, 1200)
    }

    /** Aviso si las medidas se salen de lo habitual para el ambiente; null si son razonables. */
    fun advertenciaMedidas(n: Necesidad): String? {
        val t = tipicas(n.ambiente)
        if (n.anchoMm in t.anchoMin..t.anchoMax && n.altoMm in t.altoMin..t.altoMax) return null
        val nombre = n.ambiente.etiqueta.lowercase().substringBefore(" /")
        val extra = if (n.ambiente == Ambiente.BANO) " (suelen ser ventanas altas y pequeñas)" else ""
        return "Para $nombre lo habitual es ${t.anchoMin}–${t.anchoMax} × ${t.altoMin}–${t.altoMax} mm$extra; " +
            "${n.anchoMm} × ${n.altoMm} mm se sale de eso."
    }

    /** Nombre legible de un id de apertura. */
    fun nombreApertura(id: String): String = when (id) {
        "1" -> "corredera der."
        "3" -> "corredera izq."
        "6" -> "practicable der."
        "7" -> "oscilobatiente izq."
        "8" -> "oscilobatiente der."
        "9" -> "abatible superior"
        "10" -> "fija"
        else -> "apertura $id"
    }

    /** Paños recomendados para un ancho de vano (1 a 4). */
    fun panosSugeridos(anchoMm: Int): Int = when {
        anchoMm <= 900 -> 1
        anchoMm <= 1800 -> 2
        anchoMm <= 2700 -> 3
        else -> 4
    }

    /** id del modelo de columnas simples con ese número de paños. */
    fun modeloParaPanos(panos: Int): String = MODELOS_POR_PANOS.getValue(panos)

    /** Devuelve un mensaje si las medidas no son utilizables; null si están bien. */
    fun errorMedidas(ancho: Int, alto: Int): String? = when {
        ancho !in ANCHO_MIN..ANCHO_MAX -> "El ancho debe estar entre $ANCHO_MIN y $ANCHO_MAX mm."
        alto !in ALTO_MIN..ALTO_MAX -> "El alto debe estar entre $ALTO_MIN y $ALTO_MAX mm."
        else -> null
    }

    fun proponerPorReglas(n: Necesidad, precios: Precios): Propuesta {
        val razones = mutableListOf<String>()
        val silencio = n.daALaCalle || n.prioridad == Prioridad.SILENCIO

        // ---- Número de paños según el ancho del vano ----
        val panos = panosSugeridos(n.anchoMm)
        razones += "Vano de ${n.anchoMm} × ${n.altoMm} mm: $panos ${if (panos == 1) "paño" else "paños"} para que cada hoja sea manejable."
        if (n.anchoMm > 3600) razones += "El ancho es muy grande; considera dividirlo en dos ventanas."
        if (n.altoMm > 2200) razones += "El alto es considerable; revisa el refuerzo del perfil con tu taller."

        // ---- Aperturas ----
        val (aperturas, razonesApertura) = disenarAperturas(n, panos)
        razones += razonesApertura

        // ---- Cristal ----
        val privacidad = n.ambiente == Ambiente.BANO
        val cristal = elegirCristal(precios, silencio, privacidad)
        if (cristal != null) {
            razones += if (privacidad && esPrivacidad(cristal)) "Cristal \"$cristal\": no deja ver hacia adentro, necesario en un baño."
            else if (silencio && capas(cristal) > 1 && capasMin(precios) < capas(cristal))
                "Cristal \"$cristal\": más capas para reducir el ruido" + (if (n.daALaCalle) " de la calle." else ".")
            else "Cristal \"$cristal\"."
        }

        // ---- Sistema ----
        val sistema = elegirSistema(precios, silencio)
        if (sistema != null) razones += "Sistema \"$sistema\" de tu tabla de precios."

        // ---- Mosquitero (solo en hojas corredizas, que no chocan con la red) ----
        val mosqFila = precios.mosquitera.firstOrNull()?.firstOrNull()
        val conMosq = if (mosqFila != null && (n.prioridad == Prioridad.VENTILACION || n.ambiente == Ambiente.DORMITORIO || n.ambiente == Ambiente.COCINA))
            aperturas.filter { it.value == DESLIZ_DER || it.value == DESLIZ_IZQ }.keys else emptySet()
        if (conMosq.isNotEmpty()) razones += "Mosquitero en las hojas corredizas (ambiente con ventilación frecuente)."

        return Propuesta(
            modeloId = MODELOS_POR_PANOS.getValue(panos), anchoMm = n.anchoMm, altoMm = n.altoMm,
            aperturas = aperturas, panosConMosquitero = conMosq.toSet(), sistema = sistema, cristal = cristal,
            mosquitero = if (conMosq.isNotEmpty()) mosqFila else null, razones = razones,
            advertencias = listOfNotNull(advertenciaMedidas(n), avisoPrivacidad(n, cristal)),
        ).let { it.copy(textoCliente = textoClienteReglas(it, n)) }
    }

    private const val PRIVACIDAD = "esmeril|satin|frost|opac|mate|privacid|translucid|catedral"

    /** Cristal que no deja ver hacia adentro (esmerilado, satinado, opaco…), por su nombre en la tabla de Precios. */
    fun esPrivacidad(nombre: String) = Regex(PRIVACIDAD, RegexOption.IGNORE_CASE).containsMatchIn(nombre)

    private fun avisoPrivacidad(n: Necesidad, cristal: String?): String? =
        if (n.ambiente == Ambiente.BANO && (cristal == null || !esPrivacidad(cristal)))
            "Un baño necesita cristal esmerilado o satinado y no hay uno en tu tabla de Precios; agrégalo para dar privacidad."
        else null

    private fun esCorredera(id: String?) = id == DESLIZ_DER || id == DESLIZ_IZQ

    /** Aperturas lógicas para el ambiente y el número de paños, con el motivo de cada decisión. */
    internal fun disenarAperturas(n: Necesidad, panos: Int): Pair<Map<Int, String>, List<String>> {
        val silencio = n.daALaCalle || n.prioridad == Prioridad.SILENCIO
        val aperturas = mutableMapOf<Int, String>()
        val razones = mutableListOf<String>()
        when {
            n.ambiente == Ambiente.BANO -> {
                aperturas[0] = ABAT_SUPERIOR
                for (i in 1 until panos) aperturas[i] = HOJA_FIJA
                razones += "Baño: abatible superior, ventila sin quedar expuesto y protege la privacidad."
            }
            silencio || n.prioridad == Prioridad.SEGURIDAD -> {
                aperturas[0] = OSCILOBAT_DER
                for (i in 1 until panos) aperturas[i] = HOJA_FIJA
                razones += if (silencio) "Una hoja oscilobatiente y el resto fijas: cierra mejor y deja pasar menos ruido que una corrediza."
                else "Una hoja oscilobatiente y el resto fijas: menos partes móviles y cierre más firme."
            }
            n.prioridad == Prioridad.VENTILACION && panos == 1 -> {
                aperturas[0] = PRACTICABLE_DER
                razones += "Ventilación: hoja practicable que abre por completo."
            }
            panos == 1 -> {
                aperturas[0] = OSCILOBAT_DER
                razones += "Un solo paño: oscilobatiente, ventila abierta o entreabierta."
            }
            else -> {
                aperturas[0] = DESLIZ_DER
                aperturas[panos - 1] = DESLIZ_IZQ
                for (i in 1 until panos - 1) aperturas[i] = HOJA_FIJA
                razones += "Corredizas en los extremos" + (if (panos > 2) " y paños centrales fijos" else "") +
                    (if (n.prioridad == Prioridad.VENTILACION) ": máxima ventilación sin ocupar espacio al abrir." else ": práctica y sin ocupar espacio al abrir.")
            }
        }
        return aperturas to razones
    }

    internal val AFIRMACIONES_SIN_BASE = Regex("calidad.?precio|econ[oó]mic|barat|garant[ií]a|premium|mejor del mercado|duradero|resistente|aislamiento t[eé]rmico", RegexOption.IGNORE_CASE)

    /**
     * Corrige lo que no tiene sentido en una propuesta (de la IA o de quien sea): un baño o una ventana a la calle no
     * llevan corredizas, el baño lleva cristal de privacidad, la ventana silenciosa lleva el cristal de más capas, y el
     * mosquitero solo va en hojas corredizas. Cada cambio queda explicado en las razones.
     */
    fun ajustarLogica(p: Propuesta, n: Necesidad, precios: Precios): Propuesta {
        val panos = MODELOS_POR_PANOS.entries.firstOrNull { it.value == p.modeloId }?.key ?: return p
        val silencio = n.daALaCalle || n.prioridad == Prioridad.SILENCIO
        val bano = n.ambiente == Ambiente.BANO
        val ajustes = mutableListOf<String>()

        // ---- Aperturas ----
        var aperturas = p.aperturas
        val hayCorredera = aperturas.values.any { esCorredera(it) }
        // En un baño solo vale abatible superior y paños fijos; cualquier otra hoja operable se reemplaza
        val banoInvalido = bano && (aperturas.values.any { it != ABAT_SUPERIOR && it != HOJA_FIJA } || aperturas.values.none { it == ABAT_SUPERIOR })
        if (banoInvalido || (hayCorredera && silencio)) {
            aperturas = disenarAperturas(n, panos).first
            ajustes += if (bano) "Ajuste del asesor: en el baño solo va abatible superior y paños fijos: ventila sin quedar expuesto."
            else "Ajuste del asesor: una ventana a la calle cierra mejor con oscilobatiente que con corredizas."
        }

        // ---- Mosquitero: solo donde quedan corredizas; nunca en el baño ----
        var conMosq = p.panosConMosquitero.filter { esCorredera(aperturas[it]) }.toSet()
        if (bano) conMosq = emptySet()
        val mosquitero = if (conMosq.isEmpty()) null else p.mosquitero

        // ---- Cristal ----
        var cristal = p.cristal
        if (bano) {
            val priv = elegirCristal(precios, silencio, privacidad = true)
            if (priv != null && esPrivacidad(priv) && (cristal == null || !esPrivacidad(cristal))) {
                cristal = priv
                ajustes += "Ajuste del asesor: cristal \"$priv\", que no deja ver hacia adentro."
            }
        } else if (silencio && p.etiqueta != "economica") {
            val mejor = elegirCristal(precios, true, privacidad = false)
            if (mejor != null && (cristal == null || capas(mejor) > capas(cristal))) {
                cristal = mejor
                ajustes += "Ajuste del asesor: cristal \"$mejor\", el de más capas de tu tabla, para reducir el ruido."
            }
        }

        // ---- Razones: solo las que describen esta propuesta, sin promesas que la app no puede comprobar ----
        val coherentes = p.razones.filter {
            GeminiAsesor.coherente(it, aperturas.values.toSet()) && !AFIRMACIONES_SIN_BASE.containsMatchIn(it)
        }
        val razones = (if (ajustes.isEmpty()) coherentes else coherentes.take(2) + ajustes)
            .ifEmpty { listOf("Elegido entre las opciones de tu catálogo.") }

        val advertencias = (p.advertencias + listOfNotNull(
            avisoPrivacidad(n, cristal),
            if (silencio && !bano && p.etiqueta == "economica" && cristal != null && capas(cristal) < capasMax(precios) && n.daALaCalle)
                "Cristal más simple: deja pasar más ruido de la calle." else null,
        )).distinct()
        val ajustada = p.copy(
            aperturas = aperturas, panosConMosquitero = conMosq, mosquitero = mosquitero,
            cristal = cristal, razones = razones, advertencias = advertencias,
        )
        // Si se cambió algo, el texto que escribió la IA ya no describe esta ventana: se rehace con los datos reales
        return if (ajustes.isEmpty() && p.textoCliente.isNotBlank()) ajustada else ajustada.copy(textoCliente = textoClienteReglas(ajustada, n))
    }

    /** Cantidad de capas de vidrio que sugiere el nombre ("4-6-4" = 2, "4-6-4-6-4" = 3). */
    private fun capas(nombre: String): Int = Regex("\\d+").findAll(nombre).count().let { (it + 1) / 2 }.coerceAtLeast(1)

    private fun capasMax(precios: Precios): Int = precios.cristal.mapNotNull { it.firstOrNull() }.maxOfOrNull { capas(it) } ?: 1

    private fun capasMin(precios: Precios): Int = precios.cristal.mapNotNull { it.firstOrNull() }.minOfOrNull { capas(it) } ?: 1

    private fun esAcustico(nombre: String) = Regex("acus|acoust|lamin", RegexOption.IGNORE_CASE).containsMatchIn(nombre)

    private fun elegirCristal(precios: Precios, silencio: Boolean, privacidad: Boolean = false): String? {
        val nombres = precios.cristal.mapNotNull { it.firstOrNull() }.filter { it.isNotBlank() }
        if (nombres.isEmpty()) return null
        if (privacidad) {
            val priv = nombres.filter { esPrivacidad(it) }
            if (priv.isNotEmpty()) return priv.maxByOrNull { capas(it) }
        }
        return if (silencio) nombres.maxByOrNull { capas(it) + if (esAcustico(it)) 10 else 0 }
        else nombres.firstOrNull { !esAcustico(it) } ?: nombres.first()
    }

    private fun elegirSistema(precios: Precios, silencio: Boolean): String? {
        val nombres = precios.ventanas.mapNotNull { it.firstOrNull() }.filter { it.isNotBlank() }
        if (nombres.isEmpty()) return null
        return if (silencio) nombres.firstOrNull { esAcustico(it) } ?: nombres.first() else nombres.first()
    }

    /**
     * Revisa una propuesta (venga de donde venga) contra el catálogo y la tabla de Precios. Devuelve la propuesta
     * corregida o null si no hay forma de usarla (p. ej. un modelo que no existe).
     */
    fun validar(p: Propuesta, precios: Precios): Propuesta? {
        if (p.modeloId !in MODELOS_POR_PANOS.values) return null
        if (errorMedidas(p.anchoMm, p.altoMm) != null) return null
        val panos = MODELOS_POR_PANOS.entries.first { it.value == p.modeloId }.key
        val validas = (AperturasMulti.TODAS + listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10")).toSet()
        val aperturas = (0 until panos).associateWith { i -> p.aperturas[i]?.takeIf { it in validas } ?: HOJA_FIJA }
        fun enTabla(valor: String?, filas: List<List<String>>) = valor?.takeIf { v -> filas.any { it.firstOrNull() == v } }
        return p.copy(
            aperturas = aperturas,
            panosConMosquitero = p.panosConMosquitero.filter { it in 0 until panos }.toSet(),
            sistema = enTabla(p.sistema, precios.ventanas) ?: precios.ventanas.firstOrNull()?.firstOrNull(),
            cristal = enTabla(p.cristal, precios.cristal) ?: precios.cristal.firstOrNull()?.firstOrNull(),
            mosquitero = enTabla(p.mosquitero, precios.mosquitera),
        )
    }

    /** Ventana del pedido a partir de una propuesta ya validada. */
    fun aOrderItem(p: Propuesta, index: Int): OrderItem {
        val config = WindowConfigCatalog.catalogo().first { it.id == WindowConfigCatalog.PREFIJO_MODELO + p.modeloId }
        val item = OrderItem.create(index = index, config = config, altoMm = p.altoMm, anchoMm = p.anchoMm)
        item.paneMechanisms.putAll(p.aperturas)
        p.panosConMosquitero.forEach { item.paneMosqNet[it] = true }
        item.opciones.sistemaVentana = p.sistema
        item.opciones.tipoCristal = p.cristal
        item.opciones.mosquitero = p.mosquitero
        return item
    }
}

/** Entrega la ventana propuesta (ya aceptada) al diseñador, que la toma una sola vez. */
object SugerenciaDiseno {
    var pendiente: AsesorDiseno.Propuesta? = null

    fun tomar(): AsesorDiseno.Propuesta? = pendiente.also { pendiente = null }
}
