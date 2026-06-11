package com.geoagent.ui.documents

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.geoagent.R
import com.geoagent.data.api.dto.DocumentChunkDto
import com.geoagent.data.api.dto.DocumentImageDto
import com.geoagent.domain.repository.DocumentRepository
import com.geoagent.ui.TransitionHelper
import com.geoagent.ui.motion.MotionUtils
import com.google.android.material.appbar.MaterialToolbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DocumentDetailActivity : AppCompatActivity() {

    @Inject lateinit var documentRepository: DocumentRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_document_detail)

        val documentId = intent.getStringExtra(EXTRA_DOCUMENT_ID).orEmpty()
        val source = intent.getStringExtra(EXTRA_SOURCE).orEmpty()
        val collection = intent.getStringExtra(EXTRA_COLLECTION).orEmpty()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = source
        toolbar.setNavigationOnClickListener {
            MotionUtils.press(toolbar)
            finish()
            TransitionHelper.backward(this)
        }

        val tvSummary = findViewById<TextView>(R.id.tv_content_summary)
        val tvState = findViewById<TextView>(R.id.tv_content_state)
        val layoutChunks = findViewById<LinearLayout>(R.id.layout_chunks)
        val layoutImages = findViewById<LinearLayout>(R.id.layout_images)
        lifecycleScope.launch {
            documentRepository.getDocumentChunks(documentId.ifBlank { source }, collection).fold(
                onSuccess = { chunks -> renderChunks(tvSummary, tvState, layoutChunks, chunks) },
                onFailure = { e ->
                    tvState.text = e.message ?: "加载失败"
                    Toast.makeText(this@DocumentDetailActivity, e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
                }
            )
            documentRepository.getDocumentImages(documentId.ifBlank { source }).fold(
                onSuccess = { images -> renderImages(layoutImages, images) },
                onFailure = { e ->
                    Toast.makeText(this@DocumentDetailActivity, e.message ?: "图片加载失败", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun renderChunks(
        summary: TextView,
        state: TextView,
        container: LinearLayout,
        chunks: List<DocumentChunkDto>
    ) {
        container.removeAllViews()
        val readableChunks = chunks
            .map { it.copy(text = it.text.toReadableText()) }
            .filter { it.text.isNotBlank() }
        if (readableChunks.isEmpty()) {
            state.text = "未找到可阅读的文档内容"
            state.visibility = View.VISIBLE
            summary.visibility = View.GONE
            container.visibility = View.GONE
            return
        }

        val wordCount = readableChunks.sumOf { it.text.length }
        summary.text = "${readableChunks.size} 个知识片段 · 约 $wordCount 字"
        summary.visibility = View.VISIBLE
        state.visibility = View.GONE
        container.visibility = View.VISIBLE

        readableChunks.forEachIndexed { position, chunk ->
            container.addView(createChunkView(chunk, position))
        }
    }

    private fun createChunkView(chunk: DocumentChunkDto, position: Int): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_document_chunk)
            setPadding(dp(16), dp(14), dp(16), dp(15))
        }
        card.addView(createChunkHeader(chunk))
        card.addView(TextView(this).apply {
            text = chunk.text
            setTextColor(getColor(R.color.text_primary))
            textSize = 16f
            setLineSpacing(dp(5).toFloat(), 1.0f)
            includeFontPadding = true
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(12)
        })

        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = if (position == 0) 0 else dp(14)
        }
        return card
    }

    private fun createChunkHeader(chunk: DocumentChunkDto): View {
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(this@DocumentDetailActivity).apply {
                text = "片段 ${chunk.index + 1}"
                setTextColor(getColor(R.color.on_primary_soft))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.bg_document_chunk_badge)
                setPadding(dp(10), dp(5), dp(10), dp(5))
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(TextView(this@DocumentDetailActivity).apply {
                text = "起始字符 ${chunk.charOffset}"
                setTextColor(getColor(R.color.text_muted))
                textSize = 12f
                includeFontPadding = false
            }, LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = dp(10)
            })
        }
    }

    private fun renderImages(container: LinearLayout, images: List<DocumentImageDto>) {
        container.removeAllViews()
        if (images.isEmpty()) {
            container.visibility = View.GONE
            return
        }
        container.visibility = View.VISIBLE
        images.forEachIndexed { index, image ->
            val label = TextView(this).apply {
                text = "文档图片 ${index + 1}"
                setTextColor(getColor(R.color.text_secondary))
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
            }
            container.addView(label, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = if (index == 0) 0 else dp(18)
                bottomMargin = dp(8)
            })

            val imageView = ImageView(this).apply {
                adjustViewBounds = true
                maxHeight = dp(520)
                minimumHeight = dp(120)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundResource(R.drawable.bg_document_image_frame)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setImageURI(android.net.Uri.fromFile(java.io.File(image.path)))
            }
            container.addView(imageView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    private fun String.toReadableText(): String {
        return lines()
            .map { it.trim() }
            .joinToString("\n")
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_DOCUMENT_ID = "document_id"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_COLLECTION = "collection"
    }
}
