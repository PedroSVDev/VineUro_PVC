package com.example.vineuro_pvc

import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.vineuro_pvc.data.CalculadoraPrecios
import com.example.vineuro_pvc.data.ClientRepository
import com.example.vineuro_pvc.data.LogoEmpresa
import com.example.vineuro_pvc.data.OrderItemHolder
import com.example.vineuro_pvc.data.PreciosRepository
import com.example.vineuro_pvc.data.RequisitosRepository
import com.example.vineuro_pvc.databinding.FragmentPdfPreviewBinding
import com.example.vineuro_pvc.views.PdfAcciones
import com.example.vineuro_pvc.views.ProyectosPdf
import java.io.File

/**
 * Vista previa del "PDF solo proyectos" / "PDF con detalles". Dibuja cada hoja con la misma función que el archivo
 * (ProyectosPdf.dibujarPagina), escalada al ancho de la pantalla. Flechas ‹ › para cambiar de hoja y
 * GUARDAR PDF para crear el archivo y abrir el menú de compartir.
 */
class PdfPreviewFragment : Fragment() {

    private var _binding: FragmentPdfPreviewBinding? = null
    private val binding get() = _binding!!

    private lateinit var modo: ProyectosPdf.Modo
    private lateinit var datos: ProyectosPdf.Datos
    private var paginas: List<List<ProyectosPdf.Elemento>> = emptyList()
    private var pagina = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPdfPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        modo = if (OrderItemHolder.conDetalles) ProyectosPdf.Modo.CON_DETALLES else ProyectosPdf.Modo.SOLO_PROYECTOS
        datos = armarDatos()
        paginas = ProyectosPdf.paginar(datos, modo)
        pagina = (savedInstanceState?.getInt(KEY_PAGINA) ?: 0).coerceIn(0, paginas.lastIndex.coerceAtLeast(0))

        binding.btnPrevPage.setOnClickListener { cambiarPagina(-1) }
        binding.btnNextPage.setOnClickListener { cambiarPagina(+1) }
        binding.btnGuardarPdf.setOnClickListener { guardarYCompartir() }
        mostrarPagina()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PAGINA, pagina)
    }

    private fun cambiarPagina(delta: Int) {
        val nueva = (pagina + delta).coerceIn(0, paginas.lastIndex.coerceAtLeast(0))
        if (nueva == pagina) return
        pagina = nueva
        mostrarPagina()
        binding.scrollPdfContainer.scrollTo(0, 0)
    }

    private fun mostrarPagina() {
        val ctx = requireContext().applicationContext
        val n = pagina
        binding.pdfSheetView.mostrar { c -> ProyectosPdf.dibujarPagina(ctx, c, datos, modo, paginas, n) }
        binding.tvPageIndicator.text = "Página ${pagina + 1} de ${paginas.size}"
        binding.btnPrevPage.visibility = if (pagina > 0) View.VISIBLE else View.GONE
        binding.btnNextPage.visibility = if (pagina < paginas.lastIndex) View.VISIBLE else View.GONE
    }

    /** Crea el PDF (exactamente lo que se ve en la vista previa) y abre el menú de compartir. */
    private fun guardarYCompartir() {
        val ctx = requireContext()
        try {
            val carpeta = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: ctx.filesDir
            val base = if (modo == ProyectosPdf.Modo.CON_DETALLES) "Proyectos_Detalles" else "Proyectos"
            val codigo = datos.idOferta.takeIf { it.isNotBlank() && it != SIN_DATO }
                ?.replace(Regex("[^A-Za-z0-9_-]"), "_")?.let { "_$it" }.orEmpty()
            val archivo = File(carpeta, "${base}${codigo}_${System.currentTimeMillis()}.pdf")
            ProyectosPdf.crearArchivo(ctx, datos, modo, archivo)
            Toast.makeText(ctx, "PDF guardado: ${archivo.name}", Toast.LENGTH_SHORT).show()
            PdfAcciones.enviar(ctx, archivo, if (modo == ProyectosPdf.Modo.CON_DETALLES) "Cotización con detalles" else "Cotización")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(ctx, "Error al guardar el PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    /** Datos del documento: empresa (Datos de la empresa), cliente, ventanas y sus precios calculados. */
    private fun armarDatos(): ProyectosPdf.Datos {
        val ctx = requireContext()
        val items = OrderItemHolder.items
        val precios = PreciosRepository(ctx).obtener()
        val resultados = items.map { runCatching { CalculadoraPrecios.calcular(it, precios) }.getOrNull() }
        val cliente = OrderItemHolder.clienteId.takeIf { it.isNotBlank() }
            ?.let { ClientRepository(ctx).obtenerClientePorId(it) }
        fun v(s: String?) = s?.trim()?.takeIf { it.isNotBlank() } ?: SIN_DATO
        val colores = precios.colores.mapNotNull { f ->
            runCatching { f.nombre.trim() to Color.parseColor(f.colorHex.trim()) }.getOrNull()
        }.toMap()
        return ProyectosPdf.Datos(
            requisitos = RequisitosRepository(ctx).obtener(),
            clienteNombre = v(cliente?.nombreCompleto ?: OrderItemHolder.clientName),
            clienteDireccion = v(OrderItemHolder.direccionPedido.ifBlank { cliente?.direccion.orEmpty() }),
            clienteTelefono = v(cliente?.telefono),
            clienteCorreo = v(cliente?.email),
            // "ID de oferta" = N° del pedido
            idOferta = v(OrderItemHolder.codigoPedido.ifBlank { OrderItemHolder.idOfertaPedido }),
            items = items,
            resultados = resultados,
            colores = colores,
            logo = runCatching { LogoEmpresa.cargar(ctx) }.getOrNull(),
            impuestoNombre = precios.impuesto.firstOrNull()?.getOrNull(0)?.trim()?.takeIf { it.isNotBlank() } ?: "IGV",
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_PAGINA = "pagina_pdf"
        private const val SIN_DATO = "......"
    }
}
