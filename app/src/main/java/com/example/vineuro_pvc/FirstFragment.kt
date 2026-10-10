package com.example.vineuro_pvc

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.vineuro_pvc.data.ClientRepository
import com.example.vineuro_pvc.data.Pedido
import com.example.vineuro_pvc.data.PedidoRepository
import com.example.vineuro_pvc.databinding.FragmentFirstBinding
import kotlinx.coroutines.launch
import java.util.Calendar

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

    override fun onResume() {
        super.onResume()
        // La cabecera azul ya muestra la marca: se oculta la barra superior solo en el menú principal
        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.hide()
        actualizarResumen()
    }

    override fun onPause() {
        super.onPause()
        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.show()
    }

    /** Llena la cabecera con datos reales del teléfono (clientes, pedidos, ventanas y último pedido). */
    private fun actualizarResumen() {
        val ctx = context ?: return
        val pedidos = PedidoRepository(ctx).obtenerPedidos()
        val totalClientes = ClientRepository(ctx).obtenerClientes().size

        binding.tvSaludo.text = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Buenos días 👋"
            in 12..18 -> "Buenas tardes 👋"
            else -> "Buenas noches 👋"
        }
        binding.tvStatClientes.text = totalClientes.toString()
        binding.tvStatPedidos.text = pedidos.size.toString()
        binding.tvStatVentanas.text = pedidos.sumOf { it.totalVentanas }.toString()

        val ultimo = pedidos.maxByOrNull { it.fechaActualizacion }
        binding.tvSubUltimoPedido.text = if (ultimo != null) {
            val precios = com.example.vineuro_pvc.data.PreciosRepository(ctx).obtener()
            val simbolo = precios.moneda.firstOrNull()?.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "S/"
            val total = com.example.vineuro_pvc.data.CalculadoraPrecios.totalPedido(ultimo.items, precios)
            String.format(java.util.Locale.US, "%s · %s · %s %.2f", ultimo.codigo, ultimo.clienteNombre, simbolo, total)
        } else {
            "Aún no hay pedidos guardados"
        }
        binding.tvSubPedidos.text = when (pedidos.size) {
            0 -> "Ver y abrir pedidos guardados"
            1 -> "1 pedido guardado"
            else -> "${pedidos.size} pedidos guardados"
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
            putString("cliente_id", pedido.clienteId)
            putString("nombre_cliente", pedido.clienteNombre)
        }
        findNavController().navigate(R.id.CalculoPedidoFragment, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
