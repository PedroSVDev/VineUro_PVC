package com.example.vineuro_pvc.data

/**
 * Precios con los que trabaja el gerente (US$), tomados de las capturas de su tabla de Precios. Se usan cuando el teléfono
 * aún no tiene precios guardados y con la opción "Cargar precios del gerente" de la pantalla de Precios.
 *
 * LO QUE FALTA POR COMPLETAR (no se vio en las capturas y queda en 0.00): de los sistemas de ventanas, todas las columnas
 * desde la 4 (hoja de puerta, panel sándwich, junquillo, caucho, barras, refuerzos, kits de herrajes, juntas, etc.); de los
 * cristales nuevos, las columnas 6 a 9; de los muebles, la segunda y tercera columna; y de la mosquitera, varias columnas.
 * Mientras tanto, la cotización avisa cuando una ventana usa un kit o un perfil sin precio.
 */
object PreciosGerente {
    private const val Z = "0.00"

    /** Sistema de ventanas: nombre, marco, imposta y hoja (US$ por m). El resto de las 37 columnas queda en 0.00. */
    private fun sistema(nombre: String, marco: String, imposta: String, hoja: String) =
        listOf(nombre, marco, imposta, hoja) + List(Precios.COLUMNAS_SISTEMA - 3) { Z }

    /** Cristal: nombre y precio de vidrio 1 (US$ por m²); el resto de las 9 columnas queda en 0.00. */
    private fun vidrio(nombre: String, precio: String) = listOf(nombre, precio) + List(8) { Z }

    fun precios(): Precios = Precios(
        moneda = listOf(listOf("US$", "1.00", "US$")),
        impuesto = listOf(listOf("IGV", "18.00")),
        planes = listOf(
            listOf("Descuento 10%", "10.00", "0.00", "0.00"),
            listOf("Descuento 15%", "15.00", "0.00", "0.00"),
            listOf("Descuento especial 12%", "12.00", "12.00", "0.00"),
            listOf("discount 12%", "12.00", "0.00", "0.00"),
        ),
        ventanas = listOf(
            sistema("Batiente Exterior blanco", "25.43", "25.42", "33.90"),
            sistema("Batiente Exterior económico, blanco", "25.42", "0.00", "25.42"),
            sistema("Batiente Exterior económico, color", "30.50", "0.00", "33.90"),
            sistema("Batiente Exterior, color", "50.00", "50.00", "50.00"),
            sistema("Batiente interior, blanco", "45.00", "45.00", "50.00"),
            sistema("Batiente interior, color", "50.00", "50.00", "55.00"),
            sistema("Conjunto corredera , blanco", "30.00", "0.00", "32.00"),
            sistema("Conjunto corredera,  color", "35.00", "35.00", "40.00"),
            sistema("Corredera Sliding Classic S125", "0.00", "0.00", "0.00"),
            sistema("Corredera, Triple riel triple hoja, blanco", "45.00", "40.00", "50.00"),
            sistema("Corredera, Triple riel triple hoja, color", "40.00", "45.00", "40.00"),
            sistema("Corredera, doble riel doble hoja, blanco", "35.00", "40.00", "40.00"),
            sistema("Corredera, doble riel doble hoja, color", "40.00", "40.00", "45.00"),
            sistema("Marco fijo blanco", "45.00", "0.00", "0.00"),
            sistema("Marco fijo color", "45.00", "45.00", "0.00"),
            sistema("Oscilobatiente, blanco", "35.00", "40.00", "40.00"),
            sistema("Oscilobatiente, color", "40.00", "40.00", "45.00"),
            sistema("Oscilobatiente, fijo _ blanco", "21.18", "25.42", "25.42"),
            sistema("Oscilobatiente-fijo, color", "25.00", "25.00", "25.00"),
            sistema("Osciloparalela", "40.00", "45.00", "45.00"),
            sistema("Proyectante exterior económico, blanco", "45.00", "50.00", "50.00"),
            sistema("Proyectante exterior económico, color", "50.00", "50.00", "55.00"),
            sistema("Proyectante exterior,  blanco", "45.00", "45.00", "50.00"),
            sistema("Proyectante exterior, color", "40.00", "40.00", "40.00"),
            sistema("Proyectante interior, blanco", "45.00", "45.00", "50.00"),
            sistema("Proyectante interior, color", "50.00", "55.00", "55.00"),
            sistema("Puerta Batiente Exterior,  blanco", "35.00", "40.00", "45.00"),
            sistema("Puerta Batiente Exterior,  color", "45.00", "50.00", "50.00"),
            sistema("Puerta Batiente interior,  blanco", "35.00", "40.00", "45.00"),
            sistema("Puerta Batiente interior, color", "45.00", "50.00", "50.00"),
        ),
        cristal = listOf(
            vidrio("Laminado incoloro 10mm", "65.00"),
            vidrio("Laminado incoloro 6mm", "55.00"),
            vidrio("Laminado incoloro 8mm", "80.00"),
            vidrio("Laminado pavonado 8mm", "70.00"),
            vidrio("Panel de PVC Lambiri 100 x 20 mm blanco", "100.00"),
            vidrio("Termopanel DVH 19mm", "110.00"),
            vidrio("Termopanel DVH 21mm", "120.00"),
            vidrio("Termopanel DVH 24mm", "110.00"),
            vidrio("Termopanel DVH 24mm Pavonado", "120.00"),
            vidrio("Termopanel TVH 32mm", "140.00"),
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
            listOf("Perfil PVC ANDES 60 Sliding,  color", Z, Z, Z),
            listOf("Perfil PVC Andes 38, blanco", Z, Z, Z),
            listOf("Perfil PVC Andes 38, color", Z, Z, Z),
            listOf("Perfil PVC Andes 60 Sliding, blanco", Z, Z, Z),
            listOf("Perfil PVC ELEGANS 60 Window, Blanco", Z, Z, Z),
            listOf("Perfil PVC Sliding Classic S125, blanco", Z, Z, Z),
            listOf("Perfil PVC Sliding Classic S125, color", Z, Z, Z),
            listOf("Perfil PVC Sliding Classic S75, Blanco", Z, Z, Z),
            listOf("Perfil PVC Sliding Classic S75, color", Z, Z, Z),
            listOf("Perfil PVC Super 60 Door, blanco", Z, Z, Z),
            listOf("Perfil PVC Super 60 Door, color", Z, Z, Z),
            listOf("Perfil PVC Super 60 Window, blanco", Z, Z, Z),
            listOf("Perfil PVC Super 60 Window, color", Z, Z, Z),
            listOf("Perfil PVC Ventaplast 60 Window, 2mm", Z, Z, Z),
        ),
        extras = listOf(
            FilaSeleccion("1 Kit Volkswagen", "600.00", 0),
            FilaSeleccion("2 Kits Volkswagen", "900.00", 0),
            FilaSeleccion("Diseño estilo francesa con barrotillos", "0.00", 0),
            FilaSeleccion("Embalaje de Material", "50.00", 0),
            FilaSeleccion("mounting foam", "12.30", 0),
        ),
        empleos = listOf(
            FilaSeleccion("Fabricación", "0.00", 2),
            FilaSeleccion("Fabricación - Instalación", "0.00", 0),
            FilaSeleccion("Sin Instalación", "0.00", 0),
            FilaSeleccion("Transporte", "150.00", 1),
            FilaSeleccion("Viáticos", "0.00", 0),
            FilaSeleccion("dismantling", "2.32", 0),
            FilaSeleccion("finishing slopes", "150.00", 3),
            FilaSeleccion("inner sill install", "80.00", 4),
            FilaSeleccion("instal to concrete", "12.48", 0),
            FilaSeleccion("instalación", "0.00", 1),
            FilaSeleccion("outer sill install", "70.00", 0),
        ),
        mosquitera = listOf(
            listOf("mosq. marron- net negro", "160.00", "0.00", "0.00", "0.00", "0.00", "0.00"),
            listOf("mosq. net black", "180.00", "0.00", "0.00", "0.00", "0.00", "0.00"),
            listOf("mosq. net brown", "160.00", "0.00", "0.00", "0.00", "0.00", "0.00"),
            listOf("mosq. net gray", "140.00", "3.60", "0.08", "0.10", "0.05", "0.05"),
            listOf("mosq. net white", "140.00", "3.30", "0.08", "0.09", "0.05", "0.05"),
            listOf("mosquitero marron - net negro", "160.00", "0.00", "0.00", "0.00", "0.00", "0.00"),
        ),
        laminacion = listOf(
            listOf("Antracita", "0.00"),
            listOf("Negro", "0.00"),
            listOf("Roble Dorado", "0.00"),
            listOf("color bordo", "80.00"),
            listOf("color gray", "80.00"),
            listOf("new_lamination", "0.00"),
            listOf("oak dark", "75.00"),
            listOf("oak light", "75.00"),
        ),
        // Los 6 colores del gerente primero y en su orden
        colores = listOf(
            FilaColor("Antracita", "#5E5E5C"),
            FilaColor("Blanco", "#F5F5F5"),
            FilaColor("Negro", "#3E3E3E"),
            FilaColor("Nogal", "#7C5D49"),
            FilaColor("Roble Dorado", "#AF642A"),
            FilaColor("Silver", "#D0D1D5"),
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
