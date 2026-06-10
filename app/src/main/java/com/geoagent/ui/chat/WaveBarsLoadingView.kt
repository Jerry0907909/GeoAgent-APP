package com.geoagent.ui.chat

import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.geoagent.R
import kotlin.math.max
import kotlin.random.Random

class WaveBarsLoadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var barCount = DEFAULT_BAR_COUNT
    private var barColorStart = ContextCompat.getColor(context, R.color.geoagent_blue_air)
    private var barColorEnd = ContextCompat.getColor(context, R.color.geoagent_red_air)
    private var highlightColor = ContextCompat.getColor(context, R.color.geoagent_yellow_air)
    private var barCornerRadius = dp(DEFAULT_CORNER_RADIUS_DP)
    private var animationDuration = DEFAULT_ANIMATION_DURATION
    private var flowDuration = DEFAULT_FLOW_DURATION

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val gradientMatrix = Matrix()
    private val random = Random(System.nanoTime())
    private val heightInterpolator = DecelerateInterpolator(1.7f)

    private var barHeights = FloatArray(barCount) { MIN_HEIGHT_RATIO + random.nextFloat() * 0.8f }
    private var startHeights = barHeights.copyOf()
    private var targetHeights = nextTargetHeights()
    private var flowProgress = 0f
    private var shader: LinearGradient? = null
    private var shaderWidth = 1f

    private var heightAnimator: ValueAnimator? = null
    private var flowAnimator: ValueAnimator? = null

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.WaveBarsLoadingView, defStyleAttr, 0)
        try {
            barCount = max(1, typedArray.getInt(R.styleable.WaveBarsLoadingView_barCount, DEFAULT_BAR_COUNT))
            barColorStart = typedArray.getColor(R.styleable.WaveBarsLoadingView_barColorStart, barColorStart)
            barColorEnd = typedArray.getColor(R.styleable.WaveBarsLoadingView_barColorEnd, barColorEnd)
            highlightColor = typedArray.getColor(R.styleable.WaveBarsLoadingView_highlightColor, highlightColor)
            barCornerRadius = typedArray.getDimension(
                R.styleable.WaveBarsLoadingView_barCornerRadius,
                barCornerRadius
            )
            animationDuration = typedArray.getInt(
                R.styleable.WaveBarsLoadingView_animationDuration,
                DEFAULT_ANIMATION_DURATION.toInt()
            ).toLong().coerceAtLeast(100L)
            flowDuration = typedArray.getInt(
                R.styleable.WaveBarsLoadingView_flowDuration,
                DEFAULT_FLOW_DURATION.toInt()
            ).toLong().coerceAtLeast(300L)
        } finally {
            typedArray.recycle()
        }
        resetBars()
    }

    fun startAnimation() {
        if (heightAnimator?.isStarted != true) {
            startHeightAnimator()
        }
        if (flowAnimator?.isStarted != true) {
            startFlowAnimator()
        }
    }

    fun stopAnimation() {
        heightAnimator?.cancel()
        flowAnimator?.cancel()
        heightAnimator = null
        flowAnimator = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == VISIBLE) startAnimation()
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && isAttachedToWindow) {
            startAnimation()
        } else if (changedView == this) {
            stopAnimation()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateShader()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val contentWidth = width - paddingLeft - paddingRight
        val contentHeight = height - paddingTop - paddingBottom
        if (contentWidth <= 0 || contentHeight <= 0) return

        val gap = contentWidth * GAP_RATIO / (barCount + 1)
        val barWidth = ((contentWidth - gap * (barCount - 1)) / barCount).coerceAtLeast(1f)
        val leftBase = paddingLeft.toFloat()
        val bottom = height - paddingBottom.toFloat()
        val topLimit = paddingTop.toFloat()
        val gradient = shader ?: return
        val shift = (flowProgress * (contentWidth + shaderWidth * 2f)) - shaderWidth

        // Move the shader instead of rebuilding it on every frame.
        gradientMatrix.setTranslate(leftBase + shift, 0f)
        gradient.setLocalMatrix(gradientMatrix)
        paint.shader = gradient

        for (index in 0 until barCount) {
            val barHeight = contentHeight * barHeights[index].coerceIn(MIN_HEIGHT_RATIO, 1f)
            val left = leftBase + index * (barWidth + gap)
            rect.set(left, bottom - barHeight, left + barWidth, bottom)
            rect.top = rect.top.coerceAtLeast(topLimit)
            canvas.drawRoundRect(rect, barCornerRadius, barCornerRadius, paint)
        }

        paint.shader = null
    }

    private fun startHeightAnimator() {
        heightAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animationDuration
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val progress = heightInterpolator.getInterpolation(animator.animatedFraction)
                for (index in barHeights.indices) {
                    barHeights[index] = startHeights[index] + (targetHeights[index] - startHeights[index]) * progress
                }
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationRepeat(animation: Animator) {
                    // Pick new per-bar targets once per cycle, then interpolate smoothly toward them.
                    startHeights = barHeights.copyOf()
                    targetHeights = nextTargetHeights()
                }
            })
            start()
        }
    }

    private fun startFlowAnimator() {
        flowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = flowDuration
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                flowProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun resetBars() {
        barHeights = FloatArray(barCount) { MIN_HEIGHT_RATIO + random.nextFloat() * (1f - MIN_HEIGHT_RATIO) }
        startHeights = barHeights.copyOf()
        targetHeights = nextTargetHeights()
    }

    private fun nextTargetHeights(): FloatArray {
        return FloatArray(barCount) { MIN_HEIGHT_RATIO + random.nextFloat() * (1f - MIN_HEIGHT_RATIO) }
    }

    private fun updateShader() {
        shaderWidth = max(1f, width.toFloat() * 0.72f)
        shader = LinearGradient(
            0f,
            0f,
            shaderWidth,
            0f,
            intArrayOf(barColorStart, barColorEnd, highlightColor, barColorEnd, barColorStart),
            floatArrayOf(0f, 0.32f, 0.5f, 0.68f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }

    companion object {
        private const val DEFAULT_BAR_COUNT = 4
        private const val DEFAULT_CORNER_RADIUS_DP = 8f
        private const val DEFAULT_ANIMATION_DURATION = 800L
        private const val DEFAULT_FLOW_DURATION = 2000L
        private const val MIN_HEIGHT_RATIO = 0.2f
        private const val GAP_RATIO = 0.22f
    }
}
