package com.example.vineuro_pvc

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.vineuro_pvc.data.WindowConfig
import com.example.vineuro_pvc.databinding.ItemWindowConfigBinding

/**
 * Una opción de apertura.
 * - Simple: [id] = "1".."11", se aplica a todo el paño.
 * - Combinada: [partes] = "1,3" (una apertura por cada parte de la imposta).
 */
data class AperturaMechanism(
    val id: String,
    val nombre: String,
    val iconRes: Int,
    val partes: String? = null,
)

class AperturaAdapter(
    private var mechanisms: List<AperturaMechanism>,
    private var selectedId: String = "5",
    private var filas: Int = 1,
    private var columnas: Int = 1,
    private val onMechanismSelected: (AperturaMechanism) -> Unit,
) : RecyclerView.Adapter<AperturaAdapter.AperturaViewHolder>() {

    /** Partes con panel sándwich (bit por parte) que se muestran en blanco en las vistas previas. */
    var mascaraSandwich: Int = 0

    /** Barrotillos a dibujar en las vistas previas (filas*100 + columnas; 0 = sin barrotillos). */
    var barras: Int = 0

    @SuppressLint("NotifyDataSetChanged")
    fun refrescar() = notifyDataSetChanged()

    @SuppressLint("NotifyDataSetChanged")
    fun actualizar(nuevas: List<AperturaMechanism>, nuevasFilas: Int, nuevasColumnas: Int, nuevoSeleccionado: String) {
        mechanisms = nuevas
        filas = nuevasFilas
        columnas = nuevasColumnas
        selectedId = nuevoSeleccionado
        notifyDataSetChanged()
    }

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

        @SuppressLint("NotifyDataSetChanged")
        fun bind(mechanism: AperturaMechanism) {
            // Vista previa dibujada: un paño con la división actual y su apertura
            val preview = binding.wpvConfigPreview
            preview.visibility = View.VISIBLE
            binding.ivConfigIcon.visibility = View.GONE
            preview.setConfig(
                WindowConfig("prev_apertura", "", "Estándar", WindowConfig.LayoutType.GRID, cols = 1, rows = 1),
                isTechnicalMode = false,
            )
            preview.paneRows[0] = filas
            preview.paneCols[0] = columnas
            preview.paneSandwichMask[0] = mascaraSandwich
            preview.paneBarras[0] = barras
            if (mechanism.partes != null) {
                preview.setPanePartMechanisms(0, mechanism.partes)
            } else {
                preview.setPaneMechanism(0, mechanism.id)
            }
            preview.invalidate()
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
