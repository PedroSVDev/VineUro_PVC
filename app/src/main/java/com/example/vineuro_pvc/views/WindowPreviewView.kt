package com.example.vineuro_pvc.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.vineuro_pvc.data.AperturasMulti
import com.example.vineuro_pvc.data.OrderItem
import com.example.vineuro_pvc.data.Unidades
import com.example.vineuro_pvc.data.WindowConfig

class WindowPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    enum class DimensionType {
        TOTAL_HEIGHT,
        TOTAL_WIDTH,
        SECTION_HEIGHT,
        SECTION_WIDTH,
    }

    data class BadgeArea(
        val rect: RectF,
        val type: DimensionType,
        val index: Int = 0,
        val currentValue: Int,
    )

    data class PaneArea(
        val rect: RectF,
        val paneIndex: Int,
        val row: Int,
        val col: Int,
    )

    /**
     * Convierte valores DP a píxeles reales según la densidad del celular.
     */
    private fun dpToPx(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val marcoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /** Pintura del marco exterior: el mismo color del perfil un poco más oscuro que el de las hojas, para dar profundidad. */
    private fun marcoPintura(): Paint {
        val c = framePaint.color
        val f = 0.915f
        marcoPaint.color = Color.argb(Color.alpha(c), (Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())
        return marcoPaint
    }

    /** Color del perfil (#RRGGBB) elegido en las opciones de la ventana; null = blanco. Pinta marco, imposta y hojas. */
    var perfilColorHex: String? = null
        set(value) {
            field = value
            framePaint.color = colorOBlanco(value)
            invalidate()
        }

    private fun colorOBlanco(hex: String?): Int =
        hex?.let { runCatching { Color.parseColor(it) }.getOrNull() } ?: Color.WHITE

    private val frameBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1f)
    }

    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C8D8E8")
        style = Paint.Style.FILL
    }

    private val sandwichPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E4E4E4")
        style = Paint.Style.FILL
    }

    private val meshPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#70666666")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(0.5f)
    }

    /**
     * Modo impresión (PDF y su vista previa): las cotas van como texto limpio, sin recuadros (solo el número sobre
     * un fondo blanco que corta la línea de cota), con la letra de los PDF, y sin elementos del editor
     * (por ejemplo la manija azul para arrastrar).
     */
    var modoImpresion: Boolean = false

    /** "shadows": sombras suaves en perfiles y vidrio (marcado por defecto). */
    var showShadows: Boolean = true
        set(value) { field = value; invalidate() }

    private val junquilloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#B9B9B9"); style = Paint.Style.FILL }
    private val junquilloFilete = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#DDDDDD"); style = Paint.Style.FILL }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(46, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val handlePlatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val doorHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#444444")
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = dpToPx(1f)
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#444444")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.5f)
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80A0D0F0")
        style = Paint.Style.FILL
    }

    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A90E2")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.5f)
    }

    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A90E2")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.2f)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1976D2")
        textSize = dpToPx(11f)
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A1A")
        style = Paint.Style.STROKE
        // trazo fino: los símbolos de apertura no deben confundirse con travesaños o perfiles de la ventana
        strokeWidth = dpToPx(0.9f)
        strokeCap = Paint.Cap.ROUND
    }

    private val circleCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#65A0D0F5")
        style = Paint.Style.FILL
    }

    private var config: WindowConfig? = null
    var showTechnicalDetails: Boolean = false

    var sectionHeights = floatArrayOf(600f, 1200f)
    var sectionWidths = floatArrayOf(500f, 500f, 500f)

    val altoTotalMm: Int
        get() = sectionHeights.sum().toInt()

    val anchoTotalMm: Int
        get() = sectionWidths.sum().toInt()

    private val badgeAreas = mutableListOf<BadgeArea>()
    private val paneAreas = mutableListOf<PaneArea>()

    val paneMechanisms = mutableMapOf<Int, String>()
    val paneMosqNet = mutableMapOf<Int, Boolean>()
    val panePuerta = mutableMapOf<Int, Boolean>()
    val paneSandwich = mutableMapOf<Int, Boolean>()
    val paneCols = mutableMapOf<Int, Int>()
    /** Bits de sándwich por vidrio cuando la hoja tiene imposta interior (códigos 11..14 de paneCols). */
    val paneSandwichSub = mutableMapOf<Int, Int>()
    /** Barras de vidrio por paño: "HxV HxV HxV" (horizontales x verticales de cada vidrio). */
    val paneBars = mutableMapOf<Int, String>()

    /** Hojas (índices temporales de las puertas francesas) que no llevan manija: la hoja pasiva. */
    private val hojasSinManija = mutableSetOf<Int>()
    val paneImpOff = mutableMapOf<Int, String>()

    var onDimensionClickListener: ((type: DimensionType, index: Int, currentValue: Int) -> Unit)? = null
    var onPaneClickListener: ((paneIndex: Int, row: Int, col: Int) -> Unit)? = null

    fun setConfig(windowConfig: WindowConfig, isTechnicalMode: Boolean = false, resetMechanisms: Boolean = true) {
        val isNewConfig = (this.config?.id != windowConfig.id)
        this.config = windowConfig
        this.showTechnicalDetails = isTechnicalMode

        val rows = windowConfig.filasActivas()
        val cols = windowConfig.colsActivas()

        val currentTotalH = if (sectionHeights.isNotEmpty()) sectionHeights.sum() else 1800f
        val currentTotalW = if (sectionWidths.isNotEmpty()) sectionWidths.sum() else 1500f

        val modelo = windowConfig.modeloEstructura
        if (modelo != null) {
            // Modelos: tamaño y secciones iniciales según su estructura
            if (isNewConfig || sectionHeights.size != rows || sectionWidths.size != cols) {
                val (aw, ah) = com.example.vineuro_pvc.data.ModelosVentana.seccionesPorDefecto(
                    modelo, if (isNewConfig) modelo.anchoMm.toFloat() else currentTotalW, if (isNewConfig) modelo.altoMm.toFloat() else currentTotalH,
                )
                sectionWidths = aw; sectionHeights = ah
            }
        } else {
        if (isNewConfig || sectionHeights.size != rows) {
            sectionHeights = if (rows == 2) {
                floatArrayOf(600f, (currentTotalH - 600f).coerceAtLeast(50f))
            } else {
                val defaultH = currentTotalH / rows
                FloatArray(rows) { defaultH }
            }
        }

        if (isNewConfig || sectionWidths.size != cols) {
            val defaultW = currentTotalW / cols
            sectionWidths = FloatArray(cols) { defaultW }
        }
        }

        if (resetMechanisms) {
            paneMechanisms.clear()
            paneMosqNet.clear()
            panePuerta.clear()
            paneSandwich.clear()
            paneCols.clear()
            paneSandwichSub.clear()
            paneBars.clear()
            paneImpOff.clear()
        }

        invalidate()
    }

    fun resetCanvas() {
        this.config = null
        paneMechanisms.clear()
        paneMosqNet.clear()
        panePuerta.clear()
        paneSandwich.clear()
        paneCols.clear()
        paneSandwichSub.clear()
        paneBars.clear()
        paneImpOff.clear()
        badgeAreas.clear()
        paneAreas.clear()
        invalidate()
    }

    fun updateDimensions(alto: Int, ancho: Int) {
        updateTotalHeight(alto)
        updateTotalWidth(ancho)
    }

    fun updateSectionHeight(index: Int, newHeightMm: Int) {
        val cfg = this.config
        if (cfg != null) {
            val rows = getActiveLayoutRows(cfg)
            if (sectionHeights.size != rows) {
                val totalH = if (sectionHeights.isNotEmpty()) sectionHeights.sum() else 1800f
                val defaultH = totalH / rows
                sectionHeights = FloatArray(rows) { defaultH }
            }
        }

        if ((index in sectionHeights.indices) && (newHeightMm >= 50)) {
            val oldTotalInt = sectionHeights.sum().toInt()
            val numSections = sectionHeights.size

            if (numSections > 1) {
                val minRequiredOther = (numSections - 1) * 50
                if (newHeightMm + minRequiredOther <= oldTotalInt) {
                    val remainingInt = oldTotalInt - newHeightMm
                    val otherIndices = sectionHeights.indices.filter { it != index }
                    val countOthers = otherIndices.size

                    var otherSumFloat = 0f
                    for (i in otherIndices) {
                        otherSumFloat += sectionHeights[i]
                    }

                    sectionHeights[index] = newHeightMm.toFloat()

                    if (otherSumFloat > 0f) {
                        var assignedSum = 0
                        val allocatedInts = IntArray(countOthers)

                        for (k in otherIndices.indices) {
                            val origIdx = otherIndices[k]
                            val ratio = sectionHeights[origIdx] / otherSumFloat
                            val alloc = (remainingInt * ratio).toInt()
                            allocatedInts[k] = alloc
                            assignedSum += alloc
                        }

                        var remainder = remainingInt - assignedSum
                        var k = 0
                        while (remainder > 0) {
                            allocatedInts[k % countOthers] += 1
                            remainder--
                            k++
                        }

                        for (k in otherIndices.indices) {
                            val origIdx = otherIndices[k]
                            sectionHeights[origIdx] = allocatedInts[k].toFloat().coerceAtLeast(50f)
                        }
                    } else {
                        val base = remainingInt / countOthers
                        var remainder = remainingInt % countOthers
                        for (k in otherIndices.indices) {
                            val origIdx = otherIndices[k]
                            val extra = if (remainder > 0) 1 else 0
                            if (remainder > 0) remainder--
                            sectionHeights[origIdx] = (base + extra).toFloat().coerceAtLeast(50f)
                        }
                    }
                } else {
                    sectionHeights[index] = newHeightMm.toFloat()
                    val otherIndices = sectionHeights.indices.filter { it != index }
                    for (i in otherIndices) {
                        if (sectionHeights[i] < 50f) sectionHeights[i] = 50f
                    }
                }
            } else {
                sectionHeights[index] = newHeightMm.toFloat()
            }
            invalidate()
        }
    }

    fun updateSectionWidth(index: Int, newWidthMm: Int) {
        val cfg = this.config
        if (cfg != null) {
            val cols = getActiveLayoutCols(cfg)
            if (sectionWidths.size != cols) {
                val totalW = if (sectionWidths.isNotEmpty()) sectionWidths.sum() else 1500f
                val defaultW = totalW / cols
                sectionWidths = FloatArray(cols) { defaultW }
            }
        }

        if ((index in sectionWidths.indices) && (newWidthMm >= 50)) {
            val oldTotalInt = sectionWidths.sum().toInt()
            val numSections = sectionWidths.size

            if (numSections > 1) {
                val minRequiredOther = (numSections - 1) * 50
                if (newWidthMm + minRequiredOther <= oldTotalInt) {
                    val remainingInt = oldTotalInt - newWidthMm
                    val otherIndices = sectionWidths.indices.filter { it != index }
                    val countOthers = otherIndices.size

                    var otherSumFloat = 0f
                    for (i in otherIndices) {
                        otherSumFloat += sectionWidths[i]
                    }

                    sectionWidths[index] = newWidthMm.toFloat()

                    if (otherSumFloat > 0f) {
                        var assignedSum = 0
                        val allocatedInts = IntArray(countOthers)

                        for (k in otherIndices.indices) {
                            val origIdx = otherIndices[k]
                            val ratio = sectionWidths[origIdx] / otherSumFloat
                            val alloc = (remainingInt * ratio).toInt()
                            allocatedInts[k] = alloc
                            assignedSum += alloc
                        }

                        var remainder = remainingInt - assignedSum
                        var k = 0
                        while (remainder > 0) {
                            allocatedInts[k % countOthers] += 1
                            remainder--
                            k++
                        }

                        for (k in otherIndices.indices) {
                            val origIdx = otherIndices[k]
                            sectionWidths[origIdx] = allocatedInts[k].toFloat().coerceAtLeast(50f)
                        }
                    } else {
                        val base = remainingInt / countOthers
                        var remainder = remainingInt % countOthers
                        for (k in otherIndices.indices) {
                            val origIdx = otherIndices[k]
                            val extra = if (remainder > 0) 1 else 0
                            if (remainder > 0) remainder--
                            sectionWidths[origIdx] = (base + extra).toFloat().coerceAtLeast(50f)
                        }
                    }
                } else {
                    sectionWidths[index] = newWidthMm.toFloat()
                    val otherIndices = sectionWidths.indices.filter { it != index }
                    for (i in otherIndices) {
                        if (sectionWidths[i] < 50f) sectionWidths[i] = 50f
                    }
                }
            } else {
                sectionWidths[index] = newWidthMm.toFloat()
            }
            invalidate()
        }
    }

    /** Cantidad de secciones (columnas o filas) que tiene la ventana en el dibujo. */
    fun cantidadSecciones(esAncho: Boolean): Int = if (esAncho) sectionWidths.size else sectionHeights.size

    /**
     * "¿Igualar dimensiones?": reparte el total en secciones de igual medida
     * (en mm enteros; el resto se reparte de a 1 mm para que la suma siga siendo el total).
     */
    fun igualarSecciones(esAncho: Boolean) {
        val arr = if (esAncho) sectionWidths else sectionHeights
        val n = arr.size
        if (n < 2) return
        val total = arr.sum().toInt()
        val base = total / n
        var resto = total % n
        for (i in 0 until n) { arr[i] = (base + if (resto > 0) 1 else 0).toFloat(); if (resto > 0) resto-- }
        invalidate()
    }

    fun updateTotalHeight(newTotalHeightMm: Int) {
        if ((newTotalHeightMm >= 100) && sectionHeights.isNotEmpty()) {
            val oldTotal = sectionHeights.sum()
            if (oldTotal > 0) {
                val scale = newTotalHeightMm.toFloat() / oldTotal
                for (i in sectionHeights.indices) {
                    sectionHeights[i] = (sectionHeights[i] * scale).coerceAtLeast(10f)
                }
            }
            invalidate()
        }
    }

    fun updateTotalWidth(newTotalWidthMm: Int) {
        if ((newTotalWidthMm >= 100) && sectionWidths.isNotEmpty()) {
            val oldTotal = sectionWidths.sum()
            if (oldTotal > 0) {
                val scale = newTotalWidthMm.toFloat() / oldTotal
                for (i in sectionWidths.indices) {
                    sectionWidths[i] = (sectionWidths[i] * scale).coerceAtLeast(10f)
                }
            }
            invalidate()
        }
    }

    /** Posición (mm) de las impostas interiores de un paño: "400" o "400,700"; vacío = reparto igual. */
    fun setPaneImpOff(paneIndex: Int, offs: String?) {
        if (offs.isNullOrBlank() || offs.split(",").all { it.trim().toFloatOrNull() == null }) paneImpOff.remove(paneIndex)
        else paneImpOff[paneIndex] = offs.trim()
        invalidate()
    }

    /** Barras de vidrio de un paño ("HxV HxV HxV"); null o todo ceros las quita. */
    fun setPaneBars(paneIndex: Int, bars: String?) {
        if (bars.isNullOrBlank() || bars.split(" ").all { t -> t.split("x").all { (it.toIntOrNull() ?: 0) == 0 } }) paneBars.remove(paneIndex)
        else paneBars[paneIndex] = bars.trim()
        invalidate()
    }

    fun setPaneMechanism(paneIndex: Int, mechanismId: String) {
        paneMechanisms[paneIndex] = mechanismId
        invalidate()
    }

    fun setPaneOptions(
        paneIndex: Int,
        isSandwich: Boolean,
        isPuerta: Boolean,
        isMosqNet: Boolean,
        subCols: Int = 1,
        sandwichMask: Int = -1,
    ) {
        if (sandwichMask >= 0) paneSandwichSub[paneIndex] = sandwichMask else paneSandwichSub.remove(paneIndex)
        paneSandwich[paneIndex] = isSandwich
        panePuerta[paneIndex] = isPuerta
        paneMosqNet[paneIndex] = isMosqNet
        paneCols[paneIndex] = subCols
        invalidate()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!showTechnicalDetails) return false

        if (event.action == MotionEvent.ACTION_UP) {
            val x = event.x
            val y = event.y

            var closestBadge: BadgeArea? = null
            var minDistanceSq = Float.MAX_VALUE
            val touchThreshold = dpToPx(32f)

            for (badge in badgeAreas) {
                val expandedRect = RectF(
                    badge.rect.left - touchThreshold,
                    badge.rect.top - touchThreshold,
                    badge.rect.right + touchThreshold,
                    badge.rect.bottom + touchThreshold
                )
                if (expandedRect.contains(x, y)) {
                    val cX = badge.rect.centerX()
                    val cY = badge.rect.centerY()
                    val distSq = (x - cX) * (x - cX) + (y - cY) * (y - cY)
                    if (distSq < minDistanceSq) {
                        minDistanceSq = distSq
                        closestBadge = badge
                    }
                }
            }

            if (closestBadge != null) {
                performClick()
                onDimensionClickListener?.invoke(closestBadge.type, closestBadge.index, closestBadge.currentValue)
                return true
            }

            for (pane in paneAreas) {
                if (pane.rect.contains(x, y)) {
                    performClick()
                    onPaneClickListener?.invoke(pane.paneIndex, pane.row, pane.col)
                    return true
                }
            }
        }
        return true
    }

    /**
     * Permite dibujar un OrderItem específico en cualquier Canvas con límites dados.
     */
    fun drawItem(
        canvas: Canvas,
        bounds: RectF,
        item: OrderItem,
        showTechnicalDetails: Boolean = true,
        isCompactMode: Boolean = false,
        /** Color del perfil a usar; null = el color elegido en las opciones de la ventana (o blanco). */
        colorMarco: Int? = null,
    ) {
        val savedConfig = this.config
        val savedTechMode = this.showTechnicalDetails
        val savedHeights = this.sectionHeights.clone()
        val savedWidths = this.sectionWidths.clone()
        val savedMechanisms = HashMap(this.paneMechanisms)
        val savedMosqNet = HashMap(this.paneMosqNet)
        val savedPuerta = HashMap(this.panePuerta)
        val savedSandwich = HashMap(this.paneSandwich)
        val savedCols = HashMap(this.paneCols)
        val savedSub = HashMap(this.paneSandwichSub)
        val savedBars = HashMap(this.paneBars)
        val savedImpOff = HashMap(this.paneImpOff)
        val savedFrameColor = framePaint.color

        try {
            framePaint.color = colorMarco ?: colorOBlanco(com.example.vineuro_pvc.data.ColoresPerfil.hexDe(item.opciones.color))
            this.config = item.config
            this.showTechnicalDetails = showTechnicalDetails
            this.sectionHeights = item.sectionHeights.clone()
            this.sectionWidths = item.sectionWidths.clone()

            this.paneMechanisms.clear()
            this.paneMechanisms.putAll(item.paneMechanisms)

            this.paneMosqNet.clear()
            this.paneMosqNet.putAll(item.paneMosqNet)

            this.panePuerta.clear()
            this.panePuerta.putAll(item.panePuerta)

            this.paneSandwich.clear()
            this.paneSandwich.putAll(item.paneSandwich)

            this.paneCols.clear()
            this.paneCols.putAll(item.paneCols)
            this.paneSandwichSub.clear()
            this.paneSandwichSub.putAll(item.paneSandwichSub)
            this.paneBars.clear()
            this.paneImpOff.clear()
            this.paneBars.putAll(item.paneBars)
            this.paneImpOff.putAll(item.paneImpOff)

            val saveCount = canvas.save()
            canvas.translate(bounds.left, bounds.top)

            val w = bounds.width()
            val h = bounds.height()

            if (w > 0 && h > 0) {
                badgeAreas.clear()
                paneAreas.clear()

                val cfg = item.config
                if (!showTechnicalDetails) {
                    val padding = dpToPx(2f)
                    val rect = RectF(padding, padding, w - padding, h - padding)
                    drawWindowConfig(canvas, rect, cfg)
                } else {
                val isCompact = isCompactMode || (w < dpToPx(300f))

                val leftMargin = if (isCompact) dpToPx(24f) else dpToPx(55f)
                val bottomMargin = if (isCompact) dpToPx(20f) else dpToPx(45f)
                val topMargin = if (isCompact) dpToPx(6f) else dpToPx(15f)
                val rightMargin = if (isCompact) dpToPx(6f) else dpToPx(15f)

                val availW = w - leftMargin - rightMargin
                val availH = h - topMargin - bottomMargin

                if (availW > 0 && availH > 0) {
                    val totalH = item.altoTotalMm.toFloat().coerceAtLeast(1f)
                    val totalW = item.anchoTotalMm.toFloat().coerceAtLeast(1f)

                    val targetAspect = totalW / totalH
                    val availAspect = availW / availH

                    val drawW: Float
                    val drawH: Float

                    if (targetAspect > availAspect) {
                        drawW = availW
                        drawH = availW / targetAspect
                    } else {
                        drawH = availH
                        drawW = availH * targetAspect
                    }

                    val rectLeft = leftMargin + (availW - drawW) / 2f
                    val rectTop = topMargin + (availH - drawH) / 2f
                    val rectRight = rectLeft + drawW
                    val rectBottom = rectTop + drawH

                    val windowRect = RectF(rectLeft, rectTop, rectRight, rectBottom)
                    pxPorMm = drawW / totalW

                    drawWindowConfig(canvas, windowRect, cfg)
                    drawDimensionLines(canvas, windowRect, cfg, isCompactMode = isCompact)
                }
                }
            }
            canvas.restoreToCount(saveCount)
        } finally {
            framePaint.color = savedFrameColor
            this.config = savedConfig
            this.showTechnicalDetails = savedTechMode
            this.sectionHeights = savedHeights
            this.sectionWidths = savedWidths
            this.paneMechanisms.clear()
            this.paneMechanisms.putAll(savedMechanisms)
            this.paneMosqNet.clear()
            this.paneMosqNet.putAll(savedMosqNet)
            this.panePuerta.clear()
            this.panePuerta.putAll(savedPuerta)
            this.paneSandwich.clear()
            this.paneSandwich.putAll(savedSandwich)
            this.paneCols.clear()
            this.paneCols.putAll(savedCols)
            this.paneSandwichSub.clear()
            this.paneSandwichSub.putAll(savedSub)
            this.paneBars.clear()
            this.paneImpOff.clear()
            this.paneBars.putAll(savedBars)
            this.paneImpOff.putAll(savedImpOff)
        }
    }

    companion object {
        fun renderItemToBitmap(
            context: Context,
            item: OrderItem,
            widthPx: Int,
            heightPx: Int,
            showTechnicalDetails: Boolean = true
        ): Bitmap {
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val view = WindowPreviewView(context)
            view.layout(0, 0, widthPx, heightPx)
            view.drawItem(canvas, RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat()), item, showTechnicalDetails)
            return bitmap
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        badgeAreas.clear()
        paneAreas.clear()

        val cfg = config ?: return
        val w = width.toFloat()
        val h = height.toFloat()

        if ((w <= 0) || (h <= 0)) return

        if (!showTechnicalDetails) {
            val padding = dpToPx(2f)
            val rect = RectF(padding, padding, w - padding, h - padding)
            drawWindowConfig(canvas, rect, cfg)
            return
        }

        val leftMargin = dpToPx(55f)
        val bottomMargin = dpToPx(45f)
        val topMargin = dpToPx(15f)
        val rightMargin = dpToPx(15f)

        val availW = w - leftMargin - rightMargin
        val availH = h - topMargin - bottomMargin

        if ((availW <= 0) || (availH <= 0)) return

        val totalH = altoTotalMm.toFloat().coerceAtLeast(1f)
        val totalW = anchoTotalMm.toFloat().coerceAtLeast(1f)

        val targetAspect = totalW / totalH
        val availAspect = availW / availH

        val drawW: Float
        val drawH: Float

        if (targetAspect > availAspect) {
            drawW = availW
            drawH = availW / targetAspect
        } else {
            drawH = availH
            drawW = availH * targetAspect
        }

        val rectLeft = leftMargin + (availW - drawW) / 2f
        val rectTop = topMargin + (availH - drawH) / 2f
        val rectRight = rectLeft + drawW
        val rectBottom = rectTop + drawH

        val windowRect = RectF(rectLeft, rectTop, rectRight, rectBottom)
        pxPorMm = drawW / totalW

        drawWindowConfig(canvas, windowRect, cfg)
        if (showDiagonals) drawDiagonales(canvas, windowRect, cfg)
        drawDimensionLines(canvas, windowRect, cfg)
    }

    /** Muestra las diagonales de cada marco con su medida (sirve para comprobar la escuadra al armar). */
    var showDiagonals: Boolean = false
        set(v) { field = v; invalidate() }

    private val diagonalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD32F2F.toInt(); style = Paint.Style.STROKE; strokeWidth = dpToPx(1.4f)
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(dpToPx(6f), dpToPx(4f)), 0f)
    }
    private val diagonalTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); textSize = dpToPx(11f); textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val diagonalFondoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD32F2F.toInt() }

    private fun drawDiagonales(canvas: Canvas, rect: RectF, cfg: WindowConfig) {
        // Un marco por base: en las ventanas L / T cada bloque tiene sus propias diagonales
        val marcos = ArrayList<RectF>()
        val mo = cfg.modeloEstructura
        if (cfg.layoutType == WindowConfig.LayoutType.ARBOL && mo != null) {
            val pl = com.example.vineuro_pvc.data.ModelosVentana.plano(mo, anchoTotalMm.toDouble(), altoTotalMm.toDouble(), sectionWidths, sectionHeights)
            val k = pxPorMm
            pl.bases.forEach { marcos += RectF(rect.left + it.x0.toFloat() * k, rect.top + it.y0.toFloat() * k, rect.left + it.x1.toFloat() * k, rect.top + it.y1.toFloat() * k) }
        }
        if (marcos.isEmpty()) marcos += rect
        val k = pxPorMm.coerceAtLeast(1e-6f)
        for (m in marcos) {
            canvas.drawLine(m.left, m.top, m.right, m.bottom, diagonalPaint)
            canvas.drawLine(m.right, m.top, m.left, m.bottom, diagonalPaint)
            val mm = Math.round(Math.hypot((m.width() / k).toDouble(), (m.height() / k).toDouble())).toInt()
            val txt = Unidades.mostrar(mm)
            val tw = diagonalTextPaint.measureText(txt)
            val cx = m.centerX(); val cy = m.centerY()
            canvas.drawRoundRect(cx - tw / 2 - dpToPx(4f), cy - dpToPx(9f), cx + tw / 2 + dpToPx(4f), cy + dpToPx(9f), dpToPx(4f), dpToPx(4f), diagonalFondoPaint)
            canvas.drawText(txt, cx, cy + dpToPx(4f), diagonalTextPaint)
        }
    }

    /**
     * Remate de la separación entre dos hojas (visto en el original): en el hueco de ~25 mm que queda entre hojas vecinas
     * se marca una raya a 12.5 mm del borde de las hojas, arriba y abajo, que es donde empieza y termina la sombra de la hoja.
     * [x0]..[x1] es el hueco en horizontal y [yAlto]/[yBajo] las alturas de las rayas (o, para huecos horizontales, el reverso).
     */
    private fun rematesSeparacion(canvas: Canvas, huecoVertical: Boolean, a0: Float, a1: Float, b0: Float, b1: Float) {
        if (a1 - a0 < dpToPx(2f)) return
        if (huecoVertical) {
            canvas.drawLine(a0, b0, a1, b0, frameBorderPaint)
            canvas.drawLine(a0, b1, a1, b1, frameBorderPaint)
        } else {
            canvas.drawLine(b0, a0, b0, a1, frameBorderPaint)
            canvas.drawLine(b1, a0, b1, a1, frameBorderPaint)
        }
    }

    /** Mitad de la separación entre dos paños vecinos: 12.5 mm por lado (25 mm entre hojas, como en el original). */
    private fun mitadSeparacion(): Float = (12.5f * pxPorMm).coerceIn(dpToPx(1f), dpToPx(8f))

    /**
     * Sombra proyectada (abajo/derecha) como en el original: el rectángulo desplazado [off], pero con chaflán diagonal
     * en las esquinas superior derecha e inferior izquierda, en un solo trazado para no doblar la transparencia.
     */
    private fun sombraProyectada(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, off: Float) {
        val p = android.graphics.Path()
        p.moveTo(r, t); p.lineTo(r + off, t + off); p.lineTo(r + off, b + off)
        p.lineTo(l + off, b + off); p.lineTo(l, b); p.lineTo(r, b); p.close()
        canvas.drawPath(p, shadowPaint)
    }

    /** Píxeles por milímetro de la ventana dibujada (para dibujar las hojas de las aperturas múltiples a escala). */
    private var pxPorMm = 1f

    /**
     * Ajusta junquillo, manija y bordes al color del perfil, como hace el original: en un perfil oscuro el junquillo y los
     * bordes son aún más oscuros y la manija es del mismo tono del perfil (no blanca). Con blanco/crema queda como siempre.
     */
    private fun ajustarTonosAlPerfil() {
        val c = framePaint.color
        val lum = 0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)
        fun esc(f: Float) = Color.rgb((Color.red(c) * f).toInt().coerceIn(0, 255), (Color.green(c) * f).toInt().coerceIn(0, 255), (Color.blue(c) * f).toInt().coerceIn(0, 255))
        if (lum > 200f) {
            junquilloPaint.color = Color.parseColor("#B9B9B9"); junquilloFilete.color = Color.parseColor("#DDDDDD")
            frameBorderPaint.color = Color.parseColor("#333333")
            handlePlatePaint.color = Color.WHITE; metalBordePaint.color = Color.parseColor("#4A4A4A")
        } else {
            junquilloPaint.color = esc(0.72f); junquilloFilete.color = esc(0.87f)
            frameBorderPaint.color = Color.rgb(minOf(51, (Color.red(c) * 0.6f).toInt()), minOf(51, (Color.green(c) * 0.6f).toInt()), minOf(51, (Color.blue(c) * 0.6f).toInt()))
            handlePlatePaint.color = esc(1.18f); metalBordePaint.color = esc(0.5f)
        }
    }

    private fun drawWindowConfig(canvas: Canvas, rect: RectF, cfg: WindowConfig) {
        ajustarTonosAlPerfil()
        pxPorMm = rect.width() / anchoTotalMm.toFloat().coerceAtLeast(1f)
        if (showShadows && cfg.layoutType != WindowConfig.LayoutType.ARBOL) {
            sombraProyectada(canvas, rect.left, rect.top, rect.right, rect.bottom, dpToPx(2.5f))
        }
        val frameThickness = if (showTechnicalDetails) dpToPx(7f) else dpToPx(3f)

        when (cfg.layoutType) {
            WindowConfig.LayoutType.GRID -> drawGrid(canvas, rect, cfg.cols, cfg.rows, frameThickness)
            WindowConfig.LayoutType.TOP_TRANSOM -> drawTopTransom(canvas, rect, cfg.cols, frameThickness)
            WindowConfig.LayoutType.BOTTOM_TRANSOM -> drawBottomTransom(canvas, rect, cfg.cols, frameThickness)
            WindowConfig.LayoutType.TOP_BOTTOM_TRANSOM -> drawTopBottomTransom(canvas, rect, cfg.cols, frameThickness)
            WindowConfig.LayoutType.DOOR_L_LEFT -> drawDoorL(canvas, rect, isLeftDoor = true, hasTransom = false, frameThickness)
            WindowConfig.LayoutType.DOOR_L_RIGHT -> drawDoorL(canvas, rect, isLeftDoor = false, hasTransom = false, frameThickness)
            WindowConfig.LayoutType.DOOR_T_CENTER -> drawDoorT(canvas, rect, hasTransom = false, frameThickness)
            WindowConfig.LayoutType.DOOR_L_LEFT_TRANSOM -> drawDoorL(canvas, rect, isLeftDoor = true, hasTransom = true, frameThickness)
            WindowConfig.LayoutType.DOOR_L_RIGHT_TRANSOM -> drawDoorL(canvas, rect, isLeftDoor = false, hasTransom = true, frameThickness)
            WindowConfig.LayoutType.DOOR_T_CENTER_TRANSOM -> drawDoorT(canvas, rect, hasTransom = true, frameThickness)
            WindowConfig.LayoutType.ASYMMETRIC_SPLIT -> drawAsymmetric(canvas, rect, cfg.cols, cfg.rows, frameThickness)
            WindowConfig.LayoutType.ARBOL -> drawArbol(canvas, rect, cfg, frameThickness)
        }
    }

    private fun drawPaneWithMechanism(
        canvas: Canvas,
        pL: Float,
        pT: Float,
        pR: Float,
        pB: Float,
        paneIndex: Int = 0,
        row: Int = 0,
        col: Int = 0,
    ) {
        if (pR <= pL || pB <= pT) return

        val subCols = (paneCols[paneIndex] ?: 1).let { if (it in 2..4) it else 1 }
        if (subCols > 1) {
            val subW = (pR - pL) / subCols
            val spacing = dpToPx(1.5f)
            for (sc in 0 until subCols) {
                val spL = pL + sc * subW + (if (sc > 0) spacing else 0f)
                val spR = pL + (sc + 1) * subW - (if (sc < subCols - 1) spacing else 0f)
                val subIndex = paneIndex * 10 + sc
                drawSinglePane(canvas, spL, pT, spR, pB, subIndex, row, col)
            }
        } else {
            drawSinglePane(canvas, pL, pT, pR, pB, paneIndex, row, col)
        }
    }

    private fun drawSinglePane(
        canvas: Canvas,
        pL: Float,
        pT: Float,
        pR: Float,
        pB: Float,
        paneIndex: Int,
        row: Int,
        col: Int,
    ) {
        if (pR <= pL || pB <= pT) return

        if (AperturasMulti.esMulti(paneMechanisms[paneIndex])) {
            dibujarMulti(canvas, pL, pT, pR, pB, paneIndex, row, col)
            return
        }

        val isSandwich = paneSandwich[paneIndex] == true
        val isMosqNet = paneMosqNet[paneIndex] == true
        val isPuerta = panePuerta[paneIndex] == true

        var glassL = pL
        var glassT = pT
        var glassR = pR
        var glassB = pB
        var beadW = 0f   // junquillo (marco gris) alrededor del vidrio

        // Hoja: toda apertura distinta de "sin apertura" (y toda puerta) lleva perfil de hoja alrededor del vidrio y manija.
        val mecanismo = paneMechanisms[paneIndex] ?: "5"
        val tieneHoja = isPuerta || mecanismo != "5"
        var grosorManija = -1f
        if (tieneHoja) {
            val ladoMin = Math.min(pR - pL, pB - pT)
            // Perfil de la hoja a escala (62 mm; 94 mm en puertas), sin pasar del 20 % del lado menor
            val sashThickness = ((if (isPuerta) 94f else 62f) * pxPorMm)
                .coerceIn(dpToPx(2f), maxOf(dpToPx(2f), ladoMin * 0.2f))
            beadW = (16f * pxPorMm).coerceIn(dpToPx(1.5f), sashThickness * 0.6f)

            if (showShadows) {
                // sombra de la hoja sobre el marco (abajo/derecha)
                val off = (12f * pxPorMm).coerceIn(dpToPx(1.2f), dpToPx(6f))
                sombraProyectada(canvas, pL, pT, pR, pB, off)
            }
            canvas.drawRect(pL, pT, pR, pB, framePaint)
            canvas.drawRect(pL, pT, pR, pB, frameBorderPaint)

            // Junquillo: franja gris alrededor del vidrio con un filete más claro por dentro
            val jl = pL + sashThickness; val jt = pT + sashThickness; val jr = pR - sashThickness; val jb = pB - sashThickness
            if (jr - jl > 2 * beadW + dpToPx(2f) && jb - jt > 2 * beadW + dpToPx(2f)) {
                canvas.drawRect(jl, jt, jr, jb, junquilloFilete)          // abajo y derecha: claro
                val oscuro = android.graphics.Path().apply {              // arriba e izquierda: oscuro, con inglete, en una sola pieza
                    moveTo(jl, jt); lineTo(jr, jt); lineTo(jr - beadW, jt + beadW); lineTo(jl + beadW, jt + beadW)
                    lineTo(jl + beadW, jb - beadW); lineTo(jl, jb); close()
                }
                canvas.drawPath(oscuro, junquilloPaint)
                canvas.drawRect(jl, jt, jr, jb, frameBorderPaint)
            } else beadW = 0f

            glassL = pL + sashThickness + beadW
            glassT = pT + sashThickness + beadW
            glassR = pR - sashThickness - beadW
            glassB = pB - sashThickness - beadW

            if (mecanismo != "10" && paneIndex !in hojasSinManija && (pR - pL) > dpToPx(28f) && (pB - pT) > dpToPx(28f)) grosorManija = sashThickness
        }

        val imp = (paneCols[paneIndex] ?: 0).let { if (it in 11..14) it - 10 else 0 }
        if (imp == 0) {
            canvas.drawRect(glassL, glassT, glassR, glassB, if (isSandwich) sandwichPaint else glassPaint)
            canvas.drawRect(glassL, glassT, glassR, glassB, frameBorderPaint)
            dibujarBarrasVidrio(canvas, RectF(glassL, glassT, glassR, glassB), paneIndex, 0)
        } else {
            dibujarVidriosImposta(canvas, glassL, glassT, glassR, glassB, paneIndex, imp, paneSandwichSub[paneIndex] ?: (if (isSandwich) 7 else 0))
        }

        if (showShadows && glassR - glassL > dpToPx(6f) && glassB - glassT > dpToPx(6f)) {
            // sombra interior del perfil sobre el vidrio (arriba e izquierda)
            val franja = (26f * pxPorMm).coerceAtLeast(dpToPx(2.5f))
            canvas.drawRect(glassL, glassT, glassR, glassT + minOf(franja, (glassB - glassT) * 0.3f), shadowPaint)
            canvas.drawRect(glassL, glassT, glassL + minOf(franja, (glassR - glassL) * 0.3f), glassB, shadowPaint)
        }

        if (isMosqNet) {
            val paneW = glassR - glassL
            val paneH = glassB - glassT
            val stepX = paneW / 14f
            val stepY = paneH / 14f

            for (i in 1..13) {
                canvas.drawLine(glassL + i * stepX, glassT, glassL + i * stepX, glassB, meshPaint)
                canvas.drawLine(glassL, glassT + i * stepY, glassR, glassT + i * stepY, meshPaint)
            }
        }

        if (grosorManija >= 0f) {
            dibujarManija(canvas, mecanismo, glassL - beadW, glassT - beadW, glassR + beadW, glassB + beadW, grosorManija, isPuerta)
        }

        val paneRect = RectF(pL, pT, pR, pB)
        if (showTechnicalDetails) {
            paneAreas.add(PaneArea(paneRect, paneIndex, row, col))
        }

        val cX = (glassL + glassR) / 2f
        val cY = (glassT + glassB) / 2f
        val paneW = glassR - glassL
        val paneH = glassB - glassT

        if (paneW <= dpToPx(3f) || paneH <= dpToPx(3f)) return

        val mechanismId = paneMechanisms[paneIndex] ?: "5"
        val padding = dpToPx(2f)
        val arrowHeadSize = dpToPx(4f)

        when (mechanismId) {
            "1" -> {
                val arrowLen = paneW * 0.35f
                canvas.drawLine(cX - arrowLen / 2f, cY, cX + arrowLen / 2f, cY, crosshairPaint)
                canvas.drawLine(cX + arrowLen / 6f, cY - arrowHeadSize, cX + arrowLen / 2f, cY, crosshairPaint)
                canvas.drawLine(cX + arrowLen / 6f, cY + arrowHeadSize, cX + arrowLen / 2f, cY, crosshairPaint)
            }

            "2" -> {
                canvas.drawLine(glassL + padding, glassT + padding, cX, glassB - padding, crosshairPaint)
                canvas.drawLine(glassR - padding, glassT + padding, cX, glassB - padding, crosshairPaint)
            }

            "9" -> {
                // Abatible por arriba ("^"): el vértice apunta hacia la bisagra superior
                canvas.drawLine(glassL + padding, glassB - padding, cX, glassT + padding, crosshairPaint)
                canvas.drawLine(glassR - padding, glassB - padding, cX, glassT + padding, crosshairPaint)
            }

            "10" -> {
                // Hoja fija: tiene perfil de hoja pero sin herraje; se marca con un recuadro interior
                val ins = Math.min(paneW, paneH) * 0.18f
                canvas.drawRect(glassL + ins, glassT + ins, glassR - ins, glassB - ins, crosshairPaint)
            }

            "3" -> {
                val arrowLen = paneW * 0.35f
                canvas.drawLine(cX - arrowLen / 2f, cY, cX + arrowLen / 2f, cY, crosshairPaint)
                canvas.drawLine(cX - arrowLen / 6f, cY - arrowHeadSize, cX - arrowLen / 2f, cY, crosshairPaint)
                canvas.drawLine(cX - arrowLen / 6f, cY + arrowHeadSize, cX - arrowLen / 2f, cY, crosshairPaint)
            }

            "4" -> {
                // Vértice hacia la manija (izquierda)
                canvas.drawLine(glassR - padding, glassT + padding, glassL + padding, cY, crosshairPaint)
                canvas.drawLine(glassR - padding, glassB - padding, glassL + padding, cY, crosshairPaint)
            }

            "6" -> {
                // Vértice hacia la manija (derecha)
                canvas.drawLine(glassL + padding, glassT + padding, glassR - padding, cY, crosshairPaint)
                canvas.drawLine(glassL + padding, glassB - padding, glassR - padding, cY, crosshairPaint)
            }

            "7" -> {
                // Giro con vértice hacia la manija (izquierda) + abatible superior (vértice arriba), como en el selector
                canvas.drawLine(glassR - padding, glassT + padding, glassL + padding, cY, crosshairPaint)
                canvas.drawLine(glassR - padding, glassB - padding, glassL + padding, cY, crosshairPaint)
                canvas.drawLine(glassL + padding, glassB - padding, cX, glassT + padding, crosshairPaint)
                canvas.drawLine(glassR - padding, glassB - padding, cX, glassT + padding, crosshairPaint)
            }

            "8" -> {
                // Giro con vértice hacia la manija (derecha) + abatible superior (vértice arriba), como en el selector
                canvas.drawLine(glassL + padding, glassT + padding, glassR - padding, cY, crosshairPaint)
                canvas.drawLine(glassL + padding, glassB - padding, glassR - padding, cY, crosshairPaint)
                canvas.drawLine(glassL + padding, glassB - padding, cX, glassT + padding, crosshairPaint)
                canvas.drawLine(glassR - padding, glassB - padding, cX, glassT + padding, crosshairPaint)
            }

            else -> {
                val radius = Math.min(paneW, paneH) * 0.28f
                canvas.drawCircle(cX, cY, radius, circleCenterPaint)

                val crossLength = radius * 1.5f
                canvas.drawLine(cX - crossLength, cY, cX + crossLength, cY, crosshairPaint)
                canvas.drawLine(cX, cY - crossLength, cX, cY + crossLength, crosshairPaint)
            }
        }
    }

    private fun flecha(canvas: Canvas, cx: Float, cy: Float, dx: Float, dy: Float, largo: Float) {
        val cab = dpToPx(4f)
        val x1 = cx - dx * largo / 2f; val y1 = cy - dy * largo / 2f
        val x2 = cx + dx * largo / 2f; val y2 = cy + dy * largo / 2f
        canvas.drawLine(x1, y1, x2, y2, crosshairPaint)
        // cabeza: dos trazos hacia atrás desde la punta
        val px = -dy; val py = dx
        canvas.drawLine(x2, y2, x2 - dx * cab + px * cab, y2 - dy * cab + py * cab, crosshairPaint)
        canvas.drawLine(x2, y2, x2 - dx * cab - px * cab, y2 - dy * cab - py * cab, crosshairPaint)
    }

    /**
     * Vidrio de una hoja con imposta interior: 2 o 3 vidrios con la(s) imposta(s) en la posición guardada
     * (o repartidos a partes iguales). [imp]: 1 hor. x1, 2 hor. x2, 3 ver. x1, 4 ver. x2; [mask] = bits de sándwich por vidrio.
     */
    private fun dibujarVidriosImposta(canvas: Canvas, glassL: Float, glassT: Float, glassR: Float, glassB: Float, paneIndex: Int, imp: Int, mask: Int) {
        // Imposta interior de la hoja: 2 o 3 vidrios repartidos a partes iguales
        val horizontal = imp <= 2
        val n = if (imp % 2 == 1) 2 else 3
        val bar = dpToPx(3f)
        val total = if (horizontal) glassB - glassT else glassR - glassL
        // Posición de las impostas: reparto igual o los offsets (mm) guardados para el paño
        val offs = paneImpOff[paneIndex]?.split(",")?.map { it.trim().toFloatOrNull() } ?: emptyList()
        fun corte(k: Int): Float {
            val porDefecto = total * (k + 1) / n
            val o0 = offs.getOrNull(0); val o1 = offs.getOrNull(1)
            val v = when (imp) {
                1 -> o0?.let { total - it * pxPorMm }
                2 -> if (k == 0) o0?.let { it * pxPorMm } else o1?.let { total - it * pxPorMm }
                3 -> o0?.let { it * pxPorMm }
                else -> if (k == 0) o0?.let { it * pxPorMm } else o1?.let { total - it * pxPorMm }
            } ?: porDefecto
            return v.coerceIn(total * 0.08f, total * 0.92f)
        }
        val bordes = FloatArray(n + 1) { k -> if (k == 0) 0f else if (k == n) total else corte(k - 1) }
        for (i in 0 until n) {
            val a = bordes[i] + (if (i > 0) bar / 2 else 0f)
            val b = bordes[i + 1] - (if (i < n - 1) bar / 2 else 0f)
            val r = if (horizontal) RectF(glassL, glassT + a, glassR, glassT + b) else RectF(glassL + a, glassT, glassL + b, glassB)
            canvas.drawRect(r, if (((mask shr i) and 1) == 1) sandwichPaint else glassPaint)
            canvas.drawRect(r, frameBorderPaint)
            dibujarBarrasVidrio(canvas, r, paneIndex, i)
        }
        for (i in 1 until n) {
            val c = bordes[i]
            val r = if (horizontal) RectF(glassL, glassT + c - bar / 2, glassR, glassT + c + bar / 2)
            else RectF(glassL + c - bar / 2, glassT, glassL + c + bar / 2, glassB)
            canvas.drawRect(r, framePaint)
            canvas.drawRect(r, frameBorderPaint)
        }
    }

    /**
     * Apertura de varias hojas (corredizas múltiples/verticales y puerta francesa) con las medidas:
     * cada hoja es un contorno con su vidrio, flecha de corrida (o bisagras en la francesa) y manija.
     */
    private fun dibujarMulti(canvas: Canvas, pL: Float, pT: Float, pR: Float, pB: Float, paneIndex: Int, row: Int, col: Int) {
        val mec = paneMechanisms[paneIndex] ?: return
        val isPuerta = panePuerta[paneIndex] == true
        val isSandwich = paneSandwich[paneIndex] == true
        val isMosq = paneMosqNet[paneIndex] == true
        val pxM = (pxPorMm * 1000f).coerceAtLeast(1f)
        val wM = (pR - pL) / pxM.toDouble()
        val hM = (pB - pT) / pxM.toDouble()
        val hojas = AperturasMulti.hojas(mec, wM, hM, isPuerta)
        val impMulti = (paneCols[paneIndex] ?: 0).let { if (it in 11..14) it - 10 else 0 }.takeIf { AperturasMulti.impostaPermitida(mec, it) } ?: 0
        val sinContornoPano = AperturasMulti.grupo(mec) == AperturasMulti.Grupo.FRANCESA
        if (showShadows && !sinContornoPano) sombraProyectada(canvas, pL, pT, pR, pB, dpToPx(1.2f))
        if (!sinContornoPano) canvas.drawRect(pL, pT, pR, pB, frameBorderPaint)
        val esFrancesa = AperturasMulti.grupo(mec) == AperturasMulti.Grupo.FRANCESA
        val basc = if (esFrancesa) AperturasMulti.francesaBasculante(AperturasMulti.clave(mec)) else null
        val vertical = AperturasMulti.grupo(mec) == AperturasMulti.Grupo.CORREDIZA_VERT
        for ((i, h) in hojas.withIndex()) {
            val gl = pL + (h.x * pxM).toFloat(); val gt = pT + (h.y * pxM).toFloat()
            val gr = gl + (h.ancho * pxM).toFloat(); val gb = gt + (h.alto * pxM).toFloat()
            val pf = (h.perfil * pxM).toFloat()
            val ol = Math.max(pL, gl - pf); val ot = Math.max(pT, gt - pf); val orr = Math.min(pR, gr + pf); val ob = Math.min(pB, gb + pf)
            if (esFrancesa) {
                // Dos hojas iguales que se reparten el paño, con ~26 mm de separación (como dos paños practicables)
                val mitad = (pL + pR) / 2f
                val separacion = 26f * pxPorMm
                if (gr > gl && gb > gt) dibujarHojaFrancesa(canvas, if (i == 0) pL else mitad + separacion / 2f, pT, if (i == 0) mitad - separacion / 2f else pR, pB, paneIndex, i, h.manija, basc, row, col)
                continue
            }
            canvas.drawRect(ol, ot, orr, ob, framePaint)
            canvas.drawRect(ol, ot, orr, ob, frameBorderPaint)
            if (gr <= gl || gb <= gt) continue
            // Cada hoja se configura por separado (sándwich por bit; sin dato = el del paño)
            if (impMulti > 0) {
                // Imposta interior: cada hoja se parte igual y los datos (sándwich, barras) son por vidrio, iguales en todas las hojas
                dibujarVidriosImposta(canvas, gl, gt, gr, gb, paneIndex, impMulti, paneSandwichSub[paneIndex] ?: (if (isSandwich) 7 else 0))
            } else {
                val hojaSandwich = paneSandwichSub[paneIndex]?.let { ((it shr i) and 1) == 1 } ?: isSandwich
                canvas.drawRect(gl, gt, gr, gb, if (hojaSandwich) sandwichPaint else glassPaint)
                canvas.drawRect(gl, gt, gr, gb, frameBorderPaint)
                dibujarBarrasVidrio(canvas, RectF(gl, gt, gr, gb), paneIndex, i)
            }
            if (isMosq && h.movil) {
                val sx = (gr - gl) / 14f; val sy = (gb - gt) / 14f
                for (k in 1..13) {
                    canvas.drawLine(gl + k * sx, gt, gl + k * sx, gb, meshPaint)
                    canvas.drawLine(gl, gt + k * sy, gr, gt + k * sy, meshPaint)
                }
            }
            val cx = (gl + gr) / 2f; val cy = (gt + gb) / 2f
            val ancho = gr - gl; val alto = gb - gt
            if (ancho > dpToPx(4f) && alto > dpToPx(4f)) {
                if (esFrancesa) {
                    val izq = i == 0
                    val pad = dpToPx(2f)
                    // manijas al centro: el vértice del símbolo apunta hacia ellas
                    if (izq) {
                        canvas.drawLine(gl + pad, gt + pad, gr - pad, cy, crosshairPaint); canvas.drawLine(gl + pad, gb - pad, gr - pad, cy, crosshairPaint)
                    } else {
                        canvas.drawLine(gr - pad, gt + pad, gl + pad, cy, crosshairPaint); canvas.drawLine(gr - pad, gb - pad, gl + pad, cy, crosshairPaint)
                    }
                    if ((izq && basc == 'L') || (!izq && basc == 'R')) {
                        canvas.drawLine(gl + pad, gb - pad, cx, gt + pad, crosshairPaint); canvas.drawLine(gr - pad, gb - pad, cx, gt + pad, crosshairPaint)
                    }
                } else if (h.movil) {
                    val (dx, dy) = when (h.dir) { 'L' -> -1f to 0f; 'R' -> 1f to 0f; 'U' -> 0f to -1f; else -> 0f to 1f }
                    flecha(canvas, cx, cy, dx, dy, (if (dx != 0f) ancho else alto) * 0.4f)
                }
            }
            if (h.manija && ancho > dpToPx(10f) && alto > dpToPx(10f)) {
                val m = when {
                    esFrancesa -> if (i == 0) "1" else "3"
                    vertical -> if (h.dir == 'U') "9" else "2"
                    else -> if (h.dir == 'L') "3" else "1"
                }
                dibujarManija(canvas, m, gl, gt, gr, gb, pf.coerceIn(dpToPx(2f), dpToPx(8f)), esFrancesa)
            }
        }
        if (showTechnicalDetails) paneAreas.add(PaneArea(RectF(pL, pT, pR, pB), paneIndex, row, col))
    }

    /**
     * Una hoja de la puerta francesa se dibuja con el mismo estilo de un paño normal (perfil con sombra, vidrio con
     * marco, símbolo de apertura y manija): se le asigna un paño temporal con el equivalente de la hoja
     * ("6"/"8" la izquierda, con la manija hacia el centro; "4"/"7" la derecha) y se dibuja con [drawSinglePane].
     */
    private fun dibujarHojaFrancesa(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, paneIndex: Int, i: Int, manija: Boolean, basc: Char?, row: Int, col: Int) {
        val tmp = 100000 + paneIndex * 10 + i
        paneMechanisms[tmp] = if (i == 0) (if (basc == 'L') "8" else "6") else (if (basc == 'R') "7" else "4")
        panePuerta[tmp] = false
        val sandwichHoja = paneSandwichSub[paneIndex]?.let { ((it shr i) and 1) == 1 } ?: (paneSandwich[paneIndex] == true)
        paneSandwich[tmp] = sandwichHoja
        paneMosqNet[tmp] = paneMosqNet[paneIndex] == true
        paneCols[paneIndex]?.let { paneCols[tmp] = it }
        paneSandwichSub[paneIndex]?.let { paneSandwichSub[tmp] = it }
        paneBars[paneIndex]?.trim()?.split(" ")?.filter { it.isNotBlank() }?.getOrNull(i)?.let { paneBars[tmp] = it }
        if (!manija) hojasSinManija.add(tmp)
        val areas = paneAreas.size
        drawSinglePane(canvas, l, t, r, b, tmp, row, col)
        while (paneAreas.size > areas) paneAreas.removeAt(paneAreas.size - 1)
        hojasSinManija.remove(tmp)
        paneMechanisms.remove(tmp); panePuerta.remove(tmp); paneSandwich.remove(tmp); paneMosqNet.remove(tmp)
        paneCols.remove(tmp); paneSandwichSub.remove(tmp); paneBars.remove(tmp)
    }

    // ---------- herrajes: manijas y bisagras con detalle ----------
    private val metalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val metalBordePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A4A4A"); style = Paint.Style.STROKE; strokeWidth = dpToPx(0.7f)
    }
    private val manijaSombraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(50, 0, 0, 0); style = Paint.Style.FILL }
    private val herrajeSombraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 0, 0, 0); style = Paint.Style.FILL }
    private val herrajeDetallePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#5E5E5E"); style = Paint.Style.FILL }
    private val herrajeBrilloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = dpToPx(0.7f); strokeCap = Paint.Cap.ROUND
    }

    /** Relleno metálico con degradado (claro, brillo, sombra) a lo ancho de [r] o, si [alAncho] es false, a lo alto. */
    private fun metalizar(r: RectF, alAncho: Boolean, oscuro: Boolean = false): Paint {
        val c = if (oscuro) intArrayOf(0xFF8E8E8E.toInt(), 0xFFE2E2E2.toInt(), 0xFF7C7C7C.toInt())
        else intArrayOf(0xFFCFCFCF.toInt(), 0xFFFFFFFF.toInt(), 0xFFB0B0B0.toInt())
        val pos = floatArrayOf(0f, 0.42f, 1f)
        metalPaint.shader = if (alAncho) LinearGradient(r.left, 0f, r.right, 0f, c, pos, Shader.TileMode.CLAMP)
        else LinearGradient(0f, r.top, 0f, r.bottom, c, pos, Shader.TileMode.CLAMP)
        return metalPaint
    }

    private fun pieza(canvas: Canvas, r: RectF, radio: Float, alAncho: Boolean = true, oscuro: Boolean = false, sombra: Boolean = true) {
        if (sombra) {
            val o = dpToPx(0.8f)
            canvas.drawRoundRect(RectF(r.left + o, r.top + o, r.right + o, r.bottom + o), radio, radio, herrajeSombraPaint)
        }
        canvas.drawRoundRect(r, radio, radio, metalizar(r, alAncho, oscuro))
        canvas.drawRoundRect(r, radio, radio, metalBordePaint)
    }

    /** Lado de la hoja donde van las bisagras (el contrario a la manija en las hojas que giran); null si no lleva. */
    private fun ladoBisagra(mec: String, puerta: Boolean): Char? = when (mec) {
        "4", "7" -> 'R'
        "6", "8" -> 'L'
        "2" -> 'B'
        "9" -> 'T'
        "1", "3", "10" -> null
        else -> if (puerta) 'L' else null
    }

    /** Bisagras (2 o 3 charnelas) sobre el perfil de la hoja, en el [lado] indicado ('L', 'R', 'T' o 'B'). */
    private fun dibujarBisagras(canvas: Canvas, lado: Char, gL: Float, gT: Float, gR: Float, gB: Float, grosor: Float, cantidad: Int) {
        val horizontal = lado == 'T' || lado == 'B'
        val largoLado = if (horizontal) gR - gL else gB - gT
        if (largoLado < dpToPx(36f)) return
        val g = grosor.coerceIn(dpToPx(3f), dpToPx(8f))
        val w = g * 0.8f
        val l = maxOf(g * 2.2f, dpToPx(6f))
        val fracs = if (cantidad >= 3) floatArrayOf(0.12f, 0.5f, 0.88f) else floatArrayOf(0.14f, 0.86f)
        for (f in fracs) {
            val r = when (lado) {
                'L' -> { val x = gL - grosor / 2f; val y = gT + largoLado * f; RectF(x - w / 2f, y - l / 2f, x + w / 2f, y + l / 2f) }
                'R' -> { val x = gR + grosor / 2f; val y = gT + largoLado * f; RectF(x - w / 2f, y - l / 2f, x + w / 2f, y + l / 2f) }
                'T' -> { val y = gT - grosor / 2f; val x = gL + largoLado * f; RectF(x - l / 2f, y - w / 2f, x + l / 2f, y + w / 2f) }
                else -> { val y = gB + grosor / 2f; val x = gL + largoLado * f; RectF(x - l / 2f, y - w / 2f, x + l / 2f, y + w / 2f) }
            }
            pieza(canvas, r, dpToPx(0.8f), alAncho = !horizontal, oscuro = true)
            // charnela: la línea del eje y los dos tornillos
            if (horizontal) canvas.drawLine(r.centerX(), r.top, r.centerX(), r.bottom, metalBordePaint)
            else canvas.drawLine(r.left, r.centerY(), r.right, r.centerY(), metalBordePaint)
            val t = w * 0.12f
            if (horizontal) { canvas.drawCircle(r.left + l * 0.18f, r.centerY(), t, herrajeDetallePaint); canvas.drawCircle(r.right - l * 0.18f, r.centerY(), t, herrajeDetallePaint) }
            else { canvas.drawCircle(r.centerX(), r.top + l * 0.18f, t, herrajeDetallePaint); canvas.drawCircle(r.centerX(), r.bottom - l * 0.18f, t, herrajeDetallePaint) }
        }
    }

    /**
     * Manija sencilla de la hoja (la que se usa): placa blanca con borde fino y palanca que cuelga del eje, con su sombra.
     * Va en el lado contrario a la bisagra: "4"/"7"/"3" izquierda, "6"/"8"/"1" derecha, "2" arriba y "9" abajo (palanca de lado).
     *
     * Medidas reales (mm, tomadas de las capturas del original): placa 37 x 80, palanca de 25 de ancho que arranca a 27 del
     * borde de la placa y llega a 160 en total (90 en corredizas; igual en puertas). Se dibuja a la escala de la ventana
     * ([pxPorMm]) pero se acota para que siempre sea coherente con la hoja: no pasa del 30 % del lado donde va, la placa
     * cabe dentro del perfil y, si quedaría demasiado pequeña para verse, no se dibuja.
     */
    private fun dibujarManija(canvas: Canvas, mec: String, gL: Float, gT: Float, gR: Float, gB: Float, grosorHoja: Float, puerta: Boolean = false) {
        val cx = (gL + gR) / 2f
        val cy = (gT + gB) / 2f
        val lado = when (mec) { "4", "7", "3" -> 'L'; "2" -> 'T'; "9" -> 'B'; else -> 'R' }
        val horizontal = lado == 'T' || lado == 'B'
        val largoLado = if (horizontal) gR - gL else gB - gT
        val deslizante = (mec == "1" || mec == "3") && !puerta
        val totalMm = if (deslizante) 90f else 160f
        var k = pxPorMm
        k = minOf(k, largoLado * 0.30f / totalMm)      // como máximo el 30 % del lado de la hoja
        k = minOf(k, grosorHoja * 0.8f / 37f)          // la placa cabe en el perfil
        if (totalMm * k < dpToPx(6f)) return           // demasiado pequeña para verse
        val mx: Float
        val my: Float
        when (lado) {
            'L' -> { mx = gL - grosorHoja / 2f; my = cy }
            'T' -> { mx = cx; my = gT - grosorHoja / 2f }
            'B' -> { mx = cx; my = gB + grosorHoja / 2f }
            else -> { mx = gR + grosorHoja / 2f; my = cy }
        }
        val placa = RectF(-18.5f * k, -40f * k, 18.5f * k, 40f * k)
        val palanca = RectF(-12.5f * k, -13f * k, 12.5f * k, (totalMm - 40f) * k)
        canvas.save()
        canvas.translate(mx, my)
        if (horizontal) canvas.rotate(-90f)
        if (showShadows) {
            // Silueta de la sombra medida del original (u = 1/54 del ancho de la placa): la placa casi no se despega de la hoja
            // y la palanca, que sobresale, proyecta un bloque desplazado abajo-derecha unido a la placa con una diagonal.
            val u = placa.width() / 54f
            val sombra = android.graphics.Path()
            sombra.moveTo(placa.right, placa.top + 2 * u)
            sombra.lineTo(placa.right + 11 * u, placa.top + 13 * u)
            sombra.lineTo(placa.right + 11 * u, placa.top + 62 * u)
            sombra.lineTo(palanca.right + 49 * u, placa.top + 91 * u)
            sombra.lineTo(palanca.right + 49 * u, palanca.bottom + 49 * u)
            sombra.lineTo(palanca.right + 15 * u, palanca.bottom + 49 * u)
            sombra.lineTo(palanca.right + 15 * u, placa.bottom)
            sombra.lineTo(placa.right, placa.bottom)
            sombra.close()
            canvas.drawPath(sombra, manijaSombraPaint)
        }
        canvas.drawRect(placa, handlePlatePaint); canvas.drawRect(placa, metalBordePaint)
        canvas.drawRect(palanca, handlePlatePaint); canvas.drawRect(palanca, metalBordePaint)
        canvas.restore()
    }

    /** (Sin uso) Versión con roseta, tornillos, eje, palanca metálica y cilindro de llave; se conserva por si se necesita. */
    private fun dibujarManijaDetallada(canvas: Canvas, mec: String, gL: Float, gT: Float, gR: Float, gB: Float, grosorHoja: Float, puerta: Boolean = false) {
        val cx = (gL + gR) / 2f
        val cy = (gT + gB) / 2f
        val lado = when (mec) { "4", "7", "3" -> 'L'; "2" -> 'T'; "9" -> 'B'; else -> 'R' }
        val horizontal = lado == 'T' || lado == 'B'
        val largoLado = if (horizontal) gR - gL else gB - gT
        var g = grosorHoja.coerceIn(dpToPx(3f), dpToPx(8f)) * (if (puerta) 1.15f else 1f)
        g = minOf(g, largoLado * 0.42f / (if (puerta) 6f else 4.8f))
        if (g < dpToPx(1.2f)) return
        val mx: Float
        val my: Float
        when (lado) {
            'L' -> { mx = gL - grosorHoja / 2f; my = cy }
            'T' -> { mx = cx; my = gT - grosorHoja / 2f }
            'B' -> { mx = cx; my = gB + grosorHoja / 2f }
            else -> { mx = gR + grosorHoja / 2f; my = cy }
        }
        canvas.save()
        canvas.translate(mx, my)
        if (horizontal) canvas.rotate(-90f)
        val deslizante = (mec == "1" || mec == "3") && !puerta
        if (deslizante) {
            // tirador embutido con gancho de cierre
            val placa = RectF(-g * 0.55f, -g * 2.2f, g * 0.55f, g * 2.2f)
            pieza(canvas, placa, g * 0.3f)
            canvas.drawRoundRect(RectF(-g * 0.2f, -g * 1.6f, g * 0.2f, g * 0.5f), g * 0.2f, g * 0.2f, herrajeDetallePaint)
            val gancho = RectF(-g * 0.34f, g * 0.9f, g * 0.34f, g * 1.7f)
            pieza(canvas, gancho, g * 0.15f, oscuro = true, sombra = false)
        } else {
            val roseta = RectF(-g * 0.6f, -g * 1.5f, g * 0.6f, g * 1.5f)
            pieza(canvas, roseta, g * 0.3f)
            canvas.drawCircle(0f, -g * 1.1f, g * 0.13f, herrajeDetallePaint)
            canvas.drawCircle(0f, g * 1.1f, g * 0.13f, herrajeDetallePaint)
            // palanca que cuelga del eje
            val palanca = RectF(-g * 0.3f, -g * 0.1f, g * 0.3f, g * (if (puerta) 4.4f else 3.6f))
            pieza(canvas, palanca, g * 0.3f)
            canvas.drawLine(-g * 0.12f, g * 0.6f, -g * 0.12f, palanca.bottom - g * 0.4f, herrajeBrilloPaint)
            // eje
            val eje = RectF(-g * 0.4f, -g * 0.4f, g * 0.4f, g * 0.4f)
            canvas.drawCircle(0f, 0f, g * 0.4f, metalizar(eje, true))
            canvas.drawCircle(0f, 0f, g * 0.4f, metalBordePaint)
            canvas.drawCircle(-g * 0.1f, -g * 0.1f, g * 0.12f, herrajeBrilloPaint.apply { style = Paint.Style.FILL })
            herrajeBrilloPaint.style = Paint.Style.STROKE
            if (puerta) {
                // cilindro de llave sobre la roseta
                val cil = RectF(-g * 0.42f, -g * 2.9f, g * 0.42f, -g * 2.06f)
                canvas.drawCircle(0f, -g * 2.48f, g * 0.42f, metalizar(cil, true, oscuro = true))
                canvas.drawCircle(0f, -g * 2.48f, g * 0.42f, metalBordePaint)
                canvas.drawCircle(0f, -g * 2.56f, g * 0.1f, herrajeDetallePaint)
                canvas.drawRect(-g * 0.04f, -g * 2.56f, g * 0.04f, -g * 2.2f, herrajeDetallePaint)
            }
        }
        canvas.restore()
        metalPaint.shader = null
    }

    private val barraVidrioPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FAFAFA")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.2f)
    }

    private val barraVidrioBordePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#555555")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2.4f)
    }

    /** Barras de vidrio (glazing bars): n barras repartidas a partes iguales en cada dirección del vidrio [i] del paño. */
    private fun dibujarBarrasVidrio(canvas: Canvas, r: RectF, paneIndex: Int, i: Int) {
        val partes = paneBars[paneIndex]?.trim()?.split(" ")?.filter { it.isNotBlank() } ?: return
        val hv = partes.getOrNull(i)?.split("x")?.map { it.toIntOrNull() ?: 0 } ?: return
        val h = hv.getOrNull(0) ?: 0
        val vv = hv.getOrNull(1) ?: 0
        if (h + vv == 0) return
        for (pase in 0..1) {
            val paint = if (pase == 0) barraVidrioBordePaint else barraVidrioPaint
            for (k in 1..h) { val y = r.top + r.height() * k / (h + 1); canvas.drawLine(r.left, y, r.right, y, paint) }
            for (k in 1..vv) { val x = r.left + r.width() * k / (vv + 1); canvas.drawLine(x, r.top, x, r.bottom, paint) }
        }
    }

    private fun getActiveLayoutRows(cfg: WindowConfig): Int = cfg.filasActivas()

    private fun getActiveLayoutCols(cfg: WindowConfig): Int = cfg.colsActivas()

    private fun drawGrid(canvas: Canvas, rect: RectF, cols: Int, rows: Int, t: Float) {
        canvas.drawRect(rect, marcoPintura())
        canvas.drawRect(rect, frameBorderPaint)

        val innerL = rect.left + t
        val innerT = rect.top + t
        val innerR = rect.right - t
        val innerB = rect.bottom - t
        val totalInnerH = innerB - innerT
        val totalInnerW = innerR - innerL

        val activeHeights = getScaledActiveHeights(rows)
        val activeWidths = getScaledActiveWidths(cols)

        val totalHVal = activeHeights.sum().coerceAtLeast(1f)
        val totalWVal = activeWidths.sum().coerceAtLeast(1f)

        var currentY = innerT
        val padding = dpToPx(1f)
        // Entre dos paños vecinos queda ~26 mm (13 mm por lado), como en el original; pegados al marco, casi nada
        val mitadSep = mitadSeparacion().coerceAtLeast(padding)

        var pIdx = 0
        for (r in 0 until rows) {
            val rowRatio = activeHeights[r] / totalHVal
            val cellH = totalInnerH * rowRatio
            val pT = currentY + (if (r == 0) padding else mitadSep)
            val pB = (currentY + cellH - (if (r == rows - 1) padding else mitadSep)).coerceAtMost(innerB - padding)

            var currentX = innerL
            for (c in 0 until cols) {
                val colRatio = activeWidths[c] / totalWVal
                val cellW = totalInnerW * colRatio
                val pL = currentX + (if (c == 0) padding else mitadSep)
                val pR = (currentX + cellW - (if (c == cols - 1) padding else mitadSep)).coerceAtMost(innerR - padding)

                if (pR > pL && pB > pT) {
                    drawPaneWithMechanism(canvas, pL, pT, pR, pB, paneIndex = pIdx, row = r, col = c)
                    if (c < cols - 1) rematesSeparacion(canvas, true, pR, pR + 2 * mitadSep, pT + mitadSep, pB - mitadSep)
                    if (r < rows - 1) rematesSeparacion(canvas, false, pB, pB + 2 * mitadSep, pL + mitadSep, pR - mitadSep)
                }
                pIdx++
                currentX += cellW
            }
            currentY += cellH
        }
    }

    private fun drawTopTransom(canvas: Canvas, rect: RectF, cols: Int, t: Float) {
        canvas.drawRect(rect, marcoPintura())
        canvas.drawRect(rect, frameBorderPaint)

        val innerL = rect.left + t
        val innerT = rect.top + t
        val innerR = rect.right - t
        val innerB = rect.bottom - t

        val activeHeights = getScaledActiveHeights(2)
        val totalHSum = activeHeights.sum().coerceAtLeast(1f)
        val transomRatio = activeHeights[0] / totalHSum
        val transomH = (innerB - innerT) * transomRatio
        val padding = dpToPx(1f)
        val sep = mitadSeparacion().coerceAtLeast(padding)   // entre paños vecinos

        drawPaneWithMechanism(canvas, innerL + padding, innerT + padding, innerR - padding, innerT + transomH - sep, paneIndex = 0)

        val cellW = (innerR - innerL) / cols
        val bottomT = innerT + transomH + sep

        for (c in 0 until cols) {
            val pL = innerL + c * cellW + (if (c == 0) padding else sep)
            val pR = (innerL + (c + 1) * cellW - (if (c == cols - 1) padding else sep)).coerceAtMost(innerR - padding)
            drawPaneWithMechanism(canvas, pL, bottomT, pR, innerB - padding, paneIndex = c + 1)
        }
    }

    private fun drawBottomTransom(canvas: Canvas, rect: RectF, cols: Int, t: Float) {
        canvas.drawRect(rect, marcoPintura())
        canvas.drawRect(rect, frameBorderPaint)

        val innerL = rect.left + t
        val innerT = rect.top + t
        val innerR = rect.right - t
        val innerB = rect.bottom - t

        val activeHeights = getScaledActiveHeights(2)
        val totalHSum = activeHeights.sum().coerceAtLeast(1f)
        val mainRatio = activeHeights[0] / totalHSum
        val topB = innerT + (innerB - innerT) * mainRatio
        val padding = dpToPx(1f)
        val sep = mitadSeparacion().coerceAtLeast(padding)   // entre paños vecinos

        val cellW = (innerR - innerL) / cols
        for (c in 0 until cols) {
            val pL = innerL + c * cellW + (if (c == 0) padding else sep)
            val pR = (innerL + (c + 1) * cellW - (if (c == cols - 1) padding else sep)).coerceAtMost(innerR - padding)
            drawPaneWithMechanism(canvas, pL, innerT + padding, pR, topB - sep, paneIndex = c)
        }

        drawPaneWithMechanism(canvas, innerL + padding, topB + sep, innerR - padding, innerB - padding, paneIndex = cols)
    }

    private fun drawTopBottomTransom(canvas: Canvas, rect: RectF, cols: Int, t: Float) {
        canvas.drawRect(rect, marcoPintura())
        canvas.drawRect(rect, frameBorderPaint)

        val innerL = rect.left + t
        val innerT = rect.top + t
        val innerR = rect.right - t
        val innerB = rect.bottom - t

        val activeHeights = getScaledActiveHeights(3)
        val totalHSum = activeHeights.sum().coerceAtLeast(1f)

        val topRatio = activeHeights[0] / totalHSum
        val midRatio = activeHeights[1] / totalHSum

        val totalH = innerB - innerT
        val topH = totalH * topRatio
        val midH = totalH * midRatio
        val padding = dpToPx(1f)
        val sep = mitadSeparacion().coerceAtLeast(padding)   // entre paños vecinos

        drawPaneWithMechanism(canvas, innerL + padding, innerT + padding, innerR - padding, innerT + topH - sep, paneIndex = 0)

        val cellW = (innerR - innerL) / cols
        val midT = innerT + topH + sep
        val midB = midT + midH - sep * 2f

        for (c in 0 until cols) {
            val pL = innerL + c * cellW + (if (c == 0) padding else sep)
            val pR = (innerL + (c + 1) * cellW - (if (c == cols - 1) padding else sep)).coerceAtMost(innerR - padding)
            drawPaneWithMechanism(canvas, pL, midT, pR, midB, paneIndex = c + 1)
        }

        drawPaneWithMechanism(canvas, innerL + padding, midB + sep * 2f, innerR - padding, innerB - padding, paneIndex = cols + 1)
    }

    private fun drawDoorL(canvas: Canvas, rect: RectF, isLeftDoor: Boolean, hasTransom: Boolean, t: Float) {
        val splitX = if (isLeftDoor) rect.left + (rect.width() * 0.45f) else rect.left + (rect.width() * 0.55f)

        val activeHeights = getScaledActiveHeights(2)
        val totalHSum = activeHeights.sum().coerceAtLeast(1f)
        val mainRatio = activeHeights[0] / totalHSum
        val windowBottomY = rect.top + (rect.height() * mainRatio)

        if (isLeftDoor) {
            canvas.drawRect(rect.left, rect.top, splitX, rect.bottom, marcoPintura())
            canvas.drawRect(rect.left, rect.top, splitX, rect.bottom, frameBorderPaint)
            canvas.drawRect(splitX, rect.top, rect.right, windowBottomY, marcoPintura())
            canvas.drawRect(splitX, rect.top, rect.right, windowBottomY, frameBorderPaint)
        } else {
            canvas.drawRect(rect.left, rect.top, splitX, windowBottomY, marcoPintura())
            canvas.drawRect(rect.left, rect.top, splitX, windowBottomY, frameBorderPaint)
            canvas.drawRect(splitX, rect.top, rect.right, rect.bottom, marcoPintura())
            canvas.drawRect(splitX, rect.top, rect.right, rect.bottom, frameBorderPaint)
        }

        val iL = rect.left + t
        val iT = rect.top + t
        val iR = rect.right - t
        val iB = rect.bottom - t
        val padding = dpToPx(1f)
        val sep = mitadSeparacion().coerceAtLeast(padding)   // entre paños vecinos

        val transomH = if (hasTransom) (iB - iT) * 0.22f else 0f

        if (isLeftDoor) {
            if (hasTransom) {
                drawPaneWithMechanism(canvas, iL + padding, iT + padding, splitX - sep, iT + transomH - sep, paneIndex = 0)
                drawPaneWithMechanism(canvas, iL + padding, iT + transomH + sep, splitX - sep, iB - padding, paneIndex = 1)
            } else {
                drawPaneWithMechanism(canvas, iL + padding, iT + padding, splitX - sep, iB - padding, paneIndex = 0)
            }
            if (hasTransom) {
                drawPaneWithMechanism(canvas, splitX + sep, iT + padding, iR - padding, iT + transomH - sep, paneIndex = 2)
                drawPaneWithMechanism(canvas, splitX + sep, iT + transomH + sep, iR - padding, windowBottomY - t - padding, paneIndex = 3)
            } else {
                drawPaneWithMechanism(canvas, splitX + sep, iT + padding, iR - padding, windowBottomY - t - padding, paneIndex = 1)
            }
        } else {
            if (hasTransom) {
                drawPaneWithMechanism(canvas, iL + padding, iT + padding, splitX - sep, iT + transomH - sep, paneIndex = 0)
                drawPaneWithMechanism(canvas, iL + padding, iT + transomH + sep, splitX - sep, windowBottomY - t - padding, paneIndex = 1)
            } else {
                drawPaneWithMechanism(canvas, iL + padding, iT + padding, splitX - sep, windowBottomY - t - padding, paneIndex = 0)
            }
            if (hasTransom) {
                drawPaneWithMechanism(canvas, splitX + sep, iT + padding, iR - padding, iT + transomH - sep, paneIndex = 2)
                drawPaneWithMechanism(canvas, splitX + sep, iT + transomH + sep, iR - padding, iB - padding, paneIndex = 3)
            } else {
                drawPaneWithMechanism(canvas, splitX + sep, iT + padding, iR - padding, iB - padding, paneIndex = 1)
            }
        }
    }

    private fun drawDoorT(canvas: Canvas, rect: RectF, hasTransom: Boolean, t: Float) {
        val splitLeftX = rect.left + (rect.width() * 0.33f)
        val splitRightX = rect.left + (rect.width() * 0.67f)

        val activeHeights = getScaledActiveHeights(2)
        val totalHSum = activeHeights.sum().coerceAtLeast(1f)
        val topRatio = activeHeights[0] / totalHSum
        val windowBottomY = rect.top + (rect.height() * topRatio)

        if (hasTransom) {
            canvas.drawRect(rect.left, rect.top, rect.right, windowBottomY, marcoPintura())
            canvas.drawRect(rect.left, rect.top, rect.right, windowBottomY, frameBorderPaint)
            canvas.drawRect(splitLeftX, windowBottomY, splitRightX, rect.bottom, marcoPintura())
            canvas.drawRect(splitLeftX, windowBottomY, splitRightX, rect.bottom, frameBorderPaint)
        } else {
            canvas.drawRect(rect.left, rect.top, splitLeftX, windowBottomY, marcoPintura())
            canvas.drawRect(rect.left, rect.top, splitLeftX, windowBottomY, frameBorderPaint)
            canvas.drawRect(splitLeftX, rect.top, splitRightX, rect.bottom, marcoPintura())
            canvas.drawRect(splitLeftX, rect.top, splitRightX, rect.bottom, frameBorderPaint)
            canvas.drawRect(splitRightX, rect.top, rect.right, windowBottomY, marcoPintura())
            canvas.drawRect(splitRightX, rect.top, rect.right, windowBottomY, frameBorderPaint)
        }

        val iL = rect.left + t
        val iT = rect.top + t
        val iR = rect.right - t
        val iB = rect.bottom - t
        val padding = dpToPx(1f)
        val sep = mitadSeparacion().coerceAtLeast(padding)   // entre paños vecinos

        if (hasTransom) {
            drawPaneWithMechanism(canvas, iL + padding, iT + padding, iR - padding, windowBottomY - sep, paneIndex = 0)
            drawPaneWithMechanism(canvas, iL + padding, windowBottomY + sep, splitLeftX - sep, iB - padding, paneIndex = 1)
            drawPaneWithMechanism(canvas, splitLeftX + sep, windowBottomY + sep, splitRightX - sep, iB - padding, paneIndex = 2)
            drawPaneWithMechanism(canvas, splitRightX + sep, windowBottomY + sep, iR - padding, iB - padding, paneIndex = 3)
        } else {
            drawPaneWithMechanism(canvas, iL + padding, iT + padding, splitLeftX - sep, windowBottomY - sep, paneIndex = 0)
            drawPaneWithMechanism(canvas, splitRightX + sep, iT + padding, iR - padding, windowBottomY - sep, paneIndex = 2)
            drawPaneWithMechanism(canvas, splitLeftX + sep, iT + padding, splitRightX - sep, iB - padding, paneIndex = 1)
        }
    }

    /**
     * Modelos: cada paño se dibuja con la estructura exacta decodificada de su fórmula (ver ModelosVentana).
     * Los huecos vacíos (ventanas en L / T) no se dibujan; los lados que dan al vacío o al exterior llevan el marco completo.
     */
    private fun drawArbol(canvas: Canvas, rect: RectF, cfg: WindowConfig, t: Float) {
        val mo = cfg.modeloEstructura ?: return
        val w = anchoTotalMm.toFloat().coerceAtLeast(1f)
        val h = altoTotalMm.toFloat().coerceAtLeast(1f)
        val k = minOf(rect.width() / w, rect.height() / h)
        val ox = rect.left + (rect.width() - w * k) / 2f
        val oy = rect.top + (rect.height() - h * k) / 2f
        pxPorMm = k
        val plano = com.example.vineuro_pvc.data.ModelosVentana.plano(mo, w.toDouble(), h.toDouble(), sectionWidths, sectionHeights)
        val sep = mitadSeparacion()   // entre paños vecinos del mismo marco
        if (showShadows) {
            for (c in plano.celdas) {
                val sh = dpToPx(2.5f)
                sombraProyectada(canvas, ox + c.x0.toFloat() * k, oy + c.y0.toFloat() * k, ox + c.x1.toFloat() * k, oy + c.y1.toFloat() * k, sh)
            }
        }
        for (c in plano.celdas) {
            val l = ox + c.x0.toFloat() * k; val tp = oy + c.y0.toFloat() * k
            val r = ox + c.x1.toFloat() * k; val b = oy + c.y1.toFloat() * k
            canvas.drawRect(l, tp, if (c.der) r else r + 0.6f, if (c.aba) b else b + 0.6f, marcoPintura())
            // Borde solo en los lados exteriores: entre dos hojas el marco es continuo, sin raya por el centro del hueco
            val m = frameBorderPaint.strokeWidth / 2f
            if (c.arr) canvas.drawLine(l - m, tp, r + m, tp, frameBorderPaint)
            if (c.aba) canvas.drawLine(l - m, b, r + m, b, frameBorderPaint)
            if (c.izq) canvas.drawLine(l, tp - m, l, b + m, frameBorderPaint)
            if (c.der) canvas.drawLine(r, tp - m, r, b + m, frameBorderPaint)
        }
        for ((i, c) in plano.celdas.withIndex()) {
            val l = ox + c.x0.toFloat() * k; val tp = oy + c.y0.toFloat() * k
            val r = ox + c.x1.toFloat() * k; val b = oy + c.y1.toFloat() * k
            drawPaneWithMechanism(
                canvas,
                l + (if (c.izq) t else sep), tp + (if (c.arr) t else sep),
                r - (if (c.der) t else sep), b - (if (c.aba) t else sep),
                paneIndex = i, row = 0, col = i,
            )
        }
        // Remates en los huecos entre hojas vecinas
        for (c in plano.celdas) {
            val l = ox + c.x0.toFloat() * k; val tp = oy + c.y0.toFloat() * k
            val r = ox + c.x1.toFloat() * k; val b = oy + c.y1.toFloat() * k
            val pt = tp + (if (c.arr) t else sep); val pb = b - (if (c.aba) t else sep)
            val pl = l + (if (c.izq) t else sep); val pr = r - (if (c.der) t else sep)
            if (!c.der) rematesSeparacion(canvas, true, pr, r, pt + sep, pb - sep)
            if (!c.izq) rematesSeparacion(canvas, true, l, pl, pt + sep, pb - sep)
            if (!c.aba) rematesSeparacion(canvas, false, pb, b, pl + sep, pr - sep)
            if (!c.arr) rematesSeparacion(canvas, false, tp, pt, pl + sep, pr - sep)
        }
    }

    private fun drawAsymmetric(canvas: Canvas, rect: RectF, cols: Int, rows: Int, t: Float) {
        drawGrid(canvas, rect, cols, rows, t)
    }

    private fun getScaledActiveHeights(rows: Int): FloatArray {
        val totalH = altoTotalMm.toFloat().coerceAtLeast(1f)
        val result = FloatArray(rows)

        if (sectionHeights.size == rows) {
            val rawSum = sectionHeights.sum()
            if (rawSum > 0f) {
                val scale = totalH / rawSum
                for (r in 0 until rows) {
                    result[r] = (sectionHeights[r] * scale).coerceAtLeast(1f)
                }
                return result
            }
        }

        for (r in 0 until rows) {
            result[r] = totalH / rows
        }
        return result
    }

    private fun getScaledActiveWidths(cols: Int): FloatArray {
        val totalW = anchoTotalMm.toFloat().coerceAtLeast(1f)
        val result = FloatArray(cols)

        if (sectionWidths.size == cols) {
            val rawSum = sectionWidths.sum()
            if (rawSum > 0f) {
                val scale = totalW / rawSum
                for (c in 0 until cols) {
                    result[c] = (sectionWidths[c] * scale).coerceAtLeast(1f)
                }
                return result
            }
        }

        for (c in 0 until cols) {
            result[c] = totalW / cols
        }
        return result
    }

    private fun drawDimensionLines(canvas: Canvas, rect: RectF, cfg: WindowConfig, isCompactMode: Boolean = false) {
        val lineX = if (isCompactMode) rect.left - dpToPx(8f) else rect.left - dpToPx(12f)
        val lineOuterX = if (isCompactMode) rect.left - dpToPx(18f) else rect.left - dpToPx(42f)
        val dotRadius = if (isCompactMode) dpToPx(1.8f) else dpToPx(2.5f)

        // 1. Línea vertical principal (alto total)
        canvas.drawLine(lineOuterX, rect.top, lineOuterX, rect.bottom, linePaint)
        canvas.drawCircle(lineOuterX, rect.top, dotRadius, dotPaint)
        canvas.drawCircle(lineOuterX, rect.bottom, dotRadius, dotPaint)
        drawInteractiveBadge(
            canvas,
            lineOuterX,
            (rect.top + rect.bottom) / 2f,
            Unidades.mostrar(altoTotalMm),
            DimensionType.TOTAL_HEIGHT,
            0,
            altoTotalMm,
            isCompactMode = isCompactMode
        )

        // Manija azul inferior (solo en modo interactivo del editor; nunca al imprimir)
        if (!isCompactMode && !modoImpresion) {
            val handleRadius = dpToPx(6f)
            canvas.drawCircle(lineOuterX, rect.bottom + dpToPx(8f), handleRadius, handlePaint)
            canvas.drawCircle(lineOuterX, rect.bottom + dpToPx(8f), handleRadius, handleStrokePaint)
        }

        // 2. Línea vertical por secciones (solo si hay múltiples filas)
        val rows = getActiveLayoutRows(cfg)
        if (rows > 1) {
            canvas.drawLine(lineX, rect.top, lineX, rect.bottom, linePaint)
            canvas.drawCircle(lineX, rect.top, dotRadius, dotPaint)
            canvas.drawCircle(lineX, rect.bottom, dotRadius, dotPaint)

            val activeHeights = getScaledActiveHeights(rows)
            val totalHVal = activeHeights.sum().coerceAtLeast(1f)
            var currentY = rect.top

            for (r in 0 until rows) {
                val rowRatio = activeHeights[r] / totalHVal
                val sectionH = rect.height() * rowRatio
                val sY1 = currentY
                val sY2 = currentY + sectionH
                canvas.drawCircle(lineX, sY2, dotRadius, dotPaint)

                val sectionVal = activeHeights[r].toInt()

                drawInteractiveBadge(
                    canvas,
                    lineX,
                    (sY1 + sY2) / 2f,
                    Unidades.mostrar(sectionVal),
                    DimensionType.SECTION_HEIGHT,
                    r,
                    sectionVal,
                    isCompactMode = isCompactMode
                )
                currentY += sectionH
            }
        }

        // 3. Línea horizontal por paneles (solo si hay múltiples columnas)
        val cols = getActiveLayoutCols(cfg)
        if (cols > 1) {
            val activeWidths = getScaledActiveWidths(cols)
            val totalWVal = activeWidths.sum().coerceAtLeast(1f)

            val lineInnerY = if (isCompactMode) rect.bottom + dpToPx(7f) else rect.bottom + dpToPx(15f)
            canvas.drawLine(rect.left, lineInnerY, rect.right, lineInnerY, linePaint)
            canvas.drawCircle(rect.left, lineInnerY, dotRadius, dotPaint)
            canvas.drawCircle(rect.right, lineInnerY, dotRadius, dotPaint)

            var currentX = rect.left
            for (c in 0 until cols) {
                val colRatio = activeWidths[c] / totalWVal
                val sectionW = rect.width() * colRatio
                val sX1 = currentX
                val sX2 = currentX + sectionW
                canvas.drawCircle(sX2, lineInnerY, dotRadius, dotPaint)

                val panelVal = activeWidths[c].toInt()

                drawInteractiveBadge(
                    canvas,
                    (sX1 + sX2) / 2f,
                    lineInnerY,
                    Unidades.mostrar(panelVal),
                    DimensionType.SECTION_WIDTH,
                    c,
                    panelVal,
                    isCompactMode = isCompactMode
                )
                currentX += sectionW
            }
        }

        // 4. Línea horizontal principal (ancho total)
        val lineOuterY = if (isCompactMode) rect.bottom + dpToPx(15f) else rect.bottom + dpToPx(38f)
        canvas.drawLine(rect.left, lineOuterY, rect.right, lineOuterY, linePaint)
        canvas.drawCircle(rect.left, lineOuterY, dotRadius, dotPaint)
        canvas.drawCircle(rect.right, lineOuterY, dotRadius, dotPaint)
        drawInteractiveBadge(
            canvas,
            (rect.left + rect.right) / 2f,
            lineOuterY,
            Unidades.mostrar(anchoTotalMm),
            DimensionType.TOTAL_WIDTH,
            0,
            anchoTotalMm,
            isCompactMode = isCompactMode
        )
    }

    private fun drawInteractiveBadge(
        canvas: Canvas,
        cX: Float,
        cY: Float,
        text: String,
        type: DimensionType,
        index: Int,
        currentValue: Int,
        isCompactMode: Boolean = false
    ) {
        val badgeW = if (isCompactMode) dpToPx(18f) else dpToPx(32f)
        val badgeH = if (isCompactMode) dpToPx(8f) else dpToPx(13f)
        val textSizeDp = if (isCompactMode) 6.8f else 11f

        if (modoImpresion) {
            dibujarCotaImpresion(canvas, cX, cY, text, textSizeDp)
            return
        }

        val badgeRect = RectF(cX - badgeW / 2f, cY - badgeH / 2f, cX + badgeW / 2f, cY + badgeH / 2f)

        val borderP = if (showTechnicalDetails || isCompactMode) {
            Paint(badgeBorderPaint).apply { color = Color.parseColor("#444444") }
        } else badgeBorderPaint

        val textP = if (showTechnicalDetails || isCompactMode) {
            Paint(textPaint).apply {
                color = Color.parseColor("#222222")
                textSize = dpToPx(textSizeDp)
            }
        } else textPaint

        canvas.drawRoundRect(badgeRect, dpToPx(2f), dpToPx(2f), badgePaint)
        canvas.drawRoundRect(badgeRect, dpToPx(2f), dpToPx(2f), borderP)
        canvas.drawText(text, cX, cY + dpToPx(if (isCompactMode) 2.5f else 4f), textP)

        badgeAreas.add(BadgeArea(badgeRect, type, index, currentValue))
    }

    private val cotaFondoPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val cotaTextoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#222222")
        textAlign = Paint.Align.CENTER
    }

    /** Cota para imprimir: solo el número, centrado sobre un fondo blanco del tamaño del texto que corta la línea. */
    private fun dibujarCotaImpresion(canvas: Canvas, cX: Float, cY: Float, text: String, textSizeDp: Float) {
        cotaTextoPaint.typeface = PdfFuentes.tipo(false)
        cotaTextoPaint.textSize = dpToPx(textSizeDp)
        val fm = cotaTextoPaint.fontMetrics
        val anchoTxt = cotaTextoPaint.measureText(text)
        val altoTxt = fm.descent - fm.ascent
        val holgura = dpToPx(1.2f)
        canvas.drawRect(
            cX - anchoTxt / 2f - holgura, cY - altoTxt / 2f,
            cX + anchoTxt / 2f + holgura, cY + altoTxt / 2f,
            cotaFondoPaint,
        )
        // Línea base para que el texto quede centrado verticalmente en la línea de cota
        canvas.drawText(text, cX, cY - (fm.ascent + fm.descent) / 2f, cotaTextoPaint)
    }
}
