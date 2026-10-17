package com.example.vineuro_pvc

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.vineuro_pvc.data.Ajustes
import com.example.vineuro_pvc.data.ClientRepository
import com.example.vineuro_pvc.data.Pedido
import com.example.vineuro_pvc.data.PedidoRepository
import com.example.vineuro_pvc.data.Unidades
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

        binding.btnPenultimoPedido.setOnClickListener { abrirPenultimoPedido() }
        binding.btnTema.setOnClickListener { elegirTema() }
        binding.btnIdioma.setOnClickListener { elegirIdioma() }
        binding.btnUnidades.setOnClickListener { elegirUnidades() }
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

        val ordenados = pedidos.sortedByDescending { it.fechaActualizacion }
        val precios = com.example.vineuro_pvc.data.PreciosRepository(ctx).obtener()
        val simbolo = precios.moneda.firstOrNull()?.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "S/"
        fun resumenPedido(p: Pedido): String {
            val total = com.example.vineuro_pvc.data.CalculadoraPrecios.totalPedido(p.items, precios)
            return String.format(java.util.Locale.US, "%s · %s · %s %.2f", p.codigo, p.clienteNombre, simbolo, total)
        }
        binding.tvSubUltimoPedido.text = ordenados.firstOrNull()?.let { resumenPedido(it) } ?: "Aún no hay pedidos guardados"
        // "Penúltimo pedido": solo aparece cuando existe (si no, repetiría al último)
        val penultimo = ordenados.getOrNull(1)
        binding.btnPenultimoPedido.visibility = if (penultimo != null) View.VISIBLE else View.GONE
        binding.tvSubPenultimoPedido.text = penultimo?.let { resumenPedido(it) } ?: ""
        actualizarValoresAjustes()
        binding.tvSubPedidos.text = when (pedidos.size) {
            0 -> "Ver y abrir pedidos guardados"
            1 -> "1 pedido guardado"
            else -> "${pedidos.size} pedidos guardados"
        }
    }

    /** Muestra en cada ficha de ajustes el valor que está en uso. */
    private fun actualizarValoresAjustes() {
        val ctx = context ?: return
        binding.tvValorTema.text = when (Ajustes.tema(ctx)) { 1 -> "Claro"; 2 -> "Oscuro"; else -> "Sistema" }
        binding.tvValorIdioma.text = when (Ajustes.idioma()) { "es" -> "Español"; "en" -> "English"; else -> "Sistema" }
        binding.tvValorUnidades.text = if (Unidades.imperial) "in, ft, sq.ft" else "mm, m, m2"
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

    /** "Penúltimo pedido" ("second to last order"): el anterior al último modificado. */
    private fun abrirPenultimoPedido() {
        val pedidos = PedidoRepository(requireContext()).obtenerPedidos().sortedByDescending { it.fechaActualizacion }
        val penultimo = pedidos.getOrNull(1)
        if (penultimo != null) navegarAPedido(penultimo)
        else Toast.makeText(requireContext(), "Aún no hay un penúltimo pedido", Toast.LENGTH_SHORT).show()
    }

    private fun elegirTema() {
        val ctx = requireContext()
        val opciones = arrayOf("Según el sistema", "Claro", "Oscuro")
        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle("Tema")
            .setSingleChoiceItems(opciones, Ajustes.tema(ctx)) { d, which ->
                Ajustes.guardarTema(ctx, which)
                d.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun elegirIdioma() {
        val codigos = arrayOf("", "es", "en")
        val opciones = arrayOf("Idioma del sistema", "Español", "English")
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Idioma / Language")
            .setSingleChoiceItems(opciones, codigos.indexOf(Ajustes.idioma()).coerceAtLeast(0)) { d, which ->
                Ajustes.guardarIdioma(codigos[which])
                d.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun elegirUnidades() {
        val ctx = requireContext()
        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle("Unidades de medida")
            .setSingleChoiceItems(arrayOf("Métrico (mm, m, m2)", "Imperial (in, ft, sq.ft)"), if (Unidades.imperial) 1 else 0) { d, which ->
                Unidades.guardar(ctx, which == 1)
                d.dismiss()
                actualizarValoresAjustes()
                Toast.makeText(ctx, "Las medidas se muestran en ${if (which == 1) "pulgadas, pies y pies²" else "mm, m y m²"}; los cálculos y la guía de corte siguen en mm.", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
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
