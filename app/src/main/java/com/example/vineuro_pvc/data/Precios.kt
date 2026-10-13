package com.example.vineuro_pvc.data

import org.json.JSONArray
import org.json.JSONObject

/** Fila de extras / trabajos: nombre, precio y unidad elegida (0..5 = pc, m2, m, m, m, m). */
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
        private fun ceros(n: Int) = List(n) { "0.00" }
        private fun fila(nombre: String, columnasNumericas: Int) = listOf(nombre) + ceros(columnasNumericas)

        /** Valores iniciales cuando todavía no hay nada guardado (ni en el teléfono ni en la nube). */
        fun porDefecto(): Precios = Precios(
            moneda = listOf(listOf("S/", "1.00", "S/")),
            impuesto = listOf(listOf("IGV", "18.00")),
            planes = listOf(
                listOf("descuento 1%", "1.00", "0.00", "0.00"),
                listOf("descuento 3%", "3.00", "0.00", "0.00"),
                listOf("descuento 5%", "5.00", "0.00", "0.00"),
            ),
            ventanas = listOf(
                fila("Sistema PVC personalizado 1", 9),
                fila("Sistema PVC personalizado 2", 9),
                fila("Sistema PVC personalizado 3", 9),
                listOf("Sistema PVC A", "5.67", "5.58", "4.81", "0.00", "0.00", "1.67", "0.28", "0.00", "0.00"),
                fila("Sistema PVC B", 9),
                fila("Sistema PVC C", 9),
            ),
            cristal = listOf(
                fila("vidrio común 4-6-4", 9),
                fila("vidrio común 4-6-4-6-4", 9),
            ),
            umbralExt = listOf(
                fila("alféizar exterior blanco 200", 2),
                fila("alféizar exterior blanco 300", 2),
            ),
            umbralInt = listOf(
                fila("alféizar interior blanco 300", 2),
                fila("alféizar interior blanco 400", 2),
                fila("imitación mármol 300", 2),
                fila("imitación mármol 400", 2),
            ),
            muebles = listOf(
                fila("blanco tipo 1", 3),
                fila("blanco tipo 2", 3),
            ),
            extras = listOf(
                FilaSeleccion("espuma de montaje", "12.30", 0),
                FilaSeleccion("nuevo material extra", "0.00", 0),
                FilaSeleccion("juego de tornillos", "0.00", 0),
            ),
            empleos = listOf(
                FilaSeleccion("transporte", "6.00", 0),
                FilaSeleccion("desmontaje", "0.00", 0),
                FilaSeleccion("acabado de derrames", "0.00", 2),
                FilaSeleccion("instalación de alféizar interior", "0.00", 2),
                FilaSeleccion("instalación en concreto", "0.00", 0),
                FilaSeleccion("instalación", "0.00", 1),
                FilaSeleccion("instalación de alféizar exterior", "0.00", 2),
            ),
            mosquitera = listOf(
                fila("mosquitero marrón", 6),
                fila("mosquitero gris", 6),
                fila("mosquitero blanco", 6),
            ),
            laminacion = listOf(
                fila("color burdeos", 1),
                fila("color gris", 1),
                fila("nueva laminación", 1),
                fila("roble oscuro", 1),
                fila("roble claro", 1),
            ),
            colores = listOf(
                FilaColor("aliso", "#C49B7A"),
                FilaColor("fresno", "#B8957A"),
                FilaColor("azul oscuro", "#2F4F86"),
                FilaColor("haya", "#B08B80"),
                FilaColor("abedul", "#C9AD8F"),
                FilaColor("negro", "#2B2B2B"),
                FilaColor("celeste", "#2F86C9"),
                FilaColor("cerezo", "#A5583A"),
                FilaColor("ébano", "#5A3A3E"),
                FilaColor("verde", "#7FA06A"),
                FilaColor("nogal americano", "#8F6E55"),
                FilaColor("caoba", "#8A4B22"),
                FilaColor("nogal", "#8B5E4A"),
            ),
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
            ventanas = tabla(o, "ventanas") ?: def.ventanas,
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
                    arr.optJSONObject(i)?.let { FilaColor(NombresEs.es(it.optString("nombre")), it.optString("color", "#FFFFFF")) }
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
            arr.optJSONArray(i)?.let { f -> (0 until f.length()).map { c -> if (c == 0) NombresEs.es(f.optString(c)) else f.optString(c) } }
        }
    }

    private fun seleccionToJson(l: List<FilaSeleccion>) = JSONArray().apply {
        l.forEach { put(JSONObject().put("nombre", it.nombre).put("precio", it.precio).put("unidad", it.unidad)) }
    }

    private fun seleccion(o: JSONObject, key: String): List<FilaSeleccion>? {
        val arr = o.optJSONArray(key) ?: return null
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { FilaSeleccion(NombresEs.es(it.optString("nombre")), it.optString("precio", "0.00"), it.optInt("unidad", 0)) }
        }
    }
}
