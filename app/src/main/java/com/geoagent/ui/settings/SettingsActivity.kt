package com.geoagent.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.geoagent.BuildConfig
import com.geoagent.R
import com.geoagent.domain.repository.AuthRepository
import com.geoagent.ui.TransitionHelper
import com.geoagent.ui.auth.LoginActivity
import com.geoagent.ui.documents.DocumentListActivity
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class SettingsActivity : AppCompatActivity() {

    private val authRepository: AuthRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            finish()
            TransitionHelper.backward(this)
        }

        bindRow(R.id.row_account, getString(R.string.account_security))
        bindRow(R.id.row_appearance, getString(R.string.appearance))
        bindRow(R.id.row_documents, getString(R.string.knowledge_base))
        bindRow(R.id.row_version, getString(R.string.version), BuildConfig.VERSION_NAME, showChevron = false)

        findViewById<View>(R.id.row_account).setOnClickListener {
            startActivity(Intent(this, AccountSecurityActivity::class.java))
            TransitionHelper.forward(this)
        }
        findViewById<View>(R.id.row_appearance).setOnClickListener {
            startActivity(Intent(this, AppearanceActivity::class.java))
            TransitionHelper.forward(this)
        }
        findViewById<View>(R.id.row_documents).setOnClickListener {
            startActivity(Intent(this, DocumentListActivity::class.java))
            TransitionHelper.forward(this)
        }
        findViewById<TextView>(R.id.btn_logout).setOnClickListener {
            lifecycleScope.launch {
                authRepository.logout()
                startActivity(Intent(this@SettingsActivity, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                TransitionHelper.fade(this@SettingsActivity)
                finish()
            }
        }

        loadUser()
    }

    private fun bindRow(
        rowId: Int,
        label: String,
        value: String? = null,
        showChevron: Boolean = true
    ) {
        val row = findViewById<View>(rowId)
        row.findViewById<TextView>(R.id.tv_row_label).text = label
        val valueView = row.findViewById<TextView>(R.id.tv_row_value)
        if (!value.isNullOrBlank()) {
            valueView.text = value
            valueView.visibility = View.VISIBLE
        }
        if (!showChevron) {
            row.findViewById<View>(R.id.iv_chevron)?.visibility = View.GONE
        }
    }

    private fun loadUser() {
        lifecycleScope.launch {
            authRepository.getMe().fold(
                onSuccess = { user ->
                    findViewById<TextView>(R.id.tv_user_name).text =
                        user.full_name?.takeIf { it.isNotBlank() } ?: user.username
                    findViewById<TextView>(R.id.tv_user_email).text = user.email
                },
                onFailure = { e ->
                    Toast.makeText(this@SettingsActivity, e.message, Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}
