package com.example.vineuro_pvc

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.vineuro_pvc.databinding.ItemAperturaCeldaBinding
import com.example.vineuro_pvc.views.AperturaOriginalDrawable

data class AperturaMechanism(
    val id: String,
    val nombre: String,
    /** Ya no se usa: los iconos se dibujan por código igual que la original ([AperturaOriginalDrawable]). */
    val iconRes: Int = 0,
    val icono: (() -> android.graphics.drawable.Drawable)? = null,
)

/**
 * Rejilla "apertura" idéntica a la de la original (include__leafs_types): sin títulos, en bloques
 * contiguos de 3 columnas (básicas + hoja fija centrada), 2 (francesas), 4 (corredizas verticales) y 2 (corredizas).
 * Todas las filas miden lo mismo (0,395 × ancho de la rejilla) y la opción elegida se resalta con fondo negro (#111111).
 */
class AperturaAdapter(
    private val mechanisms: List<AperturaMechanism>,
    private var selectedId: String = "5",
    private val onMechanismSelected: (AperturaMechanism) -> Unit,
) : RecyclerView.Adapter<AperturaAdapter.CeldaVH>() {

    companion object {
        /** mcm(3, 2, 4) columnas: así cada bloque reparte su ancho con spans enteros (4, 6, 3, 6). */
        const val SPANS = 12
        /** Alto de fila = 7,5 / 19 del ancho de la rejilla (proporción 1:7,5 con 19 filas de la original). */
        private const val ALTO_FILA = 7.5f / 19f
    }

    class Celda(val m: AperturaMechanism?, val span: Int)

    private val celdas: List<Celda> = buildList {
        val basicas = mechanisms.filter { it.id.getOrNull(1) != ':' }
        basicas.forEachIndexed { i, m ->
            // la hoja fija (última) queda sola en el centro de la última fila, como en la original
            if (i == 9) add(Celda(null, 4))
            add(Celda(m, 4))
            if (i == 9) add(Celda(null, 4))
        }
        mechanisms.filter { it.id.startsWith("F:") }.forEach { add(Celda(it, 6)) }
        mechanisms.filter { it.id.startsWith("V:") }.forEach { add(Celda(it, 3)) }
        mechanisms.filter { it.id.startsWith("S:") }.forEach { add(Celda(it, 6)) }
    }

    private var recycler: RecyclerView? = null

    fun spanSizeLookup() = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int) = celdas[position].span
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) { recycler = recyclerView }
    override fun getItemCount() = celdas.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        CeldaVH(ItemAperturaCeldaBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: CeldaVH, position: Int) = holder.bind(celdas[position])

    inner class CeldaVH(private val b: ItemAperturaCeldaBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(c: Celda) {
            val rv = recycler
            val ancho = if (rv != null && rv.width > 0) rv.width - rv.paddingLeft - rv.paddingRight
            else (b.root.resources.displayMetrics.density * 260).toInt()
            b.root.layoutParams = (b.root.layoutParams ?: ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)).apply {
                height = (ancho * ALTO_FILA).toInt()
            }
            val m = c.m
            if (m == null) {
                b.ivIcono.visibility = View.INVISIBLE
                b.flItem.setBackgroundColor(Color.TRANSPARENT)
                b.root.setOnClickListener(null)
                return
            }
            b.ivIcono.visibility = View.VISIBLE
            val dens = b.root.resources.displayMetrics.density
            b.ivIcono.setImageDrawable(m.icono?.invoke() ?: AperturaOriginalDrawable(m.id, dens))
            b.ivIcono.contentDescription = m.nombre
            b.flItem.setBackgroundColor(if (m.id == selectedId) 0xFF111111.toInt() else Color.TRANSPARENT)
            b.root.setOnClickListener {
                selectedId = m.id
                notifyDataSetChanged()
                onMechanismSelected(m)
            }
        }
    }
}
