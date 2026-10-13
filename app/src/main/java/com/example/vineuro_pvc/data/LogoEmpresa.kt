package com.example.vineuro_pvc.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * Logo de la empresa. Como en el original ("sp_logo_path"), el usuario puede elegir su propia imagen;
 * si no hay una, se usa el logo de VINEURO PVC incluido en la app. Se guarda una copia reducida en
 * el almacenamiento interno (solo local, no se sube a la nube).
 */
object LogoEmpresa {
    private const val ARCHIVO = "logo_empresa.png"
    private const val LADO_MAX = 800

    private fun archivo(ctx: Context) = File(ctx.filesDir, ARCHIVO)

    fun personalizado(ctx: Context): Boolean = archivo(ctx).exists()

    /** Logo vigente: el elegido por el usuario o el de la app. */
    fun cargar(ctx: Context): Bitmap {
        if (personalizado(ctx)) {
            runCatching { BitmapFactory.decodeFile(archivo(ctx).absolutePath) }.getOrNull()?.let { return it }
        }
        return BitmapFactory.decodeResource(ctx.resources, com.example.vineuro_pvc.R.drawable.logo_vineuro)
    }

    /** Copia la imagen elegida (reducida a [LADO_MAX] px). Devuelve false si no se pudo leer. */
    fun guardar(ctx: Context, uri: Uri): Boolean = runCatching {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
        var muestra = 1
        while (limites.outWidth / muestra > LADO_MAX * 2 || limites.outHeight / muestra > LADO_MAX * 2) muestra *= 2
        val bmp = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = muestra })
        } ?: return false
        val k = minOf(1f, LADO_MAX.toFloat() / maxOf(bmp.width, bmp.height))
        val final = if (k < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true) else bmp
        archivo(ctx).outputStream().use { final.compress(Bitmap.CompressFormat.PNG, 100, it) }
        true
    }.getOrDefault(false)

    fun quitar(ctx: Context) { archivo(ctx).delete() }
}
