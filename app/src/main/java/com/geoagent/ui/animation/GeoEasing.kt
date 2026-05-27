package com.geoagent.ui.animation

import androidx.compose.animation.core.CubicBezierEasing

/**
 * Custom easing curves for GeoAgent animations.
 * Matches the feel of Framer Motion's easing presets.
 */
object GeoEasing {
    /** Standard ease-out for entrances. */
    val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

    /** Standard ease-in for exits. */
    val EaseInCubic = CubicBezierEasing(0.32f, 0f, 0.67f, 0f)

    /** Smooth ease-in-out for bidirectional transitions. */
    val EaseInOutCubic = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

    /** Slight overshoot for bouncy entrances (use sparingly). */
    val EaseOutBack = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
}
