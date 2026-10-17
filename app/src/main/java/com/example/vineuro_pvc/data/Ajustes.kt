package com.example.vineuro_pvc.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Ajustes de la pantalla de inicio: estilo (tema) e idioma. Las unidades están en [Unidades]. */
object Ajustes {
    private const val PREFS = "pref_ui"
    private const val KEY_TEMA = "tema" // 0 = sistema, 1 = claro, 2 = oscuro

    fun tema(ctx: Context): Int = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_TEMA, 0)

    fun guardarTema(ctx: Context, tema: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_TEMA, tema).apply()
        aplicarTema(ctx)
    }

    fun aplicarTema(ctx: Context) {
        AppCompatDelegate.setDefaultNightMode(
            when (tema(ctx)) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            },
        )
    }

    /** "" = idioma del sistema; "es" o "en". */
    fun idioma(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore("-")

    fun guardarIdioma(tag: String) {
        AppCompatDelegate.setApplicationLocales(if (tag.isBlank()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))
    }
}
