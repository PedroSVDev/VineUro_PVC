package com.example.vineuro_pvc.data

import org.json.JSONObject

/**
 * "Datos de la empresa": encabezado, contacto, lemas y firma que se imprimen en los PDF.
 * (Las condiciones de la oferta ahora están en TerminosCondiciones.)
 * Es una configuración general (la misma para todos los pedidos).
 */
data class RequisitosDocumento(
    // Encabezado del PDF
    val cabecera: String = "Oferta comercial",
    // Empresa que ejecuta el trabajo
    val ejecutante: String = "VINEURO PVC - Ventanas Europeas de PVC",
    val ruc: String = "10178956766",
    val direccion: String = "Av. América Sur # 2164 Urb. Sta. María, Trujillo - PERÚ",
    val telefono: String = "952613977",
    val telefonoFijo: String = "044-713940",
    val correo: String = "vineuro.pvc@gmail.com",
    val paginaWeb: String = "",
    val facebook: String = "trujicoinsa",
    // Lemas que van debajo del encabezado del PDF
    val lema1: String = "VENTANAS ANTIRUIDO Y TERMOACÚSTICAS DE PVC",
    val lema2: String = "SISTEMAS DE CERRAMIENTOS PARA LA INDUSTRIA DE LA CONSTRUCCIÓN",
    // Firma al pie de la oferta
    val firmaNombre: String = "Victor Neyra García",
    val firmaCargo: String = "Titular · Gerente",
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
        put("telefono_fijo", telefonoFijo)
        put("facebook", facebook)
        put("lema1", lema1)
        put("lema2", lema2)
        put("firma_nombre", firmaNombre)
        put("firma_cargo", firmaCargo)
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
                telefonoFijo = t("telefono_fijo", d.telefonoFijo),
                facebook = t("facebook", d.facebook),
                lema1 = t("lema1", d.lema1),
                lema2 = t("lema2", d.lema2),
                firmaNombre = t("firma_nombre", d.firmaNombre),
                firmaCargo = t("firma_cargo", d.firmaCargo),
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
