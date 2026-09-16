package com.example.vineuro_pvc

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.vineuro_pvc.databinding.FragmentFirstBinding

/**
 * Fragmento principal que muestra el menú de opciones.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnNuevoOrden.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }

        binding.btnUltimoPedido.setOnClickListener {
            showOptionSelected(getString(R.string.menu_ultimo_pedido))
        }

        binding.btnPedidos.setOnClickListener {
            showOptionSelected(getString(R.string.menu_pedidos))
        }

        binding.btnPrecios.setOnClickListener {
            showOptionSelected(getString(R.string.menu_precios))
        }

        binding.btnCompartirDatos.setOnClickListener {
            showOptionSelected(getString(R.string.menu_compartir_datos))
        }
    }

    private fun showOptionSelected(optionName: String) {
        Toast.makeText(requireContext(), "Opción seleccionada: $optionName", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
