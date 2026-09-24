package com.scantranslate

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout

/** A movable/resizable overlay rectangle. The button commits the current geometry. */
class ScanBoxView(
    context: Context,
    private val onConfirm: () -> Unit,
    private val onChanged: (dx: Int, dy: Int, dw: Int, dh: Int) -> Unit
) : FrameLayout(context) {
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(99, 102, 241)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(99, 102, 241) }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 12f }
    private var downX = 0f
    private var downY = 0f
    private var moving = false
    private var resizing = false
    private var baseW = 0
    private var baseH = 0

    init {
        setWillNotDraw(false)
        setBackgroundColor(Color.argb(28, 99, 102, 241))
        val confirm = Button(context).apply {
            text = "✓"
            textSize = 18f
            contentDescription = "确认扫描框"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.rgb(79, 70, 229)); cornerRadius = 12f
            }
            setOnClickListener { onConfirm() }
        }
        addView(confirm, LayoutParams(58, 52, Gravity.CENTER_VERTICAL or Gravity.END).apply { rightMargin = 8 })
        isClickable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(2f, 2f, width - 2f, height - 2f, borderPaint)
        canvas.drawRect(width - 28f, height - 28f, width - 4f, height - 4f, handlePaint)
        canvas.drawText("拖动边框调整区域", 12f, 24f, hintPaint)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        // Let the confirm button receive taps. All other touches manipulate the rectangle.
        val isButtonArea = event.x > width - 82 && event.y in (height / 2 - 38f)..(height / 2 + 38f)
        return !isButtonArea
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX; downY = event.rawY; baseW = width; baseH = height
                resizing = event.x > width - 72 && event.y > height - 72
                moving = !resizing
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - downX).toInt(); val dy = (event.rawY - downY).toInt()
                if (resizing) onChanged(0, 0, dx, dy) else if (moving) onChanged(dx, dy, 0, 0)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { moving = false; resizing = false; return true }
        }
        return true
    }
}
