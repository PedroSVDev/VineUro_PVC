package com.example.vineuro_pvc

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.vineuro_pvc.data.ClientRepository
import com.example.vineuro_pvc.data.OrderItem
import com.example.vineuro_pvc.data.WindowConfig
import com.example.vineuro_pvc.data.WindowConfigCatalog
import com.example.vineuro_pvc.databinding.DialogSelectConfigBinding
import com.example.vineuro_pvc.databinding.DialogSelectConfigGeneralBinding
import com.example.vineuro_pvc.databinding.FragmentCalculoPedidoBinding
import com.example.vineuro_pvc.databinding.ItemOrderWindowTabBinding
import com.example.vineuro_pvc.views.WindowPreviewView
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.Locale

/**
 * Fragmento de Cálculo y Diseño con separación clara entre Catálogo 73 Modelos (+) y Ajuste Impostas/Apertura por Paño.
 */
class CalculoPedidoFragment : Fragment() {

    private var _binding: FragmentCalculoPedidoBinding? = null
    private val binding get() = _binding!!

    private lateinit var clientRepository: ClientRepository
    private val listaConfiguraciones = mutableListOf<WindowConfig>()

    private val itemsOrden = mutableListOf<OrderItem>()
    private var activeItemIndex: Int = 0

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
        inicializarConfiguraciones()

        val dniCliente = arguments?.getString("dni_cliente") ?: ""
        var nombreHeader = arguments?.getString("nombre_cliente") ?: "Aleksander White : S1"

        if (dniCliente.isNotEmpty()) {
            val cliente = clientRepository.obtenerClientePorDni(dniCliente)
            cliente?.let {
                nombreHeader = "${it.nombreCompleto} : ${it.codigoCliente}"
            }
        }

        binding.tvHeaderClienteNombre.text = nombreHeader

        // 1. Configurar listener táctil en las cotas de medidas
        binding.wpvMainCanvas.onDimensionClickListener = { dimensionType, sectionIndex, currentValue ->
            mostrarDialogoEditarMedida(dimensionType, sectionIndex, currentValue)
        }

        // 2. Configurar listener táctil al pulsar en cualquier (+) de los paños de la plantilla -> Abre 'impostas' / 'apertura' (SÍ/NO)
        binding.wpvMainCanvas.onPaneClickListener = { paneIndex, _, _ ->
            mostrarDialogoSeleccionConfigParaPano(paneIndex)
        }

        // 3. Botón azul inferior (+): Abre el CATÁLOGO GENERAL DE LOS 73 MODELOS
        binding.btnAddItem.setOnClickListener {
            mostrarDialogoSeleccionCatalogGeneral()
        }

        // 4. Botón Eliminar / Papelera
        binding.btnDeleteItem.setOnClickListener {
            if (itemsOrden.isNotEmpty()) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Eliminar Elemento")
                    .setMessage("¿Desea eliminar la Ventana ${itemsOrden[activeItemIndex].index} de la orden?")
                    .setPositiveButton("ELIMINAR") { dialog, _ ->
                        eliminarElementoActivo()
                        dialog.dismiss()
                    }
                    .setNegativeButton("CANCELAR") { dialog, _ -> dialog.dismiss() }
                    .show()
            }
        }

        inicializarOrden()
    }

    private fun inicializarConfiguraciones() {
        listaConfiguraciones.clear()
        listaConfiguraciones.addAll(WindowConfigCatalog.obtenerTodas())
    }

    private fun inicializarOrden() {
        itemsOrden.clear()
        if (listaConfiguraciones.size > 5) {
            val window1 = OrderItem(
                index = 1,
                config = listaConfiguraciones[5],
                colorBgHex = "#42A5F5",
            )

            val window2 = OrderItem(
                index = 2,
                config = listaConfiguraciones[3],
                colorBgHex = "#CE3B75",
            )

            itemsOrden.add(window1)
            itemsOrden.add(window2)
            activeItemIndex = 0

            renderizarPestanasInferiores()
            cargarItemActivoEnCanvas()
        }
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
            tabBinding.wpvTabThumb.setConfig(item.config, isTechnicalMode = false)

            val colorBg = Color.parseColor(item.colorBgHex)
            tabBinding.cardWindowTab.setCardBackgroundColor(colorBg)

            val isActive = (i == activeItemIndex)
            if (isActive) {
                tabBinding.cardWindowTab.strokeColor = Color.WHITE
                tabBinding.cardWindowTab.strokeWidth = 6
            } else {
                tabBinding.cardWindowTab.strokeWidth = 0
            }

            tabBinding.root.setOnClickListener {
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
        }
    }

    private fun cargarItemActivoEnCanvas() {
        if (activeItemIndex !in itemsOrden.indices) return
        val item = itemsOrden[activeItemIndex]

        binding.wpvMainCanvas.sectionHeights = item.sectionHeights.clone()
        binding.wpvMainCanvas.sectionWidths = item.sectionWidths.clone()
        binding.wpvMainCanvas.paneMechanisms.clear()
        binding.wpvMainCanvas.paneMechanisms.putAll(item.paneMechanisms)

        binding.wpvMainCanvas.updateDimensions(item.altoTotalMm, item.anchoTotalMm)
        binding.wpvMainCanvas.setConfig(item.config, isTechnicalMode = true)

        actualizarTablaMatriz(item.config)
    }

    /**
     * Muestra el CATÁLOGO GENERAL DE LOS 73 MODELOS al pulsar el botón azul (+) inferior.
     */
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

        // Filtro por categorías
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

        // Botón ACEPTAR MODELO
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

    /**
     * Muestra el diálogo de 'impostas' y 'apertura' (SÍ/NO) al pulsar en un (+) del dibujo en el canvas.
     */
    private fun mostrarDialogoSeleccionConfigParaPano(targetPaneIndex: Int) {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogSelectConfigBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val currentItem = if (activeItemIndex in itemsOrden.indices) itemsOrden[activeItemIndex] else null
        var tempConfigSeleccionada: WindowConfig = currentItem?.config ?: listaConfiguraciones.first()
        var tempMecanismoSeleccionado: AperturaMechanism = listaMecanismosApertura.find { it.id == "5" } ?: listaMecanismosApertura.first()

        val layoutAdapter = WindowConfigAdapter(
            listaConfiguraciones.take(12),
            selectedId = tempConfigSeleccionada.id,
        ) { config ->
            tempConfigSeleccionada = config
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
            var finalConfig = tempConfigSeleccionada

            if (dialogBinding.cbPuerta.isChecked && finalConfig.categoria != "Puerta-Ventana") {
                val puertaConfig = listaConfiguraciones.find { it.categoria == "Puerta-Ventana" }
                if (puertaConfig != null) finalConfig = puertaConfig
            }

            currentItem?.config = finalConfig
            aplicarConfiguracionDirecta(finalConfig)
            binding.wpvMainCanvas.setPaneMechanism(targetPaneIndex, tempMecanismoSeleccionado.id)
            guardarEstadoItemActivo()
            renderizarPestanasInferiores()

            dialog.dismiss()

            val msg = buildString {
                append("Paño ${targetPaneIndex + 1}: ")
                append(tempMecanismoSeleccionado.nombre)
                if (dialogBinding.cbPuerta.isChecked) append(" | Modo Puerta")
            }
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun aplicarConfiguracionDirecta(config: WindowConfig) {
        if (activeItemIndex in itemsOrden.indices) {
            itemsOrden[activeItemIndex].config = config
        }
        binding.wpvMainCanvas.setConfig(config, isTechnicalMode = true)
        actualizarTablaMatriz(config)
    }

    private fun eliminarElementoActivo() {
        if (itemsOrden.size > 1) {
            itemsOrden.removeAt(activeItemIndex)
            for (idx in itemsOrden.indices) {
                itemsOrden[idx].index = idx + 1
            }
            activeItemIndex = Math.max(0, activeItemIndex - 1)
            renderizarPestanasInferiores()
            cargarItemActivoEnCanvas()
            Toast.makeText(requireContext(), "Elemento eliminado de la orden", Toast.LENGTH_SHORT).show()
        } else if (itemsOrden.size == 1) {
            val item = itemsOrden[0]
            binding.wpvMainCanvas.resetCanvas()
            limpiarTablaMatriz()
            Toast.makeText(requireContext(), "Lienzo de Ventana ${item.index} limpiado", Toast.LENGTH_SHORT).show()
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
        val titulo = when (type) {
            WindowPreviewView.DimensionType.TOTAL_HEIGHT -> "Editar Alto Total (mm)"
            WindowPreviewView.DimensionType.TOTAL_WIDTH -> "Editar Ancho Total (mm)"
            WindowPreviewView.DimensionType.SECTION_HEIGHT -> "Editar Alto Sección ${index + 1} (mm)"
        }

        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentValue.toString())
            setSelection(text.length)
            hint = "Ingrese valor en mm"
        }

        AlertDialog.Builder(requireContext())
            .setTitle(titulo)
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
                    }
                    guardarEstadoItemActivo()
                    if (activeItemIndex in itemsOrden.indices) {
                        actualizarTablaMatriz(itemsOrden[activeItemIndex].config)
                    }
                    Toast.makeText(requireContext(), "Medida actualizada: $nuevoValor mm", Toast.LENGTH_SHORT).show()
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
