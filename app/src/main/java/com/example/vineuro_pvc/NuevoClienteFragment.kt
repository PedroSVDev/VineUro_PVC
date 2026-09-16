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
import com.example.vineuro_pvc.data.DniRepository
import com.example.vineuro_pvc.data.DniResult
import com.example.vineuro_pvc.databinding.FragmentNuevoClienteBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Fragmento para el registro de un Nuevo Cliente con autocompletado por DNI y guardado local.
 */
class NuevoClienteFragment : Fragment() {

    private var _binding: FragmentNuevoClienteBinding? = null
    private val binding get() = _binding!!

    private val dniRepository = DniRepository()
    private var searchJob: Job? = null
    private var lastSearchedDni: String = ""

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

        // Configurar icono de búsqueda manual de DNI
        binding.tilDni.setEndIconOnClickListener {
            val dni = binding.etDni.text.toString().trim()
            if (dni.length == 8) {
                consultarDni(dni)
            } else {
                binding.tilDni.error = getString(R.string.error_dni_invalido)
            }
        }

        // Búsqueda automática cuando se completan 8 dígitos
        binding.etDni.doOnTextChanged { text, _, _, _ ->
            binding.tilDni.error = null
            val dni = text?.toString()?.trim() ?: ""
            if ((dni.length == 8) && (dni != lastSearchedDni)) {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(300.milliseconds)
                    consultarDni(dni)
                }
            } else if (dni.length < 8) {
                binding.tvDniStatus.visibility = View.GONE
                binding.progressDni.visibility = View.GONE
            }
        }

        // Botón Guardar Cliente
        binding.btnGuardarCliente.setOnClickListener {
            guardarCliente()
        }
    }

    private fun consultarDni(dni: String) {
        lastSearchedDni = dni
        binding.progressDni.visibility = View.VISIBLE
        binding.tvDniStatus.visibility = View.VISIBLE
        binding.tvDniStatus.text = getString(R.string.dni_buscando)
        binding.tvDniStatus.setTextColor(requireContext().getColor(R.color.menu_nuevo_orden))

        lifecycleScope.launch {
            val result = dniRepository.buscarDni(dni)
            if (_binding == null) return@launch

            binding.progressDni.visibility = View.GONE

            when (result) {
                is DniResult.Success -> {
                    binding.etNombres.setText(result.nombres)
                    binding.etApellidos.setText(result.apellidos)
                    binding.tvDniStatus.text = getString(R.string.dni_encontrado)
                    binding.tvDniStatus.setTextColor(requireContext().getColor(android.R.color.holo_green_dark))
                }

                is DniResult.Error -> {
                    binding.tvDniStatus.text = result.message
                    binding.tvDniStatus.setTextColor(requireContext().getColor(android.R.color.holo_red_dark))
                }
            }
        }
    }

    private fun guardarCliente() {
        val dni = binding.etDni.text.toString().trim()
        val nombres = binding.etNombres.text.toString().trim()
        val apellidos = binding.etApellidos.text.toString().trim()
        val telefono = binding.etTelefono.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()

        if (dni.length != 8) {
            binding.tilDni.error = getString(R.string.error_dni_invalido)
            return
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
                "Cliente $nombres $apellidos guardado con éxito",
                Toast.LENGTH_SHORT,
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
