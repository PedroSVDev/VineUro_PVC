package com.example.vineuro_pvc

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.vineuro_pvc.data.AperturasMulti
import com.example.vineuro_pvc.data.CalculadoraPrecios
import com.example.vineuro_pvc.views.AperturaOriginalDrawable
import com.example.vineuro_pvc.data.ClientRepository
import com.example.vineuro_pvc.data.OrderItem
import com.example.vineuro_pvc.data.OrderItemHolder
import com.example.vineuro_pvc.data.Pedido
import com.example.vineuro_pvc.data.PedidoRepository
import com.example.vineuro_pvc.data.Precios
import com.example.vineuro_pvc.data.Unidades
import com.example.vineuro_pvc.data.PreciosRepository
import com.example.vineuro_pvc.data.WindowConfig
import com.example.vineuro_pvc.data.WindowConfigCatalog
import com.example.vineuro_pvc.databinding.DialogSelectConfigBinding
import com.example.vineuro_pvc.databinding.DialogSelectConfigGeneralBinding
import com.example.vineuro_pvc.databinding.FragmentCalculoPedidoBinding
import com.example.vineuro_pvc.databinding.ItemOrderWindowTabBinding
import com.example.vineuro_pvc.views.WindowPreviewView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Fragmento de Cálculo y Diseño que soporta sugerencias de IA y se inicializa en blanco para nuevos pedidos.
 */
class CalculoPedidoFragment : Fragment() {

    private var _binding: FragmentCalculoPedidoBinding? = null
    private val binding get() = _binding!!

    private lateinit var clientRepository: ClientRepository
    private val listaConfiguraciones = mutableListOf<WindowConfig>()

    private val itemsOrden = mutableListOf<OrderItem>()
    private var activeItemIndex: Int = 0

    // ---- Persistencia del pedido (local + Supabase) ----
    private lateinit var pedidoRepository: PedidoRepository
    private var direccionPedido = ""
    private var idOfertaPedido = ""
    private var notaPedido = ""
    private var pedidoActual: Pedido? = null      // null = pedido nuevo aún no guardado
    private var clienteIdActual: String = ""
    private var nombreClienteActual: String = ""

    // Precios (alimentan el menú lateral del compás)
    private lateinit var preciosRepository: PreciosRepository
    private var precios: Precios = Precios.porDefecto()

    // Mismo orden de la cuadrícula de la original (3 columnas):
    // corr. der. | V (inferior) | corr. izq. / giro der. | + (sin apertura) | giro izq. / sup. der. | ^ (superior) | sup. izq. / hoja fija
    private val listaMecanismosApertura = listOf(
        AperturaMechanism("1", "Desliz. Der.", R.drawable.ic_apertura_arrow_right),
        AperturaMechanism("2", "Abat. Inferior", R.drawable.ic_apertura_tilt_top),
        AperturaMechanism("3", "Desliz. Izq.", R.drawable.ic_apertura_arrow_left),
        AperturaMechanism("6", "Practicable Der.", R.drawable.ic_apertura_right_turn),
        AperturaMechanism("5", "Sin apertura", R.drawable.ic_apertura_fixed_plus),
        AperturaMechanism("4", "Practicable Izq.", R.drawable.ic_apertura_left_turn),
        AperturaMechanism("8", "Oscilobat. Der.", R.drawable.ic_apertura_tilt_turn_right),
        AperturaMechanism("9", "Abat. Superior", R.drawable.ic_apertura_tilt_up),
        AperturaMechanism("7", "Oscilobat. Izq.", R.drawable.ic_apertura_tilt_turn_left),
        AperturaMechanism("10", "Hoja fija", R.drawable.ic_apertura_fixed_sash),
    ) + AperturasMulti.TODAS.map { id ->
        // Francesas, corredizas verticales y corredizas múltiples de la original, con icono dibujado por código
        AperturaMechanism(id, AperturasMulti.nombre(id), 0) { AperturaOriginalDrawable(id, resources.displayMetrics.density) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCalculoPedidoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        clientRepository = ClientRepository(requireContext())
        pedidoRepository = PedidoRepository(requireContext())
        preciosRepository = PreciosRepository(requireContext())
        inicializarConfiguraciones()
        binding.tableMatriz.setOnClickListener { mostrarTablaDetallada() }

        // Traer en segundo plano los precios más recientes de Supabase (para el menú del compás)
        precios = preciosRepository.obtener()
        viewLifecycleOwner.lifecycleScope.launch {
            precios = preciosRepository.sincronizar()
            refrescarTablaMatriz()
        }

        // Si la vista se recrea (p. ej. al volver del PDF) se recarga el pedido ya autoguardado
        val pedidoId = pedidoActual?.id ?: arguments?.getString("pedido_id")
        pedidoActual = pedidoId?.let { pedidoRepository.obtenerPedido(it) } ?: pedidoActual
        pedidoActual?.let { direccionPedido = it.direccion; idOfertaPedido = it.idOferta; notaPedido = it.nota }

        val clienteId = pedidoActual?.clienteId ?: arguments?.getString("cliente_id") ?: ""
        nombreClienteActual = pedidoActual?.clienteNombre ?: arguments?.getString("nombre_cliente") ?: "Cliente sin nombre"
        clienteIdActual = clienteId
        var nombreHeader = nombreClienteActual
        val usarSugerenciaIa = arguments?.getBoolean("usar_sugerencia_ia", false) ?: false

        if (clienteId.isNotEmpty()) {
            val cliente = clientRepository.obtenerClientePorId(clienteId)
            cliente?.let {
                nombreClienteActual = it.nombreCompleto
                nombreHeader = it.nombreCompleto
            }
        }

        nombreHeaderBase = nombreHeader
        refrescarEncabezadoCliente()

        // 1. Configurar listener táctil en las cotas de medidas
        binding.wpvMainCanvas.onDimensionClickListener = { dimensionType, sectionIndex, currentValue ->
            mostrarDialogoEditarMedida(dimensionType, sectionIndex, currentValue)
        }

        // 2. Configurar listener táctil al pulsar en cualquier (+) de los paños de la plantilla
        binding.wpvMainCanvas.onPaneClickListener = { paneIndex, _, _ ->
            mostrarDialogoSeleccionConfigParaPano(paneIndex)
        }

        // 3. Botón azul inferior (+): Abre el CATÁLOGO GENERAL
        binding.btnAddItem.setOnClickListener {
            mostrarDialogoSeleccionCatalogGeneral()
        }

        // "param. comprobar": muestra sobre el dibujo las opciones elegidas en el compás
        val prefsUi = requireContext().getSharedPreferences("pref_ui", android.content.Context.MODE_PRIVATE)
        binding.cbParamComprobar.isChecked = prefsUi.getBoolean("param_comprobar", true)
        binding.cbParamComprobar.setOnCheckedChangeListener { _, marcado ->
            prefsUi.edit().putBoolean("param_comprobar", marcado).apply()
            actualizarPanelParametros()
        }

        // "diagonales": diagonales de cada marco con su medida (comprobación de escuadra)
        binding.cbDiagonales.isChecked = prefsUi.getBoolean("diagonales", false)
        binding.wpvMainCanvas.showDiagonals = binding.cbDiagonales.isChecked
        binding.cbDiagonales.setOnCheckedChangeListener { _, marcado ->
            prefsUi.edit().putBoolean("diagonales", marcado).apply()
            binding.wpvMainCanvas.showDiagonals = marcado
        }

        // "shadows": sombras del dibujo (como la original, marcado por defecto)
        binding.cbShadows.isChecked = prefsUi.getBoolean("shadows", true)
        binding.wpvMainCanvas.showShadows = binding.cbShadows.isChecked
        binding.cbShadows.setOnCheckedChangeListener { _, marcado ->
            prefsUi.edit().putBoolean("shadows", marcado).apply()
            binding.wpvMainCanvas.showShadows = marcado
        }

        // 4. Botón verde compás (Herramientas Técnicas de trazado)
        binding.btnCompass.setOnClickListener {
            mostrarPanelConfiguracionVentana()
        }

        // 5. Botón Eliminar / Papelera
        binding.btnDeleteItem.setOnClickListener {
            if (itemsOrden.isNotEmpty() && activeItemIndex in itemsOrden.indices) {
                val itemActual = itemsOrden[activeItemIndex]
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("¿Eliminar Ventana ${itemActual.index}?")
                    .setMessage("Esta acción eliminará el diseño y los cálculos de esta ventana de la orden. ¿Desea continuar?")
                    .setIcon(android.R.drawable.ic_menu_delete)
                    .setPositiveButton("ELIMINAR") { dialog, _ ->
                        eliminarElementoActivo()
                        dialog.dismiss()
                    }
                    .setNegativeButton("CANCELAR") { dialog, _ -> dialog.dismiss() }
                    .show()
            } else {
                Toast.makeText(requireContext(), "No hay ventanas para eliminar", Toast.LENGTH_SHORT).show()
            }
        }

        // 0. Botón azul circular superior izquierdo (Hamburguesa): Abre el menú flotante
        binding.btnTopLeftMenu.setOnClickListener {
            mostrarMenuOpcionesFlotante()
        }

        // 6. Botón de menú superior derecho con opciones
        binding.btnHeaderMenu.setOnClickListener { viewAnchor ->
            mostrarMenuOpcionesSuperiores(viewAnchor)
        }

        val pedidoCargado = pedidoActual
        if (pedidoCargado != null) {
            cargarPedidoGuardado(pedidoCargado)
        } else if (usarSugerenciaIa) {
            inicializarOrdenSugerida()
        } else {
            inicializarOrdenVacia()
        }
    }

    // ===================== MENÚ LATERAL "CONFIGURACIÓN DE LA VENTANA" =====================

    /** Abre el menú lateral con las opciones de la ventana activa (listas cargadas desde Precios). */
    private fun mostrarPanelConfiguracionVentana() {
        if (activeItemIndex !in itemsOrden.indices) {
            Toast.makeText(requireContext(), "Agregue una ventana primero con el botón +", Toast.LENGTH_SHORT).show()
            return
        }
        guardarEstadoItemActivo()
        val item = itemsOrden[activeItemIndex]
        // Siempre con los precios guardados más recientes (por si se cambiaron en la pestaña Precios)
        precios = preciosRepository.obtener()
        PanelConfigVentana(requireContext(), precios, item.opciones) { nuevasOpciones ->
            item.opciones = nuevasOpciones
            precios = preciosRepository.obtener()
            refrescarTablaMatriz()
            guardarPedido(mostrarResultado = false)
            Toast.makeText(requireContext(), "Configuración de la ventana ${item.index} guardada", Toast.LENGTH_SHORT).show()
            // Si el sistema o el cristal elegidos no tienen precios, el costo sale 0: se avisa en vez de dejarlo mudo
            val graves = CalculadoraPrecios.advertencias(item, precios).filter {
                it.startsWith("El sistema") || it.startsWith("El cristal") || it.startsWith("No hay sistema") || it.startsWith("No hay tipo de cristal")
            }
            if (graves.isNotEmpty()) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("El costo sale en 0")
                    .setMessage(graves.joinToString("\n\n• ", prefix = "• ") +
                        "\n\nEn la pestaña Precios completa esas filas, o usa el botón verde \"CARGAR PRECIOS DE EJEMPLO\" de la pantalla Precios.")
                    .setPositiveButton("Entendido", null)
                    .show()
            }
        }.mostrar()
    }

    // ===================== GUARDADO DEL PEDIDO =====================

    private fun cargarPedidoGuardado(pedido: Pedido) {
        itemsOrden.clear()
        itemsOrden.addAll(pedido.items)
        activeItemIndex = 0
        renderizarPestanasInferiores()
        if (itemsOrden.isNotEmpty()) {
            cargarItemActivoEnCanvas()
        } else {
            binding.wpvMainCanvas.resetCanvas()
            limpiarTablaMatriz()
        }
        Toast.makeText(requireContext(), "Pedido ${pedido.codigo} cargado (${pedido.totalVentanas} ventanas)", Toast.LENGTH_SHORT).show()
    }

    /** Construye el Pedido con el estado actual de la pantalla (mantiene id y código si ya existía). */
    private fun construirPedido(): Pedido {
        guardarEstadoItemActivo()
        val ahora = System.currentTimeMillis()
        val base = pedidoActual
        return if (base == null) {
            Pedido(
                clienteId = clienteIdActual,
                clienteNombre = nombreClienteActual,
                items = itemsOrden.toList(),
                fechaCreacion = ahora,
                fechaActualizacion = ahora,
                direccion = direccionPedido, idOferta = idOfertaPedido, nota = notaPedido,
            )
        } else {
            base.copy(
                clienteId = clienteIdActual,
                clienteNombre = nombreClienteActual,
                items = itemsOrden.toList(),
                fechaActualizacion = ahora,
                direccion = direccionPedido, idOferta = idOfertaPedido, nota = notaPedido,
            )
        }
    }

    /**
     * Guarda el pedido en el teléfono y en Supabase.
     * @param mostrarResultado true cuando lo pide el usuario desde el menú (muestra Toast con el resultado real).
     */
    private fun guardarPedido(mostrarResultado: Boolean) {
        if (itemsOrden.isEmpty()) {
            if (mostrarResultado) {
                Toast.makeText(requireContext(), "Agregue al menos una ventana antes de guardar", Toast.LENGTH_SHORT).show()
            }
            return
        }
        val pedido = construirPedido()
        pedidoActual = pedido
        val appContext = requireContext().applicationContext
        lifecycleScope.launch {
            val error = pedidoRepository.guardarPedido(pedido)
            if (!mostrarResultado) return@launch
            val msg = if (error == null) {
                "Pedido ${pedido.codigo} guardado en la nube ✅"
            } else {
                "Guardado en el teléfono. La nube respondió: $error"
            }
            Toast.makeText(appContext, msg, Toast.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        super.onPause()
        // Autoguardado al salir de la pantalla para no perder el trabajo
        if (_binding != null) guardarPedido(mostrarResultado = false)
    }

    private fun mostrarMenuOpcionesFlotante() {
        val dialogBinding = com.example.vineuro_pvc.databinding.DialogMenuOpcionesBinding.inflate(layoutInflater)
        // Panel lateral a pantalla completa como el menú de la original; tocar fuera del panel lo cierra
        val dialog = android.app.Dialog(requireContext())
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialogBinding.flMenuFondo.setOnClickListener { dialog.dismiss() }

        // 1. CREAR PDF SÓLO PROYECTOS
        dialogBinding.btnPdfSoloProyectos.setOnClickListener {
            guardarEstadoItemActivo()
            OrderItemHolder.items = itemsOrden.toList()
            OrderItemHolder.clientName = nombreClienteActual
            OrderItemHolder.clienteId = clienteIdActual
            // Guardar antes de generar el PDF para tener el código del pedido
            guardarPedido(mostrarResultado = false)
            OrderItemHolder.codigoPedido = pedidoActual?.codigo ?: ""
            OrderItemHolder.direccionPedido = direccionPedido
            OrderItemHolder.idOfertaPedido = idOfertaPedido
            OrderItemHolder.conDetalles = false
            dialog.dismiss()
            findNavController().navigate(R.id.action_CalculoPedidoFragment_to_PdfPreviewFragment)
        }

        // 2. CREAR PDF CON LOS DETALLES (como el original: dibujo + lista de propiedades, 6 por página, sin precios)
        dialogBinding.btnPdfConDetalles.setOnClickListener {
            if (itemsOrden.isEmpty()) {
                Toast.makeText(requireContext(), "Agrega al menos una ventana al pedido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            guardarEstadoItemActivo()
            OrderItemHolder.items = itemsOrden.toList()
            OrderItemHolder.clientName = nombreClienteActual
            OrderItemHolder.clienteId = clienteIdActual
            guardarPedido(mostrarResultado = false)
            OrderItemHolder.codigoPedido = pedidoActual?.codigo ?: ""
            OrderItemHolder.direccionPedido = direccionPedido
            OrderItemHolder.idOfertaPedido = idOfertaPedido
            OrderItemHolder.conDetalles = true
            dialog.dismiss()
            findNavController().navigate(R.id.action_CalculoPedidoFragment_to_PdfPreviewFragment)
        }

        // 3. PDF OFERTA COMERCIAL
        dialogBinding.btnPdfOfertaComercial1.setOnClickListener {
            dialog.dismiss()
            if (itemsOrden.isEmpty()) {
                Toast.makeText(requireContext(), "Agrega al menos una ventana al pedido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Guardar primero para que la oferta lea el pedido actualizado
            guardarPedido(mostrarResultado = false)
            val id = pedidoActual?.id ?: return@setOnClickListener
            findNavController().navigate(R.id.OfertaComercialFragment, Bundle().apply { putString("pedido_id", id); putInt("variante", 1) })
        }

        // 4. PDF OFERTA COMERCIAL 2 (Desbloqueado)
        // 4. PDF OFERTA COMERCIAL 2: igual que la oferta pero con UNA ventana por página (dibujo grande), como el original
        dialogBinding.btnPdfOfertaComercial2.setOnClickListener {
            dialog.dismiss()
            if (itemsOrden.isEmpty()) {
                Toast.makeText(requireContext(), "Agrega al menos una ventana al pedido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            guardarPedido(mostrarResultado = false)
            val id = pedidoActual?.id ?: return@setOnClickListener
            findNavController().navigate(R.id.OfertaComercialFragment, Bundle().apply { putString("pedido_id", id); putInt("variante", 2) })
        }

        // GUÍA DE CORTE Y MATERIALES (módulo 4)
        dialogBinding.btnGuiaCorte.setOnClickListener {
            dialog.dismiss()
            if (itemsOrden.isEmpty()) {
                Toast.makeText(requireContext(), "Agrega al menos una ventana al pedido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            guardarPedido(mostrarResultado = false)
            val id = pedidoActual?.id ?: return@setOnClickListener
            findNavController().navigate(R.id.GuiaCorteFragment, Bundle().apply { putString("pedido_id", id) })
        }

        // 5. CONFIGURAR REQUISITOS
        dialogBinding.btnTerminosCondiciones.setOnClickListener {
            dialog.dismiss()
            DialogTerminos(this).mostrar()
        }

        dialogBinding.btnConfigurarRequisitos.setOnClickListener {
            dialog.dismiss()
            DialogRequisitos(this).mostrar()
        }

        // 6. CERRAR EL MENÚ
        dialogBinding.btnCerrarMenu.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun mostrarMenuOpcionesSuperiores(anchorView: View) {
        val popup = PopupMenu(requireContext(), anchorView)
        popup.menu.apply {
            add(0, 1, 0, "📄 Ver Detalles del Cliente / Pedido")
            add(0, 2, 1, "💾 Guardar Cambios en la Nube")
            add(0, 3, 2, "📐 PDF interno de costos y margen")
            add(0, 4, 3, "🔄 Reiniciar Orden Completa")
            add(0, 5, 4, "⚠️ Revisar precios del pedido")
            add(0, 6, 5, "📑 Duplicar ventana activa")
        }

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    mostrarDetallesPedido()
                    true
                }
                2 -> {
                    guardarPedido(mostrarResultado = true)
                    true
                }
                3 -> {
                    generarPdfDetalles()
                    true
                }
                4 -> {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("¿Reiniciar Orden?")
                        .setMessage("¿Desea eliminar todas las ventanas y vaciar este pedido?")
                        .setIcon(android.R.drawable.ic_menu_delete)
                        .setPositiveButton("VACIAR ORDEN") { dialog, _ ->
                            inicializarOrdenVacia()
                            dialog.dismiss()
                        }
                        .setNegativeButton("CANCELAR") { dialog, _ -> dialog.dismiss() }
                        .show()
                    true
                }
                5 -> {
                    mostrarRevisionPrecios()
                    true
                }
                6 -> {
                    duplicarVentanaActiva()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun inicializarConfiguraciones() {
        listaConfiguraciones.clear()
        listaConfiguraciones.addAll(WindowConfigCatalog.originales())
    }

    private fun inicializarOrdenVacia() {
        itemsOrden.clear()
        activeItemIndex = 0
        renderizarPestanasInferiores()
        binding.wpvMainCanvas.resetCanvas()
        limpiarTablaMatriz()
    }

    /**
     * Una ventana nueva hereda sistema, vidrio, color y plan de la anterior; si es la primera, toma la primera fila
     * de Precios. Así no se cotiza todo en 0 por no haber elegido nada (se puede cambiar o poner "no utilice").
     */
    private fun aplicarOpcionesPorDefecto(item: OrderItem) {
        val op = item.opciones
        val previa = itemsOrden.lastOrNull()?.opciones
        if (op.sistemaVentana == null) op.sistemaVentana = previa?.sistemaVentana ?: precios.nombres(precios.ventanas).firstOrNull()
        if (op.tipoCristal == null) op.tipoCristal = previa?.tipoCristal ?: precios.nombres(precios.cristal).firstOrNull()
        if (op.color == null) op.color = previa?.color
        if (op.planFinanciacion == null) op.planFinanciacion = previa?.planFinanciacion
    }

    private fun inicializarOrdenSugerida() {
        itemsOrden.clear()
        // 2 paneles verticales (werAB) de la original
        val configSugerida = listaConfiguraciones.find { it.modelo == "werAB" } ?: listaConfiguraciones.first()

        val window1 = OrderItem.create(
            index = 1,
            config = configSugerida,
            colorBgHex = "#42A5F5",
        )

        // Asignar apertura "Desliz. Der." y "Desliz. Izq." a los paneles por defecto como sugerencia
        window1.paneMechanisms[0] = "1" // Desliz Der
        window1.paneMechanisms[1] = "3" // Desliz Izq

        aplicarOpcionesPorDefecto(window1)
        itemsOrden.add(window1)
        activeItemIndex = 0

        renderizarPestanasInferiores()
        cargarItemActivoEnCanvas()
        
        Toast.makeText(requireContext(), "Sugerencia de IA aplicada correctamente ✨", Toast.LENGTH_LONG).show()
    }


    private fun renderizarPestanasInferiores() {
        binding.containerWindowTabs.removeAllViews()

        for (i in itemsOrden.indices) {
            val item = itemsOrden[i]
            val tabBinding = ItemOrderWindowTabBinding.inflate(
                layoutInflater,
                binding.containerWindowTabs,
                false,
            )

            tabBinding.tvTabIndex.text = item.index.toString()

            tabBinding.wpvTabThumb.sectionHeights = item.sectionHeights.clone()
            tabBinding.wpvTabThumb.sectionWidths = item.sectionWidths.clone()

            tabBinding.wpvTabThumb.paneMechanisms.clear()
            tabBinding.wpvTabThumb.paneMechanisms.putAll(item.paneMechanisms)

            tabBinding.wpvTabThumb.paneMosqNet.clear()
            tabBinding.wpvTabThumb.paneMosqNet.putAll(item.paneMosqNet)

            tabBinding.wpvTabThumb.panePuerta.clear()
            tabBinding.wpvTabThumb.panePuerta.putAll(item.panePuerta)

            tabBinding.wpvTabThumb.paneSandwich.clear()
            tabBinding.wpvTabThumb.paneSandwich.putAll(item.paneSandwich)

            tabBinding.wpvTabThumb.paneCols.clear()
            tabBinding.wpvTabThumb.paneCols.putAll(item.paneCols)
            tabBinding.wpvTabThumb.paneSandwichSub.clear()
            tabBinding.wpvTabThumb.paneSandwichSub.putAll(item.paneSandwichSub)
            tabBinding.wpvTabThumb.paneBars.clear()
            tabBinding.wpvTabThumb.paneImpOff.clear()
            tabBinding.wpvTabThumb.paneBars.putAll(item.paneBars)
            tabBinding.wpvTabThumb.paneImpOff.putAll(item.paneImpOff)

            tabBinding.wpvTabThumb.setConfig(item.config, isTechnicalMode = false)

            val isActive = (i == activeItemIndex)
            if (isActive) {
                tabBinding.cardWindowTab.setCardBackgroundColor(Color.parseColor("#CE3B75"))
                tabBinding.cardWindowTab.strokeColor = Color.WHITE
                tabBinding.cardWindowTab.strokeWidth = 2
            } else {
                tabBinding.cardWindowTab.setCardBackgroundColor(Color.parseColor("#42A5F5"))
                tabBinding.cardWindowTab.strokeWidth = 0
            }

            tabBinding.cardWindowTab.setOnClickListener {
                guardarEstadoItemActivo()
                activeItemIndex = i
                renderizarPestanasInferiores()
                cargarItemActivoEnCanvas()
            }

            binding.containerWindowTabs.addView(tabBinding.root)
        }
    }

    private fun guardarEstadoItemActivo() {
        if (activeItemIndex in itemsOrden.indices) {
            val item = itemsOrden[activeItemIndex]
            item.altoTotalMm = binding.wpvMainCanvas.altoTotalMm
            item.anchoTotalMm = binding.wpvMainCanvas.anchoTotalMm
            item.sectionHeights = binding.wpvMainCanvas.sectionHeights.clone()
            item.sectionWidths = binding.wpvMainCanvas.sectionWidths.clone()

            item.paneMechanisms.clear()
            item.paneMechanisms.putAll(binding.wpvMainCanvas.paneMechanisms)

            item.paneMosqNet.clear()
            item.paneMosqNet.putAll(binding.wpvMainCanvas.paneMosqNet)

            item.panePuerta.clear()
            item.panePuerta.putAll(binding.wpvMainCanvas.panePuerta)

            item.paneSandwich.clear()
            item.paneSandwich.putAll(binding.wpvMainCanvas.paneSandwich)

            item.paneCols.clear()
            item.paneCols.putAll(binding.wpvMainCanvas.paneCols)
            item.paneSandwichSub.clear()
            item.paneSandwichSub.putAll(binding.wpvMainCanvas.paneSandwichSub)
            item.paneBars.clear()
            item.paneImpOff.clear()
            item.paneBars.putAll(binding.wpvMainCanvas.paneBars)
            item.paneImpOff.putAll(binding.wpvMainCanvas.paneImpOff)
        }
    }

    private fun cargarItemActivoEnCanvas() {
        if (activeItemIndex !in itemsOrden.indices) return
        val item = itemsOrden[activeItemIndex]

        binding.wpvMainCanvas.setConfig(item.config, isTechnicalMode = true, resetMechanisms = false)
        binding.wpvMainCanvas.sectionHeights = item.sectionHeights.clone()
        binding.wpvMainCanvas.sectionWidths = item.sectionWidths.clone()

        binding.wpvMainCanvas.paneMechanisms.clear()
        binding.wpvMainCanvas.paneMechanisms.putAll(item.paneMechanisms)

        binding.wpvMainCanvas.paneMosqNet.clear()
        binding.wpvMainCanvas.paneMosqNet.putAll(item.paneMosqNet)

        binding.wpvMainCanvas.panePuerta.clear()
        binding.wpvMainCanvas.panePuerta.putAll(item.panePuerta)

        binding.wpvMainCanvas.paneSandwich.clear()
        binding.wpvMainCanvas.paneSandwich.putAll(item.paneSandwich)

        binding.wpvMainCanvas.paneCols.clear()
        binding.wpvMainCanvas.paneCols.putAll(item.paneCols)
        binding.wpvMainCanvas.paneSandwichSub.clear()
        binding.wpvMainCanvas.paneSandwichSub.putAll(item.paneSandwichSub)
        binding.wpvMainCanvas.paneBars.clear()
        binding.wpvMainCanvas.paneImpOff.clear()
        binding.wpvMainCanvas.paneBars.putAll(item.paneBars)
        binding.wpvMainCanvas.paneImpOff.putAll(item.paneImpOff)

        binding.wpvMainCanvas.updateDimensions(item.altoTotalMm, item.anchoTotalMm)
        binding.wpvMainCanvas.perfilColorHex = com.example.vineuro_pvc.data.ColoresPerfil.hexDe(item.opciones.color)

        actualizarTablaMatriz(item.config)
    }

    /**
     * Diálogo centrado casi a pantalla completa (como los de la original). No usa BottomSheet:
     * el sheet se tragaba el gesto de desplazar y las listas largas (aperturas, catálogo) no bajaban.
     */
    private fun dialogoGrande(): android.app.Dialog {
        val d = android.app.Dialog(requireContext())
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        d.setOnShowListener {
            val m = resources.displayMetrics
            d.window?.setLayout((m.widthPixels * 0.94).toInt(), (m.heightPixels * 0.90).toInt())
            d.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
        return d
    }

    /**
     * Selector de modelos como el de la original: ventana pequeña (80 % del ancho, proporción 1 : 1,2) con cabecera "configuración",
     * rejilla de 3 columnas con solo los dibujos y botones NO / SÍ.
     */
    private fun dialogoCatalogo(): android.app.Dialog {
        val d = android.app.Dialog(requireContext())
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        d.setOnShowListener {
            val m = resources.displayMetrics
            val ancho = minOf(m.widthPixels * 0.80f, m.heightPixels * 0.96f / 1.2f)
            d.window?.setLayout(ancho.toInt(), (ancho * 1.2f).toInt())
            d.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
        return d
    }

    private fun mostrarDialogoSeleccionCatalogGeneral() {
        val dialog = dialogoCatalogo()
        val dialogBinding = DialogSelectConfigGeneralBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        var tempConfigSeleccionada: WindowConfig = listaConfiguraciones.first()

        dialogBinding.rvCatalogConfigs.adapter = WindowConfigAdapter(
            listaConfiguraciones,
            selectedId = tempConfigSeleccionada.id,
        ) { config ->
            tempConfigSeleccionada = config
        }

        dialogBinding.btnCancelarCatalogConfig.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnAceptarCatalogConfig.setOnClickListener {
            guardarEstadoItemActivo()

            val newIndex = itemsOrden.size + 1
            val bgHex = if (newIndex % 2 == 0) "#CE3B75" else "#42A5F5"
            val newItem = OrderItem.create(
                index = newIndex,
                config = tempConfigSeleccionada,
                colorBgHex = bgHex,
            )

            aplicarOpcionesPorDefecto(newItem)
            itemsOrden.add(newItem)
            activeItemIndex = itemsOrden.size - 1

            renderizarPestanasInferiores()
            cargarItemActivoEnCanvas()

            dialog.dismiss()
            Toast.makeText(requireContext(), "Nueva Ventana $newIndex (${tempConfigSeleccionada.nombre}) agregada a la orden", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    /** Diálogo de barras de vidrio: por cada vidrio (A, B, C) barras horizontales y verticales con - / +. */
    private fun mostrarDialogoBarras(actual: String, vidrios: Int, alAceptar: (String) -> Unit) {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        val previo = actual.trim().split(" ").filter { it.isNotBlank() }.map { t ->
            t.split("x").map { it.toIntOrNull() ?: 0 }.let { (it.getOrNull(0) ?: 0) to (it.getOrNull(1) ?: 0) }
        }
        val valores = Array(vidrios) { i -> intArrayOf(previo.getOrNull(i)?.first ?: 0, previo.getOrNull(i)?.second ?: 0) }
        val raiz = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
        }
        fun fila(titulo: String, i: Int, k: Int): android.view.View {
            val tv = android.widget.TextView(ctx).apply { text = "${valores[i][k]}"; textSize = 16f; gravity = android.view.Gravity.CENTER; minWidth = (36 * dp).toInt() }
            val menos = android.widget.Button(ctx).apply { text = "-"; setOnClickListener { valores[i][k] = maxOf(0, valores[i][k] - 1); tv.text = "${valores[i][k]}" } }
            val mas = android.widget.Button(ctx).apply { text = "+"; setOnClickListener { valores[i][k] = valores[i][k] + 1; tv.text = "${valores[i][k]}" } }
            return android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                addView(android.widget.TextView(ctx).apply { text = titulo; layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
                addView(menos); addView(tv); addView(mas)
            }
        }
        for (i in 0 until vidrios) {
            raiz.addView(android.widget.TextView(ctx).apply { text = "Vidrio ${'A' + i}"; setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(0, (8 * dp).toInt(), 0, 0) })
            raiz.addView(fila("barras horizontales", i, 0))
            raiz.addView(fila("barras verticales", i, 1))
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle("glazing bars")
            .setView(android.widget.ScrollView(ctx).apply { addView(raiz) })
            .setNegativeButton("NO", null)
            .setPositiveButton("SÍ") { _, _ ->
                val texto = valores.joinToString(" ") { "${it[0]}x${it[1]}" }
                alAceptar(if (valores.all { it[0] == 0 && it[1] == 0 }) "" else texto)
            }
            .show()
    }

    private fun mostrarDialogoSeleccionConfigParaPano(targetPaneIndex: Int) {
        val dialog = dialogoGrande()
        val dialogBinding = DialogSelectConfigBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val mecanismoActual = binding.wpvMainCanvas.paneMechanisms[targetPaneIndex] ?: "5"
        var tempMecanismoSeleccionado: AperturaMechanism = listaMecanismosApertura.find { it.id == mecanismoActual } ?: listaMecanismosApertura.first()
        dialogBinding.cbMosqNet.isChecked = binding.wpvMainCanvas.paneMosqNet[targetPaneIndex] == true
        dialogBinding.cbPuerta.isChecked = binding.wpvMainCanvas.panePuerta[targetPaneIndex] == true

        // Imposta interior de la hoja (original: ninguna / vertical 1 / horizontal 1 / vertical 2 / horizontal 2).
        // paneCols: 1..4 = código antiguo (hojas separadas); 11..14 = imposta interior de una sola hoja.
        val colsActual = binding.wpvMainCanvas.paneCols[targetPaneIndex] ?: 1
        var tempCols = colsActual
        val impActual = when (colsActual) { in 11..14 -> colsActual - 10; 2 -> 3; 3 -> 4; else -> 0 } // 1 hor.x1 2 hor.x2 3 ver.x1 4 ver.x2
        var tempImp = impActual
        val cbsSandwich = listOf(
            dialogBinding.cbSandwichPanels, dialogBinding.cbSandwich2, dialogBinding.cbSandwich3, dialogBinding.cbSandwich4,
            dialogBinding.cbSandwich5, dialogBinding.cbSandwich6, dialogBinding.cbSandwich7, dialogBinding.cbSandwich8,
        )
        val maskActual = binding.wpvMainCanvas.paneSandwichSub[targetPaneIndex]
            ?: if (binding.wpvMainCanvas.paneSandwich[targetPaneIndex] == true) 255 else 0
        cbsSandwich.forEachIndexed { i, cb -> cb.isChecked = ((maskActual shr i) and 1) == 1 }
        // Vidrios que se configuran por separado: los de la imposta interior o, en aperturas de varias hojas, uno por hoja (como la original)
        fun vidriosDelPano(): Int =
            if (AperturasMulti.esMulti(tempMecanismoSeleccionado.id) && tempImp == 0) AperturasMulti.hojas(tempMecanismoSeleccionado.id, 1.0, 1.0, false).size
            else when (tempImp) { 0 -> 1; 1, 3 -> 2; else -> 3 }   // con imposta: los vidrios de la imposta (los mismos en cada hoja)
        fun actualizarSandwich() {
            val visibles = vidriosDelPano()
            cbsSandwich.forEachIndexed { i, cb ->
                cb.visibility = if (i < visibles) android.view.View.VISIBLE else android.view.View.GONE
                if (i >= visibles) cb.isChecked = false
            }
        }
        actualizarSandwich()

        // Posición de las impostas interiores (mm). Vacío = reparto igual (mitad / tercios), como la original por defecto.
        val offsGuardados = (binding.wpvMainCanvas.paneImpOff[targetPaneIndex] ?: "").split(",").map { it.trim() }
        dialogBinding.etImpOff1.setText(offsGuardados.getOrNull(0)?.removeSuffix(".0") ?: "")
        dialogBinding.etImpOff2.setText(offsGuardados.getOrNull(1)?.removeSuffix(".0") ?: "")
        fun actualizarOffsets() {
            dialogBinding.llImpOffsets.visibility = if (tempImp > 0) android.view.View.VISIBLE else android.view.View.GONE
            dialogBinding.etImpOff2.visibility = if (tempImp == 2 || tempImp == 4) android.view.View.VISIBLE else android.view.View.GONE
            dialogBinding.tvImpOffsets.text = when (tempImp) {
                1 -> "posición de la imposta (mm desde abajo)"
                2 -> "posición de las impostas (mm desde arriba / mm desde abajo)"
                3 -> "posición de la imposta (mm desde la izquierda)"
                4 -> "posición de las impostas (mm desde la izquierda / mm desde la derecha)"
                else -> ""
            }
        }
        actualizarOffsets()

        // Barras de vidrio (glazing bars): horizontales x verticales por cada vidrio, como la original
        var tempBars: String = binding.wpvMainCanvas.paneBars[targetPaneIndex] ?: ""

        // Panel "impostas" como la original: 1ª celda = glazing bars, después ninguna / vert.1 / hor.1 / vert.2 / hor.2.
        // Regla de la original (pk_r2.z): corredizas y puertas francesas admiten solo impostas horizontales; corredizas verticales
        // solo verticales; el resto se muestra atenuado (alpha 0.3, no pulsable). Cada hoja se parte igual.
        fun maskIconos(): Int = cbsSandwich.withIndex().sumOf { (i, cb) -> if (cb.isChecked && cb.visibility == android.view.View.VISIBLE) (1 shl i) else 0 }
        fun itemsImposta(mecId: String) = listOf(
            ImpostaItem(ImpostaItem.BARRAS), ImpostaItem(0),
            ImpostaItem(3, AperturasMulti.impostaPermitida(mecId, 3)), ImpostaItem(1, AperturasMulti.impostaPermitida(mecId, 1)),
            ImpostaItem(4, AperturasMulti.impostaPermitida(mecId, 4)), ImpostaItem(2, AperturasMulti.impostaPermitida(mecId, 2)),
        )
        /** Categoría de apertura para reiniciar la imposta como la original: 0 normal, 1 corrediza/francesa, 2 corrediza vertical. */
        fun categoria(id: String) = when (AperturasMulti.grupo(id)) {
            null -> 0
            AperturasMulti.Grupo.CORREDIZA, AperturasMulti.Grupo.FRANCESA -> 1
            AperturasMulti.Grupo.CORREDIZA_VERT -> 2
        }
        var categoriaActual = categoria(tempMecanismoSeleccionado.id)
        lateinit var layoutAdapter: ImpostaIconAdapter
        layoutAdapter = ImpostaIconAdapter(
            itemsImposta(tempMecanismoSeleccionado.id), tempImp, maskIconos(),
            alElegir = { imp ->
                tempImp = imp
                tempCols = if (tempImp == 0) 1 else 10 + tempImp
                actualizarSandwich()
                actualizarOffsets()
                layoutAdapter.actualizarMascara(maskIconos())
            },
            alPulsarBarras = {
                mostrarDialogoBarras(tempBars, vidriosDelPano()) { nuevo -> tempBars = nuevo }
            },
        )
        dialogBinding.rvImpostasLayouts.adapter = layoutAdapter
        cbsSandwich.forEach { cb -> cb.setOnCheckedChangeListener { _, _ -> layoutAdapter.actualizarMascara(maskIconos()) } }

        // Reglas de la original (pk_r2): sin apertura ("+") bloquea puerta y mosquitero; hoja fija bloquea mosquitero.
        fun aplicarReglasApertura(id: String) {
            fun bloquear(cb: android.widget.CheckBox, bloqueado: Boolean) {
                if (bloqueado) cb.isChecked = false
                cb.isEnabled = !bloqueado
                cb.isClickable = !bloqueado
                cb.alpha = if (bloqueado) 0.3f else 1f
            }
            bloquear(dialogBinding.cbPuerta, id == "5")
            bloquear(dialogBinding.cbMosqNet, id == "5" || id == "10")
            // Al cambiar de categoría la original reinicia la imposta elegida
            val categoriaNueva = categoria(id)
            if (categoriaNueva != categoriaActual || !AperturasMulti.impostaPermitida(id, tempImp)) { tempImp = 0; tempCols = 1 }
            categoriaActual = categoriaNueva
            actualizarSandwich()
            actualizarOffsets()
            layoutAdapter.actualizar(itemsImposta(id), tempImp, maskIconos())
        }
        aplicarReglasApertura(tempMecanismoSeleccionado.id)

        val aperturaAdapter = AperturaAdapter(
            listaMecanismosApertura,
            selectedId = binding.wpvMainCanvas.paneMechanisms[targetPaneIndex] ?: "5",
        ) { mecanismo ->
            tempMecanismoSeleccionado = mecanismo
            aplicarReglasApertura(mecanismo.id)
        }
        (dialogBinding.rvAperturaMechanisms.layoutManager as androidx.recyclerview.widget.GridLayoutManager).apply {
            spanCount = AperturaAdapter.SPANS
            spanSizeLookup = aperturaAdapter.spanSizeLookup()
        }
        dialogBinding.rvAperturaMechanisms.adapter = aperturaAdapter

        dialogBinding.btnDialogNo.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnDialogSi.setOnClickListener {
            val maskSandwich = cbsSandwich.withIndex().sumOf { (i, cb) -> if (cb.isChecked && cb.visibility == android.view.View.VISIBLE) (1 shl i) else 0 }
            val isSandwich = maskSandwich != 0
            val isPuerta = dialogBinding.cbPuerta.isChecked
            val isMosqNet = dialogBinding.cbMosqNet.isChecked

            binding.wpvMainCanvas.setPaneMechanism(targetPaneIndex, tempMecanismoSeleccionado.id)
            binding.wpvMainCanvas.setPaneBars(targetPaneIndex, tempBars)
            val o1 = dialogBinding.etImpOff1.text.toString().trim(); val o2 = dialogBinding.etImpOff2.text.toString().trim()
            binding.wpvMainCanvas.setPaneImpOff(targetPaneIndex, if (tempImp == 0 || (o1.isEmpty() && o2.isEmpty())) null else if (tempImp == 2 || tempImp == 4) "$o1,$o2" else o1)
            binding.wpvMainCanvas.setPaneOptions(targetPaneIndex, isSandwich, isPuerta, isMosqNet, tempCols, if (tempCols in 11..14 || AperturasMulti.esMulti(tempMecanismoSeleccionado.id)) maskSandwich else -1)

            guardarEstadoItemActivo()
            renderizarPestanasInferiores()
            refrescarTablaMatriz()

            dialog.dismiss()

            val msg = buildString {
                append("Paño ${targetPaneIndex + 1}: ")
                append(tempMecanismoSeleccionado.nombre)
                if (tempCols in 11..14) append(" | imposta interior") else if (tempCols > 1) append(" | $tempCols Paneles")
                if (isPuerta) append(" | Modo Puerta")
                if (isMosqNet) append(" | Mosq.Net")
                if (isSandwich) append(" | Panel Sándwich")
            }
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun eliminarElementoActivo() {
        if (activeItemIndex in itemsOrden.indices) {
            val itemEliminado = itemsOrden.removeAt(activeItemIndex)

            for (idx in itemsOrden.indices) {
                itemsOrden[idx].index = idx + 1
            }

            if (itemsOrden.isNotEmpty()) {
                activeItemIndex = Math.min(activeItemIndex, itemsOrden.size - 1)
                renderizarPestanasInferiores()
                cargarItemActivoEnCanvas()
                Toast.makeText(requireContext(), "Ventana ${itemEliminado.index} eliminada de la orden", Toast.LENGTH_SHORT).show()
            } else {
                activeItemIndex = 0
                renderizarPestanasInferiores()
                binding.wpvMainCanvas.resetCanvas()
                limpiarTablaMatriz()
                Toast.makeText(requireContext(), "Se eliminaron todas las ventanas de la orden", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var nombreHeaderBase = ""

    /** Como el original: "cliente : ID de oferta" arriba a la derecha. */
    private fun refrescarEncabezadoCliente() {
        _binding?.tvHeaderClienteNombre?.text =
            if (idOfertaPedido.isBlank()) nombreHeaderBase else "$nombreHeaderBase : $idOfertaPedido"
    }

    private fun simboloMoneda(): String =
        precios.moneda.firstOrNull()?.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "US$"

    /** "Detalles del pedido": datos editables (dirección, ID de oferta, nota) + resumen de importes. */
    private fun mostrarDetallesPedido() {
        val ctx = requireContext()
        val fmt = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
        val resultados = itemsOrden.map { CalculadoraPrecios.calcular(it, precios) }
        val r = CalculadoraPrecios.resumenPedido(resultados)
        val s = simboloMoneda()
        val area = itemsOrden.sumOf { it.areaM2 * maxOf(1, it.opciones.cantidad) }
        val p = pedidoActual
        val resumen = buildString {
            append("Cliente: $nombreClienteActual\n")
            append("Pedido: ${p?.codigo ?: "(aún sin guardar)"}\n")
            p?.let { append("Creado: ${fmt.format(java.util.Date(it.fechaCreacion))}  ·  Modificado: ${fmt.format(java.util.Date(it.fechaActualizacion))}\n") }
            append("Ventanas: ${itemsOrden.size}  ·  Unidades: ${itemsOrden.sumOf { maxOf(1, it.opciones.cantidad) }}  ·  ${Unidades.areaM2(area)}\n")
            append("Neto: ${String.format(java.util.Locale.US, "%.2f", r.neto)} $s  ·  Impuesto (${String.format(java.util.Locale.US, "%.2f", r.impuestoPct)} %): ${String.format(java.util.Locale.US, "%.2f", r.impuesto)} $s\n")
            append("TOTAL: ${String.format(java.util.Locale.US, "%.2f", r.total)} $s")
        }
        val dp = ctx.resources.displayMetrics.density
        fun campo(titulo: String, valor: String, multilinea: Boolean) = android.widget.EditText(ctx).apply {
            hint = titulo
            setText(valor)
            inputType = if (multilinea) android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            else android.text.InputType.TYPE_CLASS_TEXT
            if (multilinea) minLines = 2
        }
        val etDir = campo("Dirección", direccionPedido, true)
        val etOferta = campo("ID de la oferta", idOfertaPedido, false)
        val etNota = campo("Nota del pedido", notaPedido, true)
        val caja = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val m = (20 * dp).toInt(); setPadding(m, (8 * dp).toInt(), m, 0)
            addView(android.widget.TextView(ctx).apply { text = resumen; textSize = 13f })
            addView(etDir); addView(etOferta); addView(etNota)
        }
        MaterialAlertDialogBuilder(ctx).setTitle("Detalles del pedido").setView(android.widget.ScrollView(ctx).apply { addView(caja) })
            .setPositiveButton("GUARDAR") { _, _ ->
                direccionPedido = etDir.text.toString().trim()
                idOfertaPedido = etOferta.text.toString().trim()
                notaPedido = etNota.text.toString().trim()
                refrescarEncabezadoCliente()
                guardarPedido(mostrarResultado = false)
                Toast.makeText(ctx, "Datos del pedido guardados", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("CERRAR", null).show()
    }

    /** Lista los precios que faltan y podrían hacer que el pedido salga más barato de lo real. */
    private fun mostrarRevisionPrecios() {
        guardarEstadoItemActivo()
        val avisos = itemsOrden.flatMap { it -> CalculadoraPrecios.advertencias(it, precios).map { m -> "Ventana ${it.index}: $m" } }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (avisos.isEmpty()) "Precios completos" else "Revisar precios (${avisos.size})")
            .setMessage(if (avisos.isEmpty()) "No se detectaron precios faltantes para las opciones elegidas." else avisos.joinToString("\n\n• ", prefix = "• "))
            .setPositiveButton("CERRAR", null).show()
    }

    private fun duplicarVentanaActiva() {
        if (itemsOrden.isEmpty()) {
            Toast.makeText(requireContext(), "No hay ventana para duplicar", Toast.LENGTH_SHORT).show()
            return
        }
        guardarEstadoItemActivo()
        val copia = itemsOrden[activeItemIndex].copiar(itemsOrden.size + 1)
        itemsOrden.add(copia)
        activeItemIndex = itemsOrden.size - 1
        renderizarPestanasInferiores()
        cargarItemActivoEnCanvas()
        Toast.makeText(requireContext(), "Ventana duplicada como N° ${copia.index}", Toast.LENGTH_SHORT).show()
    }

    private fun generarPdfDetalles() {
        if (itemsOrden.isEmpty()) {
            Toast.makeText(requireContext(), "Agrega al menos una ventana al pedido", Toast.LENGTH_SHORT).show()
            return
        }
        guardarEstadoItemActivo()
        guardarPedido(mostrarResultado = false)
        val ctx = requireContext()
        val archivo = try {
            val fecha = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date())
            com.example.vineuro_pvc.views.DetallePedidoPdf.crear(
                ctx, pedidoActual?.codigo ?: "", nombreClienteActual, fecha, itemsOrden.toList(), precios,
            )
        } catch (e: Exception) {
            Toast.makeText(ctx, "No se pudo crear el PDF: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }
        com.example.vineuro_pvc.views.PdfAcciones.mostrarDialogo(ctx, archivo, "Detalle del pedido ${pedidoActual?.codigo ?: ""}")
    }

    private fun limpiarTablaMatriz() {
        _binding?.cardParametros?.visibility = View.GONE
        binding.cellVZona.text = "-"
        binding.cellVMarcos.text = "-"
        binding.cellVImpostas.text = "-"
        binding.cellVGlaz.text = "-"
        binding.cellVCaucho.text = "-"
        binding.cellVHojas.text = "-"
        binding.cellVHojasP.text = "-"
        binding.cellVTotal.text = "0 ${simboloMoneda()}"

        binding.cellPZona.text = "-"
        binding.cellPMarcos.text = "-"
        binding.cellPImpostas.text = "-"
        binding.cellPGlaz.text = "-"
        binding.cellPCaucho.text = "-"
        binding.cellPHojas.text = "-"
        binding.cellPHojasP.text = "-"
        binding.cellPTotal.text = "0.00 (-0%)"

        binding.cellTvZona.text = "-"
        binding.cellTvMarcos.text = "-"
        binding.cellTvImpostas.text = "-"
        binding.cellTvGlaz.text = "-"
        binding.cellTvCaucho.text = "-"
        binding.cellTvHojas.text = "-"
        binding.cellTvHojasP.text = "-"
        binding.cellTvTotal.text = "0 ${simboloMoneda()}"

        binding.cellCZona.text = "-"
        binding.cellCMarcos.text = "-"
        binding.cellCImpostas.text = "-"
        binding.cellCGlaz.text = "-"
        binding.cellCCaucho.text = "-"
        binding.cellCHojas.text = "-"
        binding.cellCHojasP.text = "-"
        binding.cellCTotal.text = "0.00 (-0%)"
    }

    /** Medida mínima de una sección (columna/fila) de la ventana, igual que la app original. */
    private val SECCION_MIN_MM = 300

    private fun mostrarDialogoEditarMedida(
        type: WindowPreviewView.DimensionType,
        index: Int,
        currentValue: Int,
    ) {
        val totalAnchoActual = binding.wpvMainCanvas.anchoTotalMm
        val totalAltoActual = binding.wpvMainCanvas.altoTotalMm

        val titulo = when (type) {
            WindowPreviewView.DimensionType.TOTAL_HEIGHT -> "Editar Alto Total (${Unidades.sufijo()})"
            WindowPreviewView.DimensionType.TOTAL_WIDTH -> "Editar Ancho Total (${Unidades.sufijo()})"
            WindowPreviewView.DimensionType.SECTION_HEIGHT -> "Editar Alto Sección ${index + 1} (${Unidades.sufijo()})"
            WindowPreviewView.DimensionType.SECTION_WIDTH -> "Editar Ancho Panel ${index + 1} (${Unidades.sufijo()})"
        }

        val mensaje = when (type) {
            WindowPreviewView.DimensionType.TOTAL_HEIGHT -> "Alto total actual: ${Unidades.mostrarConUnidad(totalAltoActual)}"
            WindowPreviewView.DimensionType.TOTAL_WIDTH -> "Ancho total actual: ${Unidades.mostrarConUnidad(totalAnchoActual)}"
            WindowPreviewView.DimensionType.SECTION_HEIGHT -> "Alto total de la ventana: ${Unidades.mostrarConUnidad(totalAltoActual)}\nEl alto restante se rebalanceará automáticamente."
            WindowPreviewView.DimensionType.SECTION_WIDTH -> "Ancho total de la ventana: ${Unidades.mostrarConUnidad(totalAnchoActual)}\nEl ancho restante se rebalanceará automáticamente."
        }

        val input = EditText(requireContext()).apply {
            inputType = if (Unidades.imperial) (InputType.TYPE_CLASS_TEXT) else InputType.TYPE_CLASS_NUMBER
            setText(Unidades.paraEditar(currentValue))
            setSelection(text.length)
            hint = if (Unidades.imperial) "Pulgadas (ej. 59 1/2)" else "Ingrese valor en mm"
            isFocusable = true
            isFocusableInTouchMode = true
        }

        val alertDialog = AlertDialog.Builder(requireContext())
            .setTitle(titulo)
            .setMessage(mensaje)
            .setView(input)
            .setPositiveButton("APLICAR") { dialog, _ ->
                val nuevoValor = Unidades.aMm(input.text.toString())
                // Como la original: cada sección mide al menos 300 mm y las demás tampoco pueden quedar por debajo
                val esSeccion = type == WindowPreviewView.DimensionType.SECTION_WIDTH || type == WindowPreviewView.DimensionType.SECTION_HEIGHT
                if (esSeccion && nuevoValor != null) {
                    val esAncho = type == WindowPreviewView.DimensionType.SECTION_WIDTH
                    val n = binding.wpvMainCanvas.cantidadSecciones(esAncho)
                    val total = if (esAncho) totalAnchoActual else totalAltoActual
                    val maximo = total - (n - 1) * SECCION_MIN_MM
                    if (n > 1 && (nuevoValor < SECCION_MIN_MM || nuevoValor > maximo)) {
                        Toast.makeText(
                            requireContext(),
                            "Cada sección debe medir entre ${Unidades.mostrarConUnidad(SECCION_MIN_MM)} y ${Unidades.mostrarConUnidad(maximo)} (total ${Unidades.mostrarConUnidad(total)}). Cambia primero el total si necesitas más.",
                            Toast.LENGTH_LONG,
                        ).show()
                        dialog.dismiss()
                        return@setPositiveButton
                    }
                }
                if (nuevoValor != null && nuevoValor >= 50) {
                    when (type) {
                        WindowPreviewView.DimensionType.TOTAL_HEIGHT -> {
                            binding.wpvMainCanvas.updateTotalHeight(nuevoValor)
                        }
                        WindowPreviewView.DimensionType.TOTAL_WIDTH -> {
                            binding.wpvMainCanvas.updateTotalWidth(nuevoValor)
                        }
                        WindowPreviewView.DimensionType.SECTION_HEIGHT -> {
                            binding.wpvMainCanvas.updateSectionHeight(index, nuevoValor)
                        }
                        WindowPreviewView.DimensionType.SECTION_WIDTH -> {
                            binding.wpvMainCanvas.updateSectionWidth(index, nuevoValor)
                        }
                    }
                    guardarEstadoItemActivo()
                    renderizarPestanasInferiores()
                    if (activeItemIndex in itemsOrden.indices) {
                        actualizarTablaMatriz(itemsOrden[activeItemIndex].config)
                    }

                    val msgToast = when (type) {
                        WindowPreviewView.DimensionType.SECTION_WIDTH -> {
                            val totalAncho = binding.wpvMainCanvas.anchoTotalMm
                            "Panel ${index + 1}: ${Unidades.mostrarConUnidad(nuevoValor)} (Ancho Total: ${Unidades.mostrarConUnidad(totalAncho)})"
                        }
                        WindowPreviewView.DimensionType.SECTION_HEIGHT -> {
                            val totalAlto = binding.wpvMainCanvas.altoTotalMm
                            "Sección ${index + 1}: ${Unidades.mostrarConUnidad(nuevoValor)} (Alto Total: ${Unidades.mostrarConUnidad(totalAlto)})"
                        }
                        else -> "Medida actualizada: ${Unidades.mostrarConUnidad(nuevoValor)}"
                    }
                    Toast.makeText(requireContext(), msgToast, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), if (Unidades.imperial) "Ingrese una medida válida en pulgadas" else "Ingrese una medida válida en mm", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("CANCELAR") { dialog, _ -> dialog.dismiss() }
            .apply {
                // "¿Igualar dimensiones?": reparte el total en partes iguales (solo si hay más de una sección)
                val esAncho = type == WindowPreviewView.DimensionType.SECTION_WIDTH
                val esSeccion = esAncho || type == WindowPreviewView.DimensionType.SECTION_HEIGHT
                if (esSeccion && binding.wpvMainCanvas.cantidadSecciones(esAncho) > 1) {
                    setNeutralButton("IGUALAR") { dialog, _ ->
                        binding.wpvMainCanvas.igualarSecciones(esAncho)
                        guardarEstadoItemActivo()
                        renderizarPestanasInferiores()
                        if (activeItemIndex in itemsOrden.indices) actualizarTablaMatriz(itemsOrden[activeItemIndex].config)
                        Toast.makeText(requireContext(), "Dimensiones igualadas", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
            }
            .create()

        input.requestFocus()
        alertDialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        alertDialog.show()
    }

    /**
     * "Tabla en vivo" completa de la original: por cada concepto (A..X) la cantidad y el precio de UNA ventana y
     * la cantidad y el costo de TODAS las ventanas del pedido. Se abre tocando la tabla inferior.
     */
    private fun mostrarTablaDetallada() {
        if (activeItemIndex !in itemsOrden.indices) return
        guardarEstadoItemActivo()
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        val activo = CalculadoraPrecios.calcular(itemsOrden[activeItemIndex], precios)
        val todos = itemsOrden.map { CalculadoraPrecios.calcular(it, precios) }
        val simbolo = activo.simbolo

        fun cant(v: Double, un: Int) = when {
            v < 0.0001 -> "-"
            un == 0 -> Unidades.largoM(v)
            un == 1 -> Unidades.areaM2(v)
            else -> v.toInt().toString() + " pz"
        }
        fun dinero(v: Double) = if (v > 0.004) String.format(Locale.US, "%.2f", v) else "-"

        val tabla = android.widget.TableLayout(ctx).apply { setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt()) }
        fun fila(celdas: List<String>, negrita: Boolean, fondo: Int) {
            val row = android.widget.TableRow(ctx).apply { setBackgroundColor(fondo) }
            celdas.forEachIndexed { i, t ->
                row.addView(TextView(ctx).apply {
                    text = t
                    textSize = 12f
                    setTextColor(android.graphics.Color.parseColor(if (t == "-") "#9E9E9E" else "#212121"))
                    if (negrita) setTypeface(typeface, android.graphics.Typeface.BOLD)
                    gravity = if (i < 2) android.view.Gravity.START else android.view.Gravity.END
                    setPadding((8 * dp).toInt(), (5 * dp).toInt(), (8 * dp).toInt(), (5 * dp).toInt())
                })
            }
            tabla.addView(row)
        }
        fila(listOf("", "parte", "1 ventana", "precio ($simbolo)", "todas", "costo ($simbolo)"), true, android.graphics.Color.parseColor("#DDDDDD"))
        activo.desglose.forEachIndexed { i, l ->
            val cantTodas = todos.sumOf { r -> (r.desglose.getOrNull(i)?.cantidad ?: 0.0) * r.cantidad }
            val costoTodas = todos.sumOf { r -> (r.desglose.getOrNull(i)?.costo ?: 0.0) * r.cantidad }
            fila(
                listOf(l.codigo, l.nombre, cant(l.cantidad, l.unidad), dinero(l.precio), cant(cantTodas, l.unidad), dinero(costoTodas)),
                false, android.graphics.Color.parseColor(if (i % 2 == 0) "#FFFFFF" else "#F2F2F2"),
            )
        }
        fila(
            listOf("W", "total", "x${activo.cantidad}", String.format(Locale.US, "%.2f", activo.precioUnitarioTotal),
                "${todos.sumOf { it.cantidad }} u.", String.format(Locale.US, "%.2f", todos.sumOf { it.totalCosto })),
            true, android.graphics.Color.parseColor("#FFE0B2"),
        )
        val ventaPedido = todos.sumOf { it.totalVenta }
        fila(listOf("AL", "venta pedido", "", "", "", String.format(Locale.US, "%.2f", ventaPedido)), true, android.graphics.Color.parseColor("#FFE0B2"))

        val scroll = android.widget.ScrollView(ctx)
        val horizontal = android.widget.HorizontalScrollView(ctx)
        horizontal.addView(tabla)
        scroll.addView(horizontal)
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Tabla por concepto · Ventana ${itemsOrden[activeItemIndex].index}")
            .setView(scroll)
            .setPositiveButton("CERRAR", null)
            .show()
    }

    /** Recalcula la tabla con la ventana activa (se llama al cambiar medidas, paños, opciones o precios). */
    private fun refrescarTablaMatriz() {
        if (_binding == null) return
        if (activeItemIndex in itemsOrden.indices) {
            binding.wpvMainCanvas.perfilColorHex = com.example.vineuro_pvc.data.ColoresPerfil.hexDe(itemsOrden[activeItemIndex].opciones.color)
            actualizarTablaMatriz(itemsOrden[activeItemIndex].config)
        } else {
            limpiarTablaMatriz()
        }
    }

    /**
     * Tabla inferior del diseñador:
     *  - ventana:            cantidades de la ventana activa (x cantidad) y su precio total
     *  - precio:             precio de venta por concepto de UNA ventana activa (descuento aplicado)
     *  - todas las ventanas: cantidades sumadas de todo el pedido y total del pedido
     *  - costo:              costo por concepto de todo el pedido y margen de ganancia
     */
    private fun actualizarTablaMatriz(@Suppress("UNUSED_PARAMETER") config: WindowConfig) {
        guardarEstadoItemActivo()
        if (activeItemIndex !in itemsOrden.indices) {
            limpiarTablaMatriz()
            return
        }

        fun m(v: Double) = if (v > 0.0001) Unidades.largoM(v) else "-"
        fun m2(v: Double) = if (v > 0.0001) Unidades.areaM2(v) else "-"
        fun n(v: Int) = if (v > 0) v.toString() else "-"
        fun dinero(v: Double) = if (v > 0.004) String.format(Locale.US, "%.2f", v) else "-"

        val activo = CalculadoraPrecios.calcular(itemsOrden[activeItemIndex], precios)
        val resultados = itemsOrden.map { CalculadoraPrecios.calcular(it, precios) }
        val s = activo.simbolo
        val k = activo.cantidad.toDouble()
        val qa = activo.cantidades

        // ---- Fila VENTANA (activa, x cantidad) ----
        binding.cellVZona.text = m2(qa.areaM2 * k)
        binding.cellVMarcos.text = m(qa.marcoM * k)
        binding.cellVImpostas.text = m(qa.impostaM * k)
        binding.cellVGlaz.text = m(qa.glazingM * k)
        binding.cellVCaucho.text = m(qa.cauchoM * k)
        binding.cellVHojas.text = n(qa.hojas * activo.cantidad)
        binding.cellVHojasP.text = n(qa.hojasPuerta * activo.cantidad)
        binding.cellVTotal.text = String.format(Locale.US, "%.2f %s", activo.totalVenta, s) +
            if (activo.cantidad > 1) " (x${activo.cantidad})" else ""

        // ---- Fila PRECIO (venta por concepto de UNA ventana) ----
        val pu = activo.precioUnitario
        binding.cellPZona.text = dinero(pu.zona)
        binding.cellPMarcos.text = dinero(pu.marco)
        binding.cellPImpostas.text = dinero(pu.imposta)
        binding.cellPGlaz.text = dinero(pu.glazing)
        binding.cellPCaucho.text = dinero(pu.caucho)
        binding.cellPHojas.text = dinero(pu.hoja)
        binding.cellPHojasP.text = dinero(pu.hojaPuerta)
        binding.cellPTotal.text = String.format(Locale.US, "%.2f (-%.0f%%)", activo.precioUnitarioTotal, activo.descuentoPct)

        // ---- Fila TODAS LAS VENTANAS (cantidades del pedido completo) ----
        fun suma(f: (CalculadoraPrecios.Resultado) -> Double) = resultados.sumOf { f(it) * it.cantidad }
        binding.cellTvZona.text = m2(suma { it.cantidades.areaM2 })
        binding.cellTvMarcos.text = m(suma { it.cantidades.marcoM })
        binding.cellTvImpostas.text = m(suma { it.cantidades.impostaM })
        binding.cellTvGlaz.text = m(suma { it.cantidades.glazingM })
        binding.cellTvCaucho.text = m(suma { it.cantidades.cauchoM })
        binding.cellTvHojas.text = n(suma { it.cantidades.hojas.toDouble() }.toInt())
        binding.cellTvHojasP.text = n(suma { it.cantidades.hojasPuerta.toDouble() }.toInt())
        val totalPedido = resultados.sumOf { it.totalVenta }
        binding.cellTvTotal.text = String.format(Locale.US, "%.2f %s", totalPedido, s)

        // ---- Fila COSTO (todo el pedido, sin margen ni impuesto) ----
        val costoPedido = resultados.fold(CalculadoraPrecios.Costos.CERO) { acc, r -> acc + r.costoUnitario * r.cantidad.toDouble() }
        binding.cellCZona.text = dinero(costoPedido.zona)
        binding.cellCMarcos.text = dinero(costoPedido.marco)
        binding.cellCImpostas.text = dinero(costoPedido.imposta)
        binding.cellCGlaz.text = dinero(costoPedido.glazing)
        binding.cellCCaucho.text = dinero(costoPedido.caucho)
        binding.cellCHojas.text = dinero(costoPedido.hoja)
        binding.cellCHojasP.text = dinero(costoPedido.hojaPuerta)
        val ventaSinImpuesto = resultados.sumOf { it.totalVenta / (1 + it.impuestoPct / 100.0) }
        val margenPedido = if (costoPedido.total > 0) (ventaSinImpuesto / costoPedido.total - 1) * 100 else 0.0
        binding.cellCTotal.text = String.format(Locale.US, "%.2f (%+.0f%%)", costoPedido.total, margenPedido)
        actualizarPanelParametros()
    }

    // ===================== "PARAM. COMPROBAR" =====================

    /** Muestra u oculta la tarjeta con las opciones del compás de la ventana activa. */
    private fun actualizarPanelParametros() {
        val b = _binding ?: return
        if (!b.cbParamComprobar.isChecked || activeItemIndex !in itemsOrden.indices) {
            b.cardParametros.visibility = View.GONE
            return
        }
        val item = itemsOrden[activeItemIndex]
        b.tvParametrosTitulo.text = "Ventana ${item.index} · ${item.config.nombre}"
        b.tvParametros.text = textoParametros(item)
        b.cardParametros.visibility = View.VISIBLE
    }

    private fun textoParametros(item: OrderItem): CharSequence {
        val op = item.opciones
        val sb = android.text.SpannableStringBuilder()
        fun linea(etiqueta: String, valor: String?) {
            if (valor.isNullOrBlank()) return
            if (sb.isNotEmpty()) sb.append("\n")
            val ini = sb.length
            sb.append(etiqueta).append(": ")
            sb.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), ini, sb.length, 0)
            sb.append(valor)
        }
        fun lista(set: Set<String>) = set.sorted().joinToString(", ").ifBlank { null }

        linea("Medidas", Unidades.dimensiones(item.anchoTotalMm, item.altoTotalMm))
        if (op.cantidad > 1) linea("Cantidad", "${op.cantidad}")
        linea("Plan", op.planFinanciacion)
        linea("Sistema", op.sistemaVentana)
        linea("Cristal", op.tipoCristal)
        linea("Umbral ext.", lista(op.umbralesExternos))
        linea("Umbral int.", lista(op.umbralesInternos))
        linea("Herrajes", op.mueble)
        linea("Extras", lista(op.extras))
        linea("Trabajos", lista(op.trabajos))
        linea("Mosquitero", op.mosquitero)
        linea("Lam. interna", op.laminacionInterna)
        linea("Lam. externa", op.laminacionExterna)
        linea("Color", op.color)
        linea("Adicionales", lista(op.adicionales))
        resumenPerfiles(op.perfiles).forEach { (k, v) -> linea(k, v) }
        linea("Precio propio", op.precioPropio.takeIf { it.isNotBlank() && (it.replace(",", ".").toDoubleOrNull() ?: 0.0) > 0 })
        linea("Nota", op.nota)

        // Solo hay medidas: aún no se configuró nada en el compás
        if (!sb.contains("\n")) {
            sb.append("\n")
            val ini = sb.length
            sb.append("Sin opciones. Configúralas con el compás.")
            sb.setSpan(android.text.style.ForegroundColorSpan(Color.parseColor("#999999")), ini, sb.length, 0)
        }
        return sb
    }

    /** "juntas:0", "extensor:40:1"... -> "Juntas: izq., sup." / "Extensor 40 mm: sup." */
    private fun resumenPerfiles(perfiles: Set<String>): List<Pair<String, String>> {
        val lados = mapOf("0" to "izq.", "1" to "sup.", "2" to "inf.", "3" to "der.")
        val nombres = mapOf("juntas" to "Juntas", "juntas90" to "Juntas 90°", "juntas135" to "Juntas 135°", "huecos" to "Perf. huecos")
        val grupos = linkedMapOf<String, MutableList<String>>()
        perfiles.sorted().forEach { clave ->
            val p = clave.split(":")
            val (titulo, lado) = when {
                p.size == 3 && p[0] == "extensor" -> "Extensor ${p[1]} mm" to p[2]
                p.size == 2 -> (nombres[p[0]] ?: p[0]) to p[1]
                else -> return@forEach
            }
            grupos.getOrPut(titulo) { mutableListOf() }.add(lados[lado] ?: lado)
        }
        return grupos.map { (t, l) -> t to l.joinToString(", ") }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
