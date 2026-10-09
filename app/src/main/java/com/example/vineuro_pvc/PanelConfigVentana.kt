package com.example.vineuro_pvc

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import com.example.vineuro_pvc.data.OpcionesVentana
import com.example.vineuro_pvc.data.Precios
import com.example.vineuro_pvc.views.StrokeTextView

/**
 * Menú lateral derecho "configuración de la ventana".
 * Las listas (planes, sistemas, cristal, umbrales, muebles, extras, trabajos, mosquitero,
 * laminaciones y colores) se cargan del apartado de Precios.
 *
 * Se edita una copia de las opciones: solo se aplican al pulsar ACEPTAR.
 */
class PanelConfigVentana(
    private val context: Context,
    private val precios: Precios,
    opcionesIniciales: OpcionesVentana,
    private val onAceptar: (OpcionesVentana) -> Unit,
) {

    private val op: OpcionesVentana = opcionesIniciales.copiar()
    private lateinit var contenedor: LinearLayout

    fun mostrar() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_config_ventana, null)
        contenedor = view.findViewById(R.id.contenedorPanel)

        construirSecciones()

        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)
        dialog.setCanceledOnTouchOutside(true)

        view.findViewById<View>(R.id.btnAceptarPanel).setOnClickListener {
            onAceptar(op)
            dialog.dismiss()
        }

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.WHITE))
            val ancho = (context.resources.displayMetrics.widthPixels * 0.62f).toInt()
            setLayout(ancho, ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.END)
            setDimAmount(0.25f)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        dialog.show()
    }

    // ===================== SECCIONES (en el orden de las capturas) =====================

    private fun construirSecciones() {
        seccionCantidad()
        seccionNota()
        seccionPrecioPropio()

        seccionRadio("planes de financiación", COLOR_PLANES, precios.nombres(precios.planes), op.planFinanciacion) { op.planFinanciacion = it }
        seccionRadio("Tipo de sistema de ventanas", COLOR_SISTEMA, precios.nombres(precios.ventanas), op.sistemaVentana) { op.sistemaVentana = it }
        seccionRadio("tipo de cristal", COLOR_CRISTAL, precios.nombres(precios.cristal), op.tipoCristal) { op.tipoCristal = it }
        seccionCheck("Tipo de umbral externo", COLOR_UMBRAL_EXT, precios.nombres(precios.umbralExt), op.umbralesExternos)
        seccionCheck("Tipo de umbral interno", COLOR_UMBRAL_INT, precios.nombres(precios.umbralInt), op.umbralesInternos)
        seccionRadio("mueble", COLOR_MUEBLE, precios.nombres(precios.muebles), op.mueble) { op.mueble = it }
        seccionCheck("extras", COLOR_EXTRAS, precios.extras.map { it.nombre.trim() }.filter { it.isNotEmpty() }.distinct(), op.extras)
        seccionCheck("trabajos", COLOR_TRABAJOS, precios.empleos.map { it.nombre.trim() }.filter { it.isNotEmpty() }.distinct(), op.trabajos)
        seccionRadio("mosquitero", COLOR_MOSQUITERO, precios.nombres(precios.mosquitera), op.mosquitero) { op.mosquitero = it }

        val laminaciones = precios.nombres(precios.laminacion)
        seccionRadio("laminación interna", COLOR_LAM_INT, laminaciones, op.laminacionInterna) { op.laminacionInterna = it }
        seccionRadio("laminación externa", COLOR_LAM_EXT, laminaciones, op.laminacionExterna) { op.laminacionExterna = it }

        val colores = precios.colores.filter { it.nombre.isNotBlank() }.distinctBy { it.nombre.trim() }
        seccionRadio(
            titulo = "color",
            colorEncabezado = COLOR_COLOR,
            nombres = colores.map { it.nombre.trim() },
            seleccionado = op.color,
            muestras = colores.map { runCatching { Color.parseColor(it.colorHex) }.getOrDefault(Color.WHITE) },
        ) { op.color = it }

        seccionAdicionales()
        seccionPerfiles("Los perfiles de juntas", "juntas")
        seccionPerfiles("Los perfiles de juntas 90°", "juntas90")
        seccionPerfiles("Los perfiles de juntas 135°", "juntas135")
        seccionPerfiles("perfiles huecos", "huecos")
        seccionExtensor()
    }

    private fun seccionCantidad() {
        encabezado("cantidad", COLOR_AZUL_CLARO)
        val fila = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val valor = TextView(context).apply {
            text = op.cantidad.toString()
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        fun boton(texto: String, accion: () -> Unit) = TextView(context).apply {
            text = texto
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#DDDDDD"))
            layoutParams = LinearLayout.LayoutParams(0, dp(56), 1f)
            isClickable = true
            setOnClickListener { accion() }
        }
        fila.addView(boton("-") {
            if (op.cantidad > 1) op.cantidad--
            valor.text = op.cantidad.toString()
        })
        fila.addView(valor)
        fila.addView(boton("+") {
            op.cantidad++
            valor.text = op.cantidad.toString()
        })
        contenedor.addView(fila)
    }

    private fun seccionNota() {
        encabezado("Nota", COLOR_AZUL_CLARO)
        contenedor.addView(EditText(context).apply {
            setText(op.nota)
            hint = "Escriba una nota…"
            minLines = 2
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setTextColor(Color.BLACK)
            addTextChangedListener(simpleWatcher { op.nota = it.trim() })
            layoutParams = margenes(dp(8), dp(4))
        })
    }

    private fun seccionPrecioPropio() {
        encabezado("precio propio", COLOR_PRECIO_PROPIO)
        contenedor.addView(EditText(context).apply {
            setText(op.precioPropio)
            hint = "0.00"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            addTextChangedListener(simpleWatcher { op.precioPropio = it.trim() })
            layoutParams = margenes(dp(48), dp(4))
        })
    }

    /**
     * Lista de selección única con "no utilice" al inicio.
     * Si [muestras] no es null, cada opción lleva a la izquierda un cuadro con su color.
     */
    private fun seccionRadio(
        titulo: String,
        colorEncabezado: Int,
        nombres: List<String>,
        seleccionado: String?,
        muestras: List<Int>? = null,
        onSeleccion: (String?) -> Unit,
    ) {
        encabezado(titulo, colorEncabezado)
        // Si lo guardado ya no existe en Precios (se renombró o eliminó), queda "no utilice"
        val actual = seleccionado?.takeIf { it in nombres }
        if (actual != seleccionado) onSeleccion(null)

        val botones = mutableListOf<RadioButton>()
        fun agregar(nombre: String?, muestra: Int?) {
            val rb = RadioButton(context).apply {
                text = nombre ?: "no utilice"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTextColor(Color.BLACK)
                isChecked = (nombre == actual)
                minHeight = dp(48)
            }
            rb.setOnClickListener {
                botones.forEach { it.isChecked = (it == rb) }
                onSeleccion(nombre)
            }
            botones.add(rb)

            if (muestra == null) {
                rb.layoutParams = margenes(dp(6), 0)
                contenedor.addView(rb)
            } else {
                val fila = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    isClickable = true
                    setOnClickListener { rb.performClick() }
                }
                fila.addView(View(context).apply {
                    setBackgroundColor(muestra)
                    layoutParams = LinearLayout.LayoutParams(dp(56), dp(52))
                })
                rb.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(10)
                }
                fila.addView(rb)
                contenedor.addView(fila)
            }
        }

        agregar(null, null)
        nombres.forEachIndexed { i, nombre -> agregar(nombre, muestras?.getOrNull(i)) }
    }

    /** Lista de selección múltiple (sin "no utilice"). */
    private fun seccionCheck(titulo: String, colorEncabezado: Int, nombres: List<String>, seleccionados: MutableSet<String>) {
        encabezado(titulo, colorEncabezado)
        seleccionados.retainAll(nombres.toSet())
        if (nombres.isEmpty()) {
            contenedor.addView(textoVacio())
            return
        }
        nombres.forEach { nombre ->
            contenedor.addView(CheckBox(context).apply {
                text = nombre
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTextColor(Color.BLACK)
                isChecked = nombre in seleccionados
                minHeight = dp(48)
                layoutParams = margenes(dp(6), 0)
                setOnCheckedChangeListener { _, marcado -> if (marcado) seleccionados.add(nombre) else seleccionados.remove(nombre) }
            })
        }
    }

    /** Adicionales: ícono con su casilla dentro + nombre (como en el ejemplo). */
    private fun seccionAdicionales() {
        encabezado("aditionals por tamaño", COLOR_AZUL_CLARO)
        listOf(
            "perfil del asiento" to R.drawable.ic_perfil_lado_inf,
            "dorsill" to R.drawable.ic_perfil_lado_inf,
            "jamba de la esquina" to R.drawable.ic_perfil_lado_izq,
        ).forEach { (nombre, icono) ->
            val fila = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(3), dp(8), dp(3))
            }
            val casilla = iconoConCasilla(icono, op.adicionales.contains(nombre), TAM_ICONO) { marcado ->
                if (marcado) op.adicionales.add(nombre) else op.adicionales.remove(nombre)
            }
            fila.addView(casilla.first)
            fila.addView(TextView(context).apply {
                text = nombre
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                setTextColor(Color.BLACK)
                setPadding(dp(10), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            fila.setOnClickListener { casilla.second.toggle() }
            contenedor.addView(fila)
        }
    }

    /** Fila de 4 perfiles (izquierda, arriba, abajo, derecha) con la casilla dentro del ícono. */
    private fun seccionPerfiles(titulo: String, clave: String) {
        encabezado(titulo, COLOR_AZUL_CLARO)
        val fila = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        ICONOS_LADOS.forEachIndexed { lado, icono ->
            val key = "$clave:$lado"
            val (celda, _) = iconoConCasilla(icono, key in op.perfiles, TAM_ICONO) { marcado ->
                if (marcado) op.perfiles.add(key) else op.perfiles.remove(key)
            }
            (celda.layoutParams as LinearLayout.LayoutParams).setMargins(dp(3), 0, dp(3), 0)
            fila.addView(celda)
        }
        contenedor.addView(fila)
    }

    /** Tabla de perfiles de extensor: medidas 15..100 x 4 lados. Las 4 columnas se reparten el ancho del panel. */
    private fun seccionExtensor() {
        encabezado("perfiles de extensor", COLOR_AZUL_CLARO)
        val anchoEtiqueta = dp(40)

        fun filaBase() = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
        }
        fun columna() = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val cabecera = filaBase().apply { setPadding(dp(8), dp(6), dp(8), dp(4)) }
        cabecera.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(anchoEtiqueta, 1) })
        ICONOS_LADOS.forEach { icono ->
            cabecera.addView(columna().apply {
                addView(ImageView(context).apply {
                    setImageResource(icono)
                    layoutParams = FrameLayout.LayoutParams(dp(TAM_ICONO_DP), dp(TAM_ICONO_DP), Gravity.CENTER)
                })
            })
        }
        contenedor.addView(cabecera)

        listOf(15, 20, 40, 60, 80, 100).forEachIndexed { i, medida ->
            val fila = filaBase().apply {
                if (i % 2 == 1) setBackgroundColor(Color.parseColor("#DDE4EC"))
            }
            fila.addView(TextView(context).apply {
                text = medida.toString()
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(Color.BLACK)
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(anchoEtiqueta, dp(40))
            })
            for (lado in 0 until 4) {
                val key = "extensor:$medida:$lado"
                fila.addView(columna().apply {
                    addView(casillaCompacta(key in op.perfiles) { marcado ->
                        if (marcado) op.perfiles.add(key) else op.perfiles.remove(key)
                    }.apply {
                        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
                    })
                })
            }
            contenedor.addView(fila)
        }
    }

    /**
     * Ícono de perfil (tamaño [tamDp]) con una casilla pequeña centrada encima.
     * Devuelve la celda (para añadir a una fila horizontal) y la casilla.
     */
    private fun iconoConCasilla(icono: Int, marcado: Boolean, tamDp: Int, onCambio: (Boolean) -> Unit): Pair<FrameLayout, CheckBox> {
        val celda = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(tamDp), dp(tamDp))
        }
        celda.addView(ImageView(context).apply {
            setImageResource(icono)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        })
        val casilla = casillaCompacta(marcado, onCambio).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        }
        celda.addView(casilla)
        celda.setOnClickListener { casilla.toggle() }
        return celda to casilla
    }

    /** CheckBox sin texto ni márgenes mínimos (el estándar ocupa 48dp y deformaba las filas). */
    private fun casillaCompacta(marcado: Boolean, onCambio: (Boolean) -> Unit) = CheckBox(context).apply {
        text = ""
        minWidth = 0
        minHeight = 0
        minimumWidth = 0
        minimumHeight = 0
        setPadding(0, 0, 0, 0)
        scaleX = 0.75f
        scaleY = 0.75f
        isChecked = marcado
        setOnCheckedChangeListener { _, m -> onCambio(m) }
    }

    // ===================== AYUDANTES =====================

    private fun encabezado(texto: String, color: Int) {
        contenedor.addView(StrokeTextView(context).apply {
            text = texto
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setBackgroundColor(color)
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
    }

    private fun textoVacio() = TextView(context).apply {
        text = "(no hay elementos en Precios)"
        setTextColor(Color.GRAY)
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun margenes(horizontal: Int, vertical: Int) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(horizontal, vertical, horizontal, vertical)
        }

    private fun simpleWatcher(onChange: (String) -> Unit) = object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: android.text.Editable?) = onChange(s?.toString() ?: "")
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    companion object {
        private val COLOR_AZUL_CLARO = Color.parseColor("#8DAEE8")
        private val COLOR_PRECIO_PROPIO = Color.parseColor("#FFE100")
        private val COLOR_PLANES = Color.parseColor("#E07AB8")
        private val COLOR_SISTEMA = Color.parseColor("#F5A623")
        private val COLOR_CRISTAL = Color.parseColor("#55A8F5")
        private val COLOR_UMBRAL_EXT = Color.parseColor("#B08CF5")
        private val COLOR_UMBRAL_INT = Color.parseColor("#7ED957")
        private val COLOR_MUEBLE = Color.parseColor("#D9577E")
        private val COLOR_EXTRAS = Color.parseColor("#B5CC7A")
        private val COLOR_TRABAJOS = Color.parseColor("#E07A7A")
        private val COLOR_MOSQUITERO = Color.parseColor("#E5AE5E")
        private val COLOR_LAM_INT = Color.parseColor("#7BAA2A")
        private val COLOR_LAM_EXT = Color.parseColor("#26A68A")
        private val COLOR_COLOR = Color.parseColor("#A8865C")

        /** Tamaño de los íconos de perfiles (más compacto que antes para que no se corte). */
        private const val TAM_ICONO_DP = 38
        private const val TAM_ICONO = TAM_ICONO_DP

        private val ICONOS_LADOS = listOf(
            R.drawable.ic_perfil_lado_izq,
            R.drawable.ic_perfil_lado_sup,
            R.drawable.ic_perfil_lado_inf,
            R.drawable.ic_perfil_lado_der,
        )
    }
}
