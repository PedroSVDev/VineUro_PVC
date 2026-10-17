package com.example.vineuro_pvc

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.vineuro_pvc.data.IntercambioDatos
import com.example.vineuro_pvc.databinding.FragmentCompartirDatosBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Compartir datos: envía los precios o los pedidos como archivo (por Bluetooth, WhatsApp, correo…) y recibe
 * archivos de otro teléfono mostrando antes un resumen (nuevos / idénticos / se reemplazan / ignorados).
 * Los pedidos enviados incluyen sus clientes y cotizaciones, así que también sirve de copia de respaldo.
 */
class CompartirDatosFragment : Fragment() {

    private var _binding: FragmentCompartirDatosBinding? = null
    private val binding get() = _binding!!

    private val elegirArchivo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) leerYMostrarResumen(uri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCompartirDatosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnObtenerArchivos.setOnClickListener {
            // El archivo puede llegar por Bluetooth (carpeta Descargas / Bluetooth), WhatsApp, correo o Drive
            elegirArchivo.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
        }
        binding.btnEnviarPrecios.setOnClickListener { enviar { IntercambioDatos.exportarPrecios(requireContext()) } }
        binding.btnEnviarPedidos.setOnClickListener { enviar { IntercambioDatos.exportarPedidos(requireContext()) } }
        binding.btnVolver.setOnClickListener { findNavController().navigateUp() }
    }

    private fun enviar(crear: () -> File) {
        val ctx = requireContext()
        val archivo = try { crear() } catch (e: Exception) {
            Toast.makeText(ctx, "No se pudo preparar el archivo: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", archivo)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, archivo.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Enviar por Bluetooth, WhatsApp, correo…"))
    }

    private fun leerYMostrarResumen(uri: Uri) {
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val resultado = withContext(Dispatchers.IO) {
                runCatching {
                    val texto = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: throw IntercambioDatos.ArchivoInvalido("No se pudo leer el archivo.")
                    IntercambioDatos.analizar(ctx, texto)
                }
            }
            if (_binding == null) return@launch
            resultado.onFailure {
                MaterialAlertDialogBuilder(ctx).setTitle("Archivo no válido").setMessage(it.message ?: "Error al leer el archivo")
                    .setPositiveButton("CERRAR", null).show()
            }.onSuccess { mostrarResumen(it) }
        }
    }

    private fun mostrarResumen(r: IntercambioDatos.Resumen) {
        val ctx = requireContext()
        val msg = buildString {
            append(if (r.tipo == "precios") "Archivo de PRECIOS" else "Archivo de PEDIDOS").append("\n\n")
            for (c in IntercambioDatos.Categoria.values()) {
                val lista = r.entradas.filter { it.categoria == c }
                if (lista.isEmpty()) continue
                append("${c.titulo}: ${lista.size}\n")
                lista.take(8).forEach { append("   • ${it.titulo}\n") }
                if (lista.size > 8) append("   … y ${lista.size - 8} más\n")
                append("\n")
            }
            if (r.entradas.isEmpty()) append("El archivo no trae datos.")
        }
        val b = MaterialAlertDialogBuilder(ctx).setTitle("Resumen del archivo").setMessage(msg.trim())
        if (r.aplicables.isNotEmpty()) {
            b.setPositiveButton("APLICAR (${r.aplicables.size})") { _, _ -> aplicar(r) }
            b.setNegativeButton("CANCELAR", null)
        } else {
            b.setPositiveButton("CERRAR", null)
        }
        b.show()
    }

    private fun aplicar(r: IntercambioDatos.Resumen) {
        val ctx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val n = runCatching { withContext(Dispatchers.IO) { IntercambioDatos.aplicar(r) } }
            Toast.makeText(
                ctx,
                n.fold({ "Listo: se aplicaron $it elemento(s)" }, { "No se pudo aplicar todo: ${it.message}" }),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
