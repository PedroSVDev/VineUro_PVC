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
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.CheckBox
import android.view.Gravity
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.vineuro_pvc.data.CalculadoraPrecios
import com.example.vineuro_pvc.data.ClientRepository
import com.example.vineuro_pvc.data.OrderItem
import com.example.vineuro_pvc.data.OrderItemHolder
import com.example.vineuro_pvc.data.Pedido
import com.example.vineuro_pvc.data.PedidoRepository
import com.example.vineuro_pvc.data.Precios
import com.example.vineuro_pvc.data.PreciosRepository
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
    private var clienteIdActual: String = ""
    private var nombreClienteActual: String = ""

    // Precios (alimentan el menú lateral del compás)
    private lateinit var preciosRepository: PreciosRepository
    private var precios: Precios = Precios.porDefecto()

    private val listaMecanismosApertura = listOf(
        AperturaMechanism("1", "Desliz. Der.", R.drawable.ic_apertura_arrow_right),
        AperturaMechanism("2", "Oscilobat. Sup.", R.drawable.ic_apertura_tilt_top),
        AperturaMechanism("3", "Desliz. Izq.", R.drawable.ic_apertura_arrow_left),
        AperturaMechanism("4", "Practicable Izq.", R.drawable.ic_apertura_left_turn),
        AperturaMechanism("5", "Fijo Crosshair", R.drawable.ic_apertura_fixed_plus),
        AperturaMechanism("6", "Practicable Der.", R.drawable.ic_apertura_right_turn),
        AperturaMechanism("7", "Oscilobat. Izq.", R.drawable.ic_apertura_tilt_turn_left),
        AperturaMechanism("8", "Oscilobat. Der.", R.drawable.ic_apertura_tilt_turn_right),
        AperturaMechanism("9", "Proyectante", 0),
        AperturaMechanism("10", "Guillotina Sub.", 0),
        AperturaMechanism("11", "Guillotina Baj.", 0),
    )

    /** Aperturas combinadas (una por parte) según la división [filas]x[columnas] del paño. */
    private fun combosApertura(filas: Int, columnas: Int): List<AperturaMechanism> {
        val partes = when ("${filas}x$columnas") {
            "1x2" -> listOf("1,3", "1,5", "5,3", "4,6", "7,6", "4,8", "7,5", "5,8", "4,5", "5,6", "2,2", "7,8")
            "1x3" -> listOf("5,3,3", "1,1,5", "5,3,5", "5,1,5", "1,3,3", "1,1,3", "1,5,3", "4,5,6", "7,5,8", "5,7,5", "5,8,5", "2,5,2")
            "1x4" -> listOf("1,1,3,3", "5,1,3,5", "1,3,1,3", "4,6,4,6", "5,4,6,5", "1,5,5,3", "7,6,7,6", "7,5,5,8")
            "2x1" -> listOf("5,10", "11,10", "11,5", "2,5", "5,2", "9,5", "2,8", "2,7", "2,4", "2,6")
            "3x1" -> listOf("2,5,5", "5,5,2", "2,5,2", "5,10,5", "2,7,5", "2,8,5", "2,4,5", "2,6,5")
            else -> emptyList()
        }
        return partes.map { AperturaMechanism("c:$it", "Combinada", 0, partes = it) }
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

        // Traer en segundo plano los precios más recientes de Supabase (para el menú del compás)
        precios = preciosRepository.obtener()
        viewLifecycleOwner.lifecycleScope.launch {
            precios = preciosRepository.sincronizar()
            refrescarTablaMatriz()
        }

        // Si la vista se recrea (p. ej. al volver del PDF) se recarga el pedido ya autoguardado
        val pedidoId = pedidoActual?.id ?: arguments?.getString("pedido_id")
        pedidoActual = pedidoId?.let { pedidoRepository.obtenerPedido(it) } ?: pedidoActual

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

        binding.tvHeaderClienteNombre.text = nombreHeader

        // 1. Configurar listener táctil en las cotas de medidas
        binding.wpvMainCanvas.onDimensionClickListener = { dimensionType, sectionIndex, currentValue ->
            mostrarDialogoEditarMedida(dimensionType, sectionIndex, currentValue)
        }

        // 2. Configurar listener táctil al pulsar en cualquier (+) de los paños de la plantilla
        binding.wpvMainCanvas.onPaneClickListener = { paneIndex, _, _ ->
            mostrarDialogoSeleccionConfigParaPano(paneIndex)
        }
        // Toque sobre una sola parte de una apertura combinada: se cambia solo esa parte
        binding.wpvMainCanvas.onPaneParteClickListener = { paneIndex, parte ->
            mostrarDialogoParteDePano(paneIndex, parte)
        }

        // 3. Botón azul inferior (+): Abre el CATÁLOGO GENERAL
        binding.btnAddItem.setOnClickListener {
            mostrarDialogoSeleccionCatalogGeneral()
        }

        // "Sombras" y "Parámetros": siempre desactivados en un diseño nuevo.
        // Si se abre un pedido guardado, se recupera lo que tenía ese pedido.
        val idGuardado = pedidoActual?.id
        binding.cbParamComprobar.isChecked = idGuardado != null && prefsUi().getBoolean("param_$idGuardado", false)
        binding.cbShadows.isChecked = idGuardado != null && prefsUi().getBoolean("sombras_$idGuardado", false)
        binding.wpvMainCanvas.showShadows = binding.cbShadows.isChecked
        binding.cbShadows.setOnCheckedChangeListener { _, marcado ->
            binding.wpvMainCanvas.showShadows = marcado
            guardarPreferenciasVista()
            renderizarPestanasInferiores()
        }
        binding.cbParamComprobar.setOnCheckedChangeListener { _, _ ->
            guardarPreferenciasVista()
            actualizarPanelParametros()
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

        val pedidoCargado = pedidoActual
        if (pedidoCargado == null) {
            // Pedido nuevo: se trae el último N° de la nube para no repetir números entre teléfonos
            val appCtx = requireContext().applicationContext
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching { com.example.vineuro_pvc.data.Correlativos(appCtx).sincronizar(com.example.vineuro_pvc.data.Correlativos.COTIZACION) }
            }
        }
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
        PanelConfigVentana(requireContext(), preciosRepository.obtener(), item.opciones) { nuevasOpciones ->
            item.opciones = nuevasOpciones
            refrescarTablaMatriz()
            guardarPedido(mostrarResultado = false)
            Toast.makeText(requireContext(), "Configuración de la ventana ${item.index} guardada", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(requireContext(), "Pedido ${pedido.codigoVisible} cargado (${pedido.totalVentanas} ventanas)", Toast.LENGTH_SHORT).show()
    }

    /** Construye el Pedido con el estado actual de la pantalla (mantiene id y código si ya existía). */
    private fun construirPedido(): Pedido {
        guardarEstadoItemActivo()
        val ahora = System.currentTimeMillis()
        val base = pedidoActual
        return if (base == null) {
            Pedido(
                // N° correlativo (0405, 0406…): el mismo que la cotización de la oferta comercial
                codigo = com.example.vineuro_pvc.data.TerminosRepository(requireContext()).siguienteNumeroLocal(requireContext()),
                clienteId = clienteIdActual,
                clienteNombre = nombreClienteActual,
                items = itemsOrden.toList(),
                fechaCreacion = ahora,
                fechaActualizacion = ahora,
            )
        } else {
            base.copy(
                clienteId = clienteIdActual,
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
    private fun prefsUi() =
        requireContext().getSharedPreferences("pref_ui", android.content.Context.MODE_PRIVATE)

    /** Recuerda "Sombras" y "Parámetros" para este pedido (solo si ya tiene id). */
    private fun guardarPreferenciasVista() {
        val b = _binding ?: return
        val id = pedidoActual?.id ?: return
        prefsUi().edit()
            .putBoolean("sombras_$id", b.cbShadows.isChecked)
            .putBoolean("param_$id", b.cbParamComprobar.isChecked)
            .apply()
    }

    private fun guardarPedido(mostrarResultado: Boolean) {
        if (itemsOrden.isEmpty()) {
            if (mostrarResultado) {
                Toast.makeText(requireContext(), "Agregue al menos una ventana antes de guardar", Toast.LENGTH_SHORT).show()
            }
            return
        }
        val pedido = construirPedido()
        pedidoActual = pedido
        guardarPreferenciasVista()
        val appContext = requireContext().applicationContext
        lifecycleScope.launch {
            val error = pedidoRepository.guardarPedido(pedido)
            if (!mostrarResultado) return@launch
            val msg = if (error == null) {
                "Pedido ${pedido.codigoVisible} guardado en la nube ✅"
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

        // 1. CREAR PDF SÓLO PROYECTOS (hasta 6 diseños por hoja)
        dialogBinding.btnPdfSoloProyectos.setOnClickListener {
            if (itemsOrden.isEmpty()) {
                Toast.makeText(requireContext(), "Agrega al menos una ventana al pedido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            OrderItemHolder.conDetalles = false
            OrderItemHolder.guiaCorte = false
            guardarEstadoItemActivo()
            OrderItemHolder.items = itemsOrden.toList()
            OrderItemHolder.clientName = nombreClienteActual
            OrderItemHolder.clienteId = clienteIdActual
            // Guardar antes de generar el PDF para tener el código del pedido
            guardarPedido(mostrarResultado = false)
            OrderItemHolder.codigoPedido = pedidoActual?.codigo ?: ""
            dialog.dismiss()
            findNavController().navigate(R.id.action_CalculoPedidoFragment_to_PdfPreviewFragment)
        }

        // 2. CREAR PDF CON LOS DETALLES (2 por hoja, dibujo a color, tabla completa de precios)
        dialogBinding.btnPdfConDetalles.setOnClickListener {
            if (itemsOrden.isEmpty()) {
                Toast.makeText(requireContext(), "Agrega al menos una ventana al pedido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            OrderItemHolder.conDetalles = true
            OrderItemHolder.guiaCorte = false
            guardarEstadoItemActivo()
            OrderItemHolder.items = itemsOrden.toList()
            OrderItemHolder.clientName = nombreClienteActual
            OrderItemHolder.clienteId = clienteIdActual
            guardarPedido(mostrarResultado = false)
            OrderItemHolder.codigoPedido = pedidoActual?.codigo ?: ""
            dialog.dismiss()
            findNavController().navigate(R.id.action_CalculoPedidoFragment_to_PdfPreviewFragment)
        }

        // 2b. GUÍA DE CORTE (para el vendedor / taller): piezas, barras y vidrios
        dialogBinding.btnPdfGuiaCorte.setOnClickListener {
            dialog.dismiss()
            if (itemsOrden.isEmpty()) {
                Toast.makeText(requireContext(), "Agrega al menos una ventana al pedido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            guardarEstadoItemActivo()
            guardarPedido(mostrarResultado = false)
            generarGuiaCorte()
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
            findNavController().navigate(R.id.OfertaComercialFragment, Bundle().apply { putString("pedido_id", id) })
        }

        // 4. PDF OFERTA COMERCIAL 2 (Desbloqueado)
        dialogBinding.btnPdfOfertaComercial2.setOnClickListener {
            Toast.makeText(requireContext(), "Generador de Oferta Comercial 2 en desarrollo", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
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
        dialogBinding.btnCerrarMenuX.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
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

        val window1 = OrderItem.create(
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
            tabBinding.wpvTabThumb.paneRows.clear()
            tabBinding.wpvTabThumb.paneRows.putAll(item.paneRows)
            tabBinding.wpvTabThumb.paneSandwichMask.clear()
            tabBinding.wpvTabThumb.paneSandwichMask.putAll(item.paneSandwichMask)
            tabBinding.wpvTabThumb.paneBarras.clear()
            tabBinding.wpvTabThumb.paneBarras.putAll(item.paneBarras)
            tabBinding.wpvTabThumb.panePartMechanisms.clear()
            tabBinding.wpvTabThumb.panePartMechanisms.putAll(item.panePartMechanisms)

            tabBinding.wpvTabThumb.setConfig(item.config, isTechnicalMode = false, resetMechanisms = false)
            tabBinding.wpvTabThumb.showShadows = binding.cbShadows.isChecked

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
            item.paneRows.clear()
            item.paneRows.putAll(binding.wpvMainCanvas.paneRows)
            item.paneSandwichMask.clear()
            item.paneSandwichMask.putAll(binding.wpvMainCanvas.paneSandwichMask)
            item.paneBarras.clear()
            item.paneBarras.putAll(binding.wpvMainCanvas.paneBarras)
            item.panePartMechanisms.clear()
            item.panePartMechanisms.putAll(binding.wpvMainCanvas.panePartMechanisms)
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
        binding.wpvMainCanvas.paneRows.clear()
        binding.wpvMainCanvas.paneRows.putAll(item.paneRows)
        binding.wpvMainCanvas.paneSandwichMask.clear()
        binding.wpvMainCanvas.paneSandwichMask.putAll(item.paneSandwichMask)
        binding.wpvMainCanvas.paneBarras.clear()
        binding.wpvMainCanvas.paneBarras.putAll(item.paneBarras)
        binding.wpvMainCanvas.panePartMechanisms.clear()
        binding.wpvMainCanvas.panePartMechanisms.putAll(item.panePartMechanisms)

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
            val newItem = OrderItem.create(
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
        val lienzo = binding.wpvMainCanvas
        val ctx = requireContext()
        val dp = resources.displayMetrics.density

        // ---- Estado actual del paño ----
        var filas = lienzo.paneRows[targetPaneIndex] ?: 1
        var columnas = lienzo.paneCols[targetPaneIndex] ?: 1
        val barrasGuardadas = lienzo.paneBarras[targetPaneIndex] ?: 0
        var usarBarras = barrasGuardadas > 0
        var barrasFilas = if (barrasGuardadas > 0) barrasGuardadas / 100 else 3
        var barrasColumnas = if (barrasGuardadas > 0) barrasGuardadas % 100 else 3
        var mascara = lienzo.paneSandwichMask[targetPaneIndex]
            ?: if (lienzo.paneSandwich[targetPaneIndex] == true) (1 shl (filas * columnas)) - 1 else 0
        fun opcionesApertura(): List<AperturaMechanism> =
            combosApertura(filas, columnas) + listaMecanismosApertura
        val partesGuardadas = lienzo.panePartMechanisms[targetPaneIndex]
        var tempMecanismoSeleccionado: AperturaMechanism =
            opcionesApertura().find { it.partes != null && it.partes == partesGuardadas }
                ?: listaMecanismosApertura.find { it.id == (lienzo.paneMechanisms[targetPaneIndex] ?: "5") }
                ?: listaMecanismosApertura.first()
        var aperturaAdapter: AperturaAdapter? = null
        var impostasAdapter: WindowConfigAdapter? = null
        var vistaBarras: com.example.vineuro_pvc.views.WindowPreviewView? = null

        /** Vuelve a dibujar las vistas previas con las partes sándwich (blanco) y los barrotillos actuales. */
        fun refrescarPreviews() {
            vistaBarras?.let {
                it.paneBarras[0] = barrasFilas * 100 + barrasColumnas
                it.invalidate()
            }
            impostasAdapter?.refrescar()
            aperturaAdapter?.let {
                it.mascaraSandwich = mascara
                it.barras = if (usarBarras) barrasFilas * 100 + barrasColumnas else 0
                it.refrescar()
            }
        }

        dialogBinding.cbMosqNet.isChecked = lienzo.paneMosqNet[targetPaneIndex] == true
        dialogBinding.cbPuerta.isChecked = lienzo.panePuerta[targetPaneIndex] == true

        // ---- Casillas de panel sándwich: una por cada parte del paño ----
        // La casilla original queda como título "sandwich panels"
        dialogBinding.cbSandwichPanels.buttonDrawable = null
        dialogBinding.cbSandwichPanels.isClickable = false
        val contenedorSandwich = dialogBinding.contenedorPartesSandwich

        fun refrescarSandwich() {
            contenedorSandwich.removeAllViews()
            val partes = filas * columnas
            mascara = mascara and ((1 shl partes) - 1)
            for (p in 0 until partes) {
                contenedorSandwich.addView(CheckBox(ctx).apply {
                    isChecked = ((mascara shr p) and 1) == 1
                    text = nombreParte(p, filas, columnas)
                    textSize = 11f
                    buttonTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1E3A5F"))
                    contentDescription = "Parte ${p + 1}"
                    setOnCheckedChangeListener { _, marcado ->
                        mascara = if (marcado) mascara or (1 shl p) else mascara and (1 shl p).inv()
                        refrescarPreviews()
                    }
                })
            }
        }

        // ---- Barrotillos: arriba en la columna "apertura", antes de los diseños.
        //      Con + / − se arma la cuadrícula propia y las vistas previas cambian al instante.
        val panelBarras = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#E6E6E6"))
            setPadding((10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt())
        }
        fun stepper(etiqueta: String, valor: () -> Int, cambiar: (Int) -> Unit): LinearLayout {
            val tv = TextView(ctx).apply { textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding((10 * dp).toInt(), 0, (10 * dp).toInt(), 0) }
            fun pintar() { tv.text = valor().toString() }
            fun boton(t: String, delta: Int) = TextView(ctx).apply {
                text = t; textSize = 18f; gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.parseColor("#4A90E2")) }
                layoutParams = LinearLayout.LayoutParams((30 * dp).toInt(), (30 * dp).toInt())
                setOnClickListener { cambiar((valor() + delta).coerceIn(1, 8)); pintar() }
            }
            pintar()
            return LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(0, (2 * dp).toInt(), 0, (2 * dp).toInt())
                addView(TextView(ctx).apply { text = etiqueta; textSize = 13f; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
                addView(boton("−", -1)); addView(tv); addView(boton("+", +1))
            }
        }
        panelBarras.addView(TextView(ctx).apply { text = "Barrotillos · arma tu diseño"; textSize = 13f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(Color.parseColor("#1E3A5F")) })
        // Dibujo en blanco: muestra la cuadrícula que se va armando con + / −
        vistaBarras = com.example.vineuro_pvc.views.WindowPreviewView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((84 * dp).toInt(), (84 * dp).toInt()).apply { marginEnd = (8 * dp).toInt() }
            mostrarSimboloApertura = false
            setConfig(WindowConfig("prev_barras", "", "Estándar", WindowConfig.LayoutType.GRID, cols = 1, rows = 1), isTechnicalMode = false)
        }
        panelBarras.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(vistaBarras)
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(stepper("Filas", { barrasFilas }) { barrasFilas = it; refrescarPreviews() })
                addView(stepper("Columnas", { barrasColumnas }) { barrasColumnas = it; refrescarPreviews() })
            })
        })
        val columnaApertura = dialogBinding.rvAperturaMechanisms.parent as ViewGroup
        columnaApertura.addView(panelBarras, columnaApertura.indexOfChild(dialogBinding.rvAperturaMechanisms))

        fun refrescarPaneles() {
            panelBarras.visibility = if (usarBarras) View.VISIBLE else View.GONE
            refrescarSandwich()
            refrescarPreviews()
        }

        // ---- Opciones de impostas (id: imp_[filas]x[columnas]) ----
        val opcionesImpostas = listOf(
            WindowConfig("imp_g", "Barrotillos", "Estándar", WindowConfig.LayoutType.GRID, cols = 3, rows = 3),
            WindowConfig("imp_1x1", "1 paño", "Estándar", WindowConfig.LayoutType.GRID, cols = 1, rows = 1),
            WindowConfig("imp_1x2", "2 vertical", "Estándar", WindowConfig.LayoutType.GRID, cols = 2, rows = 1),
            WindowConfig("imp_2x1", "2 horizontal", "Estándar", WindowConfig.LayoutType.GRID, cols = 1, rows = 2),
            WindowConfig("imp_1x3", "3 vertical", "Estándar", WindowConfig.LayoutType.GRID, cols = 3, rows = 1),
            WindowConfig("imp_3x1", "3 horizontal", "Estándar", WindowConfig.LayoutType.GRID, cols = 1, rows = 3),
            WindowConfig("imp_1x4", "4 vertical", "Estándar", WindowConfig.LayoutType.GRID, cols = 4, rows = 1),
        )
        val idInicial = if (usarBarras) "imp_g" else "imp_${filas}x$columnas"
        impostasAdapter = WindowConfigAdapter(opcionesImpostas, selectedId = idInicial, tamPreviewDp = 50) { config ->
            if (config.id == "imp_g") {
                usarBarras = true; filas = 1; columnas = 1
            } else {
                usarBarras = false; filas = config.rows; columnas = config.cols
            }
            refrescarPaneles()
            // Las aperturas combinadas dependen de la división elegida
            if (tempMecanismoSeleccionado.partes != null) {
                tempMecanismoSeleccionado = listaMecanismosApertura.first { it.id == "5" }
            }
            aperturaAdapter?.actualizar(opcionesApertura(), filas, columnas, tempMecanismoSeleccionado.id)
            dialogBinding.rvAperturaMechanisms.scrollToPosition(0)
        }
        // En la imposta elegida, cada parte marcada como sándwich se pinta en blanco (las demás quedan celestes)
        impostasAdapter?.personalizarPreview = { config, vista, seleccionada ->
            if (seleccionada && config.id != "imp_g") {
                for (i in 0 until config.rows * config.cols) {
                    vista.paneSandwichMask[i] = (mascara shr i) and 1
                }
            }
        }
        dialogBinding.rvImpostasLayouts.adapter = impostasAdapter

        aperturaAdapter = AperturaAdapter(
            opcionesApertura(),
            selectedId = tempMecanismoSeleccionado.id,
            filas = filas,
            columnas = columnas,
        ) { mecanismo ->
            tempMecanismoSeleccionado = mecanismo
        }
        dialogBinding.rvAperturaMechanisms.adapter = aperturaAdapter
        refrescarPaneles()

        ajustarDialogoPano(dialogBinding, dialog)

        dialogBinding.btnDialogNo.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnDialogSi.setOnClickListener {
            val isPuerta = dialogBinding.cbPuerta.isChecked
            val isMosqNet = dialogBinding.cbMosqNet.isChecked

            val partesElegidas = tempMecanismoSeleccionado.partes
            if (partesElegidas != null) {
                lienzo.setPanePartMechanisms(targetPaneIndex, partesElegidas)
            } else {
                lienzo.setPaneMechanism(targetPaneIndex, tempMecanismoSeleccionado.id)
            }
            lienzo.setPaneDivision(
                targetPaneIndex, filas, columnas, mascara,
                if (usarBarras) barrasFilas else 0, if (usarBarras) barrasColumnas else 0,
                isPuerta, isMosqNet,
            )

            guardarEstadoItemActivo()
            renderizarPestanasInferiores()
            refrescarTablaMatriz()
            dialog.dismiss()

            val msg = buildString {
                append("Paño ${targetPaneIndex + 1}: ")
                append(tempMecanismoSeleccionado.nombre)
                tempMecanismoSeleccionado.partes?.let { append(" ($it)") }
                if (filas * columnas > 1) append(" | ${filas}x$columnas partes")
                if (usarBarras) append(" | Barrotillos ${barrasFilas}x$barrasColumnas")
                if (mascara != 0) append(" | Sándwich: ${Integer.bitCount(mascara)}")
                if (isPuerta) append(" | Puerta")
                if (isMosqNet) append(" | Mosquitero")
            }
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    /**
     * Ajuste a la pantalla (teléfono / tablet), manteniendo las dos columnas:
     * izquierda (45%) impostas, derecha (55%) apertura. Las columnas de cada cuadrícula
     * dependen del ancho, y el alto usa buena parte de la pantalla para no tener que bajar.
     */
    private fun ajustarDialogoPano(db: DialogSelectConfigBinding, dialog: BottomSheetDialog) {
        val ctx = requireContext()
        val metricas = resources.displayMetrics
        val dp = metricas.density
        val anchoDp = metricas.widthPixels / dp
        val altoPx = metricas.heightPixels
        db.rvImpostasLayouts.layoutManager =
            androidx.recyclerview.widget.GridLayoutManager(ctx, (anchoDp * 0.45f / 78f).toInt().coerceIn(2, 5))
        db.rvAperturaMechanisms.layoutManager =
            androidx.recyclerview.widget.GridLayoutManager(ctx, (anchoDp * 0.55f / 96f).toInt().coerceIn(3, 8))
        db.cuerpoDosColumnas.layoutParams = db.cuerpoDosColumnas.layoutParams.apply {
            height = (altoPx * 0.62f).toInt().coerceIn((340 * dp).toInt(), (640 * dp).toInt())
        }
        dialog.behavior.skipCollapsed = true
        dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
    }

    /** Nombre de una parte según la división: Izquierda / Centro / Derecha, Arriba / Abajo, etc. */
    private fun nombreParte(parte: Int, filas: Int, columnas: Int): String {
        fun nombre(i: Int, n: Int, primero: String, ultimo: String) = when {
            i == 0 -> primero
            i == n - 1 -> ultimo
            n == 3 -> "Centro"
            else -> "Centro ${i}"
        }
        return when {
            filas * columnas <= 1 -> "Paño"
            filas == 1 -> nombre(parte, columnas, "Izq.", "Der.")
            columnas == 1 -> nombre(parte, filas, "Arriba", "Abajo")
            else -> "${parte + 1}"
        }
    }

    /**
     * Diálogo para UNA parte de un paño con apertura combinada (p. ej. el fijo "+" del medio
     * en "3 vertical"). Solo cambia la apertura y el panel sándwich de esa parte;
     * las otras partes se quedan como están.
     */
    private fun mostrarDialogoParteDePano(paneIndex: Int, parte: Int) {
        val lienzo = binding.wpvMainCanvas
        val partes = lienzo.panePartMechanisms[paneIndex]?.split(",")?.map { it.trim() }
        if (partes == null || parte !in partes.indices) {
            mostrarDialogoSeleccionConfigParaPano(paneIndex)
            return
        }
        val ctx = requireContext()
        val dp = resources.displayMetrics.density
        val filas = lienzo.paneRows[paneIndex] ?: 1
        val columnas = lienzo.paneCols[paneIndex] ?: 1
        val mascaraPano = lienzo.paneSandwichMask[paneIndex] ?: 0
        var sandwichParte = ((mascaraPano shr parte) and 1) == 1
        var elegido = listaMecanismosApertura.find { it.id == partes[parte] }
            ?: listaMecanismosApertura.first { it.id == "5" }

        val dialog = BottomSheetDialog(ctx)
        val db = DialogSelectConfigBinding.inflate(layoutInflater)
        dialog.setContentView(db.root)

        // ---- Columna izquierda: qué parte se edita + dibujo del paño completo ----
        db.rvImpostasLayouts.visibility = View.GONE
        val columnaIzq = db.rvImpostasLayouts.parent as ViewGroup
        val vistaPano = com.example.vineuro_pvc.views.WindowPreviewView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (140 * dp).toInt())
            setConfig(WindowConfig("prev_parte", "", "Estándar", WindowConfig.LayoutType.GRID, cols = 1, rows = 1), isTechnicalMode = false)
            paneRows[0] = filas
            paneCols[0] = columnas
        }
        fun refrescarVistaPano() {
            val nuevas = partes.toMutableList().also { it[parte] = elegido.id }
            vistaPano.setPanePartMechanisms(0, nuevas.joinToString(","))
            val bit = 1 shl parte
            vistaPano.paneSandwichMask[0] = if (sandwichParte) mascaraPano or bit else mascaraPano and bit.inv()
            vistaPano.invalidate()
        }
        columnaIzq.addView(TextView(ctx).apply {
            text = "Parte ${parte + 1} de ${partes.size} · ${nombreParte(parte, filas, columnas)}"
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#1E3A5F"))
            setPadding((4 * dp).toInt(), (4 * dp).toInt(), 0, (4 * dp).toInt())
        }, 0)
        columnaIzq.addView(vistaPano, 1)
        columnaIzq.addView(com.google.android.material.button.MaterialButton(ctx).apply {
            text = "Cambiar todo el paño"
            textSize = 12f
            isAllCaps = false
            setOnClickListener {
                dialog.dismiss()
                mostrarDialogoSeleccionConfigParaPano(paneIndex)
            }
        }, 2)

        // Puerta y mosquitero son del paño completo: aquí solo se muestra el sándwich de esta parte
        (db.cbPuerta.parent.parent.parent as? View)?.visibility = View.GONE
        (db.cbMosqNet.parent.parent.parent as? View)?.visibility = View.GONE
        db.contenedorPartesSandwich.visibility = View.GONE
        db.cbSandwichPanels.text = "panel sándwich en esta parte"
        db.cbSandwichPanels.isChecked = sandwichParte

        // ---- Columna derecha: aperturas simples para esta parte ----
        val adapter = AperturaAdapter(listaMecanismosApertura, selectedId = elegido.id) { mec ->
            elegido = mec
            refrescarVistaPano()
        }
        adapter.mascaraSandwich = if (sandwichParte) 1 else 0
        db.rvAperturaMechanisms.adapter = adapter
        db.cbSandwichPanels.setOnCheckedChangeListener { _, marcado ->
            sandwichParte = marcado
            adapter.mascaraSandwich = if (marcado) 1 else 0
            adapter.refrescar()
            refrescarVistaPano()
        }
        refrescarVistaPano()
        ajustarDialogoPano(db, dialog)

        db.btnDialogNo.setOnClickListener { dialog.dismiss() }
        db.btnDialogSi.setOnClickListener {
            lienzo.setParteMechanism(paneIndex, parte, elegido.id)
            val bit = 1 shl parte
            val nuevaMascara = if (sandwichParte) mascaraPano or bit else mascaraPano and bit.inv()
            lienzo.paneSandwichMask[paneIndex] = nuevaMascara
            lienzo.paneSandwich[paneIndex] = nuevaMascara != 0
            lienzo.invalidate()

            guardarEstadoItemActivo()
            renderizarPestanasInferiores()
            refrescarTablaMatriz()
            dialog.dismiss()
            Toast.makeText(
                ctx,
                "Paño ${paneIndex + 1}, ${nombreParte(parte, filas, columnas)}: ${elegido.nombre}" +
                    if (sandwichParte) " | Sándwich" else "",
                Toast.LENGTH_SHORT,
            ).show()
        }
        dialog.show()
    }

    /** Abre la vista previa de la "Guía de corte" (desde ahí se guarda y comparte). */
    private fun generarGuiaCorte() {
        OrderItemHolder.items = itemsOrden.toList()
        OrderItemHolder.clientName = nombreClienteActual
        OrderItemHolder.clienteId = clienteIdActual
        OrderItemHolder.codigoPedido = pedidoActual?.codigo ?: ""
        OrderItemHolder.conDetalles = false
        OrderItemHolder.guiaCorte = true
        findNavController().navigate(R.id.action_CalculoPedidoFragment_to_PdfPreviewFragment)
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
        _binding?.cardParametros?.visibility = View.GONE
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
            isFocusable = true
            isFocusableInTouchMode = true
        }

        val alertDialog = AlertDialog.Builder(requireContext())
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
            .create()

        input.requestFocus()
        alertDialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        alertDialog.show()
    }

    /** Recalcula la tabla con la ventana activa (se llama al cambiar medidas, paños, opciones o precios). */
    private fun refrescarTablaMatriz() {
        if (_binding == null) return
        if (activeItemIndex in itemsOrden.indices) {
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

        fun m(v: Double) = if (v > 0.0001) String.format(Locale.US, "%.2f m", v) else "-"
        fun m2(v: Double) = if (v > 0.0001) String.format(Locale.US, "%.2f m2", v) else "-"
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
        binding.cellPTotal.text = String.format(Locale.US, "%.2f (-%.0f%%)", pu.total, activo.descuentoPct)

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

        linea("Medidas", "${item.anchoTotalMm} x ${item.altoTotalMm} mm")
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
