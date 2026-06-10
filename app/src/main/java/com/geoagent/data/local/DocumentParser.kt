package com.geoagent.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.tasks.await
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.BufferedReader
import java.io.InputStreamReader

object DocumentParser {

    private var mlKitReady = false

    fun init(context: Context) {
        PDFBoxResourceLoader.init(context)
    }

    suspend fun parse(context: Context, uri: Uri, fileName: String): Result<String> {
        return try {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            val text = when (ext) {
                "pdf" -> parsePdf(context, uri)
                "docx", "doc" -> parseDocx(context, uri)
                "md", "markdown", "txt", "text" -> parseText(context, uri)
                else -> return Result.failure(Exception("不支持的文件格式: .$ext"))
            }
            if (text.isBlank()) {
                Result.failure(Exception("未能提取到文本内容，文件可能为扫描图片"))
            } else {
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(Exception("文档解析失败: ${e.message}"))
        }
    }

    private suspend fun parsePdf(context: Context, uri: Uri): String {
        // Try PDFBox first
        val pdfBoxText = context.contentResolver.openInputStream(uri)?.use { input ->
            runCatching {
                PDDocument.load(input).use { doc ->
                    val stripper = PDFTextStripper()
                    stripper.sortByPosition = true
                    stripper.getText(doc)
                }
            }.getOrDefault("")
        } ?: ""

        // If PDFBox got meaningful text, return it
        if (pdfBoxText.trim().length > 100) return pdfBoxText

        // Otherwise, use ML Kit OCR
        return parsePdfWithOcr(context, uri)
    }

    private suspend fun parsePdfWithOcr(context: Context, uri: Uri): String {
        val sb = StringBuilder()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return ""
            renderer = PdfRenderer(pfd)
            val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

            for (pageIndex in 0 until renderer.pageCount.coerceAtMost(20)) {
                val page = renderer.openPage(pageIndex)
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                // Render at 2x for better OCR quality
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, page.width * 2, page.height * 2, true)
                page.render(scaledBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val image = InputImage.fromBitmap(scaledBitmap, 0)
                val result = recognizer.process(image).await()
                sb.appendLine(result.text)

                scaledBitmap.recycle()
                bitmap.recycle()
            }
        } catch (_: Exception) {
        } finally {
            renderer?.close()
            pfd?.close()
        }
        return sb.toString().trim()
    }

    private fun parseDocx(context: Context, uri: Uri): String {
        context.contentResolver.openInputStream(uri)?.use { input ->
            XWPFDocument(input).use { doc ->
                return XWPFWordExtractor(doc).text
            }
        }
        return ""
    }

    private fun parseText(context: Context, uri: Uri): String {
        val sb = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    sb.appendLine(line)
                    line = reader.readLine()
                }
            }
        }
        return sb.toString()
    }
}
