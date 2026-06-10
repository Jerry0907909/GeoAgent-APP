package com.geoagent.ui.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.geoagent.R
import com.geoagent.ui.TransitionHelper
import com.google.android.material.appbar.MaterialToolbar

class LegalDocumentActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_legal_document)

        val type = intent.getStringExtra(EXTRA_TYPE) ?: TYPE_SERVICE
        val titleRes = if (type == TYPE_PRIVACY) {
            R.string.legal_privacy_policy_title
        } else {
            R.string.legal_service_agreement_title
        }
        val contentRes = if (type == TYPE_PRIVACY) {
            R.string.legal_privacy_policy_content
        } else {
            R.string.legal_service_agreement_content
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = getString(titleRes)
        toolbar.setNavigationOnClickListener {
            finish()
            TransitionHelper.backward(this)
        }

        findViewById<TextView>(R.id.tv_updated_at).text = getString(R.string.legal_updated_at)
        findViewById<TextView>(R.id.tv_content).text = getString(contentRes)
    }

    companion object {
        private const val EXTRA_TYPE = "legal_type"
        const val TYPE_SERVICE = "service"
        const val TYPE_PRIVACY = "privacy"

        fun intent(context: Context, type: String): Intent {
            return Intent(context, LegalDocumentActivity::class.java).putExtra(EXTRA_TYPE, type)
        }
    }
}
