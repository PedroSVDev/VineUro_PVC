package com.example.vineuro_pvc

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.vineuro_pvc.data.CalculadoraPrecios
import com.example.vineuro_pvc.data.Pedido
import com.example.vineuro_pvc.data.Precios
import com.example.vineuro_pvc.databinding.ItemGrupoClienteBinding
import com.example.vineuro_pvc.databinding.ItemPedidoBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pedidos guardados AGRUPADOS POR CLIENTE (como en la app original):
 * - Fila del cliente: nombre, código (CLI-0001), cuántos pedidos tiene y la fecha del último.
 *   Al tocarla se despliegan (o se ocultan) sus pedidos.
 * - Debajo, cada pedido: N°, fecha y hora, ventanas, m² y total. Tocar -> abrir (con confirmación);
 *   mantener presionado -> eliminar.
 */
class PedidosAdapter(
    private val onClick: (Pedido) -> Unit,
    private val onLongClick: (Pedido) -> Unit,
    /** Lista de precios vigente, para mostrar el total de cada pedido. */
    var precios: Precios? = null,
    /** id del cliente -> código corto (CLI-0001). */
    var codigosClientes: Map<String, String> = emptyMap(),
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private data class Grupo(val clave: String, val nombre: String, val clienteId: String, val pedidos: List<Pedido>)

    private sealed class Fila {
        data class Cabecera(val grupo: Grupo, val abierto: Boolean) : Fila()
        data class Item(val pedido: Pedido, val ultimoDelGrupo: Boolean) : Fila()
    }

    private var grupos: List<Grupo> = emptyList()
    private val abiertos = mutableSetOf<String>()
    private var filas: List<Fila> = emptyList()
    private val formatoFecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    /**
     * Carga la lista. [desplegarTodo] = true al buscar, para ver de una vez los pedidos encontrados.
     */
    fun submitList(nueva: List<Pedido>, desplegarTodo: Boolean = false) {
        grupos = nueva
            .groupBy { it.clienteId.ifBlank { "nombre:" + it.clienteNombre.trim().lowercase(Locale.ROOT) } }
            .map { (clave, lista) ->
                val ordenados = lista.sortedByDescending { it.fechaActualizacion }
                Grupo(clave, ordenados.first().clienteNombre.ifBlank { "Cliente sin nombre" }, ordenados.first().clienteId, ordenados)
            }
            .sortedByDescending { g -> g.pedidos.first().fechaActualizacion }
        if (desplegarTodo) abiertos.addAll(grupos.map { it.clave })
        reconstruir()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun reconstruir() {
        val lista = mutableListOf<Fila>()
        grupos.forEach { g ->
            val abierto = g.clave in abiertos
            lista.add(Fila.Cabecera(g, abierto))
            if (abierto) g.pedidos.forEachIndexed { i, p -> lista.add(Fila.Item(p, i == g.pedidos.lastIndex)) }
        }
        filas = lista
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = if (filas[position] is Fila.Cabecera) TIPO_CABECERA else TIPO_PEDIDO

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TIPO_CABECERA) {
            CabeceraViewHolder(ItemGrupoClienteBinding.inflate(inflater, parent, false))
        } else {
            PedidoViewHolder(ItemPedidoBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val f = filas[position]) {
            is Fila.Cabecera -> (holder as CabeceraViewHolder).bind(f)
            is Fila.Item -> (holder as PedidoViewHolder).bind(f)
        }
    }

    override fun getItemCount(): Int = filas.size

    private inner class CabeceraViewHolder(private val b: ItemGrupoClienteBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(f: Fila.Cabecera) {
            val g = f.grupo
            b.tvNombreGrupo.text = g.nombre
            b.tvInicialesGrupo.text = g.nombre.split(" ").filter { it.isNotBlank() }.take(2)
                .joinToString("") { it.first().uppercase() }.ifBlank { "?" }
            val partes = mutableListOf<String>()
            codigosClientes[g.clienteId]?.takeIf { it.isNotBlank() }?.let { partes.add(it) }
            partes.add(if (g.pedidos.size == 1) "1 pedido" else "${g.pedidos.size} pedidos")
            partes.add("último ${formatoFecha.format(Date(g.pedidos.first().fechaActualizacion))}")
            b.tvDetalleGrupo.text = partes.joinToString("  ·  ")
            b.tvCantidadGrupo.text = g.pedidos.size.toString()
            b.ivFlechaGrupo.rotation = if (f.abierto) 270f else 90f
            b.root.setOnClickListener {
                if (!abiertos.remove(g.clave)) abiertos.add(g.clave)
                reconstruir()
            }
        }
    }

    private inner class PedidoViewHolder(private val b: ItemPedidoBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(f: Fila.Item) {
            val p = f.pedido
            // Sangría para que se vea "dentro" del cliente
            (b.root.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                val d = b.root.resources.displayMetrics.density
                lp.marginStart = (40 * d).toInt()
                lp.topMargin = (4 * d).toInt()
                lp.bottomMargin = ((if (f.ultimoDelGrupo) 10 else 2) * d).toInt()
                b.root.layoutParams = lp
            }
            b.tvBadgeVentanas.text = p.totalVentanas.toString()
            b.tvCodigoPedido.text = "Pedido ${p.codigoVisible}"
            b.tvClientePedido.text = formatoFecha.format(Date(p.fechaActualizacion))
            val ventanas = if (p.totalVentanas == 1) "1 ventana" else "${p.totalVentanas} ventanas"
            val total = precios?.let { pr ->
                val simbolo = pr.moneda.firstOrNull()?.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "S/"
                String.format(Locale.US, " · %s %.2f", simbolo, CalculadoraPrecios.totalPedido(p.items, pr))
            } ?: ""
            b.tvResumenPedido.text = String.format(Locale.US, "%s · %.2f m²%s", ventanas, p.areaTotalM2, total)
            b.tvResumenPedido.visibility = View.VISIBLE
            b.root.setOnClickListener { onClick(p) }
            b.root.setOnLongClickListener { onLongClick(p); true }
        }
    }

    companion object {
        private const val TIPO_CABECERA = 0
        private const val TIPO_PEDIDO = 1
    }
}
