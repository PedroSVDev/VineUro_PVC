package com.example.vineuro_pvc

import com.example.vineuro_pvc.data.AperturasMulti
import com.example.vineuro_pvc.data.CalculadoraPrecios
import com.example.vineuro_pvc.data.CalculadoraPrecios.COL_ESQUINERO_HOJA
import com.example.vineuro_pvc.data.CalculadoraPrecios.COL_EXT_40
import com.example.vineuro_pvc.data.CalculadoraPrecios.COL_HOJA
import com.example.vineuro_pvc.data.CalculadoraPrecios.COL_KIT_CORREDERA
import com.example.vineuro_pvc.data.CalculadoraPrecios.COL_KIT_ROTACION
import com.example.vineuro_pvc.data.CalculadoraPrecios.COL_KIT_VERTICAL
import com.example.vineuro_pvc.data.CalculadoraPrecios.COL_MARCO
import com.example.vineuro_pvc.data.CalculadoraPrecios.COL_GUIAS_CORREDERA
import com.example.vineuro_pvc.data.FilaSeleccion
import com.example.vineuro_pvc.data.OrderItem
import com.example.vineuro_pvc.data.Precios
import com.example.vineuro_pvc.data.WindowConfig
import com.example.vineuro_pvc.data.WindowConfig.LayoutType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Casos de referencia de la calculadora (CP-06 / CA). Las cifras se derivan a mano de las reglas de cálculo.
 * Los resultados esperados están verificados a mano (ver comentarios), no calculados con la propia calculadora.
 *
 * Medidas del sistema: marco 48 mm, imposta 52 mm (a ejes), hoja 62 mm, solape de hoja 13 mm.
 */
class CalculadoraPreciosTest {

    private val D = 1e-6
    /** Las cantidades agregadas se redondean a 4 decimales. */
    private fun r4(x: Double) = Math.round(x * 10000.0) / 10000.0

    private fun cfg(tipo: LayoutType, cols: Int = 1, rows: Int = 1) =
        WindowConfig(id = "t", nombre = "t", categoria = "t", layoutType = tipo, cols = cols, rows = rows)

    private fun item(tipo: LayoutType, ancho: Int, alto: Int, cols: Int = 1, rows: Int = 1) =
        OrderItem.create(0, cfg(tipo, cols, rows), alto, ancho)

    /** Fila de sistema con [valores] por columna (el resto en 0). */
    private fun sistema(vararg valores: Pair<Int, Double>): List<String> {
        val f = MutableList(Precios.COLUMNAS_SISTEMA + 1) { "0" }
        f[0] = "SIS"
        valores.forEach { (c, v) -> f[c] = v.toString() }
        return f
    }

    /** Precios mínimos: tarifa 1, IGV 18, plan "P" (desc 10 %, mat 20 %, trab 10 %). */
    private fun precios(
        sis: List<String> = sistema(),
        vidrio: List<String> = listOf("VID", "0", "0", "0", "0", "0", "0", "0", "0", "0"),
        extras: List<FilaSeleccion> = emptyList(),
    ): Precios = Precios.porDefecto().copy(
        moneda = listOf(listOf("US$", "1.00", "US$")),
        impuesto = listOf(listOf("IGV", "18")),
        planes = listOf(listOf("P", "10", "20", "10")),
        ventanas = listOf(sis),
        cristal = listOf(vidrio),
        extras = extras,
        empleos = emptyList(),
    )

    private fun conSistema(i: OrderItem): OrderItem {
        i.opciones.sistemaVentana = "SIS"; i.opciones.tipoCristal = "VID"; i.opciones.planFinanciacion = "P"
        return i
    }

    // ---------------- Geometría ----------------

    @Test fun grillaDosColumnas_usaHuecosReales() {
        // 1500 x 1800, columnas 600 / 900. Hueco = paño - 48 (marco) - 26 (media imposta) por lado.
        val it = item(LayoutType.GRID, 1500, 1800, cols = 2)
        it.sectionWidths = floatArrayOf(600f, 900f)
        val g = CalculadoraPrecios.geometria(it)
        assertEquals(0.6 - 0.048 - 0.026, g.panos[0].huecoAncho, D)
        assertEquals(0.9 - 0.026 - 0.048, g.panos[1].huecoAncho, D)
        assertEquals(1.8 - 0.096, g.panos[0].huecoAlto, D)
        assertEquals(1.8, g.impostaM, D)
        assertEquals(1, g.impostaN)
        val q = CalculadoraPrecios.cantidades(it)
        assertEquals(2 * (0.526 + 1.704) + 2 * (0.826 + 1.704), q.glazingM, D) // vidrio fijo = hueco
    }

    @Test fun vidrioFijo_esElHueco() {
        val q = CalculadoraPrecios.cantidades(item(LayoutType.GRID, 1500, 1800))
        assertEquals(r4((1.5 - 0.096) * (1.8 - 0.096)), q.vidrioM2, D)
        assertEquals(1.5 * 1.8, q.areaM2, D)                               // el área de la ventana no cambia
    }

    @Test fun hoja_vidrioSeReduceYHojaMontaSobreElMarco() {
        val it = item(LayoutType.GRID, 1500, 1800)
        it.paneMechanisms[0] = "4"
        val q = CalculadoraPrecios.cantidades(it)
        val hw = 1.404; val hh = 1.704                                     // hueco
        assertEquals(2 * (hw + 0.026 + hh + 0.026), q.hojaM, D)            // hoja = hueco + 13 mm por lado
        assertEquals(r4((hw - 2 * 0.049) * (hh - 2 * 0.049)), q.vidrioM2, D)   // vidrio = hueco - (62-13) por lado
        assertEquals(1, q.hojas)
    }

    @Test fun puerta_perfil94() {
        val it = item(LayoutType.GRID, 1500, 1800)
        it.panePuerta[0] = true
        val q = CalculadoraPrecios.cantidades(it)
        assertEquals(r4((1.404 - 2 * 0.081) * (1.704 - 2 * 0.081)), q.vidrioM2, D)
        assertEquals(1, q.hojasPuerta)
    }

    @Test fun travesanoSuperior_imposta() {
        val it = item(LayoutType.TOP_TRANSOM, 1500, 1800, cols = 3)
        it.sectionHeights = floatArrayOf(400f, 1400f)
        val g = CalculadoraPrecios.geometria(it)
        assertEquals(4, g.panos.size)
        assertEquals(1.5 + 2 * 1.4, g.impostaM, D)
        assertEquals(3, g.impostaN)
    }

    @Test fun subdivisionAgregaImpostaYPanos() {
        val it = item(LayoutType.GRID, 1500, 1800)
        it.paneCols[0] = 3
        val g = CalculadoraPrecios.geometria(it)
        assertEquals(3, g.panos.size)
        assertEquals(2 * 1.8, g.impostaM, D)
    }

    @Test fun impostaInteriorEsUnaSolaHojaConVariosVidrios() {
        // 1500 x 1800, un paño con apertura practicable e imposta horizontal x1 (código 11)
        val it = item(LayoutType.GRID, 1500, 1800)
        it.paneMechanisms[0] = "4"
        it.paneCols[0] = 11
        val q = CalculadoraPrecios.cantidades(it)
        assertEquals(1, q.hojasDetalle.size)                 // UNA sola hoja
        assertEquals(1, q.impostaN)                          // 1 imposta interior
        val v = CalculadoraPrecios.piezasVidrio(it)
        assertEquals(2, v.size)
        // vidrio total = hueco - (62-13) por lado = 1.404 x 1.704 ; imposta de 52 mm entre los dos vidrios
        val alto = v.sumOf { it.altoMm } + 52
        assertEquals(1606.0, alto.toDouble(), 1.0)
        assertEquals(1.306, q.impostaM, D)                   // largo de imposta = ancho del vidrio
    }

    @Test fun sandwichPorVidrioEnImpostaInterior() {
        val it = item(LayoutType.GRID, 1500, 1800)
        it.paneMechanisms[0] = "4"
        it.paneCols[0] = 12            // horizontal x2 => 3 vidrios
        it.paneSandwich[0] = true
        it.paneSandwichSub[0] = 2      // solo el vidrio del medio
        val v = CalculadoraPrecios.piezasVidrio(it)
        assertEquals(listOf(false, true, false), v.map { it.sandwich })
    }

    @Test fun barrasDeVidrio_largoYConectores() {
        val it = conSistema(item(LayoutType.GRID, 1500, 1800))
        it.paneBars[0] = "2x1"        // 2 horizontales y 1 vertical en el único vidrio (fijo: 1404 x 1704)
        val q = CalculadoraPrecios.cantidades(it)
        assertEquals(2 * 1.404 + 1 * 1.704, q.barrasM, D)
        assertEquals(2, q.barrasCruces)
        val p = precios(sistema(CalculadoraPrecios.COL_BARRAS to 3.0, CalculadoraPrecios.COL_CONECTOR_BARRAS to 0.5))
        val r = CalculadoraPrecios.calcular(it, p)
        assertEquals(3.0 * (2 * 1.404 + 1.704) + 2 * 0.5, r.costoUnitario.hoja, D)
        val cortes = CalculadoraPrecios.piezasCorte(it).filter { c -> c.perfil == "Barra de vidrio" }
        assertEquals(3, cortes.size)
    }

    @Test fun hojaFijaNoLlevaKit() {
        val it = conSistema(item(LayoutType.GRID, 1500, 1800))
        it.paneMechanisms[0] = "10"
        val p = precios(sistema(COL_HOJA to 5.0, COL_KIT_ROTACION to 40.0, COL_KIT_VERTICAL to 25.0, CalculadoraPrecios.COL_KIT_HORIZONTAL to 15.0))
        val q = CalculadoraPrecios.cantidades(it)
        assertEquals(1, q.hojasDetalle.size)
        assertEquals(0, q.hojas)
        val h = CalculadoraPrecios.calcular(it, p).costoUnitario.hoja
        assertEquals(5.0 * q.hojaM, h, D)
    }

    @Test fun impostaInterior_posicionEditable() {
        val it = conSistema(item(LayoutType.GRID, 1500, 1800))
        it.paneMechanisms[0] = "4"; it.paneCols[0] = 11 // practicable con imposta horizontal x1
        // por defecto: a la mitad del hueco (1704 / 2 = 852 desde abajo)
        val def = CalculadoraPrecios.piezasVidrio(it).map { v -> v.altoMm }
        assertEquals(listOf(1606 - 852 + 26 + 62 - 52, 852 - 26 - 62), def)
        it.paneImpOff[0] = "500" // 500 mm desde abajo
        val v = CalculadoraPrecios.piezasVidrio(it).map { x -> x.altoMm }
        assertEquals(listOf(1606 - 412 - 52, 500 - 26 - 62), v)
    }

    @Test fun aperturasMulti_todasCabenEnElHuecoYNoSeSolapanLosVidrios() {
        val w = 3.0; val h = 2.0
        AperturasMulti.TODAS.forEach { id ->
            val hs = AperturasMulti.hojas(id, w, h)
            check(hs.isNotEmpty()) { "hojas de $id" }
            val francesa = AperturasMulti.grupo(id) == AperturasMulti.Grupo.FRANCESA
            hs.forEach { x ->
                val l = x.x - x.perfil; val r = x.x + x.ancho + x.perfil; val t = x.y - x.perfil; val b = x.y + x.alto + x.perfil
                val m = if (francesa) 0.013 else 0.0
                check(l >= -m - 1e-9) { "$id izq" }; check(r <= w + m + 1e-9) { "$id der" }
                check(t >= -m - 1e-9) { "$id arr" }; check(b <= h + m + 1e-9) { "$id aba" }
            }
            // los vidrios no se solapan entre sí
            for (i in hs.indices) for (j in i + 1 until hs.size) {
                val a = hs[i]; val c = hs[j]
                val sx = a.x < c.x + c.ancho - 1e-9 && c.x < a.x + a.ancho - 1e-9
                val sy = a.y < c.y + c.alto - 1e-9 && c.y < a.y + a.alto - 1e-9
                check(!(sx && sy)) { "$id vidrios $i y $j" }
            }
        }
    }

    @Test fun umbral_aMedidaYPorPiezas() {
        val it = conSistema(item(LayoutType.GRID, 1500, 1800))
        it.opciones.umbralesExternos.add("U")
        val base = precios().copy(umbralExt = listOf(listOf("U", "4.0", "0.5", "-")))
        // a medida: 1.5 m x 4 + 2 tapones x 0.5
        assertEquals(1.5 * 4.0 + 2 * 0.5, CalculadoraPrecios.calcular(it, base).costoUnitario.accesorios, 1e-9)
        // por piezas (override): 2 x 1.6 m + 1 x 0.8 m = 4.0 m; 3 piezas = 6 tapones
        val pz = precios().copy(umbralExt = listOf(listOf("U", "4.0", "0.5", "1600x2 800x1")))
        assertEquals(4.0 * 4.0 + 6 * 0.5, CalculadoraPrecios.calcular(it, pz).costoUnitario.accesorios, 1e-9)
    }

    // ---------------- Aperturas de varias hojas ----------------

    @Test fun corredizaDeDosHojas_geometriaYKit() {
        val it = conSistema(item(LayoutType.GRID, 1500, 1800))
        it.paneMechanisms[0] = "S:1R_2L"
        val p = precios(sistema(COL_HOJA to 5.0, COL_KIT_CORREDERA to 30.0, COL_GUIAS_CORREDERA to 8.0))
        val q = CalculadoraPrecios.cantidades(it)
        // hueco 1404 x 1704; capas distintas: (1404 - 3*62)/2 = 609 por hoja; vidrio alto 1704 - 124 = 1580
        assertEquals(2 * 2 * ((0.609 + 0.124) + (1.580 + 0.124)), q.hojaM, 1e-9)
        assertEquals(2, q.hojas)
        assertEquals(2, CalculadoraPrecios.piezasVidrio(it).size)
        assertEquals(609, CalculadoraPrecios.piezasVidrio(it)[0].anchoMm)
        // 2 kits de corredera (sin guías: son solo de la corrediza simple)
        assertEquals(5.0 * q.hojaM + 2 * 30.0, CalculadoraPrecios.calcular(it, p).costoUnitario.hoja, 1e-9)
    }

    @Test fun corredizaConHojaFija_soloCobraKitDeLasMoviles() {
        val it = conSistema(item(LayoutType.GRID, 1500, 1800))
        it.paneMechanisms[0] = "S:1X_2L"
        val p = precios(sistema(COL_HOJA to 5.0, COL_KIT_CORREDERA to 30.0))
        val q = CalculadoraPrecios.cantidades(it)
        assertEquals(1, q.hojas)
        assertEquals(5.0 * q.hojaM + 30.0, CalculadoraPrecios.calcular(it, p).costoUnitario.hoja, 1e-9)
    }

    @Test fun puertaFrancesa_dosHojasUnKitEImpostaCentral() {
        val it = conSistema(item(LayoutType.GRID, 1500, 1800))
        it.paneMechanisms[0] = "F:LCF_RC"
        val p = precios(sistema(COL_HOJA to 5.0, CalculadoraPrecios.COL_KIT_FRANCESA to 50.0, CalculadoraPrecios.COL_IMPOSTA_FRANCESA to 7.0))
        val q = CalculadoraPrecios.cantidades(it)
        // cada hoja: (1404 + 26)/2 = 715 de ancho exterior y 1730 de alto
        assertEquals(2 * 2 * (0.715 + 1.730), q.hojaM, 1e-9)
        assertEquals(5.0 * q.hojaM + 50.0 + 7.0 * 1.730, CalculadoraPrecios.calcular(it, p).costoUnitario.hoja, 1e-9)
    }

    @Test fun aperturasMulti_manijas() {
        fun manijas(mec: String) = AperturasMulti.hojas(mec, 1.4, 1.7).count { it.manija }
        assertEquals(2, manijas("S:1R_2L"))
        assertEquals(1, manijas("S:1X_2L"))
        assertEquals(1, AperturasMulti.manijas("F:LC_RCF", AperturasMulti.hojas("F:LC_RCF", 1.4, 1.7)))
        AperturasMulti.TODAS.forEach { id -> assertEquals(true, AperturasMulti.hojas(id, 3.0, 2.0).all { h -> h.ancho > 0 && h.alto > 0 }) }
    }

    // ---------------- Costos del sistema ----------------

    @Test fun marco_perfilRefuerzoYEsquineros() {
        val it = conSistema(item(LayoutType.GRID, 1500, 1800))
        val p = precios(sistema(COL_MARCO to 10.0, CalculadoraPrecios.COL_REFUERZO_MARCO to 2.0, CalculadoraPrecios.COL_ESQUINERO_MARCO to 1.5))
        val r = CalculadoraPrecios.calcular(it, p)
        assertEquals(6.6 * 12.0 + 4 * 1.5, r.costoUnitario.marco, D)
    }

    @Test fun herrajeSegunTipoDeApertura() {
        fun costoHoja(mec: String): Double {
            val it = conSistema(item(LayoutType.GRID, 1500, 1800))
            it.paneMechanisms[0] = mec
            val p = precios(sistema(COL_HOJA to 5.0, COL_ESQUINERO_HOJA to 1.0, COL_KIT_ROTACION to 40.0,
                COL_KIT_VERTICAL to 25.0, COL_KIT_CORREDERA to 30.0, COL_GUIAS_CORREDERA to 8.0))
            return CalculadoraPrecios.calcular(it, p).costoUnitario.hoja
        }
        val base = 5.0 * 2 * (1.43 + 1.73) + 4 * 1.0
        assertEquals(base + 25.0, costoHoja("4"), 1e-9)                    // practicable
        assertEquals(base + 40.0, costoHoja("7"), 1e-9)                    // oscilobatiente
        assertEquals(base + 30.0 + 8.0, costoHoja("1"), 1e-9)              // corrediza = kit + guías
        assertEquals(0.0, costoHoja("5"), 1e-9)                            // fijo: no hay hoja
    }

    @Test fun vidrio_dobleYTriple() {
        // Doble: butilo x2, espaciador x1. Triple (3 vidrios con precio): butilo x4, espaciador x2.
        fun costo(v3: String): Double {
            val it = conSistema(item(LayoutType.GRID, 1500, 1800))
            val vid = listOf("VID", "10", "10", v3, "0", "1", "0", "1", "0", "0") // espaciador 1, butilo 1
            val q = CalculadoraPrecios.cantidades(it)
            return CalculadoraPrecios.calcular(it, precios(vidrio = vid)).costoUnitario.zona - 0.0 * q.areaM2
        }
        val q = CalculadoraPrecios.cantidades(conSistema(item(LayoutType.GRID, 1500, 1800)))
        assertEquals(q.vidrioM2 * 20 + q.vidrioPerimM * (1 + 2), costo("0"), D)
        assertEquals(q.vidrioM2 * 30 + q.vidrioPerimM * (2 + 4), costo("10"), D)
    }

    @Test fun perfilesExtensorPorLado() {
        val it = conSistema(item(LayoutType.GRID, 1500, 1800))
        it.opciones.perfiles.add("extensor:40:1")                          // lado superior = ancho
        it.opciones.perfiles.add("extensor:40:0")                          // lado izquierdo = alto
        val r = CalculadoraPrecios.calcular(it, precios(sistema(COL_EXT_40 to 3.0)))
        assertEquals(3.0 * (1.5 + 1.8), r.costoUnitario.accesorios, D)
    }

    @Test fun laminacion_esSobrePerfilNoSobreVidrio() {
        val it = conSistema(item(LayoutType.GRID, 1500, 1800))
        it.opciones.laminacionInterna = "L"
        val p = precios().copy(laminacion = listOf(listOf("L", "10")))
        val r = CalculadoraPrecios.calcular(it, p)
        // marco (48 mm de cara) + junquillos
        assertEquals(10 * r4(6.6 * 0.048 + 2 * (1.404 + 1.704) * 0.027), r.costoUnitario.zona, D)
    }

    // ---------------- Cadena de precio ----------------

    @Test fun cadenaDePrecio_sinPrecioPropio() {
        // Un paño fijo 1500x1800: marco 10/m, vidrio 100/m2.
        // costo = 6.6*10 + (1.404*1.704)*100 ; fMat = 1.2*0.9*1.18
        val it = conSistema(item(LayoutType.GRID, 1500, 1800))
        val r = CalculadoraPrecios.calcular(it, precios(sistema(COL_MARCO to 10.0), listOf("VID", "100", "0", "0", "0", "0", "0", "0", "0", "0")))
        val costo = 66.0 + r4(1.404 * 1.704) * 100
        assertEquals(costo, r.costoUnitario.total, 1e-9)
        assertEquals(costo * 1.2 * 0.9 * 1.18, r.precioUnitarioTotal, 1e-9)
    }

    @Test fun precioPropio_recibeDescuentoEImpuesto() {
        val it = conSistema(item(LayoutType.GRID, 1500, 1800))
        it.opciones.precioPropio = "300"; it.opciones.cantidad = 2
        val r = CalculadoraPrecios.calcular(it, precios(sistema(COL_MARCO to 10.0)))
        assertEquals(300 * 0.9 * 1.18, r.precioUnitarioTotal, 1e-6)       // 318.60 por ventana
        assertEquals(2 * 300 * 0.9 * 1.18, r.totalVenta, 1e-6)             // 637.20
    }

    @Test fun sinFilaElegida_cuestaCero() {
        val it = item(LayoutType.GRID, 1500, 1800)                         // no se eligió sistema ni vidrio
        val r = CalculadoraPrecios.calcular(it, precios(sistema(COL_MARCO to 10.0)))
        assertEquals(0.0, r.totalVenta, D)
    }

    @Test fun resumenPedido_impuestoSobreElTotalDescontado() {
        val it = conSistema(item(LayoutType.GRID, 1500, 1800))
        it.opciones.precioPropio = "300"; it.opciones.cantidad = 2
        val r = CalculadoraPrecios.calcular(it, precios())
        val res = CalculadoraPrecios.resumenPedido(listOf(r))
        assertEquals(540.0, res.neto, 1e-6)                                // 2 x 300 x 0.9
        assertEquals(97.2, res.impuesto, 1e-6)                             // 18 %
        assertEquals(637.2, res.total, 1e-6)
    }

    @Test fun unidadesDeExtras() {
        val it = conSistema(item(LayoutType.GRID, 1500, 1800))
        val q = CalculadoraPrecios.cantidades(it)
        fun costo(unidad: Int): Double {
            val e = FilaSeleccion("E", "10", unidad)
            it.opciones.extras.clear(); it.opciones.extras.add("E")
            return CalculadoraPrecios.calcular(it, precios(extras = listOf(e))).costoUnitario.accesorios
        }
        assertEquals(10.0, costo(0), D)                                    // pieza
        assertEquals(10 * q.areaM2, costo(1), D)                           // área
        assertEquals(10 * 2 * (1.5 + 1.8), costo(2), D)                    // perímetro
        assertEquals(10 * (1.5 + 2 * 1.8), costo(3), D)                    // pendientes (inferido)
        assertEquals(15.0, costo(4), D)                                    // ancho
        assertEquals(18.0, costo(5), D)                                    // alto
    }

    @Test fun mosquitero_conMedidasDeRed() {
        val it = conSistema(item(LayoutType.GRID, 1500, 1800))
        it.paneMechanisms[0] = "4"; it.paneMosqNet[0] = true
        it.opciones.mosquitero = "M"
        val p = precios().copy(mosquitera = listOf(listOf("M", "10", "2", "1", "0.5", "0.25", "3")))
        val r = CalculadoraPrecios.calcular(it, p)
        val w = 1.404 + 0.026; val h = 1.704 + 0.026                       // red = hueco + 13 mm por lado
        val esperado = w * h * 10 + 2 * (w + h) * (2 + 1) + (4 * 0.5 + 4 * 0.25 + 2 * 3)
        assertEquals(esperado, r.costoUnitario.accesorios, 1e-9)
    }

    // ---------------- Advertencias y copia ----------------

    @Test fun advertencias_pedidoCompleto_noAvisaNada() {
        val it = conSistema(item(LayoutType.GRID, 1500, 1800))
        val p = precios(
            sis = sistema(COL_MARCO to 5.0, COL_HOJA to 6.0),
            vidrio = listOf("VID", "10", "0", "0", "0", "0", "0", "0", "0", "0"),
        )
        assertEquals(emptyList<String>(), CalculadoraPrecios.advertencias(it, p))
    }

    @Test fun advertencias_sinSistemaNiPlan_avisa() {
        val it = item(LayoutType.GRID, 1500, 1800)
        val a = CalculadoraPrecios.advertencias(it, precios())
        assertEquals(true, a.any { "sistema de ventanas" in it })
        assertEquals(true, a.any { "plan" in it })
    }

    @Test fun advertencias_sistemaSinPrecioDeMarco_avisa() {
        val it = conSistema(item(LayoutType.GRID, 1500, 1800))
        val a = CalculadoraPrecios.advertencias(it, precios(sis = sistema(COL_HOJA to 6.0)))
        assertEquals(true, a.any { "perfil de marco" in it })
    }

    @Test fun advertencias_kitFaltante_porApertura() {
        val it = conSistema(item(LayoutType.GRID, 1500, 1800))
        it.paneMechanisms[0] = "4"
        val a = CalculadoraPrecios.advertencias(it, precios(sis = sistema(COL_MARCO to 5.0, COL_HOJA to 6.0)))
        assertEquals(true, a.any { "practicable" in it })
    }

    @Test fun copiar_esIndependiente() {
        val o = conSistema(item(LayoutType.GRID, 1500, 1800))
        o.paneMechanisms[0] = "4"; o.opciones.extras.add("E")
        val c = o.copiar(2)
        c.paneMechanisms[0] = "1"; c.opciones.extras.add("F"); c.sectionWidths[0] = 999f; c.opciones.cantidad = 5
        assertEquals(2, c.index)
        assertEquals("4", o.paneMechanisms[0])
        assertEquals(setOf("E"), o.opciones.extras.toSet())
        assertEquals(1500.0, o.sectionWidths[0].toDouble(), 0.0)
        assertEquals(1, o.opciones.cantidad)
        assertEquals("SIS", c.opciones.sistemaVentana)
    }

    @Test fun desglosePorConcepto_sumaElCostoYElPrecioDeLaVentana() {
        val it = conSistema(item(LayoutType.GRID, 1500, 1800, cols = 2))
        it.paneMechanisms[0] = "4"
        it.opciones.adicionales.add("dorsill")
        it.opciones.perfiles.add("juntas:1"); it.opciones.perfiles.add("extensor:40:0")
        val p = precios(
            sis = sistema(CalculadoraPrecios.COL_MARCO to 5.0, CalculadoraPrecios.COL_IMPOSTA to 4.0, CalculadoraPrecios.COL_HOJA to 4.0,
                CalculadoraPrecios.COL_KIT_VERTICAL to 20.0, CalculadoraPrecios.COL_DORSILL to 5.0, CalculadoraPrecios.COL_JUNTA to 5.0, CalculadoraPrecios.COL_EXT_40 to 3.0),
            vidrio = listOf("VID", "10", "0", "0", "0", "0", "0", "0", "0", "0"),
        )
        val r = CalculadoraPrecios.calcular(it, p)
        assertEquals(r.costoUnitario.total, r.desglose.sumOf { l -> l.costo }, 1e-6)
        assertEquals(r.precioUnitario.total, r.desglose.sumOf { l -> l.precio }, 1e-6)
        assertEquals(1.5, r.desglose.first { l -> l.codigo == "O" }.cantidad, D)       // dorsill = ancho
        assertEquals(1.8, r.desglose.first { l -> l.codigo == "T" }.cantidad, D)       // extensor 40 lado izquierdo = alto
        assertEquals(1.5, r.desglose.first { l -> l.codigo == "Q" }.cantidad, D)       // junta arriba = ancho
    }

    @Test fun puertaFrancesa_cadaHojaConSuPropioSandwichYBarras() {
        val base = item(LayoutType.GRID, 1500, 2100).apply { paneMechanisms[0] = "F:LCF_RC" }
        val todoVidrio = CalculadoraPrecios.cantidades(base)
        check(todoVidrio.sandwichM2 == 0.0) { "sin marcar no hay sándwich" }
        val v = todoVidrio.vidrioM2
        // hoja 2 (derecha) en sándwich: el vidrio total baja y aparece el sándwich; las dos hojas son iguales
        val it = item(LayoutType.GRID, 1500, 2100).apply { paneMechanisms[0] = "F:LCF_RC"; paneSandwichSub[0] = 2; paneSandwich[0] = true }
        val q = CalculadoraPrecios.cantidades(it)
        check(q.sandwichM2 > 0.0 && Math.abs(q.sandwichM2 * 2 - v) < 0.002) { "sándwich=${q.sandwichM2} vidrio=${v}" }
        check(Math.abs(q.vidrioM2 - q.sandwichM2) < 0.002) { "una hoja vidrio y la otra sándwich" }
        // barras solo en la hoja 2: "0x0 2x1"
        val b = item(LayoutType.GRID, 1500, 2100).apply { paneMechanisms[0] = "F:LCF_RC"; paneBars[0] = "0x0 2x1" }
        val qb = CalculadoraPrecios.cantidades(b)
        check(qb.barrasM > 0.0) { "debe haber barras en la hoja derecha" }
    }

}
