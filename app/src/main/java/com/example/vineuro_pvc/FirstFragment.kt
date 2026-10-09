package com.example.vineuro_pvc

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.vineuro_pvc.data.Pedido
import com.example.vineuro_pvc.data.PedidoRepository
import com.example.vineuro_pvc.databinding.FragmentFirstBinding
import kotlinx.coroutines.launch

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
            abrirUltimoPedido()
        }

        binding.btnPedidos.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_PedidosFragment)
        }

        binding.btnPrecios.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_PreciosFragment)
        }

        binding.btnCompartirDatos.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_CompartirDatosFragment)
        }
    }

    private fun abrirUltimoPedido() {
        val repo = PedidoRepository(requireContext())
        val ultimo = repo.obtenerUltimoPedido()
        if (ultimo != null) {
            navegarAPedido(ultimo)
            return
        }
        // No hay nada en el teléfono: intentar traer desde Supabase
        Toast.makeText(requireContext(), "Buscando pedidos en la nube…", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val desdeNube = repo.sincronizar().maxByOrNull { it.fechaActualizacion }
            if (_binding == null) return@launch
            if (desdeNube != null) {
                navegarAPedido(desdeNube)
            } else {
                Toast.makeText(requireContext(), "Aún no hay pedidos guardados", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navegarAPedido(pedido: Pedido) {
        val bundle = Bundle().apply {
            putString("pedido_id", pedido.id)
            putString("dni_cliente", pedido.clienteDni)
            putString("nombre_cliente", pedido.clienteNombre)
        }
        findNavController().navigate(R.id.CalculoPedidoFragment, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
