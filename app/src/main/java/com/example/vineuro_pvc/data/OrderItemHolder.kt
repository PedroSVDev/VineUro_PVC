package com.example.vineuro_pvc.data

/** Datos que el diseñador le pasa a la vista previa del PDF. */
object OrderItemHolder {
    var items: List<OrderItem> = emptyList()
    var clientName: String = ""
    /** id del cliente (para leer su dirección, teléfono y correo). */
    var clienteId: String = ""
    /** Código del pedido (PED-...), se imprime como "ID de oferta". */
    var codigoPedido: String = ""
    /** true = "PDF con detalles" (2 por hoja, a color); false = "PDF solo proyectos" (6 por hoja). */
    var conDetalles: Boolean = false
    /** true = vista previa de la "Guía de corte". */
    var guiaCorte: Boolean = false
}
