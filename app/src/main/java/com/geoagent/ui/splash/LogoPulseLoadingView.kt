package com.geoagent.ui.splash

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.geoagent.R
import com.geoagent.ui.motion.MotionUtils

class LogoPulseLoadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val gradientMatrix = Matrix()
    private val blue = ContextCompat.getColor(context, R.color.geoagent_blue)
    private val red = ContextCompat.getColor(context, R.color.geoagent_red)
    private val yellow = ContextCompat.getColor(context, R.color.geoagent_yellow)
    private var progress = 0f
    private var shader: LinearGradient? = null

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1500L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
    }

    fun startAnimation() {
        if (MotionUtils.animationsEnabled() && !animator.isStarted) animator.start()
    }

    fun stopAnimation() {
        animator.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimation()
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        shader = LinearGradient(
            0f,
            0f,
            w.toFloat().coerceAtLeast(1f),
            0f,
            intArrayOf(blue, red, yellow, blue),
            floatArrayOf(0f, 0.36f, 0.68f, 1f),
            Shader.TileMode.MIRROR
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val contentWidth = width - paddingLeft - paddingRight
        val contentHeight = height - paddingTop - paddingBottom
        if (contentWidth <= 0 || contentHeight <= 0) return

        val barHeight = contentHeight * 0.38f
        val radius = barHeight / 2f
        val top = paddingTop + (contentHeight - barHeight) / 2f
        val left = paddingLeft.toFloat()
        val right = width - paddingRight.toFloat()

        val gradient = shader ?: return
        gradientMatrix.setTranslate(progress * contentWidth, 0f)
        gradient.setLocalMatrix(gradientMatrix)
        paint.shader = gradient
        paint.alpha = 255

        rect.set(left, top, right, top + barHeight)
        canvas.drawRoundRect(rect, radius, radius, paint)

        paint.shader = null
    }
}
