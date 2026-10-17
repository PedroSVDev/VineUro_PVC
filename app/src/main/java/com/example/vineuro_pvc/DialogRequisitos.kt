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
 * Ventana "Datos de la empresa": encabezado, contacto, lemas y firma que se imprimen en los PDF.
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
            b.etCabecera, b.etEjecutante, b.etRuc, b.etDireccion, b.etTelefono, b.etTelefonoFijo, b.etCorreo,
            b.etPaginaWeb, b.etFacebook, b.etLema1, b.etLema2, b.etFirmaNombre, b.etFirmaCargo,
        ).forEach { it.doAfterTextChanged { actualizarVistaPrevia() } }

        // Logo propio 
        mostrarLogo()
        b.btnElegirLogo.setOnClickListener { selectorLogo.launch("image/*") }
        b.btnQuitarLogo.setOnClickListener {
            com.example.vineuro_pvc.data.LogoEmpresa.quitar(context)
            mostrarLogo()
        }

        dialog.setOnDismissListener { runCatching { selectorLogo.unregister() } }
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
                val msg = if (error == null) "Datos de la empresa guardados ✅" else "Guardado en el teléfono. La nube respondió: $error"
                Toast.makeText(appContext, msg, if (error == null) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    // Se registra sin LifecycleOwner porque este diálogo se crea con el fragmento ya en pantalla
    private val selectorLogo by lazy {
        fragment.requireActivity().activityResultRegistry.register(
            "logo_empresa_${System.nanoTime()}",
            androidx.activity.result.contract.ActivityResultContracts.GetContent(),
        ) { uri ->
            if (uri == null) return@register
            if (com.example.vineuro_pvc.data.LogoEmpresa.guardar(context, uri)) mostrarLogo()
            else Toast.makeText(context, "No se pudo leer la imagen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun mostrarLogo() {
        if (!::b.isInitialized) return
        b.imgLogoEmpresa.setImageBitmap(com.example.vineuro_pvc.data.LogoEmpresa.cargar(context))
        b.btnQuitarLogo.isEnabled = com.example.vineuro_pvc.data.LogoEmpresa.personalizado(context)
    }

    private fun cargar(r: RequisitosDocumento) {
        base = r
        b.etCabecera.setText(r.cabecera)
        b.etEjecutante.setText(r.ejecutante)
        b.etRuc.setText(r.ruc)
        b.etDireccion.setText(r.direccion)
        b.etTelefono.setText(r.telefono)
        b.etCorreo.setText(r.correo)
        b.etPaginaWeb.setText(r.paginaWeb)
        b.etTelefonoFijo.setText(r.telefonoFijo)
        b.etFacebook.setText(r.facebook)
        b.etLema1.setText(r.lema1)
        b.etLema2.setText(r.lema2)
        b.etFirmaNombre.setText(r.firmaNombre)
        b.etFirmaCargo.setText(r.firmaCargo)
        actualizarVistaPrevia()
    }

    private fun texto(e: android.widget.EditText) = e.text?.toString()?.trim() ?: ""

    /** Copia de lo guardado: conserva los campos que ya no se editan aquí (validez, etc.). */
    private var base: RequisitosDocumento = RequisitosDocumento()

    private fun leer() = base.copy(
        cabecera = texto(b.etCabecera),
        ejecutante = texto(b.etEjecutante),
        ruc = texto(b.etRuc),
        direccion = texto(b.etDireccion),
        telefono = texto(b.etTelefono),
        correo = texto(b.etCorreo),
        paginaWeb = texto(b.etPaginaWeb),
        telefonoFijo = texto(b.etTelefonoFijo),
        facebook = texto(b.etFacebook),
        lema1 = texto(b.etLema1),
        lema2 = texto(b.etLema2),
        firmaNombre = texto(b.etFirmaNombre),
        firmaCargo = texto(b.etFirmaCargo),
    )

    private fun actualizarVistaPrevia() {
        val r = leer()
        b.tvPrevEjecutante.text = r.ejecutante.ifBlank { "Nombre de la empresa" }
        b.tvPrevRuc.text = listOf(
            r.ruc.takeIf { it.isNotBlank() }?.let { "RUC $it" },
            r.direccion.takeIf { it.isNotBlank() },
        ).filterNotNull().joinToString(" · ").ifBlank { "RUC · Dirección" }
        b.tvPrevContacto.text = listOf(
            r.telefono.takeIf { it.isNotBlank() }?.let { "Cel. $it" },
            r.telefonoFijo.takeIf { it.isNotBlank() }?.let { "TF. $it" },
            r.correo.takeIf { it.isNotBlank() },
            r.paginaWeb.takeIf { it.isNotBlank() },
            r.facebook.takeIf { it.isNotBlank() }?.let { "Facebook: $it" },
        ).filterNotNull().joinToString("  ·  ").ifBlank { "Celular · Correo · Página web" }
        b.tvPrevCabecera.text = r.cabecera.ifBlank { "Oferta comercial" }
        b.tvPrevCondiciones.text = buildString {
            if (r.lema1.isNotBlank()) append(r.lema1).append("\n")
            if (r.lema2.isNotBlank()) append(r.lema2).append("\n")
            if (r.firmaNombre.isNotBlank() || r.firmaCargo.isNotBlank()) append("\nFirma: ${r.firmaNombre} — ${r.firmaCargo}")
        }.trimEnd()
    }
}
