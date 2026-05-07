package com.tamarilog.codexblanche.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.android.gms.common.api.ApiException
import com.tamarilog.codexblanche.ChatViewModel
import com.tamarilog.codexblanche.data.model.ChatMessage
import com.tamarilog.codexblanche.data.model.ChatSession
import com.tamarilog.codexblanche.data.model.Persona
import com.tamarilog.codexblanche.ui.components.ChatPaperBackdrop
import com.tamarilog.codexblanche.ui.theme.CodexWebPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onOpenSettings: () -> Unit,
) {
    val ui by vm.uiState.collectAsState()
    var input by remember { mutableStateOf("") }
    var attachMenu by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var sessionConfigFor by remember { mutableStateOf<String?>(null) }
    var personaConfigFor by remember { mutableStateOf<String?>(null) }
    var personaRenameFor by remember { mutableStateOf<String?>(null) }
    var personaRenameDraft by remember { mutableStateOf("") }
    var personaDeleteConfirm by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val isDark = when (ui.snapshot.settings.theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        vm.onImagesSelected(uris)
    }

    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        vm.onFilesSelected(uris)
    }

    val importJson = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) { readUriText(context, uri) }
            if (text != null) {
                vm.importChatsJson(text)
            } else {
                Toast.makeText(context, "ファイルを読み取れませんでした", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val jsHistoryImport = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) { readUriText(context, uri) }
            if (text != null) {
                vm.importChatsJson(text)
            } else {
                Toast.makeText(context, "ファイルを読み取れませんでした", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val session = ui.activeSession()
    val messages = session?.messages.orEmpty()

    LaunchedEffect(messages.size, ui.streamingAssistant) {
        val extra = if (ui.streamingAssistant != null) 1 else 0
        val last = messages.size + extra - 1
        if (last >= 0) {
            listState.animateScrollToItem(last)
        }
    }

    val showScrollToBottom by remember {
        derivedStateOf { listState.canScrollForward }
    }

    if (ui.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    ui.importConfirmMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.cancelImport() },
            title = { Text("インポート確認") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { vm.confirmImportReplace() }) { Text("置き換える") }
            },
            dismissButton = {
                TextButton(onClick = { vm.cancelImport() }) { Text("キャンセル") }
            },
        )
    }

    renameTarget?.let { (sid, _) ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("会話名の編集") },
            text = {
                TextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.renameSession(sid, renameDraft)
                        renameTarget = null
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("キャンセル") }
            },
        )
    }

    if (ui.error != null && ui.importConfirmMessage == null) {
        AlertDialog(
            onDismissRequest = { vm.clearError() },
            confirmButton = {
                TextButton(onClick = { vm.clearError() }) { Text("閉じる") }
            },
            title = { Text("エラー") },
            text = { Text(ui.error!!) },
        )
    }

    personaRenameFor?.let { pid ->
        val p = ui.snapshot.personas.firstOrNull { it.id == pid }
        if (p != null) {
            AlertDialog(
                onDismissRequest = { personaRenameFor = null },
                title = { Text("プリセット名を入力") },
                text = {
                    TextField(
                        value = personaRenameDraft,
                        onValueChange = { personaRenameDraft = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            vm.renamePersona(pid, personaRenameDraft)
                            personaRenameFor = null
                        },
                    ) { Text("保存") }
                },
                dismissButton = {
                    TextButton(onClick = { personaRenameFor = null }) { Text("キャンセル") }
                },
            )
        }
    }

    personaDeleteConfirm?.let { pid ->
        val p = ui.snapshot.personas.firstOrNull { it.id == pid }
        if (p != null) {
            AlertDialog(
                onDismissRequest = { personaDeleteConfirm = null },
                title = { Text("削除確認") },
                text = { Text("プリセット「${p.name}」を削除しますか？") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            vm.deletePersonaById(pid)
                            personaDeleteConfirm = null
                        },
                    ) { Text("削除") }
                },
                dismissButton = {
                    TextButton(onClick = { personaDeleteConfirm = null }) { Text("キャンセル") }
                },
            )
        }
    }

    sessionConfigFor?.let { sid ->
        val s = ui.snapshot.sessions.firstOrNull { it.id == sid }
        if (s != null) {
            SessionAiConfigDialog(
                session = s,
                global = ui.snapshot.settings,
                onDismiss = { sessionConfigFor = null },
                onSave = { prov, gm, om, think, ag, ao, temp, maxT, sys, sig ->
                    vm.saveSessionAiConfig(sid, prov, gm, om, think, ag, ao, temp, maxT, sys, sig)
                },
            )
        }
    }

    personaConfigFor?.let { pid ->
        val p = ui.snapshot.personas.firstOrNull { it.id == pid }
        if (p != null) {
            CustomPersonaAiConfigDialog(
                persona = p,
                global = ui.snapshot.settings,
                onDismiss = { personaConfigFor = null },
                onSave = { name, prov, gm, om, think, ag, ao, temp, maxT, sys, sig ->
                    vm.updateCustomPersonaFull(pid, name, prov, gm, om, think, ag, ao, temp, maxT, sys, sig)
                },
            )
        }
    }

    val chatBg = if (isDark) CodexWebPalette.chatAreaDark else CodexWebPalette.chatAreaLight
    val footerBg = if (isDark) Color(0xFF1E293B) else CodexWebPalette.footerBarLight
    val headerDivider = if (isDark) CodexWebPalette.headerBorderDark else CodexWebPalette.headerBorderLight

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val shellW = minOf(448.dp, maxWidth)
        Surface(
            modifier = Modifier
                .width(shellW)
                .fillMaxHeight()
                .align(Alignment.Center),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 10.dp,
        ) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    // --- Web: header ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "CODEX BLANCHE",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF40260F),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    scope.launch { listState.scrollToItem(0) }
                                },
                        )
                        EmojiIconButton(
                            emoji = "☁️",
                            isDark = isDark,
                            onClick = { vm.drivePull() },
                        )
                        Spacer(Modifier.width(8.dp))
                        EmojiIconButton(
                            emoji = "⚙️",
                            isDark = isDark,
                            onClick = onOpenSettings,
                        )
                    }
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = headerDivider,
                    )

                    // --- Web: preset toggle row ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PresetToggleButton(
                            open = ui.presetPanelOpen,
                            isDark = isDark,
                            onClick = { vm.togglePresetPanel() },
                        )
                    }
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = headerDivider,
                    )

                    // --- main chat ---
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        ChatPaperBackdrop(isDark = isDark, baseColor = chatBg) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 16.dp,
                                    bottom = 8.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(32.dp),
                            ) {
                            if (messages.isEmpty() && ui.streamingAssistant == null) {
                                item {
                                    Text(
                                        "ようこそ、白い写本へ。",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF64748B),
                                        modifier = Modifier.padding(start = 4.dp),
                                    )
                                }
                            }
                            itemsIndexed(messages) { _, msg ->
                                ChatWebBubble(
                                    msg = msg,
                                    session = session,
                                    isDark = isDark,
                                )
                            }
                            if (ui.streamingAssistant != null) {
                                item {
                                    StreamingAiBubble(
                                        text = ui.streamingAssistant ?: "",
                                        isDark = isDark,
                                    )
                                }
                            }
                            }
                        }

                        if (showScrollToBottom) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        val n = listState.layoutInfo.totalItemsCount
                                        if (n > 0) listState.animateScrollToItem(n - 1)
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 100.dp)
                                    .size(42.dp),
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDark) Color(0xEB1E293B) else Color(0xE6FFFFFF),
                                    contentColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF40260F),
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isDark) Color(0x99CBD5E1) else Color(0x807B4F24),
                                ),
                            ) {
                                Text("↓", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // --- footer (composer) ---
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(footerBg)
                            .border(1.dp, CodexWebPalette.footerBorder)
                            .navigationBarsPadding()
                            .padding(16.dp)
                            .imePadding(),
                    ) {
                        Text(
                            text = ui.driveStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569),
                            modifier = Modifier.padding(bottom = 8.dp),
                        )

                        if (ui.pendingImages.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ui.pendingImages.forEachIndexed { i, p ->
                                    ImagePreviewThumb(
                                        uri = p.uri,
                                        onRemove = { vm.removePendingImage(i) },
                                    )
                                }
                            }
                        }

                        if (ui.pendingFiles.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                ui.pendingFiles.forEachIndexed { i, f ->
                                    FilePreviewRow(
                                        name = f.name,
                                        isDark = isDark,
                                        onRemove = { vm.removePendingFile(i) },
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            val fieldColors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (isDark) Color(0xFF64748B) else Color(0xFFCBD5E1),
                                unfocusedBorderColor = if (isDark) Color(0xFF64748B) else Color(0xFFCBD5E1),
                                focusedContainerColor = if (isDark) Color(0xFF334155) else Color(0xFFFFFFFF),
                                unfocusedContainerColor = if (isDark) Color(0xFF334155) else Color(0xFFFFFFFF),
                                focusedTextColor = if (isDark) Color.White else Color.Black,
                                unfocusedTextColor = if (isDark) Color.White else Color.Black,
                            )
                            OutlinedTextField(
                                value = input,
                                onValueChange = { input = it },
                                modifier = Modifier
                                    .weight(8f)
                                    .widthIn(max = Dp.Infinity),
                                placeholder = { Text("問いを深く刻む...") },
                                maxLines = 8,
                                shape = RoundedCornerShape(16.dp),
                                colors = fieldColors,
                            )

                            Box(
                                modifier = Modifier.weight(2f),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box {
                                    AttachPlusButton(isDark = isDark, onClick = { attachMenu = true })
                                    DropdownMenu(
                                        expanded = attachMenu,
                                        onDismissRequest = { attachMenu = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("画像を添付") },
                                            onClick = {
                                                pickImages.launch("image/*")
                                                attachMenu = false
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("ファイルを添付") },
                                            onClick = {
                                                pickFiles.launch(arrayOf("*/*"))
                                                attachMenu = false
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("会話JSONをインポート") },
                                            onClick = {
                                                importJson.launch(
                                                    arrayOf("application/json", "text/plain", "*/*"),
                                                )
                                                attachMenu = false
                                            },
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier.weight(2f),
                                contentAlignment = Alignment.Center,
                            ) {
                                SendGlyphButton(
                                    sending = ui.sending,
                                    isDark = isDark,
                                    onClick = {
                                        if (ui.sending) vm.stopGeneration()
                                        else {
                                            vm.sendUserMessage(input)
                                            input = ""
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                // --- 左スライドプリセット（Web: system-preset-panel）---
                AnimatedVisibility(
                    visible = ui.presetPanelOpen,
                    enter = fadeIn() + slideInHorizontally { -it },
                    exit = fadeOut() + slideOutHorizontally { -it },
                ) {
                    val config = LocalConfiguration.current
                    val panelW = minOf(384.dp, (config.screenWidthDp * 0.84f).dp)
                    Row(Modifier.fillMaxSize()) {
                        Surface(
                            modifier = Modifier
                                .width(panelW)
                                .fillMaxHeight(),
                            color = if (isDark) CodexWebPalette.presetPanelDark else CodexWebPalette.presetPanelLight,
                            tonalElevation = 6.dp,
                            shadowElevation = 12.dp,
                            shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isDark) Color(0xE647486B) else Color(0xB3B89D74),
                            ),
                        ) {
                            val scroll = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .verticalScroll(scroll)
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Column(Modifier.padding(bottom = 2.dp)) {
                                    OutlinedTextField(
                                        value = ui.historySearchQuery,
                                        onValueChange = { vm.setHistorySearchQuery(it) },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("チャット履歴を検索") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = Color(0xA60F172A),
                                            unfocusedContainerColor = Color(0xA60F172A),
                                            focusedTextColor = Color(0xFFF8FAFC),
                                            unfocusedTextColor = Color(0xFFF8FAFC),
                                            focusedPlaceholderColor = Color(0xFF94A3B8),
                                            unfocusedPlaceholderColor = Color(0xFF94A3B8),
                                            focusedBorderColor = Color(0x8F94A3B8),
                                            unfocusedBorderColor = Color(0x8F94A3B8),
                                        ),
                                    )
                                }
                                PresetSidebarGroup(title = "カスタムプリセット", isDark = isDark) {
                                    val custom = ui.snapshot.personas.sortedByDescending { it.pinned }
                                    if (custom.isEmpty()) {
                                        Text(
                                            "項目がありません",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF8B7355),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    } else {
                                        custom.forEach { p ->
                                            CustomPersonaRow(
                                                persona = p,
                                                isDark = isDark,
                                                active = ui.activePersonaId == p.id,
                                                onStart = { vm.startSessionFromPersona(p) },
                                                onEditSettings = { personaConfigFor = p.id },
                                                onRename = {
                                                    personaRenameDraft = p.name
                                                    personaRenameFor = p.id
                                                },
                                                onPin = { vm.togglePersonaPin(p.id) },
                                                onDelete = { personaDeleteConfirm = p.id },
                                            )
                                        }
                                    }
                                }
                                PresetSidebarGroup(title = "チャット履歴", isDark = isDark) {
                                    val q = ui.historySearchQuery.trim().lowercase()
                                    val rows = ui.snapshot.sessions
                                        .sortedByDescending { it.pinned }
                                        .filter { q.isEmpty() || it.title.lowercase().contains(q) }
                                    if (rows.isEmpty()) {
                                        Text(
                                            "項目がありません",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF8B7355),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    } else {
                                        rows.forEach { s ->
                                            SessionHistoryRow(
                                                session = s,
                                                isDark = isDark,
                                                onSelect = {
                                                    vm.selectSession(s.id)
                                                    vm.setPresetPanel(false)
                                                },
                                                onRename = {
                                                    renameDraft = s.title
                                                    renameTarget = s.id to s.title
                                                },
                                                onPin = { vm.pinSession(s.id) },
                                                onSessionConfig = { sessionConfigFor = s.id },
                                                onDelete = { vm.deleteSession(s.id) },
                                                onJsHistoryImport = {
                                                    jsHistoryImport.launch(
                                                        arrayOf(
                                                            "application/json",
                                                            "text/javascript",
                                                            "text/plain",
                                                            "*/*",
                                                        ),
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(Color(0x700F172A))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { vm.setPresetPanel(false) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetSidebarGroup(
    title: String,
    isDark: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by remember { mutableStateOf(true) }
    val border = if (isDark) Color(0xD947486B) else Color(0xA6B89D74)
    val bg = if (isDark) Color(0x730F172A) else Color(0x59FFFFFF)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, border), RoundedCornerShape(13.dp))
            .clip(RoundedCornerShape(13.dp))
            .background(bg),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(horizontal = 13.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF4A2C12),
            )
            Text(
                if (open) "▾" else "▸",
                color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF4A2C12),
            )
        }
        if (open) {
            Column(
                Modifier.padding(start = 8.dp, end = 8.dp, bottom = 11.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun EmojiIconButton(
    emoji: String,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isDark) Color(0xCC1E293B) else Color(0x73FFFFFF),
        border = BorderStroke(1.dp, if (isDark) Color(0xFF475569) else CodexWebPalette.emojiBtnBorder),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(emoji, fontSize = 20.sp)
        }
    }
}

@Composable
private fun PresetToggleButton(open: Boolean, isDark: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        shape = RoundedCornerShape(12.dp),
        color = when {
            open && isDark -> Color(0xFF334155)
            open -> Color(0xFF7B4F24)
            isDark -> Color(0xCC1E293B)
            else -> Color(0x8CFFFFFF)
        },
        border = BorderStroke(1.dp, if (isDark) Color(0xFF475569) else Color(0xFFB89D74)),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text("📜", fontSize = 20.sp, color = if (open) Color.White else Color.Unspecified)
        }
    }
}

@Composable
private fun AttachPlusButton(isDark: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        color = if (isDark) Color(0xCC1E293B) else Color(0x8CFFFFFF),
        border = BorderStroke(1.dp, if (isDark) Color(0xFF475569) else CodexWebPalette.emojiBtnBorder),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text("＋", fontSize = 22.sp, color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF40260F))
        }
    }
}

@Composable
private fun SendGlyphButton(sending: Boolean, isDark: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = if (isDark) Color(0xFFF1F5F9) else CodexWebPalette.sendBtnBg,
        shadowElevation = 6.dp,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                if (sending) "⏹️" else "🖋️",
                fontSize = 22.sp,
                color = if (isDark) Color(0xFF0F172A) else Color.White,
            )
        }
    }
}

@Composable
private fun ChatWebBubble(
    msg: ChatMessage,
    session: ChatSession?,
    isDark: Boolean,
) {
    val isUser = msg.role == "user"
    val sig = session?.userSignature?.takeIf { it.isNotBlank() } ?: "Blanche"
    val labelColor = if (isDark) Color(0xFFF8FAFC) else CodexWebPalette.captionBrown
    val lineColor = if (isDark) Color(0xFF94A3B8) else CodexWebPalette.userBorder
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = if (isUser) "✦ $sig" else "✦ Codex",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = labelColor,
            letterSpacing = 0.1.sp,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        if (isUser) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.End,
            ) {
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(lineColor),
                    )
                    Text(
                        text = msg.text,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            lineHeight = 26.sp,
                        ),
                        color = if (isDark) Color(0xFFF1F5F9) else CodexWebPalette.userMsg,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        } else {
            Text(
                text = msg.text,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp),
                color = if (isDark) Color(0xFFCBD5E1) else CodexWebPalette.aiMsg,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp),
            )
        }
        if (msg.attachments.isNotEmpty()) {
            FlowAttachmentChips(msg, isUser, isDark)
        }
    }
}

@Composable
private fun FlowAttachmentChips(msg: ChatMessage, userAlignRight: Boolean, isDark: Boolean) {
    Row(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth(),
        horizontalArrangement = if (userAlignRight) Arrangement.End else Arrangement.Start,
        // simple row wrap substitute: single row truncate
    ) {
        msg.attachments.take(4).forEach { a ->
            val t = when (a.type) {
                "image" -> "🖼 ${a.name ?: "image"}"
                else -> "📎 ${a.name ?: a.mimeType ?: "?"}"
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isDark) Color(0x591E293B) else Color(0x80FFFFFF),
                border = BorderStroke(1.dp, if (isDark) Color(0x6694A3B8) else CodexWebPalette.fileChipBorder),
                modifier = Modifier.padding(4.dp),
            ) {
                Text(
                    t,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StreamingAiBubble(text: String, isDark: Boolean) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Text(
            "✦ Codex",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = if (isDark) Color(0xFFF8FAFC) else CodexWebPalette.captionBrown,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp),
            color = if (isDark) Color(0xFFCBD5E1) else CodexWebPalette.aiMsg,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun ImagePreviewThumb(uri: Uri, onRemove: () -> Unit) {
    Box {
        AsyncImage(
            model = uri,
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Color(0x99CBD5E1), RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
        Surface(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(4.dp, (-4).dp)
                .size(20.dp),
            shape = CircleShape,
            color = Color(0xE60F172A),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text("×", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun FilePreviewRow(name: String, isDark: Boolean, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isDark) Color(0x591E293B) else Color(0x85FFFFFF))
            .border(1.dp, if (isDark) Color(0x6694A3B8) else CodexWebPalette.fileChipBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
                            color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF40260F),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Surface(
            onClick = onRemove,
            shape = CircleShape,
            color = Color(0xE60F172A),
            modifier = Modifier.size(18.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text("×", color = Color.White, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun CustomPersonaRow(
    persona: Persona,
    isDark: Boolean,
    active: Boolean,
    onStart: () -> Unit,
    onEditSettings: () -> Unit,
    onRename: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PersonaTabPill(
            text = "${if (persona.pinned) "📌 " else ""}${persona.name}",
            active = active,
            isDark = isDark,
            onClick = onStart,
            modifier = Modifier.weight(1f),
        )
        Box {
            Text(
                "⋯",
                modifier = Modifier
                    .clickable { menu = true }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF8B7355),
                fontSize = 16.sp,
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("会話設定を編集") },
                    onClick = {
                        onEditSettings()
                        menu = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("名前を編集") },
                    onClick = {
                        onRename()
                        menu = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (persona.pinned) "ピン留め解除" else "ピン留め") },
                    onClick = {
                        onPin()
                        menu = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("削除") },
                    onClick = {
                        onDelete()
                        menu = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SessionHistoryRow(
    session: ChatSession,
    isDark: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onPin: () -> Unit,
    onSessionConfig: () -> Unit,
    onDelete: () -> Unit,
    onJsHistoryImport: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonaTabPill(
            text = "${if (session.pinned) "📌 " else ""}${session.title}",
            active = false,
            isDark = isDark,
            onClick = onSelect,
            modifier = Modifier.weight(1f),
        )
        Box {
            Text(
                "⋯",
                modifier = Modifier
                    .clickable { menu = true }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF8B7355),
                fontSize = 16.sp,
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("名前を編集") },
                    onClick = {
                        onRename()
                        menu = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (session.pinned) "ピン留め解除" else "ピン留め") },
                    onClick = {
                        onPin()
                        menu = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("会話設定を編集") },
                    onClick = {
                        onSessionConfig()
                        menu = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("削除") },
                    onClick = {
                        onDelete()
                        menu = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("JSファイルから履歴読込") },
                    onClick = {
                        onJsHistoryImport()
                        menu = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PersonaTabPill(
    text: String,
    active: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = when {
            active && isDark -> Color(0xFF2563EB)
            active -> Color(0xFF7B4F24)
            isDark -> Color(0xB81E293B)
            else -> Color(0x59FFFFFF)
        },
        border = BorderStroke(
            1.dp,
            when {
                active && isDark -> Color(0xFF2563EB)
                active -> Color(0xFF7B4F24)
                isDark -> Color(0xFF475569)
                else -> Color(0xFFB89D74)
            },
        ),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = when {
                active -> Color.White
                isDark -> Color(0xFFF8FAFC)
                else -> Color(0xFF40260F)
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun readUriText(context: android.content.Context, uri: Uri): String? =
    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
