package com.tamarilog.codexblanche.data

import com.tamarilog.codexblanche.data.model.AppSettings
import com.tamarilog.codexblanche.data.model.ChatSession

private val thinkingOptions = setOf("low", "medium", "high")

fun normalizeThinkingLevel(value: String?, fallback: String = "medium"): String {
    val v = value?.trim()?.lowercase().orEmpty()
    return if (v in thinkingOptions) v else fallback
}

/**
 * Web版 `getEffectiveSettings()` と同じ優先順位（会話オーバーライド → グローバル設定）
 */
fun resolveEffectiveSettings(session: ChatSession?, global: AppSettings): EffectiveAiSettings {
    val o = session?.overrides
    val provider = o?.provider?.ifBlank { null } ?: global.provider
    val systemPrompt = when {
        o?.systemPrompt != null -> o.systemPrompt
        !session?.systemPrompt.isNullOrBlank() -> session!!.systemPrompt
        else -> global.systemPrompt
    }
    val geminiModel = o?.geminiModel?.ifBlank { null } ?: global.geminiModel
    val openaiModel = o?.openaiModel?.ifBlank { null } ?: global.openaiModel
    val allowGeminiSearch = o?.allowGeminiSearch ?: global.allowGeminiSearch
    val allowOpenaiSearch = o?.allowOpenaiSearch ?: global.allowOpenaiSearch
    val temperature = o?.temperature?.takeIf { it.isFinite() } ?: global.temperature
    val maxTokens = o?.maxTokens?.takeIf { it > 0 } ?: global.maxTokens
    val thinkingLevel = o?.thinkingLevel?.let { normalizeThinkingLevel(it) }
        ?: normalizeThinkingLevel(global.thinkingLevel)
    val userSignature = session?.userSignature?.ifBlank { null } ?: global.userSignature
    val renderSpeed = global.renderSpeed.ifBlank { "normal" }

    return EffectiveAiSettings(
        provider = provider,
        systemPrompt = systemPrompt,
        geminiModel = geminiModel,
        openaiModel = openaiModel,
        allowGeminiSearch = allowGeminiSearch,
        allowOpenaiSearch = allowOpenaiSearch,
        temperature = temperature,
        maxTokens = maxTokens,
        thinkingLevel = thinkingLevel,
        userSignature = userSignature,
        renderSpeed = renderSpeed,
    )
}

data class EffectiveAiSettings(
    val provider: String,
    val systemPrompt: String,
    val geminiModel: String,
    val openaiModel: String,
    val allowGeminiSearch: Boolean,
    val allowOpenaiSearch: Boolean,
    val temperature: Double,
    val maxTokens: Int,
    val thinkingLevel: String,
    val userSignature: String,
    val renderSpeed: String,
)
