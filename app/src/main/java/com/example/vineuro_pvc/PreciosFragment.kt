package com.example.vineuro_pvc

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.vineuro_pvc.data.FilaColor
import com.example.vineuro_pvc.data.FilaSeleccion
import com.example.vineuro_pvc.data.Precios
import com.example.vineuro_pvc.data.PreciosRepository
import com.example.vineuro_pvc.data.TipoCambioService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.vineuro_pvc.databinding.FragmentPreciosBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Apartado de Precios. Todo lo que se edita aquí se guarda en el teléfono al instante
 * y se sube a Supabase (tabla "precios") al salir de la pantalla.
 */
class PreciosFragment : Fragment() {

    private var _binding: FragmentPreciosBinding? = null
    private val binding get() = _binding!!

    private lateinit var repo: PreciosRepository
    private var hayCambiosSinSubir = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPreciosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo = PreciosRepository(requireContext())
        setupListeners()

        // 1. Mostrar al instante lo guardado en el teléfono
        mostrarPrecios(repo.obtener())

        // 2. Traer la versión de la nube (si es más reciente, reemplaza la pantalla)
        viewLifecycleOwner.lifecycleScope.launch {
            val antes = repo.obtener().fechaActualizacion
            val sincronizados = repo.sincronizar()
            if (_binding == null || hayCambiosSinSubir) return@launch
            if (sincronizados.fechaActualizacion != antes) mostrarPrecios(sincronizados)
        }
    }

    override fun onPause() {
        super.onPause()
        subirSiHayCambios()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnActualizarTipoCambio.setOnClickListener { actualizarTipoCambio() }
        binding.btnConvertir.setOnClickListener { elegirMonedaParaConvertir() }
        mostrarInfoTipoCambioGuardado()

        // Botones de añadir
        binding.btnAddPlanes.setOnClickListener { addRow(binding.tablePlanes, listOf("new_finance", "0", "0", "0"), true, colorType = 3); onDatosCambiados() }
        binding.btnAddVentanas.setOnClickListener { addRow(binding.tableVentanas, listOf("new_system") + List(Precios.COLUMNAS_SISTEMA) { "0" }, true, colorType = 4); onDatosCambiados() }
        binding.btnAddCristal.setOnClickListener { addRow(binding.tableCristal, listOf("new_glass") + List(9) { "0" }, true, colorType = 5); onDatosCambiados() }
        binding.btnAddUmbralExt.setOnClickListener { addRow(binding.tableUmbralExt, listOf("new_sill", "0", "0", "-"), true, colorType = 6); onDatosCambiados() }
        binding.btnAddUmbralInt.setOnClickListener { addRow(binding.tableUmbralInt, listOf("new_sill", "0", "0", "-"), true, colorType = 7); onDatosCambiados() }
        binding.btnAddMuebles.setOnClickListener { addRow(binding.tableMuebles, listOf("new_furniture", "0", "0", "0"), true, colorType = 8); onDatosCambiados() }
        binding.btnAddExtras.setOnClickListener { addSelectionRow(binding.tableExtras, "new_extra", "0", 0, true); onDatosCambiados() }
        binding.btnAddEmpleos.setOnClickListener { addSelectionRow(binding.tableEmpleos, "new_job", "0", 0, true); onDatosCambiados() }
        binding.btnAddMosquitera.setOnClickListener { addRow(binding.tableMosquitera, listOf("new_mosq_net") + List(6) { "0" }, true, colorType = 11); onDatosCambiados() }
        binding.btnAddLaminacion.setOnClickListener { addRow(binding.tableLaminacion, listOf("new_lam", "0"), true, colorType = 12); onDatosCambiados() }
        binding.btnAddColors.setOnClickListener {
            if (colorNameExists("new_color")) {
                Toast.makeText(requireContext(), "El color 'new_color' ya existe. Cámbiale el nombre primero.", Toast.LENGTH_SHORT).show()
            } else {
                addColorRow("new_color", Color.WHITE)
                onDatosCambiados()
            }
        }
    }

    // ===================== CARGA / GUARDADO =====================

    /** Todas las tablas simples (fila = lista de celdas) con su colorType. */
    private fun tablasSimples(): List<Triple<TableLayout, Int, Boolean>> = listOf(
        // tabla, colorType, tieneBotonEliminar
        Triple(binding.tableMoneda, 1, false),
        Triple(binding.tableImpuesto, 2, false),
        Triple(binding.tablePlanes, 3, true),
        Triple(binding.tableVentanas, 4, true),
        Triple(binding.tableCristal, 5, true),
        Triple(binding.tableUmbralExt, 6, true),
        Triple(binding.tableUmbralInt, 7, true),
        Triple(binding.tableMuebles, 8, true),
        Triple(binding.tableMosquitera, 11, true),
        Triple(binding.tableLaminacion, 12, true),
    )

    private fun datosDeTablas(p: Precios): List<List<List<String>>> = listOf(
        p.moneda, p.impuesto, p.planes, p.ventanas, p.cristal,
        p.umbralExt, p.umbralInt, p.muebles, p.mosquitera, p.laminacion,
    )

    /** Pinta en pantalla todos los precios (borra las filas actuales, deja los encabezados). */
    private fun mostrarPrecios(p: Precios) {
        val tablas = tablasSimples()
        val datos = datosDeTablas(p)
        tablas.forEachIndexed { i, (tabla, colorType, conEliminar) ->
            limpiarTabla(tabla)
            datos[i].forEach { fila -> addRow(tabla, if ((i == 5 || i == 6) && fila.size == 3) fila + "-" else fila, conEliminar, colorType) }
        }
        limpiarTabla(binding.tableExtras)
        p.extras.forEach { addSelectionRow(binding.tableExtras, it.nombre, it.precio, it.unidad, true) }
        limpiarTabla(binding.tableEmpleos)
        p.empleos.forEach { addSelectionRow(binding.tableEmpleos, it.nombre, it.precio, it.unidad, true) }
        actualizarEncabezadosMoneda(p.moneda.firstOrNull()?.getOrNull(0))
        limpiarTabla(binding.tableColors)
        p.colores.forEach {
            val color = runCatching { Color.parseColor(it.colorHex) }.getOrDefault(Color.WHITE)
            addColorRow(it.nombre, color)
        }
    }

    /** Elimina todas las filas excepto la primera (encabezado). */
    private fun limpiarTabla(tabla: ViewGroup) {
        while (tabla.childCount > 1) tabla.removeViewAt(1)
    }

    /** Lee de la pantalla el estado actual de todas las tablas. */
    private fun recolectarPrecios(): Precios {
        val simples = tablasSimples().map { (tabla, _, _) -> leerTablaSimple(tabla) }
        return Precios(
            moneda = simples[0],
            impuesto = simples[1],
            planes = simples[2],
            ventanas = simples[3],
            cristal = simples[4],
            umbralExt = simples[5],
            umbralInt = simples[6],
            muebles = simples[7],
            mosquitera = simples[8],
            laminacion = simples[9],
            extras = leerTablaSeleccion(binding.tableExtras),
            empleos = leerTablaSeleccion(binding.tableEmpleos),
            colores = leerTablaColores(),
        )
    }

    private fun filasDe(tabla: ViewGroup): List<TableRow> =
        (1 until tabla.childCount).mapNotNull { tabla.getChildAt(it) as? TableRow }

    private fun celdasConTag(row: TableRow, tag: String): List<View> =
        (0 until row.childCount).map { row.getChildAt(it) }.filter { it.tag == tag }

    private fun leerTablaSimple(tabla: ViewGroup): List<List<String>> =
        filasDe(tabla).map { row -> celdasConTag(row, TAG_VALOR).map { (it as TextView).text.toString() } }
            .filter { it.isNotEmpty() }

    private fun leerTablaSeleccion(tabla: ViewGroup): List<FilaSeleccion> =
        filasDe(tabla).mapNotNull { row ->
            val valores = celdasConTag(row, TAG_VALOR).map { (it as TextView).text.toString() }
            if (valores.size < 2) return@mapNotNull null
            val unidad = celdasConTag(row, TAG_UNIDAD).indexOfFirst { it.isSelected }
            FilaSeleccion(valores[0], valores[1], unidad)
        }

    private fun leerTablaColores(): List<FilaColor> =
        filasDe(binding.tableColors).mapNotNull { row ->
            val nombre = (celdasConTag(row, TAG_VALOR).firstOrNull() as? TextView)?.text?.toString() ?: return@mapNotNull null
            val celdaColor = celdasConTag(row, TAG_COLOR).firstOrNull() ?: return@mapNotNull null
            val color = (celdaColor.background as? ColorDrawable)?.color ?: Color.WHITE
            FilaColor(nombre, String.format("#%06X", 0xFFFFFF and color))
        }

    /** Se llama tras cualquier edición: guarda en el teléfono al instante. */
    private fun onDatosCambiados() {
        if (_binding == null) return
        repo.guardarLocal(recolectarPrecios())
        hayCambiosSinSubir = true
    }

    /** Sube a Supabase (en segundo plano, aunque se cierre la pantalla). */
    private fun subirSiHayCambios() {
        if (!hayCambiosSinSubir) return
        hayCambiosSinSubir = false
        val appContext = requireContext().applicationContext
        val repoSubida = repo
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            val error = repoSubida.subir()
            if (error != null) {
                Toast.makeText(appContext, "Precios guardados en el teléfono. La nube respondió: $error", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ===================== FILAS =====================

    private fun addRow(table: ViewGroup, data: List<String>, hasDelete: Boolean, colorType: Int) {
        val row = TableRow(requireContext())
        val isEven = table.childCount % 2 == 0
        row.setBackgroundColor(if (isEven) Color.parseColor("#F9F9F9") else Color.WHITE)

        val bgColor = when (colorType) {
            3 -> Color.parseColor("#FF69B4") // Rosa
            4 -> Color.parseColor("#FFA500") // Naranja
            5 -> Color.parseColor("#1E90FF") // Azul
            else -> Color.TRANSPARENT
        }

        data.forEachIndexed { index, text ->
            val cell = TextView(requireContext()).apply {
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(20, 20, 20, 20)
                this.text = text
                tag = TAG_VALOR
                gravity = android.view.Gravity.CENTER
                setTextColor(Color.BLACK)
                if (bgColor != Color.TRANSPARENT) setBackgroundColor(bgColor)
                setOnClickListener {
                    if (table === _binding?.tableMoneda && (index == 0 || index == 2)) {
                        // "precios moneda" y "resultados moneda": desplegable Soles / Dólares / Euros
                        mostrarSelectorMoneda(this, esMonedaDePrecios = index == 0)
                        return@setOnClickListener
                    }
                    // La primera columna es el nombre (texto); el resto son números salvo que contengan letras/símbolos
                    val esTexto = index == 0 || this.text.toString().toDoubleOrNull() == null
                    if (esTexto) showTextKeypad(this) else showNumericKeypad(this)
                }
            }
            row.addView(cell)
        }

        if (hasDelete) {
            val deleteCell = TextView(requireContext()).apply {
                text = "X"
                setPadding(20, 20, 20, 20)
                setTextColor(Color.parseColor("#CE3B75"))
                gravity = android.view.Gravity.CENTER
                setOnClickListener { showDeleteConfirmation(table, row, nombreDeFila(row)) }
            }
            row.addView(deleteCell)
        }
        table.addView(row)
    }

    private fun addSelectionRow(table: ViewGroup, name: String, price: String, unidad: Int, hasDelete: Boolean) {
        val row = TableRow(requireContext())
        row.setBackgroundColor(Color.parseColor("#F9F9F9"))

        // Nombre
        row.addView(TextView(requireContext()).apply {
            text = name
            tag = TAG_VALOR
            setPadding(20, 20, 20, 20)
            setOnClickListener { showTextKeypad(this) }
        })
        // Precio
        row.addView(TextView(requireContext()).apply {
            text = price
            tag = TAG_VALOR
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor("#C5E1A5"))
            setOnClickListener { showNumericKeypad(this) }
        })
        val selectionBoxes = mutableListOf<View>()
        val drawables = listOf(
            R.drawable.bg_sel_option_0,
            R.drawable.bg_sel_option_1,
            R.drawable.bg_sel_option_2,
            R.drawable.bg_sel_option_3,
            R.drawable.bg_sel_option_4,
            R.drawable.bg_sel_option_5,
        )

        // Botones de selección (pc, m2, m...)
        for (i in 0 until 6) {
            val box = View(requireContext()).apply {
                val size = dpToPx(24)
                layoutParams = TableRow.LayoutParams(size, size).apply { setMargins(10, 10, 10, 10) }
                setBackgroundResource(drawables[i])
                tag = TAG_UNIDAD
                isSelected = (i == unidad)
                setOnClickListener { clicked ->
                    selectionBoxes.forEach { it.isSelected = (it == clicked) }
                    onDatosCambiados()
                }
            }
            selectionBoxes.add(box)
            row.addView(box)
        }

        if (hasDelete) {
            row.addView(TextView(requireContext()).apply {
                text = "X"
                setPadding(20, 20, 20, 20)
                setOnClickListener { showDeleteConfirmation(table, row, nombreDeFila(row)) }
            })
        }
        table.addView(row)
    }

    /** Nombre actual de la fila (la primera celda), aunque se haya editado después de crearla. */
    private fun nombreDeFila(row: TableRow): String =
        (celdasConTag(row, TAG_VALOR).firstOrNull() as? TextView)?.text?.toString() ?: ""

    private fun colorNameExists(name: String): Boolean {
        val table = binding.tableColors
        for (i in 0 until table.childCount) {
            val row = table.getChildAt(i) as? TableRow
            val nameView = row?.getChildAt(0) as? TextView
            if (nameView?.text.toString().trim().equals(name, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun isColorNameDuplicate(name: String, currentTextView: TextView): Boolean {
        val table = binding.tableColors
        for (i in 0 until table.childCount) {
            val row = table.getChildAt(i) as? TableRow
            val nameView = row?.getChildAt(0) as? TextView
            if (nameView != null && nameView != currentTextView) {
                if (nameView.text.toString().trim().equals(name, ignoreCase = true)) {
                    return true
                }
            }
        }
        return false
    }

    private fun addColorRow(name: String, colorValue: Int) {
        val table = binding.tableColors
        val row = TableRow(requireContext())

        row.addView(TextView(requireContext()).apply {
            text = name
            tag = TAG_VALOR
            setPadding(20, 20, 20, 20)
            setOnClickListener { showTextKeypad(this, isColorName = true) }
        })

        val colorCell = View(requireContext()).apply {
            layoutParams = TableRow.LayoutParams(dpToPx(80), dpToPx(40)).apply { setMargins(10, 10, 10, 10) }
            setBackgroundColor(colorValue)
            tag = TAG_COLOR
            setOnClickListener { showColorPicker(this) }
        }
        row.addView(colorCell)

        row.addView(TextView(requireContext()).apply {
            text = "X"
            setPadding(20, 20, 20, 20)
            setOnClickListener { showDeleteConfirmation(table, row, nombreDeFila(row)) }
        })

        table.addView(row)
    }

    // ===================== MONEDA =====================

    /** Desplegable de moneda (Soles / Dólares / Euros) para las celdas de la tabla "moneda". */
    private fun mostrarSelectorMoneda(celda: TextView, esMonedaDePrecios: Boolean) {
        val popup = PopupMenu(requireContext(), celda)
        MONEDAS.forEachIndexed { i, (simbolo, nombre) -> popup.menu.add(0, i, i, "$simbolo  $nombre") }
        popup.setOnMenuItemClickListener { item ->
            val simbolo = MONEDAS[item.itemId].first
            celda.text = simbolo
            val fila = celda.parent as? TableRow
            val valores = fila?.let { celdasConTag(it, TAG_VALOR).map { v -> (v as TextView).text.toString() } }
            val monedaPrecios = valores?.getOrNull(0) ?: simbolo
            val monedaResultado = valores?.getOrNull(2) ?: simbolo
            val celdaTarifa = fila?.let { celdasConTag(it, TAG_VALOR).getOrNull(1) as? TextView }

            if (monedaPrecios == monedaResultado) {
                // Misma moneda: no hay conversión
                celdaTarifa?.text = "1.00"
            } else {
                // Monedas distintas: traer el tipo de cambio oficial del BCRP
                actualizarTipoCambio()
            }
            if (esMonedaDePrecios) actualizarEncabezadosMoneda(simbolo)
            onDatosCambiados()
            true
        }
        popup.show()
    }

    /**
     * Consulta el tipo de cambio oficial del BCRP y llena la "tarifa" según las monedas elegidas.
     * Si no hay Internet, usa el último tipo de cambio guardado.
     */
    private fun actualizarTipoCambio() {
        val b = _binding ?: return
        val fila = b.tableMoneda.getChildAt(1) as? TableRow ?: return
        val celdas = celdasConTag(fila, TAG_VALOR).map { it as TextView }
        if (celdas.size < 3) return
        val desde = celdas[0].text.toString()
        val hacia = celdas[2].text.toString()

        if (desde == hacia) {
            celdas[1].text = "1.00"
            onDatosCambiados()
            Toast.makeText(requireContext(), "Misma moneda en precios y resultados: tarifa 1.00", Toast.LENGTH_SHORT).show()
            return
        }

        val servicio = TipoCambioService(requireContext())
        b.btnActualizarTipoCambio.isEnabled = false
        b.tvInfoTipoCambio.text = "Consultando BCRP…"
        viewLifecycleOwner.lifecycleScope.launch {
            val resultado = servicio.obtener(forzar = true)
            val tc = resultado.getOrNull() ?: servicio.guardado()
            if (_binding == null) return@launch
            binding.btnActualizarTipoCambio.isEnabled = true

            if (tc == null) {
                binding.tvInfoTipoCambio.text = "No se pudo consultar el BCRP"
                Toast.makeText(requireContext(), resultado.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_LONG).show()
                return@launch
            }
            val tarifa = tc.tarifa(desde, hacia)
            if (tarifa == null) {
                binding.tvInfoTipoCambio.text = "Elige S/, US$ o € en la tabla de moneda"
                return@launch
            }
            celdas[1].text = String.format(Locale.US, "%.4f", tarifa)
            onDatosCambiados()

            val origen = if (resultado.isSuccess) "BCRP" else "último guardado (sin conexión)"
            binding.tvInfoTipoCambio.text = String.format(
                Locale.US, "1 %s = %.4f %s · T.C. %s del %s", desde, tarifa, hacia, origen, tc.fechaPara(desde, hacia),
            )
        }
    }

    // ===================== CONVERTIR PRECIOS =====================

    /**
     * Pasa TODOS los precios escritos a otra moneda con el tipo de cambio del BCRP (p. ej. los precios que estaban
     * en soles a dólares). Cambiar solo el símbolo de "precios moneda" NO convierte los números.
     */
    private fun elegirMonedaParaConvertir() {
        val popup = PopupMenu(requireContext(), binding.btnConvertir)
        popup.menu.add(1, 100, 100, "Cargar precios de ejemplo (originales)")
        MONEDAS.forEachIndexed { i, (simbolo, nombre) -> popup.menu.add(0, i, i, "Convertir a $simbolo  $nombre") }
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == 100) { cargarPreciosDeEjemplo(); return@setOnMenuItemClickListener true }
            convertirPreciosA(MONEDAS[item.itemId].first)
            true
        }
        popup.show()
    }

    /** Reemplaza las tablas por los precios de ejemplo del programa original (en US$). */
    private fun cargarPreciosDeEjemplo() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("¿Cargar precios de ejemplo?")
            .setMessage("Se reemplazarán TODAS las tablas por los precios de ejemplo del programa original (sistemas, vidrios, umbrales, muebles, extras, trabajos, mosquiteras, laminación y colores), en US$. Tus precios actuales se perderán.")
            .setPositiveButton("CARGAR") { _, _ ->
                mostrarPrecios(Precios.porDefecto())
                onDatosCambiados()
                Toast.makeText(requireContext(), "Precios de ejemplo cargados", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("CANCELAR", null)
            .show()
    }

    private fun convertirPreciosA(destino: String) {
        val actual = recolectarPrecios()
        val origen = actual.moneda.firstOrNull()?.getOrNull(0)?.trim().orEmpty()
        if (origen == destino) {
            Toast.makeText(requireContext(), "Los precios ya están en $destino", Toast.LENGTH_SHORT).show()
            return
        }
        val servicio = TipoCambioService(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            val resultado = servicio.obtener(forzar = true)
            val tc = resultado.getOrNull() ?: servicio.guardado()
            if (_binding == null) return@launch
            val factor = tc?.tarifa(origen, destino)
            if (tc == null || factor == null) {
                Toast.makeText(requireContext(), "No hay tipo de cambio disponible (BCRP) para convertir", Toast.LENGTH_LONG).show()
                return@launch
            }
            val fecha = tc.fechaPara(origen, destino)
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("¿Convertir los precios a $destino?")
                .setMessage(
                    String.format(Locale.US, "Todos los precios de las tablas se multiplicarán por %.4f (1 %s = %.4f %s, T.C. BCRP del %s).\n\n" +
                        "Los porcentajes (planes, impuesto) no cambian. Esta acción modifica tus precios.", factor, origen, factor, destino, fecha),
                )
                .setPositiveButton("CONVERTIR") { _, _ ->
                    val convertido = actual.convertirPrecios(factor)
                    val resultados = actual.moneda.firstOrNull()?.getOrNull(2) ?: destino
                    val tarifa = tc.tarifa(destino, resultados) ?: 1.0
                    val moneda = listOf(listOf(destino, String.format(Locale.US, "%.4f", tarifa), resultados))
                    mostrarPrecios(convertido.copy(moneda = moneda))
                    onDatosCambiados()
                    Toast.makeText(requireContext(), "Precios convertidos a $destino", Toast.LENGTH_LONG).show()
                }
                .setNegativeButton("CANCELAR", null)
                .show()
        }
    }

    /** Al abrir la pantalla, muestra de qué fecha es el último tipo de cambio consultado. */
    private fun mostrarInfoTipoCambioGuardado() {
        val tc = TipoCambioService(requireContext()).guardado() ?: return
        binding.tvInfoTipoCambio.text = String.format(
            Locale.US, "Último T.C. BCRP: US$ %.3f · € %.3f (%s)", tc.usdPen, tc.eurPen, tc.fechaUsd,
        )
    }

    /** Cambia "S/ por m", "US$ por m2", "€ por la ps"... en los encabezados según la moneda de los precios. */
    private fun actualizarEncabezadosMoneda(simbolo: String?) {
        val b = _binding ?: return
        val nuevo = simbolo?.takeIf { it.isNotBlank() } ?: return
        val patron = Regex("""(S/|US\$|€|\$)(?=\s)""")
        fun recorrer(v: View) {
            when (v) {
                is TextView -> {
                    val t = v.text.toString()
                    if (patron.containsMatchIn(t)) v.text = patron.replace(t) { nuevo }
                }
                is ViewGroup -> for (i in 0 until v.childCount) recorrer(v.getChildAt(i))
            }
        }
        listOf(
            b.tableVentanas, b.tableCristal, b.tableUmbralExt, b.tableUmbralInt, b.tableMuebles,
            b.tableExtras, b.tableEmpleos, b.tableMosquitera, b.tableLaminacion,
        ).forEach { tabla -> if (tabla.childCount > 0) recorrer(tabla.getChildAt(0)) }
    }

    private fun showNumericKeypad(target: TextView) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_keypad_number, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val display = dialogView.findViewById<TextView>(R.id.tvKeypadValue)
        display.text = target.text

        val listener = View.OnClickListener { v ->
            if (v is Button) {
                val current = display.text.toString()
                if (v.text == "." && current.contains(".")) return@OnClickListener
                display.text = if (current == "0" && v.text != ".") v.text else current + v.text
            }
        }

        listOf(R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9, R.id.btnDot).forEach {
            dialogView.findViewById<Button>(it).setOnClickListener(listener)
        }

        dialogView.findViewById<View>(R.id.btnKeypadDelete).setOnClickListener {
            val s = display.text.toString()
            display.text = if (s.length > 1) s.dropLast(1) else "0"
        }
        dialogView.findViewById<View>(R.id.btnKeypadClear).setOnClickListener {
            display.text = "0"
        }
        dialogView.findViewById<View>(R.id.btnKeypadClose).setOnClickListener {
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.btnKeypadDone).setOnClickListener {
            target.text = formatDecimal(display.text.toString())
            onDatosCambiados()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showTextKeypad(target: TextView, isColorName: Boolean = false) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_keypad_text, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val input = dialogView.findViewById<EditText>(R.id.etKeypadValue)
        input.setText(target.text)
        input.setSelection(input.text.length)
        input.requestFocus()

        dialogView.findViewById<View>(R.id.btnKeypadDelete).setOnClickListener {
            val current = input.text.toString()
            if (current.isNotEmpty()) {
                input.setText(current.dropLast(1))
                input.setSelection(input.text.length)
            }
        }

        dialogView.findViewById<View>(R.id.btnKeypadClear).setOnClickListener {
            input.setText("")
        }

        dialogView.findViewById<View>(R.id.btnKeypadClose).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<View>(R.id.btnKeypadDone).setOnClickListener {
            val newName = input.text.toString().trim()
            if (isColorName) {
                if (newName.isBlank()) {
                    Toast.makeText(requireContext(), "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (isColorNameDuplicate(newName, target)) {
                    Toast.makeText(requireContext(), "Ya existe un color con el nombre '$newName'", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }
            target.text = newName
            onDatosCambiados()
            dialog.dismiss()
        }

        dialog.setOnShowListener {
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(input, 0)
        }

        dialog.show()
    }

    private fun showColorPicker(target: View) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        val grid = dialogView.findViewById<GridLayout>(R.id.gridColors)
        // Paleta de 49 colores del original (filas A..G, columnas 01..07), con sus valores exactos
        val colors = listOf(
            Color.parseColor("#f5f5f5"),
            Color.parseColor("#d0d1d5"),
            Color.parseColor("#afb1b1"),
            Color.parseColor("#999999"),
            Color.parseColor("#7e7d7d"),
            Color.parseColor("#5e5e5d"),
            Color.parseColor("#3e3e3e"),
            Color.parseColor("#fff1c9"),
            Color.parseColor("#ffeebd"),
            Color.parseColor("#ffe3a4"),
            Color.parseColor("#fed8ae"),
            Color.parseColor("#fccfae"),
            Color.parseColor("#ddcd99"),
            Color.parseColor("#daaf96"),
            Color.parseColor("#eeba8e"),
            Color.parseColor("#dca780"),
            Color.parseColor("#dca15f"),
            Color.parseColor("#bb9269"),
            Color.parseColor("#cf7749"),
            Color.parseColor("#af652a"),
            Color.parseColor("#785052"),
            Color.parseColor("#fbf08c"),
            Color.parseColor("#f6dd63"),
            Color.parseColor("#dfd581"),
            Color.parseColor("#cfae70"),
            Color.parseColor("#af8448"),
            Color.parseColor("#7c5d49"),
            Color.parseColor("#55513a"),
            Color.parseColor("#b5cd8e"),
            Color.parseColor("#addc62"),
            Color.parseColor("#79d37b"),
            Color.parseColor("#71a860"),
            Color.parseColor("#8b985c"),
            Color.parseColor("#727a45"),
            Color.parseColor("#487548"),
            Color.parseColor("#ed928a"),
            Color.parseColor("#f38172"),
            Color.parseColor("#dd7171"),
            Color.parseColor("#b8799c"),
            Color.parseColor("#b65f5f"),
            Color.parseColor("#855874"),
            Color.parseColor("#aa4141"),
            Color.parseColor("#81c5bf"),
            Color.parseColor("#5eb1ab"),
            Color.parseColor("#42a2ed"),
            Color.parseColor("#5c92af"),
            Color.parseColor("#338fb3"),
            Color.parseColor("#487fd3"),
            Color.parseColor("#436dae")
        )
        
        var selectedColor = (target.background as? ColorDrawable)?.color ?: Color.WHITE

        colors.forEach { color ->
            val v = View(requireContext()).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = dpToPx(40); height = dpToPx(40); setMargins(4, 4, 4, 4)
                }
                setBackgroundColor(color)
                setOnClickListener { selectedColor = color }
            }
            grid.addView(v)
        }

        dialogView.findViewById<View>(R.id.btnColorYes).setOnClickListener {
            target.setBackgroundColor(selectedColor)
            onDatosCambiados()
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.btnColorNo).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showDeleteConfirmation(parent: ViewGroup, row: View, itemName: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_delete, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogView.findViewById<TextView>(R.id.tvConfirmMessage).text = "¿Eliminar \"$itemName\" de la tabla?"
        dialogView.findViewById<View>(R.id.btnAccept).setOnClickListener {
            parent.removeView(row)
            onDatosCambiados()
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun formatDecimal(input: String): String {
        return try {
            val value = input.toDouble()
            String.format(Locale.US, "%.2f", value)
        } catch (e: Exception) { input }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** Monedas disponibles en el desplegable: símbolo y nombre. */
        private val MONEDAS = listOf("S/" to "Soles", "US$" to "Dólares", "€" to "Euros")

        private const val TAG_VALOR = "valor"
        private const val TAG_UNIDAD = "unidad"
        private const val TAG_COLOR = "color"
    }
}
