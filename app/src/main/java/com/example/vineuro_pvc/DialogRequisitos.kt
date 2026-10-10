package com.example.vineuro_pvc

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.vineuro_pvc.data.RequisitosDocumento
import com.example.vineuro_pvc.data.RequisitosRepository
import com.example.vineuro_pvc.databinding.DialogConfigurarRequisitosBinding
import kotlinx.coroutines.launch

/**
 * Ventana "Configurar requisitos": datos de la empresa y condiciones que se imprimen en el PDF.
 * Muestra una vista previa en vivo del encabezado del PDF.
 */
class DialogRequisitos(private val fragment: Fragment) {

    private val context = fragment.requireContext()
    private val repo = RequisitosRepository(context)
    private lateinit var b: DialogConfigurarRequisitosBinding

    fun mostrar() {
        b = DialogConfigurarRequisitosBinding.inflate(LayoutInflater.from(context))

        val dialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(b.root)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                val dm = context.resources.displayMetrics
                setLayout((dm.widthPixels * 0.94f).toInt(), (dm.heightPixels * 0.90f).toInt())
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
        }

        cargar(repo.obtener())

        // Traer la versión de la nube por si otro compañero la cambió
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val antes = repo.obtener().fechaActualizacion
            val sincronizado = repo.sincronizar()
            if (dialog.isShowing && sincronizado.fechaActualizacion > antes) cargar(sincronizado)
        }

        // Vista previa en vivo
        listOf(
            b.etCabecera, b.etEjecutante, b.etRuc, b.etDireccion, b.etTelefono, b.etCorreo,
            b.etPaginaWeb, b.etValidez, b.etPlazoEntrega, b.etFormaPago, b.etGarantia, b.etNotas,
        ).forEach { it.doAfterTextChanged { actualizarVistaPrevia() } }

        b.btnCerrarRequisitos.setOnClickListener { dialog.dismiss() }
        b.btnCancelarRequisitos.setOnClickListener { dialog.dismiss() }
        b.btnGuardarRequisitos.setOnClickListener {
            val datos = leer()
            if (datos.ejecutante.isBlank()) {
                b.etEjecutante.error = "Requerido"
                return@setOnClickListener
            }
            if (datos.telefono.isNotBlank() && !Regex("^\\d{9}$").matches(datos.telefono)) {
                b.tilTelefono.error = "El teléfono debe tener 9 dígitos"
                return@setOnClickListener
            } else {
                b.tilTelefono.error = null
            }
            if (datos.ruc.isNotBlank() && datos.ruc.length != 11) {
                b.etRuc.error = "El RUC tiene 11 dígitos"
                return@setOnClickListener
            }
            val appContext = context.applicationContext
            b.btnGuardarRequisitos.isEnabled = false
            fragment.lifecycleScope.launch {
                val error = repo.guardar(datos)
                val msg = if (error == null) "Requisitos guardados ✅" else "Guardado en el teléfono. La nube respondió: $error"
                Toast.makeText(appContext, msg, if (error == null) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun cargar(r: RequisitosDocumento) {
        b.etCabecera.setText(r.cabecera)
        b.etEjecutante.setText(r.ejecutante)
        b.etRuc.setText(r.ruc)
        b.etDireccion.setText(r.direccion)
        b.etTelefono.setText(r.telefono)
        b.etCorreo.setText(r.correo)
        b.etPaginaWeb.setText(r.paginaWeb)
        b.etValidez.setText(r.validezDias)
        b.etPlazoEntrega.setText(r.plazoEntrega)
        b.etFormaPago.setText(r.formaPago)
        b.etGarantia.setText(r.garantia)
        b.etNotas.setText(r.notas)
        actualizarVistaPrevia()
    }

    private fun texto(e: android.widget.EditText) = e.text?.toString()?.trim() ?: ""

    private fun leer() = RequisitosDocumento(
        cabecera = texto(b.etCabecera),
        ejecutante = texto(b.etEjecutante),
        ruc = texto(b.etRuc),
        direccion = texto(b.etDireccion),
        telefono = texto(b.etTelefono),
        correo = texto(b.etCorreo),
        paginaWeb = texto(b.etPaginaWeb),
        validezDias = texto(b.etValidez),
        plazoEntrega = texto(b.etPlazoEntrega),
        formaPago = texto(b.etFormaPago),
        garantia = texto(b.etGarantia),
        notas = texto(b.etNotas),
    )

    private fun actualizarVistaPrevia() {
        val r = leer()
        b.tvPrevEjecutante.text = r.ejecutante.ifBlank { "Nombre de la empresa" }
        b.tvPrevRuc.text = listOf(
            r.ruc.takeIf { it.isNotBlank() }?.let { "RUC $it" },
            r.direccion.takeIf { it.isNotBlank() },
        ).filterNotNull().joinToString(" · ").ifBlank { "RUC · Dirección" }
        b.tvPrevContacto.text = listOf(
            r.telefono.takeIf { it.isNotBlank() }?.let { "Tel. $it" },
            r.correo.takeIf { it.isNotBlank() },
            r.paginaWeb.takeIf { it.isNotBlank() },
        ).filterNotNull().joinToString("  ·  ").ifBlank { "Teléfono · Correo · Página web" }
        b.tvPrevCabecera.text = r.cabecera.ifBlank { "Oferta comercial" }
        b.tvPrevCondiciones.text = buildString {
            if (r.validezDias.isNotBlank()) append("• Validez de la oferta: ${r.validezDias} días\n")
            if (r.plazoEntrega.isNotBlank()) append("• Plazo de entrega: ${r.plazoEntrega}\n")
            if (r.formaPago.isNotBlank()) append("• Forma de pago: ${r.formaPago}\n")
            if (r.garantia.isNotBlank()) append("• Garantía: ${r.garantia}\n")
            if (r.notas.isNotBlank()) append("• ${r.notas}")
        }.trimEnd()
    }
}
