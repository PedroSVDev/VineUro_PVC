package com.example.vineuro_pvc.data

/** Datos que el diseñador le pasa a la vista previa del PDF. */
object OrderItemHolder {
    var items: List<OrderItem> = emptyList()
    var clientName: String = ""
    /** id del cliente (para leer su dirección, teléfono y correo). */
    var clienteId: String = ""
    /** Código del pedido (PED-...), se imprime como "ID de oferta". */
    var codigoPedido: String = ""
    /** Dirección de la obra y ID de oferta del pedido (si están cargados; si no, se usan los del cliente / el código). */
    var direccionPedido: String = ""
    var idOfertaPedido: String = ""
    /** id interno del pedido (para leer el tipo de cambio de su cotización). */
    var pedidoId: String = ""
    /** true = "PDF con los detalles" (dibujo + lista de propiedades); false = "solo proyectos". */
    var conDetalles: Boolean = false
}
