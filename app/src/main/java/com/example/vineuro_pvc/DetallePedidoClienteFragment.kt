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

/**
 * Fragmento para ingresar dirección y notas del cliente antes de continuar con la orden.
 */
class DetallePedidoClienteFragment : Fragment() {

    private var _binding: FragmentDetallePedidoClienteBinding? = null
    private val binding get() = _binding!!

    private lateinit var clientRepository: ClientRepository
    private var clienteActual: Cliente? = null

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

        val dniCliente = arguments?.getString("dni_cliente") ?: ""
        if (dniCliente.isNotEmpty()) {
            clienteActual = clientRepository.obtenerClientePorDni(dniCliente)
        }

        clienteActual?.let { cliente ->
            cargarDatosCliente(cliente)
        } ?: run {
            Toast.makeText(requireContext(), "No se pudo cargar la información del cliente", Toast.LENGTH_SHORT).show()
        }

        binding.btnContinuarPedido.setOnClickListener {
            guardarYContinuar()
        }
    }

    private fun cargarDatosCliente(cliente: Cliente) {
        binding.tvCodigoCliente.text = cliente.codigoCliente
        binding.tvNombreCliente.text = cliente.nombreCompleto
        binding.tvDniCliente.text = getString(R.string.label_dni_format, cliente.dni)

        val contacto = buildString {
            if (cliente.telefono.isNotEmpty()) append("Tel: ${cliente.telefono}")
            if (cliente.email.isNotEmpty()) {
                if (isNotEmpty()) append(" | ")
                append(cliente.email)
            }
        }.ifEmpty { "Sin teléfono/correo registrado" }

        binding.tvContactoCliente.text = contacto

        if (cliente.direccion.isNotEmpty()) {
            binding.etDireccion.setText(cliente.direccion)
        }
        if (cliente.nota.isNotEmpty()) {
            binding.etNota.setText(cliente.nota)
        }
    }

    private fun guardarYContinuar() {
        val cliente = clienteActual ?: return
        val direccion = binding.etDireccion.text.toString().trim()
        val nota = binding.etNota.text.toString().trim()

        if (direccion.isEmpty()) {
            binding.tilDireccion.error = getString(R.string.error_direccion_requerida)
            return
        } else {
            binding.tilDireccion.error = null
        }

        val clienteActualizado = cliente.copy(
            direccion = direccion,
            nota = nota,
        )

        clientRepository.guardarCliente(clienteActualizado)

        val bundle = Bundle().apply {
            putString("dni_cliente", cliente.dni)
            putString("nombre_cliente", "${cliente.nombreCompleto} : ${cliente.codigoCliente}")
        }

        findNavController().navigate(
            R.id.action_DetallePedidoClienteFragment_to_CalculoPedidoFragment,
            bundle,
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
