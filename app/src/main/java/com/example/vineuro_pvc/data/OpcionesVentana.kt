package com.example.vineuro_pvc.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Configuración de una ventana elegida en el menú lateral (botón verde del compás).
 * Las opciones se guardan por NOMBRE tal como aparecen en el apartado de Precios.
 * null = "no utilice".
 */
data class OpcionesVentana(
    var cantidad: Int = 1,
    var nota: String = "",
    var precioPropio: String = "",
    var planFinanciacion: String? = null,
    var sistemaVentana: String? = null,
    var tipoCristal: String? = null,
    val umbralesExternos: MutableSet<String> = mutableSetOf(),
    val umbralesInternos: MutableSet<String> = mutableSetOf(),
    var mueble: String? = null,
    val extras: MutableSet<String> = mutableSetOf(),
    val trabajos: MutableSet<String> = mutableSetOf(),
    var mosquitero: String? = null,
    var laminacionInterna: String? = null,
    var laminacionExterna: String? = null,
    var color: String? = null,
    /** "perfil del asiento", "dorsill", "jamba de la esquina" */
    val adicionales: MutableSet<String> = mutableSetOf(),
    /** Claves tipo "juntas:0", "juntas90:2", "huecos:3", "extensor:40:1" (lado 0=izq,1=sup,2=inf,3=der). */
    val perfiles: MutableSet<String> = mutableSetOf(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("cantidad", cantidad)
        put("nota", nota)
        put("precio_propio", precioPropio)
        put("plan_financiacion", planFinanciacion ?: JSONObject.NULL)
        put("sistema_ventana", sistemaVentana ?: JSONObject.NULL)
        put("tipo_cristal", tipoCristal ?: JSONObject.NULL)
        put("umbrales_externos", JSONArray(umbralesExternos.toList()))
        put("umbrales_internos", JSONArray(umbralesInternos.toList()))
        put("mueble", mueble ?: JSONObject.NULL)
        put("extras", JSONArray(extras.toList()))
        put("trabajos", JSONArray(trabajos.toList()))
        put("mosquitero", mosquitero ?: JSONObject.NULL)
        put("laminacion_interna", laminacionInterna ?: JSONObject.NULL)
        put("laminacion_externa", laminacionExterna ?: JSONObject.NULL)
        put("color", color ?: JSONObject.NULL)
        put("adicionales", JSONArray(adicionales.toList()))
        put("perfiles", JSONArray(perfiles.toList()))
    }

    /** Copia independiente (para editar en el menú sin tocar la ventana hasta pulsar ACEPTAR). */
    fun copiar(): OpcionesVentana = fromJson(toJson())

    companion object {
        fun fromJson(o: JSONObject?): OpcionesVentana {
            if (o == null) return OpcionesVentana()
            fun str(k: String): String? = if (o.isNull(k)) null else o.optString(k).ifBlank { null }
            fun set(k: String): MutableSet<String> {
                val arr = o.optJSONArray(k) ?: return mutableSetOf()
                return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }.toMutableSet()
            }
            return OpcionesVentana(
                cantidad = o.optInt("cantidad", 1).coerceAtLeast(1),
                nota = o.optString("nota", ""),
                precioPropio = o.optString("precio_propio", ""),
                planFinanciacion = str("plan_financiacion"),
                sistemaVentana = str("sistema_ventana"),
                tipoCristal = str("tipo_cristal"),
                umbralesExternos = set("umbrales_externos"),
                umbralesInternos = set("umbrales_internos"),
                mueble = str("mueble"),
                extras = set("extras"),
                trabajos = set("trabajos"),
                mosquitero = str("mosquitero"),
                laminacionInterna = str("laminacion_interna"),
                laminacionExterna = str("laminacion_externa"),
                color = str("color"),
                adicionales = set("adicionales"),
                perfiles = set("perfiles"),
            )
        }
    }
}
