package com.example.vineuro_pvc

import com.example.vineuro_pvc.data.Unidades
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.util.LruCache
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

    /** Miniaturas de la primera ventana de cada pedido (se dibujan una vez y se guardan). */
    private val miniaturas = LruCache<String, Bitmap>(60)
    private var dibujante: com.example.vineuro_pvc.views.WindowPreviewView? = null

    private fun miniatura(ctx: android.content.Context, p: Pedido): Bitmap? {
        val item = p.items.firstOrNull() ?: return null
        val clave = "${p.id}:${p.fechaActualizacion}"
        miniaturas.get(clave)?.let { return it }
        return runCatching {
            val lado = (64 * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(64)
            val bmp = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
            val d = dibujante ?: com.example.vineuro_pvc.views.WindowPreviewView(ctx).also { dibujante = it }
            d.drawItem(Canvas(bmp), RectF(2f, 2f, lado - 2f, lado - 2f), item, showTechnicalDetails = false, isCompactMode = true)
            miniaturas.put(clave, bmp)
            bmp
        }.getOrNull()
    }

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
            b.imgMiniatura.setImageBitmap(miniatura(b.root.context, p))
            b.tvCodigoPedido.text = p.codigo
            val cliente = p.clienteNombre.ifBlank { "Cliente sin nombre" }
            b.tvClientePedido.text = if (p.idOferta.isBlank()) cliente else "$cliente : ${p.idOferta}"
            val ventanas = if (p.totalVentanas == 1) "1 ventana" else "${p.totalVentanas} ventanas"
            val total = precios?.let { pr ->
                val simbolo = pr.moneda.firstOrNull()?.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "S/"
                String.format(Locale.US, " · %s %.2f", simbolo, CalculadoraPrecios.totalPedido(p.items, pr))
            } ?: ""
            b.tvResumenPedido.text = String.format(Locale.US, "%s · %s%s · %s", ventanas, Unidades.areaM2(p.areaTotalM2), total, p.fechaActualizacionTexto)
            if (p.direccion.isNotBlank()) b.tvResumenPedido.text = "${b.tvResumenPedido.text}\n${p.direccion}"
            b.root.setOnClickListener { onClick(p) }
            b.root.setOnLongClickListener { onLongClick(p); true }
        }
    }
}
