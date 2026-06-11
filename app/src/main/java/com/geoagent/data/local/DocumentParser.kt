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
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class ParsedDocument(
    val text: String,
    val images: List<ParsedDocumentImage> = emptyList()
)

data class ParsedDocumentImage(
    val bytes: ByteArray,
    val mimeType: String
)

object DocumentParser {

    private const val MAX_OCR_ITEMS = 20
    private const val MAX_OCR_BITMAP_PIXELS = 2_000_000
    private const val PDF_RENDER_SCALE = 2

    fun init(context: Context) {
        PDFBoxResourceLoader.init(context)
    }

    suspend fun parse(context: Context, uri: Uri, fileName: String): Result<ParsedDocument> {
        return try {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            val parsed = when (ext) {
                "pdf" -> ParsedDocument(parsePdf(context, uri))
                "docx" -> parseDocx(context, uri)
                "doc" -> ParsedDocument(parseLegacyDoc(context, uri))
                "md", "markdown", "txt", "text" -> ParsedDocument(parseText(context, uri))
                else -> return Result.failure(Exception("不支持的文件格式: .$ext"))
            }
            if (parsed.text.isBlank() && parsed.images.isEmpty()) {
                Result.failure(Exception("未能提取到文本内容，文件可能为扫描图片"))
            } else {
                Result.success(parsed)
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

        val ocrText = parsePdfWithOcr(context, uri)
        return mergeExtractedText(pdfBoxText, ocrText, "图片/扫描页 OCR")
    }

    private suspend fun parsePdfWithOcr(context: Context, uri: Uri): String {
        val sb = StringBuilder()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return ""
            renderer = PdfRenderer(pfd)
            val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            try {
                for (pageIndex in 0 until renderer.pageCount.coerceAtMost(MAX_OCR_ITEMS)) {
                    val page = renderer.openPage(pageIndex)
                    val (bitmapWidth, bitmapHeight) = page.ocrBitmapSize()
                    val bitmap = try {
                        Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                    } catch (_: OutOfMemoryError) {
                        page.close()
                        continue
                    }
                    try {
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val image = InputImage.fromBitmap(bitmap, 0)
                        val result = recognizer.process(image).await()
                        sb.appendLine(result.text)
                    } finally {
                        page.close()
                        bitmap.recycle()
                    }
                }
            } finally {
                recognizer.close()
            }
        } catch (_: Exception) {
        } finally {
            renderer?.close()
            pfd?.close()
        }
        return sb.toString().trim()
    }

    private suspend fun parseDocx(context: Context, uri: Uri): ParsedDocument {
        context.contentResolver.openInputStream(uri)?.use { input ->
            XWPFDocument(input).use { doc ->
                val bodyText = XWPFWordExtractor(doc).text
                val images = extractDocxImages(doc)
                return ParsedDocument(
                    text = bodyText,
                    images = images
                )
            }
        }
        return ParsedDocument("")
    }

    private fun parseLegacyDoc(context: Context, uri: Uri): String {
        context.contentResolver.openInputStream(uri)?.use { input ->
            HWPFDocument(input).use { doc ->
                WordExtractor(doc).use { extractor ->
                    return extractor.text
                }
            }
        }
        return ""
    }

    private fun extractDocxImages(doc: XWPFDocument): List<ParsedDocumentImage> {
        return doc.allPictures.mapNotNull { picture ->
            val bytes = runCatching { picture.data }.getOrNull() ?: return@mapNotNull null
            if (bytes.isEmpty()) return@mapNotNull null
            ParsedDocumentImage(
                bytes = bytes,
                mimeType = picture.pictureType.toImageMimeType()
            )
        }
    }

    private fun parseText(context: Context, uri: Uri): String {
        context.contentResolver.openInputStream(uri)?.use { input ->
            return input.readBytes().decodeTextWithFallback()
        }
        return ""
    }

    private fun mergeExtractedText(primary: String, secondary: String, secondaryTitle: String): String {
        val mainText = primary.trim()
        val imageText = secondary.trim()
        if (mainText.isBlank()) return imageText
        if (imageText.isBlank() || looksDuplicate(mainText, imageText)) return mainText
        return buildString {
            append(mainText)
            appendLine()
            appendLine()
            appendLine("【$secondaryTitle】")
            append(imageText)
        }
    }

    private fun looksDuplicate(primary: String, secondary: String): Boolean {
        val normalizedPrimary = primary.normalizeForCompare()
        val normalizedSecondary = secondary.normalizeForCompare()
        if (normalizedPrimary.isBlank() || normalizedSecondary.length < 40) return false
        val sample = normalizedSecondary.take(240)
        return normalizedPrimary.contains(sample)
    }

    private fun String.normalizeForCompare(): String {
        return replace(Regex("\\s+"), "")
    }

    private fun PdfRenderer.Page.ocrBitmapSize(): Pair<Int, Int> {
        val targetWidth = (width * PDF_RENDER_SCALE).coerceAtLeast(1)
        val targetHeight = (height * PDF_RENDER_SCALE).coerceAtLeast(1)
        val targetPixels = targetWidth.toLong() * targetHeight.toLong()
        if (targetPixels <= MAX_OCR_BITMAP_PIXELS) return targetWidth to targetHeight

        val scale = sqrt(MAX_OCR_BITMAP_PIXELS.toDouble() / targetPixels.toDouble())
        val bitmapWidth = (targetWidth * scale).roundToInt().coerceAtLeast(1)
        val bitmapHeight = (targetHeight * scale).roundToInt().coerceAtLeast(1)
        return bitmapWidth to bitmapHeight
    }

    private fun ByteArray.decodeTextWithFallback(): String {
        return try {
            decodeStrict(Charsets.UTF_8)
        } catch (_: CharacterCodingException) {
            decodeStrict(Charset.forName("GB18030"))
        }
    }

    private fun ByteArray.decodeStrict(charset: Charset): String {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return decoder.decode(ByteBuffer.wrap(this)).toString()
    }

    private fun Int.toImageMimeType(): String {
        return when (this) {
            XWPFDocument.PICTURE_TYPE_EMF -> "image/x-emf"
            XWPFDocument.PICTURE_TYPE_WMF -> "image/x-wmf"
            XWPFDocument.PICTURE_TYPE_PICT -> "image/x-pict"
            XWPFDocument.PICTURE_TYPE_JPEG -> "image/jpeg"
            XWPFDocument.PICTURE_TYPE_PNG -> "image/png"
            XWPFDocument.PICTURE_TYPE_DIB -> "image/bmp"
            XWPFDocument.PICTURE_TYPE_GIF -> "image/gif"
            XWPFDocument.PICTURE_TYPE_TIFF -> "image/tiff"
            XWPFDocument.PICTURE_TYPE_EPS -> "application/postscript"
            XWPFDocument.PICTURE_TYPE_BMP -> "image/bmp"
            XWPFDocument.PICTURE_TYPE_WPG -> "image/x-wpg"
            else -> "application/octet-stream"
        }
    }
}
