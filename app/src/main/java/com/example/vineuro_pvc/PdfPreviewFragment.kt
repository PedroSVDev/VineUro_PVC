package com.example.vineuro_pvc

import android.content.Intent
import android.graphics.pdf.PdfDocument
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
import com.example.vineuro_pvc.data.ClientRepository
import com.example.vineuro_pvc.data.OrderItemHolder
import com.example.vineuro_pvc.data.RequisitosDocumento
import com.example.vineuro_pvc.data.RequisitosRepository
import com.example.vineuro_pvc.databinding.FragmentPdfPreviewBinding
import com.example.vineuro_pvc.views.PdfPageSheetView
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.ceil

class PdfPreviewFragment : Fragment() {

    private var _binding: FragmentPdfPreviewBinding? = null
    private val binding get() = _binding!!

    private var currentPageIndex = 0
    private var totalPages = 1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPdfPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val items = OrderItemHolder.items
        val count = items.size.coerceAtLeast(1)
        totalPages = ceil(count / 6.0).toInt().coerceAtLeast(1)

        setupListeners()
        renderCurrentPage()
    }

    private fun setupListeners() {
        binding.btnCerca.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnPrevPage.setOnClickListener {
            if (currentPageIndex > 0) {
                currentPageIndex--
                renderCurrentPage()
            }
        }

        binding.btnNextPage.setOnClickListener {
            if (currentPageIndex < totalPages - 1) {
                currentPageIndex++
                renderCurrentPage()
            }
        }

        binding.btnGuardarPdf.setOnClickListener {
            guardarYCompartirPdf()
        }
    }

    private fun renderCurrentPage() {
        val items = OrderItemHolder.items

        val startIdx = currentPageIndex * 6
        val endIdx = minOf(startIdx + 6, items.size)
        val pageSublist = if (startIdx in items.indices) items.subList(startIdx, endIdx) else emptyList()

        var totalArea = 0.0
        var totalCount = 0
        for (item in items) {
            val cant = item.opciones.cantidad.coerceAtLeast(1)
            totalArea += item.areaM2 * cant
            totalCount += cant
        }

        val datos = datosDocumento()

        binding.pdfSheetView.bindData(
            pageIndex = currentPageIndex,
            totalPages = totalPages,
            pageItems = pageSublist,
            clientName = datos.clienteNombre,
            clientAddress = datos.clienteDireccion,
            clientPhone = datos.clienteTelefono,
            clientEmail = datos.clienteCorreo,
            offerId = datos.codigo,
            totalOrderCount = totalCount,
            totalOrderAreaM2 = totalArea,
            requisitos = datos.requisitos,
            conDetalles = OrderItemHolder.conDetalles,
        )

        binding.tvPageIndicator.text = "Página ${currentPageIndex + 1} de $totalPages"

        binding.btnPrevPage.visibility = if (currentPageIndex > 0) View.VISIBLE else View.GONE
        binding.btnNextPage.visibility = if (currentPageIndex < totalPages - 1) View.VISIBLE else View.GONE
    }

    private fun guardarYCompartirPdf() {
        try {
            val items = OrderItemHolder.items
            val datos = datosDocumento()

            var totalArea = 0.0
            var totalCount = 0
            for (item in items) {
                val cant = item.opciones.cantidad.coerceAtLeast(1)
                totalArea += item.areaM2 * cant
                totalCount += cant
            }

            val pdfDocument = PdfDocument()

            // Dimensiones hoja A4 en puntos (595 x 842 pt @ 72 dpi)
            val a4Width = 595
            val a4Height = 842

            for (p in 0 until totalPages) {
                val pageInfo = PdfDocument.PageInfo.Builder(a4Width, a4Height, p + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                val startIdx = p * 6
                val endIdx = minOf(startIdx + 6, items.size)
                val pageSublist = if (startIdx in items.indices) items.subList(startIdx, endIdx) else emptyList()

                // La hoja dibuja sus medidas en dp (se multiplican por la densidad de la pantalla).
                // Se dibuja en un lienzo "virtual" del tamaño que tiene en la vista previa (~400 dp de ancho)
                // y luego se reduce a la hoja A4 (595 x 842 pt). Así el PDF queda igual que la vista previa.
                val densidad = resources.displayMetrics.density
                val anchoVirtual = (400f * densidad).toInt()
                val altoVirtual = (anchoVirtual * a4Height.toFloat() / a4Width).toInt()
                val escala = a4Width.toFloat() / anchoVirtual

                val sheetView = PdfPageSheetView(requireContext())
                sheetView.layout(0, 0, anchoVirtual, altoVirtual)
                sheetView.bindData(
                    pageIndex = p,
                    totalPages = totalPages,
                    pageItems = pageSublist,
                    clientName = datos.clienteNombre,
                    clientAddress = datos.clienteDireccion,
                    clientPhone = datos.clienteTelefono,
                    clientEmail = datos.clienteCorreo,
                    offerId = datos.codigo,
                    totalOrderCount = totalCount,
                    totalOrderAreaM2 = totalArea,
                    requisitos = datos.requisitos,
                    conDetalles = OrderItemHolder.conDetalles,
                )
                canvas.save()
                canvas.scale(escala, escala)
                sheetView.draw(canvas)
                canvas.restore()

                pdfDocument.finishPage(page)
            }

            val docsDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val pdfFile = File(docsDir, "${if (OrderItemHolder.conDetalles) "Proyectos_Detalles" else "Proyectos"}_${System.currentTimeMillis()}.pdf")

            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            Toast.makeText(requireContext(), "PDF guardado exitosamente: ${pdfFile.name}", Toast.LENGTH_LONG).show()

            // Enviar o abrir el PDF (diálogo como el)
            com.example.vineuro_pvc.views.PdfAcciones.mostrarDialogo(requireContext(), pdfFile, "PDF de Oferta")

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Error al guardar el PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    /** Datos del encabezado: requisitos de la empresa (editables) + datos reales del cliente. */
    private data class DatosDocumento(
        val requisitos: RequisitosDocumento,
        val clienteNombre: String,
        val clienteDireccion: String,
        val clienteTelefono: String,
        val clienteCorreo: String,
        val codigo: String,
    )

    private fun datosDocumento(): DatosDocumento {
        val ctx = requireContext()
        val cliente = OrderItemHolder.clienteId.takeIf { it.isNotBlank() }
            ?.let { ClientRepository(ctx).obtenerClientePorId(it) }
        fun v(s: String?) = s?.takeIf { it.isNotBlank() } ?: "......"
        return DatosDocumento(
            requisitos = RequisitosRepository(ctx).obtener(),
            clienteNombre = cliente?.nombreCompleto ?: OrderItemHolder.clientName.ifBlank { "......" },
            clienteDireccion = v(OrderItemHolder.direccionPedido.ifBlank { cliente?.direccion }),
            clienteTelefono = v(cliente?.telefono),
            clienteCorreo = v(cliente?.email),
            codigo = OrderItemHolder.idOfertaPedido.ifBlank { OrderItemHolder.codigoPedido },
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
