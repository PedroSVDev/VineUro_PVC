package com.example.vineuro_pvc.data

import org.json.JSONObject

/**
 * "Configurar requisitos": datos de la empresa y condiciones que se imprimen en el PDF de la oferta.
 * Es una configuración general (la misma para todos los pedidos).
 */
data class RequisitosDocumento(
    // Encabezado del PDF
    val cabecera: String = "Oferta comercial",
    // Empresa que ejecuta el trabajo
    val ejecutante: String = "VINEURO PVC - Ventanas Europeas de PVC",
    val ruc: String = "10178956766",
    val direccion: String = "",
    val telefono: String = "",
    val correo: String = "",
    val paginaWeb: String = "",
    // Condiciones de la oferta
    val validezDias: String = "15",
    val plazoEntrega: String = "15 días hábiles desde la confirmación del pedido",
    val formaPago: String = "50% de adelanto y 50% contra entrega",
    val garantia: String = "1 año en perfiles y accesorios",
    val notas: String = "",
    val fechaActualizacion: Long = 0L,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("cabecera", cabecera)
        put("ejecutante", ejecutante)
        put("ruc", ruc)
        put("direccion", direccion)
        put("telefono", telefono)
        put("correo", correo)
        put("pagina_web", paginaWeb)
        put("validez_dias", validezDias)
        put("plazo_entrega", plazoEntrega)
        put("forma_pago", formaPago)
        put("garantia", garantia)
        put("notas", notas)
        put("fecha_actualizacion", fechaActualizacion)
    }

    companion object {
        const val CLAVE = "requisitos_documento"

        fun fromJson(o: JSONObject): RequisitosDocumento {
            val d = RequisitosDocumento()
            fun t(k: String, def: String) = if (o.has(k) && !o.isNull(k)) o.optString(k, def) else def
            return RequisitosDocumento(
                cabecera = t("cabecera", d.cabecera),
                ejecutante = t("ejecutante", d.ejecutante),
                ruc = t("ruc", d.ruc),
                direccion = t("direccion", d.direccion),
                telefono = t("telefono", d.telefono),
                correo = t("correo", d.correo),
                paginaWeb = t("pagina_web", d.paginaWeb),
                validezDias = t("validez_dias", d.validezDias),
                plazoEntrega = t("plazo_entrega", d.plazoEntrega),
                formaPago = t("forma_pago", d.formaPago),
                garantia = t("garantia", d.garantia),
                notas = t("notas", d.notas),
                fechaActualizacion = o.optLong("fecha_actualizacion", 0L),
            )
        }
    }
}
