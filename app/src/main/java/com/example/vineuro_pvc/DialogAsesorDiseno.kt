package com.example.vineuro_pvc

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.vineuro_pvc.data.AsesorDiseno
import com.example.vineuro_pvc.data.CalculadoraPrecios
import com.example.vineuro_pvc.data.GeminiClient
import com.example.vineuro_pvc.data.GeminiConfig
import com.example.vineuro_pvc.data.GeminiAsesor
import com.example.vineuro_pvc.data.ResultadoIA
import com.example.vineuro_pvc.data.ModelosVentana
import com.example.vineuro_pvc.data.Precios
import com.example.vineuro_pvc.data.PreciosRepository
import com.example.vineuro_pvc.data.SugerenciaDiseno
import com.example.vineuro_pvc.databinding.DialogAsesorDisenoBinding
import com.example.vineuro_pvc.databinding.DialogAsesorPropuestaBinding
import com.example.vineuro_pvc.views.WindowPreviewView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

/** Crea un diálogo con el mismo formato que el resto de la app (tarjeta redondeada sobre fondo transparente). */
internal fun crearDialogoApp(fragment: Fragment, raiz: View, altoCompleto: Boolean): Dialog =
    Dialog(fragment.requireContext()).apply {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(raiz)
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val dm = context.resources.displayMetrics
            setLayout(
                (dm.widthPixels * 0.96f).toInt(),
                if (altoCompleto) (dm.heightPixels * 0.92f).toInt() else ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

/** Rellena un ChipGroup de selección única con las etiquetas dadas y devuelve los ids en el mismo orden. */
internal fun llenarChipsSeleccion(grupo: ChipGroup, etiquetas: List<String>, seleccionado: Int): List<Int> {
    grupo.removeAllViews()
    val ids = etiquetas.mapIndexed { i, etiqueta ->
        val chip = Chip(grupo.context).apply {
            id = View.generateViewId()
            text = etiqueta
            isCheckable = true
            isChecked = i == seleccionado
            setEnsureMinTouchTargetSize(false)
        }
        grupo.addView(chip)
        chip.id
    }
    return ids
}

/**
 * Asesor de diseño: el vendedor responde unas preguntas, la app propone una ventana elegida solo de su catálogo y
 * de su tabla de Precios, la muestra dibujada con su precio estimado y él decide: aceptar, ajustar las respuestas
 * o descartar. No se envía ningún dato del cliente: solo el ambiente, las medidas y la prioridad.
 */
class DialogAsesorDiseno(
    private val fragment: Fragment,
    private val alAceptar: (AsesorDiseno.Propuesta) -> Unit,
) {
    private val ctx get() = fragment.requireContext()
    private val dp get() = ctx.resources.displayMetrics.density

    fun mostrar(previa: AsesorDiseno.Necesidad? = null) = cuestionario(previa)

    // ===================== 1. CUESTIONARIO =====================

    private fun cuestionario(previa: AsesorDiseno.Necesidad?) {
        val b = DialogAsesorDisenoBinding.inflate(LayoutInflater.from(ctx))
        val ambientes = AsesorDiseno.Ambiente.values()
        val prioridades = AsesorDiseno.Prioridad.values()
        val idsAmbiente = llenarChipsSeleccion(b.chipsAmbiente, ambientes.map { it.etiqueta }, previa?.ambiente?.ordinal ?: 0)
        val idsPrioridad = llenarChipsSeleccion(
            b.chipsPrioridad, prioridades.map { it.etiqueta },
            previa?.prioridad?.ordinal ?: prioridades.indexOf(AsesorDiseno.Prioridad.EQUILIBRADO),
        )
        b.cbDaALaCalle.isChecked = previa?.daALaCalle ?: false
        previa?.let {
            b.etAnchoVano.setText(it.anchoMm.toString()); b.etAltoVano.setText(it.altoMm.toString())
            b.etComentario.setText(it.comentario)
            it.presupuesto?.let { p -> b.etPresupuesto.setText(String.format(Locale.US, "%.2f", p)) }
        }

        fun ambienteActual() = ambientes[idsAmbiente.indexOf(b.chipsAmbiente.checkedChipId).coerceAtLeast(0)]
        fun prioridadActual() = prioridades[idsPrioridad.indexOf(b.chipsPrioridad.checkedChipId).coerceAtLeast(0)]
        fun actualizarHabituales() {
            val t = AsesorDiseno.tipicas(ambienteActual())
            b.tvMedidasHabituales.text = "Habitual en ${ambienteActual().etiqueta.lowercase().substringBefore(" /")}: " +
                "${t.anchoMin}–${t.anchoMax} × ${t.altoMin}–${t.altoMax} mm"
        }
        actualizarHabituales()
        b.chipsAmbiente.setOnCheckedStateChangeListener { _, _ -> actualizarHabituales() }

        val dialog = crearDialogoApp(fragment, b.root, altoCompleto = false)
        b.btnCerrarAsesor.setOnClickListener { dialog.dismiss() }
        b.btnCancelarAsesor.setOnClickListener { dialog.dismiss() }
        b.btnProponer.setOnClickListener {
            val ancho = b.etAnchoVano.text.toString().toIntOrNull() ?: 0
            val alto = b.etAltoVano.text.toString().toIntOrNull() ?: 0
            val error = AsesorDiseno.errorMedidas(ancho, alto)
            if (error != null) {
                Toast.makeText(ctx, error, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val necesidad = AsesorDiseno.Necesidad(
                ambienteActual(), b.cbDaALaCalle.isChecked, ancho, alto, prioridadActual(),
                comentario = AsesorDiseno.limpiarComentario(b.etComentario.text.toString()),
                presupuesto = b.etPresupuesto.text.toString().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 },
            )
            dialog.dismiss()
            confirmarMedidas(necesidad)
        }
        dialog.show()
    }

    /** Si las medidas son raras para el ambiente, lo avisa y deja corregir, usar las habituales o seguir igual. */
    private fun confirmarMedidas(n: AsesorDiseno.Necesidad) {
        val aviso = AsesorDiseno.advertenciaMedidas(n)
        if (aviso == null) { consultar(n); return }
        val t = AsesorDiseno.tipicas(n.ambiente)
        MaterialAlertDialogBuilder(ctx)
            .setTitle("¿Medidas correctas?")
            .setMessage("$aviso\n\n¿Qué prefieres?")
            .setPositiveButton("USAR ${t.sugAncho} × ${t.sugAlto}") { _, _ -> consultar(n.copy(anchoMm = t.sugAncho, altoMm = t.sugAlto)) }
            .setNeutralButton("CORREGIR") { _, _ -> cuestionario(n) }
            .setNegativeButton("SEGUIR ASÍ") { _, _ -> consultar(n) }
            .show()
    }

    // ===================== 2. CONSULTA (IA o reglas) =====================

    private fun consultar(necesidad: AsesorDiseno.Necesidad) {
        val precios = PreciosRepository(ctx).obtener()
        val conIa = GeminiConfig.clave(ctx).isNotBlank()
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val espera = if (conIa) mostrarEspera() else null
            // Si la pantalla se cierra mientras se espera, el aviso de espera no debe quedarse abierto
            val respuesta = try { GeminiClient.proponer(ctx, necesidad, precios) } finally { espera?.dismiss() }
            if (!fragment.isAdded) return@launch
            when (respuesta) {
                is ResultadoIA.Ok -> resultado(necesidad, precios, AsesorDiseno.conPresupuesto(respuesta.propuestas, necesidad, precios), null)
                is ResultadoIA.Error -> {
                    val reglas = AsesorDiseno.conPresupuesto(AsesorDiseno.proponerAlternativas(necesidad, precios), necesidad, precios)
                    if (reglas.isEmpty()) Toast.makeText(ctx, "No se pudo armar una propuesta con esas medidas", Toast.LENGTH_LONG).show()
                    else resultado(necesidad, precios, reglas, respuesta.fallo)
                }
            }
        }
    }

    private fun mostrarEspera(): android.app.Dialog {
        val fila = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
            val m = (24 * dp).toInt(); setPadding(m, m, m, m)
            addView(android.widget.ProgressBar(ctx))
            addView(TextView(ctx).apply { text = "Consultando a la IA…"; textSize = 15f; setPadding((16 * dp).toInt(), 0, 0, 0) })
        }
        return MaterialAlertDialogBuilder(ctx).setView(fila).setCancelable(false).show()
    }

    // ===================== 3. PROPUESTA =====================

    private fun resultado(necesidad: AsesorDiseno.Necesidad, precios: Precios, inicial: List<AsesorDiseno.Propuesta>, fallo: GeminiAsesor.Fallo?) {
        val b = DialogAsesorPropuestaBinding.inflate(LayoutInflater.from(ctx))
        val propuestas = inicial.toMutableList()
        val simbolo = precios.moneda.firstOrNull()?.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "US$"
        var actual = propuestas.indexOfFirst { it.etiqueta == "equilibrada" }.coerceAtLeast(0)
        var ids = emptyList<Int>()

        fun dinero(v: Double) = String.format(Locale.US, "%.2f", v) + " $simbolo"
        fun fila(titulo: String, valor: String) {
            val f = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, (3 * dp).toInt(), 0, (3 * dp).toInt())
            }
            f.addView(TextView(ctx).apply {
                text = titulo; textSize = 13f; setTextColor(Color.parseColor("#6B7280"))
                layoutParams = LinearLayout.LayoutParams((96 * dp).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            f.addView(TextView(ctx).apply {
                text = valor; textSize = 13f; setTextColor(Color.parseColor("#1F2937"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            b.contenedorDetallePropuesta.addView(f)
        }
        fun etiquetaChip(p: AsesorDiseno.Propuesta) = AsesorDiseno.nombreEtiqueta(p.etiqueta) + " · " + dinero(AsesorDiseno.precioDe(p, precios))

        fun llenarChips() {
            if (propuestas.size < 2) { b.chipsAlternativas.visibility = View.GONE; return }
            ids = llenarChipsSeleccion(b.chipsAlternativas, propuestas.map { etiquetaChip(it) }, actual)
        }

        /** Qué cambia frente a la recomendada: el dibujo solo muestra aperturas, así que se dice con palabras. */
        fun diferencias(): String? {
            val rec = propuestas.firstOrNull { it.etiqueta == "equilibrada" }
            val p = propuestas[actual]
            if (rec == null || p === rec || propuestas.size < 2) return null
            val cambios = listOfNotNull(
                if (p.modeloId != rec.modeloId || p.aperturas != rec.aperturas) "aperturas" else null,
                if (p.cristal != rec.cristal) "cristal" else null,
                if (p.sistema != rec.sistema) "sistema" else null,
                if (p.mosquitero != rec.mosquitero) "mosquitero" else null,
            )
            return if (cambios.isEmpty()) null else "Frente a la recomendada cambia: ${cambios.joinToString(", ")}."
        }

        fun mostrar() {
            val propuesta = propuestas[actual]
            val item = AsesorDiseno.aOrderItem(propuesta, 1)
            val calculo = CalculadoraPrecios.calcular(item, precios)
            val modelo = ModelosVentana.buscar(propuesta.modeloId)?.let { ModelosVentana.nombre(it) } ?: propuesta.modeloId
            val vista = WindowPreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setOnTouchListener { _, _ -> true } // solo para mirar
                setConfig(item.config, isTechnicalMode = false, resetMechanisms = false)
                sectionHeights = item.sectionHeights.clone()
                sectionWidths = item.sectionWidths.clone()
                paneMechanisms.clear(); paneMechanisms.putAll(item.paneMechanisms)
                paneMosqNet.clear(); paneMosqNet.putAll(item.paneMosqNet)
                updateDimensions(item.altoTotalMm, item.anchoTotalMm)
            }
            b.contenedorVistaPropuesta.removeAllViews()
            b.contenedorVistaPropuesta.addView(vista)
            b.tvModeloPropuesta.text = "$modelo · ${propuesta.anchoMm} × ${propuesta.altoMm} mm"
            b.tvOrigenPropuesta.text = (if (propuesta.origen == "ia") "Propuesta de la IA" else "Propuesta por reglas del catálogo") +
                " · " + AsesorDiseno.nombreEtiqueta(propuesta.etiqueta)

            b.contenedorDetallePropuesta.removeAllViews()
            fila("Aperturas", (0 until item.sectionWidths.size).joinToString(" · ") { i ->
                "${i + 1}) ${AsesorDiseno.nombreApertura(propuesta.aperturas[i] ?: "10")}"
            })
            propuesta.sistema?.let { fila("Sistema", it) }
            propuesta.cristal?.let { fila("Cristal", it) }
            propuesta.mosquitero?.let { fila("Mosquitero", it) }

            b.tvPrecioPropuesta.text = dinero(calculo.totalVenta)
            val avisos = mutableListOf<String>()
            if (fallo != null && fallo.tipo != GeminiAsesor.TipoFallo.SIN_CLAVE) {
                avisos += "IA no disponible: ${fallo.mensaje} Se usaron las reglas del catálogo."
            }
            avisos += propuesta.advertencias
            b.cajaAdvertencias.visibility = if (avisos.isEmpty()) View.GONE else View.VISIBLE
            b.tvAdvertencias.text = avisos.joinToString("\n") { "⚠ $it" }
            b.tvRazonesPropuesta.text = propuesta.razones.joinToString("\n") { "• $it" }
            b.tvTextoCliente.text = propuesta.textoCliente
            b.tvDiferencias.text = diferencias().orEmpty()
            b.tvDiferencias.visibility = if (b.tvDiferencias.text.isEmpty()) View.GONE else View.VISIBLE
        }

        llenarChips()
        mostrar()
        b.chipsAlternativas.setOnCheckedStateChangeListener { _, _ ->
            val i = ids.indexOf(b.chipsAlternativas.checkedChipId)
            if (i >= 0 && i != actual) { actual = i; mostrar() }
        }

        val dialog = crearDialogoApp(fragment, b.root, altoCompleto = true)
        b.btnCerrarPropuesta.setOnClickListener { dialog.dismiss() }
        b.btnDescartarPropuesta.setOnClickListener { dialog.dismiss() }
        b.btnCambiarRespuestas.setOnClickListener { dialog.dismiss(); cuestionario(necesidad) }
        b.btnCopiarTexto.setOnClickListener {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("Texto para el cliente", b.tvTextoCliente.text))
            Toast.makeText(ctx, "Texto copiado", Toast.LENGTH_SHORT).show()
        }
        b.btnAjustar.setOnClickListener {
            val frase = b.etAjuste.text.toString().trim()
            if (frase.isBlank()) { Toast.makeText(ctx, "Escribe qué quieres cambiar", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            b.btnAjustar.isEnabled = false
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                val espera = mostrarEspera()
                val r = try { GeminiClient.ajustar(ctx, necesidad, precios, propuestas[actual], frase) } finally { espera.dismiss() }
                if (!fragment.isAdded) return@launch
                b.btnAjustar.isEnabled = true
                when (r) {
                    is ResultadoIA.Ok -> {
                        val nueva = AsesorDiseno.conPresupuesto(r.propuestas.take(1), necesidad, precios).first()
                        propuestas[actual] = nueva
                        b.etAjuste.setText("")
                        llenarChips(); mostrar()
                    }
                    is ResultadoIA.Error -> Toast.makeText(ctx, "No se pudo ajustar: ${r.fallo.mensaje}", Toast.LENGTH_LONG).show()
                }
            }
        }
        b.btnAceptarPropuesta.setOnClickListener {
            val elegida = propuestas[actual]
            SugerenciaDiseno.pendiente = elegida
            dialog.dismiss()
            alAceptar(elegida)
        }
        dialog.show()
    }
}
