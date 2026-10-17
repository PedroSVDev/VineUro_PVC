package com.example.vineuro_pvc.views

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

/**
 * Diálogo "pdf sending": muestra la ruta y el nombre del PDF recién creado y ofrece
 * ENVIAR PDF, ABRIR PDF o CERRAR.
 */
object PdfAcciones {

    fun mostrarDialogo(ctx: Context, archivo: File, asunto: String) {
        MaterialAlertDialogBuilder(ctx)
            .setTitle("PDF creado")
            .setMessage("Archivo:\n${archivo.name}\n\nRuta:\n${archivo.parent}")
            .setPositiveButton("ENVIAR PDF") { _, _ -> enviar(ctx, archivo, asunto) }
            .setNeutralButton("ABRIR PDF") { _, _ -> abrir(ctx, archivo) }
            .setNegativeButton("CERRAR", null)
            .show()
    }

    private fun uri(ctx: Context, archivo: File) =
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", archivo)

    fun enviar(ctx: Context, archivo: File, asunto: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri(ctx, archivo))
            putExtra(Intent.EXTRA_SUBJECT, asunto)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, asunto))
    }

    fun abrir(ctx: Context, archivo: File) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri(ctx, archivo), "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            ctx.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, "No hay ninguna aplicación para abrir PDF. Usa ENVIAR PDF.", Toast.LENGTH_LONG).show()
        }
    }
}
