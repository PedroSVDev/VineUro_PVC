package com.example.vineuro_pvc.data

import org.json.JSONArray
import org.json.JSONObject

/** Fila de extras / trabajos: nombre, precio y unidad elegida (0..5 = pieza, área, perímetro, pendientes, ancho, alto). */
data class FilaSeleccion(val nombre: String, val precio: String, val unidad: Int = 0)

/** Fila de la tabla de colores: nombre y color en formato #RRGGBB. */
data class FilaColor(val nombre: String, val colorHex: String)

/**
 * Todos los datos del apartado "Precios".
 * Las tablas simples se guardan como lista de filas; cada fila es la lista de celdas (la celda 0 es el nombre).
 */
data class Precios(
    val moneda: List<List<String>>,
    val impuesto: List<List<String>>,
    val planes: List<List<String>>,
    val ventanas: List<List<String>>,
    val cristal: List<List<String>>,
    val umbralExt: List<List<String>>,
    val umbralInt: List<List<String>>,
    val muebles: List<List<String>>,
    val extras: List<FilaSeleccion>,
    val empleos: List<FilaSeleccion>,
    val mosquitera: List<List<String>>,
    val laminacion: List<List<String>>,
    val colores: List<FilaColor>,
    val fechaActualizacion: Long = System.currentTimeMillis(),
) {
    companion object {
        /** Columnas numéricas de un sistema de ventanas (las 37 de la original; la 0 es el nombre). */
        const val COLUMNAS_SISTEMA = 37

        /** Completa con "0.00" las filas guardadas antes de ampliar la tabla (solo tenían 9 columnas). */
        fun normalizarSistema(f: List<String>): List<String> =
            if (f.size >= COLUMNAS_SISTEMA + 1) f else f + List(COLUMNAS_SISTEMA + 1 - f.size) { "0.00" }

        private fun ceros(n: Int) = List(n) { "0.00" }
        private fun fila(nombre: String, columnasNumericas: Int) = listOf(nombre) + ceros(columnasNumericas)

        /** Valores iniciales cuando todavía no hay nada guardado (ni en el teléfono ni en la nube). */
        fun porDefecto(): Precios = Precios(
            // Moneda base = dólar (RN01). Para ver otra moneda se cambia "resultados" y el tipo de cambio sale del BCRP.
            moneda = listOf(listOf("US$", "1.00", "US$")),
            impuesto = listOf(listOf("IGV", "18.00")),
            planes = listOf(
                listOf("discount 1%", "1.00", "0.00", "0.00"),
                listOf("discount 3%", "3.00", "0.00", "0.00"),
                listOf("discount 5%", "5.00", "0.00", "0.00"),
            ),
            ventanas = listOf(
                listOf("Custom PVC system 1", "5.75", "5.65", "4.18", "0.00", "0.00", "1.21", "0.22", "0.00", "0.00", "3.75", "4.32", "4.12", "0.00", "21.15", "17.35", "15.85", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.70", "1.53", "5.11", "2.75", "5.20", "0.00", "0.00", "0.29", "0.00", "3.43", "4.80", "4.90", "6.32", "7.25"),
                listOf("Custom PVC system 2", "5.41", "5.31", "4.60", "0.00", "0.00", "1.63", "0.28", "0.00", "0.00", "3.98", "4.21", "4.25", "0.00", "22.41", "17.81", "16.20", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.84", "1.68", "5.20", "2.46", "5.41", "0.00", "0.00", "0.41", "0.00", "3.41", "4.70", "5.21", "7.41", "7.45"),
                listOf("Custom PVC system 3", "5.31", "5.21", "4.24", "0.00", "0.00", "1.58", "0.28", "0.00", "0.00", "3.91", "4.06", "4.05", "0.00", "21.01", "17.41", "15.91", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.81", "1.68", "5.11", "2.21", "5.31", "0.00", "0.00", "0.37", "0.00", "3.30", "4.61", "4.81", "6.41", "7.33"),
                listOf("PVC system A", "5.67", "5.58", "4.81", "0.00", "0.00", "1.67", "0.28", "0.00", "0.00", "4.06", "4.25", "4.32", "0.00", "24.20", "18.51", "16.25", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.87", "1.68", "5.40", "2.85", "5.61", "0.00", "0.00", "0.45", "0.00", "3.72", "4.81", "5.59", "7.30", "7.72"),
                listOf("PVC system B", "5.91", "5.78", "5.22", "0.00", "0.00", "1.85", "0.31", "0.00", "0.00", "4.40", "4.61", "4.60", "0.00", "28.41", "19.71", "17.81", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.88", "1.68", "5.90", "3.01", "5.81", "0.00", "0.00", "0.49", "0.00", "4.05", "5.30", "5.71", "7.60", "7.88"),
                listOf("PVC system C", "5.90", "5.46", "5.22", "0.00", "0.00", "1.43", "0.56", "0.00", "0.00", "4.36", "4.75", "4.45", "0.00", "28.23", "19.89", "17.80", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.75", "1.68", "5.90", "3.21", "5.81", "0.00", "0.00", "0.49", "0.00", "4.25", "5.35", "6.75", "8.85", "9.90"),
            ),
            cristal = listOf(
                listOf("common glass 4-6-4", "9.80", "9.80", "0.00", "0.00", "0.03", "0.04", "0.03", "0.05", "0.00"),
                listOf("common glass 4-6-4-6-4", "9.80", "9.80", "9.80", "0.00", "0.03", "0.04", "0.03", "0.05", "0.00"),
            ),
            umbralExt = listOf(
                listOf("outer sill white 200", "4.26", "0.80"),
                listOf("outer sill white 300", "5.16", "0.86"),
            ),
            umbralInt = listOf(
                listOf("inner sill white 300", "14.72", "0.40"),
                listOf("inner sill white 400", "18.32", "0.46"),
                listOf("marble imitation 300", "15.00", "0.40"),
                listOf("marble imitation 400", "19.00", "0.46"),
            ),
            muebles = listOf(
                listOf("white type 1", "9.66", "13.84", "1.20"),
                listOf("white type 2", "9.90", "16.10", "1.44"),
            ),
            extras = listOf(
                FilaSeleccion("mounting foam", "12.30", 0),
                FilaSeleccion("new_extra_material", "0.00", 0),
                FilaSeleccion("set of screws", "14.20", 0),
            ),
            empleos = listOf(
                FilaSeleccion("delivery", "6.00", 0),
                FilaSeleccion("dismantling", "2.32", 0),
                FilaSeleccion("finishing slopes", "150.00", 3),
                FilaSeleccion("inner sill install", "80.00", 4),
                FilaSeleccion("instal to concrete", "12.48", 0),
                FilaSeleccion("instalation", "13.78", 0),
                FilaSeleccion("outer sill install", "70.00", 4),
            ),
            mosquitera = listOf(
                listOf("mosq. net brown", "3.70", "3.80", "0.08", "0.10", "0.05", "0.05"),
                listOf("mosq. net gray", "3.40", "3.60", "0.08", "0.10", "0.05", "0.05"),
                listOf("mosq. net white", "3.06", "3.30", "0.08", "0.09", "0.05", "0.05"),
            ),
            laminacion = listOf(
                listOf("color bordo", "80.00"),
                listOf("color gray", "80.00"),
                listOf("new_lamination", "0.00"),
                listOf("oak dark", "75.00"),
                listOf("oak light", "75.00"),
            ),
            // Los 18 colores de la tabla original (códigos de su paleta B05, C01, ...) + los propios de VINEURO
            colores = listOf(
                FilaColor("alder", "#fccfae"),
                FilaColor("ash", "#eeba8e"),
                FilaColor("azul oscuro", "#2F4F86"),
                FilaColor("beech", "#daaf96"),
                FilaColor("birch", "#fed8ae"),
                FilaColor("black", "#7e7d7d"),
                FilaColor("blue", "#5c92af"),
                FilaColor("celeste", "#2F86C9"),
                FilaColor("cherry", "#cf7749"),
                FilaColor("ebony", "#785052"),
                FilaColor("green", "#b5cd8e"),
                FilaColor("hickory", "#bb9269"),
                FilaColor("mahogany", "#af652a"),
                FilaColor("maple", "#ffe3a4"),
                FilaColor("oak", "#dca15f"),
                FilaColor("pine", "#ffeebd"),
                FilaColor("poplar", "#ddcd99"),
                FilaColor("red", "#dd7171"),
                FilaColor("walnut", "#dca780"),
                FilaColor("yellow", "#f6dd63"),
            ),
        )
    }

    /**
     * Multiplica por [factor] TODOS los precios (no los porcentajes, ni la moneda, ni el impuesto, ni los colores).
     * Sirve para pasar los precios de una moneda a otra (p. ej. de S/ a US$) sin volver a escribirlos.
     */
    fun convertirPrecios(factor: Double): Precios {
        fun n(s: String): String {
            val v = s.replace(",", ".").trim().toDoubleOrNull() ?: return s
            var t = String.format(java.util.Locale.US, "%.4f", v * factor).trimEnd('0')
            if (t.endsWith(".")) t += "00" else if (t.substringAfter('.').length < 2) t += "0"
            return t
        }
        // Las columnas de precio de cada tabla (la 0 es el nombre); los % de "planes" no se tocan
        fun tabla(t: List<List<String>>, hasta: Int) = t.map { f -> f.mapIndexed { i, c -> if (i in 1..hasta) n(c) else c } }
        return copy(
            ventanas = tabla(ventanas, COLUMNAS_SISTEMA),
            cristal = tabla(cristal, 9),
            umbralExt = tabla(umbralExt, 2),
            umbralInt = tabla(umbralInt, 2),
            muebles = tabla(muebles, 3),
            mosquitera = tabla(mosquitera, 6),
            laminacion = tabla(laminacion, 1),
            extras = extras.map { it.copy(precio = n(it.precio)) },
            empleos = empleos.map { it.copy(precio = n(it.precio)) },
        )
    }

    /** Nombres (primera celda) de una tabla, sin vacíos ni repetidos. Se usa para el menú lateral. */
    fun nombres(tabla: List<List<String>>): List<String> =
        tabla.mapNotNull { it.firstOrNull()?.trim() }.filter { it.isNotEmpty() }.distinct()
}

/** Conversión Precios <-> JSON (columna "data" de SQLite y columna jsonb "data" de Supabase). */
object PreciosJson {

    fun toJson(p: Precios): JSONObject = JSONObject().apply {
        put("moneda", tablaToJson(p.moneda))
        put("impuesto", tablaToJson(p.impuesto))
        put("planes", tablaToJson(p.planes))
        put("ventanas", tablaToJson(p.ventanas))
        put("cristal", tablaToJson(p.cristal))
        put("umbral_ext", tablaToJson(p.umbralExt))
        put("umbral_int", tablaToJson(p.umbralInt))
        put("muebles", tablaToJson(p.muebles))
        put("extras", seleccionToJson(p.extras))
        put("empleos", seleccionToJson(p.empleos))
        put("mosquitera", tablaToJson(p.mosquitera))
        put("laminacion", tablaToJson(p.laminacion))
        put("colores", JSONArray().apply {
            p.colores.forEach { put(JSONObject().put("nombre", it.nombre).put("color", it.colorHex)) }
        })
        put("fecha_actualizacion", p.fechaActualizacion)
    }

    fun fromJson(o: JSONObject): Precios {
        val def = Precios.porDefecto()
        return Precios(
            moneda = tabla(o, "moneda") ?: def.moneda,
            impuesto = tabla(o, "impuesto") ?: def.impuesto,
            planes = tabla(o, "planes") ?: def.planes,
            ventanas = tabla(o, "ventanas")?.map { Precios.normalizarSistema(it) } ?: def.ventanas,
            cristal = tabla(o, "cristal") ?: def.cristal,
            umbralExt = tabla(o, "umbral_ext") ?: def.umbralExt,
            umbralInt = tabla(o, "umbral_int") ?: def.umbralInt,
            muebles = tabla(o, "muebles") ?: def.muebles,
            extras = seleccion(o, "extras") ?: def.extras,
            empleos = seleccion(o, "empleos") ?: def.empleos,
            mosquitera = tabla(o, "mosquitera") ?: def.mosquitera,
            laminacion = tabla(o, "laminacion") ?: def.laminacion,
            colores = o.optJSONArray("colores")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let { FilaColor(it.optString("nombre"), it.optString("color", "#FFFFFF")) }
                }
            } ?: def.colores,
            fechaActualizacion = o.optLong("fecha_actualizacion", 0L),
        )
    }

    private fun tablaToJson(t: List<List<String>>) = JSONArray().apply {
        t.forEach { fila -> put(JSONArray().apply { fila.forEach { put(it) } }) }
    }

    private fun tabla(o: JSONObject, key: String): List<List<String>>? {
        val arr = o.optJSONArray(key) ?: return null
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONArray(i)?.let { f -> (0 until f.length()).map { f.optString(it) } }
        }
    }

    private fun seleccionToJson(l: List<FilaSeleccion>) = JSONArray().apply {
        l.forEach { put(JSONObject().put("nombre", it.nombre).put("precio", it.precio).put("unidad", it.unidad)) }
    }

    private fun seleccion(o: JSONObject, key: String): List<FilaSeleccion>? {
        val arr = o.optJSONArray(key) ?: return null
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { FilaSeleccion(it.optString("nombre"), it.optString("precio", "0.00"), it.optInt("unidad", 0)) }
        }
    }
}
