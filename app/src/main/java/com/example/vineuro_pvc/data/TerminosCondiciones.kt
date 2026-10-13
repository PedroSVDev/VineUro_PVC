package com.example.vineuro_pvc.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Una cláusula de las condiciones generales: título corto (PAGOS, GARANTÍA…) y su texto. */
data class Clausula(val titulo: String, val texto: String)

/**
 * Plantilla general de "Términos y condiciones" (la edita el titular una sola vez).
 * Al generar una oferta se copia al pedido y ahí se puede ajustar solo para esa cotización.
 */
data class TerminosCondiciones(
    val clausulas: List<Clausula> = porDefecto(),
    val vigenciaDias: Int = 3,
    val adelantoPct: Int = 50,
    val fechaActualizacion: Long = 0L,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("clausulas", ClausulasJson.toJson(clausulas))
        put("vigencia_dias", vigenciaDias)
        put("adelanto_pct", adelantoPct)
        put("fecha_actualizacion", fechaActualizacion)
    }

    companion object {
        const val CLAVE = "terminos_condiciones"

        fun fromJson(o: JSONObject) = TerminosCondiciones(
            clausulas = o.optJSONArray("clausulas")?.let { ClausulasJson.fromJson(it) }?.takeIf { it.isNotEmpty() } ?: porDefecto(),
            vigenciaDias = o.optInt("vigencia_dias", 3),
            adelantoPct = o.optInt("adelanto_pct", 50).coerceIn(0, 100),
            fechaActualizacion = o.optLong("fecha_actualizacion", 0L),
        )

        /**
         * Cláusulas iniciales. Las 7 primeras son el "ANEXO A" que el cliente usaba en su software anterior
         * (garantía, material, modalidades de pago, entrega, precios y métodos de pago); luego siguen las
         * condiciones de VINEURO que no contradicen ese anexo.
         */
        fun porDefecto(): List<Clausula> = listOf(
            Clausula("GARANTÍA", "Su compra está protegida por una garantía de 10 años por los perfiles de PVC y 1 año por los accesorios."),
            Clausula("MATERIAL", "Perfil de PVC de procedencia turca, marca ASAS-VERATEC. (TURQUÍA)"),
            Clausula("PAGO LIMA", "Modalidad de pago: Lima Metropolitana 50% de adelanto y 50% día de la instalación."),
            Clausula("PAGO PROVINCIAS", "Modalidad de pago provincias (50% adelanto, 40% antes de viajar para instalación y 10% finalizando)."),
            Clausula("ENTREGA", "La entrega de su pedido se hará 10-15 días hábiles a partir de la visita de remetrado. En caso de provincias se coordina."),
            Clausula("PRECIOS", "Los precios son en {moneda}. Incluye IGV."),
            Clausula("MÉTODOS DE PAGO", "Efectivo, transferencia, depósito y tarjeta de crédito (cobro extra del 5%)."),
            Clausula("INICIO", "A partir de la firma del contrato, efectuado el adelanto y realizado el remetrado."),
            Clausula("RENDIMIENTO ACÚSTICO", "70% en apertura corredera, 80% en osciloparalela."),
            Clausula("VIDRIO", "Laminado 8 mm acústico."),
            Clausula("HERRAJES", "Herrajes y accesorios de alta gama y calidad europea, manija ROTO FRANK (Alemania)."),
            Clausula(
                "NO INCLUYE",
                "El desmontaje de las ventanas existentes de fierro, madera antigua o aluminio (en caso exista).\n" +
                    "Resanes o pintado provocado por el retiro de ventanas, rejas o puertas.",
            ),
            Clausula(
                "COLOR",
                "El color del perfil de PVC es {color}.\n" +
                    "Otros colores: Negro, Roble dorado, Silver y Antracita + el 20% aprox. con respecto al blanco.",
            ),
            Clausula(
                "ADICIONALES",
                "Cualquier cambio requerido en esta cotización (diseños, medidas, cantidades, etc.) estará sujeto a cambios en esta cotización.\n" +
                    "Si la instalación es en otros lugares (distrito, provincia, región u otro departamento), se cobrarán los gastos de viáticos, pasajes, embalaje, transporte e instalación.",
            ),
            Clausula("VIGENCIA", "Este presupuesto tiene una validez de {vigencia} días."),
            Clausula("INF. BANCARIA", "Cta. BCP Soles y ME\nCta. INTERBANK Soles y ME"),
        )
    }
}

/**
 * Datos de UNA cotización (oferta comercial de un pedido): número correlativo, tipo de cambio
 * y una copia propia de las cláusulas, que se puede editar sin tocar la plantilla.
 */
data class CotizacionPedido(
    val pedidoId: String,
    val numero: String,
    val tipoCambio: Double,          // S/ por 1 US$
    val fechaTipoCambio: String,     // fecha del dato (BCRP) o "manual"
    val adelantoPct: Int,
    val vigenciaDias: Int,
    val clausulas: List<Clausula>,
    val fechaActualizacion: Long = 0L,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("pedido_id", pedidoId)
        put("numero", numero)
        put("tipo_cambio", tipoCambio)
        put("fecha_tipo_cambio", fechaTipoCambio)
        put("adelanto_pct", adelantoPct)
        put("vigencia_dias", vigenciaDias)
        put("clausulas", ClausulasJson.toJson(clausulas))
        put("fecha_actualizacion", fechaActualizacion)
    }

    companion object {
        fun clave(pedidoId: String) = "cotizacion_$pedidoId"

        fun fromJson(o: JSONObject) = CotizacionPedido(
            pedidoId = o.optString("pedido_id"),
            numero = o.optString("numero"),
            tipoCambio = o.optDouble("tipo_cambio", 0.0),
            fechaTipoCambio = o.optString("fecha_tipo_cambio"),
            adelantoPct = o.optInt("adelanto_pct", 50).coerceIn(0, 100),
            vigenciaDias = o.optInt("vigencia_dias", 3),
            clausulas = o.optJSONArray("clausulas")?.let { ClausulasJson.fromJson(it) } ?: emptyList(),
            fechaActualizacion = o.optLong("fecha_actualizacion", 0L),
        )
    }
}

object ClausulasJson {
    fun toJson(l: List<Clausula>) = JSONArray().apply {
        l.forEach { put(JSONObject().put("titulo", it.titulo).put("texto", it.texto)) }
    }

    fun fromJson(a: JSONArray): List<Clausula> = (0 until a.length()).mapNotNull { i ->
        a.optJSONObject(i)?.let { Clausula(it.optString("titulo"), it.optString("texto")) }
    }
}

/**
 * Campos automáticos que se pueden escribir dentro del texto de una cláusula.
 * Se reemplazan por los datos del pedido al generar la oferta.
 */
object VariablesOferta {
    val DISPONIBLES = listOf(
        "{cliente}" to "Nombre del cliente",
        "{cotizacion}" to "N° de cotización",
        "{fecha}" to "Fecha de la oferta",
        "{color}" to "Color(es) elegido(s) en el compás",
        "{cristal}" to "Tipo(s) de cristal elegido(s)",
        "{sistema}" to "Sistema(s) de ventana elegido(s)",
        "{adelanto}" to "% de adelanto",
        "{saldo}" to "% del saldo",
        "{monto_adelanto}" to "Monto del adelanto",
        "{monto_saldo}" to "Monto del saldo",
        "{vigencia}" to "Días de validez",
        "{tipo_cambio}" to "Tipo de cambio",
        "{total_usd}" to "Total en dólares",
        "{total_soles}" to "Total en soles",
        "{moneda}" to "Moneda de los precios",
    )

    fun reemplazar(texto: String, valores: Map<String, String>): String {
        var r = texto
        valores.forEach { (k, v) -> r = r.replace(k, v) }
        return r
    }

    fun dinero(simbolo: String, v: Double): String = String.format(Locale.US, "%s %,.2f", simbolo, v)
}
