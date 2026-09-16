package com.example.vineuro_pvc.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Repositorio de almacenamiento local para Clientes usando SharedPreferences.
 */
class ClientRepository(context: Context) {

    private val prefs = context.getSharedPreferences("pref_clientes", Context.MODE_PRIVATE)

    fun guardarCliente(cliente: Cliente): Boolean {
        val listaActual = obtenerClientes().toMutableList()

        val index = listaActual.indexOfFirst { it.dni == cliente.dni }
        if (index >= 0) {
            listaActual[index] = cliente
        } else {
            listaActual.add(0, cliente)
        }

        return guardarLista(listaActual)
    }

    fun obtenerClientes(): List<Cliente> {
        val jsonString = prefs.getString("lista_clientes", null) ?: return emptyList()
        val clientesList = mutableListOf<Cliente>()

        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val cliente = Cliente(
                    id = obj.optString("id"),
                    dni = obj.optString("dni"),
                    nombres = obj.optString("nombres"),
                    apellidos = obj.optString("apellidos"),
                    telefono = obj.optString("telefono"),
                    email = obj.optString("email"),
                    direccion = obj.optString("direccion"),
                    nota = obj.optString("nota"),
                    fechaRegistro = obj.optLong("fechaRegistro", System.currentTimeMillis()),
                )
                clientesList.add(cliente)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return clientesList
    }

    fun obtenerClientePorDni(dni: String): Cliente? {
        return obtenerClientes().find { it.dni == dni }
    }

    fun buscarClientes(query: String): List<Cliente> {
        val todos = obtenerClientes()
        if (query.isBlank()) return todos

        val queryClean = query.trim().lowercase()
        return todos.filter { cliente ->
            cliente.dni.lowercase().contains(queryClean) ||
                    cliente.nombreCompleto.lowercase().contains(queryClean) ||
                    cliente.telefono.lowercase().contains(queryClean) ||
                    cliente.email.lowercase().contains(queryClean) ||
                    cliente.direccion.lowercase().contains(queryClean)
        }
    }

    fun eliminarCliente(dni: String): Boolean {
        val listaActual = obtenerClientes().filterNot { it.dni == dni }
        return guardarLista(listaActual)
    }

    private fun guardarLista(lista: List<Cliente>): Boolean {
        return try {
            val jsonArray = JSONArray()
            for (cliente in lista) {
                val obj = JSONObject().apply {
                    put("id", cliente.id)
                    put("dni", cliente.dni)
                    put("nombres", cliente.nombres)
                    put("apellidos", cliente.apellidos)
                    put("telefono", cliente.telefono)
                    put("email", cliente.email)
                    put("direccion", cliente.direccion)
                    put("nota", cliente.nota)
                    put("fechaRegistro", cliente.fechaRegistro)
                }
                jsonArray.put(obj)
            }
            prefs.edit { putString("lista_clientes", jsonArray.toString()) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
