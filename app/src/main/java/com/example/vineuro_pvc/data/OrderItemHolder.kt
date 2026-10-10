package com.example.vineuro_pvc.data

/** Datos que el diseñador le pasa a la vista previa del PDF. */
object OrderItemHolder {
    var items: List<OrderItem> = emptyList()
    var clientName: String = ""
    /** id del cliente (para leer su dirección, teléfono y correo). */
    var clienteId: String = ""
    /** Código del pedido (PED-...), se imprime como "ID de oferta". */
    var codigoPedido: String = ""
}
