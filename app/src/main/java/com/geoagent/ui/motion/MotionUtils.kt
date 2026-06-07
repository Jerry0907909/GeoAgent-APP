package com.geoagent.ui.motion

import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView

object MotionUtils {
    val easeOut: Interpolator = DecelerateInterpolator(1.7f)
    val easeOutSoft: Interpolator = DecelerateInterpolator(1.35f)

    fun animationsEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()

    fun press(view: View) {
        if (!animationsEnabled()) return
        view.animate().cancel()
        view.animate()
            .scaleX(MotionTokens.PRESS_SCALE)
            .scaleY(MotionTokens.PRESS_SCALE)
            .setDuration(MotionTokens.PRESS_MILLIS)
            .setInterpolator(easeOut)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(MotionTokens.PRESS_MILLIS)
                    .setInterpolator(easeOut)
                    .start()
            }
            .start()
    }

    fun show(view: View, duration: Long = MotionTokens.STATE_MILLIS, offsetDp: Float = MotionTokens.CONTENT_OFFSET_DP) {
        if (
            view.visibility == View.VISIBLE &&
            view.alpha == 1f &&
            view.translationY == 0f &&
            view.scaleX == 1f &&
            view.scaleY == 1f
        ) {
            return
        }
        view.animate().cancel()
        if (!animationsEnabled()) {
            view.alpha = 1f
            view.translationY = 0f
            view.scaleX = 1f
            view.scaleY = 1f
            view.visibility = View.VISIBLE
            return
        }
        val offset = view.dp(offsetDp)
        view.alpha = 0f
        view.translationY = offset
        view.scaleX = MotionTokens.SOFT_SCALE
        view.scaleY = MotionTokens.SOFT_SCALE
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(duration)
            .setInterpolator(easeOut)
            .start()
    }

    fun hide(view: View, duration: Long = MotionTokens.EXIT_MILLIS, endVisibility: Int = View.GONE) {
        view.animate().cancel()
        if (!animationsEnabled() || view.visibility != View.VISIBLE) {
            view.visibility = endVisibility
            view.alpha = 1f
            view.translationY = 0f
            view.scaleX = 1f
            view.scaleY = 1f
            return
        }
        view.animate()
            .alpha(0f)
            .translationY(view.dp(MotionTokens.CONTENT_OFFSET_DP) * 0.5f)
            .scaleX(MotionTokens.SOFT_SCALE)
            .scaleY(MotionTokens.SOFT_SCALE)
            .setDuration(duration)
            .setInterpolator(easeOutSoft)
            .withEndAction {
                view.visibility = endVisibility
                view.alpha = 1f
                view.translationY = 0f
                view.scaleX = 1f
                view.scaleY = 1f
            }
            .start()
    }

    fun switchVisibility(showing: View, hiding: View) {
        if (showing === hiding || showing.visibility == View.VISIBLE && hiding.visibility != View.VISIBLE) return
        hide(hiding)
        showing.postDelayed(
            { show(showing, MotionTokens.STATE_MILLIS) },
            if (animationsEnabled()) 70L else 0L
        )
    }

    fun crossfadeText(vararg views: View) {
        views.forEach { view ->
            if (!animationsEnabled()) return@forEach
            view.animate().cancel()
            view.alpha = 0.72f
            view.translationY = view.dp(2f)
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(MotionTokens.MICRO_MILLIS)
                .setInterpolator(easeOut)
                .start()
        }
    }

    fun staggerChildren(parent: ViewGroup, maxChildren: Int = 8) {
        if (!animationsEnabled()) return
        val count = minOf(parent.childCount, maxChildren)
        for (index in 0 until count) {
            val child = parent.getChildAt(index)
            child.animate().cancel()
            child.alpha = 0f
            child.translationY = child.dp(MotionTokens.CONTENT_OFFSET_DP)
            child.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(index * 35L)
                .setDuration(MotionTokens.ENTER_MILLIS)
                .setInterpolator(easeOut)
                .start()
        }
    }

    fun setupRecyclerItemAnimator(recyclerView: RecyclerView) {
        recyclerView.itemAnimator = DefaultItemAnimator().apply {
            addDuration = MotionTokens.ENTER_MILLIS
            removeDuration = MotionTokens.EXIT_MILLIS
            moveDuration = MotionTokens.STATE_MILLIS
            changeDuration = MotionTokens.MICRO_MILLIS
            supportsChangeAnimations = false
        }
    }

    fun View.dp(value: Float): Float = value * resources.displayMetrics.density
}
