package com.example.vineuro_pvc

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.vineuro_pvc.data.WindowConfig
import com.example.vineuro_pvc.databinding.ItemWindowConfigBinding

class WindowConfigAdapter(
    private val configurations: List<WindowConfig>,
    private var selectedId: String = "6",
    private val onConfigSelected: (WindowConfig) -> Unit,
) : RecyclerView.Adapter<WindowConfigAdapter.ConfigViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConfigViewHolder {
        val binding = ItemWindowConfigBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ConfigViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ConfigViewHolder, position: Int) {
        holder.bind(configurations[position])
    }

    override fun getItemCount(): Int = configurations.size

    inner class ConfigViewHolder(private val binding: ItemWindowConfigBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(config: WindowConfig) {
            binding.wpvConfigPreview.visibility = View.VISIBLE
            binding.ivConfigIcon.visibility = View.GONE
            binding.wpvConfigPreview.setConfig(config)
            binding.tvConfigNombre.text = config.nombre

            val isSelected = config.id == selectedId
            if (isSelected) {
                binding.cardItem.strokeColor = Color.parseColor("#42A5F5")
                binding.cardItem.strokeWidth = 6
            } else {
                binding.cardItem.strokeWidth = 0
            }

            binding.root.setOnClickListener {
                selectedId = config.id
                notifyDataSetChanged()
                onConfigSelected(config)
            }
        }
    }
}
