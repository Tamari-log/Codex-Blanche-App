package com.tamarilog.codexblanche.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val lenientJson = Json { ignoreUnknownKeys = true }

private const val MaxBodySnippet = 12_000
private const val MaxJsonField = 6_000

/**
 * アプリ側の意味づけや推測をせず、HTTP または本文 JSON から取れた事実だけを並べたレポート。
 * [displayLines] をそのままユーザー向けチャットへ載せられる。
 */
class ApiFailureReport(
    val headerLine: String,
    val detailLines: List<String>,
) : Exception(
    (listOf(headerLine) + detailLines).filter { it.isNotBlank() }.joinToString("\n"),
) {
    val displayLines: List<String> =
        buildList {
            add(headerLine)
            addAll(detailLines.filter { it.isNotBlank() })
        }

    companion object {

        internal fun truncateForDisplay(s: String, max: Int = MaxBodySnippet): String {
            val t = s.trim()
            if (t.length <= max) return t
            return t.take(max) + "\n…（以降 ${t.length - max} 文字）"
        }

        fun openAi(httpCode: Int, rawBody: String): ApiFailureReport {
            val doc = truncateForDisplay(rawBody, MaxBodySnippet)
            val root = runCatching { lenientJson.parseToJsonElement(rawBody.trim()).jsonObject }.getOrNull()
            val err = root?.get("error")?.jsonObject
            val lines = buildList<String> {
                add("HTTP $httpCode")
                if (err != null) {
                    err["message"]?.jsonScalar()?.let { add("error.message: $it") }
                    err["type"]?.jsonScalar()?.let { add("error.type: $it") }
                    err["code"]?.jsonScalar()?.let { add("error.code: $it") }
                    err["param"]?.jsonScalar()?.let { add("error.param: $it") }
                    err["metadata"]?.let { elt ->
                        runCatching { lenientJson.encodeToString(JsonElement.serializer(), elt) }.getOrNull()
                            ?.let { encoded -> add("error.metadata(JSON): ${truncateForDisplay(encoded, MaxJsonField)}") }
                    }
                    if (!any { line -> line.startsWith("error.message:") }) {
                        runCatching { lenientJson.encodeToString(JsonObject.serializer(), err) }.getOrNull()
                            ?.let { encoded -> add("error(JSON):\n${truncateForDisplay(encoded, MaxJsonField)}") }
                    }
                }
                if (!any { line -> line.startsWith("response_body:") } && doc.isNotBlank()) {
                    if (err == null || !any { it.startsWith("error.message:") || it.startsWith("error(JSON):") }) {
                        add("response_body:\n$doc")
                    }
                }
            }
            return ApiFailureReport(headerLine = "OpenAI API", detailLines = lines)
        }

        fun geminiHttp(httpCode: Int, rawBody: String): ApiFailureReport {
            val doc = truncateForDisplay(rawBody, MaxBodySnippet)
            val root = runCatching { lenientJson.parseToJsonElement(rawBody.trim()).jsonObject }.getOrNull()
            val err = root?.get("error")?.jsonObject
            val lines = buildList<String> {
                add("HTTP $httpCode")
                if (err != null) {
                    err["message"]?.jsonScalar()?.let { add("error.message: $it") }
                    err["status"]?.jsonScalar()?.let { add("error.status: $it") }
                    err["code"]?.jsonScalar()?.let { add("error.code: $it") }
                    err["details"]?.let { d ->
                        runCatching { lenientJson.encodeToString(JsonElement.serializer(), d) }.getOrNull()
                            ?.let { encoded -> add("error.details(JSON):\n${truncateForDisplay(encoded, MaxJsonField)}") }
                    }
                    if (!any { line -> line.startsWith("error.message:") }) {
                        runCatching { lenientJson.encodeToString(JsonObject.serializer(), err) }.getOrNull()
                            ?.let { encoded -> add("error(JSON):\n${truncateForDisplay(encoded, MaxJsonField)}") }
                    }
                }
                if (doc.isNotBlank() &&
                    none { line -> line.startsWith("response_body:") } &&
                    (err == null || !any { it.startsWith("error.message:") || it.startsWith("error(JSON):") })
                ) {
                    add("response_body:\n$doc")
                }
            }
            return ApiFailureReport(headerLine = "Gemini API", detailLines = lines)
        }

        fun geminiSafetyFromHttpResponse(httpCode: Int, rawBody: String): ApiFailureReport {
            val doc = truncateForDisplay(rawBody, MaxBodySnippet)
            val lines = mutableListOf<String>()
            lines.add("HTTP $httpCode")
            val root = runCatching { lenientJson.parseToJsonElement(rawBody.trim()).jsonObject }.getOrNull()
            val cand = root?.get("candidates")?.jsonArray?.firstOrNull()?.jsonObject
            cand?.get("finishReason")?.jsonScalar()?.let { lines.add("candidates[0].finishReason: $it") }
            root?.get("promptFeedback")?.let { fb ->
                runCatching { lenientJson.encodeToString(JsonElement.serializer(), fb) }.getOrNull()
                    ?.let { lines.add("promptFeedback(JSON):\n${truncateForDisplay(it, MaxJsonField)}") }
            }
            cand?.let { co ->
                runCatching { lenientJson.encodeToString(JsonObject.serializer(), co) }.getOrNull()
                    ?.let { lines.add("candidates[0](JSON):\n${truncateForDisplay(it, MaxJsonField)}") }
            }
            lines.add("response_body:\n$doc")
            return ApiFailureReport(headerLine = "Gemini API", detailLines = lines)
        }

        fun geminiSafetyFromStreamEvent(rawSsePayload: String): ApiFailureReport {
            val excerpt = truncateForDisplay(rawSsePayload.trim(), MaxJsonField)
            val lines = buildList<String> {
                add("SSE: streamGenerateContent（data 行の原文を表示）")
                rootFinishReasonLine(rawSsePayload)?.let(::add)
                add("SSE data:")
                add(excerpt)
            }
            return ApiFailureReport(headerLine = "Gemini API（ストリーム）", detailLines = lines)
        }

        private fun rootFinishReasonLine(payload: String): String? {
            val root =
                runCatching { lenientJson.parseToJsonElement(payload.trim()).jsonObject }.getOrNull()
                    ?: return null
            val c0 = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
            val fr = c0["finishReason"]?.jsonScalar() ?: return null
            return "candidates[0].finishReason: $fr"
        }
    }
}

/** JSON プリミティブの [kotlinx.serialization.json.JsonPrimitive.content] をそのまま（数値も文字列表現で出す）。 */
private fun JsonElement.jsonScalar(): String? =
    runCatching { jsonPrimitive.content.trim().takeIf { it.isNotEmpty() } }.getOrNull()

internal fun Throwable.apiFailureCause(): ApiFailureReport? {
    var c: Throwable? = this
    while (c != null) {
        if (c is ApiFailureReport) return c
        c = c.cause
    }
    return null
}
