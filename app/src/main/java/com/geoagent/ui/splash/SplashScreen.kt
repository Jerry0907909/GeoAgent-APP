package com.geoagent.ui.splash

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.geoagent.data.local.TokenDataStore
import com.geoagent.navigation.Routes
import com.geoagent.ui.theme.BrandPrimary
import com.geoagent.ui.theme.White
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.koin.java.KoinJavaComponent.get

@Composable
fun SplashScreen(navController: NavHostController) {
    val context = LocalContext.current
    var startAnimation by remember { mutableStateOf(false) }

    // Animate logo scale with spring
    val logoScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.5f,
        animationSpec = spring(stiffness = 200f, dampingRatio = 0.6f),
        label = "logo_scale"
    )

    // Animate text alpha
    val textAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(600, delayMillis = 300),
        label = "text_alpha"
    )

    // Animate subtitle slide
    val subtitleOffset by animateFloatAsState(
        targetValue = if (startAnimation) 0f else 30f,
        animationSpec = tween(700, delayMillis = 400),
        label = "subtitle_offset"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(2000)
        val tokenDataStore = get<TokenDataStore>(TokenDataStore::class.java)
        val token = tokenDataStore.accessToken.first()
        val destination = if (token != null && token.isNotBlank()) Routes.MAIN else Routes.LOGIN
        navController.navigate(destination) {
            popUpTo(Routes.SPLASH) { inclusive = true }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(logoScale)
                    .clip(CircleShape)
                    .background(BrandPrimary),
                contentAlignment = Alignment.Center
            ) {
                Text("G", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = White)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "GeoAgent",
                modifier = Modifier.alpha(textAlpha),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "AI 地质文献智能问答",
                modifier = Modifier
                    .alpha(textAlpha)
                    .run {
                        // Use graphicsLayer for offset, but alpha-based approach for simplicity
                        // since we can't easily access graphicsLayer in a chain
                        this
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
