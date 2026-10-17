package com.example.vineuro_pvc

import com.example.vineuro_pvc.data.Unidades
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.vineuro_pvc.data.ListadoMateriales
import com.example.vineuro_pvc.data.OptimizadorCorte
import com.example.vineuro_pvc.data.Pedido
import com.example.vineuro_pvc.data.PedidoRepository
import com.example.vineuro_pvc.data.VineuroDatabase
import com.example.vineuro_pvc.databinding.FragmentGuiaCorteBinding
import com.example.vineuro_pvc.views.GuiaCortePdf
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Módulo 4 (RF10-RF12, VN-039): listado de materiales y guía de corte por varilla del pedido.
 * Los parámetros (largo de varilla, corte, soldadura, retazo útil) se guardan en el teléfono.
 */
class GuiaCorteFragment : Fragment() {

    private var _binding: FragmentGuiaCorteBinding? = null
    private val binding get() = _binding!!
    private lateinit var pedido: Pedido
    private lateinit var plan: OptimizadorCorte.Plan
    private lateinit var listado: ListadoMateriales.Listado

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGuiaCorteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        val p = arguments?.getString("pedido_id")?.let { PedidoRepository(ctx).obtenerPedido(it) }
        if (p == null || p.items.isEmpty()) {
            Toast.makeText(ctx, "Primero agrega ventanas al pedido", Toast.LENGTH_LONG).show()
            findNavController().navigateUp()
            return
        }
        pedido = p
        binding.btnGuiaVolver.setOnClickListener { findNavController().navigateUp() }
        binding.btnGuiaParametros.setOnClickListener { editarParametros() }
        binding.btnGuiaPdf.setOnClickListener { crearPdf() }
        calcularYMostrar()
    }

    private fun leerParametros(): OptimizadorCorte.Parametros {
        val json = VineuroDatabase.get(requireContext()).leerConfiguracion(CLAVE)
        return OptimizadorCorte.Parametros.fromJson(json?.let { runCatching { JSONObject(it) }.getOrNull() })
    }

    private fun calcularYMostrar() {
        plan = OptimizadorCorte.planificar(pedido.items, leerParametros())
        listado = ListadoMateriales.generar(pedido.items)
        pintar()
    }

    private fun editarParametros() {
        val ctx = requireContext()
        val p = plan.parametros
        fun campo(titulo: String, v: Int) = EditText(ctx).apply {
            hint = titulo; setText(v.toString()); inputType = InputType.TYPE_CLASS_NUMBER
        }
        val dp = ctx.resources.displayMetrics.density
        val eLargo = campo("Largo de la varilla (mm)", p.largoVarillaMm)
        val eCorte = campo("Material que se pierde por corte (mm)", p.separacionMm)
        val eSold = campo("Compensación de soldadura, marco y hoja (mm)", p.soldaduraMm)
        val eRetazo = campo("Retazo reutilizable desde (mm)", p.retazoUtilMm)
        val caja = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val m = (20 * dp).toInt(); setPadding(m, (8 * dp).toInt(), m, 0)
            addView(TextView(ctx).apply {
                text = "Confirma estos valores con tu taller: cambian la cantidad de varillas y el largo de cada pieza soldada."
                textSize = 12f
            })
            addView(eLargo); addView(eCorte); addView(eSold); addView(eRetazo)
        }
        MaterialAlertDialogBuilder(ctx).setTitle("Parámetros de corte").setView(ScrollView(ctx).apply { addView(caja) })
            .setPositiveButton("GUARDAR") { _, _ ->
                fun n(e: EditText, d: Int) = e.text.toString().toIntOrNull() ?: d
                val nuevo = OptimizadorCorte.Parametros(
                    largoVarillaMm = n(eLargo, p.largoVarillaMm).coerceAtLeast(500),
                    separacionMm = n(eCorte, p.separacionMm),
                    soldaduraMm = n(eSold, p.soldaduraMm),
                    retazoUtilMm = n(eRetazo, p.retazoUtilMm),
                )
                VineuroDatabase.get(ctx).guardarConfiguracion(CLAVE, nuevo.toJson().toString(), System.currentTimeMillis())
                calcularYMostrar()
            }
            .setNegativeButton("CANCELAR", null).show()
    }

    private fun crearPdf() {
        val ctx = requireContext()
        val archivo = try {
            val fecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            GuiaCortePdf.crear(ctx, pedido.codigo, pedido.clienteNombre, fecha, plan, listado)
        } catch (e: Exception) {
            Toast.makeText(ctx, "No se pudo crear el PDF: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }
        com.example.vineuro_pvc.views.PdfAcciones.mostrarDialogo(ctx, archivo, "Guía de corte ${pedido.codigo}")
    }

    // ===================== PANTALLA =====================

    private fun pintar() {
        val ctx = requireContext()
        val cont = binding.contenedorGuia
        cont.removeAllViews()
        val dp = ctx.resources.displayMetrics.density
        fun f(v: Double) = String.format(Locale.US, "%.1f", v)

        fun titulo(t: String) = TextView(ctx).apply {
            text = t; textSize = 15f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.parseColor("#1E3A5F"))
            setPadding(0, (14 * dp).toInt(), 0, (4 * dp).toInt())
        }
        fun linea(t: String, mono: Boolean = false, color: Int = Color.parseColor("#1F2937"), tam: Float = 13f) = TextView(ctx).apply {
            text = t; textSize = tam; setTextColor(color)
            if (mono) typeface = Typeface.MONOSPACE
            setPadding(0, (2 * dp).toInt(), 0, (2 * dp).toInt())
        }

        val p = plan.parametros
        cont.addView(linea("${pedido.clienteNombre} · ${pedido.codigo}", tam = 14f))
        cont.addView(linea("Varilla ${p.largoVarillaMm} mm · corte ${p.separacionMm} mm · soldadura +${p.soldaduraMm} mm · retazo útil ≥ ${p.retazoUtilMm} mm",
            color = Color.parseColor("#6B7280"), tam = 12f))
        cont.addView(titulo("Resumen"))
        cont.addView(linea("${plan.varillasTotal} varillas a comprar · aprovechamiento ${f(plan.aprovechamientoPct)} % · desperdicio ${f(plan.desperdicioPct)} %"))
        plan.grupos.filter { it.sinCabida.isNotEmpty() }.forEach { g ->
            cont.addView(linea("⚠ ${g.clave}: ${g.sinCabida.size} pieza(s) más largas que la varilla (hasta ${g.sinCabida.maxOf { it.largoMm }} mm). Revisa medidas.",
                color = Color.parseColor("#C00000")))
        }
        cont.addView(titulo("Perfiles"))
        for (g in plan.grupos) {
            cont.addView(linea("${g.clave}", color = Color.parseColor("#1E3A5F")).apply { setTypeface(typeface, Typeface.BOLD) })
            cont.addView(linea("${g.varillas.size} varillas (mín. teórico ${g.minimoTeorico}) · ${g.piezasTotal} piezas · aprov. ${f(g.aprovechamientoPct)} % · desperd. ${f(g.desperdicioPct)} %",
                color = Color.parseColor("#6B7280"), tam = 12f))
            g.varillas.forEachIndexed { i, v ->
                val texto = v.piezas.joinToString(" + ") { "${it.largoMm}" } + "  → sobra ${v.sobranteMm}" +
                    if (v.sobranteMm >= g.retazoUtilMm) " (retazo)" else ""
                cont.addView(linea("${(i + 1).toString().padStart(2)}) $texto", mono = true, tam = 11f))
                val detalle = v.piezas.joinToString(", ") { "V${it.ventana}${if (it.copia > 1) "·${it.copia}" else ""} ${it.etiqueta}" }
                cont.addView(linea("     $detalle", mono = true, tam = 9f, color = Color.parseColor("#6B7280")))
            }
        }
        cont.addView(titulo("Otros materiales"))
        cont.addView(linea("${listado.ventanas} ventanas · ${listado.unidades} unidades · ${Unidades.areaM2(listado.areaM2)}"))
        cont.addView(linea("Junta de caucho / sellador: ${Unidades.largoM(listado.cauchoM)}"))
        if (listado.mosquiteros > 0) cont.addView(linea("Paños con mosquitero: ${listado.mosquiteros}"))
        listado.hojasPorApertura.forEach { (k, v) -> cont.addView(linea("Hojas $k: $v")) }
        cont.addView(titulo("Vidrios y paneles (mm)"))
        listado.vidrios.forEach {
            cont.addView(linea("${it.cantidad} × ${it.anchoMm} × ${it.altoMm}  ·  ${it.tipo}", mono = true, tam = 12f))
        }
        cont.addView(linea("Vidrio ${Unidades.areaM2(listado.vidrioM2)} · Paneles sándwich ${Unidades.areaM2(listado.sandwichM2)}").apply { setTypeface(typeface, Typeface.BOLD) })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val CLAVE = "parametros_corte"
    }
}
