package com.example.vineuro_pvc

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.vineuro_pvc.data.TerminosCondiciones
import com.example.vineuro_pvc.data.TerminosRepository
import com.example.vineuro_pvc.data.VariablesOferta
import com.example.vineuro_pvc.databinding.DialogTerminosCondicionesBinding
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Plantilla general de "Términos y condiciones": la edita el titular una sola vez.
 * Cada oferta comercial copia esta plantilla y la puede ajustar solo para esa cotización.
 */
class DialogTerminos(private val fragment: Fragment) {

    private val context = fragment.requireContext()
    private val repo = TerminosRepository(context)
    private lateinit var b: DialogTerminosCondicionesBinding
    private lateinit var editor: EditorClausulas

    fun mostrar() {
        b = DialogTerminosCondicionesBinding.inflate(LayoutInflater.from(context))
        editor = EditorClausulas(b.contenedorClausulas)

        val dialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(b.root)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                val dm = context.resources.displayMetrics
                setLayout((dm.widthPixels * 0.96f).toInt(), (dm.heightPixels * 0.92f).toInt())
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
        }

        cargar(repo.plantilla())
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val antes = repo.plantilla().fechaActualizacion
            val nube = repo.sincronizarPlantilla()
            if (dialog.isShowing && nube.fechaActualizacion > antes) cargar(nube)
        }

        llenarChipsVariables(b.chipsVariables) { variable ->
            if (!editor.insertarEnCursor(variable)) {
                Toast.makeText(context, "Primero toca el texto de una cláusula", Toast.LENGTH_SHORT).show()
            }
        }

        b.btnAgregarClausula.setOnClickListener { editor.agregarNueva() }
        b.btnRestaurarClausulas.setOnClickListener {
            MaterialAlertDialogBuilder(context)
                .setTitle("¿Restaurar las cláusulas originales?")
                .setMessage("Se reemplazarán las cláusulas actuales por las de VINEURO PVC. No se guarda hasta presionar Guardar.")
                .setPositiveButton("RESTAURAR") { d, _ -> editor.cargar(TerminosCondiciones.porDefecto()); d.dismiss() }
                .setNegativeButton("CANCELAR", null)
                .show()
        }
        b.btnCerrarTerminos.setOnClickListener { dialog.dismiss() }
        b.btnCancelarTerminos.setOnClickListener { dialog.dismiss() }
        b.btnGuardarTerminos.setOnClickListener {
            val clausulas = editor.leer()
            if (clausulas.isEmpty()) {
                Toast.makeText(context, "Agrega al menos una cláusula", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val datos = TerminosCondiciones(
                clausulas = clausulas,
                vigenciaDias = b.etVigenciaDias.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 3,
                adelantoPct = b.etAdelantoPct.text.toString().toIntOrNull()?.coerceIn(0, 100) ?: 50,
            )
            val appContext = context.applicationContext
            fragment.lifecycleScope.launch {
                val error = repo.guardarPlantilla(datos)
                val msg = if (error == null) "Términos y condiciones guardados ✅" else "Guardado en el teléfono. La nube respondió: $error"
                Toast.makeText(appContext, msg, Toast.LENGTH_LONG).show()
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun cargar(t: TerminosCondiciones) {
        b.etVigenciaDias.setText(t.vigenciaDias.toString())
        b.etAdelantoPct.setText(t.adelantoPct.toString())
        editor.cargar(t.clausulas)
    }

    companion object {
        /** Chips con los campos automáticos; al tocar uno se inserta en la cláusula que tiene el cursor. */
        fun llenarChipsVariables(grupo: ChipGroup, onClick: (String) -> Unit) {
            grupo.removeAllViews()
            VariablesOferta.DISPONIBLES.forEach { (variable, descripcion) ->
                grupo.addView(Chip(grupo.context).apply {
                    text = variable
                    textSize = 12f
                    isFocusable = false
                    isFocusableInTouchMode = false
                    setOnClickListener { onClick(variable) }
                    setOnLongClickListener {
                        Toast.makeText(context, descripcion, Toast.LENGTH_SHORT).show(); true
                    }
                })
            }
        }
    }
}
