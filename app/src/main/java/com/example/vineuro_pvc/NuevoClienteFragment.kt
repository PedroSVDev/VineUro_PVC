package com.example.vineuro_pvc

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.vineuro_pvc.data.ClientRepository
import com.example.vineuro_pvc.data.Cliente
import com.example.vineuro_pvc.databinding.FragmentNuevoClienteBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Fragmento para el registro manual de un Nuevo Cliente sin búsqueda por API.
 */
class NuevoClienteFragment : Fragment() {

    private var _binding: FragmentNuevoClienteBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentNuevoClienteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Botón Guardar Cliente
        binding.btnGuardarCliente.setOnClickListener {
            guardarCliente()
        }
    }

    private fun guardarCliente() {
        val nombres = binding.etNombres.text.toString().trim()
        val apellidos = binding.etApellidos.text.toString().trim()
        val telefono = binding.etTelefono.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()

        if (nombres.isEmpty()) {
            binding.tilNombres.error = "Ingrese el nombre"
            return
        } else {
            binding.tilNombres.error = null
        }

        if (apellidos.isEmpty()) {
            binding.tilApellidos.error = "Ingrese los apellidos"
            return
        } else {
            binding.tilApellidos.error = null
        }

        // Teléfono opcional, pero si se escribe debe tener exactamente 9 dígitos
        if (telefono.isNotEmpty() && !Regex("^\\d{9}$").matches(telefono)) {
            binding.tilTelefono.error = "El teléfono debe tener 9 dígitos"
            return
        } else {
            binding.tilTelefono.error = null
        }

        val clientRepository = ClientRepository(requireContext())

        // El cliente se identifica por su id (UUID generado automáticamente)
        val nuevoCliente = Cliente(
            nombres = nombres,
            apellidos = apellidos,
            telefono = telefono,
            email = email,
        )

        // Como "such customer is exist" de la original: avisa si ya hay un cliente con el mismo nombre
        val repetido = clientRepository.obtenerClientes().any {
            it.nombreCompleto.equals(nuevoCliente.nombreCompleto, ignoreCase = true)
        }
        if (repetido) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Cliente repetido")
                .setMessage("Ya existe un cliente llamado ${nuevoCliente.nombreCompleto}. ¿Crear otro con el mismo nombre?")
                .setPositiveButton("CREAR") { _, _ -> registrar(clientRepository, nuevoCliente) }
                .setNegativeButton("CANCELAR", null)
                .show()
        } else {
            registrar(clientRepository, nuevoCliente)
        }
    }

    private fun registrar(clientRepository: ClientRepository, nuevoCliente: Cliente) {
        if (clientRepository.guardarCliente(nuevoCliente)) {
            Toast.makeText(requireContext(), "Cliente ${nuevoCliente.nombreCompleto} guardado", Toast.LENGTH_SHORT).show()

            // Ir directo a "Información del Cliente". Al volver atrás no se regresa a este formulario.
            val bundle = Bundle().apply { putString("cliente_id", nuevoCliente.id) }
            val opciones = NavOptions.Builder()
                .setPopUpTo(R.id.NuevoClienteFragment, true)
                .build()
            findNavController().navigate(R.id.DetallePedidoClienteFragment, bundle, opciones)
        } else {
            Toast.makeText(requireContext(), "Error al guardar el cliente", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
