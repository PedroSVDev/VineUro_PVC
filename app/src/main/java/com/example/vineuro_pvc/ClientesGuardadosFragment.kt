package com.example.vineuro_pvc

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.vineuro_pvc.data.ClientRepository
import com.example.vineuro_pvc.data.Cliente
import com.example.vineuro_pvc.databinding.FragmentClientesGuardadosBinding
import kotlinx.coroutines.launch

/**
 * Fragmento que lista todos los clientes guardados localmente y sincronizados con Supabase.
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

        adapter = ClientesAdapter { cliente ->
            mostrarDetallesCliente(cliente)
        }

        binding.rvClientesGuardados.adapter = adapter

        // Búsqueda en tiempo real
        binding.etSearchCliente.doOnTextChanged { text, _, _, _ ->
            cargarClientes(text?.toString() ?: "")
        }

        cargarClientes("")

        // Sincronización automática con Supabase Cloud
        lifecycleScope.launch {
            val clientesSincronizados = clientRepository.sincronizarConSupabase()
            if (_binding != null && binding.etSearchCliente.text.isNullOrBlank()) {
                if (clientesSincronizados.isEmpty()) {
                    binding.rvClientesGuardados.visibility = View.GONE
                    binding.layoutEmptyState.visibility = View.VISIBLE
                } else {
                    binding.rvClientesGuardados.visibility = View.VISIBLE
                    binding.layoutEmptyState.visibility = View.GONE
                    adapter.submitList(clientesSincronizados)
                }
            }
        }
    }

    private fun cargarClientes(query: String) {
        val lista = if (query.isBlank()) {
            clientRepository.obtenerClientes()
        } else {
            clientRepository.buscarClientes(query)
        }

        if (lista.isEmpty()) {
            binding.rvClientesGuardados.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.VISIBLE
        } else {
            binding.rvClientesGuardados.visibility = View.VISIBLE
            binding.layoutEmptyState.visibility = View.GONE
            adapter.submitList(lista)
        }
    }

    private fun mostrarDetallesCliente(cliente: Cliente) {
        val bundle = Bundle().apply {
            putString("dni_cliente", cliente.dni)
        }
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
