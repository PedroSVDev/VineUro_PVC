package com.example.vineuro_pvc

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.vineuro_pvc.data.ClientRepository
import com.example.vineuro_pvc.data.Cliente
import com.example.vineuro_pvc.databinding.FragmentClientesGuardadosBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Lista de clientes guardados (teléfono + Supabase). Tocar abre la información del cliente;
 * mantener presionado lo edita (nombre, teléfono, correo) y el tacho de la derecha lo elimina previa confirmación.
 */
class ClientesGuardadosFragment : Fragment() {

    private var _binding: FragmentClientesGuardadosBinding? = null
    private val binding get() = _binding!!

    private lateinit var clientRepository: ClientRepository
    private lateinit var adapter: ClientesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentClientesGuardadosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        clientRepository = ClientRepository(requireContext())

        adapter = ClientesAdapter(
            onEliminarClick = { confirmarEliminar(it) },
            onEditarClick = { editarCliente(it) },
            onClienteClick = { mostrarDetallesCliente(it) },
        )
        binding.rvClientesGuardados.adapter = adapter

        // Búsqueda en tiempo real
        binding.etSearchCliente.doOnTextChanged { _, _, _, _ -> refrescarLista() }

        refrescarLista()

        // Sincronización automática con Supabase
        viewLifecycleOwner.lifecycleScope.launch {
            clientRepository.sincronizarConSupabase()
            if (_binding != null) refrescarLista()
        }
    }

    private fun textoBusqueda(): String = binding.etSearchCliente.text?.toString() ?: ""

    private fun refrescarLista() {
        val lista = clientRepository.buscarClientes(textoBusqueda())
        val vacio = lista.isEmpty()
        binding.rvClientesGuardados.visibility = if (vacio) View.GONE else View.VISIBLE
        binding.layoutEmptyState.visibility = if (vacio) View.VISIBLE else View.GONE
        adapter.submitList(lista)
    }

    private fun confirmarEliminar(cliente: Cliente) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar cliente")
            .setMessage("¿Seguro que quieres borrar a ${cliente.nombreCompleto}?")
            .setIcon(R.drawable.ic_trash)
            .setPositiveButton("SÍ, BORRAR") { dialog, _ ->
                dialog.dismiss()
                eliminar(cliente)
            }
            .setNegativeButton("NO") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    /** Edita los datos básicos del cliente (como "customer edit" de la original). */
    private fun editarCliente(cliente: Cliente) {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        fun campo(hint: String, valor: String, tipo: Int) = android.widget.EditText(ctx).apply {
            this.hint = hint; setText(valor); inputType = tipo; setSingleLine()
        }
        val etNombres = campo("Nombres", cliente.nombres, android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS or android.text.InputType.TYPE_CLASS_TEXT)
        val etApellidos = campo("Apellidos", cliente.apellidos, android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS or android.text.InputType.TYPE_CLASS_TEXT)
        val etTelefono = campo("Teléfono (9 dígitos)", cliente.telefono, android.text.InputType.TYPE_CLASS_PHONE)
        val etEmail = campo("Correo electrónico", cliente.email, android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val caja = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (8 * dp).toInt(), (20 * dp).toInt(), 0)
            listOf(etNombres, etApellidos, etTelefono, etEmail).forEach { addView(it) }
        }
        val dialogo = MaterialAlertDialogBuilder(ctx)
            .setTitle("Editar cliente")
            .setView(caja)
            .setPositiveButton("GUARDAR", null)
            .setNegativeButton("CANCELAR", null)
            .create()
        dialogo.setOnShowListener {
            dialogo.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val nombres = etNombres.text.toString().trim()
                val apellidos = etApellidos.text.toString().trim()
                val telefono = etTelefono.text.toString().trim()
                val email = etEmail.text.toString().trim()
                when {
                    nombres.isEmpty() -> etNombres.error = "Ingrese el nombre"
                    apellidos.isEmpty() -> etApellidos.error = "Ingrese los apellidos"
                    telefono.isNotEmpty() && !Regex("^\\d{9}$").matches(telefono) -> etTelefono.error = "El teléfono debe tener 9 dígitos"
                    else -> {
                        val ok = clientRepository.guardarCliente(
                            cliente.copy(nombres = nombres, apellidos = apellidos, telefono = telefono, email = email),
                        )
                        Toast.makeText(ctx, if (ok) "Cliente actualizado" else "Error al guardar el cliente", Toast.LENGTH_SHORT).show()
                        refrescarLista()
                        dialogo.dismiss()
                    }
                }
            }
        }
        dialogo.show()
    }

    private fun eliminar(cliente: Cliente) {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val enNube = clientRepository.eliminarCliente(cliente.id)
            if (_binding != null) refrescarLista()
            val msg = if (enNube) {
                "Cliente ${cliente.nombreCompleto} eliminado"
            } else {
                "Cliente eliminado del teléfono. Se borrará de la nube cuando haya conexión."
            }
            Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun mostrarDetallesCliente(cliente: Cliente) {
        val bundle = Bundle().apply { putString("cliente_id", cliente.id) }
        findNavController().navigate(
            R.id.action_ClientesGuardadosFragment_to_DetallePedidoClienteFragment,
            bundle,
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
