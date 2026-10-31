package com.example.vineuro_pvc

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.navigation.fragment.findNavController
import com.example.vineuro_pvc.data.ClientRepository
import com.example.vineuro_pvc.data.Cliente
import com.example.vineuro_pvc.data.PedidoRepository
import com.example.vineuro_pvc.databinding.FragmentDetallePedidoClienteBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class DetallePedidoClienteFragment : Fragment() {

    private var _binding: FragmentDetallePedidoClienteBinding? = null
    private val binding get() = _binding!!

    private lateinit var clientRepository: ClientRepository
    private var clienteId: String = ""
    private var cliente: Cliente? = null

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
        clienteId = arguments?.getString("cliente_id") ?: ""
        cliente = clientRepository.obtenerClientePorId(clienteId)

        val c = cliente
        if (c == null) {
            Toast.makeText(requireContext(), "Error: Cliente no encontrado", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }

        binding.tvNombreCliente.text = c.nombreCompleto
        val contacto = mutableListOf<String>()
        if (c.telefono.isNotEmpty()) contacto.add("Tel: ${c.telefono}")
        if (c.email.isNotEmpty()) contacto.add(c.email)
        binding.tvContactoCliente.text = if (contacto.isNotEmpty()) contacto.joinToString(" | ") else "Sin datos de contacto adicionales"

        binding.etDireccion.setText(c.direccion)
        binding.etNota.setText(c.nota)

        // Botón: Sugerir con IA
        binding.btnSugerirIa.setOnClickListener { mostrarSugerenciaIA() }

        // Estado de la IA bajo el botón: se actualiza solo (clave, Internet, cuota) y al tocarlo abre la configuración
        binding.tvEstadoIa.setOnClickListener { DialogGemini(this) { actualizarEstadoIa() }.mostrar() }
        viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                actualizarEstadoIa()
                delay(3000)
            }
        }

        configurarBotonesPedido()
    }

    /** Guarda en el cliente la dirección y la nota escritas (local + Supabase) si cambiaron. */
    private fun guardarDireccionYNota() {
        val c = cliente ?: return
        val direccion = binding.etDireccion.text?.toString()?.trim().orEmpty()
        val nota = binding.etNota.text?.toString()?.trim().orEmpty()
        if (direccion != c.direccion || nota != c.nota) {
            val actualizado = c.copy(direccion = direccion, nota = nota)
            clientRepository.guardarCliente(actualizado)
            cliente = actualizado
        }
    }

    /**
     * Sin pedidos anteriores del cliente: un solo botón, NUEVO PEDIDO.
     * Con pedidos anteriores: CONTINUAR ÚLTIMO PEDIDO y, debajo, NUEVO PEDIDO.
     */
    private fun configurarBotonesPedido() {
        val b = _binding ?: return
        val ultimo = PedidoRepository(requireContext()).obtenerPedidosDeCliente(clienteId).firstOrNull { it.items.isNotEmpty() }
        if (ultimo == null) {
            b.tvUltimoPedido.visibility = View.GONE
            b.btnNuevoPedido.visibility = View.GONE
            b.btnContinuarPedido.setText(R.string.btn_nuevo_pedido)
            b.btnContinuarPedido.setOnClickListener {
                guardarDireccionYNota()
                irACalculo(usarSugerenciaIa = false)
            }
        } else {
            b.tvUltimoPedido.text = getString(R.string.label_ultimo_pedido, ultimo.codigo, ultimo.totalVentanas, ultimo.fechaActualizacionTexto)
            b.tvUltimoPedido.visibility = View.VISIBLE
            b.btnNuevoPedido.visibility = View.VISIBLE
            b.btnContinuarPedido.setText(R.string.btn_continuar_ultimo_pedido)
            b.btnContinuarPedido.setOnClickListener {
                guardarDireccionYNota()
                irACalculo(usarSugerenciaIa = false, pedidoId = ultimo.id)
            }
            b.btnNuevoPedido.setOnClickListener {
                guardarDireccionYNota()
                irACalculo(usarSugerenciaIa = false)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Al volver de otra pantalla puede haber un pedido nuevo del cliente
        if (clienteId.isNotEmpty()) configurarBotonesPedido()
    }

    private fun irACalculo(usarSugerenciaIa: Boolean, pedidoId: String? = null) {
        val bundle = Bundle().apply {
            putString("cliente_id", clienteId)
            putString("nombre_cliente", cliente?.nombreCompleto)
            if (pedidoId != null) putString("pedido_id", pedidoId)
            if (usarSugerenciaIa) putBoolean("usar_sugerencia_ia", true)
        }
        findNavController().navigate(R.id.CalculoPedidoFragment, bundle)
    }

    private fun actualizarEstadoIa() {
        val b = _binding ?: return
        val e = com.example.vineuro_pvc.data.GeminiClient.estado(requireContext())
        val color = when (e.nivel) {
            com.example.vineuro_pvc.data.EstadoIA.Nivel.LISTA -> "#15803D"
            com.example.vineuro_pvc.data.EstadoIA.Nivel.PAUSA -> "#B45309"
            com.example.vineuro_pvc.data.EstadoIA.Nivel.AVISO -> "#6B7280"
        }
        b.tvEstadoIa.setTextColor(android.graphics.Color.parseColor(color))
        b.tvEstadoIa.text = "● ${e.texto}"
    }

    private fun mostrarSugerenciaIA() {
        DialogAsesorDiseno(this) {
            guardarDireccionYNota()
            irACalculo(usarSugerenciaIa = true)
        }.mostrar()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
