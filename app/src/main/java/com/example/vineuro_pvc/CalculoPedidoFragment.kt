package com.example.vineuro_pvc

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.vineuro_pvc.data.ClientRepository
import com.example.vineuro_pvc.data.WindowConfig
import com.example.vineuro_pvc.data.WindowConfigCatalog
import com.example.vineuro_pvc.databinding.DialogSelectConfigBinding
import com.example.vineuro_pvc.databinding.FragmentCalculoPedidoBinding
import com.example.vineuro_pvc.views.WindowPreviewView
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.Locale

/**
 * Fragmento de Cálculo y Diseño de Ventanas con apertura editable por paño/panel (+).
 */
class CalculoPedidoFragment : Fragment() {

    private var _binding: FragmentCalculoPedidoBinding? = null
    private val binding get() = _binding!!

    private lateinit var clientRepository: ClientRepository
    private val listaConfiguraciones = mutableListOf<WindowConfig>()
    private var configuracionSeleccionada: WindowConfig? = null

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

        // 2. Configurar listener táctil al pulsar en cualquier (+) de los paños de la plantilla
        binding.wpvMainCanvas.onPaneClickListener = { paneIndex, _, _ ->
            mostrarDialogoSeleccionConfigParaPano(paneIndex)
        }

        // Botón azul inferior (+) para abrir el catálogo general de configuraciones
        binding.btnAddItem.setOnClickListener {
            mostrarDialogoSeleccionConfigParaPano(0)
        }

        // Botón Eliminar
        binding.btnDeleteItem.setOnClickListener {
            Toast.makeText(requireContext(), "Elemento limpiado", Toast.LENGTH_SHORT).show()
        }

        // Cargar por defecto la configuración de 3 Paneles Horizontales (idéntico a la imagen)
        if (listaConfiguraciones.size > 5) {
            aplicarConfiguracion(listaConfiguraciones[5])
        }
    }

    private fun inicializarConfiguraciones() {
        listaConfiguraciones.clear()
        listaConfiguraciones.addAll(WindowConfigCatalog.obtenerTodas())
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
                    configuracionSeleccionada?.let { actualizarTablaMatriz(it) }
                    Toast.makeText(requireContext(), "Medida actualizada: $nuevoValor mm", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Ingrese una medida válida en mm", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("CANCELAR") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun mostrarDialogoSeleccionConfigParaPano(targetPaneIndex: Int) {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogSelectConfigBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        var tempConfigSeleccionada: WindowConfig = configuracionSeleccionada ?: listaConfiguraciones.first()
        var tempMecanismoSeleccionado: AperturaMechanism = listaMecanismosApertura.find { it.id == "5" } ?: listaMecanismosApertura.first()

        // 1. Cargar panel 'impostas' (layouts)
        val layoutAdapter = WindowConfigAdapter(
            listaConfiguraciones.take(12),
            selectedId = tempConfigSeleccionada.id,
        ) { config ->
            tempConfigSeleccionada = config
        }
        dialogBinding.rvImpostasLayouts.adapter = layoutAdapter

        // 2. Cargar panel 'apertura' (mecanismos)
        val aperturaAdapter = AperturaAdapter(
            listaMecanismosApertura,
            selectedId = binding.wpvMainCanvas.paneMechanisms[targetPaneIndex] ?: "5",
        ) { mecanismo ->
            tempMecanismoSeleccionado = mecanismo
        }
        dialogBinding.rvAperturaMechanisms.adapter = aperturaAdapter

        // Botón NO (Cancelar)
        dialogBinding.btnDialogNo.setOnClickListener {
            dialog.dismiss()
        }

        // Botón SÍ (Confirmar)
        dialogBinding.btnDialogSi.setOnClickListener {
            var finalConfig = tempConfigSeleccionada

            if (dialogBinding.cbPuerta.isChecked && finalConfig.categoria != "Puerta-Ventana") {
                val puertaConfig = listaConfiguraciones.find { it.categoria == "Puerta-Ventana" }
                if (puertaConfig != null) finalConfig = puertaConfig
            }

            // Aplicar la configuración al canvas y el mecanismo de apertura al paño seleccionado
            aplicarConfiguracion(finalConfig)
            binding.wpvMainCanvas.setPaneMechanism(targetPaneIndex, tempMecanismoSeleccionado.id)

            dialog.dismiss()

            val msg = buildString {
                append("Paño ${targetPaneIndex + 1}: ")
                append(tempMecanismoSeleccionado.nombre)
                if (dialogBinding.cbPuerta.isChecked) append(" | Modo Puerta")
                if (dialogBinding.cbMosqNet.isChecked) append(" | Malla Mosquitera")
                if (dialogBinding.cbSandwichPanels.isChecked) append(" | Panel Sándwich")
            }
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun aplicarConfiguracion(config: WindowConfig) {
        configuracionSeleccionada = config

        // 1. Dibujar diagrama en el Canvas
        binding.wpvMainCanvas.setConfig(config, isTechnicalMode = true)

        // 2. Actualizar los cálculos de la tabla matriz
        actualizarTablaMatriz(config)
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

        // Fila Todas las Ventanas
        binding.cellTvZona.text = areaZona
        binding.cellTvMarcos.text = marcosM
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
