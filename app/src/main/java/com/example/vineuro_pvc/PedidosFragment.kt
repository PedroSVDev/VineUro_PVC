package com.example.vineuro_pvc

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.vineuro_pvc.data.Pedido
import com.example.vineuro_pvc.data.PedidoRepository
import com.example.vineuro_pvc.databinding.FragmentPedidosBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Lista de pedidos guardados (local + Supabase). Tocar abre el pedido; mantener presionado lo elimina.
 */
class PedidosFragment : Fragment() {

    private var _binding: FragmentPedidosBinding? = null
    private val binding get() = _binding!!

    private lateinit var repo: PedidoRepository
    private lateinit var adapter: PedidosAdapter
    private var orden = Orden.RECIENTES

    /** Los cuatro órdenes del original: más antiguos, más recientes, por nombre y por cantidad de ventanas. */
    private enum class Orden(val texto: String) {
        RECIENTES("más recientes"), ANTIGUOS("más antiguos"), NOMBRE("nombre A-Z"), NOMBRE_DESC("nombre Z-A"), VENTANAS("más ventanas"), VENTANAS_ASC("menos ventanas")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPedidosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo = PedidoRepository(requireContext())

        adapter = PedidosAdapter(
            onClick = { abrirPedido(it) },
            onLongClick = { confirmarEliminar(it) },
        )
        adapter.precios = com.example.vineuro_pvc.data.PreciosRepository(requireContext()).obtener()
        binding.rvPedidos.adapter = adapter

        binding.etSearchPedido.doOnTextChanged { text, _, _, _ -> mostrar(repo.buscarPedidos(text?.toString() ?: "")) }

        binding.tvOrdenar.setOnClickListener {
            val popup = androidx.appcompat.widget.PopupMenu(requireContext(), it)
            Orden.values().forEachIndexed { i, o -> popup.menu.add(0, i, i, o.texto) }
            popup.setOnMenuItemClickListener { m ->
                orden = Orden.values()[m.itemId]
                binding.tvOrdenar.text = "Ordenar: ${orden.texto} ▾"
                mostrar(repo.buscarPedidos(binding.etSearchPedido.text?.toString() ?: ""))
                true
            }
            popup.show()
        }

        mostrar(repo.obtenerPedidos())
        sincronizar()
    }

    private fun sincronizar() {
        binding.tvEstadoSync.text = getString(R.string.pedidos_sincronizando)
        viewLifecycleOwner.lifecycleScope.launch {
            repo.sincronizar()
            if (_binding == null) return@launch
            binding.tvEstadoSync.text = getString(R.string.pedidos_sincronizados)
            mostrar(repo.buscarPedidos(binding.etSearchPedido.text?.toString() ?: ""))
        }
    }

    private fun mostrar(lista: List<Pedido>) {
        val vacio = lista.isEmpty()
        binding.rvPedidos.visibility = if (vacio) View.GONE else View.VISIBLE
        binding.layoutEmptyState.visibility = if (vacio) View.VISIBLE else View.GONE
        adapter.submitList(ordenar(lista))
    }

    private fun ordenar(l: List<Pedido>): List<Pedido> = when (orden) {
        Orden.RECIENTES -> l.sortedByDescending { it.fechaActualizacion }
        Orden.ANTIGUOS -> l.sortedBy { it.fechaActualizacion }
        Orden.NOMBRE -> l.sortedBy { it.clienteNombre.lowercase() }
        Orden.NOMBRE_DESC -> l.sortedByDescending { it.clienteNombre.lowercase() }
        Orden.VENTANAS -> l.sortedByDescending { it.totalVentanas }
        Orden.VENTANAS_ASC -> l.sortedBy { it.totalVentanas }
    }

    private fun abrirPedido(p: Pedido) {
        val bundle = Bundle().apply {
            putString("pedido_id", p.id)
            putString("cliente_id", p.clienteId)
            putString("nombre_cliente", p.clienteNombre)
        }
        findNavController().navigate(R.id.CalculoPedidoFragment, bundle)
    }

    private fun confirmarEliminar(p: Pedido) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("¿Eliminar ${p.codigo}?")
            .setMessage("Se eliminará del teléfono y de la nube. Esta acción no se puede deshacer.")
            .setIcon(android.R.drawable.ic_menu_delete)
            .setPositiveButton("ELIMINAR") { d, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val error = repo.eliminarPedido(p.id)
                    if (_binding == null) return@launch
                    mostrar(repo.buscarPedidos(binding.etSearchPedido.text?.toString() ?: ""))
                    val msg = if (error == null) "Pedido eliminado" else "Eliminado del teléfono, pero la nube respondió: $error"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                }
                d.dismiss()
            }
            .setNegativeButton("CANCELAR") { d, _ -> d.dismiss() }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
