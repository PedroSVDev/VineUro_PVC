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
import com.example.vineuro_pvc.data.OrderItem
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
        /** Parte dentro de un paño con apertura combinada (-1 = el paño completo). */
        val parte: Int = -1,
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
        color = Color.parseColor("#F4F4F4")
        style = Paint.Style.FILL
    }

    private val meshPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A0555555")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(0.8f)
    }

    private val mosqMarcoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#707070")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2f)
    }

    private val sandwichLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C8C8C8")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(0.6f)
    }

    private val barrasBordePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9E9E9E")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(0.5f)
    }

    private val sombraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val relieveClaroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = dpToPx(1.2f)
    }

    private val relieveOscuroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9A9A9A"); style = Paint.Style.STROKE; strokeWidth = dpToPx(1.2f)
    }

    private val relieveMedioPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22000000"); style = Paint.Style.STROKE; strokeWidth = dpToPx(0.6f)
    }

    private val manijaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F2F2F2")
        style = Paint.Style.FILL
    }

    private val cerraduraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#555555")
        style = Paint.Style.FILL
    }

    private val manijaBordePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#555555")
        style = Paint.Style.STROKE
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
    /** Divisiones horizontales (impostas) dentro del paño: 1..4. */
    val paneRows = mutableMapOf<Int, Int>()
    /** Partes del paño que son panel sándwich (bit i = parte i, recorrida fila por fila). */
    val paneSandwichMask = mutableMapOf<Int, Int>()
    /** Barrotillos (cuadrícula decorativa sobre el vidrio): filas * 100 + columnas. 0 = sin barrotillos. */
    val paneBarras = mutableMapOf<Int, Int>()
    /** Apertura de cada parte del paño (aperturas combinadas), p. ej. "1,3" = → ←. Vacío = una sola apertura. */
    val panePartMechanisms = mutableMapOf<Int, String>()
    /** Sombras / relieve en perfiles y vidrios (casilla "shadows"). */
    var showShadows: Boolean = false
        set(value) { field = value; invalidate() }

    var onDimensionClickListener: ((type: DimensionType, index: Int, currentValue: Int) -> Unit)? = null
    var onPaneClickListener: ((paneIndex: Int, row: Int, col: Int) -> Unit)? = null

    /** Toque sobre UNA parte de un paño con apertura combinada (p. ej. el fijo "+" del medio). */
    var onPaneParteClickListener: ((paneIndex: Int, parte: Int) -> Unit)? = null

    /** true = dibujo para PDF: cotas como texto limpio (sin recuadro) y sin la manija azul del editor. */
    var modoImpresion: Boolean = false

    /** Color del perfil (marco y hojas) para el PDF; null = blanco. */
    var colorPerfil: Int? = null

    /** false = no dibuja flechas ni "+" (vista previa en blanco, p. ej. la de barrotillos). */
    var mostrarSimboloApertura: Boolean = true

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
            paneRows.clear()
            paneSandwichMask.clear()
            paneBarras.clear()
            panePartMechanisms.clear()
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
        paneRows.clear()
        paneSandwichMask.clear()
        paneBarras.clear()
        panePartMechanisms.clear()
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

    fun setPaneMechanism(paneIndex: Int, mechanismId: String) {
        paneMechanisms[paneIndex] = mechanismId
        panePartMechanisms.remove(paneIndex)
        invalidate()
    }

    /** Cambia la apertura de UNA sola parte de un paño combinado (las demás partes no se tocan). */
    fun setParteMechanism(paneIndex: Int, parte: Int, mechanismId: String) {
        val partes = panePartMechanisms[paneIndex]?.split(",")?.map { it.trim() }?.toMutableList() ?: return
        if (parte !in partes.indices) return
        partes[parte] = mechanismId
        panePartMechanisms[paneIndex] = partes.joinToString(",")
        invalidate()
    }

    /** Apertura combinada: una apertura por cada parte del paño (lista separada por comas). */
    fun setPanePartMechanisms(paneIndex: Int, partes: String) {
        paneMechanisms[paneIndex] = "5"
        panePartMechanisms[paneIndex] = partes
        invalidate()
    }

    fun setPaneOptions(
        paneIndex: Int,
        isSandwich: Boolean,
        isPuerta: Boolean,
        isMosqNet: Boolean,
        subCols: Int = 1,
    ) {
        paneSandwich[paneIndex] = isSandwich
        panePuerta[paneIndex] = isPuerta
        paneMosqNet[paneIndex] = isMosqNet
        paneCols[paneIndex] = subCols
        invalidate()
    }

    /**
     * Opciones completas de un paño (diálogo impostas / apertura):
     * divisiones [filas] x [columnas], qué partes son sándwich, barrotillos, puerta y mosquitero.
     */
    fun setPaneDivision(
        paneIndex: Int,
        filas: Int,
        columnas: Int,
        sandwichMask: Int,
        barrasFilas: Int,
        barrasColumnas: Int,
        isPuerta: Boolean,
        isMosqNet: Boolean,
    ) {
        paneRows[paneIndex] = filas.coerceIn(1, 4)
        paneCols[paneIndex] = columnas.coerceIn(1, 4)
        paneSandwichMask[paneIndex] = sandwichMask
        paneSandwich[paneIndex] = sandwichMask != 0
        paneBarras[paneIndex] = if (barrasFilas > 1 || barrasColumnas > 1) barrasFilas * 100 + barrasColumnas else 0
        panePuerta[paneIndex] = isPuerta
        paneMosqNet[paneIndex] = isMosqNet
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

            // Primero las partes de una apertura combinada (se agregan antes que su paño)
            for (pane in paneAreas) {
                if (pane.rect.contains(x, y)) {
                    performClick()
                    val alParte = onPaneParteClickListener
                    if (pane.parte >= 0 && alParte != null) {
                        alParte.invoke(pane.paneIndex, pane.parte)
                    } else {
                        onPaneClickListener?.invoke(pane.paneIndex, pane.row, pane.col)
                    }
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
        val savedRows = HashMap(this.paneRows)
        val savedMask = HashMap(this.paneSandwichMask)
        val savedBarras = HashMap(this.paneBarras)
        val savedPartes = HashMap(this.panePartMechanisms)
        val savedColorPerfil = framePaint.color

        try {
            colorPerfil?.let { framePaint.color = it }
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
            this.paneRows.clear()
            this.paneRows.putAll(item.paneRows)
            this.paneSandwichMask.clear()
            this.paneSandwichMask.putAll(item.paneSandwichMask)
            this.paneBarras.clear()
            this.paneBarras.putAll(item.paneBarras)
            this.panePartMechanisms.clear()
            this.panePartMechanisms.putAll(item.panePartMechanisms)

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

                    drawWindowConfig(canvas, windowRect, cfg)
                    drawDimensionLines(canvas, windowRect, cfg, isCompactMode = isCompact)
                }
                }
            }
            canvas.restoreToCount(saveCount)
        } finally {
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
            this.paneRows.clear()
            this.paneRows.putAll(savedRows)
            this.paneSandwichMask.clear()
            this.paneSandwichMask.putAll(savedMask)
            this.paneBarras.clear()
            this.paneBarras.putAll(savedBarras)
            this.panePartMechanisms.clear()
            this.panePartMechanisms.putAll(savedPartes)
            framePaint.color = savedColorPerfil
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

        drawWindowConfig(canvas, windowRect, cfg)
        drawDimensionLines(canvas, windowRect, cfg)
    }

    private fun drawWindowConfig(canvas: Canvas, rect: RectF, cfg: WindowConfig) {
        val frameThickness = if (showTechnicalDetails) dpToPx(9f) else dpToPx(3f)

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

        val filas = (paneRows[paneIndex] ?: 1).coerceIn(1, 4)
        val columnas = (paneCols[paneIndex] ?: 1).coerceIn(1, 4)
        val totalPartes = filas * columnas
        // Compatibilidad: antes "sándwich" era un sí/no para todo el paño
        val mascara = paneSandwichMask[paneIndex]
            ?: if (paneSandwich[paneIndex] == true) (1 shl totalPartes) - 1 else 0
        val isPuerta = panePuerta[paneIndex] == true
        val isMosqNet = paneMosqNet[paneIndex] == true
        val barras = paneBarras[paneIndex] ?: 0
        val partes = panePartMechanisms[paneIndex]?.split(",")?.map { it.trim() }
            ?.takeIf { totalPartes > 1 && it.size == totalPartes }

        if (partes != null) {
            // Apertura combinada: cada parte es una hoja (o un fijo) independiente
            if (showTechnicalDetails) canvas.drawRect(pL, pT, pR, pB, framePaint)
            dividir(pL, pT, pR, pB, filas, columnas, grosorImposta()).forEachIndexed { i, r ->
                dibujarHoja(
                    canvas, r.left, r.top, r.right, r.bottom, partes[i], 1, 1,
                    if (((mascara shr i) and 1) == 1) 1 else 0, isPuerta, isMosqNet, barras,
                )
                if (showTechnicalDetails) paneAreas.add(PaneArea(RectF(r), paneIndex, row, col, parte = i))
            }
        } else {
            dibujarHoja(canvas, pL, pT, pR, pB, paneMechanisms[paneIndex] ?: "5", filas, columnas, mascara, isPuerta, isMosqNet, barras)
        }

        if (showTechnicalDetails) paneAreas.add(PaneArea(RectF(pL, pT, pR, pB), paneIndex, row, col))
    }

    private fun grosorImposta() = dpToPx(if (showTechnicalDetails) 5f else 1.5f)

    /** Divide un rectángulo en filas x columnas dejando el espacio de las impostas. */
    private fun dividir(l: Float, t: Float, r: Float, b: Float, filas: Int, columnas: Int, gap: Float): List<RectF> {
        val w = (r - l - gap * (columnas - 1)) / columnas
        val h = (b - t - gap * (filas - 1)) / filas
        if (w <= 0 || h <= 0) return emptyList()
        val lista = mutableListOf<RectF>()
        for (f in 0 until filas) for (c in 0 until columnas) {
            val x = l + c * (w + gap); val y = t + f * (h + gap)
            lista.add(RectF(x, y, x + w, y + h))
        }
        return lista
    }

    /**
     * Dibuja una hoja (o un fijo): perfil de la hoja si abre o es puerta, vidrios / paneles sándwich
     * divididos por impostas, barrotillos, mosquitero, manija y el símbolo de apertura.
     */
    private fun dibujarHoja(
        canvas: Canvas, pL: Float, pT: Float, pR: Float, pB: Float,
        mechanismId: String, filas: Int, columnas: Int, mascara: Int,
        isPuerta: Boolean, isMosqNet: Boolean, barras: Int,
    ) {
        val abre = mechanismId != "5"
        val tecnico = showTechnicalDetails

        // 1) Perfil de la hoja (más grueso en puertas, con zócalo inferior).
        //    También se dibuja en las miniaturas (proporcional a su tamaño).
        var gL = pL; var gT = pT; var gR = pR; var gB = pB
        val menor = minOf(pR - pL, pB - pT)
        val grosor = if (tecnico) {
            dpToPx(if (isPuerta) 15f else 12f).coerceAtMost(menor / 4.5f)
        } else {
            (menor * (if (isPuerta) 0.11f else 0.09f)).coerceIn(dpToPx(1.5f), dpToPx(6f))
        }
        if (abre || isPuerta) {
            canvas.drawRect(pL, pT, pR, pB, framePaint)
            if (showShadows) dibujarRelieve(canvas, pL, pT, pR, pB, grosor)
            canvas.drawRect(pL, pT, pR, pB, frameBorderPaint)
            gL += grosor; gT += grosor; gR -= grosor
            gB -= if (isPuerta) grosor * 2f else grosor
            canvas.drawRect(gL, gT, gR, gB, frameBorderPaint)
        }
        if (gR <= gL || gB <= gT) return

        // 2) Vidrios / sándwich divididos por impostas
        val partes = dividir(gL, gT, gR, gB, filas, columnas, grosorImposta())
        if (partes.size > 1) {
            canvas.drawRect(gL, gT, gR, gB, framePaint)
            if (showShadows) dibujarRelieve(canvas, gL, gT, gR, gB, grosorImposta())
        }
        val barrasFilas = (barras / 100).coerceAtLeast(1)
        val barrasColumnas = (barras % 100).coerceAtLeast(1)
        partes.forEachIndexed { i, r ->
            if (((mascara shr i) and 1) == 1) {
                dibujarSandwich(canvas, r.left, r.top, r.right, r.bottom)
            } else {
                canvas.drawRect(r, glassPaint)
                if (barras > 0) dibujarBarrotillos(canvas, r.left, r.top, r.right, r.bottom, barrasFilas, barrasColumnas)
                if (showShadows) dibujarSombraVidrio(canvas, r.left, r.top, r.right, r.bottom)
            }
            canvas.drawRect(r, frameBorderPaint)
        }
        // El mosquitero va sobre toda la hoja, con su propio marquito
        if (isMosqNet) dibujarMosquitero(canvas, gL, gT, gR, gB)

        // 3) Manija (sobre el perfil del lado que abre)
        if ((abre || isPuerta) && mechanismId !in setOf("2", "9", "10", "11")) {
            val manijaDerecha = mechanismId in setOf("4", "7", "3")
            val hx = if (manijaDerecha) pR - grosor / 2f else pL + grosor / 2f
            dibujarManija(canvas, hx, (pT + pB) / 2f, grosor, manijaDerecha, isPuerta)
        }

        // 4) Símbolo de apertura sobre toda la hoja
        dibujarApertura(canvas, gL, gT, gR, gB, mechanismId)
    }

    /**
     * Manija tipo PVC: placa (roseta) sobre el perfil y palanca.
     * - Ventana: palanca vertical hacia abajo.
     * - Puerta: placa larga, palanca horizontal hacia el centro y cilindro de llave abajo.
     * Todo se escala con el grosor del perfil, así se ve igual en el editor y en las miniaturas.
     */
    private fun dibujarManija(canvas: Canvas, hx: Float, hy: Float, grosor: Float, manijaDerecha: Boolean, isPuerta: Boolean) {
        val borde = manijaBordePaint.apply { strokeWidth = (grosor * 0.08f).coerceIn(dpToPx(0.5f), dpToPx(1.2f)) }
        val placaW = grosor * 0.62f
        val placaH = grosor * (if (isPuerta) 3.6f else 1.7f)
        val radio = placaW / 2f
        val placa = RectF(hx - placaW / 2f, hy - placaH / 2f, hx + placaW / 2f, hy + placaH / 2f)
        canvas.drawRoundRect(placa, radio, radio, manijaPaint)
        canvas.drawRoundRect(placa, radio, radio, borde)

        val brazo = grosor * 0.42f
        if (isPuerta) {
            // Palanca horizontal hacia el interior de la hoja
            val py = hy - placaH * 0.22f
            val largo = grosor * 2.8f
            val x2 = if (manijaDerecha) hx - largo else hx + largo
            val palanca = RectF(minOf(hx, x2), py - brazo / 2f, maxOf(hx, x2), py + brazo / 2f)
            canvas.drawRoundRect(palanca, brazo / 2f, brazo / 2f, manijaPaint)
            canvas.drawRoundRect(palanca, brazo / 2f, brazo / 2f, borde)
            canvas.drawCircle(hx, py, placaW * 0.38f, manijaPaint)
            canvas.drawCircle(hx, py, placaW * 0.38f, borde)
            // Cilindro de la cerradura
            val cy = hy + placaH * 0.25f
            canvas.drawCircle(hx, cy, placaW * 0.22f, cerraduraPaint)
            canvas.drawRect(hx - placaW * 0.07f, cy, hx + placaW * 0.07f, cy + placaW * 0.4f, cerraduraPaint)
        } else {
            // Palanca vertical hacia abajo
            val py = hy - placaH * 0.18f
            val largo = grosor * 2.6f
            val palanca = RectF(hx - brazo / 2f, py, hx + brazo / 2f, py + largo)
            canvas.drawRoundRect(palanca, brazo / 2f, brazo / 2f, manijaPaint)
            canvas.drawRoundRect(palanca, brazo / 2f, brazo / 2f, borde)
            canvas.drawCircle(hx, py, placaW * 0.4f, manijaPaint)
            canvas.drawCircle(hx, py, placaW * 0.4f, borde)
        }
    }

    /** Panel sándwich: superficie opaca blanca con líneas horizontales (paneles). */
    private fun dibujarSandwich(canvas: Canvas, l: Float, t: Float, r: Float, b: Float) {
        canvas.drawRect(l, t, r, b, sandwichPaint)
        val paso = dpToPx(5f).coerceAtLeast((b - t) / 14f)
        var y = t + paso
        while (y < b - dpToPx(1f)) {
            canvas.drawLine(l + dpToPx(1f), y, r - dpToPx(1f), y, sandwichLinePaint)
            y += paso
        }
    }

    /** Barrotillos: cuadrícula de perfiles delgados blancos sobre el vidrio. */
    private fun dibujarBarrotillos(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, filas: Int, columnas: Int) {
        val g = dpToPx(if (showTechnicalDetails) 2.2f else 1f)
        for (c in 1 until columnas) {
            val x = l + (r - l) * c / columnas
            canvas.drawRect(x - g / 2, t, x + g / 2, b, framePaint)
            canvas.drawLine(x - g / 2, t, x - g / 2, b, barrasBordePaint)
            canvas.drawLine(x + g / 2, t, x + g / 2, b, barrasBordePaint)
        }
        for (f in 1 until filas) {
            val y = t + (b - t) * f / filas
            canvas.drawRect(l, y - g / 2, r, y + g / 2, framePaint)
            canvas.drawLine(l, y - g / 2, r, y - g / 2, barrasBordePaint)
            canvas.drawLine(l, y + g / 2, r, y + g / 2, barrasBordePaint)
        }
    }

    /** Mosquitero: marquito gris propio y malla cuadriculada visible. */
    private fun dibujarMosquitero(canvas: Canvas, l: Float, t: Float, r: Float, b: Float) {
        val m = dpToPx(if (showTechnicalDetails) 3f else 1f)
        val ml = l + m; val mt = t + m; val mr = r - m; val mb = b - m
        if (mr <= ml || mb <= mt) return
        canvas.drawRect(ml, mt, mr, mb, mosqMarcoPaint)
        val paso = maxOf(dpToPx(if (showTechnicalDetails) 7f else 3f), minOf(mr - ml, mb - mt) / 10f)
        var x = ml + paso
        while (x < mr - 1) { canvas.drawLine(x, mt, x, mb, meshPaint); x += paso }
        var y = mt + paso
        while (y < mb - 1) { canvas.drawLine(ml, y, mr, y, meshPaint); y += paso }
    }

    /** Sombra interior del vidrio (arriba e izquierda) para dar profundidad. */
    private fun dibujarSombraVidrio(canvas: Canvas, l: Float, t: Float, r: Float, b: Float) {
        // Sombra que el perfil proyecta sobre el vidrio (arriba e izquierda), ancha y suave
        val menor = minOf(r - l, b - t)
        val s = if (showTechnicalDetails) dpToPx(16f).coerceAtMost(menor / 3f) else menor * 0.14f
        sombraPaint.shader = android.graphics.LinearGradient(0f, t, 0f, t + s, 0x70000000, 0x00000000, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(l, t, r, t + s, sombraPaint)
        sombraPaint.shader = android.graphics.LinearGradient(l, 0f, l + s, 0f, 0x55000000, 0x00000000, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(l, t, l + s, b, sombraPaint)
        // Reflejo leve en la esquina opuesta del vidrio
        val rf = s * 0.6f
        sombraPaint.shader = android.graphics.LinearGradient(0f, b, 0f, b - rf, 0x40FFFFFF, 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(l, b - rf, r, b, sombraPaint)
        sombraPaint.shader = null
    }

    /** Relieve del perfil: franja clara arriba/izquierda y franja oscura abajo/derecha (bisel). */
    private fun dibujarRelieve(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, grosor: Float) {
        val w = (grosor * 0.45f).coerceAtLeast(dpToPx(1f))
        val clamp = android.graphics.Shader.TileMode.CLAMP
        // Luz (arriba e izquierda)
        sombraPaint.shader = android.graphics.LinearGradient(0f, t, 0f, t + w, 0xCCFFFFFF.toInt(), 0x00FFFFFF, clamp)
        canvas.drawRect(l, t, r, t + w, sombraPaint)
        sombraPaint.shader = android.graphics.LinearGradient(l, 0f, l + w, 0f, 0xAAFFFFFF.toInt(), 0x00FFFFFF, clamp)
        canvas.drawRect(l, t, l + w, b, sombraPaint)
        // Sombra (abajo y derecha)
        sombraPaint.shader = android.graphics.LinearGradient(0f, b, 0f, b - w, 0x66000000, 0x00000000, clamp)
        canvas.drawRect(l, b - w, r, b, sombraPaint)
        sombraPaint.shader = android.graphics.LinearGradient(r, 0f, r - w, 0f, 0x55000000, 0x00000000, clamp)
        canvas.drawRect(r - w, t, r, b, sombraPaint)
        sombraPaint.shader = null
        // Aristas
        canvas.drawLine(l + 1, t + 1, r - 1, t + 1, relieveClaroPaint)
        canvas.drawLine(l + 1, t + 1, l + 1, b - 1, relieveClaroPaint)
        canvas.drawLine(l + 1, b - 1, r - 1, b - 1, relieveOscuroPaint)
        canvas.drawLine(r - 1, t + 1, r - 1, b - 1, relieveOscuroPaint)
        val m = grosor / 2f
        canvas.drawRect(l + m, t + m, r - m, b - m, relieveMedioPaint)
    }

    /** Símbolo de apertura (flechas, triángulos, cruz de paño fijo). */
    private fun dibujarApertura(canvas: Canvas, glassL: Float, glassT: Float, glassR: Float, glassB: Float, mechanismId: String) {
        if (!mostrarSimboloApertura) return
        val cX = (glassL + glassR) / 2f
        val cY = (glassT + glassB) / 2f
        val paneW = glassR - glassL
        val paneH = glassB - glassT
        if (paneW <= dpToPx(3f) || paneH <= dpToPx(3f)) return
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

            "9" -> { // proyectante: bisagra arriba, abre hacia afuera por abajo
                canvas.drawLine(glassL + padding, glassB - padding, cX, glassT + padding, crosshairPaint)
                canvas.drawLine(glassR - padding, glassB - padding, cX, glassT + padding, crosshairPaint)
            }

            "10", "11" -> { // guillotina: corredera vertical hacia arriba / abajo
                val arrowLen = paneH * 0.35f
                val haciaArriba = mechanismId == "10"
                val punta = if (haciaArriba) cY - arrowLen / 2f else cY + arrowLen / 2f
                val base = if (haciaArriba) cY - arrowLen / 6f else cY + arrowLen / 6f
                canvas.drawLine(cX, cY - arrowLen / 2f, cX, cY + arrowLen / 2f, crosshairPaint)
                canvas.drawLine(cX - arrowHeadSize, base, cX, punta, crosshairPaint)
                canvas.drawLine(cX + arrowHeadSize, base, cX, punta, crosshairPaint)
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
            altoTotalMm.toString(),
            DimensionType.TOTAL_HEIGHT,
            0,
            altoTotalMm,
            isCompactMode = isCompactMode
        )

        // Manija azul inferior (solo en modo interactivo del editor)
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
                    sectionVal.toString(),
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
                    panelVal.toString(),
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
            anchoTotalMm.toString(),
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

        if (modoImpresion) {
            // PDF: solo el número sobre un fondo blanco que corta la línea de cota
            val ancho = textP.measureText(text) + dpToPx(4f)
            canvas.drawRect(cX - ancho / 2f, badgeRect.top, cX + ancho / 2f, badgeRect.bottom, Paint().apply { color = Color.WHITE })
        } else {
            canvas.drawRoundRect(badgeRect, dpToPx(2f), dpToPx(2f), badgePaint)
            canvas.drawRoundRect(badgeRect, dpToPx(2f), dpToPx(2f), borderP)
        }
        canvas.drawText(text, cX, cY + dpToPx(if (isCompactMode) 2.5f else 4f), textP)

        badgeAreas.add(BadgeArea(badgeRect, type, index, currentValue))
    }
}
