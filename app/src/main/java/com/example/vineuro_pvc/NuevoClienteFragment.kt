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
import com.example.vineuro_pvc.databinding.FragmentNuevoClienteBinding

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
        var dni = binding.etDni.text.toString().trim()
        val nombres = binding.etNombres.text.toString().trim()
        val apellidos = binding.etApellidos.text.toString().trim()
        val telefono = binding.etTelefono.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()

        // Hacer que el DNI sea completamente OPCIONAL. Si no hay DNI, generamos un identificador temporal.
        if (dni.isEmpty()) {
            dni = "SD-" + (1000..9999).random().toString()
        }

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

        val nuevoCliente = Cliente(
            dni = dni,
            nombres = nombres,
            apellidos = apellidos,
            telefono = telefono,
            email = email,
        )

        val clientRepository = ClientRepository(requireContext())
        val guardadoOk = clientRepository.guardarCliente(nuevoCliente)

        if (guardadoOk) {
            Toast.makeText(
                requireContext(),
                "Cliente $nombres guardado localmente.\nSincronizando con la nube...",
                Toast.LENGTH_LONG,
            ).show()
            findNavController().popBackStack()
        } else {
            Toast.makeText(
                requireContext(),
                "Error al guardar el cliente",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
