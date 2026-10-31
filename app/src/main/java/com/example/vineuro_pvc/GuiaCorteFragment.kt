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
import com.example.vineuro_pvc.views.BarraVarillaView
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
        val gris = Color.parseColor("#6B7280")
        val azul = Color.parseColor("#1E3A5F")

        fun texto(t: CharSequence, tam: Float = 13f, color: Int = Color.parseColor("#1F2937"), negrita: Boolean = false, mono: Boolean = false) =
            TextView(ctx).apply {
                text = t; textSize = tam; setTextColor(color)
                if (negrita) setTypeface(typeface, Typeface.BOLD)
                if (mono) typeface = Typeface.MONOSPACE
            }
        fun tarjeta(): LinearLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val m = (12 * dp).toInt(); setPadding(m, m, m, m)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.WHITE); cornerRadius = 10 * dp; setStroke((1 * dp).toInt(), Color.parseColor("#E5E7EB"))
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = (10 * dp).toInt() }
        }
        fun seccion(t: String) = texto(t, 14f, azul, negrita = true).apply { setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt()) }

        val p = plan.parametros
        cont.addView(texto("${pedido.clienteNombre} · ${pedido.codigo}", 13f, gris).apply { setPadding(0, 0, 0, (8 * dp).toInt()) })

        // ---- 1. Qué comprar ----
        val resumen = tarjeta()
        resumen.addView(texto("${plan.varillasTotal} varillas a comprar", 22f, azul, negrita = true))
        resumen.addView(texto("de ${p.largoVarillaMm} mm · aprovechamiento ${f(plan.aprovechamientoPct)} % · desperdicio ${f(plan.desperdicioPct)} %", 12f, gris))
        plan.grupos.filter { it.sinCabida.isNotEmpty() }.forEach { g ->
            resumen.addView(texto("⚠ ${g.clave}: ${g.sinCabida.size} pieza(s) más largas que la varilla (hasta ${g.sinCabida.maxOf { it.largoMm }} mm). Revisa las medidas.",
                12f, Color.parseColor("#C00000"), negrita = true).apply { setPadding(0, (6 * dp).toInt(), 0, 0) })
        }
        cont.addView(resumen)

        cont.addView(seccion("Lista de compra"))
        val compra = tarjeta()
        for (g in plan.grupos) {
            val fila = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, (3 * dp).toInt(), 0, (3 * dp).toInt()) }
            fila.addView(texto(g.clave, 12f).apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
            fila.addView(texto("${g.varillas.size}", 14f, azul, negrita = true))
            compra.addView(fila)
        }
        cont.addView(compra)

        // ---- 2. Cómo cortar ----
        cont.addView(seccion("Cómo cortar cada perfil"))
        val ventanas = plan.grupos.flatMap { g -> g.varillas.flatMap { v -> v.piezas.map { it.ventana } } }.distinct().sorted()
        val leyenda = android.text.SpannableStringBuilder("Color = ventana:  ")
        for (n in ventanas) {
            val ini = leyenda.length
            leyenda.append(" V$n ")
            leyenda.setSpan(android.text.style.BackgroundColorSpan(BarraVarillaView.colorVentana(n)), ini, leyenda.length, 0)
            leyenda.append("  ")
        }
        cont.addView(texto(leyenda, 12f, gris).apply { setPadding(0, 0, 0, (8 * dp).toInt()) })
        cont.addView(texto("Toca un perfil para ver sus varillas. Las medidas están en mm; el sobrante en verde es un retazo reutilizable (≥ ${p.retazoUtilMm} mm).", 11f, gris)
            .apply { setPadding(0, 0, 0, (8 * dp).toInt()) })

        for (g in plan.grupos) {
            val card = tarjeta()
            val cuerpo = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
            val cab = texto("▸  ${g.perfil} · ${g.sistema} · ${g.color}", 14f, azul, negrita = true)
            card.addView(cab)
            card.addView(texto("${g.varillas.size} varillas · ${g.piezasTotal} piezas · aprovecha ${f(g.aprovechamientoPct)} %", 12f, gris))
            card.setOnClickListener {
                val abrir = cuerpo.visibility != View.VISIBLE
                cuerpo.visibility = if (abrir) View.VISIBLE else View.GONE
                cab.text = (if (abrir) "▾  " else "▸  ") + "${g.perfil} · ${g.sistema} · ${g.color}"
            }
            g.varillas.forEachIndexed { i, v ->
                val retazo = if (v.sobranteMm >= g.retazoUtilMm) " · retazo útil" else ""
                cuerpo.addView(texto("Varilla ${i + 1}  ·  sobra ${v.sobranteMm} mm$retazo", 12f, Color.parseColor("#374151"), negrita = true)
                    .apply { setPadding(0, (10 * dp).toInt(), 0, (3 * dp).toInt()) })
                cuerpo.addView(BarraVarillaView(ctx).apply {
                    mostrar(v)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                })
                cuerpo.addView(texto(v.piezas.joinToString("  +  ") { "${it.largoMm} (V${it.ventana}${if (it.copia > 1) "·${it.copia}" else ""} ${it.etiqueta})" }, 10f, gris)
                    .apply { setPadding(0, (3 * dp).toInt(), 0, 0) })
            }
            card.addView(cuerpo)
            cont.addView(card)
        }

        // ---- 3. Otros materiales ----
        cont.addView(seccion("Otros materiales"))
        val otros = tarjeta()
        otros.addView(texto("${listado.ventanas} ventanas · ${listado.unidades} unidades · ${Unidades.areaM2(listado.areaM2)}"))
        otros.addView(texto("Junta de caucho / sellador: ${Unidades.largoM(listado.cauchoM)}"))
        if (listado.mosquiteros > 0) otros.addView(texto("Paños con mosquitero: ${listado.mosquiteros}"))
        listado.hojasPorApertura.forEach { (k, v) -> otros.addView(texto("Hojas $k: $v")) }
        cont.addView(otros)

        cont.addView(seccion("Vidrios y paneles (ancho × alto, mm)"))
        val vid = tarjeta()
        listado.vidrios.forEach {
            vid.addView(texto("${it.cantidad} ×   ${it.anchoMm} × ${it.altoMm}   ·   ${it.tipo}", 12f, mono = true))
        }
        vid.addView(texto("Vidrio ${Unidades.areaM2(listado.vidrioM2)} · Paneles sándwich ${Unidades.areaM2(listado.sandwichM2)}", 13f, negrita = true)
            .apply { setPadding(0, (6 * dp).toInt(), 0, 0) })
        cont.addView(vid)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val CLAVE = "parametros_corte"
    }
}
