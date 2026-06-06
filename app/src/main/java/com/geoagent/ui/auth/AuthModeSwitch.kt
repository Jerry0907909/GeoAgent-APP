package com.geoagent.ui.auth

import android.animation.ValueAnimator
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.geoagent.R

enum class AuthLoginMode { PASSWORD, EMAIL_CODE }

class AuthModeSwitch(
    private val host: View,
    private val indicator: View,
    private val passwordTab: TextView,
    private val emailCodeTab: TextView,
    private val onModeChanged: (AuthLoginMode) -> Unit
) {
    private var currentMode = AuthLoginMode.PASSWORD
    private var animator: ValueAnimator? = null
    private var laidOut = false

    init {
        host.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (host.width > 0) {
                positionIndicator(currentMode, animate = laidOut)
                laidOut = true
            }
        }
        passwordTab.setOnClickListener { select(AuthLoginMode.PASSWORD, animate = true) }
        emailCodeTab.setOnClickListener { select(AuthLoginMode.EMAIL_CODE, animate = true) }
    }

    fun select(mode: AuthLoginMode, animate: Boolean) {
        if (mode == currentMode && laidOut) return
        currentMode = mode
        host.post {
            positionIndicator(mode, animate && laidOut)
            updateTabColors(mode)
            onModeChanged(mode)
        }
    }

    private fun positionIndicator(mode: AuthLoginMode, animate: Boolean) {
        val selected = if (mode == AuthLoginMode.EMAIL_CODE) emailCodeTab else passwordTab
        val targetMargin = selected.left
        val targetWidth = selected.width
        val lp = (indicator.layoutParams as FrameLayout.LayoutParams).apply {
            width = targetWidth
            height = FrameLayout.LayoutParams.MATCH_PARENT
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        indicator.layoutParams = lp
        indicator.translationX = 0f
        if (animate) {
            animator?.cancel()
            val startMargin = lp.leftMargin
            ValueAnimator.ofInt(startMargin, targetMargin).apply {
                duration = 280L
                interpolator = DecelerateInterpolator(1.4f)
                addUpdateListener { animation ->
                    (indicator.layoutParams as FrameLayout.LayoutParams).leftMargin =
                        animation.animatedValue as Int
                    indicator.requestLayout()
                }
                animator = this
                start()
            }
        } else {
            lp.leftMargin = targetMargin
            indicator.layoutParams = lp
        }
    }

    private fun updateTabColors(mode: AuthLoginMode) {
        val ctx = host.context
        val primary = ContextCompat.getColor(ctx, R.color.primary)
        val muted = ContextCompat.getColor(ctx, R.color.text_muted)
        if (mode == AuthLoginMode.EMAIL_CODE) {
            styleTab(emailCodeTab, primary, bold = true)
            styleTab(passwordTab, muted, bold = false)
        } else {
            styleTab(passwordTab, primary, bold = true)
            styleTab(emailCodeTab, muted, bold = false)
        }
    }

    private fun styleTab(tab: TextView, color: Int, bold: Boolean) {
        tab.setTextColor(color)
        tab.setTypeface(tab.typeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
}
