package com.geoagent.ui.documents

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.geoagent.R
import com.geoagent.domain.repository.DocumentRepository
import com.geoagent.ui.TransitionHelper
import com.geoagent.ui.motion.MotionUtils
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class DocumentDetailActivity : AppCompatActivity() {

    private val documentRepository: DocumentRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_document_detail)

        val source = intent.getStringExtra(EXTRA_SOURCE).orEmpty()
        val collection = intent.getStringExtra(EXTRA_COLLECTION).orEmpty()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = source
        toolbar.setNavigationOnClickListener {
            MotionUtils.press(toolbar)
            finish()
            TransitionHelper.backward(this)
        }

        val tvContent = findViewById<TextView>(R.id.tv_content)
        lifecycleScope.launch {
            documentRepository.getDocumentContent(source, collection).fold(
                onSuccess = { tvContent.text = it },
                onFailure = { e ->
                    Toast.makeText(this@DocumentDetailActivity, e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    companion object {
        const val EXTRA_SOURCE = "source"
        const val EXTRA_COLLECTION = "collection"
    }
}
