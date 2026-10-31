package com.example.vineuro_pvc

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.vineuro_pvc.data.IndicadoresBi
import com.example.vineuro_pvc.data.IndicadoresBi.Modelo
import com.example.vineuro_pvc.data.OptimizadorCorte
import com.example.vineuro_pvc.data.PedidoRepository
import com.example.vineuro_pvc.data.TerminosRepository
import com.example.vineuro_pvc.data.VineuroDatabase
import com.example.vineuro_pvc.databinding.FragmentBiBinding
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

/**
 * Tablero de indicadores (BI) – RF13 y RF14 (SCC-002).
 * Muestra TPC, NEC y PDM de las cotizaciones guardadas (SQLite, sincronizadas con Supabase),
 * filtrados por periodo y por modelo de ventana.
 */
class BiFragment : Fragment() {

    private var _binding: FragmentBiBinding? = null
    private val binding get() = _binding!!

    private enum class Periodo(val texto: String) {
        TODOS("Todo"), SIETE_DIAS("7 días"), TREINTA_DIAS("30 días"), MES("Este mes"), RANGO("Rango…")
    }

    private var periodo = Periodo.TODOS
    private var rango: Pair<Long, Long>? = null
    private var modelo: Modelo? = null
    private var datos: List<IndicadoresBi.Cotizacion> = emptyList()
    private var calculo: Job? = null
    private var ultimo: IndicadoresBi.Resultado? = null
    /** Planes de corte ya calculados (se descartan cuando se recargan los datos). */
    private var planes = ConcurrentHashMap<String, OptimizadorCorte.Plan>()
    /** Evita que un cambio de chip hecho por código vuelva a disparar el filtro. */
    private var ignorarCambios = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarChips()
        binding.cardNec.setOnClickListener { mostrarIncompletas() }
        // 1) Con lo que ya hay en el teléfono (inmediato); 2) se sincroniza con la nube y se vuelve a calcular
        viewLifecycleOwner.lifecycleScope.launch {
            datos = cargarDatos()
            if (_binding == null) return@launch
            recalcular()
            sincronizar()
        }
    }

    // ---------- Datos ----------

    private suspend fun cargarDatos(): List<IndicadoresBi.Cotizacion> {
        val ctx = context?.applicationContext ?: return emptyList()
        return withContext(Dispatchers.IO) {
            val terminos = TerminosRepository(ctx)
            PedidoRepository(ctx).obtenerPedidos().map { IndicadoresBi.Cotizacion(it, terminos.cotizacion(it.id), terminos.tiempoActivo(it.id)) }
        }
    }

    private suspend fun sincronizar() {
        val ctx = context?.applicationContext ?: return
        binding.tvBiEstado.text = getString(R.string.bi_sincronizando)
        runCatching {
            val pedidos = PedidoRepository(ctx).sincronizar()
            TerminosRepository(ctx).sincronizarTodo(pedidos.map { it.id })
        }
        if (_binding == null) return
        datos = cargarDatos()
        planes = ConcurrentHashMap()
        if (_binding == null) return
        binding.tvBiEstado.text = getString(R.string.bi_actualizado, SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
        recalcular()
    }

    // ---------- Filtros ----------

    private fun configurarChips() {
        val gp = binding.chipsPeriodo
        Periodo.values().forEach { gp.addView(nuevoChip(gp, it.texto, it)) }
        gp.check(gp.getChildAt(0).id)
        gp.setOnCheckedStateChangeListener { g, ids ->
            if (ignorarCambios) return@setOnCheckedStateChangeListener
            val p = ids.firstOrNull()?.let { g.findViewById<View>(it)?.tag } as? Periodo ?: return@setOnCheckedStateChangeListener
            if (p == Periodo.RANGO) elegirRango() else { periodo = p; recalcular() }
        }

        val gm = binding.chipsModelo
        gm.addView(nuevoChip(gm, "Todos", "todos"))
        Modelo.values().forEach { gm.addView(nuevoChip(gm, it.etiqueta, it)) }
        gm.check(gm.getChildAt(0).id)
        gm.setOnCheckedStateChangeListener { g, ids ->
            val tag = ids.firstOrNull()?.let { g.findViewById<View>(it)?.tag } ?: return@setOnCheckedStateChangeListener
            modelo = tag as? Modelo
            recalcular()
        }
    }

    private fun nuevoChip(grupo: ChipGroup, texto: String, tag: Any): Chip =
        (layoutInflater.inflate(R.layout.item_chip_bi, grupo, false) as Chip).apply {
            id = View.generateViewId()
            text = texto
            this.tag = tag
        }

    private fun elegirRango() {
        val picker = MaterialDatePicker.Builder.dateRangePicker().setTitleText("Rango de fechas").build()
        picker.addOnPositiveButtonClickListener { sel ->
            val ini = sel.first ?: return@addOnPositiveButtonClickListener
            val fin = sel.second ?: ini
            rango = diaLocal(ini, false) to diaLocal(fin, true)
            periodo = Periodo.RANGO
            val fmt = SimpleDateFormat("dd/MM", Locale.getDefault())
            val b = _binding ?: return@addOnPositiveButtonClickListener
            (b.chipsPeriodo.getChildAt(Periodo.RANGO.ordinal) as Chip).text = "${fmt.format(Date(rango!!.first))} – ${fmt.format(Date(rango!!.second))}"
            recalcular()
        }
        picker.addOnDismissListener {
            // Si se cerró sin elegir fechas, vuelve a marcarse el periodo anterior
            val b = _binding ?: return@addOnDismissListener
            if (periodo == Periodo.RANGO && rango != null) return@addOnDismissListener
            ignorarCambios = true
            b.chipsPeriodo.check(b.chipsPeriodo.getChildAt(periodo.ordinal).id)
            ignorarCambios = false
        }
        picker.show(childFragmentManager, "rango_bi")
    }

    /** El selector de fechas devuelve medianoche UTC: se pasa al mismo día en la hora local (inicio o fin del día). */
    private fun diaLocal(utcMillis: Long, finDelDia: Boolean): Long {
        val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
        return Calendar.getInstance().apply {
            clear()
            if (finDelDia) set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 23, 59, 59)
            else set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
            set(Calendar.MILLISECOND, if (finDelDia) 999 else 0)
        }.timeInMillis
    }

    private fun limites(): Pair<Long?, Long?> {
        val dia = 24L * 60 * 60 * 1000
        val ahora = System.currentTimeMillis()
        return when (periodo) {
            Periodo.TODOS -> null to null
            Periodo.SIETE_DIAS -> (ahora - 7 * dia) to null
            Periodo.TREINTA_DIAS -> (ahora - 30 * dia) to null
            Periodo.MES -> Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis to null
            Periodo.RANGO -> rango?.let { it.first to it.second } ?: (null to null)
        }
    }

    // ---------- Cálculo y pantalla ----------

    private fun parametrosCorte(): OptimizadorCorte.Parametros {
        val json = VineuroDatabase.get(requireContext()).leerConfiguracion(CLAVE_PARAMETROS_CORTE)
        return OptimizadorCorte.Parametros.fromJson(json?.let { runCatching { JSONObject(it) }.getOrNull() })
    }

    private fun recalcular() {
        val (desde, hasta) = limites()
        val m = modelo
        val params = parametrosCorte()
        val lista = datos
        val cache = planes
        calculo?.cancel()
        calculo = viewLifecycleOwner.lifecycleScope.launch {
            val salida = withContext(Dispatchers.Default) {
                val res = IndicadoresBi.calcular(lista, desde, hasta, m, params, cache)
                val filas = IndicadoresBi.porModelo(lista, desde, hasta, params, cache)
                val meses = IndicadoresBi.porMes(lista, desde, hasta, m, params, cache)
                Triple(res, filas, meses)
            }
            if (_binding == null) return@launch
            pintar(salida.first, salida.second, salida.third)
        }
    }

    private fun pintar(r: IndicadoresBi.Resultado, filas: List<IndicadoresBi.FilaModelo>, meses: List<IndicadoresBi.FilaMes>) {
        val b = binding
        val us = Locale.US
        ultimo = r
        b.tvBiTotal.text = if (r.cotizaciones == 1) "1 cotización · ${r.conOferta} con oferta comercial"
        else "${r.cotizaciones} cotizaciones · ${r.conOferta} con oferta comercial"

        b.tvBiLectura.text = IndicadoresBi.lecturas(r, filas, meses).joinToString("\n\n") { "• $it" }

        b.tvTpcValor.text = r.tpcMin?.let { String.format(us, "%.1f min", it) } ?: "—"
        b.tvTpcDetalle.text = buildString {
            if (r.conTiempo > 0) append("Trabajo real promedio de ${r.conTiempo} cotización${if (r.conTiempo == 1) "" else "es"}, sin contar pausas")
            else append("Aún sin tiempos: se miden desde las cotizaciones nuevas")
            if (r.sinTiempo > 0) append("\n${r.sinTiempo} sin tiempo medido (hechas antes del cronómetro)")
        }

        b.tvNecValor.text = r.nec?.let { String.format(us, "%.2f", it) } ?: "—"
        b.tvNecDetalle.text = if (r.conOferta > 0) {
            "${r.completas} de ${r.conOferta} ofertas con condiciones y Anexo A completos" +
                if (r.incompletas.isNotEmpty()) "\nToca aquí para ver cuáles no cumplen y por qué" else ""
        } else "Aún no hay ofertas comerciales"

        b.tvPdmValor.text = r.pdmPct?.let { String.format(us, "%.1f %%", it) } ?: "—"
        b.tvPdmDetalle.text = if (r.compradoMm > 0) {
            String.format(us, "Desperdicio de %.1f m sobre %.1f m de perfil comprado, sin contar retazos reutilizables", r.desperdicioMm / 1000.0, r.compradoMm / 1000.0) +
                String.format(us, "\nContando los retazos como pérdida: %.1f %% · Retazos reutilizables: %.1f m", r.pdmConRetazosPct ?: 0.0, r.retazosMm / 1000.0)
        } else "Aún no hay ventanas con guía de corte"

        pintarTabla(filas)
        pintarMeses(meses)
    }

    private fun mostrarIncompletas() {
        val lista = ultimo?.incompletas ?: return
        val texto = if (lista.isEmpty()) "Todas las ofertas evaluadas tienen las condiciones generales y el Anexo A completos."
        else lista.joinToString("\n\n") { o ->
            "${o.codigo} · ${o.cliente.ifBlank { "Sin nombre" }}\n" + o.motivos.joinToString("\n") { "• $it" }
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Ofertas que no cumplen")
            .setMessage(texto)
            .setPositiveButton("CERRAR", null)
            .show()
    }

    private fun pintarMeses(meses: List<IndicadoresBi.FilaMes>) {
        val tabla = binding.tablaMeses
        tabla.removeAllViews()
        if (meses.isEmpty()) {
            tabla.addView(celda("Sin cotizaciones en este periodo", 1f, false))
            return
        }
        val us = Locale.US
        tabla.addView(fila(listOf("Mes", "Cot.", "TPC (min)", "NEC", "PDM"), true))
        meses.forEach { f ->
            val r = f.resultado
            tabla.addView(
                fila(
                    listOf(
                        f.etiqueta,
                        r.cotizaciones.toString(),
                        r.tpcMin?.let { String.format(us, "%.1f", it) } ?: "—",
                        r.nec?.let { String.format(us, "%.2f", it) } ?: "—",
                        r.pdmPct?.let { String.format(us, "%.1f %%", it) } ?: "—",
                    ),
                    false,
                ),
            )
        }
    }

    private fun pintarTabla(filas: List<IndicadoresBi.FilaModelo>) {
        val tabla = binding.tablaModelos
        tabla.removeAllViews()
        if (filas.isEmpty()) {
            tabla.addView(celda("Sin cotizaciones en este periodo", 1f, false))
            return
        }
        val us = Locale.US
        tabla.addView(fila(listOf("Modelo", "Cot.", "TPC (min)", "NEC", "PDM"), true))
        filas.forEach { f ->
            val r = f.resultado
            tabla.addView(
                fila(
                    listOf(
                        f.modelo.etiqueta,
                        r.cotizaciones.toString(),
                        r.tpcMin?.let { String.format(us, "%.1f", it) } ?: "—",
                        r.nec?.let { String.format(us, "%.2f", it) } ?: "—",
                        r.pdmPct?.let { String.format(us, "%.1f %%", it) } ?: "—",
                    ),
                    false,
                ),
            )
        }
    }

    private fun fila(textos: List<String>, cabecera: Boolean): LinearLayout =
        LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            val v = (6 * resources.displayMetrics.density).toInt()
            setPadding(0, v, 0, v)
            textos.forEachIndexed { i, t -> addView(celda(t, if (i == 0) 1.6f else 1f, cabecera)) }
        }

    private fun celda(texto: String, peso: Float, negrita: Boolean): TextView =
        TextView(requireContext()).apply {
            text = texto
            textSize = 13f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.menu_tile_text))
            if (negrita) setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, peso)
        }

    override fun onDestroyView() {
        super.onDestroyView()
        calculo?.cancel()
        _binding = null
    }

    private companion object {
        /** Misma clave con la que la guía de corte guarda sus parámetros (GuiaCorteFragment). */
        const val CLAVE_PARAMETROS_CORTE = "parametros_corte"
    }
}
