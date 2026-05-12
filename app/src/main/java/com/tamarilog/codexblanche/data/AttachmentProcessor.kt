package com.tamarilog.codexblanche.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.util.Locale

/**
 * 添付ファイルの荷ほどき係。読めるものは読み、重すぎる荷物はほどよく畳む。
 */
object AttachmentProcessor {
    const val MAX_SHARED_FILES = 10
    private const val MAX_FILE_TEXT_CHARS_PER_FILE = 12000
    private const val MAX_FILE_TOTAL_TEXT_CHARS = 36000
    private const val MAX_FILE_TEXT_BYTES = 256 * 1024
    private const val MAX_FILE_CHUNK_CHARS = 4000
    private const val MAX_PDF_PAGES = 40

    private val textExtensions = setOf(
        "txt", "md", "markdown", "json", "csv", "tsv", "yaml", "yml", "xml", "html", "css",
        "js", "mjs", "cjs", "ts", "tsx", "jsx", "py", "java", "go", "rs", "c", "cpp", "h", "hpp",
        "sql", "log",
    )

    data class ExtractedFile(
        val name: String,
        val mimeType: String,
        val size: Long,
        val content: String = "",
        val chunks: List<String> = emptyList(),
        val contentAvailable: Boolean = false,
        val contentTruncated: Boolean = false,
        val contentReason: String = "",
        val source: String = "",
    )

    fun ensureCapacity(currentCount: Int, newCount: Int): Int {
        val available = (MAX_SHARED_FILES - currentCount).coerceAtLeast(0)
        return minOf(newCount, available)
    }

    suspend fun createFileAttachments(
        context: Context,
        uris: List<Uri>,
    ): List<ExtractedFile> = withContext(Dispatchers.IO) {
        val out = mutableListOf<ExtractedFile>()
        var remainingChars = MAX_FILE_TOTAL_TEXT_CHARS
        for (uri in uris) {
            val meta = queryMeta(context, uri)
            val ext = extension(meta.name)
            var row = ExtractedFile(name = meta.name, mimeType = meta.mime, size = meta.size)
            if (!isLikelyTextFile(meta.mime, meta.name) && ext != "pdf" && ext != "docx") {
                row = row.copy(contentReason = "unsupported_type")
                out.add(row)
                continue
            }
            if (meta.size > MAX_FILE_TEXT_BYTES) {
                row = row.copy(contentReason = "too_large")
                out.add(row)
                continue
            }
            if (remainingChars <= 0) {
                row = row.copy(contentReason = "budget_exceeded")
                out.add(row)
                continue
            }
            try {
                val (text, truncatedPages, sourceLabel) = extractText(context, uri, ext)
                val normalized = sanitizeFileText(text)
                val budget = minOf(MAX_FILE_TEXT_CHARS_PER_FILE, remainingChars)
                val trimmed = normalized.take(budget)
                val chunks = chunkTextForPrompt(trimmed)
                val avail = trimmed.isNotEmpty()
                row = ExtractedFile(
                    name = meta.name,
                    mimeType = meta.mime,
                    size = meta.size,
                    content = trimmed,
                    chunks = chunks,
                    contentAvailable = avail,
                    contentTruncated = normalized.length > trimmed.length || truncatedPages,
                    contentReason = if (avail) "" else "empty",
                    source = sourceLabel,
                )
                if (avail) remainingChars -= trimmed.length
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                row = row.copy(
                    contentReason = when (msg) {
                        "pdf_parser_unavailable" -> msg
                        "docx_parser_unavailable" -> msg
                        else -> "read_error"
                    },
                )
            }
            out.add(row)
        }
        out
    }

    fun buildFileContentNote(files: List<ExtractedFile>): String {
        if (files.isEmpty()) return ""
        val blocks = files.mapIndexed { index, attachment ->
            val title = "### file_${index + 1}: ${attachment.name} (${attachment.mimeType})"
            if (attachment.contentAvailable) {
                val chunked =
                    if (attachment.chunks.isNotEmpty()) {
                        attachment.chunks.mapIndexed { ci, chunk ->
                            "#### part ${ci + 1}/${attachment.chunks.size}\n```\n$chunk\n```"
                        }.joinToString("\n")
                    } else {
                        "```\n${attachment.content}\n```"
                    }
                val suffix = if (attachment.contentTruncated) "\n[...truncated for web performance...]" else ""
                "$title\n$chunked$suffix"
            } else {
                val reason = formatFileContentReason(attachment.contentReason)
                "$title\n（本文を添付できませんでした: $reason）"
            }
        }
        return "[添付ファイル内容]\n${blocks.joinToString("\n\n")}"
    }

    private data class Meta(val name: String, val mime: String, val size: Long)

    private fun queryMeta(context: Context, uri: Uri): Meta {
        var name = uri.lastPathSegment ?: "file"
        var size = 0L
        var mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0) name = c.getString(nameIdx) ?: name
                val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx)
            }
        }
        return Meta(name = name, mime = mime, size = size)
    }

    private fun extension(name: String): String {
        val i = name.lastIndexOf('.')
        if (i < 0 || i == name.lastIndex) return ""
        return name.substring(i + 1).lowercase(Locale.ROOT)
    }

    private fun isLikelyTextFile(mime: String, name: String): Boolean {
        val m = mime.lowercase(Locale.ROOT)
        if (m.startsWith("text/")) return true
        if (m.contains("json") || m.contains("xml") || m.contains("yaml") ||
            m.contains("csv") || m.contains("javascript") || m.contains("typescript")
        ) {
            return true
        }
        val ext = extension(name)
        return ext in textExtensions
    }

    private fun sanitizeFileText(text: String): String =
        text.replace("\r\n", "\n").replace("\u0000", "")

    private fun chunkTextForPrompt(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val chunks = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            chunks.add(text.substring(i, minOf(i + MAX_FILE_CHUNK_CHARS, text.length)))
            i += MAX_FILE_CHUNK_CHARS
        }
        return chunks
    }

    private fun formatFileContentReason(reason: String): String = when (reason) {
        "unsupported_type" -> "非対応形式"
        "too_large" -> "サイズ超過"
        "budget_exceeded" -> "合計上限超過"
        "pdf_parser_unavailable" -> "PDF抽出ライブラリ未読込"
        "docx_parser_unavailable" -> "DOCX抽出ライブラリ未読込"
        "read_error" -> "読み込み失敗"
        "empty" -> "空ファイル"
        else -> "内容なし"
    }

    private fun extractText(context: Context, uri: Uri, ext: String): Triple<String, Boolean, String> {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ext) {
                "pdf" -> {
                    PDDocument.load(stream).use { doc ->
                        val stripper = PDFTextStripper()
                        stripper.startPage = 1
                        val last = minOf(doc.numberOfPages, MAX_PDF_PAGES)
                        stripper.endPage = last
                        val full = stripper.getText(doc)
                        val truncatedByPages = doc.numberOfPages > MAX_PDF_PAGES
                        return Triple(full, truncatedByPages, "pdf")
                    }
                }
                "docx" -> {
                    XWPFDocument(stream).use { document ->
                        val text = document.paragraphs.joinToString("\n") { it.text }
                        return Triple(text, false, "docx")
                    }
                }
                else -> {
                    val raw = stream.readBytes().decodeToString()
                    return Triple(raw, false, "plain")
                }
            }
        } ?: throw IllegalStateException("read_error")
        throw IllegalStateException("read_error")
    }

    suspend fun readUriAsDataUrl(context: Context, uri: Uri): Pair<String, String> =
        withContext(Dispatchers.IO) {
            val mime = resolveMimeType(context, uri)
            val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            val dataUrl = "data:$mime;base64,$b64"
            dataUrl to mime
        }

    private fun resolveMimeType(context: Context, uri: Uri): String {
        val direct = context.contentResolver.getType(uri).orEmpty().lowercase(Locale.ROOT)
        if (direct.startsWith("image/")) return direct
        val extFromUri = MimeTypeMap.getFileExtensionFromUrl(uri.toString()).orEmpty().lowercase(Locale.ROOT)
        val guessedFromUri = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extFromUri).orEmpty()
        if (guessedFromUri.startsWith("image/")) return guessedFromUri
        var displayName = ""
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) displayName = c.getString(idx).orEmpty()
            }
        }
        val extFromName = extension(displayName)
        val guessedFromName = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extFromName).orEmpty()
        if (guessedFromName.startsWith("image/")) return guessedFromName
        return if (direct.isNotBlank() && direct != "application/octet-stream") direct else "image/png"
    }
}
