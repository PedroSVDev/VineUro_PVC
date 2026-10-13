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
 * Pedidos guardados (local + Supabase), agrupados por cliente.
 * Tocar el cliente despliega sus pedidos; tocar un pedido pide confirmación y lo abre;
 * mantener presionado un pedido lo elimina.
 */
class PedidosFragment : Fragment() {

    private var _binding: FragmentPedidosBinding? = null
    private val binding get() = _binding!!

    private lateinit var repo: PedidoRepository
    private lateinit var adapter: PedidosAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPedidosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo = PedidoRepository(requireContext())

        adapter = PedidosAdapter(
            onClick = { confirmarAbrir(it) },
            onLongClick = { confirmarEliminar(it) },
        )
        adapter.precios = com.example.vineuro_pvc.data.PreciosRepository(requireContext()).obtener()
        adapter.codigosClientes = com.example.vineuro_pvc.data.ClientRepository(requireContext())
            .obtenerClientes().associate { it.id to it.codigo }
        binding.rvPedidos.adapter = adapter

        binding.etSearchPedido.doOnTextChanged { text, _, _, _ -> mostrar(repo.buscarPedidos(text?.toString() ?: "")) }

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
        // Al buscar se despliegan los clientes encontrados para ver sus pedidos de una vez
        val buscando = !binding.etSearchPedido.text.isNullOrBlank()
        adapter.submitList(lista, desplegarTodo = buscando)
    }

    /** Confirmación antes de abrir un pedido (como en la app original). */
    private fun confirmarAbrir(p: Pedido) {
        val ventanas = if (p.totalVentanas == 1) "1 ventana" else "${p.totalVentanas} ventanas"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("¿Abrir el pedido ${p.codigoVisible}?")
            .setMessage("${p.clienteNombre.ifBlank { "Cliente sin nombre" }}\n${p.fechaActualizacionTexto}  ·  $ventanas")
            .setPositiveButton("ABRIR") { d, _ -> d.dismiss(); abrirPedido(p) }
            .setNegativeButton("CANCELAR") { d, _ -> d.dismiss() }
            .show()
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
            .setTitle("¿Eliminar el pedido ${p.codigoVisible}?")
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
