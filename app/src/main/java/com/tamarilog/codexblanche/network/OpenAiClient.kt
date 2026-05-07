package com.tamarilog.codexblanche.network

import com.tamarilog.codexblanche.data.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Web版 [assets/js/providers.js] の `callOpenAIAPI`（/v1/responses）に相当。現状は非ストリーミング。
 */
class OpenAiClient(
    private val client: OkHttpClient = defaultClient,
) {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    suspend fun complete(
        messages: List<ChatMessage>,
        apiKey: String,
        model: String,
        instructions: String?,
        allowSearch: Boolean,
        thinkingLevel: String,
        temperature: Double?,
        maxTokens: Int?,
    ): String = withContext(Dispatchers.IO) {
        val input = buildInputArray(messages)
        val supportsReasoning = Pattern.compile("^gpt-5", Pattern.CASE_INSENSITIVE).matcher(model).find()
        val effort = thinkingLevel.lowercase().let { if (it in setOf("low", "medium", "high")) it else "medium" }

        val body = buildJsonObject {
            put("model", model)
            put("input", input)
            instructions?.trim()?.takeIf { it.isNotEmpty() }?.let { put("instructions", it) }
            if (allowSearch) {
                put(
                    "tools",
                    buildJsonArray { add(buildJsonObject { put("type", "web_search_preview") }) },
                )
            }
            if (supportsReasoning) {
                put("reasoning", buildJsonObject { put("effort", effort) })
            }
            temperature?.let { put("temperature", it) }
            maxTokens?.let { put("max_output_tokens", it) }
        }

        val request = Request.Builder()
            .url("https://api.openai.com/v1/responses")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching { json.parseToJsonElement(raw).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content }.getOrNull() ?: raw
                throw IllegalStateException("OpenAI API（${response.code}）: $detail")
            }
            val root = json.parseToJsonElement(raw).jsonObject
            root["output_text"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: extractOutputTextFallback(root)
                ?: "応答を取得できませんでした。"
        }
    }

    private fun extractOutputTextFallback(root: JsonObject): String? {
        val output = root["output"]?.jsonArray ?: return null
        val sb = StringBuilder()
        for (item in output) {
            val o = item.jsonObject
            val content = o["content"]?.jsonArray ?: continue
            for (c in content) {
                val co = c.jsonObject
                val text = co["text"]?.jsonPrimitive?.content
                if (!text.isNullOrBlank()) sb.append(text)
            }
        }
        return sb.toString().ifBlank { null }
    }

    private fun buildInputArray(messages: List<ChatMessage>): JsonArray {
        val out = buildJsonArray {
            for (msg in messages) {
                if (msg.role == "system") continue
                val role = if (msg.role == "ai") "assistant" else "user"
                val parts = buildOpenAiParts(msg)
                add(
                    buildJsonObject {
                        put("role", role)
                        put("content", parts)
                    },
                )
            }
        }
        return out
    }

    private fun buildOpenAiParts(msg: ChatMessage): JsonArray {
        val parts = buildJsonArray {
            val at = msg.attachments
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
            if (text.isNotEmpty()) {
                add(buildJsonObject { put("type", "input_text"); put("text", text) })
            }
            at.filter { it.type == "image" && it.dataUrl?.startsWith("data:image/") == true }.forEach { item ->
                add(buildJsonObject { put("type", "input_image"); put("image_url", item.dataUrl!!) })
            }
        }
        if (parts.isEmpty()) {
            return buildJsonArray { add(buildJsonObject { put("type", "input_text"); put("text", "") }) }
        }
        return parts
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
