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
import com.example.vineuro_pvc.data.OrderItemHolder
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
        val clientNameFull = OrderItemHolder.clientName

        val startIdx = currentPageIndex * 6
        val endIdx = minOf(startIdx + 6, items.size)
        val pageSublist = if (startIdx in items.indices) items.subList(startIdx, endIdx) else emptyList()

        var totalArea = 0.0
        var totalCount = 0
        for (item in items) {
            val hM = item.altoTotalMm / 1000.0
            val wM = item.anchoTotalMm / 1000.0
            totalArea += (hM * wM)
            totalCount += item.paneCols.size.coerceAtLeast(1)
        }

        val parts = clientNameFull.split(":")
        val clientNameOnly = parts.getOrNull(0)?.trim() ?: "Kafe Swan"
        val clientAddressOnly = parts.getOrNull(1)?.trim() ?: "5 Green Avenue"

        binding.pdfSheetView.bindData(
            pageIndex = currentPageIndex,
            totalPages = totalPages,
            pageItems = pageSublist,
            clientName = clientNameOnly,
            clientAddress = clientAddressOnly,
            totalOrderCount = totalCount,
            totalOrderAreaM2 = totalArea
        )

        binding.tvPageIndicator.text = "Página ${currentPageIndex + 1} de $totalPages"

        binding.btnPrevPage.visibility = if (currentPageIndex > 0) View.VISIBLE else View.GONE
        binding.btnNextPage.visibility = if (currentPageIndex < totalPages - 1) View.VISIBLE else View.GONE
    }

    private fun guardarYCompartirPdf() {
        try {
            val items = OrderItemHolder.items
            val clientNameFull = OrderItemHolder.clientName
            val parts = clientNameFull.split(":")
            val clientNameOnly = parts.getOrNull(0)?.trim() ?: "Kafe Swan"
            val clientAddressOnly = parts.getOrNull(1)?.trim() ?: "5 Green Avenue"

            var totalArea = 0.0
            var totalCount = 0
            for (item in items) {
                val hM = item.altoTotalMm / 1000.0
                val wM = item.anchoTotalMm / 1000.0
                totalArea += (hM * wM)
                totalCount += item.paneCols.size.coerceAtLeast(1)
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

                val sheetView = PdfPageSheetView(requireContext())
                sheetView.layout(0, 0, a4Width, a4Height)
                sheetView.bindData(
                    pageIndex = p,
                    totalPages = totalPages,
                    pageItems = pageSublist,
                    clientName = clientNameOnly,
                    clientAddress = clientAddressOnly,
                    totalOrderCount = totalCount,
                    totalOrderAreaM2 = totalArea
                )
                sheetView.draw(canvas)

                pdfDocument.finishPage(page)
            }

            val docsDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val pdfFile = File(docsDir, "Oferta_Proyectos_${System.currentTimeMillis()}.pdf")

            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            Toast.makeText(requireContext(), "PDF guardado exitosamente: ${pdfFile.name}", Toast.LENGTH_LONG).show()

            // Abrir o Compartir el PDF
            val uri: Uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                pdfFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Compartir PDF de Oferta"))

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Error al guardar el PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
