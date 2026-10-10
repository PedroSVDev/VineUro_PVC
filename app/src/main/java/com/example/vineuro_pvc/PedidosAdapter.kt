package com.example.vineuro_pvc

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.vineuro_pvc.data.CalculadoraPrecios
import com.example.vineuro_pvc.data.Pedido
import com.example.vineuro_pvc.data.Precios
import com.example.vineuro_pvc.databinding.ItemPedidoBinding
import java.util.Locale

class PedidosAdapter(
    private val onClick: (Pedido) -> Unit,
    private val onLongClick: (Pedido) -> Unit,
    /** Lista de precios vigente, para mostrar el total de cada pedido. */
    var precios: Precios? = null,
) : RecyclerView.Adapter<PedidosAdapter.PedidoViewHolder>() {

    private var lista: List<Pedido> = emptyList()

    fun submitList(nueva: List<Pedido>) {
        lista = nueva
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PedidoViewHolder =
        PedidoViewHolder(ItemPedidoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: PedidoViewHolder, position: Int) = holder.bind(lista[position])

    override fun getItemCount(): Int = lista.size

    inner class PedidoViewHolder(private val b: ItemPedidoBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(p: Pedido) {
            b.tvBadgeVentanas.text = p.totalVentanas.toString()
            b.tvCodigoPedido.text = p.codigo
            b.tvClientePedido.text = p.clienteNombre.ifBlank { "Cliente sin nombre" }
            val ventanas = if (p.totalVentanas == 1) "1 ventana" else "${p.totalVentanas} ventanas"
            val total = precios?.let { pr ->
                val simbolo = pr.moneda.firstOrNull()?.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "S/"
                String.format(Locale.US, " · %s %.2f", simbolo, CalculadoraPrecios.totalPedido(p.items, pr))
            } ?: ""
            b.tvResumenPedido.text = String.format(Locale.US, "%s · %.2f m²%s · %s", ventanas, p.areaTotalM2, total, p.fechaActualizacionTexto)
            b.root.setOnClickListener { onClick(p) }
            b.root.setOnLongClickListener { onLongClick(p); true }
        }
    }
}
