package com.example.vineuro_pvc.views

import com.example.vineuro_pvc.data.Unidades
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.example.vineuro_pvc.data.ListadoMateriales
import com.example.vineuro_pvc.data.OptimizadorCorte
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * PDF de la guía de corte (para el taller): resumen, listado de materiales y, por cada perfil, el dibujo de
 * cada varilla con sus piezas en milímetros y el sobrante. Cada pieza lleva el N° de la ventana (V1, V2…).
 */
object GuiaCortePdf {

    private const val W = 595f
    private const val H = 842f
    private const val M = 34f
    private val AZUL = Color.parseColor("#1E3A5F")
    private val CELESTE = Color.parseColor("#BDD7EE")
    private val GRIS = Color.parseColor("#6B7280")
    private val BORDE = Color.parseColor("#9CA3AF")
    private val ROJO = Color.parseColor("#C00000")

    fun crear(
        context: Context, codigo: String, cliente: String, fecha: String,
        plan: OptimizadorCorte.Plan, listado: ListadoMateriales.Listado,
    ): File {
        val pdf = PdfDocument()
        var n = 0
        lateinit var page: PdfDocument.Page
        lateinit var c: Canvas

        fun rect(l: Float, t: Float, r: Float, b: Float, color: Int) {
            c.drawRect(l, t, r, b, Paint().apply { this.color = color; style = Paint.Style.FILL })
        }
        fun texto(t: String, x: Float, y: Float, size: Float, color: Int, negrita: Boolean = false, maxAncho: Float = Float.MAX_VALUE) {
            val pt = pintura(size, color, negrita)
            var s = t
            if (pt.measureText(s) > maxAncho) {
                while (s.length > 1 && pt.measureText("$s…") > maxAncho) s = s.dropLast(1)
                s = "$s…"
            }
            c.drawText(s, x, y, pt)
        }

        fun nueva(titulo: String): Float {
            n++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), n).create())
            c = page.canvas
            rect(M, M, W - M, M + 22f, AZUL)
            texto(titulo, M + 8f, M + 15f, 11f, Color.WHITE, true)
            texto("Pedido $codigo   ·   Cliente: $cliente   ·   $fecha", M, M + 36f, 8.5f, GRIS)
            return M + 48f
        }
        fun cerrar() {
            val p = pintura(7f, BORDE, false)
            val t = "VINEURO PVC  ·  Guía de corte  ·  Página $n"
            c.drawText(t, W - M - p.measureText(t), H - 18f, p)
            pdf.finishPage(page)
        }
        fun f(v: Double) = String.format(Locale.US, "%.1f", v)

        // ---------------- Resumen + materiales ----------------
        var y = nueva("GUÍA DE CORTE Y LISTADO DE MATERIALES")
        val p = plan.parametros
        texto("Varilla de ${p.largoVarillaMm} mm  ·  corte ${p.separacionMm} mm  ·  soldadura +${p.soldaduraMm} mm  ·  retazo útil ≥ ${p.retazoUtilMm} mm",
            M, y + 8f, 8f, GRIS)
        y += 11f
        texto(
            if (p.cortes45) "Cortes: marco, hoja y junquillo a 45° (medida en la punta larga); impostas y barras de vidrio a 90°.  Total: ${plan.cortes45} cortes a 45° · ${plan.cortes90} a 90°"
            else "Cortes: todo a 90° (${plan.cortes90} cortes).",
            M, y + 8f, 7.4f, GRIS, false, W - 2 * M,
        )
        y += 14f
        rect(M, y, W - M, y + 14f, CELESTE)
        texto("PERFILES (varillas a comprar)", M + 4f, y + 10.5f, 8.6f, AZUL, true)
        y += 18f
        val xs = floatArrayOf(M, M + 205f, M + 262f, M + 312f, M + 372f, M + 440f, W - M)
        listOf("Perfil · sistema · color", "Varillas", "Piezas", "Mín. teór.", "Aprov. %", "Desperd. %").forEachIndexed { i, t ->
            texto(t, xs[i] + 2f, y + 8f, 7f, AZUL, true, xs[i + 1] - xs[i] - 3f)
        }
        y += 12f
        for (g in plan.grupos) {
            texto("${g.clave} · ${g.angulo}°", xs[0] + 2f, y + 8f, 7.4f, Color.BLACK, false, xs[1] - xs[0] - 3f)
            texto("${g.varillas.size}", xs[1] + 2f, y + 8f, 7.6f, Color.BLACK, true)
            texto("${g.piezasTotal}", xs[2] + 2f, y + 8f, 7.6f, Color.BLACK)
            texto("${g.minimoTeorico}", xs[3] + 2f, y + 8f, 7.6f, Color.BLACK)
            texto(f(g.aprovechamientoPct), xs[4] + 2f, y + 8f, 7.6f, Color.BLACK)
            texto(f(g.desperdicioPct), xs[5] + 2f, y + 8f, 7.6f, Color.BLACK)
            y += 11f
            if (y > H - 200f) { cerrar(); y = nueva("GUÍA DE CORTE (cont.)") }
        }
        y += 4f
        texto("TOTAL: ${plan.varillasTotal} varillas  ·  aprovechamiento ${f(plan.aprovechamientoPct)} %  ·  desperdicio ${f(plan.desperdicioPct)} %",
            M, y + 9f, 8.6f, Color.BLACK, true)
        y += 22f
        plan.grupos.filter { it.sinCabida.isNotEmpty() }.forEach { g ->
            texto("⚠ ${g.clave}: ${g.sinCabida.size} pieza(s) más largas que la varilla (${g.sinCabida.maxOf { it.largoMm }} mm). Revisar medidas.",
                M, y + 9f, 8f, ROJO, true)
            y += 12f
        }

        rect(M, y, W - M, y + 14f, CELESTE)
        texto("OTROS MATERIALES DEL PEDIDO", M + 4f, y + 10.5f, 8.6f, AZUL, true)
        y += 20f
        texto("Ventanas: ${listado.ventanas}  ·  Unidades: ${listado.unidades}  ·  Área total: ${Unidades.areaM2(listado.areaM2)}", M, y + 8f, 8f, Color.BLACK)
        y += 13f
        texto("Junta de caucho / sellador: ${String.format(Locale.US, "%.2f", listado.cauchoM)} m   ·   Paños con mosquitero: ${listado.mosquiteros}", M, y + 8f, 8f, Color.BLACK)
        y += 13f
        if (listado.hojasPorApertura.isNotEmpty()) {
            texto("Hojas: " + listado.hojasPorApertura.entries.joinToString("  ·  ") { "${it.key}: ${it.value}" }, M, y + 8f, 8f, Color.BLACK, false, W - 2 * M)
            y += 15f
        }
        y += 4f
        texto("VIDRIOS Y PANELES (medida de corte en mm)", M, y + 8f, 8.4f, AZUL, true)
        y += 13f
        val vx = floatArrayOf(M, M + 230f, M + 300f, M + 370f, M + 430f, W - M)
        listOf("Tipo", "Ancho", "Alto", "Cant.", "Área m²").forEachIndexed { i, t -> texto(t, vx[i] + 2f, y + 8f, 7f, AZUL, true) }
        y += 11f
        for (v in listado.vidrios) {
            if (y > H - 50f) { cerrar(); y = nueva("LISTADO DE MATERIALES (cont.)") }
            texto(v.tipo, vx[0] + 2f, y + 8f, 7.4f, Color.BLACK, false, vx[1] - vx[0] - 3f)
            texto("${v.anchoMm}", vx[1] + 2f, y + 8f, 7.4f, Color.BLACK)
            texto("${v.altoMm}", vx[2] + 2f, y + 8f, 7.4f, Color.BLACK)
            texto("${v.cantidad}", vx[3] + 2f, y + 8f, 7.4f, Color.BLACK, true)
            texto(String.format(Locale.US, "%.2f", v.areaM2), vx[4] + 2f, y + 8f, 7.4f, Color.BLACK)
            y += 10f
        }
        y += 4f
        texto("Vidrio: ${Unidades.areaM2(listado.vidrioM2)}   ·   Paneles sándwich: ${Unidades.areaM2(listado.sandwichM2)}", M, y + 8f, 8f, Color.BLACK, true)

        // ---------------- Cortes: todos los perfiles seguidos, sin una hoja por perfil ----------------
        val anchoBarra = W - 2 * M
        val altoBarra = 18f
        val pasoBarra = 38f
        val altoEncabezado = 16f
        y += 14f
        // Si en esta hoja no entra ni el título con un perfil y su primera varilla, sigue en la hoja siguiente
        if (y + 20f + altoEncabezado + pasoBarra > H - 40f) { cerrar(); y = nueva("CORTES POR PERFIL") }
        else {
            rect(M, y, W - M, y + 18f, AZUL)
            texto("CORTES POR PERFIL", M + 8f, y + 12.5f, 10f, Color.WHITE, true)
            y += 22f
        }
        texto(
            if (p.cortes45) "∠45° = inglete en los dos extremos, medida en la punta larga (todas con la punta larga hacia arriba, mismo sentido de corte). Sin ángulo = corte a 90°."
            else "Todas las piezas se cortan a 90°.",
            M, y + 7f, 6.8f, GRIS, false, anchoBarra,
        )
        y += 12f
        val ventanasUsadas = plan.grupos.flatMap { g -> g.varillas.flatMap { v -> v.piezas.map { it.ventana } } }.distinct().sorted()
        var xl = M
        texto("Color = ventana:", xl, y + 7f, 6.8f, GRIS); xl += 52f
        for (nv in ventanasUsadas) {
            if (xl + 24f > W - M) break
            rect(xl, y, xl + 22f, y + 9f, BarraVarillaView.colorVentana(nv))
            texto("V$nv", xl + 3f, y + 7f, 6.4f, Color.BLACK)
            xl += 26f
        }
        y += 16f

        for (g in plan.grupos) {
            if (y + altoEncabezado + pasoBarra > H - 40f) { cerrar(); y = nueva("CORTES POR PERFIL (cont.)") }
            rect(M, y, W - M, y + altoEncabezado - 2f, CELESTE)
            texto("${g.perfil.uppercase(Locale("es", "PE"))}  ·  ${g.sistema}  ·  ${g.color}", M + 4f, y + 9f, 7.6f, AZUL, true, anchoBarra * 0.62f)
            val resumenG = "${g.varillas.size} varilla${if (g.varillas.size == 1) "" else "s"} de ${g.largoVarillaMm} mm  ·  corte a ${g.angulo}°"
            val pr = pintura(7f, AZUL, false)
            c.drawText(resumenG, W - M - 4f - pr.measureText(resumenG), y + 9f, pr)
            y += altoEncabezado + 2f
            g.varillas.forEachIndexed { idx, v ->
                if (y + pasoBarra > H - 40f) {
                    cerrar(); y = nueva("CORTES POR PERFIL (cont.)")
                    texto("${g.perfil.uppercase(Locale("es", "PE"))}  ·  ${g.sistema}  ·  ${g.color}  (cont.)", M, y + 7f, 7.2f, AZUL, true)
                    y += 11f
                }
                texto("Varilla ${idx + 1}  ·  usado ${v.usadoMm} mm  ·  sobrante ${v.sobranteMm} mm" + if (v.sobranteMm >= g.retazoUtilMm) "  (retazo útil)" else "",
                    M, y + 7f, 7f, Color.BLACK, true)
                val top = y + 10f
                val esc = anchoBarra / v.largoMm
                var x = M
                v.piezas.forEachIndexed { i, pz ->
                    val w = pz.largoMm * esc
                    val rel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BarraVarillaView.colorVentana(pz.ventana); style = Paint.Style.FILL }
                    if (pz.angulo == 45) {
                        val sl = minOf(altoBarra * 0.5f, w * 0.3f)
                        val tr = android.graphics.Path().apply {
                            if (true) { moveTo(x, top); lineTo(x + w, top); lineTo(x + w - sl, top + altoBarra); lineTo(x + sl, top + altoBarra) }
                            else { moveTo(x + sl, top); lineTo(x + w - sl, top); lineTo(x + w, top + altoBarra); lineTo(x, top + altoBarra) }
                            close()
                        }
                        c.drawPath(tr, rel); c.drawPath(tr, contorno())
                    } else {
                        c.drawRect(x, top, x + w, top + altoBarra, rel); c.drawRect(RectF(x, top, x + w, top + altoBarra), contorno())
                    }
                    if (w > 24f) texto("${pz.largoMm}", x + 3f, top + 8f, 6.8f, Color.BLACK, true, w - 5f)
                    if (w > 18f) texto("V${pz.ventana}", x + 3f, top + 15.5f, 5.8f, GRIS, false, w - 5f)
                    x += w + p.separacionMm * esc
                }
                c.drawRect(RectF(M, top, M + anchoBarra, top + altoBarra), contorno())
                val lista = v.piezas.joinToString("  +  ") { "${it.largoMm}${if (it.angulo == 45) " ∠45°" else ""} (V${it.ventana}${if (it.copia > 1) "·${it.copia}" else ""})" }
                texto(lista, M, top + altoBarra + 8f, 6.6f, GRIS, false, anchoBarra)
                y += pasoBarra
            }
            y += 4f
        }
        cerrar()

        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val archivo = File(dir, "GuiaCorte_${codigo.ifBlank { "SN" }}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(archivo).use { pdf.writeTo(it) }
        pdf.close()
        return archivo
    }

    private fun contorno() = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#6B7280"); style = Paint.Style.STROKE; strokeWidth = 0.6f }

    private fun pintura(size: Float, color: Int, negrita: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color
        typeface = Typeface.create(Typeface.SANS_SERIF, if (negrita) Typeface.BOLD else Typeface.NORMAL)
    }
}
