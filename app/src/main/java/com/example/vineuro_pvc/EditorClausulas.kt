package com.example.vineuro_pvc

import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.Toast
import com.example.vineuro_pvc.data.Clausula
import com.example.vineuro_pvc.databinding.ItemClausulaBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Lista editable de cláusulas (título + texto) dentro de un LinearLayout.
 * Se usa en la plantilla "Términos y condiciones" y en "Condiciones de esta cotización".
 */
class EditorClausulas(private val contenedor: LinearLayout) {

    private val context = contenedor.context
    private val filas = mutableListOf<ItemClausulaBinding>()

    fun cargar(clausulas: List<Clausula>) {
        contenedor.removeAllViews()
        filas.clear()
        clausulas.forEach { agregarFila(it) }
        renumerar()
    }

    fun leer(): List<Clausula> = filas.map {
        Clausula(it.etTituloClausula.text.toString().trim(), it.etTextoClausula.text.toString().trim())
    }.filter { it.titulo.isNotEmpty() || it.texto.isNotEmpty() }

    /** Agrega una cláusula vacía al final y pone el cursor en su título. */
    fun agregarNueva() {
        val b = agregarFila(Clausula("", ""))
        renumerar()
        b.etTituloClausula.requestFocus()
    }

    /** Inserta un campo automático (p. ej. "{color}") donde esté el cursor de la cláusula que se está editando. */
    fun insertarEnCursor(variable: String): Boolean {
        val fila = filas.firstOrNull { it.etTextoClausula.hasFocus() } ?: return false
        val et = fila.etTextoClausula
        val pos = et.selectionStart.coerceAtLeast(0)
        et.text.insert(pos, variable)
        return true
    }

    private fun agregarFila(c: Clausula): ItemClausulaBinding {
        val b = ItemClausulaBinding.inflate(LayoutInflater.from(context), contenedor, false)
        b.etTituloClausula.setText(c.titulo)
        b.etTextoClausula.setText(c.texto)

        b.btnSubirClausula.setOnClickListener { mover(b, -1) }
        b.btnBajarClausula.setOnClickListener { mover(b, +1) }
        b.btnEliminarClausula.setOnClickListener {
            val nombre = b.etTituloClausula.text.toString().ifBlank { "esta cláusula" }
            MaterialAlertDialogBuilder(context)
                .setTitle("¿Eliminar $nombre?")
                .setPositiveButton("ELIMINAR") { d, _ ->
                    contenedor.removeView(b.root)
                    filas.remove(b)
                    renumerar()
                    d.dismiss()
                }
                .setNegativeButton("CANCELAR", null)
                .show()
        }
        filas.add(b)
        contenedor.addView(b.root)
        return b
    }

    private fun mover(b: ItemClausulaBinding, delta: Int) {
        val i = filas.indexOf(b)
        val j = i + delta
        if (i < 0 || j !in filas.indices) {
            Toast.makeText(context, if (delta < 0) "Ya es la primera" else "Ya es la última", Toast.LENGTH_SHORT).show()
            return
        }
        filas.removeAt(i)
        filas.add(j, b)
        contenedor.removeView(b.root)
        contenedor.addView(b.root, j)
        renumerar()
    }

    private fun renumerar() {
        filas.forEachIndexed { i, b -> b.tvNumeroClausula.text = "${i + 1}" }
    }
}
