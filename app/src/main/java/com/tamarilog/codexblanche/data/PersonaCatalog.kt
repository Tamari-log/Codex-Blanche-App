package com.tamarilog.codexblanche.data

import com.tamarilog.codexblanche.data.model.Persona
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Web版 [assets/js/persona.js] の SYSTEM_PERSONAS
 */
object PersonaCatalog {
    val systemPersonas: List<Persona> = listOf(
        Persona(
            id = "sys-neutral",
            name = "標準",
            settings = buildJsonObject { put("systemPrompt", "") },
        ),
        Persona(
            id = "sys-creative",
            name = "創作補助",
            settings = buildJsonObject {
                put("temperature", 1.0)
                put(
                    "systemPrompt",
                    "あなたは創作支援に強いアシスタントです。複数案を提示し、改善点を具体的に示してください。",
                )
            },
        ),
        Persona(
            id = "sys-concise",
            name = "簡潔回答",
            settings = buildJsonObject {
                put("temperature", 0.3)
                put("systemPrompt", "要点を短く、箇条書き中心で回答してください。")
            },
        ),
    )
}
