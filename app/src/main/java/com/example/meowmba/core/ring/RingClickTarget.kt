package com.example.meowmba.core.ring

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/** Small Accessibility overlay; removed before injected touches so the app receives them. */
internal class RingClickTarget(context: Context) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private val side = (48 * context.resources.displayMetrics.density).toInt()
    var point = RingPoint(); private set
    var dragging = false; private set
    val visible get() = view.parent != null
    private val params = WindowManager.LayoutParams(side, side,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        title = "Meowmba ring click target"
    }
    private val view = object : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f; val cy = height / 2f
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 3 * resources.displayMetrics.density
            paint.color = Color.BLACK
            canvas.drawCircle(cx, cy, width * .24f, paint)
            paint.strokeWidth = resources.displayMetrics.density; paint.color = Color.WHITE
            canvas.drawCircle(cx, cy, width * .24f, paint)
            canvas.drawLine(cx - width * .34f, cy, cx + width * .34f, cy, paint)
            canvas.drawLine(cx, cy - height * .34f, cx, cy + height * .34f, paint)
            paint.style = Paint.Style.FILL; paint.color = Color.CYAN
            canvas.drawCircle(cx, cy, 2 * resources.displayMetrics.density, paint)
        }
    }.apply { contentDescription = "Ring click target; drag to move"; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
    init {
        var startX = 0f; var startY = 0f; var start = point
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { dragging = true; startX = event.rawX; startY = event.rawY; start = point }
                MotionEvent.ACTION_MOVE -> {
                    val bounds = windows.currentWindowMetrics.bounds
                    point = RingPoint(start.x + (event.rawX - startX) / bounds.width(), start.y + (event.rawY - startY) / bounds.height())
                    position()
                    if (visible) windows.updateViewLayout(view, params)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
            }
            true
        }
    }
    fun center() { point = RingPoint(); position(); if (visible) windows.updateViewLayout(view, params) }
    fun move(action: RingAction) { point = point.move(action); position(); if (visible) windows.updateViewLayout(view, params) }
    private fun position() {
        val bounds = windows.currentWindowMetrics.bounds
        val half = side / 2f
        val x = (point.x * bounds.width()).coerceIn(half, (bounds.width() - half).coerceAtLeast(half))
        val y = (point.y * bounds.height()).coerceIn(half, (bounds.height() - half).coerceAtLeast(half))
        point = RingPoint(x / bounds.width(), y / bounds.height())
        params.x = (x - half).toInt(); params.y = (y - half).toInt()
    }
    fun coordinates(): Pair<Float, Float> {
        position()
        val bounds = windows.currentWindowMetrics.bounds
        return point.x * bounds.width() to point.y * bounds.height()
    }
    fun show(): Boolean = runCatching {
        position()
        if (!visible) windows.addView(view, params) else windows.updateViewLayout(view, params)
        visible
    }.getOrDefault(false)
    fun hide() { dragging = false; if (visible) runCatching { windows.removeViewImmediate(view) } }
}
