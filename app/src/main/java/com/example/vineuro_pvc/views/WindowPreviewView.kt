package com.example.vineuro_pvc.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
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

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C8D8E8")
        style = Paint.Style.FILL
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#444444")
        style = Paint.Style.STROKE
        strokeWidth = 3f
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
        strokeWidth = 3f
    }

    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A90E2")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1976D2")
        textSize = 25f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#444444")
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND
    }

    private val circleCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#65A0D0F5")
        style = Paint.Style.FILL
    }

    private var config: WindowConfig? = null
    var showTechnicalDetails: Boolean = false

    var sectionHeights = floatArrayOf(600f, 600f, 600f)
    var sectionWidths = floatArrayOf(1500f)

    val altoTotalMm: Int
        get() = sectionHeights.sum().toInt()

    val anchoTotalMm: Int
        get() = sectionWidths.sum().toInt()

    private val badgeAreas = mutableListOf<BadgeArea>()
    private val paneAreas = mutableListOf<PaneArea>()

    // Mecanismos de apertura configurados por cada paño (Key: paneIndex, Value: mechanismId "1".."8")
    val paneMechanisms = mutableMapOf<Int, String>()

    var onDimensionClickListener: ((type: DimensionType, index: Int, currentValue: Int) -> Unit)? = null
    var onPaneClickListener: ((paneIndex: Int, row: Int, col: Int) -> Unit)? = null

    fun setConfig(windowConfig: WindowConfig, isTechnicalMode: Boolean = false) {
        this.config = windowConfig
        this.showTechnicalDetails = isTechnicalMode

        val rows = maxOf(1, windowConfig.rows)
        val cols = maxOf(1, windowConfig.cols)

        val defaultHeightPerSection = (1800f / rows)
        sectionHeights = FloatArray(rows) { defaultHeightPerSection }

        val defaultWidthPerSection = (1500f / cols)
        sectionWidths = FloatArray(cols) { defaultWidthPerSection }

        paneMechanisms.clear()

        invalidate()
    }

    fun updateSectionHeight(index: Int, newHeightMm: Int) {
        if ((index in sectionHeights.indices) && (newHeightMm >= 50)) {
            sectionHeights[index] = newHeightMm.toFloat()
            invalidate()
        }
    }

    fun updateSectionWidth(index: Int, newWidthMm: Int) {
        if ((index in sectionWidths.indices) && (newWidthMm >= 50)) {
            sectionWidths[index] = newWidthMm.toFloat()
            invalidate()
        }
    }

    fun updateTotalHeight(newTotalHeightMm: Int) {
        if ((newTotalHeightMm >= 100) && sectionHeights.isNotEmpty()) {
            val oldTotal = sectionHeights.sum()
            if (oldTotal > 0) {
                val scale = newTotalHeightMm.toFloat() / oldTotal
                for (i in sectionHeights.indices) {
                    sectionHeights[i] = sectionHeights[i] * scale
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
                    sectionWidths[i] = sectionWidths[i] * scale
                }
            }
            invalidate()
        }
    }

    fun setPaneMechanism(paneIndex: Int, mechanismId: String) {
        paneMechanisms[paneIndex] = mechanismId
        invalidate()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!showTechnicalDetails) return super.onTouchEvent(event)

        if (event.action == MotionEvent.ACTION_UP) {
            val x = event.x
            val y = event.y

            // 1. Detección de clic en números de cota (dimensiones)
            for (badge in badgeAreas) {
                if (badge.rect.contains(x, y)) {
                    performClick()
                    onDimensionClickListener?.invoke(badge.type, badge.index, badge.currentValue)
                    return true
                }
            }

            // 2. Detección de clic en la cruz (+) o paño de la ventana
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        badgeAreas.clear()
        paneAreas.clear()

        val cfg = config ?: return
        val w = width.toFloat()
        val h = height.toFloat()

        if ((w <= 0) || (h <= 0)) return

        if (!showTechnicalDetails) {
            val padding = 8f
            val rect = RectF(padding, padding, w - padding, h - padding)
            drawWindowConfig(canvas, rect, cfg)
            return
        }

        val leftMargin = 150f
        val bottomMargin = 85f
        val topMargin = 30f
        val rightMargin = 30f

        val availW = w - leftMargin - rightMargin
        val availH = h - topMargin - bottomMargin

        if ((availW <= 0) || (availH <= 0)) return

        val targetAspect = anchoTotalMm.toFloat() / altoTotalMm.toFloat()
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
        val frameThickness = if (showTechnicalDetails) 14f else 8f

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
        canvas.drawRect(pL, pT, pR, pB, glassPaint)

        if (!showTechnicalDetails) return

        val paneRect = RectF(pL, pT, pR, pB)
        paneAreas.add(PaneArea(paneRect, paneIndex, row, col))

        val cX = (pL + pR) / 2f
        val cY = (pT + pB) / 2f
        val paneW = pR - pL
        val paneH = pB - pT

        if (paneW <= 10f || paneH <= 10f) return

        val mechanismId = paneMechanisms[paneIndex] ?: "5" // "5" = Fijo con Cruz (+) por defecto

        when (mechanismId) {
            "1" -> { // Deslizante Derecha (->)
                val arrowLen = paneW * 0.35f
                canvas.drawLine(cX - arrowLen / 2f, cY, cX + arrowLen / 2f, cY, crosshairPaint)
                canvas.drawLine(cX + arrowLen / 6f, cY - 12f, cX + arrowLen / 2f, cY, crosshairPaint)
                canvas.drawLine(cX + arrowLen / 6f, cY + 12f, cX + arrowLen / 2f, cY, crosshairPaint)
            }

            "2" -> { // Oscilobatiente Superior (V)
                canvas.drawLine(pL + 8f, pT + 8f, cX, pB - 8f, crosshairPaint)
                canvas.drawLine(pR - 8f, pT + 8f, cX, pB - 8f, crosshairPaint)
            }

            "3" -> { // Deslizante Izquierda (<-)
                val arrowLen = paneW * 0.35f
                canvas.drawLine(cX - arrowLen / 2f, cY, cX + arrowLen / 2f, cY, crosshairPaint)
                canvas.drawLine(cX - arrowLen / 6f, cY - 12f, cX - arrowLen / 2f, cY, crosshairPaint)
                canvas.drawLine(cX - arrowLen / 6f, cY + 12f, cX - arrowLen / 2f, cY, crosshairPaint)
            }

            "4" -> { // Practicable Izquierda (>)
                canvas.drawLine(pL + 8f, pT + 8f, pR - 8f, cY, crosshairPaint)
                canvas.drawLine(pL + 8f, pB - 8f, pR - 8f, cY, crosshairPaint)
            }

            "6" -> { // Practicable Derecha (<)
                canvas.drawLine(pR - 8f, pT + 8f, pL + 8f, cY, crosshairPaint)
                canvas.drawLine(pR - 8f, pB - 8f, pL + 8f, cY, crosshairPaint)
            }

            "7" -> { // Oscilobatiente Izquierda
                canvas.drawLine(pL + 8f, pT + 8f, pR - 8f, cY, crosshairPaint)
                canvas.drawLine(pL + 8f, pB - 8f, pR - 8f, cY, crosshairPaint)
                canvas.drawLine(pL + 8f, pT + 8f, cX, pB - 8f, crosshairPaint)
                canvas.drawLine(pR - 8f, pT + 8f, cX, pB - 8f, crosshairPaint)
            }

            "8" -> { // Oscilobatiente Derecha
                canvas.drawLine(pR - 8f, pT + 8f, pL + 8f, cY, crosshairPaint)
                canvas.drawLine(pR - 8f, pB - 8f, pL + 8f, cY, crosshairPaint)
                canvas.drawLine(pL + 8f, pT + 8f, cX, pB - 8f, crosshairPaint)
                canvas.drawLine(pR - 8f, pT + 8f, cX, pB - 8f, crosshairPaint)
            }

            else -> { // "5": Fijo Cruz (+) con círculo azul
                val radius = Math.min(paneW, paneH) * 0.28f
                canvas.drawCircle(cX, cY, radius, circleCenterPaint)

                val crossLength = radius * 1.5f
                canvas.drawLine(cX - crossLength, cY, cX + crossLength, cY, crosshairPaint)
                canvas.drawLine(cX, cY - crossLength, cX, cY + crossLength, crosshairPaint)
            }
        }
    }

    private fun drawGrid(canvas: Canvas, rect: RectF, cols: Int, rows: Int, t: Float) {
        canvas.drawRect(rect, framePaint)

        val innerL = rect.left + t
        val innerT = rect.top + t
        val innerR = rect.right - t
        val innerB = rect.bottom - t
        val totalInnerH = innerB - innerT
        val totalInnerW = innerR - innerL

        val totalHVal = sectionHeights.sum().coerceAtLeast(1f)
        val totalWVal = sectionWidths.sum().coerceAtLeast(1f)

        var currentY = innerT
        var pIdx = 0

        for (r in 0 until rows) {
            val rowRatio = if (r in sectionHeights.indices) sectionHeights[r] / totalHVal else (1f / rows)
            val cellH = totalInnerH * rowRatio
            val pT = currentY + 2f
            val pB = currentY + cellH - 2f

            var currentX = innerL
            for (c in 0 until cols) {
                val colRatio = if (c in sectionWidths.indices) sectionWidths[c] / totalWVal else (1f / cols)
                val cellW = totalInnerW * colRatio
                val pL = currentX + 2f
                val pR = currentX + cellW - 2f

                drawPaneWithMechanism(canvas, pL, pT, pR, pB, paneIndex = pIdx, row = r, col = c)
                pIdx++
                currentX += cellW
            }
            currentY += cellH
        }
    }

    private fun drawTopTransom(canvas: Canvas, rect: RectF, cols: Int, t: Float) {
        canvas.drawRect(rect, framePaint)

        val innerL = rect.left + t
        val innerT = rect.top + t
        val innerR = rect.right - t
        val innerB = rect.bottom - t

        val transomH = (innerB - innerT) * 0.28f

        drawPaneWithMechanism(canvas, innerL + 2f, innerT + 2f, innerR - 2f, innerT + transomH - 2f, paneIndex = 0)

        val cellW = (innerR - innerL) / cols
        val bottomT = innerT + transomH + 2f

        for (c in 0 until cols) {
            val pL = innerL + c * cellW + 2f
            val pR = innerL + (c + 1) * cellW - 2f
            drawPaneWithMechanism(canvas, pL, bottomT, pR, innerB - 2f, paneIndex = c + 1)
        }
    }

    private fun drawBottomTransom(canvas: Canvas, rect: RectF, cols: Int, t: Float) {
        canvas.drawRect(rect, framePaint)

        val innerL = rect.left + t
        val innerT = rect.top + t
        val innerR = rect.right - t
        val innerB = rect.bottom - t

        val transomH = (innerB - innerT) * 0.28f
        val topB = innerB - transomH - 2f

        val cellW = (innerR - innerL) / cols
        for (c in 0 until cols) {
            val pL = innerL + c * cellW + 2f
            val pR = innerL + (c + 1) * cellW - 2f
            drawPaneWithMechanism(canvas, pL, innerT + 2f, pR, topB, paneIndex = c)
        }

        drawPaneWithMechanism(canvas, innerL + 2f, innerB - transomH + 2f, innerR - 2f, innerB - 2f, paneIndex = cols)
    }

    private fun drawTopBottomTransom(canvas: Canvas, rect: RectF, cols: Int, t: Float) {
        canvas.drawRect(rect, framePaint)

        val innerL = rect.left + t
        val innerT = rect.top + t
        val innerR = rect.right - t
        val innerB = rect.bottom - t

        val transomH = (innerB - innerT) * 0.22f

        drawPaneWithMechanism(canvas, innerL + 2f, innerT + 2f, innerR - 2f, innerT + transomH - 2f, paneIndex = 0)

        val cellW = (innerR - innerL) / cols
        val midT = innerT + transomH + 2f
        val midB = innerB - transomH - 2f

        for (c in 0 until cols) {
            val pL = innerL + c * cellW + 2f
            val pR = innerL + (c + 1) * cellW - 2f
            drawPaneWithMechanism(canvas, pL, midT, pR, midB, paneIndex = c + 1)
        }

        drawPaneWithMechanism(canvas, innerL + 2f, innerB - transomH + 2f, innerR - 2f, innerB - 2f, paneIndex = cols + 1)
    }

    private fun drawDoorL(canvas: Canvas, rect: RectF, isLeftDoor: Boolean, hasTransom: Boolean, t: Float) {
        val splitX = if (isLeftDoor) rect.left + (rect.width() * 0.45f) else rect.left + (rect.width() * 0.55f)
        val windowBottomY = rect.top + (rect.height() * 0.65f)

        if (isLeftDoor) {
            canvas.drawRect(rect.left, rect.top, splitX, rect.bottom, framePaint)
            canvas.drawRect(splitX, rect.top, rect.right, windowBottomY, framePaint)
        } else {
            canvas.drawRect(rect.left, rect.top, splitX, windowBottomY, framePaint)
            canvas.drawRect(splitX, rect.top, rect.right, rect.bottom, framePaint)
        }

        val iL = rect.left + t
        val iT = rect.top + t
        val iR = rect.right - t
        val iB = rect.bottom - t

        val transomH = if (hasTransom) (iB - iT) * 0.22f else 0f

        if (isLeftDoor) {
            if (hasTransom) {
                drawPaneWithMechanism(canvas, iL + 2f, iT + 2f, splitX - 2f, iT + transomH - 2f, paneIndex = 0)
                drawPaneWithMechanism(canvas, iL + 2f, iT + transomH + 2f, splitX - 2f, iB - 2f, paneIndex = 1)
            } else {
                drawPaneWithMechanism(canvas, iL + 2f, iT + 2f, splitX - 2f, iB - 2f, paneIndex = 0)
            }
            if (hasTransom) {
                drawPaneWithMechanism(canvas, splitX + 2f, iT + 2f, iR - 2f, iT + transomH - 2f, paneIndex = 2)
                drawPaneWithMechanism(canvas, splitX + 2f, iT + transomH + 2f, iR - 2f, windowBottomY - t - 2f, paneIndex = 3)
            } else {
                drawPaneWithMechanism(canvas, splitX + 2f, iT + 2f, iR - 2f, windowBottomY - t - 2f, paneIndex = 1)
            }
        } else {
            if (hasTransom) {
                drawPaneWithMechanism(canvas, iL + 2f, iT + 2f, splitX - 2f, iT + transomH - 2f, paneIndex = 0)
                drawPaneWithMechanism(canvas, iL + 2f, iT + transomH + 2f, splitX - 2f, windowBottomY - t - 2f, paneIndex = 1)
            } else {
                drawPaneWithMechanism(canvas, iL + 2f, iT + 2f, splitX - 2f, windowBottomY - t - 2f, paneIndex = 0)
            }
            if (hasTransom) {
                drawPaneWithMechanism(canvas, splitX + 2f, iT + 2f, iR - 2f, iT + transomH - 2f, paneIndex = 2)
                drawPaneWithMechanism(canvas, splitX + 2f, iT + transomH + 2f, iR - 2f, iB - 2f, paneIndex = 3)
            } else {
                drawPaneWithMechanism(canvas, splitX + 2f, iT + 2f, iR - 2f, iB - 2f, paneIndex = 1)
            }
        }
    }

    private fun drawDoorT(canvas: Canvas, rect: RectF, hasTransom: Boolean, t: Float) {
        val splitLeftX = rect.left + (rect.width() * 0.3f)
        val splitRightX = rect.left + (rect.width() * 0.7f)
        val windowBottomY = rect.top + (rect.height() * 0.65f)

        canvas.drawRect(rect.left, rect.top, splitLeftX, windowBottomY, framePaint)
        canvas.drawRect(splitLeftX, rect.top, splitRightX, rect.bottom, framePaint)
        canvas.drawRect(splitRightX, rect.top, rect.right, windowBottomY, framePaint)

        val iL = rect.left + t
        val iT = rect.top + t
        val iR = rect.right - t
        val iB = rect.bottom - t

        val transomH = if (hasTransom) (iB - iT) * 0.22f else 0f

        drawPaneWithMechanism(canvas, iL + 2f, iT + (if (hasTransom) transomH + 2f else 2f), splitLeftX - 2f, windowBottomY - t - 2f, paneIndex = 0)
        drawPaneWithMechanism(canvas, splitLeftX + 2f, iT + (if (hasTransom) transomH + 2f else 2f), splitRightX - 2f, iB - 2f, paneIndex = 1)
        drawPaneWithMechanism(canvas, splitRightX + 2f, iT + (if (hasTransom) transomH + 2f else 2f), iR - 2f, windowBottomY - t - 2f, paneIndex = 2)

        if (hasTransom) {
            drawPaneWithMechanism(canvas, iL + 2f, iT + 2f, iR - 2f, iT + transomH - 2f, paneIndex = 3)
        }
    }

    private fun drawAsymmetric(canvas: Canvas, rect: RectF, cols: Int, rows: Int, t: Float) {
        drawGrid(canvas, rect, cols, rows, t)
    }

    private fun drawDimensionLines(canvas: Canvas, rect: RectF, cfg: WindowConfig) {
        val lineX = rect.left - 25f
        val lineOuterX = rect.left - 90f

        // Línea vertical principal (alto total)
        canvas.drawLine(lineOuterX, rect.top, lineOuterX, rect.bottom, linePaint)
        canvas.drawCircle(lineOuterX, rect.top, 5f, dotPaint)
        canvas.drawCircle(lineOuterX, rect.bottom, 5f, dotPaint)
        drawInteractiveBadge(
            canvas,
            lineOuterX,
            (rect.top + rect.bottom) / 2f,
            altoTotalMm.toString(),
            DimensionType.TOTAL_HEIGHT,
            0,
            altoTotalMm,
        )

        // Manija azul inferior
        canvas.drawCircle(lineOuterX, rect.bottom + 15f, 12f, handlePaint)
        canvas.drawCircle(lineOuterX, rect.bottom + 15f, 12f, handleStrokePaint)

        // Línea vertical por secciones
        canvas.drawLine(lineX, rect.top, lineX, rect.bottom, linePaint)
        canvas.drawCircle(lineX, rect.top, 5f, dotPaint)
        canvas.drawCircle(lineX, rect.bottom, 5f, dotPaint)

        val rows = maxOf(1, cfg.rows)
        val totalHVal = sectionHeights.sum().coerceAtLeast(1f)
        var currentY = rect.top

        for (r in 0 until rows) {
            val rowRatio = if (r in sectionHeights.indices) sectionHeights[r] / totalHVal else (1f / rows)
            val sectionH = rect.height() * rowRatio
            val sY1 = currentY
            val sY2 = currentY + sectionH
            canvas.drawCircle(lineX, sY2, 5f, dotPaint)

            val sectionVal = if (r in sectionHeights.indices) sectionHeights[r].toInt() else (altoTotalMm / rows)

            drawInteractiveBadge(
                canvas,
                lineX,
                (sY1 + sY2) / 2f,
                sectionVal.toString(),
                DimensionType.SECTION_HEIGHT,
                r,
                sectionVal,
            )
            currentY += sectionH
        }

        // Línea horizontal inferior (ancho total)
        val lineY = rect.bottom + 30f
        canvas.drawLine(rect.left, lineY, rect.right, lineY, linePaint)
        canvas.drawCircle(rect.left, lineY, 5f, dotPaint)
        canvas.drawCircle(rect.right, lineY, 5f, dotPaint)
        drawInteractiveBadge(
            canvas,
            (rect.left + rect.right) / 2f,
            lineY,
            anchoTotalMm.toString(),
            DimensionType.TOTAL_WIDTH,
            0,
            anchoTotalMm,
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
    ) {
        val badgeW = 68f
        val badgeH = 28f
        val badgeRect = RectF(cX - badgeW / 2f, cY - badgeH / 2f, cX + badgeW / 2f, cY + badgeH / 2f)

        canvas.drawRoundRect(badgeRect, 6f, 6f, badgePaint)
        canvas.drawRoundRect(badgeRect, 6f, 6f, badgeBorderPaint)
        canvas.drawText(text, cX, cY + 8f, textPaint)

        badgeAreas.add(BadgeArea(badgeRect, type, index, currentValue))
    }
}
