package com.scantranslate

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import kotlin.math.roundToInt

/** A movable/resizable overlay rectangle. The button commits the current geometry. */
class ScanBoxView(
    context: Context,
    private val onConfirm: () -> Unit,
    private val onChanged: (x: Int, y: Int, width: Int, height: Int) -> Unit
) : FrameLayout(context) {
    private val hintText = context.getString(R.string.scan_hint)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(99, 102, 241)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(99, 102, 241) }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 16f * resources.displayMetrics.scaledDensity
    }
    private var downX = 0f
    private var downY = 0f
    private var moving = false
    private var resizing = false
    private var baseX = 0
    private var baseY = 0
    private var baseW = 0
    private var baseH = 0

    init {
        setWillNotDraw(false)
        setBackgroundColor(Color.argb(28, 99, 102, 241))
        val confirm = Button(context).apply {
            text = context.getString(R.string.confirm)
            textSize = 26f
            contentDescription = "Confirm scan area"
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 0)
            minWidth = 0
            minHeight = 0
            background = GradientDrawable().apply {
                setColor(Color.rgb(79, 70, 229)); cornerRadius = 12f
            }
            setOnClickListener { onConfirm() }
        }
        val density = resources.displayMetrics.density
        addView(confirm, LayoutParams((72 * density).roundToInt(), (64 * density).roundToInt(), Gravity.CENTER_VERTICAL or Gravity.END).apply {
            rightMargin = (8 * density).roundToInt()
        })
        isClickable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(2f, 2f, width - 2f, height - 2f, borderPaint)
        canvas.drawRect(width - 28f, height - 28f, width - 4f, height - 4f, handlePaint)
        canvas.drawText(hintText, 12f, 28f * resources.displayMetrics.density, hintPaint)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        // Let the confirm button receive taps. All other touches manipulate the rectangle.
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return false
        val button = getChildAt(0)
        val isButtonArea = event.x >= button.left && event.x < button.right &&
            event.y >= button.top && event.y < button.bottom
        return !isButtonArea
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val params = layoutParams as WindowManager.LayoutParams
                downX = event.rawX; downY = event.rawY
                baseX = params.x; baseY = params.y
                baseW = params.width; baseH = params.height
                resizing = event.x > width - 72 && event.y > height - 72
                moving = !resizing
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateGeometry(event)
                return true
            }
            MotionEvent.ACTION_UP -> {
                updateGeometry(event)
                moving = false; resizing = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> { moving = false; resizing = false; return true }
            MotionEvent.ACTION_POINTER_UP -> {
                // Do not switch to another finger's coordinates halfway through a gesture.
                if (event.actionIndex == 0) { moving = false; resizing = false }
            }
        }
        return true
    }

    private fun updateGeometry(event: MotionEvent) {
        // Every event is relative to the same snapshot, regardless of event frequency
        // or whether WindowManager has finished laying out the previous update.
        val dx = (event.rawX - downX).roundToInt()
        val dy = (event.rawY - downY).roundToInt()
        if (resizing) onChanged(baseX, baseY, baseW + dx, baseH + dy)
        else if (moving) onChanged(baseX + dx, baseY + dy, baseW, baseH)
    }
}
