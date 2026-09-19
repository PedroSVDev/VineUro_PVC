package com.example.vineuro_pvc

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.TableRow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.vineuro_pvc.databinding.FragmentPreciosBinding
import java.util.Locale

class PreciosFragment : Fragment() {

    private var _binding: FragmentPreciosBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPreciosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        addInitialData()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        // Botones de añadir
        binding.btnAddPlanes.setOnClickListener { addRow(binding.tablePlanes, listOf("new_finance", "0", "0", "0"), true, colorType = 3) }
        binding.btnAddVentanas.setOnClickListener { addRow(binding.tableVentanas, List(10) { "0" }, true, colorType = 4) }
        binding.btnAddCristal.setOnClickListener { addRow(binding.tableCristal, List(10) { "0" }, true, colorType = 5) }
        binding.btnAddUmbralExt.setOnClickListener { addRow(binding.tableUmbralExt, listOf("new_sill", "0", "0"), true, colorType = 6) }
        binding.btnAddUmbralInt.setOnClickListener { addRow(binding.tableUmbralInt, listOf("new_sill", "0", "0"), true, colorType = 7) }
        binding.btnAddMuebles.setOnClickListener { addRow(binding.tableMuebles, listOf("new_furniture", "0", "0", "0"), true, colorType = 8) }
        binding.btnAddExtras.setOnClickListener { addSelectionRow(binding.tableExtras, "new_extra", "0", true) }
        binding.btnAddEmpleos.setOnClickListener { addSelectionRow(binding.tableEmpleos, "new_job", "0", true) }
        binding.btnAddMosquitera.setOnClickListener { addRow(binding.tableMosquitera, List(7) { "0" }, true, colorType = 11) }
        binding.btnAddLaminacion.setOnClickListener { addRow(binding.tableLaminacion, listOf("new_lam", "0"), true, colorType = 12) }
        binding.btnAddColors.setOnClickListener { addColorRow("new_color", Color.WHITE) }
    }

    private fun addInitialData() {
        // Moneda e Impuesto (Sin botón de añadir)
        addRow(binding.tableMoneda, listOf("€", "1.00", "€"), false, 1)
        addRow(binding.tableImpuesto, listOf("IVA", "0"), false, 2)

        // Iniciales ejemplos
        addRow(binding.tablePlanes, listOf("discount 1%", "1", "0", "0"), true, 3)
        addRow(binding.tableVentanas, listOf("PVC system A", "5.67", "5.58", "4.81", "0", "0", "1.67", "0.28", "0", "0"), true, 4)
        
        addSelectionRow(binding.tableExtras, "mounting foam", "12.30", true)
        addSelectionRow(binding.tableEmpleos, "delivery", "6.00", true)

        addColorRow("alder", Color.parseColor("#C49B7A"))
    }

    private fun addRow(table: ViewGroup, data: List<String>, hasDelete: Boolean, colorType: Int) {
        val row = TableRow(requireContext())
        val isEven = table.childCount % 2 == 0
        row.setBackgroundColor(if (isEven) Color.parseColor("#F9F9F9") else Color.WHITE)

        val bgColor = when(colorType) {
            3 -> Color.parseColor("#FF69B4") // Rosa
            4 -> Color.parseColor("#FFA500") // Naranja
            5 -> Color.parseColor("#1E90FF") // Azul
            else -> Color.TRANSPARENT
        }

        data.forEach { text ->
            val cell = TextView(requireContext()).apply {
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(20, 20, 20, 20)
                this.text = text
                gravity = android.view.Gravity.CENTER
                setTextColor(Color.BLACK)
                if (bgColor != Color.TRANSPARENT) setBackgroundColor(bgColor)
                setOnClickListener { if (text.any { it.isLetter() }) showTextKeypad(this) else showNumericKeypad(this) }
            }
            row.addView(cell)
        }

        if (hasDelete) {
            val deleteCell = TextView(requireContext()).apply {
                text = "X"
                setPadding(20, 20, 20, 20)
                setTextColor(Color.parseColor("#CE3B75"))
                gravity = android.view.Gravity.CENTER
                setOnClickListener { showDeleteConfirmation(table, row, data[0]) }
            }
            row.addView(deleteCell)
        }
        table.addView(row)
    }

    private fun addSelectionRow(table: ViewGroup, name: String, price: String, hasDelete: Boolean) {
        val row = TableRow(requireContext())
        row.setBackgroundColor(Color.parseColor("#F9F9F9"))

        // Nombre
        row.addView(TextView(requireContext()).apply {
            text = name
            setPadding(20, 20, 20, 20)
            setOnClickListener { showTextKeypad(this) }
        })
        // Precio
        row.addView(TextView(requireContext()).apply {
            text = price
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor("#C5E1A5"))
            setOnClickListener { showNumericKeypad(this) }
        })
        // Botones de selección (pc, m2, m...)
        for (i in 0 until 6) {
            val box = View(requireContext()).apply {
                val size = dpToPx(24)
                layoutParams = TableRow.LayoutParams(size, size).apply { setMargins(10, 10, 10, 10) }
                setBackgroundResource(if (i < 2) R.drawable.bg_selection_circle else R.drawable.bg_selection_box)
                setOnClickListener { 
                    it.isSelected = !it.isSelected
                }
            }
            row.addView(box)
        }

        if (hasDelete) {
            row.addView(TextView(requireContext()).apply {
                text = "X"
                setPadding(20, 20, 20, 20)
                setOnClickListener { showDeleteConfirmation(table, row, name) }
            })
        }
        table.addView(row)
    }

    private fun addColorRow(name: String, colorValue: Int) {
        val table = binding.tableColors
        val row = TableRow(requireContext())
        
        row.addView(TextView(requireContext()).apply {
            text = name
            setPadding(20, 20, 20, 20)
            setOnClickListener { showTextKeypad(this) }
        })
        
        val colorCell = View(requireContext()).apply {
            layoutParams = TableRow.LayoutParams(dpToPx(80), dpToPx(40)).apply { setMargins(10, 10, 10, 10) }
            setBackgroundColor(colorValue)
            setOnClickListener { showColorPicker(this) }
        }
        row.addView(colorCell)

        row.addView(TextView(requireContext()).apply {
            text = "X"
            setPadding(20, 20, 20, 20)
            setOnClickListener { showDeleteConfirmation(table, row, name) }
        })

        table.addView(row)
    }

    private fun showNumericKeypad(target: TextView) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_keypad_number, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val display = dialogView.findViewById<TextView>(R.id.tvKeypadValue)
        display.text = target.text

        val listener = View.OnClickListener { v ->
            if (v is Button) {
                val current = display.text.toString()
                if (v.text == "." && current.contains(".")) return@OnClickListener
                display.text = if (current == "0" && v.text != ".") v.text else current + v.text
            }
        }

        listOf(R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9, R.id.btnDot).forEach {
            dialogView.findViewById<Button>(it).setOnClickListener(listener)
        }

        dialogView.findViewById<View>(R.id.btnKeypadDelete).setOnClickListener {
            val s = display.text.toString()
            display.text = if (s.length > 1) s.dropLast(1) else "0"
        }
        dialogView.findViewById<View>(R.id.btnKeypadClear).setOnClickListener { display.text = "0" }
        dialogView.findViewById<View>(R.id.btnKeypadDone).setOnClickListener {
            target.text = formatDecimal(display.text.toString())
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showTextKeypad(target: TextView) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_keypad_text, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val input = dialogView.findViewById<EditText>(R.id.etKeypadValue)
        input.setText(target.text)

        dialogView.findViewById<View>(R.id.btnKeypadDone).setOnClickListener {
            target.text = input.text.toString()
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.btnKeypadClose).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showColorPicker(target: View) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        val grid = dialogView.findViewById<GridLayout>(R.id.gridColors)
        val colors = listOf(Color.WHITE, Color.GRAY, Color.BLACK, Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN)
        
        var selectedColor = (target.background as? ColorDrawable)?.color ?: Color.WHITE

        colors.forEach { color ->
            val v = View(requireContext()).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = dpToPx(40); height = dpToPx(40); setMargins(4, 4, 4, 4)
                }
                setBackgroundColor(color)
                setOnClickListener { selectedColor = color }
            }
            grid.addView(v)
        }

        dialogView.findViewById<View>(R.id.btnColorYes).setOnClickListener {
            target.setBackgroundColor(selectedColor)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.btnColorNo).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showDeleteConfirmation(parent: ViewGroup, row: View, itemName: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_delete, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        dialogView.findViewById<TextView>(R.id.tvConfirmMessage).text = "eliminar el ($itemName) A partir del tabe?"
        dialogView.findViewById<View>(R.id.btnAccept).setOnClickListener {
            parent.removeView(row)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun formatDecimal(input: String): String {
        return try {
            val value = input.toDouble()
            String.format(Locale.US, "%.2f", value)
        } catch (e: Exception) { input }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
