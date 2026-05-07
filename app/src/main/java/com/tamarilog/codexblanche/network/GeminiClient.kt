package com.tamarilog.codexblanche.network

import com.tamarilog.codexblanche.data.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Web版 [assets/js/providers.js] の `callGeminiAPI` に相当（ストリーム優先、失敗時は非ストリームへフォールバック）
 */
class GeminiClient(
    private val client: OkHttpClient = defaultClient,
) {
    private val jsonParser = Json { ignoreUnknownKeys = true }

    suspend fun generate(
        messages: List<ChatMessage>,
        apiKey: String,
        model: String,
        systemInstruction: String?,
        temperature: Double?,
        maxTokens: Int?,
        allowSearch: Boolean,
        onChunk: (delta: String, accumulated: String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val body = buildRequestBody(
            messages = messages,
            systemInstruction = systemInstruction,
            temperature = temperature,
            maxTokens = maxTokens,
            allowSearch = allowSearch,
        )
        val streamUrl =
            "https://generativelanguage.googleapis.com/v1beta/models/" +
                "${model}:streamGenerateContent?alt=sse&key=${java.net.URLEncoder.encode(apiKey, "UTF-8")}"
        val nonStreamUrl =
            "https://generativelanguage.googleapis.com/v1beta/models/" +
                "${model}:generateContent?key=${java.net.URLEncoder.encode(apiKey, "UTF-8")}"

        try {
            streamFromSse(streamUrl, body, onChunk)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            nonStream(nonStreamUrl, body)
        }
    }

    private fun buildRequestBody(
        messages: List<ChatMessage>,
        systemInstruction: String?,
        temperature: Double?,
        maxTokens: Int?,
        allowSearch: Boolean,
    ): String {
        val contents = normalizeGeminiContents(messages)
        if (contents.isEmpty()) {
            throw IllegalArgumentException(
                "Gemini向けの履歴が不正です（最初の発話はユーザーである必要があります）",
            )
        }
        val root = buildJsonObject {
            put("contents", contents)
            put(
                "safetySettings",
                buildJsonArray {
                    add(buildJsonObject { put("category", "HARM_CATEGORY_HARASSMENT"); put("threshold", "BLOCK_NONE") })
                    add(buildJsonObject { put("category", "HARM_CATEGORY_HATE_SPEECH"); put("threshold", "BLOCK_NONE") })
                    add(buildJsonObject { put("category", "HARM_CATEGORY_SEXUALLY_EXPLICIT"); put("threshold", "BLOCK_NONE") })
                    add(buildJsonObject { put("category", "HARM_CATEGORY_DANGEROUS_CONTENT"); put("threshold", "BLOCK_NONE") })
                },
            )
            if (temperature != null || maxTokens != null) {
                put(
                    "generationConfig",
                    buildJsonObject {
                        temperature?.let { put("temperature", it) }
                        maxTokens?.let { put("maxOutputTokens", it) }
                    },
                )
            }
            if (allowSearch) {
                put("tools", buildJsonArray { add(buildJsonObject { put("google_search", buildJsonObject {}) }) })
            }
            val sys = systemInstruction?.trim().orEmpty()
            if (sys.isNotEmpty()) {
                put(
                    "system_instruction",
                    buildJsonObject {
                        put("parts", buildJsonArray { add(buildJsonObject { put("text", sys) }) })
                    },
                )
            }
        }
        return root.toString()
    }

    /**
     * Web版 `normalizeGeminiContents` の移植（テキスト＋画像インライン／ファイル名メタ）
     */
    private fun normalizeGeminiContents(messages: List<ChatMessage>): JsonArray {
        data class Entry(val role: String, val parts: MutableList<JsonObject>)

        val normalized = mutableListOf<Entry>()

        for (msg in messages) {
            if (msg.role == "system") continue
            val textOk = msg.text.isNotBlank()
            val at = msg.attachments
            val hasImage = at.any { it.type == "image" && it.dataUrl?.startsWith("data:image/") == true }
            val hasFile = at.any { it.type == "file" && !it.name.isNullOrBlank() }
            if (!textOk && !hasImage && !hasFile) continue

            val role = if (msg.role == "ai") "model" else "user"
            val fileLines = at
                .filter { it.type == "file" && !it.name.isNullOrBlank() }
                .map { item ->
                    val mime = item.mimeType?.trim().takeIf { !it.isNullOrBlank() } ?: "unknown"
                    "- ${item.name!!.trim()} ($mime)"
                }
            var text = msg.text.trim()
            if (fileLines.isNotEmpty()) {
                val fileBlock = buildString {
                    appendLine("[添付ファイル（ファイル内容は送信されず名前のみ）]")
                    fileLines.forEach { appendLine(it) }
                }.trimEnd()
                text = if (text.isNotEmpty()) "$text\n\n$fileBlock" else fileBlock
            }
            if (text.isEmpty() && hasImage) {
                text = "(添付のみ)"
            }

            val imageParts = at
                .filter { it.type == "image" && it.dataUrl?.startsWith("data:image/") == true }
                .map { item ->
                    val comma = item.dataUrl!!.indexOf(',')
                    val payload = if (comma >= 0) item.dataUrl!!.substring(comma + 1) else ""
                    val mime = item.mimeType?.trim().takeIf { !it.isNullOrBlank() }
                        ?: item.dataUrl!!.substringAfter("data:", "").substringBefore(";")
                    buildJsonObject {
                        put(
                            "inline_data",
                            buildJsonObject {
                                put("mime_type", mime.ifBlank { "image/png" })
                                put("data", payload)
                            },
                        )
                    }
                }

            val textPart = buildJsonObject { put("text", text) }
            val last = normalized.lastOrNull()
            if (last != null && last.role == role) {
                last.parts.add(textPart)
                last.parts.addAll(imageParts)
            } else {
                normalized.add(
                    Entry(role, mutableListOf<JsonObject>().apply { add(textPart); addAll(imageParts) }),
                )
            }
        }

        while (normalized.isNotEmpty() && normalized.first().role != "user") {
            normalized.removeAt(0)
        }

        return buildJsonArray {
            normalized.forEach { e ->
                add(
                    buildJsonObject {
                        put("role", e.role)
                        put("parts", buildJsonArray { e.parts.forEach { add(it) } })
                    },
                )
            }
        }
    }

    private fun streamFromSse(
        url: String,
        jsonBody: String,
        onChunk: (delta: String, accumulated: String) -> Unit,
    ): String {
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(jsonBody.toRequestBody(JSON_MEDIA))
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val detail = response.body?.string().orEmpty()
                throw IOException("Gemini stream ${response.code}: $detail")
            }
            val source = response.body?.source() ?: throw IOException("レスポンスボディがありません")
            var fullText = ""
            val pendingEventDataLines = mutableListOf<String>()

            fun parseDelta(rawText: String): String {
                val raw = rawText.trim()
                if (raw.isEmpty() || raw == "[DONE]") return ""
                val parsed = runCatching { jsonParser.parseToJsonElement(raw).jsonObject }.getOrNull()
                    ?: return ""
                val candidate = parsed["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?: return ""
                if (candidate["finishReason"]?.jsonPrimitive?.content == "SAFETY") {
                    throw IOException("Geminiの安全フィルタにより応答がブロックされました")
                }
                val parts = candidate["content"]?.jsonObject?.get("parts")?.jsonArray ?: return ""
                return parts.joinToString("") { part ->
                    part.jsonObject["text"]?.jsonPrimitive?.content ?: ""
                }
            }

            fun flushPending() {
                if (pendingEventDataLines.isEmpty()) return
                val raw = pendingEventDataLines.joinToString("\n")
                pendingEventDataLines.clear()
                val text = parseDelta(raw)
                if (text.isEmpty()) return
                var delta = text
                if (fullText.isNotEmpty() && delta.startsWith(fullText)) {
                    delta = delta.removePrefix(fullText)
                }
                if (delta.isEmpty()) return
                fullText += delta
                onChunk(delta, fullText)
            }

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) {
                    flushPending()
                    continue
                }
                if (!line.startsWith("data:")) continue
                val raw = line.removePrefix("data:").trimStart()
                if (raw.isEmpty() || raw == "[DONE]") continue
                if (pendingEventDataLines.isEmpty()) {
                    val single = parseDelta(raw)
                    if (single.isNotEmpty()) {
                        var delta = single
                        if (fullText.isNotEmpty() && delta.startsWith(fullText)) {
                            delta = delta.removePrefix(fullText)
                        }
                        if (delta.isNotEmpty()) {
                            fullText += delta
                            onChunk(delta, fullText)
                        }
                        continue
                    }
                }
                pendingEventDataLines.add(raw)
            }
            flushPending()
            fullText.ifBlank { "応答を取得できませんでした。" }
        }
    }

    private fun nonStream(url: String, jsonBody: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .post(jsonBody.toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Gemini API ${response.code}: $body")
            }
            val root = runCatching { jsonParser.parseToJsonElement(body).jsonObject }.getOrNull()
                ?: return "応答を取得できませんでした。"
            val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            if (candidate?.get("finishReason")?.jsonPrimitive?.content == "SAFETY") {
                throw IOException("Geminiの安全フィルタにより応答がブロックされました")
            }
            return candidate?.get("content")?.jsonObject?.get("parts")?.jsonArray
                ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content ?: "" }
                .orEmpty()
                .ifBlank { "応答を取得できませんでした。" }
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
