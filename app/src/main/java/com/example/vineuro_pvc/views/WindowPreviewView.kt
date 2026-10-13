package com.example.vineuro_pvc.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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

    /** "shadows" de la original: sombras suaves en perfiles y vidrio (marcado por defecto). */
    var showShadows: Boolean = true
        set(value) { field = value; invalidate() }

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
        color = Color.parseColor("#333333")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.8f)
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
    val paneImpOff = mutableMapOf<Int, String>()

    var onDimensionClickListener: ((type: DimensionType, index: Int, currentValue: Int) -> Unit)? = null
    var onPaneClickListener: ((paneIndex: Int, row: Int, col: Int) -> Unit)? = null

    fun setConfig(windowConfig: WindowConfig, isTechnicalMode: Boolean = false, resetMechanisms: Boolean = true) {
        val isNewConfig = (this.config?.id != windowConfig.id)
        this.config = windowConfig
        this.showTechnicalDetails = isTechnicalMode

        val rows = when (windowConfig.layoutType) {
            WindowConfig.LayoutType.TOP_TRANSOM,
            WindowConfig.LayoutType.BOTTOM_TRANSOM,
            WindowConfig.LayoutType.DOOR_L_LEFT,
            WindowConfig.LayoutType.DOOR_L_RIGHT,
            WindowConfig.LayoutType.DOOR_T_CENTER,
            WindowConfig.LayoutType.DOOR_L_LEFT_TRANSOM,
            WindowConfig.LayoutType.DOOR_L_RIGHT_TRANSOM,
            WindowConfig.LayoutType.DOOR_T_CENTER_TRANSOM -> 2

            WindowConfig.LayoutType.TOP_BOTTOM_TRANSOM -> 3

            else -> maxOf(1, windowConfig.rows)
        }

        val cols = when (windowConfig.layoutType) {
            WindowConfig.LayoutType.DOOR_T_CENTER,
            WindowConfig.LayoutType.DOOR_T_CENTER_TRANSOM -> maxOf(3, windowConfig.cols)

            WindowConfig.LayoutType.DOOR_L_LEFT,
            WindowConfig.LayoutType.DOOR_L_RIGHT,
            WindowConfig.LayoutType.DOOR_L_LEFT_TRANSOM,
            WindowConfig.LayoutType.DOOR_L_RIGHT_TRANSOM -> maxOf(2, windowConfig.cols)

            else -> maxOf(1, windowConfig.cols)
        }

        val currentTotalH = if (sectionHeights.isNotEmpty()) sectionHeights.sum() else 1800f
        val currentTotalW = if (sectionWidths.isNotEmpty()) sectionWidths.sum() else 1500f

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
     * "¿Igualar dimensiones?" del original: reparte el total en secciones de igual medida
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
        isCompactMode: Boolean = false
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
            framePaint.color = colorOBlanco(com.example.vineuro_pvc.data.ColoresPerfil.hexDe(item.opciones.color))
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
        drawDimensionLines(canvas, windowRect, cfg)
    }

    /** Píxeles por milímetro de la ventana dibujada (para dibujar las hojas de las aperturas múltiples a escala). */
    private var pxPorMm = 1f

    private fun drawWindowConfig(canvas: Canvas, rect: RectF, cfg: WindowConfig) {
        pxPorMm = rect.width() / anchoTotalMm.toFloat().coerceAtLeast(1f)
        if (showShadows) {
            canvas.drawRect(rect.left + dpToPx(2.5f), rect.top + dpToPx(2.5f), rect.right + dpToPx(2.5f), rect.bottom + dpToPx(2.5f), shadowPaint)
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

        // Hoja: toda apertura distinta de "sin apertura" (y toda puerta) lleva perfil de hoja alrededor del vidrio y manija.
        val mecanismo = paneMechanisms[paneIndex] ?: "5"
        val tieneHoja = isPuerta || mecanismo != "5"
        var grosorManija = -1f
        if (tieneHoja) {
            val ladoMin = Math.min(pR - pL, pB - pT)
            val sashThickness = (ladoMin * (if (isPuerta) 0.11f else 0.075f))
                .coerceIn(dpToPx(2f), dpToPx(if (isPuerta) 9f else 7f))

            if (showShadows) {
                // sombra de la hoja sobre el marco (abajo/derecha)
                canvas.drawRect(pL + dpToPx(1.2f), pT + dpToPx(1.2f), pR + dpToPx(1.2f), pB + dpToPx(1.2f), shadowPaint)
            }
            canvas.drawRect(pL, pT, pR, pB, framePaint)
            canvas.drawRect(pL, pT, pR, pB, frameBorderPaint)

            glassL = pL + sashThickness
            glassT = pT + sashThickness
            glassR = pR - sashThickness
            glassB = pB - sashThickness

            if (mecanismo != "10" && (pR - pL) > dpToPx(28f) && (pB - pT) > dpToPx(28f)) grosorManija = sashThickness
        }

        val imp = (paneCols[paneIndex] ?: 0).let { if (it in 11..14) it - 10 else 0 }
        if (imp == 0) {
            canvas.drawRect(glassL, glassT, glassR, glassB, if (isSandwich) sandwichPaint else glassPaint)
            canvas.drawRect(glassL, glassT, glassR, glassB, frameBorderPaint)
            dibujarBarrasVidrio(canvas, RectF(glassL, glassT, glassR, glassB), paneIndex, 0)
        } else {
            // Imposta interior de la hoja (como la original): 2 o 3 vidrios repartidos a partes iguales
            val horizontal = imp <= 2
            val n = if (imp % 2 == 1) 2 else 3
            val mask = paneSandwichSub[paneIndex] ?: (if (isSandwich) 7 else 0)
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

        if (showShadows && glassR - glassL > dpToPx(6f) && glassB - glassT > dpToPx(6f)) {
            // sombra interior del perfil sobre el vidrio (arriba e izquierda)
            canvas.drawRect(glassL, glassT, glassR, glassT + dpToPx(2.5f), shadowPaint)
            canvas.drawRect(glassL, glassT, glassL + dpToPx(1.8f), glassB, shadowPaint)
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

        if (grosorManija >= 0f) dibujarManija(canvas, mecanismo, glassL, glassT, glassR, glassB, grosorManija)

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
                canvas.drawLine(glassL + padding, glassT + padding, glassR - padding, cY, crosshairPaint)
                canvas.drawLine(glassL + padding, glassB - padding, glassR - padding, cY, crosshairPaint)
            }

            "6" -> {
                canvas.drawLine(glassR - padding, glassT + padding, glassL + padding, cY, crosshairPaint)
                canvas.drawLine(glassR - padding, glassB - padding, glassL + padding, cY, crosshairPaint)
            }

            "7" -> {
                canvas.drawLine(glassL + padding, glassT + padding, glassR - padding, cY, crosshairPaint)
                canvas.drawLine(glassL + padding, glassB - padding, glassR - padding, cY, crosshairPaint)
                canvas.drawLine(glassL + padding, glassT + padding, cX, glassB - padding, crosshairPaint)
                canvas.drawLine(glassR - padding, glassT + padding, cX, glassB - padding, crosshairPaint)
            }

            "8" -> {
                canvas.drawLine(glassR - padding, glassT + padding, glassL + padding, cY, crosshairPaint)
                canvas.drawLine(glassR - padding, glassB - padding, glassL + padding, cY, crosshairPaint)
                canvas.drawLine(glassL + padding, glassT + padding, cX, glassB - padding, crosshairPaint)
                canvas.drawLine(glassR - padding, glassT + padding, cX, glassB - padding, crosshairPaint)
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
     * Apertura de varias hojas (corredizas múltiples/verticales y puerta francesa) con las medidas de la original:
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
        if (showShadows) canvas.drawRect(pL + dpToPx(1.2f), pT + dpToPx(1.2f), pR + dpToPx(1.2f), pB + dpToPx(1.2f), shadowPaint)
        canvas.drawRect(pL, pT, pR, pB, frameBorderPaint)
        val esFrancesa = AperturasMulti.grupo(mec) == AperturasMulti.Grupo.FRANCESA
        val basc = if (esFrancesa) AperturasMulti.francesaBasculante(AperturasMulti.clave(mec)) else null
        val vertical = AperturasMulti.grupo(mec) == AperturasMulti.Grupo.CORREDIZA_VERT
        for ((i, h) in hojas.withIndex()) {
            val gl = pL + (h.x * pxM).toFloat(); val gt = pT + (h.y * pxM).toFloat()
            val gr = gl + (h.ancho * pxM).toFloat(); val gb = gt + (h.alto * pxM).toFloat()
            val pf = (h.perfil * pxM).toFloat()
            val ol = Math.max(pL, gl - pf); val ot = Math.max(pT, gt - pf); val orr = Math.min(pR, gr + pf); val ob = Math.min(pB, gb + pf)
            canvas.drawRect(ol, ot, orr, ob, framePaint)
            canvas.drawRect(ol, ot, orr, ob, frameBorderPaint)
            if (gr <= gl || gb <= gt) continue
            // Cada hoja se configura por separado (sándwich por bit; sin dato = el del paño)
            val hojaSandwich = paneSandwichSub[paneIndex]?.let { ((it shr i) and 1) == 1 } ?: isSandwich
            canvas.drawRect(gl, gt, gr, gb, if (hojaSandwich) sandwichPaint else glassPaint)
            canvas.drawRect(gl, gt, gr, gb, frameBorderPaint)
            dibujarBarrasVidrio(canvas, RectF(gl, gt, gr, gb), paneIndex, i)
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
                    // bisagra en el lado exterior: el vértice del símbolo apunta a ella
                    if (izq) {
                        canvas.drawLine(gr - pad, gt + pad, gl + pad, cy, crosshairPaint); canvas.drawLine(gr - pad, gb - pad, gl + pad, cy, crosshairPaint)
                    } else {
                        canvas.drawLine(gl + pad, gt + pad, gr - pad, cy, crosshairPaint); canvas.drawLine(gl + pad, gb - pad, gr - pad, cy, crosshairPaint)
                    }
                    if ((izq && basc == 'L') || (!izq && basc == 'R')) {
                        canvas.drawLine(gl + pad, gt + pad, cx, gb - pad, crosshairPaint); canvas.drawLine(gr - pad, gt + pad, cx, gb - pad, crosshairPaint)
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
                dibujarManija(canvas, m, gl, gt, gr, gb, pf.coerceIn(dpToPx(2f), dpToPx(8f)))
            }
        }
        if (showTechnicalDetails) paneAreas.add(PaneArea(RectF(pL, pT, pR, pB), paneIndex, row, col))
    }

    /**
     * Manija de la hoja, en el lado contrario a la bisagra (el vértice del símbolo de apertura apunta a la bisagra):
     * "4"/"7" bisagra a la derecha => manija a la izquierda; "6"/"8" al revés; "2" bisagra abajo => manija arriba; "9" al revés;
     * corredizas: manija del lado hacia el que corren ("1" derecha, "3" izquierda).
     */
    private fun dibujarManija(canvas: Canvas, mec: String, gL: Float, gT: Float, gR: Float, gB: Float, grosorHoja: Float) {
        val cx = (gL + gR) / 2f
        val cy = (gT + gB) / 2f
        val placaL = dpToPx(11f)
        val placaG = Math.max(grosorHoja * 0.7f, dpToPx(2f))
        val vertical = when (mec) { "2", "9" -> false; else -> true }
        val mx: Float
        val my: Float
        when (mec) {
            "4", "7", "3" -> { mx = gL - grosorHoja / 2f; my = cy }
            "2" -> { mx = cx; my = gT - grosorHoja / 2f }
            "9" -> { mx = cx; my = gB + grosorHoja / 2f }
            else -> { mx = gR + grosorHoja / 2f; my = cy } // "6", "8", "1" y puertas
        }
        val plate = if (vertical) RectF(mx - placaG / 2f, my - placaL / 2f, mx + placaG / 2f, my + placaL / 2f)
        else RectF(mx - placaL / 2f, my - placaG / 2f, mx + placaL / 2f, my + placaG / 2f)
        canvas.drawRoundRect(plate, dpToPx(1.2f), dpToPx(1.2f), handlePlatePaint)
        canvas.drawRoundRect(plate, dpToPx(1.2f), dpToPx(1.2f), frameBorderPaint)
        // palanca hacia el interior de la hoja
        val palanca = if (vertical) {
            val dir = if (mx < cx) 1f else -1f
            RectF(Math.min(mx, mx + dir * dpToPx(6f)), my - dpToPx(1.3f), Math.max(mx, mx + dir * dpToPx(6f)), my + dpToPx(1.3f))
        } else {
            val dir = if (my < cy) 1f else -1f
            RectF(mx - dpToPx(1.3f), Math.min(my, my + dir * dpToPx(6f)), mx + dpToPx(1.3f), Math.max(my, my + dir * dpToPx(6f)))
        }
        canvas.drawRoundRect(palanca, dpToPx(1f), dpToPx(1f), doorHandlePaint)
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

    private fun getActiveLayoutRows(cfg: WindowConfig): Int {
        return when (cfg.layoutType) {
            WindowConfig.LayoutType.TOP_TRANSOM,
            WindowConfig.LayoutType.BOTTOM_TRANSOM,
            WindowConfig.LayoutType.DOOR_L_LEFT,
            WindowConfig.LayoutType.DOOR_L_RIGHT,
            WindowConfig.LayoutType.DOOR_T_CENTER,
            WindowConfig.LayoutType.DOOR_L_LEFT_TRANSOM,
            WindowConfig.LayoutType.DOOR_L_RIGHT_TRANSOM,
            WindowConfig.LayoutType.DOOR_T_CENTER_TRANSOM -> 2

            WindowConfig.LayoutType.TOP_BOTTOM_TRANSOM -> 3

            else -> maxOf(1, cfg.rows)
        }
    }

    private fun getActiveLayoutCols(cfg: WindowConfig): Int {
        return when (cfg.layoutType) {
            WindowConfig.LayoutType.DOOR_T_CENTER,
            WindowConfig.LayoutType.DOOR_T_CENTER_TRANSOM -> maxOf(3, cfg.cols)

            WindowConfig.LayoutType.DOOR_L_LEFT,
            WindowConfig.LayoutType.DOOR_L_RIGHT,
            WindowConfig.LayoutType.DOOR_L_LEFT_TRANSOM,
            WindowConfig.LayoutType.DOOR_L_RIGHT_TRANSOM -> maxOf(2, cfg.cols)

            else -> maxOf(1, cfg.cols)
        }
    }

    private fun drawGrid(canvas: Canvas, rect: RectF, cols: Int, rows: Int, t: Float) {
        canvas.drawRect(rect, framePaint)
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

        var pIdx = 0
        for (r in 0 until rows) {
            val rowRatio = activeHeights[r] / totalHVal
            val cellH = totalInnerH * rowRatio
            val pT = currentY + padding
            val pB = (currentY + cellH - padding).coerceAtMost(innerB - padding)

            var currentX = innerL
            for (c in 0 until cols) {
                val colRatio = activeWidths[c] / totalWVal
                val cellW = totalInnerW * colRatio
                val pL = currentX + padding
                val pR = (currentX + cellW - padding).coerceAtMost(innerR - padding)

                if (pR > pL && pB > pT) {
                    drawPaneWithMechanism(canvas, pL, pT, pR, pB, paneIndex = pIdx, row = r, col = c)
                }
                pIdx++
                currentX += cellW
            }
            currentY += cellH
        }
    }

    private fun drawTopTransom(canvas: Canvas, rect: RectF, cols: Int, t: Float) {
        canvas.drawRect(rect, framePaint)
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

        drawPaneWithMechanism(canvas, innerL + padding, innerT + padding, innerR - padding, innerT + transomH - padding, paneIndex = 0)

        val cellW = (innerR - innerL) / cols
        val bottomT = innerT + transomH + padding

        for (c in 0 until cols) {
            val pL = innerL + c * cellW + padding
            val pR = (innerL + (c + 1) * cellW - padding).coerceAtMost(innerR - padding)
            drawPaneWithMechanism(canvas, pL, bottomT, pR, innerB - padding, paneIndex = c + 1)
        }
    }

    private fun drawBottomTransom(canvas: Canvas, rect: RectF, cols: Int, t: Float) {
        canvas.drawRect(rect, framePaint)
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

        val cellW = (innerR - innerL) / cols
        for (c in 0 until cols) {
            val pL = innerL + c * cellW + padding
            val pR = (innerL + (c + 1) * cellW - padding).coerceAtMost(innerR - padding)
            drawPaneWithMechanism(canvas, pL, innerT + padding, pR, topB - padding, paneIndex = c)
        }

        drawPaneWithMechanism(canvas, innerL + padding, topB + padding, innerR - padding, innerB - padding, paneIndex = cols)
    }

    private fun drawTopBottomTransom(canvas: Canvas, rect: RectF, cols: Int, t: Float) {
        canvas.drawRect(rect, framePaint)
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

        drawPaneWithMechanism(canvas, innerL + padding, innerT + padding, innerR - padding, innerT + topH - padding, paneIndex = 0)

        val cellW = (innerR - innerL) / cols
        val midT = innerT + topH + padding
        val midB = midT + midH - padding * 2f

        for (c in 0 until cols) {
            val pL = innerL + c * cellW + padding
            val pR = (innerL + (c + 1) * cellW - padding).coerceAtMost(innerR - padding)
            drawPaneWithMechanism(canvas, pL, midT, pR, midB, paneIndex = c + 1)
        }

        drawPaneWithMechanism(canvas, innerL + padding, midB + padding * 2f, innerR - padding, innerB - padding, paneIndex = cols + 1)
    }

    private fun drawDoorL(canvas: Canvas, rect: RectF, isLeftDoor: Boolean, hasTransom: Boolean, t: Float) {
        val splitX = if (isLeftDoor) rect.left + (rect.width() * 0.45f) else rect.left + (rect.width() * 0.55f)

        val activeHeights = getScaledActiveHeights(2)
        val totalHSum = activeHeights.sum().coerceAtLeast(1f)
        val mainRatio = activeHeights[0] / totalHSum
        val windowBottomY = rect.top + (rect.height() * mainRatio)

        if (isLeftDoor) {
            canvas.drawRect(rect.left, rect.top, splitX, rect.bottom, framePaint)
            canvas.drawRect(rect.left, rect.top, splitX, rect.bottom, frameBorderPaint)
            canvas.drawRect(splitX, rect.top, rect.right, windowBottomY, framePaint)
            canvas.drawRect(splitX, rect.top, rect.right, windowBottomY, frameBorderPaint)
        } else {
            canvas.drawRect(rect.left, rect.top, splitX, windowBottomY, framePaint)
            canvas.drawRect(rect.left, rect.top, splitX, windowBottomY, frameBorderPaint)
            canvas.drawRect(splitX, rect.top, rect.right, rect.bottom, framePaint)
            canvas.drawRect(splitX, rect.top, rect.right, rect.bottom, frameBorderPaint)
        }

        val iL = rect.left + t
        val iT = rect.top + t
        val iR = rect.right - t
        val iB = rect.bottom - t
        val padding = dpToPx(1f)

        val transomH = if (hasTransom) (iB - iT) * 0.22f else 0f

        if (isLeftDoor) {
            if (hasTransom) {
                drawPaneWithMechanism(canvas, iL + padding, iT + padding, splitX - padding, iT + transomH - padding, paneIndex = 0)
                drawPaneWithMechanism(canvas, iL + padding, iT + transomH + padding, splitX - padding, iB - padding, paneIndex = 1)
            } else {
                drawPaneWithMechanism(canvas, iL + padding, iT + padding, splitX - padding, iB - padding, paneIndex = 0)
            }
            if (hasTransom) {
                drawPaneWithMechanism(canvas, splitX + padding, iT + padding, iR - padding, iT + transomH - padding, paneIndex = 2)
                drawPaneWithMechanism(canvas, splitX + padding, iT + transomH + padding, iR - padding, windowBottomY - t - padding, paneIndex = 3)
            } else {
                drawPaneWithMechanism(canvas, splitX + padding, iT + padding, iR - padding, windowBottomY - t - padding, paneIndex = 1)
            }
        } else {
            if (hasTransom) {
                drawPaneWithMechanism(canvas, iL + padding, iT + padding, splitX - padding, iT + transomH - padding, paneIndex = 0)
                drawPaneWithMechanism(canvas, iL + padding, iT + transomH + padding, splitX - padding, windowBottomY - t - padding, paneIndex = 1)
            } else {
                drawPaneWithMechanism(canvas, iL + padding, iT + padding, splitX - padding, windowBottomY - t - padding, paneIndex = 0)
            }
            if (hasTransom) {
                drawPaneWithMechanism(canvas, splitX + padding, iT + padding, iR - padding, iT + transomH - padding, paneIndex = 2)
                drawPaneWithMechanism(canvas, splitX + padding, iT + transomH + padding, iR - padding, iB - padding, paneIndex = 3)
            } else {
                drawPaneWithMechanism(canvas, splitX + padding, iT + padding, iR - padding, iB - padding, paneIndex = 1)
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
            canvas.drawRect(rect.left, rect.top, rect.right, windowBottomY, framePaint)
            canvas.drawRect(rect.left, rect.top, rect.right, windowBottomY, frameBorderPaint)
            canvas.drawRect(splitLeftX, windowBottomY, splitRightX, rect.bottom, framePaint)
            canvas.drawRect(splitLeftX, windowBottomY, splitRightX, rect.bottom, frameBorderPaint)
        } else {
            canvas.drawRect(rect.left, rect.top, splitLeftX, windowBottomY, framePaint)
            canvas.drawRect(rect.left, rect.top, splitLeftX, windowBottomY, frameBorderPaint)
            canvas.drawRect(splitLeftX, rect.top, splitRightX, rect.bottom, framePaint)
            canvas.drawRect(splitLeftX, rect.top, splitRightX, rect.bottom, frameBorderPaint)
            canvas.drawRect(splitRightX, rect.top, rect.right, windowBottomY, framePaint)
            canvas.drawRect(splitRightX, rect.top, rect.right, windowBottomY, frameBorderPaint)
        }

        val iL = rect.left + t
        val iT = rect.top + t
        val iR = rect.right - t
        val iB = rect.bottom - t
        val padding = dpToPx(1f)

        if (hasTransom) {
            drawPaneWithMechanism(canvas, iL + padding, iT + padding, iR - padding, windowBottomY - padding, paneIndex = 0)
            drawPaneWithMechanism(canvas, iL + padding, windowBottomY + padding, splitLeftX - padding, iB - padding, paneIndex = 1)
            drawPaneWithMechanism(canvas, splitLeftX + padding, windowBottomY + padding, splitRightX - padding, iB - padding, paneIndex = 2)
            drawPaneWithMechanism(canvas, splitRightX + padding, windowBottomY + padding, iR - padding, iB - padding, paneIndex = 3)
        } else {
            drawPaneWithMechanism(canvas, iL + padding, iT + padding, splitLeftX - padding, windowBottomY - padding, paneIndex = 0)
            drawPaneWithMechanism(canvas, splitRightX + padding, iT + padding, iR - padding, windowBottomY - padding, paneIndex = 2)
            drawPaneWithMechanism(canvas, splitLeftX + padding, iT + padding, splitRightX - padding, iB - padding, paneIndex = 1)
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

        // Manija azul inferior (solo en modo interactivo del editor)
        if (!isCompactMode) {
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
}
