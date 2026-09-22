package com.example.vineuro_pvc

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.vineuro_pvc.data.ClientRepository
import com.example.vineuro_pvc.databinding.FragmentDetallePedidoClienteBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class DetallePedidoClienteFragment : Fragment() {

    private var _binding: FragmentDetallePedidoClienteBinding? = null
    private val binding get() = _binding!!

    private lateinit var clientRepository: ClientRepository
    private var currentDni: String = ""

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
        currentDni = arguments?.getString("dni_cliente") ?: ""

        val cliente = clientRepository.obtenerClientePorDni(currentDni)

        if (cliente != null) {
            binding.tvNombreCliente.text = cliente.nombreCompleto
            binding.tvDniCliente.text = "DNI: ${cliente.dni}"
            binding.tvCodigoCliente.text = cliente.codigoCliente

            val contactoText = mutableListOf<String>()
            if (cliente.telefono.isNotEmpty()) contactoText.add("Tel: ${cliente.telefono}")
            if (cliente.email.isNotEmpty()) contactoText.add(cliente.email)
            binding.tvContactoCliente.text = if (contactoText.isNotEmpty()) contactoText.joinToString(" | ") else "Sin datos de contacto adicionales"

            binding.etDireccion.setText(cliente.direccion)
            binding.etNota.setText(cliente.nota)
        } else {
            Toast.makeText(requireContext(), "Error: Cliente no encontrado", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }

        // Botón: Sugerir con IA
        binding.btnSugerirIa.setOnClickListener {
            mostrarSugerenciaIA()
        }

        // Botón: Continuar al Cálculo
        binding.btnContinuarPedido.setOnClickListener {
            val direccion = binding.etDireccion.text.toString().trim()
            val nota = binding.etNota.text.toString().trim()

            if (cliente != null) {
                // Para simplificar, se asume que las propiedades no son inmutables o se ignora la actualizacion local directa
                // En una app real, actualizaríamos el repositorio aquí
            }

            val bundle = Bundle().apply {
                putString("dni_cliente", currentDni)
                putString("nombre_cliente", cliente?.nombreCompleto)
            }
            findNavController().navigate(R.id.CalculoPedidoFragment, bundle)
        }
    }

    private fun mostrarSugerenciaIA() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sugerencia de IA ✨")
            .setMessage("Basado en tendencias actuales y espacios residenciales, te sugerimos iniciar con un diseño de: \n\n• Ventana Corrediza de 2 Paneles (Estándar)\n• Altura: 1800 mm\n• Ancho: 1500 mm\n\n¿Deseas aplicar esta sugerencia a la nueva orden?")
            .setPositiveButton("SÍ, APLICAR") { dialog, _ ->
                val bundle = Bundle().apply {
                    putString("dni_cliente", currentDni)
                    putString("nombre_cliente", binding.tvNombreCliente.text.toString())
                    putBoolean("usar_sugerencia_ia", true)
                }
                findNavController().navigate(R.id.CalculoPedidoFragment, bundle)
                dialog.dismiss()
            }
            .setNegativeButton("CANCELAR") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
