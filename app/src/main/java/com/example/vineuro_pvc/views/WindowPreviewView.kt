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

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val frameBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
        style = Paint.Style.STROKE
        strokeWidth = 2f
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
        strokeWidth = 1f
    }

    private val handlePlatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        style = Paint.Style.FILL
    }

    private val doorHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#444444")
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 1.5f
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
        color = Color.parseColor("#333333")
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
                floatArrayOf(600f, currentTotalH - 600f)
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
        badgeAreas.clear()
        paneAreas.clear()
        invalidate()
    }

    fun updateDimensions(alto: Int, ancho: Int) {
        updateTotalHeight(alto)
        updateTotalWidth(ancho)
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

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!showTechnicalDetails) return false

        if (event.action == MotionEvent.ACTION_UP) {
            val x = event.x
            val y = event.y

            for (badge in badgeAreas) {
                if (badge.rect.contains(x, y)) {
                    performClick()
                    onDimensionClickListener?.invoke(badge.type, badge.index, badge.currentValue)
                    return true
                }
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        badgeAreas.clear()
        paneAreas.clear()

        val cfg = config ?: return
        val w = width.toFloat()
        val h = height.toFloat()

        if ((w <= 0) || (h <= 0)) return

        if (!showTechnicalDetails) {
            val padding = 4f
            val rect = RectF(padding, padding, w - padding, h - padding)
            drawWindowConfig(canvas, rect, cfg)
            return
        }

        val leftMargin = 150f
        val bottomMargin = 110f
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
        val frameThickness = if (showTechnicalDetails) 14f else 5f

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

        val subCols = paneCols[paneIndex] ?: 1
        if (subCols > 1) {
            val subW = (pR - pL) / subCols
            for (sc in 0 until subCols) {
                val spL = pL + sc * subW + (if (sc > 0) 3f else 0f)
                val spR = pL + (sc + 1) * subW - (if (sc < subCols - 1) 3f else 0f)
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

        val isSandwich = paneSandwich[paneIndex] == true
        val isMosqNet = paneMosqNet[paneIndex] == true
        val isPuerta = panePuerta[paneIndex] == true

        var glassL = pL
        var glassT = pT
        var glassR = pR
        var glassB = pB

        if (isPuerta && showTechnicalDetails) {
            val sashThickness = 12f
            canvas.drawRect(pL, pT, pR, pB, framePaint)
            canvas.drawRect(pL, pT, pR, pB, frameBorderPaint)

            glassL = pL + sashThickness
            glassT = pT + sashThickness
            glassR = pR - sashThickness
            glassB = pB - sashThickness

            val cY = (glassT + glassB) / 2f
            val handleX = glassL + 12f

            canvas.drawRoundRect(RectF(handleX - 4f, cY - 22f, handleX + 4f, cY + 22f), 3f, 3f, handlePlatePaint)
            canvas.drawRoundRect(RectF(handleX - 4f, cY - 22f, handleX + 4f, cY + 22f), 3f, 3f, frameBorderPaint)

            canvas.drawRoundRect(RectF(handleX - 14f, cY - 12f, handleX - 2f, cY + 8f), 3f, 3f, doorHandlePaint)
            canvas.drawRoundRect(RectF(handleX - 22f, cY - 12f, handleX - 14f, cY - 2f), 2f, 2f, doorHandlePaint)
        }

        canvas.drawRect(glassL, glassT, glassR, glassB, if (isSandwich) sandwichPaint else glassPaint)

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

        val paneRect = RectF(pL, pT, pR, pB)
        if (showTechnicalDetails) {
            paneAreas.add(PaneArea(paneRect, paneIndex, row, col))
        }

        val cX = (glassL + glassR) / 2f
        val cY = (glassT + glassB) / 2f
        val paneW = glassR - glassL
        val paneH = glassB - glassT

        if (paneW <= 6f || paneH <= 6f) return

        val mechanismId = paneMechanisms[paneIndex] ?: "5"

        when (mechanismId) {
            "1" -> {
                val arrowLen = paneW * 0.35f
                canvas.drawLine(cX - arrowLen / 2f, cY, cX + arrowLen / 2f, cY, crosshairPaint)
                canvas.drawLine(cX + arrowLen / 6f, cY - 8f, cX + arrowLen / 2f, cY, crosshairPaint)
                canvas.drawLine(cX + arrowLen / 6f, cY + 8f, cX + arrowLen / 2f, cY, crosshairPaint)
            }

            "2" -> {
                canvas.drawLine(glassL + 4f, glassT + 4f, cX, glassB - 4f, crosshairPaint)
                canvas.drawLine(glassR - 4f, glassT + 4f, cX, glassB - 4f, crosshairPaint)
            }

            "3" -> {
                val arrowLen = paneW * 0.35f
                canvas.drawLine(cX - arrowLen / 2f, cY, cX + arrowLen / 2f, cY, crosshairPaint)
                canvas.drawLine(cX - arrowLen / 6f, cY - 8f, cX - arrowLen / 2f, cY, crosshairPaint)
                canvas.drawLine(cX - arrowLen / 6f, cY + 8f, cX - arrowLen / 2f, cY, crosshairPaint)
            }

            "4" -> {
                canvas.drawLine(glassL + 4f, glassT + 4f, glassR - 4f, cY, crosshairPaint)
                canvas.drawLine(glassL + 4f, glassB - 4f, glassR - 4f, cY, crosshairPaint)
            }

            "6" -> {
                canvas.drawLine(glassR - 4f, glassT + 4f, glassL + 4f, cY, crosshairPaint)
                canvas.drawLine(glassR - 4f, glassB - 4f, glassL + 4f, cY, crosshairPaint)
            }

            "7" -> {
                canvas.drawLine(glassL + 4f, glassT + 4f, glassR - 4f, cY, crosshairPaint)
                canvas.drawLine(glassL + 4f, glassB - 4f, glassR - 4f, cY, crosshairPaint)
                canvas.drawLine(glassL + 4f, glassT + 4f, cX, glassB - 4f, crosshairPaint)
                canvas.drawLine(glassR - 4f, glassT + 4f, cX, glassB - 4f, crosshairPaint)
            }

            "8" -> {
                canvas.drawLine(glassR - 4f, glassT + 4f, glassL + 4f, cY, crosshairPaint)
                canvas.drawLine(glassR - 4f, glassB - 4f, glassL + 4f, cY, crosshairPaint)
                canvas.drawLine(glassL + 4f, glassT + 4f, cX, glassB - 4f, crosshairPaint)
                canvas.drawLine(glassR - 4f, glassT + 4f, cX, glassB - 4f, crosshairPaint)
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
        var pIdx = 0

        for (r in 0 until rows) {
            val rowRatio = activeHeights[r] / totalHVal
            val cellH = totalInnerH * rowRatio
            val pT = currentY + 2f
            val pB = (currentY + cellH - 2f).coerceAtMost(innerB - 2f)

            var currentX = innerL
            for (c in 0 until cols) {
                val colRatio = activeWidths[c] / totalWVal
                val cellW = totalInnerW * colRatio
                val pL = currentX + 2f
                val pR = (currentX + cellW - 2f).coerceAtMost(innerR - 2f)

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

        val innerL = rect.left + t
        val innerT = rect.top + t
        val innerR = rect.right - t
        val innerB = rect.bottom - t

        val activeHeights = getScaledActiveHeights(2)
        val transomRatio = activeHeights[0] / activeHeights.sum().coerceAtLeast(1f)
        val transomH = (innerB - innerT) * transomRatio

        drawPaneWithMechanism(canvas, innerL + 2f, innerT + 2f, innerR - 2f, innerT + transomH - 2f, paneIndex = 0)

        val cellW = (innerR - innerL) / cols
        val bottomT = innerT + transomH + 2f

        for (c in 0 until cols) {
            val pL = innerL + c * cellW + 2f
            val pR = (innerL + (c + 1) * cellW - 2f).coerceAtMost(innerR - 2f)
            drawPaneWithMechanism(canvas, pL, bottomT, pR, innerB - 2f, paneIndex = c + 1)
        }
    }

    private fun drawBottomTransom(canvas: Canvas, rect: RectF, cols: Int, t: Float) {
        canvas.drawRect(rect, framePaint)

        val innerL = rect.left + t
        val innerT = rect.top + t
        val innerR = rect.right - t
        val innerB = rect.bottom - t

        val activeHeights = getScaledActiveHeights(2)
        val mainRatio = activeHeights[0] / activeHeights.sum().coerceAtLeast(1f)
        val topB = innerT + (innerB - innerT) * mainRatio

        val cellW = (innerR - innerL) / cols
        for (c in 0 until cols) {
            val pL = innerL + c * cellW + 2f
            val pR = (innerL + (c + 1) * cellW - 2f).coerceAtMost(innerR - 2f)
            drawPaneWithMechanism(canvas, pL, innerT + 2f, pR, topB - 2f, paneIndex = c)
        }

        drawPaneWithMechanism(canvas, innerL + 2f, topB + 2f, innerR - 2f, innerB - 2f, paneIndex = cols)
    }

    private fun drawTopBottomTransom(canvas: Canvas, rect: RectF, cols: Int, t: Float) {
        canvas.drawRect(rect, framePaint)

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

        drawPaneWithMechanism(canvas, innerL + 2f, innerT + 2f, innerR - 2f, innerT + topH - 2f, paneIndex = 0)

        val cellW = (innerR - innerL) / cols
        val midT = innerT + topH + 2f
        val midB = midT + midH - 4f

        for (c in 0 until cols) {
            val pL = innerL + c * cellW + 2f
            val pR = (innerL + (c + 1) * cellW - 2f).coerceAtMost(innerR - 2f)
            drawPaneWithMechanism(canvas, pL, midT, pR, midB, paneIndex = c + 1)
        }

        drawPaneWithMechanism(canvas, innerL + 2f, midB + 4f, innerR - 2f, innerB - 2f, paneIndex = cols + 1)
    }

    private fun drawDoorL(canvas: Canvas, rect: RectF, isLeftDoor: Boolean, hasTransom: Boolean, t: Float) {
        val splitX = if (isLeftDoor) rect.left + (rect.width() * 0.45f) else rect.left + (rect.width() * 0.55f)

        val activeHeights = getScaledActiveHeights(2)
        val mainRatio = activeHeights[0] / activeHeights.sum().coerceAtLeast(1f)
        val windowBottomY = rect.top + (rect.height() * mainRatio)

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
        val splitLeftX = rect.left + (rect.width() * 0.33f)
        val splitRightX = rect.left + (rect.width() * 0.67f)

        val activeHeights = getScaledActiveHeights(2)
        val topRatio = activeHeights[0] / activeHeights.sum().coerceAtLeast(1f)
        val windowBottomY = rect.top + (rect.height() * topRatio)

        if (hasTransom) {
            canvas.drawRect(rect.left, rect.top, rect.right, windowBottomY, framePaint)
            canvas.drawRect(splitLeftX, windowBottomY, splitRightX, rect.bottom, framePaint)
        } else {
            canvas.drawRect(rect.left, rect.top, splitLeftX, windowBottomY, framePaint)
            canvas.drawRect(splitLeftX, rect.top, splitRightX, rect.bottom, framePaint)
            canvas.drawRect(splitRightX, rect.top, rect.right, windowBottomY, framePaint)
        }

        val iL = rect.left + t
        val iT = rect.top + t
        val iR = rect.right - t
        val iB = rect.bottom - t

        if (hasTransom) {
            drawPaneWithMechanism(canvas, iL + 2f, iT + 2f, iR - 2f, windowBottomY - 2f, paneIndex = 0)
            drawPaneWithMechanism(canvas, iL + 2f, windowBottomY + 2f, splitLeftX - 2f, iB - 2f, paneIndex = 1)
            drawPaneWithMechanism(canvas, splitLeftX + 2f, windowBottomY + 2f, splitRightX - 2f, iB - 2f, paneIndex = 2)
            drawPaneWithMechanism(canvas, splitRightX + 2f, windowBottomY + 2f, iR - 2f, iB - 2f, paneIndex = 3)
        } else {
            drawPaneWithMechanism(canvas, iL + 2f, iT + 2f, splitLeftX - 2f, windowBottomY - 2f, paneIndex = 0)
            drawPaneWithMechanism(canvas, splitRightX + 2f, iT + 2f, iR - 2f, windowBottomY - 2f, paneIndex = 2)
            drawPaneWithMechanism(canvas, splitLeftX + 2f, iT + 2f, splitRightX - 2f, iB - 2f, paneIndex = 1)
        }
    }

    private fun drawAsymmetric(canvas: Canvas, rect: RectF, cols: Int, rows: Int, t: Float) {
        drawGrid(canvas, rect, cols, rows, t)
    }

    private fun getScaledActiveHeights(rows: Int): FloatArray {
        val totalH = altoTotalMm.toFloat()
        val result = FloatArray(rows)

        if (sectionHeights.size == rows) {
            val rawSum = sectionHeights.sum()
            if (rawSum > 0f) {
                val scale = totalH / rawSum
                for (r in 0 until rows) {
                    result[r] = sectionHeights[r] * scale
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
        val totalW = anchoTotalMm.toFloat()
        val result = FloatArray(cols)

        if (sectionWidths.size == cols) {
            val rawSum = sectionWidths.sum()
            if (rawSum > 0f) {
                val scale = totalW / rawSum
                for (c in 0 until cols) {
                    result[c] = sectionWidths[c] * scale
                }
                return result
            }
        }

        for (c in 0 until cols) {
            result[c] = totalW / cols
        }
        return result
    }

    private fun drawDimensionLines(canvas: Canvas, rect: RectF, cfg: WindowConfig) {
        val lineX = rect.left - 25f
        val lineOuterX = rect.left - 90f

        // 1. Línea vertical principal (alto total)
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

        // 2. Línea vertical por secciones (alturas por sección/fila)
        canvas.drawLine(lineX, rect.top, lineX, rect.bottom, linePaint)
        canvas.drawCircle(lineX, rect.top, 5f, dotPaint)
        canvas.drawCircle(lineX, rect.bottom, 5f, dotPaint)

        val rows = getActiveLayoutRows(cfg)
        val activeHeights = getScaledActiveHeights(rows)
        val totalHVal = activeHeights.sum().coerceAtLeast(1f)
        var currentY = rect.top

        for (r in 0 until rows) {
            val rowRatio = activeHeights[r] / totalHVal
            val sectionH = rect.height() * rowRatio
            val sY1 = currentY
            val sY2 = currentY + sectionH
            canvas.drawCircle(lineX, sY2, 5f, dotPaint)

            val sectionVal = activeHeights[r].toInt()

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

        // 3. Línea horizontal por paneles/columnas (anchos por sección/columna)
        val cols = getActiveLayoutCols(cfg)
        val activeWidths = getScaledActiveWidths(cols)
        val totalWVal = activeWidths.sum().coerceAtLeast(1f)

        val lineInnerY = rect.bottom + 28f
        canvas.drawLine(rect.left, lineInnerY, rect.right, lineInnerY, linePaint)
        canvas.drawCircle(rect.left, lineInnerY, 5f, dotPaint)
        canvas.drawCircle(rect.right, lineInnerY, 5f, dotPaint)

        var currentX = rect.left
        for (c in 0 until cols) {
            val colRatio = activeWidths[c] / totalWVal
            val sectionW = rect.width() * colRatio
            val sX1 = currentX
            val sX2 = currentX + sectionW
            canvas.drawCircle(sX2, lineInnerY, 5f, dotPaint)

            val panelVal = activeWidths[c].toInt()

            drawInteractiveBadge(
                canvas,
                (sX1 + sX2) / 2f,
                lineInnerY,
                panelVal.toString(),
                DimensionType.SECTION_WIDTH,
                c,
                panelVal,
            )
            currentX += sectionW
        }

        // 4. Línea horizontal principal (ancho total)
        val lineOuterY = rect.bottom + 85f
        canvas.drawLine(rect.left, lineOuterY, rect.right, lineOuterY, linePaint)
        canvas.drawCircle(rect.left, lineOuterY, 5f, dotPaint)
        canvas.drawCircle(rect.right, lineOuterY, 5f, dotPaint)
        drawInteractiveBadge(
            canvas,
            (rect.left + rect.right) / 2f,
            lineOuterY,
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
