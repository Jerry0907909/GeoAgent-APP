package com.geoagent.ui.documents

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Window
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.geoagent.R
import com.geoagent.data.api.dto.DocumentUploadProgress
import com.geoagent.data.api.dto.DocumentDto
import com.geoagent.domain.repository.DocumentRepository
import com.geoagent.ui.TransitionHelper
import com.geoagent.ui.motion.MotionUtils
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DocumentListActivity : AppCompatActivity() {

    @Inject lateinit var documentRepository: DocumentRepository
    private lateinit var adapter: DocumentAdapter
    private lateinit var uploadFab: FloatingActionButton
    private var isUploading = false
    private var uploadDialog: AlertDialog? = null

    private val uploadPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        if (isUploading) return@registerForActivityResult
        setUploading(true)
        showUploadProgressDialog()
        lifecycleScope.launch {
            documentRepository.uploadFileFromUriWithProgress(this@DocumentListActivity, uri)
                .catch { e ->
                    Toast.makeText(this@DocumentListActivity, e.message ?: "上传失败", Toast.LENGTH_SHORT).show()
                }
                .onCompletion {
                    dismissUploadProgressDialog()
                    loadDocuments()
                    setUploading(false)
                }
                .collect { progress ->
                    updateUploadProgress(progress)
                    if (progress.percent >= 100) {
                        Toast.makeText(this@DocumentListActivity, progress.detail.ifBlank { "上传成功" }, Toast.LENGTH_SHORT).show()
                    }
                }
        }.invokeOnCompletion {
            if (it != null) {
                dismissUploadProgressDialog()
                setUploading(false)
            }
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
        uploadFab = findViewById(R.id.fab_upload)

        adapter = DocumentAdapter(
            onOpen = { doc -> openDocument(doc) },
            onDelete = { doc -> confirmDelete(doc) },
            onRename = { doc -> showRenameDialog(doc) }
        )
        rv.layoutManager = LinearLayoutManager(this)
        MotionUtils.setupRecyclerItemAnimator(rv)
        rv.adapter = adapter

        swipe.setOnRefreshListener { loadDocuments() }
        uploadFab.setOnClickListener {
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

    private fun setUploading(uploading: Boolean) {
        isUploading = uploading
        uploadFab.isEnabled = !uploading
        uploadFab.alpha = if (uploading) 0.55f else 1f
        if (uploading) {
            Toast.makeText(this, "正在解析文档并生成向量...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showUploadProgressDialog() {
        val content = LayoutInflater.from(this).inflate(R.layout.dialog_upload_progress, null)
        uploadDialog = AlertDialog.Builder(this)
            .setView(content)
            .setCancelable(false)
            .create()
        uploadDialog?.setOnShowListener { uploadDialog?.window?.applyDialogWindow() }
        uploadDialog?.show()
        uploadDialog?.window?.applyDialogWindow()
        updateUploadProgress(DocumentUploadProgress(0, "准备上传", "正在初始化解析任务"))
    }

    private fun updateUploadProgress(progress: DocumentUploadProgress) {
        val dialogView = uploadDialog?.window?.decorView ?: return
        dialogView.findViewById<TextView>(R.id.tv_progress_percent)?.text = "${progress.percent.coerceIn(0, 100)}%"
        dialogView.findViewById<TextView>(R.id.tv_progress_stage)?.text = progress.stage
        dialogView.findViewById<TextView>(R.id.tv_progress_detail)?.text = progress.detail
    }

    private fun dismissUploadProgressDialog() {
        uploadDialog?.dismiss()
        uploadDialog = null
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
                .putExtra(DocumentDetailActivity.EXTRA_DOCUMENT_ID, doc.id)
                .putExtra(DocumentDetailActivity.EXTRA_SOURCE, doc.source)
                .putExtra(DocumentDetailActivity.EXTRA_COLLECTION, doc.collection)
        )
        TransitionHelper.forward(this)
    }

    private fun confirmDelete(doc: DocumentDto) {
        val content = LayoutInflater.from(this).inflate(R.layout.dialog_settings_confirm, null)
        content.findViewById<TextView>(R.id.tv_dialog_title).text = "删除文档?"
        content.findViewById<TextView>(R.id.tv_dialog_body).text =
            "将从知识库中移除“${doc.source}”及其向量索引。此操作无法撤销。"
        val dialog = AlertDialog.Builder(this).setView(content).create()
        content.findViewById<TextView>(R.id.btn_cancel).setOnClickListener {
            MotionUtils.press(it)
            dialog.dismiss()
        }
        content.findViewById<TextView>(R.id.btn_confirm).apply {
            text = getString(R.string.delete)
            setOnClickListener {
                MotionUtils.press(it)
                dialog.dismiss()
                deleteDocument(doc)
            }
        }
        dialog.setOnShowListener { dialog.window?.applyDialogWindow() }
        dialog.show()
        dialog.window?.applyDialogWindow()
    }

    private fun showRenameDialog(doc: DocumentDto) {
        val content = LayoutInflater.from(this).inflate(R.layout.dialog_document_rename, null)
        val input = content.findViewById<EditText>(R.id.et_document_name)
        content.findViewById<TextView>(R.id.tv_dialog_title).text = getString(R.string.rename_document)
        content.findViewById<TextView>(R.id.tv_dialog_subtitle).text = "输入新的文件名，知识库索引和图片内容会保留。"
        input.apply {
            setText(doc.source)
            setSelection(text.length)
        }
        val dialog = AlertDialog.Builder(this).setView(content).create()
        content.findViewById<TextView>(R.id.btn_cancel).setOnClickListener {
            MotionUtils.press(it)
            dialog.dismiss()
        }
        content.findViewById<TextView>(R.id.btn_save).setOnClickListener {
            MotionUtils.press(it)
            val newName = input.text?.toString()?.trim().orEmpty()
            if (newName.isBlank()) {
                Toast.makeText(this, "文件名不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newName == doc.source) {
                dialog.dismiss()
                return@setOnClickListener
            }
            dialog.dismiss()
            renameDocument(doc, newName)
        }
        dialog.setOnShowListener { dialog.window?.applyDialogWindow() }
        dialog.show()
        dialog.window?.applyDialogWindow()
    }

    private fun renameDocument(doc: DocumentDto, newName: String) {
        lifecycleScope.launch {
            documentRepository.renameDocument(doc.id, newName).fold(
                onSuccess = {
                    Toast.makeText(this@DocumentListActivity, "已重命名", Toast.LENGTH_SHORT).show()
                    loadDocuments()
                },
                onFailure = { e -> Toast.makeText(this@DocumentListActivity, e.message ?: "重命名失败", Toast.LENGTH_SHORT).show() }
            )
        }
    }

    private fun deleteDocument(doc: DocumentDto) {
        lifecycleScope.launch {
            documentRepository.deleteDocument(doc.id).fold(
                onSuccess = { loadDocuments() },
                onFailure = { e -> Toast.makeText(this@DocumentListActivity, e.message, Toast.LENGTH_SHORT).show() }
            )
        }
    }

    private fun Window.applyDialogWindow() {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setDimAmount(0.38f)
    }
}
