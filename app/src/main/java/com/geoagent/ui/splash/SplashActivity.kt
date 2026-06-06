package com.geoagent.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.geoagent.R
import com.geoagent.data.local.UserPrefsDataStore
import com.geoagent.domain.repository.AuthRepository
import com.geoagent.ui.TransitionHelper
import com.geoagent.ui.auth.LoginActivity
import com.geoagent.ui.chat.ChatActivity
import com.geoagent.ui.theme.AppThemeHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class SplashActivity : AppCompatActivity() {

    private val authRepository: AuthRepository by inject()
    private val userPrefsDataStore: UserPrefsDataStore by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        lifecycleScope.launch {
            val theme = userPrefsDataStore.themeMode.first()
            AppThemeHelper.apply(AppThemeHelper.fromStored(theme))
            delay(600)

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
}
