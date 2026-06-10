package com.geoagent.ui.chat

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.geoagent.R
import com.geoagent.domain.model.ChatMode
import com.geoagent.ui.motion.MotionTokens
import com.geoagent.ui.motion.MotionUtils

class DeepSeekModeSwitch(
    private val host: View,
    private val indicator: View,
    private val chatTab: TextView,
    private val ragTab: TextView
) {
    private var currentMode = ChatMode.CHAT
    private var laidOut = false

    init {
        host.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (host.width > 0) {
                positionIndicator(currentMode, animate = false)
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
        val inset = (3 * host.resources.displayMetrics.density).toInt()
        val contentWidth = host.width - inset * 2
        if (contentWidth <= 0) return
        val tabWidth = contentWidth / 2
        val lp = indicator.layoutParams as FrameLayout.LayoutParams
        if (lp.width != tabWidth || lp.leftMargin != inset) {
            lp.width = tabWidth
            lp.height = indicator.layoutParams.height
            lp.leftMargin = inset
            lp.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            indicator.layoutParams = lp
        }

        val targetX = if (mode == ChatMode.RAG) tabWidth.toFloat() else 0f
        indicator.animate().cancel()
        if (!animate || !MotionUtils.animationsEnabled()) {
            indicator.translationX = targetX
            return
        }
        indicator.animate()
            .translationX(targetX)
            .setDuration(MotionTokens.STATE_MILLIS)
            .setInterpolator(MotionUtils.easeOut)
            .start()
    }

    private fun updateTabColors(mode: ChatMode) {
        val ctx = host.context
        val primary = ContextCompat.getColor(ctx, R.color.geoagent_blue)
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
        tab.compoundDrawablesRelative.forEach { drawable ->
            drawable?.mutate()?.setTint(color)
        }
    }
}
