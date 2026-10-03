package com.example.vineuro_pvc

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.vineuro_pvc.databinding.FragmentCompartirDatosBinding

/**
 * Fragmento para la pantalla de Intercambio de Datos Bluetooth.
 */
class CompartirDatosFragment : Fragment() {

    private var _binding: FragmentCompartirDatosBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCompartirDatosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnObtenerArchivos.setOnClickListener {
            Toast.makeText(requireContext(), "Obtener archivos vía Bluetooth en desarrollo", Toast.LENGTH_SHORT).show()
        }

        binding.btnEnviarPrecios.setOnClickListener {
            Toast.makeText(requireContext(), "Enviar precios vía Bluetooth en desarrollo", Toast.LENGTH_SHORT).show()
        }

        binding.btnEnviarPedidos.setOnClickListener {
            Toast.makeText(requireContext(), "Enviar pedidos vía Bluetooth en desarrollo", Toast.LENGTH_SHORT).show()
        }

        binding.btnVolver.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
