package com.example.vineuro_pvc

import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.vineuro_pvc.data.CalculadoraPrecios
import com.example.vineuro_pvc.data.ClientRepository
import com.example.vineuro_pvc.data.CotizacionPedido
import com.example.vineuro_pvc.data.Pedido
import com.example.vineuro_pvc.data.PedidoRepository
import com.example.vineuro_pvc.data.Precios
import com.example.vineuro_pvc.data.PreciosRepository
import com.example.vineuro_pvc.data.RequisitosRepository
import com.example.vineuro_pvc.data.TerminosCondiciones
import com.example.vineuro_pvc.data.TerminosRepository
import com.example.vineuro_pvc.data.TipoCambioService
import com.example.vineuro_pvc.data.VariablesOferta
import com.example.vineuro_pvc.databinding.FragmentOfertaComercialBinding
import com.example.vineuro_pvc.views.OfertaComercialPdf
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Oferta comercial de un pedido: N° de cotización, tipo de cambio, totales en US$ y S/,
 * condiciones de esta cotización (copia editable de la plantilla) y generación del PDF.
 */
class OfertaComercialFragment : Fragment() {

    private var _binding: FragmentOfertaComercialBinding? = null
    private val binding get() = _binding!!

    private lateinit var terminosRepo: TerminosRepository
    private lateinit var editor: EditorClausulas
    private lateinit var pedido: Pedido
    private lateinit var precios: Precios
    private lateinit var plantilla: TerminosCondiciones
    private var resultados: List<CalculadoraPrecios.Resultado> = emptyList()
    private var fechaTipoCambio: String = ""
    private val fechaHoy: String = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOfertaComercialBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        terminosRepo = TerminosRepository(ctx)

        val p = arguments?.getString("pedido_id")?.let { PedidoRepository(ctx).obtenerPedido(it) }
        if (p == null || p.items.isEmpty()) {
            Toast.makeText(ctx, "Primero agrega ventanas al pedido", Toast.LENGTH_LONG).show()
            findNavController().navigateUp()
            return
        }
        pedido = p
        precios = PreciosRepository(ctx).obtener()
        plantilla = terminosRepo.plantilla()
        resultados = pedido.items.map { CalculadoraPrecios.calcular(it, precios) }
        editor = EditorClausulas(binding.contenedorClausulasOferta)

        binding.tvClienteOferta.text = pedido.clienteNombre.ifBlank { "Cliente sin nombre" }
        binding.tvResumenOferta.text = String.format(
            Locale.US, "%d %s · %.2f m² · %s", pedido.totalVentanas,
            if (pedido.totalVentanas == 1) "ventana" else "ventanas", pedido.areaTotalM2, fechaHoy,
        )

        // Cotización guardada de este pedido, o una nueva copiada de la plantilla
        val guardada = terminosRepo.cotizacion(pedido.id)
        if (guardada != null) {
            mostrarCotizacion(guardada)
        } else {
            val tcCache = TipoCambioService(ctx).guardado()
            mostrarCotizacion(
                CotizacionPedido(
                    pedidoId = pedido.id, numero = "",
                    tipoCambio = tcCache?.usdPen ?: 0.0, fechaTipoCambio = tcCache?.fechaUsd ?: "",
                    adelantoPct = plantilla.adelantoPct, vigenciaDias = plantilla.vigenciaDias,
                    clausulas = plantilla.clausulas,
                ),
            )
            // El N° de cotización es el mismo N° del pedido (0405…). Los pedidos antiguos
            // (código "PED-fecha") reciben el siguiente número del correlativo.
            val numeroPedido = pedido.codigo.trim().takeIf { it.isNotEmpty() && it.all { ch -> ch.isDigit() } }
            if (numeroPedido != null) binding.etNumeroCotizacion.setText(numeroPedido)
            viewLifecycleOwner.lifecycleScope.launch {
                if (numeroPedido == null) {
                    val numero = terminosRepo.siguienteNumero()
                    if (_binding != null && binding.etNumeroCotizacion.text.isNullOrBlank()) binding.etNumeroCotizacion.setText(numero)
                }
                if (tcCache == null) actualizarTipoCambio(forzar = false)
            }
        }

        DialogTerminos.llenarChipsVariables(binding.chipsVariablesOferta) { variable ->
            if (!editor.insertarEnCursor(variable)) {
                Toast.makeText(ctx, "Primero toca el texto de una cláusula", Toast.LENGTH_SHORT).show()
            }
        }

        binding.etTipoCambio.doAfterTextChanged { fechaTipoCambio = "manual"; actualizarTotales(); actualizarInfoTc() }
        binding.etAdelantoOferta.doAfterTextChanged { actualizarTotales() }
        binding.btnTipoCambioBcrp.setOnClickListener { actualizarTipoCambio(forzar = true) }
        binding.btnAgregarClausulaOferta.setOnClickListener { editor.agregarNueva() }
        binding.btnRestaurarDesdePlantilla.setOnClickListener {
            MaterialAlertDialogBuilder(ctx)
                .setTitle("¿Copiar de nuevo desde la plantilla?")
                .setMessage("Se reemplazarán las condiciones de esta cotización por las de la plantilla general.")
                .setPositiveButton("COPIAR") { d, _ -> editor.cargar(terminosRepo.plantilla().clausulas); d.dismiss() }
                .setNegativeButton("CANCELAR", null)
                .show()
        }
        binding.btnGuardarOferta.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val error = guardar()
                Toast.makeText(ctx, if (error == null) "Cotización guardada ✅" else "Guardada en el teléfono. La nube respondió: $error", Toast.LENGTH_LONG).show()
            }
        }
        binding.btnGenerarPdfOferta.setOnClickListener { generarPdf() }
        actualizarTotales()
    }

    // ===================== DATOS EN PANTALLA =====================

    private fun mostrarCotizacion(c: CotizacionPedido) {
        binding.etNumeroCotizacion.setText(c.numero)
        binding.etAdelantoOferta.setText(c.adelantoPct.toString())
        binding.etTipoCambio.setText(if (c.tipoCambio > 0) String.format(Locale.US, "%.3f", c.tipoCambio) else "")
        fechaTipoCambio = c.fechaTipoCambio
        editor.cargar(c.clausulas.ifEmpty { plantilla.clausulas })
        actualizarInfoTc()
    }

    private fun leerCotizacion() = CotizacionPedido(
        pedidoId = pedido.id,
        numero = binding.etNumeroCotizacion.text.toString().trim(),
        tipoCambio = tipoCambio(),
        fechaTipoCambio = fechaTipoCambio,
        adelantoPct = adelantoPct(),
        vigenciaDias = terminosRepo.cotizacion(pedido.id)?.vigenciaDias ?: plantilla.vigenciaDias,
        clausulas = editor.leer(),
    )

    private fun tipoCambio() = binding.etTipoCambio.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
    private fun adelantoPct() = binding.etAdelantoOferta.text.toString().toIntOrNull()?.coerceIn(0, 100) ?: plantilla.adelantoPct

    private fun actualizarInfoTc() {
        binding.tvInfoTipoCambioOferta.text = when {
            tipoCambio() <= 0 -> "Ingresa el tipo de cambio o tócalo en BCRP"
            fechaTipoCambio.isBlank() || fechaTipoCambio == "manual" -> "Tipo de cambio ingresado a mano"
            else -> "T.C. BCRP (venta) del $fechaTipoCambio"
        }
    }

    /** Totales en dólares y soles según la moneda de los precios y el tipo de cambio. */
    private fun totales(): Pair<Double, Double> {
        val total = resultados.sumOf { it.totalVenta }
        val tc = tipoCambio()
        return when (simbolo()) {
            "US$", "$" -> total to (if (tc > 0) total * tc else 0.0)
            "€" -> {
                val eurPen = TipoCambioService(requireContext()).guardado()?.eurPen ?: 0.0
                val pen = total * eurPen
                (if (tc > 0) pen / tc else 0.0) to pen
            }
            else -> (if (tc > 0) total / tc else 0.0) to total // soles
        }
    }

    private fun simbolo(): String = precios.moneda.firstOrNull()?.getOrNull(2)?.trim()?.ifBlank { null } ?: "S/"

    private fun actualizarTotales() {
        val b = _binding ?: return
        val (usd, pen) = totales()
        b.tvTotalUsd.text = String.format(Locale.US, "US$ %,.2f", usd)
        b.tvTotalPen.text = String.format(Locale.US, "S/ %,.2f", pen)
        val total = resultados.sumOf { it.totalVenta }
        val a = adelantoPct()
        b.tvAdelantoSaldo.text = String.format(
            Locale.US, "Adelanto %d%%: %s   ·   Saldo %d%%: %s", a, VariablesOferta.dinero(simbolo(), total * a / 100.0),
            100 - a, VariablesOferta.dinero(simbolo(), total * (100 - a) / 100.0),
        )
    }

    private fun actualizarTipoCambio(forzar: Boolean) {
        val servicio = TipoCambioService(requireContext())
        binding.btnTipoCambioBcrp.isEnabled = false
        binding.tvInfoTipoCambioOferta.text = "Consultando BCRP…"
        viewLifecycleOwner.lifecycleScope.launch {
            val r = servicio.obtener(forzar)
            val tc = r.getOrNull() ?: servicio.guardado()
            if (_binding == null) return@launch
            binding.btnTipoCambioBcrp.isEnabled = true
            if (tc == null) {
                actualizarInfoTc()
                Toast.makeText(requireContext(), "No se pudo consultar el BCRP. Ingresa el tipo de cambio a mano.", Toast.LENGTH_LONG).show()
                return@launch
            }
            binding.etTipoCambio.setText(String.format(Locale.US, "%.3f", tc.usdPen))
            fechaTipoCambio = tc.fechaUsd // después del setText (que lo marca como "manual")
            actualizarInfoTc()
            actualizarTotales()
        }
    }

    // ===================== GUARDAR Y PDF =====================

    private suspend fun guardar(): String? {
        val c = leerCotizacion()
        terminosRepo.registrarNumeroManual(c.numero)
        // Pedido y cotización llevan el mismo número: si cambia aquí, se actualiza el pedido
        if (c.numero.isNotBlank() && c.numero != pedido.codigo) {
            pedido = pedido.copy(codigo = c.numero, fechaActualizacion = System.currentTimeMillis())
            runCatching { PedidoRepository(requireContext()).guardarPedido(pedido) }
        }
        return terminosRepo.guardarCotizacion(c)
    }

    /** Valores de los campos automáticos ({cliente}, {color}…) para esta cotización. */
    private fun valoresVariables(c: CotizacionPedido): Map<String, String> {
        val (usd, pen) = totales()
        val total = resultados.sumOf { it.totalVenta }
        val ops = pedido.items.map { it.opciones }
        fun distintos(f: (com.example.vineuro_pvc.data.OpcionesVentana) -> String?) =
            ops.mapNotNull(f).filter { it.isNotBlank() }.distinct().joinToString(", ")
        val nombreMoneda = when (simbolo()) { "US$", "$" -> "Dólares Americanos"; "€" -> "Euros"; else -> "Soles" }
        return mapOf(
            "{cliente}" to pedido.clienteNombre,
            "{cotizacion}" to c.numero,
            "{fecha}" to fechaHoy,
            "{color}" to distintos { it.color }.ifBlank { "BLANCO" },
            "{cristal}" to distintos { it.tipoCristal }.ifBlank { "—" },
            "{sistema}" to distintos { it.sistemaVentana }.ifBlank { "—" },
            "{adelanto}" to c.adelantoPct.toString(),
            "{saldo}" to (100 - c.adelantoPct).toString(),
            "{monto_adelanto}" to VariablesOferta.dinero(simbolo(), total * c.adelantoPct / 100.0),
            "{monto_saldo}" to VariablesOferta.dinero(simbolo(), total * (100 - c.adelantoPct) / 100.0),
            "{vigencia}" to c.vigenciaDias.toString(),
            "{tipo_cambio}" to String.format(Locale.US, "S/ %.3f", c.tipoCambio),
            "{total_usd}" to String.format(Locale.US, "US$ %,.2f", usd),
            "{total_soles}" to String.format(Locale.US, "S/ %,.2f", pen),
            "{moneda}" to nombreMoneda,
        )
    }

    private fun generarPdf() {
        val ctx = requireContext()
        val c = leerCotizacion()
        if (c.numero.isBlank()) { binding.etNumeroCotizacion.error = "Ingresa el N°"; return }
        if (c.tipoCambio <= 0) { Toast.makeText(ctx, "Ingresa el tipo de cambio o tócalo en BCRP", Toast.LENGTH_LONG).show(); return }
        if (c.clausulas.isEmpty()) { Toast.makeText(ctx, "Agrega al menos una condición", Toast.LENGTH_SHORT).show(); return }

        binding.btnGenerarPdfOferta.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            guardar()
            val valores = valoresVariables(c)
            val (usd, pen) = totales()
            val cliente = pedido.clienteId.takeIf { it.isNotBlank() }?.let { ClientRepository(ctx).obtenerClientePorId(it) }
            val datos = OfertaComercialPdf.Datos(
                requisitos = RequisitosRepository(ctx).obtener(),
                clienteNombre = cliente?.nombreCompleto ?: pedido.clienteNombre,
                clienteDireccion = cliente?.direccion ?: "",
                clienteTelefono = cliente?.telefono ?: "",
                clienteCorreo = cliente?.email ?: "",
                numero = c.numero,
                fecha = fechaHoy,
                tipoCambio = c.tipoCambio,
                totalUsd = usd,
                totalPen = pen,
                clausulas = c.clausulas.map { it.copy(texto = VariablesOferta.reemplazar(it.texto, valores)) },
                items = pedido.items,
                resultados = resultados,
                simbolo = simbolo(),
            )
            val archivo = runCatching { OfertaComercialPdf.crear(ctx, datos) }
            if (_binding == null) return@launch
            binding.btnGenerarPdfOferta.isEnabled = true
            archivo.onSuccess { mostrarVistaPrevia(it) }
                .onFailure { Toast.makeText(ctx, "Error al generar el PDF: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    /** Muestra las páginas del PDF generado, con botón para compartir. */
    private fun mostrarVistaPrevia(archivo: File) {
        val ctx = requireContext()
        val ancho = (resources.displayMetrics.widthPixels * 0.9f).toInt()
        viewLifecycleOwner.lifecycleScope.launch {
            val paginas = withContext(Dispatchers.IO) {
                runCatching {
                    ParcelFileDescriptor.open(archivo, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        PdfRenderer(pfd).use { r ->
                            (0 until r.pageCount).map { i ->
                                r.openPage(i).use { pg ->
                                    val alto = (ancho * pg.height.toFloat() / pg.width).toInt()
                                    Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888).also { bmp ->
                                        bmp.eraseColor(Color.WHITE)
                                        pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    }
                                }
                            }
                        }
                    }
                }.getOrDefault(emptyList())
            }
            if (_binding == null) return@launch

            val dp = resources.displayMetrics.density
            val lista = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
            }
            paginas.forEach { bmp ->
                lista.addView(ImageView(ctx).apply {
                    setImageBitmap(bmp)
                    adjustViewBounds = true
                    elevation = 4 * dp
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .apply { bottomMargin = (10 * dp).toInt() }
                })
            }
            val botones = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
                setBackgroundColor(Color.WHITE)
            }
            val raiz = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#E5E7EB"))
                addView(ScrollView(ctx).apply { addView(lista) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                addView(botones)
            }
            val dialog = Dialog(ctx, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
            fun boton(texto: String, color: String, accion: () -> Unit) = MaterialButton(ctx).apply {
                text = texto
                setBackgroundColor(Color.parseColor(color))
                setTextColor(Color.WHITE)
                setOnClickListener { accion() }
                layoutParams = LinearLayout.LayoutParams(0, (52 * dp).toInt(), 1f).apply { marginStart = (4 * dp).toInt(); marginEnd = (4 * dp).toInt() }
            }
            botones.addView(boton("Cerrar", "#6B7280") { dialog.dismiss() })
            botones.addView(boton("Compartir PDF", "#E56372") { compartir(archivo) })
            dialog.setContentView(raiz)
            dialog.show()
            Toast.makeText(ctx, "PDF guardado: ${archivo.name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun compartir(archivo: File) {
        val ctx = requireContext()
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", archivo)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Oferta comercial N° ${binding.etNumeroCotizacion.text}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Compartir oferta comercial"))
    }

    override fun onPause() {
        super.onPause()
        // Autoguardado de la cotización al salir de la pantalla
        if (_binding != null && ::pedido.isInitialized) {
            val c = leerCotizacion()
            val repo = terminosRepo
            lifecycleScope.launch { repo.guardarCotizacion(c) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
