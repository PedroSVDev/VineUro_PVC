package com.example.vineuro_pvc.data

/**
 * Configuración de la conexión a Supabase.
 */
object SupabaseConfig {
    const val BASE_URL = "https://ltscngamssxgnmcgqvoi.supabase.co"
    const val REST_URL = "$BASE_URL/rest/v1"
    /** Clave de acceso de la app: las políticas RLS de Supabase la exigen además de la clave pública. Sin inicio de sesión. */
    const val APP_KEY_HEADER = "x-vineuro-key"
    val APP_KEY: String = com.example.vineuro_pvc.BuildConfig.SUPABASE_APP_KEY
    const val API_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imx0c2NuZ2Ftc3N4Z25tY2dxdm9pIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODk2MTM4NjgsImV4cCI6MjEwNTE4OTg2OH0.m-ZZPHT4QKbMlEdCQGd1lbgLNCpoGDUeFJAUbJZfV8A"
}
