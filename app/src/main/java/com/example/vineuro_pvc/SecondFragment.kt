package com.example.vineuro_pvc

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.vineuro_pvc.databinding.FragmentSecondBinding

/**
 * Fragmento para la opción "NUEVO ORDEN" con opciones de cliente.
 */
class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnNuevoCliente.setOnClickListener {
            findNavController().navigate(R.id.action_SecondFragment_to_NuevoClienteFragment)
        }

        binding.btnClientesGuardados.setOnClickListener {
            findNavController().navigate(R.id.action_SecondFragment_to_ClientesGuardadosFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
