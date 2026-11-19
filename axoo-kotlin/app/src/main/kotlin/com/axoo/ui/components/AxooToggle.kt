package com.axoo.ui.components

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.widget.Checkable
import com.axoo.ui.theme.NativeColors
import com.axoo.ui.theme.dp

/** Toggle (pulsante) per attivare o disattivare una opzione */
class AxooToggle(context: Context) : View(context), Checkable {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val track = RectF()
    private val colorEvaluator = ArgbEvaluator()
    private var checked = false
    private var progress = 0f
    private var animator: ValueAnimator? = null
    private var onCheckedChange: ((Boolean) -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
    }

    fun setOnCheckedChangeListener(listener: (Boolean) -> Unit) {
        onCheckedChange = listener
    }

    override fun isChecked(): Boolean = checked

    override fun setChecked(checked: Boolean) {
        setChecked(checked, animated = isAttachedToWindow)
    }

    fun setChecked(checked: Boolean, animated: Boolean) {
        if (this.checked == checked) return
        this.checked = checked
        animator?.cancel()
        if (!animated) {
            progress = if (checked) 1f else 0f
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(progress, if (checked) 1f else 0f).apply {
            duration = 200L
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun toggle() {
        setChecked(!checked, animated = true)
        onCheckedChange?.invoke(checked)
    }

    override fun performClick(): Boolean {
        super.performClick()
        toggle()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val trackWidth = context.dp(31).toFloat()
        val trackHeight = context.dp(14).toFloat()
        val thumbTravel = context.dp(17).toFloat()
        val startX = centerX - trackWidth / 2f
        val thumbX = startX + context.dp(7) + thumbTravel * progress
        val color = colorEvaluator.evaluate(progress, NativeColors.border, NativeColors.accent) as Int

        paint.color = color
        track.set(
            startX,
            centerY - trackHeight / 2f,
            startX + trackWidth,
            centerY + trackHeight / 2f,
        )
        canvas.drawRoundRect(track, trackHeight / 2f, trackHeight / 2f, paint)
        canvas.drawCircle(thumbX, centerY, context.dp(10).toFloat(), paint)

        paint.color = NativeColors.white
        canvas.drawCircle(thumbX, centerY, context.dp(8).toFloat(), paint)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }



}
