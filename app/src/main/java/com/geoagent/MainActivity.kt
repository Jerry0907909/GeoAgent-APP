package com.geoagent

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.geoagent.ui.chat.WaveBarsLoadingView

class MainActivity : AppCompatActivity() {

    private lateinit var waveLoading: WaveBarsLoadingView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        waveLoading = findViewById(R.id.wave_loading)
        waveLoading.startAnimation()
    }

    override fun onDestroy() {
        waveLoading.stopAnimation()
        super.onDestroy()
    }
}
