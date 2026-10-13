package com.example.vineuro_pvc

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.vineuro_pvc.data.CalculadoraPrecios
import com.example.vineuro_pvc.data.ClientRepository
import com.example.vineuro_pvc.data.OrderItemHolder
import com.example.vineuro_pvc.data.PreciosRepository
import com.example.vineuro_pvc.data.RequisitosRepository
import com.example.vineuro_pvc.databinding.FragmentPdfPreviewBinding
import com.example.vineuro_pvc.views.GuiaCortePdf
import com.example.vineuro_pvc.views.ProyectosPdf
import java.io.File

/**
 * Vista previa y exportación de:
 * - "PDF solo proyectos" (hasta 6 diseños por hoja) y "PDF con detalles" (2 por hoja, a color):
 *   se dibujan directamente en la hoja.
 * - "Guía de corte": se genera el PDF y se muestran sus páginas tal cual (igual que el archivo).
 */
class PdfPreviewFragment : Fragment() {

    private var _binding: FragmentPdfPreviewBinding? = null
    private val binding get() = _binding!!

    private var paginaActual = 0
    private lateinit var datos: ProyectosPdf.Datos
    private lateinit var modo: ProyectosPdf.Modo
    private var paginas: List<List<ProyectosPdf.Elemento>> = emptyList()

    // Guía de corte: archivo ya generado + lector de páginas
    private var esGuia = false
    private var archivoGuia: File? = null
    private var lectorGuia: android.graphics.pdf.PdfRenderer? = null
    private var descriptorGuia: android.os.ParcelFileDescriptor? = null

    private val totalPaginas: Int get() = if (esGuia) (lectorGuia?.pageCount ?: 1) else paginas.size

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPdfPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        esGuia = OrderItemHolder.guiaCorte
        modo = if (OrderItemHolder.conDetalles) ProyectosPdf.Modo.CON_DETALLES else ProyectosPdf.Modo.SOLO_PROYECTOS
        datos = armarDatos()
        if (esGuia) {
            prepararGuiaCorte()
        } else {
            paginas = ProyectosPdf.paginar(datos, modo)
        }

        binding.btnPrevPage.setOnClickListener {
            if (paginaActual > 0) { paginaActual--; mostrarPagina() }
        }
        binding.btnNextPage.setOnClickListener {
            if (paginaActual < totalPaginas - 1) { paginaActual++; mostrarPagina() }
        }
        binding.btnGuardarPdf.setOnClickListener { guardarYCompartirPdf() }
        mostrarPagina()
    }

    private fun mostrarPagina() {
        if (esGuia) {
            binding.pdfSheetView.visibility = View.GONE
            binding.ivPaginaPdf.visibility = View.VISIBLE
            binding.ivPaginaPdf.setImageBitmap(renderizarPaginaGuia(paginaActual))
        } else {
            binding.pdfSheetView.mostrar(datos, modo, paginas, paginaActual)
        }
        binding.tvPageIndicator.text = "Página ${paginaActual + 1} de $totalPaginas"
        binding.btnPrevPage.visibility = if (paginaActual > 0) View.VISIBLE else View.GONE
        binding.btnNextPage.visibility = if (paginaActual < totalPaginas - 1) View.VISIBLE else View.GONE
    }

    /** Genera el PDF de la guía de corte y lo abre para mostrar sus páginas. */
    private fun prepararGuiaCorte() {
        try {
            val ctx = requireContext()
            val carpeta = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val codigo = datos.codigo.ifBlank { System.currentTimeMillis().toString() }.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val archivo = GuiaCortePdf.crear(
                ctx,
                GuiaCortePdf.Datos(clienteNombre = datos.clienteNombre, codigo = datos.codigo, items = datos.items),
                File(carpeta, "Guia_corte_$codigo.pdf"),
            )
            archivoGuia = archivo
            descriptorGuia = android.os.ParcelFileDescriptor.open(archivo, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            lectorGuia = android.graphics.pdf.PdfRenderer(descriptorGuia!!)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "No se pudo generar la guía de corte: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    /** Dibuja una página de la guía en una imagen del ancho de la pantalla (fondo blanco). */
    private fun renderizarPaginaGuia(n: Int): android.graphics.Bitmap? {
        val lector = lectorGuia ?: return null
        if (n !in 0 until lector.pageCount) return null
        val pagina = lector.openPage(n)
        try {
            val ancho = resources.displayMetrics.widthPixels.coerceIn(600, 1800)
            val alto = (ancho.toFloat() * pagina.height / pagina.width).toInt()
            val bmp = android.graphics.Bitmap.createBitmap(ancho, alto, android.graphics.Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            pagina.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bmp
        } finally {
            pagina.close()
        }
    }

    private fun guardarYCompartirPdf() {
        if (esGuia) {
            compartirArchivo(archivoGuia ?: return, "Compartir guía de corte")
            return
        }
        try {
            val ctx = requireContext()
            val carpeta = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val prefijo = if (modo == ProyectosPdf.Modo.CON_DETALLES) "Proyectos_detalle" else "Proyectos"
            val codigo = datos.codigo.ifBlank { System.currentTimeMillis().toString() }.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val archivo = ProyectosPdf.crearArchivo(ctx, datos, modo, File(carpeta, "${prefijo}_$codigo.pdf"))

            compartirArchivo(archivo, "Compartir PDF")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Error al guardar el PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun compartirArchivo(archivo: File, titulo: String) {
        val ctx = requireContext()
        Toast.makeText(ctx, "PDF guardado: ${archivo.name}", Toast.LENGTH_LONG).show()
        val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", archivo)
        val compartir = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(compartir, titulo))
    }

    /** Datos del documento: empresa (editable), cliente real, ventanas con sus precios y colores. */
    private fun armarDatos(): ProyectosPdf.Datos {
        val ctx = requireContext()
        val cliente = OrderItemHolder.clienteId.takeIf { it.isNotBlank() }
            ?.let { ClientRepository(ctx).obtenerClientePorId(it) }
        fun v(s: String?) = s?.takeIf { it.isNotBlank() } ?: "......"
        val precios = PreciosRepository(ctx).obtener()
        val items = OrderItemHolder.items
        val colores = precios.colores.mapNotNull { f ->
            runCatching { f.nombre.trim() to Color.parseColor(f.colorHex.trim()) }.getOrNull()
        }.toMap()
        return ProyectosPdf.Datos(
            requisitos = RequisitosRepository(ctx).obtener(),
            clienteNombre = cliente?.nombreCompleto ?: OrderItemHolder.clientName.ifBlank { "......" },
            clienteDireccion = v(cliente?.direccion),
            clienteTelefono = v(cliente?.telefono),
            clienteCorreo = v(cliente?.email),
            codigo = OrderItemHolder.codigoPedido,
            items = items,
            resultados = items.map { CalculadoraPrecios.calcular(it, precios) },
            colores = colores,
            impuestoNombre = precios.impuesto.firstOrNull()?.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "IGV",
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        runCatching { lectorGuia?.close() }
        runCatching { descriptorGuia?.close() }
        lectorGuia = null
        descriptorGuia = null
        _binding = null
    }
}
