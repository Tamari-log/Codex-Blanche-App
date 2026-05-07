package com.tamarilog.codexblanche.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.util.UUID

@Serializable
data class MessageAttachment(
    val type: String,
    val name: String? = null,
    @SerialName("mimeType")
    val mimeType: String? = null,
    @SerialName("dataUrl")
    val dataUrl: String? = null,
    val size: Long = 0,
    @SerialName("contentIncluded")
    val contentIncluded: Boolean = false,
)

@Serializable
data class ChatMessage(
    val role: String,
    val text: String,
    val attachments: List<MessageAttachment> = emptyList(),
)

@Serializable
data class SessionOverrides(
    val provider: String? = null,
    val geminiModel: String? = null,
    val openaiModel: String? = null,
    val allowGeminiSearch: Boolean? = null,
    val allowOpenaiSearch: Boolean? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val systemPrompt: String? = null,
    val thinkingLevel: String? = null,
)

@Serializable
data class ChatSession(
    val id: String,
    val title: String,
    val messages: List<ChatMessage> = emptyList(),
    val pinned: Boolean = false,
    val systemPrompt: String = "",
    val userSignature: String = "Blanche",
    val overrides: SessionOverrides = SessionOverrides(),
)

@Serializable
data class Persona(
    /** 省略時は空。読み込み後 [ensurePersonaIds] で UUID を付与する（Web・古いバックアップ互換） */
    val id: String = "",
    val name: String,
    val pinned: Boolean = false,
    /** Web版と同様、プリセットごとの設定を柔軟に保持 */
    val settings: JsonObject = JsonObject(emptyMap()),
)

/** `id` が欠けていた／空のペルソナに一意 ID を振る（Drive・ローカル移行用） */
fun List<Persona>.ensurePersonaIds(): List<Persona> =
    map { p ->
        if (p.id.isNotBlank()) p
        else p.copy(id = UUID.randomUUID().toString())
    }

@Serializable
data class AppSettings(
    val provider: String = "gemini",
    val geminiModel: String = "gemini-3.1-pro-preview",
    val openaiModel: String = "gpt-5.3",
    val userSignature: String = "Blanche",
    val temperature: Double = 0.7,
    val maxTokens: Int = 2048,
    val renderSpeed: String = "normal",
    val thinkingLevel: String = "medium",
    val allowGeminiSearch: Boolean = false,
    val allowOpenaiSearch: Boolean = false,
    val newSessionProvider: String = "gemini",
    val newSessionGeminiModel: String = "gemini-3.1-pro-preview",
    val newSessionOpenaiModel: String = "gpt-5.3",
    val newSessionAllowGeminiSearch: Boolean = false,
    val newSessionAllowOpenaiSearch: Boolean = false,
    val rememberApiKeys: Boolean = false,
    val rememberGoogleLogin: Boolean = false,
    val systemPrompt: String = "",
    val driveFolderName: String = "CodexBlanche",
    val driveFileName: String = "codex_data.json",
    val geminiApiKey: String = "",
    val openaiApiKey: String = "",
    /** "dark" | "light" | "" (空=システムに従う) Web版 localStorage.theme に相当 */
    val theme: String = "",
)

data class AppSnapshot(
    val sessions: List<ChatSession>,
    val activeSessionId: String?,
    val personas: List<Persona>,
    val hiddenSystemPersonaIds: List<String>,
    val settings: AppSettings,
)

enum class AiProvider(val id: String) {
    Gemini("gemini"),
    OpenAI("openai"),
}

/** Web版 settings.js CONTEXT_LIMITS と同じ上限 */
object ContextLimits {
    const val GEMINI = 15000
    const val OPENAI = 5000
    fun forProvider(provider: String): Int = if (provider == "openai") OPENAI else GEMINI
}

object ModelOptions {
    val gemini: List<Pair<String, String>> = listOf(
        "gemini-3-flash-preview" to "gemini 3 flash（高速）",
        "gemini-3.1-flash-lite-preview" to "gemini 3.1 flash lite（新しい高速）",
        "gemini-3.1-pro-preview" to "gemini 3.1 pro（高性能）",
    )
    val openai: List<Pair<String, String>> = listOf(
        "gpt-5.3" to "gpt-5.3（最新世代）",
        "gpt-5.4-thinking" to "gpt-5.4-thinking（推論重視）",
        "gpt-4.1-mini" to "gpt-4.1-mini（高速）",
    )
}
