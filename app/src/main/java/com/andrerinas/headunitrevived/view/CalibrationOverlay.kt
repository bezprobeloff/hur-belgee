package com.andrerinas.headunitrevived.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.andrerinas.headunitrevived.R
import com.andrerinas.headunitrevived.utils.HeadUnitScreenConfig
import com.andrerinas.headunitrevived.utils.Settings
import kotlin.math.roundToInt

class CalibrationOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val settings = Settings(context)
    private var onCalibrationChanged: ((Int, Int, Int, Int) -> Unit)? = null
    private var onSaveRequested: (() -> Unit)? = null
    private var onCancelRequested: (() -> Unit)? = null

    // Draggable offsets (relative to screen edges)
    private var currentLeft = settings.insetLeft
    private var currentTop = settings.insetTop
    private var currentRight = settings.insetRight
    private var currentBottom = settings.insetBottom

    private val maskPaint = Paint().apply {
        color = Color.parseColor("#AA000000")
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(10f, 0f, 0f, Color.BLACK)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f
        typeface = Typeface.DEFAULT_BOLD
    }

    private val handleRadius = 50f
    private val touchSlop = 120f

    private var activeHandle = -1 // 0: TL, 1: TR, 2: BL, 3: BR
    private var touchOffsetX = 0f
    private var touchOffsetY = 0f

    fun initInsets(l: Int, t: Int, r: Int, b: Int) {
        currentLeft = l
        currentTop = t
        currentRight = r
        currentBottom = b
        invalidate()
    }

    fun setCallbacks(
        onChanged: (Int, Int, Int, Int) -> Unit,
        onSave: () -> Unit,
        onCancel: () -> Unit
    ) {
        this.onCalibrationChanged = onChanged
        this.onSaveRequested = onSave
        this.onCancelRequested = onCancel
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        val rectL = currentLeft.toFloat()
        val rectT = currentTop.toFloat()
        val rectR = w - currentRight
        val rectB = h - currentBottom

        // 1. Draw darkening mask
        // Left
        canvas.drawRect(0f, 0f, rectL, h, maskPaint)
        // Right
        canvas.drawRect(rectR, 0f, w, h, maskPaint)
        // Top
        canvas.drawRect(rectL, 0f, rectR, rectT, maskPaint)
        // Bottom
        canvas.drawRect(rectL, rectB, rectR, h, maskPaint)

        // 2. Draw border
        canvas.drawRect(rectL, rectT, rectR, rectB, strokePaint)

        // 3. Draw handles
        drawHandle(canvas, rectL, rectT) // TL
        drawHandle(canvas, rectR, rectT) // TR
        drawHandle(canvas, rectL, rectB) // BL
        drawHandle(canvas, rectR, rectB) // BR

        // 4. Draw HUD Info
        val hudPadding = 60f
        var yPos = hudPadding + 40f
        val viewportStr = context.getString(R.string.viewport)
        val marginsStr = context.getString(R.string.handshake_margins)
        canvas.drawText("$viewportStr: ${(rectR - rectL).toInt()}x${(rectB - rectT).toInt()}", hudPadding, yPos, textPaint)
        yPos += 50f
        canvas.drawText("$marginsStr: L:$currentLeft T:$currentTop R:$currentRight B:$currentBottom", hudPadding, yPos, textPaint)
        
        // 5. Draw Instructions
        val instr = context.getString(R.string.calibration_instruction)
        val instrBounds = Rect()
        textPaint.getTextBounds(instr, 0, instr.length, instrBounds)
        canvas.drawText(instr, (w - instrBounds.width()) / 2, h - 200f, textPaint)
    }

    private fun drawHandle(canvas: Canvas, x: Float, y: Float) {
        canvas.drawCircle(x, y, handleRadius, handlePaint)
        // Crosshair
        val p = Paint().apply { color = Color.BLACK; strokeWidth = 2f }
        canvas.drawLine(x - 20, y, x + 20, y, p)
        canvas.drawLine(x, y - 20, x, y + 20, p)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        val w = width.toFloat()
        val h = height.toFloat()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val h0x = currentLeft.toFloat()
                val h0y = currentTop.toFloat()
                val h1x = w - currentRight
                val h1y = currentTop.toFloat()
                val h2x = currentLeft.toFloat()
                val h2y = h - currentBottom
                val h3x = w - currentRight
                val h3y = h - currentBottom

                activeHandle = when {
                    isNear(x, y, h0x, h0y) -> 0
                    isNear(x, y, h1x, h1y) -> 1
                    isNear(x, y, h2x, h2y) -> 2
                    isNear(x, y, h3x, h3y) -> 3
                    else -> -1
                }

                if (activeHandle != -1) {
                    val targetX = when (activeHandle) {
                        0, 2 -> h0x
                        else -> h1x
                    }
                    val targetY = when (activeHandle) {
                        0, 1 -> h0y
                        else -> h2y
                    }
                    touchOffsetX = x - targetX
                    touchOffsetY = y - targetY
                }
                return activeHandle != -1
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeHandle != -1) {
                    val snapLimit = 10
                    val adjX = x - touchOffsetX
                    val adjY = y - touchOffsetY
                    
                    when (activeHandle) {
                        0 -> { // TL
                            currentLeft = if (adjX < snapLimit) 0 else adjX.roundToInt().coerceAtLeast(0)
                            currentTop = if (adjY < snapLimit) 0 else adjY.roundToInt().coerceAtLeast(0)
                        }
                        1 -> { // TR
                            currentRight = if (w - adjX < snapLimit) 0 else (w - adjX).roundToInt().coerceAtLeast(0)
                            currentTop = if (adjY < snapLimit) 0 else adjY.roundToInt().coerceAtLeast(0)
                        }
                        2 -> { // BL
                            currentLeft = if (adjX < snapLimit) 0 else adjX.roundToInt().coerceAtLeast(0)
                            currentBottom = if (h - adjY < snapLimit) 0 else (h - adjY).roundToInt().coerceAtLeast(0)
                        }
                        3 -> { // BR
                            currentRight = if (w - adjX < snapLimit) 0 else (w - adjX).roundToInt().coerceAtLeast(0)
                            currentBottom = if (h - adjY < snapLimit) 0 else (h - adjY).roundToInt().coerceAtLeast(0)
                        }
                    }
                    onCalibrationChanged?.invoke(currentLeft, currentTop, currentRight, currentBottom)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                activeHandle = -1
            }
        }
        return true
    }

    private fun isNear(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        return Math.abs(x1 - x2) < touchSlop && Math.abs(y1 - y2) < touchSlop
    }

    fun reset() {
        currentLeft = 0
        currentTop = 0
        currentRight = 0
        currentBottom = 0
        onCalibrationChanged?.invoke(0, 0, 0, 0)
        invalidate()
    }
}
