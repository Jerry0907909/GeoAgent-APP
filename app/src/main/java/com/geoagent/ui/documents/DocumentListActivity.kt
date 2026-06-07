package com.geoagent.ui.documents

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.geoagent.R
import com.geoagent.data.api.dto.DocumentDto
import com.geoagent.domain.repository.DocumentRepository
import com.geoagent.ui.TransitionHelper
import com.geoagent.ui.motion.MotionUtils
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class DocumentListActivity : AppCompatActivity() {

    private val documentRepository: DocumentRepository by inject()
    private lateinit var adapter: DocumentAdapter

    private val uploadPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            documentRepository.uploadFileFromUri(this@DocumentListActivity, uri).fold(
                onSuccess = {
                    Toast.makeText(this@DocumentListActivity, "上传成功", Toast.LENGTH_SHORT).show()
                    loadDocuments()
                },
                onFailure = { e -> Toast.makeText(this@DocumentListActivity, e.message ?: "上传失败", Toast.LENGTH_SHORT).show() }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_document_list)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            MotionUtils.press(it)
            finish()
            TransitionHelper.backward(this)
        }
        val swipe = findViewById<SwipeRefreshLayout>(R.id.swipe_refresh)
        val rv = findViewById<RecyclerView>(R.id.rv_documents)

        adapter = DocumentAdapter(
            onOpen = { doc -> openDocument(doc) },
            onDelete = { doc -> confirmDelete(doc) }
        )
        rv.layoutManager = LinearLayoutManager(this)
        MotionUtils.setupRecyclerItemAnimator(rv)
        rv.adapter = adapter

        swipe.setOnRefreshListener { loadDocuments() }
        findViewById<FloatingActionButton>(R.id.fab_upload).setOnClickListener {
            MotionUtils.press(it)
            uploadPicker.launch(arrayOf(
                "application/pdf",
                "text/plain",
                "text/markdown",
                "text/x-markdown",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/msword"
            ))
        }

        loadDocuments()
    }

    private fun loadDocuments() {
        val swipe = findViewById<SwipeRefreshLayout>(R.id.swipe_refresh)
        swipe.isRefreshing = true
        lifecycleScope.launch {
            documentRepository.getDocuments().fold(
                onSuccess = { adapter.submit(it) },
                onFailure = { e -> Toast.makeText(this@DocumentListActivity, e.message ?: "加载失败", Toast.LENGTH_SHORT).show() }
            )
            swipe.isRefreshing = false
        }
    }

    private fun openDocument(doc: DocumentDto) {
        startActivity(
            Intent(this, DocumentDetailActivity::class.java)
                .putExtra(DocumentDetailActivity.EXTRA_SOURCE, doc.source)
                .putExtra(DocumentDetailActivity.EXTRA_COLLECTION, doc.collection)
        )
        TransitionHelper.forward(this)
    }

    private fun confirmDelete(doc: DocumentDto) {
        AlertDialog.Builder(this)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.ok) { _, _ ->
                lifecycleScope.launch {
                    documentRepository.deleteDocument(doc.id).fold(
                        onSuccess = { loadDocuments() },
                        onFailure = { e -> Toast.makeText(this@DocumentListActivity, e.message, Toast.LENGTH_SHORT).show() }
                    )
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
