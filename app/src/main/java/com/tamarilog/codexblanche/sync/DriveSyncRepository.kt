package com.tamarilog.codexblanche.sync

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.tamarilog.codexblanche.data.CodexJson
import com.tamarilog.codexblanche.data.model.ChatSession
import com.tamarilog.codexblanche.data.model.Persona
import com.tamarilog.codexblanche.data.model.ensurePersonaIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.util.Collections

/**
 * Web版 [assets/js/sync.js] の Drive 同期に相当
 */
class DriveSyncRepository(private val context: Context) {

    private var drive: Drive? = null
    var folderId: String? = null
        private set
    var fileId: String? = null
        private set

    fun disconnect() {
        drive = null
        folderId = null
        fileId = null
    }

    fun isConnected(): Boolean = drive != null

    /**
     * [credentialContext] は可能なら [android.app.Activity] を渡すこと。
     * Application のみだと UserRecoverableAuth の同意画面などが取りにくく、接続に失敗することがある。
     */
    fun connect(account: GoogleSignInAccount, credentialContext: Context): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            credentialContext,
            Collections.singleton(DriveScopes.DRIVE_FILE),
        )
        val acc: Account? = account.account
        if (acc != null) {
            credential.selectedAccount = acc
        } else {
            val email = account.email
            if (!email.isNullOrBlank()) {
                credential.setSelectedAccountName(email)
            }
        }
        val svc = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential,
        )
            .setApplicationName("Codex Blanche")
            .build()
        drive = svc
        folderId = null
        fileId = null
        return svc
    }

    fun getDriveOrThrow(): Drive = drive ?: error("Drive 未接続")

    private fun normalizeFileName(raw: String, defaultName: String): String {
        val n = raw.ifBlank { defaultName }.trim()
        return if (n.endsWith(".json")) n else "$n.json"
    }

    suspend fun ensureFolderAndFile(
        driveFolderName: String,
        driveFileName: String,
    ) = withContext(Dispatchers.IO) {
        val d = getDriveOrThrow()
        val fName = driveFolderName.trim().ifBlank { "CodexBlanche" }.replace("'", "\\'")
        val flName = normalizeFileName(driveFileName, "codex_data.json").replace("'", "\\'")
        val folderQ = "name='$fName' and mimeType='application/vnd.google-apps.folder' and trashed=false"
        val folders = d.files().list()
            .setQ(folderQ)
            .setSpaces("drive")
            .setFields("files(id,name)")
            .execute()
        folderId = folders.files.firstOrNull()?.id
        if (folderId == null) {
            val meta = com.google.api.services.drive.model.File().apply {
                name = fName
                mimeType = "application/vnd.google-apps.folder"
            }
            val created = d.files().create(meta).setFields("id").execute()
            folderId = created.id
        }
        val fid = folderId ?: error("folder")
        val fileQ = "name='$flName' and '$fid' in parents and trashed=false"
        val files = d.files().list()
            .setQ(fileQ)
            .setSpaces("drive")
            .setFields("files(id,name,modifiedTime)")
            .execute()
        fileId = files.files.firstOrNull()?.id
    }

    suspend fun pushJsonBlob(
        jsonUtf8: String,
        cloudFileName: String,
    ): String? = withContext(Dispatchers.IO) {
        val d = getDriveOrThrow()
        val flName = normalizeFileName(cloudFileName, "codex_data.json")
        val media = ByteArrayContent.fromString("application/json", jsonUtf8)
        val modified = if (fileId != null) {
            d.files().update(fileId, null, media)
                .setFields("modifiedTime")
                .execute()
        } else {
            val parent = folderId ?: error("folderId")
            val meta = com.google.api.services.drive.model.File().apply {
                name = flName
                parents = listOf(parent)
            }
            val created = d.files().create(meta, media)
                .setFields("id,modifiedTime")
                .execute()
            fileId = created.id
            created
        }
        modified.modifiedTime?.toStringRfc3339()
    }

    suspend fun getFileModifiedTime(): String? = withContext(Dispatchers.IO) {
        val id = fileId ?: return@withContext null
        val d = getDriveOrThrow()
        d.files().get(id).setFields("modifiedTime").execute().modifiedTime?.toStringRfc3339()
    }

    suspend fun downloadBody(): String = withContext(Dispatchers.IO) {
        val id = fileId ?: error("file")
        val d = getDriveOrThrow()
        val out = ByteArrayOutputStream()
        d.files().get(id).executeMediaAndDownloadTo(out)
        out.toString(Charsets.UTF_8.name())
    }

    fun parseRemotePayload(raw: String): Triple<List<ChatSession>?, List<Persona>?, Long> {
        val root = runCatching { CodexJson.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return Triple(null, null, 0L)
        val sessions = root["sessions"]?.let { el ->
            CodexJson.decodeFromJsonElement(ListSerializer(ChatSession.serializer()), el)
        }
        val personas = root["personas"]?.let { el ->
            CodexJson.decodeFromJsonElement(ListSerializer(Persona.serializer()), el).ensurePersonaIds()
        }
        val del = root["deletedAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
        return Triple(sessions, personas, del)
    }

    companion object {
        fun buildPayloadJson(
            sessions: List<ChatSession>,
            personas: List<Persona>,
            deletedAt: Long,
        ): String {
            val obj = buildJsonObject {
                put("sessions", CodexJson.encodeToJsonElement(ListSerializer(ChatSession.serializer()), sessions))
                put("personas", CodexJson.encodeToJsonElement(ListSerializer(Persona.serializer()), personas))
                if (deletedAt > 0) {
                    put("deletedAt", JsonPrimitive(deletedAt))
                } else {
                    put("deletedAt", JsonNull)
                }
            }
            return obj.toString()
        }

        fun hasSyncData(sessions: List<ChatSession>, personas: List<Persona>): Boolean {
            val hasPersonas = personas.isNotEmpty()
            val hasMessages = sessions.any { it.messages.isNotEmpty() }
            return hasPersonas || hasMessages
        }
    }
}
