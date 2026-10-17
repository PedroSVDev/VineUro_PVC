package com.example.vineuro_pvc

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.vineuro_pvc.data.WindowConfig
import com.example.vineuro_pvc.databinding.ItemModeloVentanaBinding

/** Rejilla del selector de modelos: solo el dibujo de cada ventana; el elegido se resalta con fondo azul. */
class WindowConfigAdapter(
    private val configurations: List<WindowConfig>,
    private var selectedId: String = "",
    private val onConfigSelected: (WindowConfig) -> Unit,
) : RecyclerView.Adapter<WindowConfigAdapter.ConfigViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConfigViewHolder {
        val binding = ItemModeloVentanaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ConfigViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ConfigViewHolder, position: Int) = holder.bind(configurations[position])

    override fun getItemCount(): Int = configurations.size

    inner class ConfigViewHolder(private val binding: ItemModeloVentanaBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(config: WindowConfig) {
            binding.wpvModelo.setConfig(config)
            binding.flModelo.setBackgroundColor(if (config.id == selectedId) Color.parseColor("#55AAFF") else Color.TRANSPARENT)
            binding.root.contentDescription = config.nombre
            fun elegir() {
                selectedId = config.id
                notifyDataSetChanged()
                onConfigSelected(config)
            }
            binding.root.setOnClickListener { elegir() }
            // El dibujo consume los toques (sirve para editar medidas en la pantalla principal): aquí solo se usa para elegir
            binding.wpvModelo.setOnTouchListener { v, e ->
                if (e.action == android.view.MotionEvent.ACTION_UP) { v.performClick(); elegir() }
                true
            }
        }
    }
}
