package com.geoagent.ui.settings

import android.os.Bundle
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.geoagent.R
import com.geoagent.data.local.UserPrefsDataStore
import com.geoagent.ui.TransitionHelper
import com.geoagent.ui.motion.MotionUtils
import com.geoagent.ui.theme.AppThemeHelper
import com.geoagent.ui.theme.AppThemeMode
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class AppearanceActivity : AppCompatActivity() {

    private val userPrefsDataStore: UserPrefsDataStore by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_appearance)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            MotionUtils.press(it)
            finish()
            TransitionHelper.backward(this)
        }

        val rgTheme = findViewById<RadioGroup>(R.id.rg_theme)
        rgTheme.setOnCheckedChangeListener { _, checkedId ->
            MotionUtils.press(rgTheme)
            val mode = when (checkedId) {
                R.id.rb_light -> AppThemeMode.LIGHT
                R.id.rb_dark -> AppThemeMode.DARK
                else -> AppThemeMode.SYSTEM
            }
            lifecycleScope.launch {
                userPrefsDataStore.setThemeMode(mode.name.lowercase())
                AppThemeHelper.apply(mode)
            }
        }

        lifecycleScope.launch {
            when (AppThemeHelper.fromStored(userPrefsDataStore.themeMode.first())) {
                AppThemeMode.LIGHT -> rgTheme.check(R.id.rb_light)
                AppThemeMode.DARK -> rgTheme.check(R.id.rb_dark)
                AppThemeMode.SYSTEM -> rgTheme.check(R.id.rb_system)
            }
        }
    }
}
