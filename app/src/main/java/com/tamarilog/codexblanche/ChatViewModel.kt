package com.tamarilog.codexblanche

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tamarilog.codexblanche.data.ApiMessages
import com.tamarilog.codexblanche.data.AttachmentProcessor
import com.tamarilog.codexblanche.data.ChatImportExport
import com.tamarilog.codexblanche.data.CodexJson
import com.tamarilog.codexblanche.data.CodexRepository
import com.tamarilog.codexblanche.data.EffectiveAiSettings
import com.tamarilog.codexblanche.data.normalizeThinkingLevel
import com.tamarilog.codexblanche.data.resolveEffectiveSettings
import com.tamarilog.codexblanche.data.model.AppSettings
import com.tamarilog.codexblanche.data.model.AppSnapshot
import com.tamarilog.codexblanche.data.model.ChatMessage
import com.tamarilog.codexblanche.data.model.ChatSession
import com.tamarilog.codexblanche.data.model.MessageAttachment
import com.tamarilog.codexblanche.data.model.Persona
import com.tamarilog.codexblanche.data.model.SessionOverrides
import com.tamarilog.codexblanche.network.GeminiClient
import com.tamarilog.codexblanche.network.OpenAiClient
import com.tamarilog.codexblanche.sync.DriveSyncRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class DevLogEntry(val at: Long, val level: String, val text: String)

data class ChatUiState(
    val loading: Boolean = true,
    val snapshot: AppSnapshot,
    val streamingAssistant: String? = null,
    val error: String? = null,
    val sending: Boolean = false,
    /** プリセットパネル（Webの system preset panel） */
    val presetPanelOpen: Boolean = false,
    val historySearchQuery: String = "",
    val pendingImages: List<PendingImage> = emptyList(),
    val pendingFiles: List<AttachmentProcessor.ExtractedFile> = emptyList(),
    val driveStatus: String = "Drive: 未接続",
    val driveSignedIn: Boolean = false,
    val devLogs: List<DevLogEntry> = emptyList(),
    val importConfirmMessage: String? = null,
    /** Web state.ui.activePersonaId — カスタムプリセットの選択ハイライト */
    val activePersonaId: String? = null,
) {
    fun activeSession(): ChatSession? {
        val id = snapshot.activeSessionId ?: return null
        return snapshot.sessions.firstOrNull { it.id == id }
    }
}

data class PendingImage(val uri: Uri, val dataUrl: String, val mimeType: String, val label: String)

class ChatViewModel(
    application: Application,
    private val repo: CodexRepository,
    private val drive: DriveSyncRepository,
    private val gemini: GeminiClient = GeminiClient(),
    private val openAi: OpenAiClient = OpenAiClient(),
) : AndroidViewModel(application) {

    private val appContext = application.applicationContext

    private val _ui = MutableStateFlow(ChatUiState(snapshot = emptySnapshot(), loading = true))
    val uiState: StateFlow<ChatUiState> = _ui.asStateFlow()

    private var sendJob: Job? = null
    private val devLogLimit = 200

    init {
        viewModelScope.launch {
            log("INFO", "Codex Blanche 起動")
            var snap = repo.loadSnapshot()
            snap = repo.ensureActiveSession(snap)
            _ui.value = ChatUiState(snapshot = snap, loading = false)
        }
    }

    private fun log(level: String, text: String) {
        _ui.update {
            val next = it.devLogs + DevLogEntry(System.currentTimeMillis(), level, text)
            it.copy(devLogs = next.takeLast(devLogLimit))
        }
    }

    fun clearError() {
        _ui.update { it.copy(error = null, importConfirmMessage = null) }
    }

    fun togglePresetPanel() {
        _ui.update { it.copy(presetPanelOpen = !it.presetPanelOpen) }
    }

    fun setPresetPanel(open: Boolean) {
        _ui.update { it.copy(presetPanelOpen = open) }
    }

    fun setHistorySearchQuery(q: String) {
        _ui.update { it.copy(historySearchQuery = q) }
    }

    fun stopGeneration() {
        sendJob?.cancel()
        sendJob = null
        _ui.update { it.copy(sending = false, streamingAssistant = null) }
    }

    fun updateSettings(newSettings: AppSettings) {
        viewModelScope.launch {
            val snap = _ui.value.snapshot.copy(settings = newSettings)
            repo.saveSnapshot(snap)
            _ui.update { it.copy(snapshot = snap) }
        }
    }

    fun toggleTheme() {
        viewModelScope.launch {
            val cur = _ui.value.snapshot.settings.theme
            val nextTheme = when (cur) {
                "dark" -> "light"
                else -> "dark"
            }
            val s = _ui.value.snapshot.settings.copy(theme = nextTheme)
            val snap = _ui.value.snapshot.copy(settings = s)
            repo.saveSnapshot(snap)
            _ui.update { it.copy(snapshot = snap) }
        }
    }

    fun onImagesSelected(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val current = _ui.value.pendingImages.size + _ui.value.pendingFiles.size
            val cap = AttachmentProcessor.ensureCapacity(current, uris.size)
            if (cap <= 0) {
                _ui.update { it.copy(error = "添付は最大${AttachmentProcessor.MAX_SHARED_FILES}件までです") }
                return@launch
            }
            val take = uris.take(cap)
            val list = _ui.value.pendingImages.toMutableList()
            for (u in take) {
                val (dataUrl, mime) = AttachmentProcessor.readUriAsDataUrl(appContext, u)
                val label = u.lastPathSegment ?: "image"
                list.add(PendingImage(u, dataUrl, mime, label))
            }
            _ui.update { it.copy(pendingImages = list, error = null) }
        }
    }

    fun onFilesSelected(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val current = _ui.value.pendingImages.size + _ui.value.pendingFiles.size
            val cap = AttachmentProcessor.ensureCapacity(current, uris.size)
            if (cap <= 0) {
                _ui.update { it.copy(error = "添付は最大${AttachmentProcessor.MAX_SHARED_FILES}件までです") }
                return@launch
            }
            val picked = uris.take(cap)
            val imageUris = mutableListOf<Uri>()
            val otherUris = mutableListOf<Uri>()
            for (u in picked) {
                if (isLikelyImageUri(u)) imageUris.add(u) else otherUris.add(u)
            }

            val nextImages = _ui.value.pendingImages.toMutableList()
            for (u in imageUris) {
                runCatching {
                    val (dataUrl, mime) = AttachmentProcessor.readUriAsDataUrl(appContext, u)
                    val label = displayNameOf(u) ?: u.lastPathSegment ?: "image"
                    nextImages.add(PendingImage(u, dataUrl, mime, label))
                }.onFailure {
                    otherUris.add(u)
                }
            }

            val extracted = AttachmentProcessor.createFileAttachments(appContext, otherUris)
            val mergedFiles = _ui.value.pendingFiles + extracted
            val omitted = extracted.count { !it.contentAvailable }
            _ui.update {
                it.copy(
                    pendingImages = nextImages,
                    pendingFiles = mergedFiles,
                    error = if (omitted > 0) "一部ファイルは内容抽出できずメタのみです（${omitted}件）" else null,
                )
            }
        }
    }

    private fun displayNameOf(uri: Uri): String? {
        return appContext.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) c.getString(idx) else null
        }
    }

    private fun isLikelyImageUri(uri: Uri): Boolean {
        val mime = appContext.contentResolver.getType(uri).orEmpty().lowercase(Locale.ROOT)
        if (mime.startsWith("image/")) return true
        val name = displayNameOf(uri) ?: uri.lastPathSegment.orEmpty()
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext.isBlank()) return false
        val guessed = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext).orEmpty()
        return guessed.startsWith("image/")
    }

    fun removePendingImage(index: Int) {
        _ui.update {
            it.copy(pendingImages = it.pendingImages.filterIndexed { i, _ -> i != index })
        }
    }

    fun removePendingFile(index: Int) {
        _ui.update {
            it.copy(pendingFiles = it.pendingFiles.filterIndexed { i, _ -> i != index })
        }
    }

    fun clearPendingAttachments() {
        _ui.update { it.copy(pendingImages = emptyList(), pendingFiles = emptyList()) }
    }

    fun selectSession(sessionId: String) {
        viewModelScope.launch {
            val snap = _ui.value.snapshot.copy(activeSessionId = sessionId)
            repo.saveSnapshot(snap)
            _ui.update { it.copy(snapshot = snap, presetPanelOpen = false) }
        }
    }

    fun pinSession(sessionId: String) {
        viewModelScope.launch {
            val snap = _ui.value.snapshot.copy(
                sessions = _ui.value.snapshot.sessions.map { s ->
                    if (s.id == sessionId) s.copy(pinned = !s.pinned) else s
                },
            )
            repo.saveSnapshot(snap)
            _ui.update { it.copy(snapshot = snap) }
        }
    }

    fun renameSession(sessionId: String, newTitle: String) {
        val t = newTitle.trim()
        if (t.isEmpty()) return
        viewModelScope.launch {
            val snap = _ui.value.snapshot.copy(
                sessions = _ui.value.snapshot.sessions.map { s ->
                    if (s.id == sessionId) s.copy(title = t) else s
                },
            )
            repo.saveSnapshot(snap)
            _ui.update { it.copy(snapshot = snap) }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            var snap = _ui.value.snapshot
            val nextSessions = snap.sessions.filter { it.id != sessionId }
            snap = if (nextSessions.isEmpty()) {
                newSessionSnapshot(snap)
            } else {
                val nextActive = if (snap.activeSessionId == sessionId) nextSessions.first().id else snap.activeSessionId
                snap.copy(sessions = nextSessions, activeSessionId = nextActive)
            }
            repo.saveSnapshot(snap)
            _ui.update { it.copy(snapshot = snap) }
        }
    }

    fun newSession() {
        viewModelScope.launch {
            val snap = newSessionSnapshot(_ui.value.snapshot)
            repo.saveSnapshot(snap)
            _ui.update { it.copy(snapshot = snap, activePersonaId = null) }
        }
    }

    private suspend fun newSessionSnapshot(snap: AppSnapshot): AppSnapshot {
        val id = UUID.randomUUID().toString()
        val title = "会話 ${SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN).format(Date())}"
        val global = snap.settings
        val overrides = newSessionOverrides(global)
        val session = ChatSession(
            id = id,
            title = title,
            systemPrompt = global.systemPrompt,
            userSignature = global.userSignature,
            overrides = overrides,
        )
        return snap.copy(sessions = listOf(session) + snap.sessions, activeSessionId = id)
    }

    private fun newSessionOverrides(global: AppSettings): SessionOverrides {
        val p = global.newSessionProvider.ifBlank { global.provider }
        if (p != "gemini" && p != "openai") return SessionOverrides()
        val model = if (p == "gemini") global.newSessionGeminiModel else global.newSessionOpenaiModel
        return SessionOverrides(
            provider = p,
            geminiModel = if (p == "gemini") model else null,
            openaiModel = if (p == "openai") model else null,
            allowGeminiSearch = global.newSessionAllowGeminiSearch,
            allowOpenaiSearch = global.newSessionAllowOpenaiSearch,
        )
    }

    fun startSessionFromPersona(persona: Persona) {
        viewModelScope.launch {
            val global = _ui.value.snapshot.settings
            val s = persona.settings
            val sys = s["systemPrompt"]?.jsonPrimitive?.content ?: ""
            val temp = s["temperature"]?.jsonPrimitive?.doubleOrNull
            val o = SessionOverrides(
                provider = s["provider"]?.jsonPrimitive?.content,
                geminiModel = s["geminiModel"]?.jsonPrimitive?.content,
                openaiModel = s["openaiModel"]?.jsonPrimitive?.content,
                allowGeminiSearch = s["allowGeminiSearch"]?.jsonPrimitive?.booleanOrNull,
                allowOpenaiSearch = s["allowOpenaiSearch"]?.jsonPrimitive?.booleanOrNull,
                temperature = temp,
                maxTokens = s["maxTokens"]?.jsonPrimitive?.intOrNull,
                thinkingLevel = s["thinkingLevel"]?.jsonPrimitive?.content?.let { normalizeThinkingLevel(it) },
                systemPrompt = sys.takeIf { it.isNotBlank() },
            )
            val sig = s["userSignature"]?.jsonPrimitive?.content?.ifBlank { null } ?: global.userSignature
            val id = UUID.randomUUID().toString()
            val title = "会話 ${SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN).format(Date())}"
            val session = ChatSession(
                id = id,
                title = title,
                systemPrompt = sys,
                userSignature = sig,
                overrides = o,
            )
            val nextSettings = global.copy(systemPrompt = sys)
            val snap = _ui.value.snapshot.copy(
                sessions = listOf(session) + _ui.value.snapshot.sessions,
                activeSessionId = id,
                settings = nextSettings,
            )
            repo.saveSnapshot(snap)
            _ui.update { it.copy(snapshot = snap, presetPanelOpen = false, activePersonaId = persona.id) }
        }
    }

    fun hideSystemPersona(personaId: String) {
        viewModelScope.launch {
            val hidden = _ui.value.snapshot.hiddenSystemPersonaIds + personaId
            val snap = _ui.value.snapshot.copy(hiddenSystemPersonaIds = hidden.distinct())
            repo.saveSnapshot(snap)
            _ui.update { it.copy(snapshot = snap) }
        }
    }

    fun saveCustomPersona(name: String, settingsDraft: AppSettings) {
        val n = name.trim()
        if (n.isEmpty()) return
        viewModelScope.launch {
            val json = personaSettingsFromGlobal(settingsDraft)
            val p = Persona(id = UUID.randomUUID().toString(), name = n, settings = json)
            val snap = _ui.value.snapshot.copy(personas = _ui.value.snapshot.personas + p)
            repo.saveSnapshot(snap)
            _ui.update { it.copy(snapshot = snap) }
        }
    }

    fun saveSessionAiConfig(
        sessionId: String,
        provider: String,
        geminiModel: String?,
        openaiModel: String?,
        thinkingLevel: String,
        allowGeminiSearch: Boolean,
        allowOpenaiSearch: Boolean,
        temperature: Double,
        maxTokens: Int,
        systemPrompt: String,
        userSignature: String,
    ) {
        viewModelScope.launch {
            val snap = _ui.value.snapshot
            val session = snap.sessions.firstOrNull { it.id == sessionId } ?: return@launch
            val p = provider.ifBlank { snap.settings.provider }
            val o = SessionOverrides(
                provider = p,
                geminiModel = if (p == "gemini") geminiModel?.ifBlank { null } else null,
                openaiModel = if (p == "openai") openaiModel?.ifBlank { null } else null,
                thinkingLevel = normalizeThinkingLevel(thinkingLevel),
                allowGeminiSearch = allowGeminiSearch,
                allowOpenaiSearch = allowOpenaiSearch,
                temperature = temperature,
                maxTokens = maxTokens,
                systemPrompt = systemPrompt,
            )
            val nextSession = session.copy(
                overrides = o,
                systemPrompt = systemPrompt,
                userSignature = userSignature.trim().ifBlank { "Blanche" },
            )
            val nextSnap = snap.copy(sessions = snap.sessions.map { if (it.id == sessionId) nextSession else it })
            repo.saveSnapshot(nextSnap)
            _ui.update { it.copy(snapshot = nextSnap) }
        }
    }

    fun renamePersona(personaId: String, newName: String) {
        val n = newName.trim()
        if (n.isEmpty()) return
        viewModelScope.launch {
            val snap = _ui.value.snapshot
            val personas = snap.personas.map { if (it.id == personaId) it.copy(name = n) else it }
            val next = snap.copy(personas = personas)
            repo.saveSnapshot(next)
            _ui.update { it.copy(snapshot = next) }
        }
    }

    fun togglePersonaPin(personaId: String) {
        viewModelScope.launch {
            val snap = _ui.value.snapshot
            val personas = snap.personas.map { if (it.id == personaId) it.copy(pinned = !it.pinned) else it }
            val next = snap.copy(personas = personas)
            repo.saveSnapshot(next)
            _ui.update { it.copy(snapshot = next) }
        }
    }

    fun deletePersonaById(personaId: String) {
        viewModelScope.launch {
            val snap = _ui.value.snapshot
            val personas = snap.personas.filter { it.id != personaId }
            val next = snap.copy(personas = personas)
            repo.saveSnapshot(next)
            _ui.update {
                it.copy(
                    snapshot = next,
                    activePersonaId = if (it.activePersonaId == personaId) null else it.activePersonaId,
                )
            }
        }
    }

    fun updateCustomPersonaFull(
        personaId: String,
        name: String,
        provider: String,
        geminiModel: String,
        openaiModel: String,
        thinkingLevel: String,
        allowGeminiSearch: Boolean,
        allowOpenaiSearch: Boolean,
        temperature: Double,
        maxTokens: Int,
        systemPrompt: String,
        userSignature: String,
    ) {
        val n = name.trim()
        if (n.isEmpty()) return
        viewModelScope.launch {
            val snap = _ui.value.snapshot
            val idx = snap.personas.indexOfFirst { it.id == personaId }
            if (idx < 0) return@launch
            val json = buildJsonObject {
                put("provider", provider)
                put("geminiModel", geminiModel)
                put("openaiModel", openaiModel)
                put("thinkingLevel", normalizeThinkingLevel(thinkingLevel))
                put("allowGeminiSearch", allowGeminiSearch)
                put("allowOpenaiSearch", allowOpenaiSearch)
                put("temperature", temperature)
                put("maxTokens", maxTokens)
                put("systemPrompt", systemPrompt)
                put("userSignature", userSignature)
            }
            val updated = snap.personas[idx].copy(name = n, settings = json)
            val personas = snap.personas.toMutableList().also { it[idx] = updated }
            val next = snap.copy(personas = personas)
            repo.saveSnapshot(next)
            _ui.update { it.copy(snapshot = next) }
        }
    }

    private fun personaSettingsFromGlobal(g: AppSettings): JsonObject {
        return buildJsonObject {
            put("provider", g.provider)
            put("geminiModel", g.geminiModel)
            put("openaiModel", g.openaiModel)
            put("systemPrompt", g.systemPrompt)
            put("temperature", g.temperature)
            put("maxTokens", g.maxTokens)
            put("thinkingLevel", g.thinkingLevel)
            put("allowGeminiSearch", g.allowGeminiSearch)
            put("allowOpenaiSearch", g.allowOpenaiSearch)
            put("userSignature", g.userSignature)
        }
    }

    fun deleteMessage(sessionId: String, index: Int) {
        viewModelScope.launch {
            val snap = _ui.value.snapshot
            val s = snap.sessions.firstOrNull { it.id == sessionId } ?: return@launch
            if (index !in s.messages.indices) return@launch
            val nextMsgs = s.messages.toMutableList().also { it.removeAt(index) }
            val nextSession = s.copy(messages = nextMsgs)
            val nextSnap = snap.copy(sessions = snap.sessions.map { if (it.id == sessionId) nextSession else it })
            repo.saveSnapshot(nextSnap)
            _ui.update { it.copy(snapshot = nextSnap) }
        }
    }

    fun updateMessageText(sessionId: String, index: Int, newText: String) {
        viewModelScope.launch {
            val normalized = normalizeEditableText(newText)
            val snap = _ui.value.snapshot
            val s = snap.sessions.firstOrNull { it.id == sessionId } ?: return@launch
            if (index !in s.messages.indices) return@launch
            val nextMsgs = s.messages.toMutableList()
            nextMsgs[index] = nextMsgs[index].copy(text = normalized)
            val nextSession = s.copy(messages = nextMsgs)
            val nextSnap = snap.copy(sessions = snap.sessions.map { if (it.id == sessionId) nextSession else it })
            repo.saveSnapshot(nextSnap)
            _ui.update { it.copy(snapshot = nextSnap) }
        }
    }

    fun regenerateAt(sessionId: String, index: Int) {
        if (_ui.value.sending) return
        val snap = _ui.value.snapshot
        val s = snap.sessions.firstOrNull { it.id == sessionId } ?: return
        val target = s.messages.getOrNull(index) ?: return
        if (target.role != "user" && target.role != "ai") return
        val contextMsgs = if (target.role == "user") {
            s.messages.take(index + 1)
        } else {
            s.messages.take(index)
        }
        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            val trimmed = snap.sessions.map {
                if (it.id == sessionId) it.copy(messages = contextMsgs) else it
            }
            var nextSnap = snap.copy(sessions = trimmed)
            repo.saveSnapshot(nextSnap, bumpLocalTimestamp = false)
            _ui.update { it.copy(snapshot = nextSnap, sending = true, streamingAssistant = "", error = null) }
            runAssistantTurn(sessionId, nextSnap)
        }
    }

    fun sendUserMessage(text: String) {
        val ui = _ui.value
        val trimmed = text.trim()
        if (trimmed.isEmpty() && ui.pendingImages.isEmpty() && ui.pendingFiles.isEmpty()) return
        if (ui.sending) return
        val snapBefore = ui.snapshot
        val session = snapBefore.activeSessionId?.let { id -> snapBefore.sessions.firstOrNull { it.id == id } } ?: return

        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            val fileNote = AttachmentProcessor.buildFileContentNote(ui.pendingFiles)
            val mergedText = listOf(trimmed, fileNote).filter { it.isNotBlank() }.joinToString("\n\n")
            val outgoingText = normalizeEditableText(mergedText).ifBlank {
                if (ui.pendingImages.isNotEmpty()) "(添付のみ)" else ""
            }
            if (outgoingText.isEmpty() && ui.pendingImages.isEmpty()) return@launch

            val attachments = mutableListOf<MessageAttachment>()
            for (p in ui.pendingImages) {
                attachments.add(
                    MessageAttachment(
                        type = "image",
                        mimeType = p.mimeType,
                        dataUrl = p.dataUrl,
                        name = p.label,
                    ),
                )
            }
            for (f in ui.pendingFiles) {
                attachments.add(
                    MessageAttachment(
                        type = "file",
                        mimeType = f.mimeType,
                        name = f.name,
                        previewText = f.content.takeIf { it.isNotBlank() }?.take(8000),
                        size = f.size,
                        contentIncluded = f.contentAvailable,
                    ),
                )
            }
            val userMsg = ChatMessage(role = "user", text = outgoingText, attachments = attachments)
            val updatedSession = session.copy(messages = session.messages + userMsg)
            var snap = snapBefore.copy(
                sessions = snapBefore.sessions.map { if (it.id == session.id) updatedSession else it },
            )
            repo.saveSnapshot(snap, bumpLocalTimestamp = false)
            _ui.update {
                it.copy(
                    snapshot = snap,
                    pendingImages = emptyList(),
                    pendingFiles = emptyList(),
                    streamingAssistant = "",
                    sending = true,
                    error = null,
                )
            }
            runAssistantTurn(session.id, snap)
        }
    }

    private suspend fun runAssistantTurn(sessionId: String, snap: AppSnapshot) {
        val session = snap.sessions.firstOrNull { it.id == sessionId } ?: return
        val g = snap.settings
        val eff = resolveEffectiveSettings(session, g)
        val apiKey = if (eff.provider == "openai") g.openaiApiKey.trim() else g.geminiApiKey.trim()
        if (apiKey.isEmpty()) {
            _ui.update {
                it.copy(
                    error = if (eff.provider == "openai") "OpenAI API キーを設定してください" else "Gemini API キーを設定してください",
                    sending = false,
                    streamingAssistant = null,
                )
            }
            return
        }
        try {
            val apiMessages = ApiMessages.buildApiMessages(session.messages)
            val renderSpeed = eff.renderSpeed.ifBlank { "normal" }
            val reply = when (eff.provider) {
                "openai" -> {
                    _ui.update { it.copy(streamingAssistant = "") }
                    openAi.complete(
                        messages = apiMessages,
                        apiKey = apiKey,
                        model = eff.openaiModel,
                        instructions = eff.systemPrompt.ifBlank { null },
                        allowSearch = eff.allowOpenaiSearch,
                        thinkingLevel = eff.thinkingLevel,
                        temperature = eff.temperature,
                        maxTokens = eff.maxTokens,
                    )
                }
                else -> {
                    streamGeminiWithSpeed(
                        apiMessages,
                        apiKey,
                        eff,
                        renderSpeed,
                    )
                }
            }
            val normalized = normalizeEditableText(reply)
            val revealed = revealAssistantText(normalized, renderSpeed)
            val finalText = revealed.ifBlank { "（応答が空でした。もう一度お試しください）" }
            val finalSession = session.copy(messages = session.messages + ChatMessage(role = "ai", text = finalText))
            val nextSnap = snap.copy(sessions = snap.sessions.map { if (it.id == sessionId) finalSession else it })
            repo.saveSnapshot(nextSnap)
            _ui.update { it.copy(snapshot = nextSnap, sending = false, streamingAssistant = null) }
        } catch (e: CancellationException) {
            _ui.update { it.copy(sending = false, streamingAssistant = null) }
            throw e
        } catch (e: Exception) {
            log("ERROR", e.message ?: e.toString())
            _ui.update {
                it.copy(error = e.message ?: e.toString(), sending = false, streamingAssistant = null)
            }
        } finally {
            sendJob = null
        }
    }

    private suspend fun streamGeminiWithSpeed(
        messages: List<ChatMessage>,
        apiKey: String,
        eff: EffectiveAiSettings,
        renderSpeed: String,
    ): String = withContext(Dispatchers.Default) {
        val charDelayMs = charRevealDelayMs(renderSpeed)
        var accumulated = ""
        var rendered = ""
        gemini.generate(
            messages = messages,
            apiKey = apiKey,
            model = eff.geminiModel,
            systemInstruction = eff.systemPrompt.ifBlank { null },
            temperature = eff.temperature,
            maxTokens = eff.maxTokens,
            allowSearch = eff.allowGeminiSearch,
            onChunk = { delta, full ->
                accumulated = full
                val newChars = when {
                    full.startsWith(rendered) -> full.drop(rendered.length)
                    delta.isNotEmpty() -> delta
                    else -> ""
                }
                if (newChars.isNotEmpty()) {
                    newChars.forEach { ch ->
                        rendered += ch
                        _ui.update { it.copy(streamingAssistant = rendered) }
                        if (charDelayMs > 0) Thread.sleep(charDelayMs)
                    }
                } else {
                    rendered = full
                    _ui.update { it.copy(streamingAssistant = rendered) }
                }
            },
        )
        accumulated
    }

    private suspend fun revealAssistantText(text: String, renderSpeed: String): String {
        if (text.isBlank()) return text
        val waitMs = charRevealDelayMs(renderSpeed)
        var rendered = _ui.value.streamingAssistant.orEmpty()
        if (!text.startsWith(rendered)) rendered = ""
        for (ch in text.drop(rendered.length)) {
            rendered += ch
            _ui.update { it.copy(streamingAssistant = rendered) }
            if (waitMs > 0) delay(waitMs)
        }
        return text
    }

    private fun charRevealDelayMs(renderSpeed: String): Long = when (renderSpeed) {
        "slow" -> 28L
        "fast" -> 5L
        "live" -> 0L
        "batch" -> 8L
        else -> 12L
    }

    private fun normalizeEditableText(s: String): String =
        s.replace("\r\n", "\n").trim()

    // --- Import / Export ---

    fun importChatsJson(text: String) {
        val el = ChatImportExport.decodeChatPayload(text) ?: run {
            _ui.update { it.copy(error = "会話データを解析できませんでした") }
            return
        }
        val sanitized = ChatImportExport.sanitizeConversationJson(el)
        val arr = ChatImportExport.extractSessionsFromPayload(sanitized) ?: run {
            _ui.update { it.copy(error = "会話配列が見つかりません") }
            return
        }
        val sessions = arr.mapIndexed { i, o -> ChatImportExport.normalizeImportedSession(o, i) }
            .filter { it.messages.isNotEmpty() }
        if (sessions.isEmpty()) {
            _ui.update { it.copy(error = "有効な会話がありません") }
            return
        }
        _ui.update {
            it.copy(importConfirmMessage = "${sessions.size}件の会話を読み込みます。現在の履歴を置き換えますか？")
        }
        pendingImport = sessions
    }

    private var pendingImport: List<ChatSession>? = null

    fun confirmImportReplace() {
        val list = pendingImport ?: return
        pendingImport = null
        viewModelScope.launch {
            val snap = _ui.value.snapshot.copy(
                sessions = list,
                activeSessionId = list.first().id,
            )
            repo.saveSnapshot(snap)
            _ui.update { it.copy(snapshot = snap, importConfirmMessage = null) }
        }
    }

    fun cancelImport() {
        pendingImport = null
        _ui.update { it.copy(importConfirmMessage = null) }
    }

    fun exportSessionsJson(): String =
        CodexJson.encodeToString(
            ListSerializer(ChatSession.serializer()),
            _ui.value.snapshot.sessions,
        )

    // --- Google Drive ---

    fun onGoogleSignedIn(account: GoogleSignInAccount?, credentialContext: Context? = null) {
        if (account == null) {
            drive.disconnect()
            _ui.update { it.copy(driveSignedIn = false, driveStatus = "Drive: 未接続") }
            return
        }
        val credCtx = credentialContext ?: appContext
        viewModelScope.launch {
            try {
                drive.connect(account, credCtx)
                drive.ensureFolderAndFile(
                    _ui.value.snapshot.settings.driveFolderName,
                    _ui.value.snapshot.settings.driveFileName,
                )
                _ui.update { it.copy(driveSignedIn = true, driveStatus = "Drive: 接続済み") }
                log("INFO", "Google Drive 接続")
            } catch (e: Exception) {
                _ui.update { it.copy(driveStatus = "Drive接続失敗: ${e.message}", error = e.message) }
            }
        }
    }

    fun drivePush() {
        viewModelScope.launch {
            try {
                if (!drive.isConnected()) {
                    _ui.update { it.copy(error = "先にGoogleにサインインしてください") }
                    return@launch
                }
                drive.ensureFolderAndFile(
                    _ui.value.snapshot.settings.driveFolderName,
                    _ui.value.snapshot.settings.driveFileName,
                )
                val snap = _ui.value.snapshot
                var deletedAt = repo.getDeletedAt()
                val hasData = DriveSyncRepository.hasSyncData(snap.sessions, snap.personas)
                if (hasData) deletedAt = 0L
                if (!hasData && deletedAt == 0L) {
                    // tombstone handled like web
                }
                repo.setDeletedAt(if (hasData) 0L else deletedAt)
                deletedAt = repo.getDeletedAt()
                val json = DriveSyncRepository.buildPayloadJson(snap.sessions, snap.personas, deletedAt)
                val mod = drive.pushJsonBlob(json, snap.settings.driveFileName)
                if (mod != null) repo.setLastRemoteModifiedIso(mod)
                repo.setDeletedAt(deletedAt)
                _ui.update { it.copy(driveStatus = "Drive: 同期済み ${SimpleDateFormat("HH:mm:ss", Locale.JAPAN).format(Date())}") }
            } catch (e: Exception) {
                _ui.update { it.copy(driveStatus = "Drive同期失敗: ${e.message}", error = e.message) }
            }
        }
    }

    fun drivePull() {
        viewModelScope.launch {
            try {
                if (!drive.isConnected()) {
                    _ui.update { it.copy(error = "先にGoogleにサインインしてください") }
                    return@launch
                }
                drive.ensureFolderAndFile(
                    _ui.value.snapshot.settings.driveFolderName,
                    _ui.value.snapshot.settings.driveFileName,
                )
                if (drive.fileId == null) {
                    _ui.update { it.copy(driveStatus = "Drive: リモートファイルなし") }
                    return@launch
                }
                val remoteMod = drive.getFileModifiedTime() ?: ""
                val remoteMs = runCatching {
                    if (remoteMod.isBlank()) 0L else com.google.api.client.util.DateTime(remoteMod).value
                }.getOrDefault(0L)
                val localMs = repo.getLocalUpdatedAt()
                val lastRemote = repo.getLastRemoteModifiedIso()
                val lastRemoteMs = runCatching {
                    if (lastRemote.isBlank()) 0L else com.google.api.client.util.DateTime(lastRemote).value
                }.getOrDefault(0L)
                val hasUnsynced = DriveSyncRepository.hasSyncData(_ui.value.snapshot.sessions, _ui.value.snapshot.personas) &&
                    localMs > lastRemoteMs
                if (hasUnsynced && localMs - remoteMs > 1000) {
                    drivePush()
                    _ui.update { it.copy(driveStatus = "Drive: ローカル優先で上書き") }
                    return@launch
                }
                val body = drive.downloadBody()
                val (rsess, rpers, rdel) = drive.parseRemotePayload(body)
                if (rdel > 0L && repo.getDeletedAt() <= 0L && System.currentTimeMillis() - rdel <= 30L * 24 * 60 * 60 * 1000) {
                    if (localMs <= rdel) {
                        repo.setDeletedAt(rdel)
                        val emptySnap = _ui.value.snapshot.copy(sessions = emptyList(), personas = emptyList())
                        repo.saveSnapshot(repo.ensureActiveSession(emptySnap), bumpLocalTimestamp = false)
                        if (remoteMod.isNotEmpty()) repo.setLastRemoteModifiedIso(remoteMod)
                        _ui.update { it.copy(snapshot = repo.loadSnapshot(), driveStatus = "Drive: 削除マーク適用") }
                        return@launch
                    }
                }
                var next = _ui.value.snapshot
                if (rsess != null) next = next.copy(sessions = rsess)
                if (rpers != null) next = next.copy(personas = rpers)
                repo.setDeletedAt(0L)
                next = repo.ensureActiveSession(next)
                if (next.activeSessionId == null || next.sessions.none { it.id == next.activeSessionId }) {
                    next = next.copy(activeSessionId = next.sessions.firstOrNull()?.id)
                }
                repo.saveSnapshot(next, bumpLocalTimestamp = false)
                if (remoteMod.isNotEmpty()) repo.setLastRemoteModifiedIso(remoteMod)
                _ui.update { it.copy(snapshot = next, driveStatus = "Drive: 取得済み") }
            } catch (e: Exception) {
                _ui.update { it.copy(driveStatus = "Drive取得失敗: ${e.message}", error = e.message) }
            }
        }
    }

    /**
     * Drive 用 Google サインイン。`requestIdToken` は **ウェブ**用クライアント ID のみ有効で、
     * Android 用 ID を渡すと多くの端末で DEVELOPER_ERROR（10）になるため付けない。
     * 認証は「Android OAuth に登録したパッケージ名＋SHA-1」で足りる。
     */
    fun googleSignInClient(): com.google.android.gms.auth.api.signin.GoogleSignInClient {
        val opts = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
            com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN,
        )
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        return GoogleSignIn.getClient(appContext, opts)
    }

    fun googleSignOut() {
        googleSignInClient().signOut().addOnCompleteListener {
            onGoogleSignedIn(null)
        }
    }

    companion object {
        fun factory(app: CodexBlancheApplication) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(app, app.repository, app.driveSync) as T
            }
        }

        private fun emptySnapshot() = AppSnapshot(
            sessions = emptyList(),
            activeSessionId = null,
            personas = emptyList(),
            hiddenSystemPersonaIds = emptyList(),
            settings = AppSettings(),
        )
    }
}
