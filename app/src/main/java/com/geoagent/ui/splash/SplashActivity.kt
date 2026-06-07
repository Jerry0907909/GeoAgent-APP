package com.geoagent.ui.splash

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.geoagent.R
import com.geoagent.data.local.UserPrefsDataStore
import com.geoagent.domain.repository.AuthRepository
import com.geoagent.ui.TransitionHelper
import com.geoagent.ui.auth.LoginActivity
import com.geoagent.ui.chat.ChatActivity
import com.geoagent.ui.motion.MotionUtils
import com.geoagent.ui.theme.AppThemeHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class SplashActivity : AppCompatActivity() {

    private val authRepository: AuthRepository by inject()
    private val userPrefsDataStore: UserPrefsDataStore by inject()
    private lateinit var logoLoading: LogoPulseLoadingView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        logoLoading = findViewById(R.id.logo_loading)
        startLogoAnimation()

        lifecycleScope.launch {
            val theme = userPrefsDataStore.themeMode.first()
            AppThemeHelper.apply(AppThemeHelper.fromStored(theme))

            val target = if (authRepository.isLoggedIn()) {
                Intent(this@SplashActivity, ChatActivity::class.java)
            } else {
                Intent(this@SplashActivity, LoginActivity::class.java)
            }
            startActivity(target)
            TransitionHelper.fade(this@SplashActivity)
            finish()
        }
    }

    override fun onDestroy() {
        logoLoading.stopAnimation()
        super.onDestroy()
    }

    private fun startLogoAnimation() {
        val logo = findViewById<ImageView>(R.id.iv_geoagent_logo)
        if (!MotionUtils.animationsEnabled()) return
        logo.animate()
            .scaleX(1.04f)
            .scaleY(1.04f)
            .alpha(0.92f)
            .setDuration(900L)
            .setInterpolator(MotionUtils.easeOutSoft)
            .withEndAction {
                logo.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(900L)
                    .setInterpolator(MotionUtils.easeOutSoft)
                    .withEndAction { if (!isFinishing) startLogoAnimation() }
                    .start()
            }
            .start()
        logoLoading.startAnimation()
    }
}
