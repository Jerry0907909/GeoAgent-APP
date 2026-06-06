package com.geoagent.ui.chat

import android.animation.ValueAnimator
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.geoagent.R
import com.geoagent.domain.model.ChatMode

class DeepSeekModeSwitch(
    private val host: View,
    private val indicator: View,
    private val chatTab: TextView,
    private val ragTab: TextView
) {
    private var currentMode = ChatMode.CHAT
    private var animator: ValueAnimator? = null
    private var laidOut = false

    init {
        host.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (host.width > 0) {
                positionIndicator(currentMode, animate = laidOut)
                laidOut = true
            }
        }
    }

    fun select(mode: ChatMode, animate: Boolean) {
        currentMode = mode
        host.post {
            positionIndicator(mode, animate && laidOut)
            updateTabColors(mode)
        }
    }

    private fun positionIndicator(mode: ChatMode, animate: Boolean) {
        val selected = if (mode == ChatMode.RAG) ragTab else chatTab
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
                    val margin = animation.animatedValue as Int
                    (indicator.layoutParams as FrameLayout.LayoutParams).leftMargin = margin
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

    private fun updateTabColors(mode: ChatMode) {
        val ctx = host.context
        val primary = ContextCompat.getColor(ctx, R.color.deepseek_primary)
        val muted = ContextCompat.getColor(ctx, R.color.deepseek_text_muted)
        if (mode == ChatMode.RAG) {
            styleTab(ragTab, primary, bold = true)
            styleTab(chatTab, muted, bold = false)
        } else {
            styleTab(chatTab, primary, bold = true)
            styleTab(ragTab, muted, bold = false)
        }
    }

    private fun styleTab(tab: TextView, color: Int, bold: Boolean) {
        tab.setTextColor(color)
        tab.setTypeface(tab.typeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
}
