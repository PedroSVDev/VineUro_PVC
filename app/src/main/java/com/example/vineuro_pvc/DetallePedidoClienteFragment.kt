package com.example.vineuro_pvc

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.vineuro_pvc.data.ClientRepository
import com.example.vineuro_pvc.data.Cliente
import com.example.vineuro_pvc.databinding.FragmentDetallePedidoClienteBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class DetallePedidoClienteFragment : Fragment() {

    private var _binding: FragmentDetallePedidoClienteBinding? = null
    private val binding get() = _binding!!

    private lateinit var clientRepository: ClientRepository
    private var clienteId: String = ""
    private var cliente: Cliente? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDetallePedidoClienteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        clientRepository = ClientRepository(requireContext())
        clienteId = arguments?.getString("cliente_id") ?: ""
        cliente = clientRepository.obtenerClientePorId(clienteId)

        val c = cliente
        if (c == null) {
            Toast.makeText(requireContext(), "Error: Cliente no encontrado", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }

        binding.tvNombreCliente.text = c.nombreCompleto
        val contacto = mutableListOf<String>()
        if (c.telefono.isNotEmpty()) contacto.add("Tel: ${c.telefono}")
        if (c.email.isNotEmpty()) contacto.add(c.email)
        binding.tvContactoCliente.text = if (contacto.isNotEmpty()) contacto.joinToString(" | ") else "Sin datos de contacto adicionales"

        binding.etDireccion.setText(c.direccion)
        binding.etNota.setText(c.nota)

        // Botón: Sugerir con IA
        binding.btnSugerirIa.setOnClickListener { mostrarSugerenciaIA() }

        // Botón: Continuar al Cálculo
        binding.btnContinuarPedido.setOnClickListener {
            guardarDireccionYNota()
            irACalculo(usarSugerenciaIa = false)
        }
    }

    /** Guarda en el cliente la dirección y la nota escritas (local + Supabase) si cambiaron. */
    private fun guardarDireccionYNota() {
        val c = cliente ?: return
        val direccion = binding.etDireccion.text?.toString()?.trim().orEmpty()
        val nota = binding.etNota.text?.toString()?.trim().orEmpty()
        if (direccion != c.direccion || nota != c.nota) {
            val actualizado = c.copy(direccion = direccion, nota = nota)
            clientRepository.guardarCliente(actualizado)
            cliente = actualizado
        }
    }

    private fun irACalculo(usarSugerenciaIa: Boolean) {
        val bundle = Bundle().apply {
            putString("cliente_id", clienteId)
            putString("nombre_cliente", cliente?.nombreCompleto)
            if (usarSugerenciaIa) putBoolean("usar_sugerencia_ia", true)
        }
        findNavController().navigate(R.id.CalculoPedidoFragment, bundle)
    }

    private fun mostrarSugerenciaIA() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sugerencia de IA ✨")
            .setMessage("Basado en tendencias actuales y espacios residenciales, te sugerimos iniciar con un diseño de: \n\n• Ventana Corrediza de 2 Paneles (Estándar)\n• Altura: 1800 mm\n• Ancho: 1500 mm\n\n¿Deseas aplicar esta sugerencia a la nueva orden?")
            .setPositiveButton("SÍ, APLICAR") { dialog, _ ->
                guardarDireccionYNota()
                irACalculo(usarSugerenciaIa = true)
                dialog.dismiss()
            }
            .setNegativeButton("CANCELAR") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
