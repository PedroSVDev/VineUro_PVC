package com.example.vineuro_pvc.data

import android.content.Context
import org.json.JSONObject

/**
 * Foto de los precios con los que se EMITIÓ la oferta de un pedido (la que se mandó al cliente).
 * Si después cambian los precios, el pedido sigue pudiendo reproducir exactamente el monto ofrecido
 * (evita vender por menos, o discutir con el cliente, por haber actualizado la lista de precios).
 * Se guarda solo en el teléfono (tabla local "configuracion").
 */
object OfertaCongelada {

    data class Foto(val fecha: Long, val numero: String, val totalVenta: Double, val simbolo: String, val precios: Precios)

    private fun clave(pedidoId: String) = "oferta_emitida:$pedidoId"

    fun guardar(ctx: Context, pedidoId: String, numero: String, totalVenta: Double, simbolo: String, precios: Precios) {
        val ahora = System.currentTimeMillis()
        val o = JSONObject().apply {
            put("fecha", ahora); put("numero", numero); put("total", totalVenta); put("simbolo", simbolo)
            put("precios", PreciosJson.toJson(precios))
        }
        VineuroDatabase.get(ctx).guardarConfiguracion(clave(pedidoId), o.toString(), ahora)
    }

    fun leer(ctx: Context, pedidoId: String): Foto? {
        val txt = VineuroDatabase.get(ctx).leerConfiguracion(clave(pedidoId)) ?: return null
        return runCatching {
            val o = JSONObject(txt)
            Foto(o.optLong("fecha"), o.optString("numero"), o.optDouble("total"), o.optString("simbolo"), PreciosJson.fromJson(o.getJSONObject("precios")))
        }.getOrNull()
    }
}
