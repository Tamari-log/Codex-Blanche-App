package com.tamarilog.codexblanche.data

import com.tamarilog.codexblanche.data.model.ChatMessage
import com.tamarilog.codexblanche.data.model.ChatSession
import com.tamarilog.codexblanche.data.model.MessageAttachment
import com.tamarilog.codexblanche.data.model.SessionOverrides
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val sensitiveImportKeys = setOf("geminiKey", "openaiKey", "googleAccessToken")

object ChatImportExport {
    const val CHAT_IMPORT_PREFIX = "__CODEX_CHATS__"

    fun sanitizeConversationJson(element: JsonElement): JsonElement = when (element) {
        is JsonArray -> JsonArray(element.map { sanitizeConversationJson(it) })
        is JsonObject -> buildJsonObject {
            element.forEach { (k, v) ->
                if (k !in sensitiveImportKeys) put(k, sanitizeConversationJson(v))
            }
        }
        else -> element
    }

    fun decodeChatPayload(source: String): JsonElement? {
        val text = source.trim()
        if (text.isEmpty()) return null
        runCatching { return CodexJson.parseToJsonElement(text) }
        val idx = text.indexOf(CHAT_IMPORT_PREFIX)
        if (idx < 0) return null
        val jsonText = text.substring(idx + CHAT_IMPORT_PREFIX.length).trim()
        if (jsonText.isEmpty()) return null
        return runCatching { CodexJson.parseToJsonElement(jsonText) }.getOrNull()
    }

    fun extractSessionsFromPayload(payload: JsonElement?): List<JsonObject>? {
        if (payload == null) return null
        if (payload is JsonArray) return payload.mapNotNull { it as? JsonObject }
        val obj = payload.jsonObject
        obj["sessions"]?.jsonArray?.let { return it.mapNotNull { e -> e as? JsonObject } }
        val worlds = obj["worlds"]?.jsonArray ?: return null
        val all = mutableListOf<JsonObject>()
        for (w in worlds) {
            val sessions = w.jsonObject["sessions"]?.jsonArray ?: continue
            for (s in sessions) (s as? JsonObject)?.let { all.add(it) }
        }
        return all.ifEmpty { null }
    }

    fun normalizeImportedSession(raw: JsonObject, index: Int): ChatSession {
        val id = raw["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: java.util.UUID.randomUUID().toString()
        val title = raw["title"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
            ?: "インポート会話 ${index + 1}"
        val messages = raw["messages"]?.jsonArray?.mapNotNull { m ->
            val o = m.jsonObject
            val role = o["role"]?.jsonPrimitive?.content
            val text = o["text"]?.jsonPrimitive?.content
            if ((role == "user" || role == "ai") && text != null) ChatMessage(role = role, text = text)
            else null
        } ?: emptyList()
        val systemPrompt = raw["systemPrompt"]?.jsonPrimitive?.content ?: ""
        val pinned = raw["pinned"]?.jsonPrimitive?.booleanOrNull == true
        val overridesRaw = raw["overrides"]?.jsonObject
        val overrides = parseOverrides(overridesRaw, systemPrompt)
        val userSignature = raw["userSignature"]?.jsonPrimitive?.content ?: "Blanche"
        return ChatSession(
            id = id,
            title = title,
            messages = messages,
            pinned = pinned,
            systemPrompt = systemPrompt,
            userSignature = userSignature,
            overrides = overrides,
        )
    }

    private fun parseOverrides(raw: JsonObject?, fallbackSystem: String): SessionOverrides {
        if (raw == null) {
            return SessionOverrides(systemPrompt = fallbackSystem.takeIf { it.isNotBlank() })
        }
        return SessionOverrides(
            provider = raw["provider"]?.jsonPrimitive?.content,
            geminiModel = raw["geminiModel"]?.jsonPrimitive?.content,
            openaiModel = raw["openaiModel"]?.jsonPrimitive?.content,
            allowGeminiSearch = raw["allowGeminiSearch"]?.jsonPrimitive?.booleanOrNull,
            allowOpenaiSearch = raw["allowOpenaiSearch"]?.jsonPrimitive?.booleanOrNull,
            temperature = raw["temperature"]?.jsonPrimitive?.doubleOrNull,
            maxTokens = raw["maxTokens"]?.jsonPrimitive?.intOrNull,
            systemPrompt = raw["systemPrompt"]?.jsonPrimitive?.content ?: fallbackSystem.takeIf { it.isNotBlank() },
            thinkingLevel = raw["thinkingLevel"]?.jsonPrimitive?.content,
        )
    }
}

/** AI に渡す会話を、持ち運びやすい形に整える梱包台。 */
object ApiMessages {
    private const val MAX_API_ATTACHMENT_MESSAGES = 3
    private const val MAX_API_IMAGE_ATTACHMENTS = 6

    fun buildApiMessages(messages: List<ChatMessage>): List<ChatMessage> {
        if (messages.isEmpty()) return messages
        val hasAny = messages.any { it.attachments.isNotEmpty() }
        if (!hasAny) return messages

        val next = messages.toMutableList()
        var remainAttachMsg = MAX_API_ATTACHMENT_MESSAGES
        var remainImages = MAX_API_IMAGE_ATTACHMENTS

        for (i in messages.indices.reversed()) {
            val message = messages[i]
            if (message.attachments.isEmpty()) continue
            if (remainAttachMsg <= 0) {
                next[i] = message.copy(attachments = emptyList())
                continue
            }
            val nextAtt = mutableListOf<MessageAttachment>()
            for (a in message.attachments) {
                if (a.type != "image") continue
                if (remainImages <= 0) break
                nextAtt.add(a)
                remainImages--
            }
            next[i] = message.copy(attachments = nextAtt)
            remainAttachMsg--
        }
        return next
    }
}
