package com.geoagent.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.geoagent.ui.auth.LoginScreen
import com.geoagent.ui.auth.RegisterScreen
import com.geoagent.ui.main.MainScreen
import com.geoagent.ui.splash.SplashScreen
import com.geoagent.ui.animation.GeoEasing

private const val SLIDE_DURATION = 300
private const val FADE_DURATION = 200

@Composable
fun GeoNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        enterTransition = {
            slideInHorizontally(
                animationSpec = tween(SLIDE_DURATION, easing = GeoEasing.EaseOutCubic),
                initialOffsetX = { it }
            ) + fadeIn(tween(FADE_DURATION))
        },
        exitTransition = {
            slideOutHorizontally(
                animationSpec = tween(SLIDE_DURATION, easing = GeoEasing.EaseInCubic),
                targetOffsetX = { -it }
            ) + fadeOut(tween(FADE_DURATION))
        },
        popEnterTransition = {
            slideInHorizontally(
                animationSpec = tween(SLIDE_DURATION, easing = GeoEasing.EaseOutCubic),
                initialOffsetX = { -it }
            ) + fadeIn(tween(FADE_DURATION))
        },
        popExitTransition = {
            slideOutHorizontally(
                animationSpec = tween(SLIDE_DURATION, easing = GeoEasing.EaseInCubic),
                targetOffsetX = { it }
            ) + fadeOut(tween(FADE_DURATION))
        }
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(navController)
        }
        composable(Routes.LOGIN) {
            LoginScreen(navController)
        }
        composable(Routes.REGISTER) {
            RegisterScreen(navController)
        }
        composable(Routes.MAIN) {
            MainScreen(navController)
        }
    }
}
