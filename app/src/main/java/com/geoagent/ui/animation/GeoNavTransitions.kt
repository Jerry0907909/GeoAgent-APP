package com.geoagent.ui.animation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import com.geoagent.navigation.Routes

/**
 * Navigation transition specs for GeoAgent.
 * Produces iOS-style slide-in/slide-out page transitions.
 */
object GeoNavTransitions {

    private const val SLIDE_DURATION = 300
    private const val FADE_DURATION = 200

    /** Route order for forward/back detection */
    private val routeHierarchy = listOf(
        Routes.SPLASH,
        Routes.LOGIN,
        Routes.REGISTER,
        Routes.MAIN
    )

    private fun isForward(initial: String, target: String): Boolean {
        val initialIndex = routeHierarchy.indexOf(initial)
        val targetIndex = routeHierarchy.indexOf(target)
        return if (initialIndex >= 0 && targetIndex >= 0) {
            targetIndex > initialIndex
        } else {
            true
        }
    }

    /** Global default enter transition for NavHost. */
    val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> androidx.compose.animation.EnterTransition = {
        val initialRoute = initialState.destination.route.orEmpty()
        val targetRoute = targetState.destination.route.orEmpty()
        val isForwardNav = isForward(initialRoute, targetRoute)
        val slideDirection = if (isForwardNav) 1 else -1
        slideInHorizontally(
            animationSpec = tween(SLIDE_DURATION, easing = GeoEasing.EaseOutCubic),
            initialOffsetX = { it * slideDirection }
        ) + fadeIn(tween(FADE_DURATION))
    }

    /** Global default exit transition for NavHost. */
    val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> androidx.compose.animation.ExitTransition = {
        val initialRoute = initialState.destination.route.orEmpty()
        val targetRoute = targetState.destination.route.orEmpty()
        val isForwardNav = isForward(initialRoute, targetRoute)
        val slideDirection = if (isForwardNav) -1 else 1
        slideOutHorizontally(
            animationSpec = tween(SLIDE_DURATION, easing = GeoEasing.EaseInCubic),
            targetOffsetX = { it * slideDirection }
        ) + fadeOut(tween(FADE_DURATION))
    }

    /** Pop enter: sliding back into view. */
    val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> androidx.compose.animation.EnterTransition = {
        slideInHorizontally(
            animationSpec = tween(SLIDE_DURATION, easing = GeoEasing.EaseOutCubic),
            initialOffsetX = { -it }
        ) + fadeIn(tween(FADE_DURATION))
    }

    /** Pop exit: sliding away when going back. */
    val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> androidx.compose.animation.ExitTransition = {
        slideOutHorizontally(
            animationSpec = tween(SLIDE_DURATION, easing = GeoEasing.EaseInCubic),
            targetOffsetX = { it }
        ) + fadeOut(tween(FADE_DURATION))
    }
}
