package com.example.vineuro_pvc

import android.graphics.Color
import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.vineuro_pvc.data.GeminiClient
import com.example.vineuro_pvc.data.GeminiConfig
import com.example.vineuro_pvc.databinding.DialogConfigurarIaBinding
import kotlinx.coroutines.launch

/** Configuración de la IA: aquí se pega la clave de Gemini (se guarda solo en este teléfono) y se prueba. */
class DialogGemini(private val fragment: Fragment, private val alCerrar: () -> Unit) {

    fun mostrar() {
        val ctx = fragment.requireContext()
        val b = DialogConfigurarIaBinding.inflate(LayoutInflater.from(ctx))
        b.etModeloIa.setText(GeminiConfig.modelo(ctx))
        b.etClaveIa.hint = if (GeminiConfig.clave(ctx).isBlank()) "Clave de API (AIza…)" else "Clave guardada · pega otra para cambiarla"
        b.tvEstadoConfigIa.text = GeminiClient.estado(ctx).texto

        fun guardar() {
            val nueva = b.etClaveIa.text.toString().trim().ifBlank { GeminiConfig.clave(ctx) }
            GeminiConfig.guardar(ctx, nueva, b.etModeloIa.text.toString())
        }

        val dialog = crearDialogoApp(fragment, b.root, altoCompleto = false)
        dialog.setOnCancelListener { alCerrar() }
        b.btnCerrarConfigIa.setOnClickListener { dialog.cancel() }
        b.btnGuardarIa.setOnClickListener { guardar(); dialog.dismiss(); alCerrar() }
        b.btnQuitarClaveIa.setOnClickListener {
            GeminiConfig.guardar(ctx, "", b.etModeloIa.text.toString())
            dialog.dismiss(); alCerrar()
        }
        b.btnProbarIa.setOnClickListener {
            guardar()
            b.tvEstadoConfigIa.setTextColor(Color.parseColor("#6B7280")); b.tvEstadoConfigIa.text = "Probando…"
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                val fallo = GeminiClient.probar(ctx)
                if (fallo == null) {
                    b.tvEstadoConfigIa.setTextColor(Color.parseColor("#15803D"))
                    b.tvEstadoConfigIa.text = "✔ Clave válida: la IA responde con ${GeminiConfig.modelo(ctx)}."
                } else {
                    b.tvEstadoConfigIa.setTextColor(Color.parseColor("#B45309"))
                    b.tvEstadoConfigIa.text = "✖ ${fallo.mensaje}"
                }
            }
        }
        dialog.show()
    }
}
