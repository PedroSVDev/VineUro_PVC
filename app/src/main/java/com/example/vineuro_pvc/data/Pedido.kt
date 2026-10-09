package com.example.vineuro_pvc.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Pedido (cotización) de un cliente: agrupa todas las ventanas diseñadas en CalculoPedidoFragment.
 */
data class Pedido(
    val id: String = UUID.randomUUID().toString(),
    val codigo: String = generarCodigo(),
    /** id del cliente (Cliente.id). */
    val clienteId: String,
    val clienteNombre: String,
    val items: List<OrderItem>,
    val estado: String = ESTADO_BORRADOR,
    val fechaCreacion: Long = System.currentTimeMillis(),
    val fechaActualizacion: Long = System.currentTimeMillis(),
) {
    val totalVentanas: Int get() = items.size

    val areaTotalM2: Double
        get() = items.sumOf { (it.altoTotalMm / 1000.0) * (it.anchoTotalMm / 1000.0) }

    val fechaActualizacionTexto: String
        get() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(fechaActualizacion))

    companion object {
        const val ESTADO_BORRADOR = "borrador"

        fun generarCodigo(): String =
            "PED-" + SimpleDateFormat("yyMMdd-HHmmss", Locale.US).format(Date())
    }
}

/**
 * Conversión Pedido <-> JSON. Se usa tanto para SharedPreferences como para la columna jsonb de Supabase.
 */
object PedidoJson {

    // ---------- Pedido ----------

    /** Formato de la fila en Supabase (snake_case). */
    fun pedidoToJson(p: Pedido): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("codigo", p.codigo)
        put("cliente_id", p.clienteId)
        put("cliente_nombre", p.clienteNombre)
        put("items", itemsToJson(p.items))
        put("total_ventanas", p.totalVentanas)
        put("area_total_m2", Math.round(p.areaTotalM2 * 100) / 100.0)
        put("estado", p.estado)
        put("fecha_creacion", p.fechaCreacion)
        put("fecha_actualizacion", p.fechaActualizacion)
    }

    fun pedidoFromJson(o: JSONObject): Pedido {
        // Supabase puede devolver jsonb como array u (en algunos casos) como string
        val itemsArray = when (val raw = o.opt("items")) {
            is JSONArray -> raw
            is String -> runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
            else -> JSONArray()
        }
        return Pedido(
            id = o.optString("id"),
            codigo = o.optString("codigo").ifEmpty { Pedido.generarCodigo() },
            clienteId = if (o.isNull("cliente_id")) "" else o.optString("cliente_id"),
            clienteNombre = o.optString("cliente_nombre"),
            items = itemsFromJson(itemsArray),
            estado = o.optString("estado", Pedido.ESTADO_BORRADOR),
            fechaCreacion = o.optLong("fecha_creacion", System.currentTimeMillis()),
            fechaActualizacion = o.optLong("fecha_actualizacion", System.currentTimeMillis()),
        )
    }

    // ---------- Items (ventanas) ----------

    fun itemsToJson(items: List<OrderItem>): JSONArray {
        val arr = JSONArray()
        items.forEach { arr.put(itemToJson(it)) }
        return arr
    }

    fun itemsFromJson(arr: JSONArray): List<OrderItem> {
        val catalogo = WindowConfigCatalog.obtenerTodas()
        val lista = mutableListOf<OrderItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            lista.add(itemFromJson(o, catalogo))
        }
        return lista
    }

    private fun itemToJson(item: OrderItem): JSONObject = JSONObject().apply {
        put("index", item.index)
        put("config_id", item.config.id)
        put("config_nombre", item.config.nombre)
        put("alto_total_mm", item.altoTotalMm)
        put("ancho_total_mm", item.anchoTotalMm)
        put("section_heights", floatsToJson(item.sectionHeights))
        put("section_widths", floatsToJson(item.sectionWidths))
        put("pane_mechanisms", mapToJson(item.paneMechanisms))
        put("pane_mosq_net", mapToJson(item.paneMosqNet))
        put("pane_puerta", mapToJson(item.panePuerta))
        put("pane_sandwich", mapToJson(item.paneSandwich))
        put("pane_cols", mapToJson(item.paneCols))
        put("color_bg_hex", item.colorBgHex)
        put("opciones", item.opciones.toJson())
    }

    private fun itemFromJson(o: JSONObject, catalogo: List<WindowConfig>): OrderItem {
        val configId = o.optString("config_id")
        val config = catalogo.find { it.id == configId } ?: catalogo.first()

        val item = OrderItem(
            index = o.optInt("index", 1),
            config = config,
            altoTotalMm = o.optInt("alto_total_mm", 1800),
            anchoTotalMm = o.optInt("ancho_total_mm", 1500),
            sectionHeights = jsonToFloats(o.optJSONArray("section_heights")) ?: floatArrayOf(600f, 600f, 600f),
            sectionWidths = jsonToFloats(o.optJSONArray("section_widths")) ?: floatArrayOf(1500f),
            colorBgHex = o.optString("color_bg_hex", "#42A5F5"),
            opciones = OpcionesVentana.fromJson(o.optJSONObject("opciones")),
        )
        o.optJSONObject("pane_mechanisms")?.let { m -> m.keys().forEach { k -> item.paneMechanisms[k.toInt()] = m.getString(k) } }
        o.optJSONObject("pane_mosq_net")?.let { m -> m.keys().forEach { k -> item.paneMosqNet[k.toInt()] = m.getBoolean(k) } }
        o.optJSONObject("pane_puerta")?.let { m -> m.keys().forEach { k -> item.panePuerta[k.toInt()] = m.getBoolean(k) } }
        o.optJSONObject("pane_sandwich")?.let { m -> m.keys().forEach { k -> item.paneSandwich[k.toInt()] = m.getBoolean(k) } }
        o.optJSONObject("pane_cols")?.let { m -> m.keys().forEach { k -> item.paneCols[k.toInt()] = m.getInt(k) } }
        return item
    }

    private fun floatsToJson(arr: FloatArray): JSONArray {
        val j = JSONArray()
        arr.forEach { j.put(it.toDouble()) }
        return j
    }

    private fun jsonToFloats(j: JSONArray?): FloatArray? {
        if (j == null || j.length() == 0) return null
        return FloatArray(j.length()) { j.optDouble(it, 0.0).toFloat() }
    }

    private fun mapToJson(map: Map<Int, Any>): JSONObject {
        val j = JSONObject()
        map.forEach { (k, v) -> j.put(k.toString(), v) }
        return j
    }
}
