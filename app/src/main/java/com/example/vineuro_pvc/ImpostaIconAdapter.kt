package com.example.vineuro_pvc

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.vineuro_pvc.databinding.ItemIconoConfigBinding
import com.example.vineuro_pvc.views.ImpostaIconDrawable

/** Celda del panel "impostas": [imp] 0..4, o [BARRAS] para el botón de glazing bars (primera celda). */
data class ImpostaItem(val imp: Int, val habilitada: Boolean = true) {
    companion object { const val BARRAS = -1 }
}

/** Rejilla de 2 columnas de ventanitas; la seleccionada lleva fondo azul claro (#00A0F0), las no permitidas van al 30 %. */
class ImpostaIconAdapter(
    private var items: List<ImpostaItem>,
    private var seleccion: Int,
    private var mascara: Int,
    private val alElegir: (Int) -> Unit,
    private val alPulsarBarras: () -> Unit,
) : RecyclerView.Adapter<ImpostaIconAdapter.VH>() {

    fun actualizar(nuevos: List<ImpostaItem>, sel: Int, mask: Int) { items = nuevos; seleccion = sel; mascara = mask; notifyDataSetChanged() }
    fun actualizarMascara(mask: Int) { mascara = mask; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemIconoConfigBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(private val b: ItemIconoConfigBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: ImpostaItem) {
            val barras = item.imp == ImpostaItem.BARRAS
            b.ivIcono.setImageDrawable(ImpostaIconDrawable(if (barras) 0 else item.imp, if (barras) 0 else mascara, barras))
            val sel = !barras && item.imp == seleccion
            b.flItem.setBackgroundColor(if (sel) Color.parseColor("#00A0F0") else if (barras) Color.parseColor("#42A5F5") else Color.TRANSPARENT)
            b.flItem.alpha = if (item.habilitada) 1f else 0.3f
            b.flItem.isClickable = item.habilitada
            b.root.setOnClickListener {
                val x = items[bindingAdapterPosition]
                if (!x.habilitada) return@setOnClickListener
                if (x.imp == ImpostaItem.BARRAS) alPulsarBarras() else { seleccion = x.imp; notifyDataSetChanged(); alElegir(x.imp) }
            }
        }
    }
}
