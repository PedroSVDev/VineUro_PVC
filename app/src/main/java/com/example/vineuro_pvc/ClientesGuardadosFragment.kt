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
import com.example.vineuro_pvc.data.ClientRepository
import com.example.vineuro_pvc.data.Cliente
import com.example.vineuro_pvc.databinding.FragmentClientesGuardadosBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Lista de clientes guardados (teléfono + Supabase). Tocar abre la información del cliente;
 * el tacho de la derecha lo elimina previa confirmación.
 */
class ClientesGuardadosFragment : Fragment() {

    private var _binding: FragmentClientesGuardadosBinding? = null
    private val binding get() = _binding!!

    private lateinit var clientRepository: ClientRepository
    private lateinit var adapter: ClientesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentClientesGuardadosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        clientRepository = ClientRepository(requireContext())

        adapter = ClientesAdapter(
            onEliminarClick = { confirmarEliminar(it) },
            onClienteClick = { mostrarDetallesCliente(it) },
        )
        binding.rvClientesGuardados.adapter = adapter

        // Búsqueda en tiempo real
        binding.etSearchCliente.doOnTextChanged { _, _, _, _ -> refrescarLista() }

        refrescarLista()

        // Sincronización automática con Supabase
        viewLifecycleOwner.lifecycleScope.launch {
            clientRepository.sincronizarConSupabase()
            if (_binding != null) refrescarLista()
        }
    }

    private fun textoBusqueda(): String = binding.etSearchCliente.text?.toString() ?: ""

    private fun refrescarLista() {
        val lista = clientRepository.buscarClientes(textoBusqueda())
        val vacio = lista.isEmpty()
        binding.rvClientesGuardados.visibility = if (vacio) View.GONE else View.VISIBLE
        binding.layoutEmptyState.visibility = if (vacio) View.VISIBLE else View.GONE
        adapter.submitList(lista)
    }

    private fun confirmarEliminar(cliente: Cliente) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar cliente")
            .setMessage("¿Seguro que quieres borrar a ${cliente.nombreCompleto}?")
            .setIcon(R.drawable.ic_trash)
            .setPositiveButton("SÍ, BORRAR") { dialog, _ ->
                dialog.dismiss()
                eliminar(cliente)
            }
            .setNegativeButton("NO") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun eliminar(cliente: Cliente) {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val enNube = clientRepository.eliminarCliente(cliente.id)
            if (_binding != null) refrescarLista()
            val msg = if (enNube) {
                "Cliente ${cliente.nombreCompleto} eliminado"
            } else {
                "Cliente eliminado del teléfono. Se borrará de la nube cuando haya conexión."
            }
            Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun mostrarDetallesCliente(cliente: Cliente) {
        val bundle = Bundle().apply { putString("cliente_id", cliente.id) }
        findNavController().navigate(
            R.id.action_ClientesGuardadosFragment_to_DetallePedidoClienteFragment,
            bundle,
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
