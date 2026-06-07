package com.geoagent.ui.chat

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import com.geoagent.R

class ShimmerTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val brandBlue = ContextCompat.getColor(context, R.color.geoagent_blue)
    private val brandBlueLight = ContextCompat.getColor(context, R.color.geoagent_blue_air)
    private val brandYellow = ContextCompat.getColor(context, R.color.geoagent_yellow_air)
    private var progress = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1400L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        setTextColor(brandBlue)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && isAttachedToWindow) {
            if (!animator.isStarted) animator.start()
        } else {
            animator.cancel()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat().coerceAtLeast(1f)
        val shimmerWidth = w * 0.82f
        val start = -shimmerWidth + progress * (w + shimmerWidth * 2f)
        paint.shader = LinearGradient(
            start,
            0f,
            start + shimmerWidth,
            0f,
            intArrayOf(
                brandBlue,
                brandBlueLight,
                brandYellow,
                brandBlue
            ),
            floatArrayOf(0f, 0.42f, 0.56f, 1f),
            Shader.TileMode.CLAMP
        )
        super.onDraw(canvas)
        paint.shader = null
    }
}
