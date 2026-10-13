package com.example.vineuro_pvc.data

/**
 * Traducción de los nombres de ejemplo que venían en inglés (Precios por defecto).
 * Se aplica al leer Precios y las opciones de cada ventana, así los datos ya guardados
 * también aparecen en español. Los nombres que el usuario escribió no se tocan.
 */
object NombresEs {
    private val mapa = mapOf(
        "discount 1%" to "descuento 1%",
        "discount 3%" to "descuento 3%",
        "discount 5%" to "descuento 5%",
        "Custom PVC system 1" to "Sistema PVC personalizado 1",
        "Custom PVC system 2" to "Sistema PVC personalizado 2",
        "Custom PVC system 3" to "Sistema PVC personalizado 3",
        "PVC system A" to "Sistema PVC A",
        "PVC system B" to "Sistema PVC B",
        "PVC system C" to "Sistema PVC C",
        "common glass 4-6-4" to "vidrio común 4-6-4",
        "common glass 4-6-4-6-4" to "vidrio común 4-6-4-6-4",
        "outer sill white 200" to "alféizar exterior blanco 200",
        "outer sill white 300" to "alféizar exterior blanco 300",
        "inner sill white 300" to "alféizar interior blanco 300",
        "inner sill white 400" to "alféizar interior blanco 400",
        "marble imitation 300" to "imitación mármol 300",
        "marble imitation 400" to "imitación mármol 400",
        "white type 1" to "blanco tipo 1",
        "white type 2" to "blanco tipo 2",
        "mounting foam" to "espuma de montaje",
        "new_extra_material" to "nuevo material extra",
        "set of screws" to "juego de tornillos",
        "delivery" to "transporte",
        "dismantling" to "desmontaje",
        "finishing slopes" to "acabado de derrames",
        "inner sill install" to "instalación de alféizar interior",
        "instal to concrete" to "instalación en concreto",
        "instalation" to "instalación",
        "outer sill install" to "instalación de alféizar exterior",
        "mosq. net brown" to "mosquitero marrón",
        "mosq. net gray" to "mosquitero gris",
        "mosq. net white" to "mosquitero blanco",
        "color bordo" to "color burdeos",
        "color gray" to "color gris",
        "new_lamination" to "nueva laminación",
        "oak dark" to "roble oscuro",
        "oak light" to "roble claro",
        "alder" to "aliso",
        "ash" to "fresno",
        "beech" to "haya",
        "birch" to "abedul",
        "black" to "negro",
        "cherry" to "cerezo",
        "ebony" to "ébano",
        "green" to "verde",
        "hickory" to "nogal americano",
        "mahogany" to "caoba",
        "walnut" to "nogal",
        "dorsill" to "alféizar",
    )

    fun es(nombre: String): String = mapa[nombre.trim()] ?: nombre
}
