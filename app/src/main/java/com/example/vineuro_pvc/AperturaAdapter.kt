package com.example.vineuro_pvc

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.vineuro_pvc.databinding.ItemWindowConfigBinding

data class AperturaMechanism(
    val id: String,
    val nombre: String,
    val iconRes: Int,
    /** Icono dibujado por código (aperturas múltiples); si es null se usa [iconRes]. */
    val icono: (() -> android.graphics.drawable.Drawable)? = null,
)

class AperturaAdapter(
    private val mechanisms: List<AperturaMechanism>,
    private var selectedId: String = "5",
    private val onMechanismSelected: (AperturaMechanism) -> Unit,
) : RecyclerView.Adapter<AperturaAdapter.AperturaViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AperturaViewHolder {
        val binding = ItemWindowConfigBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return AperturaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AperturaViewHolder, position: Int) {
        holder.bind(mechanisms[position])
    }

    override fun getItemCount(): Int = mechanisms.size

    inner class AperturaViewHolder(private val binding: ItemWindowConfigBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(mechanism: AperturaMechanism) {
            binding.wpvConfigPreview.visibility = View.GONE
            binding.ivConfigIcon.visibility = View.VISIBLE
            val dib = mechanism.icono?.invoke()
            if (dib != null) binding.ivConfigIcon.setImageDrawable(dib) else binding.ivConfigIcon.setImageResource(mechanism.iconRes)
            binding.tvConfigNombre.text = mechanism.nombre

            val isSelected = mechanism.id == selectedId
            if (isSelected) {
                binding.cardItem.strokeColor = Color.BLACK
                binding.cardItem.strokeWidth = 6
            } else {
                binding.cardItem.strokeWidth = 0
            }

            binding.root.setOnClickListener {
                selectedId = mechanism.id
                notifyDataSetChanged()
                onMechanismSelected(mechanism)
            }
        }
    }
}
