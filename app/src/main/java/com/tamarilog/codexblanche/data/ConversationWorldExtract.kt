package com.tamarilog.codexblanche.data

import android.content.Context
import com.tamarilog.codexblanche.data.model.ChatMessage
import com.tamarilog.codexblanche.data.model.ChatSession
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Web版 main.js の会話JSON抽出（世界設定ごと）を Kotlin で再現。
 */
object ConversationWorldExtract {

    fun toSafeFilename(value: String?, fallback: String): String {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return fallback
        return text
            .replace(Regex("[/:*?\"<>|]"), "-")
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^\\w\\-.ぁ-んァ-ヶ一-龠]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-', '_', '.')
            .ifEmpty { fallback }
    }

    private fun normalizeWorldName(raw: String?, fallback: String = "default"): String {
        val t = raw?.trim().orEmpty()
        return t.ifEmpty { fallback }
    }

    private fun sanitizeNode(node: JsonElement): JsonElement = ChatImportExport.sanitizeConversationJson(node)

    private fun buildFullSessionJson(normalized: ChatSession, raw: JsonObject, safeWorld: String): JsonObject {
        val messagesEl = CodexJson.encodeToJsonElement(
            ListSerializer(ChatMessage.serializer()),
            normalized.messages,
        )
        val base = buildJsonObject {
            put("worldSetting", JsonPrimitive(safeWorld))
            put("id", JsonPrimitive(normalized.id))
            put("title", JsonPrimitive(normalized.title))
            put("messages", messagesEl)
            raw["persona"]?.let { put("persona", it) }
            raw["character"]?.let { put("character", it) }
            raw["model"]?.let { put("model", it) }
            raw["modelName"]?.let { put("modelName", it) }
            raw["settings"]?.jsonObject?.get("model")?.let { put("modelFromSettings", it) }
            raw["temperature"]?.let { put("temperature", it) }
            raw["settings"]?.jsonObject?.get("temperature")?.let { put("temperatureFromSettings", it) }
            raw["context"]?.let { put("context", it) }
            raw["contextSettings"]?.let { put("contextSettings", it) }
            raw["settings"]?.jsonObject?.get("context")?.let { put("contextFromNestedSettings", it) }
        }
        return sanitizeNode(base).jsonObject
    }

    private fun buildHistoryOnlyEntry(normalized: ChatSession): JsonObject {
        val o = buildJsonObject {
            put(
                "messages",
                CodexJson.encodeToJsonElement(
                    ListSerializer(ChatMessage.serializer()),
                    normalized.messages,
                ),
            )
        }
        return sanitizeNode(o).jsonObject
    }

    fun extractFullByWorld(root: JsonElement): Map<String, List<JsonObject>> {
        val groups = linkedMapOf<String, MutableList<JsonObject>>()

        fun ensureWorld(name: String): MutableList<JsonObject> =
            groups.getOrPut(normalizeWorldName(name)) { mutableListOf() }

        fun pushFull(worldName: String, sessionEl: JsonObject) {
            val messages = sessionEl["messages"]?.jsonArray ?: return
            if (messages.isEmpty()) return
            val bucket = ensureWorld(worldName)
            val normalized = ChatImportExport.normalizeImportedSession(sessionEl, bucket.size)
            if (normalized.messages.isEmpty()) return
            val safeWorld = normalizeWorldName(worldName)
            val safeSession = buildFullSessionJson(normalized, sessionEl, safeWorld)
            bucket.add(safeSession)
        }

        when (root) {
            is JsonArray -> {
                root.forEachIndexed { i, el ->
                    if (el is JsonObject) {
                        pushFull("world-${i + 1}", el)
                    }
                }
            }
            is JsonObject -> {
                root["sessions"]?.jsonArray?.forEach { el ->
                    if (el !is JsonObject) return@forEach
                    val worldName =
                        el["worldSetting"]?.jsonPrimitive?.content
                            ?: el["world"]?.jsonPrimitive?.content
                            ?: el["settings"]?.jsonObject?.get("world")?.jsonPrimitive?.content
                            ?: el["meta"]?.jsonObject?.get("world")?.jsonPrimitive?.content
                            ?: "default"
                    pushFull(worldName, el)
                }
                root["worlds"]?.jsonArray?.forEachIndexed { worldIndex, w ->
                    if (w !is JsonObject) return@forEachIndexed
                    val worldName =
                        w["name"]?.jsonPrimitive?.content
                            ?: w["id"]?.jsonPrimitive?.content
                            ?: "world-${worldIndex + 1}"
                    val candidates =
                        w["sessions"]?.jsonArray
                            ?: w["conversations"]?.jsonArray
                            ?: JsonArray(emptyList())
                    candidates.forEach { el ->
                        if (el is JsonObject) pushFull(worldName, el)
                    }
                }
            }
            else -> Unit
        }

        groups.entries.removeAll { it.value.isEmpty() }
        return groups
    }

    fun extractHistoryOnlyByWorld(root: JsonElement): Map<String, List<JsonObject>> {
        val groups = linkedMapOf<String, MutableList<JsonObject>>()

        fun ensureWorld(name: String): MutableList<JsonObject> =
            groups.getOrPut(normalizeWorldName(name)) { mutableListOf() }

        fun pushHistoryOnly(worldName: String, sessionEl: JsonObject) {
            val messages = sessionEl["messages"]?.jsonArray ?: return
            if (messages.isEmpty()) return
            val bucket = ensureWorld(worldName)
            val normalized = ChatImportExport.normalizeImportedSession(sessionEl, bucket.size)
            if (normalized.messages.isEmpty()) return
            bucket.add(buildHistoryOnlyEntry(normalized))
        }

        when (root) {
            is JsonArray -> {
                root.forEachIndexed { i, el ->
                    if (el is JsonObject) {
                        pushHistoryOnly("world-${i + 1}", el)
                    }
                }
            }
            is JsonObject -> {
                root["sessions"]?.jsonArray?.forEach { el ->
                    if (el !is JsonObject) return@forEach
                    val worldName =
                        el["worldSetting"]?.jsonPrimitive?.content
                            ?: el["world"]?.jsonPrimitive?.content
                            ?: el["settings"]?.jsonObject?.get("world")?.jsonPrimitive?.content
                            ?: el["meta"]?.jsonObject?.get("world")?.jsonPrimitive?.content
                            ?: "default"
                    pushHistoryOnly(worldName, el)
                }
                root["worlds"]?.jsonArray?.forEachIndexed { worldIndex, w ->
                    if (w !is JsonObject) return@forEachIndexed
                    val worldName =
                        w["name"]?.jsonPrimitive?.content
                            ?: w["id"]?.jsonPrimitive?.content
                            ?: "world-${worldIndex + 1}"
                    val candidates =
                        w["sessions"]?.jsonArray
                            ?: w["conversations"]?.jsonArray
                            ?: JsonArray(emptyList())
                    candidates.forEach { el ->
                        if (el is JsonObject) pushHistoryOnly(worldName, el)
                    }
                }
            }
            else -> Unit
        }

        groups.entries.removeAll { it.value.isEmpty() }
        return groups
    }

    /**
     * 抽出した JSON をアプリ専用外部ストレージに保存し、保存先パス用メッセージを返す。
     */
    fun writeGroupedWorldExports(
        context: Context,
        groups: Map<String, List<JsonObject>>,
        sourceFileName: String,
        historyOnly: Boolean,
    ): Pair<Int, File?> {
        if (groups.isEmpty()) return 0 to null
        val dir = context.getExternalFilesDir("exports") ?: return 0 to null
        if (!dir.exists()) dir.mkdirs()
        val exportedAt = java.time.Instant.now().toString()
        val ts = System.currentTimeMillis()
        var index = 0
        for ((worldName, sessions) in groups) {
            val payload = sanitizeNode(
                buildJsonObject {
                    put("exportedAt", JsonPrimitive(exportedAt))
                    put("sourceFileName", JsonPrimitive(sourceFileName))
                    if (!historyOnly) {
                        put("worldName", JsonPrimitive(worldName))
                    }
                    put(
                        "sessions",
                        JsonArray(sessions),
                    )
                },
            ).jsonObject
            val token = toSafeFilename(worldName, "world-${index + 1}")
            val prefix = if (historyOnly) "conversation_history_only_" else "conversation_"
            val file = File(dir, "${prefix}${token}_$ts.json")
            file.writeText(CodexJson.encodeToString(JsonObject.serializer(), payload))
            index++
        }
        return groups.size to dir
    }
}
