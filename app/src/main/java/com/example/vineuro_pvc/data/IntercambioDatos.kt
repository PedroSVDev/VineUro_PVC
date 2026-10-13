package com.example.vineuro_pvc.data

import android.content.Context
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Compartir datos": enviar precios o pedidos como un archivo (se comparte por Bluetooth, WhatsApp, correo,
 * Drive…) y recibirlo en otro teléfono con un resumen antes de aplicar: Nuevos, Idénticos, Se reemplazan
 * (el archivo es más reciente) e Ignorados (el archivo es más antiguo), igual que el intercambio del original.
 * También sirve de copia de respaldo (RNF-012): un archivo de pedidos contiene pedidos, clientes y cotizaciones.
 */
object IntercambioDatos {

    private const val FORMATO = "vineuro-intercambio"
    private const val VERSION = 1

    enum class Categoria(val titulo: String) {
        NUEVO("Nuevos"), REEMPLAZA("Se reemplazan (el archivo es más reciente)"),
        IDENTICO("Idénticos"), IGNORADO("Ignorados (el archivo es más antiguo)"),
    }

    class Entrada(val categoria: Categoria, val titulo: String, val aplicar: suspend () -> Unit)

    class Resumen(val tipo: String, val fecha: Long, val entradas: List<Entrada>) {
        fun cuantos(c: Categoria) = entradas.count { it.categoria == c }
        val aplicables: List<Entrada> get() = entradas.filter { it.categoria == Categoria.NUEVO || it.categoria == Categoria.REEMPLAZA }
    }

    class ArchivoInvalido(mensaje: String) : Exception(mensaje)

    /** Decide qué pasa con un elemento recibido según existencia, igualdad y fecha de modificación. */
    fun clasificar(fechaLocal: Long?, fechaEntrante: Long, identico: Boolean): Categoria = when {
        fechaLocal == null -> Categoria.NUEVO
        identico -> Categoria.IDENTICO
        fechaEntrante > fechaLocal -> Categoria.REEMPLAZA
        else -> Categoria.IGNORADO
    }

    // ===================== EXPORTAR =====================

    private fun marca() = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())

    private fun cabecera(tipo: String) = JSONObject().apply {
        put("formato", FORMATO); put("version", VERSION); put("tipo", tipo); put("fecha", System.currentTimeMillis())
    }

    fun exportarPrecios(ctx: Context): File {
        val db = VineuroDatabase.get(ctx)
        val o = cabecera("precios")
        o.put("precios", PreciosJson.toJson(PreciosRepository(ctx).obtener()))
        val conf = JSONObject()
        for (k in listOf(TerminosCondiciones.CLAVE, RequisitosDocumento.CLAVE, "parametros_corte")) {
            db.leerConfiguracion(k)?.let { conf.put(k, it) }
        }
        o.put("configuracion", conf)
        return escribir(ctx, "VineuroPrecios_${marca()}.json", o)
    }

    fun exportarPedidos(ctx: Context, pedidos: List<Pedido>? = null): File {
        val db = VineuroDatabase.get(ctx)
        val lista = pedidos ?: PedidoRepository(ctx).obtenerPedidos()
        val o = cabecera("pedidos")
        val clientes = ClientRepository(ctx).obtenerClientes()
        val usados = lista.map { it.clienteId }.toSet()
        o.put("clientes", JSONArray().apply {
            clientes.filter { it.id in usados }.forEach {
                put(JSONObject().apply {
                    put("id", it.id); put("nombres", it.nombres); put("apellidos", it.apellidos); put("telefono", it.telefono)
                    put("email", it.email); put("direccion", it.direccion); put("nota", it.nota); put("fecha_registro", it.fechaRegistro)
                })
            }
        })
        o.put("pedidos", JSONArray().apply {
            lista.forEach { p ->
                put(JSONObject().apply {
                    put("pedido", PedidoJson.pedidoToJson(p))
                    put("direccion", p.direccion); put("id_oferta", p.idOferta); put("nota", p.nota)
                    db.leerConfiguracion(CotizacionPedido.clave(p.id))?.let { put("cotizacion", it) }
                })
            }
        })
        return escribir(ctx, "VineuroPedidos_${marca()}.json", o)
    }

    private fun escribir(ctx: Context, nombre: String, o: JSONObject): File {
        val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: ctx.filesDir
        val f = File(dir, nombre)
        f.writeText(o.toString(), Charsets.UTF_8)
        return f
    }

    // ===================== IMPORTAR =====================

    /** Lee el archivo recibido y arma el resumen SIN tocar nada. */
    fun analizar(ctx: Context, texto: String): Resumen {
        val o = try { JSONObject(texto) } catch (e: Exception) { throw ArchivoInvalido("El archivo no es un archivo de VINEURO.") }
        if (o.optString("formato") != FORMATO) throw ArchivoInvalido("El archivo no es un archivo de intercambio de VINEURO.")
        if (o.optInt("version", 0) > VERSION) throw ArchivoInvalido("El archivo es de una versión más nueva de la app. Actualiza la app e intenta de nuevo.")
        return when (val tipo = o.optString("tipo")) {
            "precios" -> analizarPrecios(ctx, o)
            "pedidos" -> analizarPedidos(ctx, o)
            else -> throw ArchivoInvalido("Tipo de archivo desconocido: $tipo")
        }
    }

    private fun sinFecha(p: JSONObject): String = JSONObject(p.toString()).apply { remove("fecha_actualizacion") }.toString()

    private fun analizarPrecios(ctx: Context, o: JSONObject): Resumen {
        val repo = PreciosRepository(ctx)
        val db = VineuroDatabase.get(ctx)
        val pj = o.optJSONObject("precios") ?: throw ArchivoInvalido("El archivo no trae precios.")
        val entrantes = PreciosJson.fromJson(pj)
        val local = if (repo.tieneDatosLocales()) repo.obtener() else null
        val identico = local != null && sinFecha(PreciosJson.toJson(local)) == sinFecha(PreciosJson.toJson(entrantes))
        val cat = clasificar(local?.fechaActualizacion, entrantes.fechaActualizacion, identico)
        val conf = o.optJSONObject("configuracion")
        val entradas = mutableListOf<Entrada>()
        entradas += Entrada(cat, "Tablas de precios (${entrantes.ventanas.size} sistemas, ${entrantes.cristal.size} cristales)") {
            repo.reemplazar(entrantes)
            repo.subir()
        }
        // Configuración que viaja con los precios: términos, datos de la empresa y parámetros de corte
        conf?.keys()?.forEach { k ->
            val nuevo = conf.optString(k)
            val actual = db.leerConfiguracion(k)
            val c = when {
                actual == null -> Categoria.NUEVO
                actual == nuevo -> Categoria.IDENTICO
                else -> cat.takeIf { it == Categoria.NUEVO || it == Categoria.REEMPLAZA } ?: Categoria.IGNORADO
            }
            val nombre = when (k) {
                TerminosCondiciones.CLAVE -> "Términos y condiciones"
                RequisitosDocumento.CLAVE -> "Datos de la empresa"
                "parametros_corte" -> "Parámetros de corte"
                else -> k
            }
            entradas += Entrada(c, nombre) { db.guardarConfiguracion(k, nuevo, System.currentTimeMillis()) }
        }
        return Resumen("precios", o.optLong("fecha"), entradas)
    }

    private fun analizarPedidos(ctx: Context, o: JSONObject): Resumen {
        val pedRepo = PedidoRepository(ctx)
        val cliRepo = ClientRepository(ctx)
        val db = VineuroDatabase.get(ctx)
        val entradas = mutableListOf<Entrada>()

        val clientes = o.optJSONArray("clientes") ?: JSONArray()
        for (i in 0 until clientes.length()) {
            val c = clientes.optJSONObject(i) ?: continue
            val nuevo = Cliente(
                id = c.optString("id"), nombres = c.optString("nombres"), apellidos = c.optString("apellidos"),
                telefono = c.optString("telefono"), email = c.optString("email"), direccion = c.optString("direccion"),
                nota = c.optString("nota"), fechaRegistro = c.optLong("fecha_registro", System.currentTimeMillis()),
            )
            if (nuevo.id.isBlank()) continue
            val local = cliRepo.obtenerClientePorId(nuevo.id)
            val cat = when {
                local == null -> Categoria.NUEVO
                local == nuevo -> Categoria.IDENTICO
                else -> Categoria.IGNORADO   // los clientes no tienen fecha de modificación: no se pisa lo que ya hay
            }
            entradas += Entrada(cat, "Cliente: ${nuevo.nombreCompleto}") { cliRepo.guardarCliente(nuevo) }
        }

        val pedidos = o.optJSONArray("pedidos") ?: JSONArray()
        for (i in 0 until pedidos.length()) {
            val e = pedidos.optJSONObject(i) ?: continue
            val pj = e.optJSONObject("pedido") ?: continue
            val entrante = PedidoJson.pedidoFromJson(pj).copy(
                direccion = e.optString("direccion"), idOferta = e.optString("id_oferta"), nota = e.optString("nota"),
            )
            val local = pedRepo.obtenerPedido(entrante.id)
            val identico = local != null && local.fechaActualizacion == entrante.fechaActualizacion &&
                PedidoJson.pedidoToJson(local).optString("items") == PedidoJson.pedidoToJson(entrante).optString("items")
            val cat = clasificar(local?.fechaActualizacion, entrante.fechaActualizacion, identico)
            val cot = e.optString("cotizacion").takeIf { it.isNotBlank() }
            entradas += Entrada(cat, "Pedido ${entrante.codigo} · ${entrante.clienteNombre} · ${entrante.totalVentanas} ventanas") {
                pedRepo.guardarPedido(entrante)
                cot?.let { db.guardarConfiguracion(CotizacionPedido.clave(entrante.id), it, System.currentTimeMillis()) }
            }
        }
        return Resumen("pedidos", o.optLong("fecha"), entradas)
    }

    /** Aplica lo nuevo y lo que se reemplaza. Devuelve cuántos elementos se aplicaron. */
    suspend fun aplicar(r: Resumen): Int {
        var n = 0
        for (e in r.aplicables) { e.aplicar(); n++ }
        return n
    }
}
