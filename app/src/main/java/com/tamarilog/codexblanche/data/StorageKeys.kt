package com.tamarilog.codexblanche.data

/**
 * 保存場所の名札。将来の引っ越しでも迷子になりにくいように揃えておく。
 */
object StorageKeys {
    const val sessions = "codex_sessions"
    const val activeSessionId = "codex_active_session_id"
    const val personas = "codex_personas"
    const val hiddenSystemPersonaIds = "codex_hidden_system_persona_ids"
    const val provider = "provider"
    const val geminiModel = "gemini_model"
    const val openaiModel = "openai_model"
    const val geminiKey = "gemini_api_key"
    const val openaiKey = "openai_api_key"
    const val rememberApiKeys = "remember_api_keys"
    const val rememberGoogleLogin = "remember_google_login"
    const val driveFolderName = "drive_folder_name"
    const val driveFileName = "drive_file_name"
    const val systemPrompt = "system_prompt"
    const val temperature = "temperature"
    const val maxTokens = "max_tokens"
    const val userSignature = "user_signature"
    const val renderSpeed = "render_speed"
    const val thinkingLevel = "thinking_level"
    const val newSessionProvider = "new_session_provider"
    const val newSessionGeminiModel = "new_session_gemini_model"
    const val newSessionOpenaiModel = "new_session_openai_model"
    const val allowGeminiSearch = "allow_gemini_search"
    const val allowOpenaiSearch = "allow_openai_search"
    const val newSessionAllowGeminiSearch = "new_session_allow_gemini_search"
    const val newSessionAllowOpenaiSearch = "new_session_allow_openai_search"
    const val localUpdatedAt = "codex_local_updated_at"
    const val lastRemoteModifiedAt = "codex_last_remote_modified_at"
    const val deletedAt = "codex_deleted_at"
    const val theme = "codex_theme"
}
