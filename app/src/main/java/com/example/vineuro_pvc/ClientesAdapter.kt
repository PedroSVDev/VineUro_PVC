package com.example.vineuro_pvc

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.vineuro_pvc.data.Cliente
import com.example.vineuro_pvc.databinding.ItemClienteBinding

class ClientesAdapter(
    private var listaClientes: List<Cliente> = emptyList(),
    private val onClienteClick: (Cliente) -> Unit,
) : RecyclerView.Adapter<ClientesAdapter.ClienteViewHolder>() {

    fun submitList(newList: List<Cliente>) {
        listaClientes = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClienteViewHolder {
        val binding = ItemClienteBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ClienteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ClienteViewHolder, position: Int) {
        holder.bind(listaClientes[position])
    }

    override fun getItemCount(): Int = listaClientes.size

    inner class ClienteViewHolder(private val binding: ItemClienteBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(cliente: Cliente) {
            val context = binding.root.context
            binding.tvNombreCliente.text = cliente.nombreCompleto
            binding.tvDniCliente.text = context.getString(R.string.label_dni_format, cliente.dni)

            if (cliente.telefono.isNotEmpty()) {
                binding.tvTelefonoCliente.text = context.getString(R.string.label_tel_format, cliente.telefono)
                binding.tvTelefonoCliente.visibility = View.VISIBLE
            } else {
                binding.tvTelefonoCliente.visibility = View.GONE
            }

            if (cliente.email.isNotEmpty()) {
                binding.tvEmailCliente.text = context.getString(R.string.label_email_format, cliente.email)
                binding.tvEmailCliente.visibility = View.VISIBLE
            } else {
                binding.tvEmailCliente.visibility = View.GONE
            }

            val iniciales = buildString {
                if (cliente.nombres.isNotEmpty()) append(cliente.nombres.first().uppercaseChar())
                if (cliente.apellidos.isNotEmpty()) append(cliente.apellidos.first().uppercaseChar())
            }.ifEmpty { "C" }

            binding.tvAvatarInitials.text = iniciales

            binding.root.setOnClickListener {
                onClienteClick(cliente)
            }
        }
    }
}
