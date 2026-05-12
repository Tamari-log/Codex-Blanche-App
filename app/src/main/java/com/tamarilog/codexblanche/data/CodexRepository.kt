package com.tamarilog.codexblanche.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tamarilog.codexblanche.data.model.AppSettings
import com.tamarilog.codexblanche.data.model.AppSnapshot
import com.tamarilog.codexblanche.data.model.ChatSession
import com.tamarilog.codexblanche.data.model.Persona
import com.tamarilog.codexblanche.data.model.ensurePersonaIds
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val Context.codexDataStore by preferencesDataStore(name = "codex_blanche_prefs")

class CodexRepository(private val context: Context) {

    suspend fun getDeletedAt(): Long {
        val prefs = context.codexDataStore.data.first()
        return prefs[longPreferencesKey(StorageKeys.deletedAt)] ?: 0L
    }

    suspend fun setDeletedAt(value: Long) {
        context.codexDataStore.edit { prefs ->
            if (value > 0) {
                prefs[longPreferencesKey(StorageKeys.deletedAt)] = value
            } else {
                prefs.remove(longPreferencesKey(StorageKeys.deletedAt))
            }
        }
    }

    suspend fun getLocalUpdatedAt(): Long {
        val prefs = context.codexDataStore.data.first()
        return prefs[longPreferencesKey(StorageKeys.localUpdatedAt)] ?: 0L
    }

    suspend fun getLastRemoteModifiedIso(): String {
        val prefs = context.codexDataStore.data.first()
        return prefs[stringPreferencesKey(StorageKeys.lastRemoteModifiedAt)].orEmpty()
    }

    suspend fun setLastRemoteModifiedIso(iso: String) {
        context.codexDataStore.edit {
            it[stringPreferencesKey(StorageKeys.lastRemoteModifiedAt)] = iso
        }
    }

    suspend fun loadSnapshot(): AppSnapshot {
        val prefs = context.codexDataStore.data.first()
        fun str(key: Preferences.Key<String>) = prefs[key].orEmpty()
        fun strOr(key: Preferences.Key<String>, default: String) = prefs[key] ?: default
        fun dbl(key: Preferences.Key<Double>, default: Double) = prefs[key] ?: default
        fun int(key: Preferences.Key<Int>, default: Int) = prefs[key] ?: default
        fun bool(key: Preferences.Key<Boolean>) = prefs[key] == true

        val sessionsJson = str(stringPreferencesKey(StorageKeys.sessions))
        val personasJson = str(stringPreferencesKey(StorageKeys.personas))
        val hiddenJson = str(stringPreferencesKey(StorageKeys.hiddenSystemPersonaIds))

        val sessions = if (sessionsJson.isBlank()) {
            emptyList()
        } else {
            CodexJson.decodeFromString(ListSerializer(ChatSession.serializer()), sessionsJson)
        }
        val personasDecoded = if (personasJson.isBlank()) {
            emptyList()
        } else {
            CodexJson.decodeFromString(ListSerializer(Persona.serializer()), personasJson)
        }
        val personas = personasDecoded.ensurePersonaIds()
        val hidden = if (hiddenJson.isBlank()) {
            emptyList()
        } else {
            CodexJson.decodeFromString(ListSerializer(String.serializer()), hiddenJson)
        }

        val activeId = str(stringPreferencesKey(StorageKeys.activeSessionId)).ifBlank { null }

        val settings = AppSettings(
            provider = strOr(stringPreferencesKey(StorageKeys.provider), "gemini"),
            geminiModel = strOr(stringPreferencesKey(StorageKeys.geminiModel), "gemini-3.1-pro-preview"),
            openaiModel = strOr(stringPreferencesKey(StorageKeys.openaiModel), "gpt-5.3"),
            userSignature = strOr(stringPreferencesKey(StorageKeys.userSignature), "Blanche"),
            temperature = dbl(doublePreferencesKey(StorageKeys.temperature), 0.7),
            maxTokens = int(intPreferencesKey(StorageKeys.maxTokens), 2048),
            renderSpeed = strOr(stringPreferencesKey(StorageKeys.renderSpeed), "normal"),
            thinkingLevel = strOr(stringPreferencesKey(StorageKeys.thinkingLevel), "medium"),
            allowGeminiSearch = bool(booleanPreferencesKey(StorageKeys.allowGeminiSearch)),
            allowOpenaiSearch = bool(booleanPreferencesKey(StorageKeys.allowOpenaiSearch)),
            newSessionProvider = strOr(stringPreferencesKey(StorageKeys.newSessionProvider), "gemini"),
            newSessionGeminiModel = strOr(
                stringPreferencesKey(StorageKeys.newSessionGeminiModel),
                "gemini-3.1-pro-preview",
            ),
            newSessionOpenaiModel = strOr(
                stringPreferencesKey(StorageKeys.newSessionOpenaiModel),
                "gpt-5.3",
            ),
            newSessionAllowGeminiSearch = bool(booleanPreferencesKey(StorageKeys.newSessionAllowGeminiSearch)),
            newSessionAllowOpenaiSearch = bool(booleanPreferencesKey(StorageKeys.newSessionAllowOpenaiSearch)),
            rememberApiKeys = bool(booleanPreferencesKey(StorageKeys.rememberApiKeys)),
            rememberGoogleLogin = bool(booleanPreferencesKey(StorageKeys.rememberGoogleLogin)),
            systemPrompt = str(stringPreferencesKey(StorageKeys.systemPrompt)),
            driveFolderName = strOr(stringPreferencesKey(StorageKeys.driveFolderName), "CodexBlanche"),
            driveFileName = strOr(stringPreferencesKey(StorageKeys.driveFileName), "codex_data.json"),
            geminiApiKey = str(stringPreferencesKey(StorageKeys.geminiKey)),
            openaiApiKey = str(stringPreferencesKey(StorageKeys.openaiKey)),
            theme = str(stringPreferencesKey(StorageKeys.theme)),
        )

        val snapshot = AppSnapshot(
            sessions = sessions,
            activeSessionId = activeId,
            personas = personas,
            hiddenSystemPersonaIds = hidden,
            settings = settings,
        )
        if (personasDecoded.any { it.id.isBlank() }) {
            saveSnapshot(snapshot, bumpLocalTimestamp = false)
        }
        return snapshot
    }

    suspend fun saveSnapshot(snapshot: AppSnapshot, bumpLocalTimestamp: Boolean = true) {
        val sessionsJson = CodexJson.encodeToString(
            ListSerializer(ChatSession.serializer()),
            snapshot.sessions,
        )
        val personasJson = CodexJson.encodeToString(
            ListSerializer(Persona.serializer()),
            snapshot.personas,
        )
        val hiddenJson = CodexJson.encodeToString(
            ListSerializer(String.serializer()),
            snapshot.hiddenSystemPersonaIds,
        )
        val s = snapshot.settings
        context.codexDataStore.edit { prefs ->
            prefs[stringPreferencesKey(StorageKeys.sessions)] = sessionsJson
            prefs[stringPreferencesKey(StorageKeys.personas)] = personasJson
            prefs[stringPreferencesKey(StorageKeys.hiddenSystemPersonaIds)] = hiddenJson
            prefs[stringPreferencesKey(StorageKeys.activeSessionId)] = snapshot.activeSessionId.orEmpty()
            prefs[stringPreferencesKey(StorageKeys.provider)] = s.provider
            prefs[stringPreferencesKey(StorageKeys.geminiModel)] = s.geminiModel
            prefs[stringPreferencesKey(StorageKeys.openaiModel)] = s.openaiModel
            prefs[stringPreferencesKey(StorageKeys.userSignature)] = s.userSignature
            prefs[doublePreferencesKey(StorageKeys.temperature)] = s.temperature
            prefs[intPreferencesKey(StorageKeys.maxTokens)] = s.maxTokens
            prefs[stringPreferencesKey(StorageKeys.renderSpeed)] = s.renderSpeed
            prefs[stringPreferencesKey(StorageKeys.thinkingLevel)] = s.thinkingLevel
            prefs[booleanPreferencesKey(StorageKeys.allowGeminiSearch)] = s.allowGeminiSearch
            prefs[booleanPreferencesKey(StorageKeys.allowOpenaiSearch)] = s.allowOpenaiSearch
            prefs[stringPreferencesKey(StorageKeys.newSessionProvider)] = s.newSessionProvider
            prefs[stringPreferencesKey(StorageKeys.newSessionGeminiModel)] = s.newSessionGeminiModel
            prefs[stringPreferencesKey(StorageKeys.newSessionOpenaiModel)] = s.newSessionOpenaiModel
            prefs[booleanPreferencesKey(StorageKeys.newSessionAllowGeminiSearch)] = s.newSessionAllowGeminiSearch
            prefs[booleanPreferencesKey(StorageKeys.newSessionAllowOpenaiSearch)] = s.newSessionAllowOpenaiSearch
            prefs[booleanPreferencesKey(StorageKeys.rememberApiKeys)] = s.rememberApiKeys
            prefs[booleanPreferencesKey(StorageKeys.rememberGoogleLogin)] = s.rememberGoogleLogin
            prefs[stringPreferencesKey(StorageKeys.systemPrompt)] = s.systemPrompt
            prefs.remove(stringPreferencesKey("google_client_id"))
            prefs[stringPreferencesKey(StorageKeys.driveFolderName)] = s.driveFolderName
            prefs[stringPreferencesKey(StorageKeys.driveFileName)] = s.driveFileName
            prefs[stringPreferencesKey(StorageKeys.theme)] = s.theme
            if (s.rememberApiKeys) {
                prefs[stringPreferencesKey(StorageKeys.geminiKey)] = s.geminiApiKey
                prefs[stringPreferencesKey(StorageKeys.openaiKey)] = s.openaiApiKey
            } else {
                prefs.remove(stringPreferencesKey(StorageKeys.geminiKey))
                prefs.remove(stringPreferencesKey(StorageKeys.openaiKey))
            }
            if (bumpLocalTimestamp) {
                prefs[longPreferencesKey(StorageKeys.localUpdatedAt)] = System.currentTimeMillis()
            }
        }
    }

    /** 会話がまだ無い時は、最初の白紙を一枚そっと用意する。 */
    suspend fun ensureActiveSession(snapshot: AppSnapshot): AppSnapshot {
        if (snapshot.sessions.isEmpty()) {
            val id = UUID.randomUUID().toString()
            val title = "会話 ${SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN).format(Date())}"
            val newSession = ChatSession(id = id, title = title)
            val next = snapshot.copy(
                sessions = listOf(newSession) + snapshot.sessions,
                activeSessionId = id,
            )
            saveSnapshot(next)
            return next
        }
        if (snapshot.activeSessionId == null ||
            snapshot.sessions.none { it.id == snapshot.activeSessionId }
        ) {
            val next = snapshot.copy(activeSessionId = snapshot.sessions.first().id)
            saveSnapshot(next)
            return next
        }
        return snapshot
    }
}
