package com.example.vineuro_pvc

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.vineuro_pvc.data.ClientRepository
import com.example.vineuro_pvc.data.OrderItem
import com.example.vineuro_pvc.data.OrderItemHolder
import com.example.vineuro_pvc.data.Pedido
import com.example.vineuro_pvc.data.PedidoRepository
import com.example.vineuro_pvc.data.WindowConfig
import com.example.vineuro_pvc.data.WindowConfigCatalog
import com.example.vineuro_pvc.databinding.DialogSelectConfigBinding
import com.example.vineuro_pvc.databinding.DialogSelectConfigGeneralBinding
import com.example.vineuro_pvc.databinding.FragmentCalculoPedidoBinding
import com.example.vineuro_pvc.databinding.ItemOrderWindowTabBinding
import com.example.vineuro_pvc.views.WindowPreviewView
import com.google.android.material.bottomsheet.BottomSheetDialog
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
    private var pedidoActual: Pedido? = null      // null = pedido nuevo aún no guardado
    private var dniClienteActual: String = ""
    private var nombreClienteActual: String = ""

    private val listaMecanismosApertura = listOf(
        AperturaMechanism("1", "Desliz. Der.", R.drawable.ic_apertura_arrow_right),
        AperturaMechanism("2", "Oscilobat. Sup.", R.drawable.ic_apertura_tilt_top),
        AperturaMechanism("3", "Desliz. Izq.", R.drawable.ic_apertura_arrow_left),
        AperturaMechanism("4", "Practicable Izq.", R.drawable.ic_apertura_left_turn),
        AperturaMechanism("5", "Fijo Crosshair", R.drawable.ic_apertura_fixed_plus),
        AperturaMechanism("6", "Practicable Der.", R.drawable.ic_apertura_right_turn),
        AperturaMechanism("7", "Oscilobat. Izq.", R.drawable.ic_apertura_tilt_turn_left),
        AperturaMechanism("8", "Oscilobat. Der.", R.drawable.ic_apertura_tilt_turn_right),
    )

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
        inicializarConfiguraciones()

        // Si la vista se recrea (p. ej. al volver del PDF) se recarga el pedido ya autoguardado
        val pedidoId = pedidoActual?.id ?: arguments?.getString("pedido_id")
        pedidoActual = pedidoId?.let { pedidoRepository.obtenerPedido(it) } ?: pedidoActual

        val dniCliente = pedidoActual?.clienteDni ?: arguments?.getString("dni_cliente") ?: ""
        nombreClienteActual = pedidoActual?.clienteNombre ?: arguments?.getString("nombre_cliente") ?: "Cliente sin nombre"
        dniClienteActual = dniCliente
        var nombreHeader = nombreClienteActual
        val usarSugerenciaIa = arguments?.getBoolean("usar_sugerencia_ia", false) ?: false

        if (dniCliente.isNotEmpty()) {
            val cliente = clientRepository.obtenerClientePorDni(dniCliente)
            cliente?.let {
                nombreClienteActual = it.nombreCompleto
                nombreHeader = "${it.nombreCompleto} : ${it.codigoCliente}"
            }
        }

        binding.tvHeaderClienteNombre.text = nombreHeader

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

        // 4. Botón verde compás (Herramientas Técnicas de trazado)
        binding.btnCompass.setOnClickListener {
            Toast.makeText(requireContext(), "Modo de calibración y compás técnico activo", Toast.LENGTH_SHORT).show()
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
                clienteDni = dniClienteActual,
                clienteNombre = nombreClienteActual,
                items = itemsOrden.toList(),
                fechaCreacion = ahora,
                fechaActualizacion = ahora,
            )
        } else {
            base.copy(
                clienteDni = dniClienteActual,
                clienteNombre = nombreClienteActual,
                items = itemsOrden.toList(),
                fechaActualizacion = ahora,
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
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // 1. CREAR PDF SÓLO PROYECTOS
        dialogBinding.btnPdfSoloProyectos.setOnClickListener {
            guardarEstadoItemActivo()
            OrderItemHolder.items = itemsOrden.toList()
            OrderItemHolder.clientName = binding.tvHeaderClienteNombre.text.toString()
            dialog.dismiss()
            findNavController().navigate(R.id.action_CalculoPedidoFragment_to_PdfPreviewFragment)
        }

        // 2. CREAR PDF CON LOS DETALLES
        dialogBinding.btnPdfConDetalles.setOnClickListener {
            Toast.makeText(requireContext(), "Generador de PDF con Detalles en desarrollo", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        // 3. PDF OFERTA COMERCIAL
        dialogBinding.btnPdfOfertaComercial1.setOnClickListener {
            Toast.makeText(requireContext(), "Generador de Oferta Comercial en desarrollo", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        // 4. PDF OFERTA COMERCIAL 2 (Desbloqueado)
        dialogBinding.btnPdfOfertaComercial2.setOnClickListener {
            Toast.makeText(requireContext(), "Generador de Oferta Comercial 2 en desarrollo", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        // 5. CONFIGURAR REQUISITOS
        dialogBinding.btnConfigurarRequisitos.setOnClickListener {
            Toast.makeText(requireContext(), "Configuración de requisitos técnicos de la orden", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
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
            add(0, 3, 2, "📐 Exportar / Compartir Cotización")
            add(0, 4, 3, "🔄 Reiniciar Orden Completa")
            add(0, 5, 4, "⚙️ Configuración de Parámetros")
        }

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    Toast.makeText(requireContext(), "Detalles del cliente: ${binding.tvHeaderClienteNombre.text}", Toast.LENGTH_SHORT).show()
                    true
                }
                2 -> {
                    guardarPedido(mostrarResultado = true)
                    true
                }
                3 -> {
                    Toast.makeText(requireContext(), "Generando cotización técnica...", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(requireContext(), "Ajuste de parámetros comprobados activo", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun inicializarConfiguraciones() {
        listaConfiguraciones.clear()
        listaConfiguraciones.addAll(WindowConfigCatalog.obtenerTodas())
    }

    private fun inicializarOrdenVacia() {
        itemsOrden.clear()
        activeItemIndex = 0
        renderizarPestanasInferiores()
        binding.wpvMainCanvas.resetCanvas()
        limpiarTablaMatriz()
    }

    private fun inicializarOrdenSugerida() {
        itemsOrden.clear()
        // Buscar "2 Paneles V."
        val configSugerida = listaConfiguraciones.find { it.nombre == "2 Paneles V." } ?: listaConfiguraciones.first()

        val window1 = OrderItem(
            index = 1,
            config = configSugerida,
            colorBgHex = "#42A5F5",
        )

        // Asignar apertura "Desliz. Der." y "Desliz. Izq." a los paneles por defecto como sugerencia
        window1.paneMechanisms[0] = "1" // Desliz Der
        window1.paneMechanisms[1] = "3" // Desliz Izq

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

        binding.wpvMainCanvas.updateDimensions(item.altoTotalMm, item.anchoTotalMm)

        actualizarTablaMatriz(item.config)
    }

    private fun mostrarDialogoSeleccionCatalogGeneral() {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogSelectConfigGeneralBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        var tempConfigSeleccionada: WindowConfig = listaConfiguraciones.first()

        val catalogAdapter = WindowConfigAdapter(
            listaConfiguraciones,
            selectedId = tempConfigSeleccionada.id,
        ) { config ->
            tempConfigSeleccionada = config
        }
        dialogBinding.rvCatalogConfigs.adapter = catalogAdapter

        dialogBinding.chipGroupCatalogCategories.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val filtrados = when (checkedIds.first()) {
                R.id.chip_cat_estandar -> listaConfiguraciones.filter { it.categoria == "Estándar" }
                R.id.chip_cat_puertas -> listaConfiguraciones.filter { it.categoria == "Puerta-Ventana" }
                R.id.chip_cat_fijos -> listaConfiguraciones.filter { it.categoria == "Con Fijo" }
                else -> listaConfiguraciones
            }
            dialogBinding.rvCatalogConfigs.adapter = WindowConfigAdapter(filtrados) { config ->
                tempConfigSeleccionada = config
            }
        }

        dialogBinding.btnAceptarCatalogConfig.setOnClickListener {
            guardarEstadoItemActivo()

            val newIndex = itemsOrden.size + 1
            val bgHex = if (newIndex % 2 == 0) "#CE3B75" else "#42A5F5"
            val newItem = OrderItem(
                index = newIndex,
                config = tempConfigSeleccionada,
                colorBgHex = bgHex,
            )

            itemsOrden.add(newItem)
            activeItemIndex = itemsOrden.size - 1

            renderizarPestanasInferiores()
            cargarItemActivoEnCanvas()

            dialog.dismiss()
            Toast.makeText(requireContext(), "Nueva Ventana $newIndex (${tempConfigSeleccionada.nombre}) agregada a la orden", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun mostrarDialogoSeleccionConfigParaPano(targetPaneIndex: Int) {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogSelectConfigBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        var tempSubCols = binding.wpvMainCanvas.paneCols[targetPaneIndex] ?: 1
        var tempMecanismoSeleccionado: AperturaMechanism = listaMecanismosApertura.find { it.id == "5" } ?: listaMecanismosApertura.first()

        dialogBinding.cbMosqNet.isChecked = binding.wpvMainCanvas.paneMosqNet[targetPaneIndex] == true
        dialogBinding.cbPuerta.isChecked = binding.wpvMainCanvas.panePuerta[targetPaneIndex] == true
        dialogBinding.cbSandwichPanels.isChecked = binding.wpvMainCanvas.paneSandwich[targetPaneIndex] == true

        val impostaConfigs = listOf(
            WindowConfig("imp_1", "1 Paño Fijo", "Estándar", WindowConfig.LayoutType.GRID, cols = 1, rows = 1),
            WindowConfig("imp_2", "2 Paneles V.", "Estándar", WindowConfig.LayoutType.GRID, cols = 2, rows = 1),
            WindowConfig("imp_3", "3 Paneles V.", "Estándar", WindowConfig.LayoutType.GRID, cols = 3, rows = 1),
            WindowConfig("imp_4", "4 Paneles V.", "Estándar", WindowConfig.LayoutType.GRID, cols = 4, rows = 1),
        )

        val layoutAdapter = WindowConfigAdapter(
            impostaConfigs,
            selectedId = "imp_$tempSubCols",
        ) { config ->
            tempSubCols = when (config.id) {
                "imp_2" -> 2
                "imp_3" -> 3
                "imp_4" -> 4
                else -> 1
            }
        }
        dialogBinding.rvImpostasLayouts.adapter = layoutAdapter

        val aperturaAdapter = AperturaAdapter(
            listaMecanismosApertura,
            selectedId = binding.wpvMainCanvas.paneMechanisms[targetPaneIndex] ?: "5",
        ) { mecanismo ->
            tempMecanismoSeleccionado = mecanismo
        }
        dialogBinding.rvAperturaMechanisms.adapter = aperturaAdapter

        dialogBinding.btnDialogNo.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnDialogSi.setOnClickListener {
            val isSandwich = dialogBinding.cbSandwichPanels.isChecked
            val isPuerta = dialogBinding.cbPuerta.isChecked
            val isMosqNet = dialogBinding.cbMosqNet.isChecked

            binding.wpvMainCanvas.setPaneMechanism(targetPaneIndex, tempMecanismoSeleccionado.id)
            binding.wpvMainCanvas.setPaneOptions(targetPaneIndex, isSandwich, isPuerta, isMosqNet, tempSubCols)

            guardarEstadoItemActivo()
            renderizarPestanasInferiores()

            dialog.dismiss()

            val msg = buildString {
                append("Paño ${targetPaneIndex + 1}: ")
                append(tempMecanismoSeleccionado.nombre)
                if (tempSubCols > 1) append(" | $tempSubCols Paneles")
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

    private fun limpiarTablaMatriz() {
        binding.cellVZona.text = "-"
        binding.cellVMarcos.text = "-"
        binding.cellVImpostas.text = "-"
        binding.cellVGlaz.text = "-"
        binding.cellVCaucho.text = "-"
        binding.cellVHojas.text = "-"
        binding.cellVHojasP.text = "-"
        binding.cellVTotal.text = "0 S/"

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
        binding.cellTvTotal.text = "0 S/"

        binding.cellCZona.text = "-"
        binding.cellCMarcos.text = "-"
        binding.cellCImpostas.text = "-"
        binding.cellCGlaz.text = "-"
        binding.cellCCaucho.text = "-"
        binding.cellCHojas.text = "-"
        binding.cellCHojasP.text = "-"
        binding.cellCTotal.text = "0.00 (-0%)"
    }

    private fun mostrarDialogoEditarMedida(
        type: WindowPreviewView.DimensionType,
        index: Int,
        currentValue: Int,
    ) {
        val totalAnchoActual = binding.wpvMainCanvas.anchoTotalMm
        val totalAltoActual = binding.wpvMainCanvas.altoTotalMm

        val titulo = when (type) {
            WindowPreviewView.DimensionType.TOTAL_HEIGHT -> "Editar Alto Total (mm)"
            WindowPreviewView.DimensionType.TOTAL_WIDTH -> "Editar Ancho Total (mm)"
            WindowPreviewView.DimensionType.SECTION_HEIGHT -> "Editar Alto Sección ${index + 1} (mm)"
            WindowPreviewView.DimensionType.SECTION_WIDTH -> "Editar Ancho Panel ${index + 1} (mm)"
        }

        val mensaje = when (type) {
            WindowPreviewView.DimensionType.TOTAL_HEIGHT -> "Alto total actual: $totalAltoActual mm"
            WindowPreviewView.DimensionType.TOTAL_WIDTH -> "Ancho total actual: $totalAnchoActual mm"
            WindowPreviewView.DimensionType.SECTION_HEIGHT -> "Alto total de la ventana: $totalAltoActual mm\nEl alto restante se rebalanceará automáticamente."
            WindowPreviewView.DimensionType.SECTION_WIDTH -> "Ancho total de la ventana: $totalAnchoActual mm\nEl ancho restante se rebalanceará automáticamente."
        }

        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentValue.toString())
            setSelection(text.length)
            hint = "Ingrese valor en mm"
        }

        AlertDialog.Builder(requireContext())
            .setTitle(titulo)
            .setMessage(mensaje)
            .setView(input)
            .setPositiveButton("APLICAR") { dialog, _ ->
                val nuevoValor = input.text.toString().toIntOrNull()
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
                            "Panel ${index + 1}: $nuevoValor mm (Ancho Total: $totalAncho mm)"
                        }
                        WindowPreviewView.DimensionType.SECTION_HEIGHT -> {
                            val totalAlto = binding.wpvMainCanvas.altoTotalMm
                            "Sección ${index + 1}: $nuevoValor mm (Alto Total: $totalAlto mm)"
                        }
                        else -> "Medida actualizada: $nuevoValor mm"
                    }
                    Toast.makeText(requireContext(), msgToast, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Ingrese una medida válida en mm", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("CANCELAR") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun actualizarTablaMatriz(config: WindowConfig) {
        val altoTotalMm = binding.wpvMainCanvas.altoTotalMm
        val anchoTotalMm = binding.wpvMainCanvas.anchoTotalMm

        val altoM = altoTotalMm / 1000.0
        val anchoM = anchoTotalMm / 1000.0

        val areaZonaVal = altoM * anchoM
        val marcosMVal = (2 * altoM) + (2 * anchoM)
        val impostasMVal = if (config.rows > 1) (config.rows - 1) * anchoM else 0.0
        val glazMVal = marcosMVal * config.cols
        val cauchoMVal = marcosMVal * config.cols

        val areaZona = String.format(Locale.US, "%.2f m2", areaZonaVal)
        val marcosM = String.format(Locale.US, "%.2f m", marcosMVal)
        val impostasM = if (impostasMVal > 0) String.format(Locale.US, "%.2f m", impostasMVal) else "-"
        val glazM = String.format(Locale.US, "%.2f m", glazMVal)
        val cauchoM = String.format(Locale.US, "%.2f m", cauchoMVal)

        // Fila Ventana
        binding.cellVZona.text = areaZona
        binding.cellVMarcos.text = marcosM
        binding.cellVImpostas.text = impostasM
        binding.cellVGlaz.text = glazM
        binding.cellVCaucho.text = cauchoM
        binding.cellVHojas.text = if (config.hojasCount > 1) "${config.hojasCount}" else "-"
        binding.cellVHojasP.text = if (config.categoria == "Puerta-Ventana") "1" else "-"
        binding.cellVTotal.text = "0 S/"

        // Fila Precio (azul)
        binding.cellPZona.text = "-"
        binding.cellPMarcos.text = "-"
        binding.cellPImpostas.text = "-"
        binding.cellPGlaz.text = "-"
        binding.cellPCaucho.text = "-"
        binding.cellPHojas.text = "-"
        binding.cellPHojasP.text = "-"
        binding.cellPTotal.text = "0.00 (-0%)"

        // Sumatoria para 'todas las ventanas' en la orden
        var totalAreaOrden = 0.0
        var totalMarcosOrden = 0.0
        for (item in itemsOrden) {
            val hM = item.altoTotalMm / 1000.0
            val wM = item.anchoTotalMm / 1000.0
            totalAreaOrden += (hM * wM)
            totalMarcosOrden += ((2 * hM) + (2 * wM))
        }

        binding.cellTvZona.text = String.format(Locale.US, "%.2f m2", totalAreaOrden)
        binding.cellTvMarcos.text = String.format(Locale.US, "%.2f m", totalMarcosOrden)
        binding.cellTvImpostas.text = impostasM
        binding.cellTvGlaz.text = glazM
        binding.cellTvCaucho.text = cauchoM
        binding.cellTvHojas.text = if (config.hojasCount > 1) "${config.hojasCount}" else "-"
        binding.cellTvHojasP.text = if (config.categoria == "Puerta-Ventana") "1" else "-"
        binding.cellTvTotal.text = "0 S/"

        // Fila Costo (rojo)
        binding.cellCZona.text = "-"
        binding.cellCMarcos.text = "-"
        binding.cellCImpostas.text = "-"
        binding.cellCGlaz.text = "-"
        binding.cellCCaucho.text = "-"
        binding.cellCHojas.text = "-"
        binding.cellCHojasP.text = "-"
        binding.cellCTotal.text = "0.00 (-0%)"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
