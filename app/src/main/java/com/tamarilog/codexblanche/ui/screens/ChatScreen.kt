package com.tamarilog.codexblanche.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
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
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.input.pointer.pointerInput
import coil.compose.AsyncImage
import com.tamarilog.codexblanche.ChatViewModel
import com.tamarilog.codexblanche.data.model.ChatMessage
import com.tamarilog.codexblanche.data.model.ChatSession
import com.tamarilog.codexblanche.data.model.MessageAttachment
import com.tamarilog.codexblanche.data.model.Persona
import com.tamarilog.codexblanche.ui.components.ChatPaperBackdrop
import com.tamarilog.codexblanche.ui.theme.CodexWebPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

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
    var previewAttachment by remember { mutableStateOf<MessageAttachment?>(null) }
    var messageEditTarget by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var messageEditDraft by remember { mutableStateOf("") }
    var messageDeleteIndex by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    /** LazyList のスクロール位置は [ui] 更新では再計算されない。末尾矢印の表示に必要。 */
    var listCanScrollForward by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.canScrollForward }.collect { listCanScrollForward = it }
    }
    /** プログラムスクロール同士の競合による中途半端な scroll を防ぐ */
    val programScrollMutex = remember { Mutex() }

    suspend fun withProgrammaticScroll(block: suspend () -> Unit) {
        programScrollMutex.withLock { block() }
    }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

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

    val listBottomPadding = 8.dp

    val density = LocalDensity.current
    /** キーボード開閉でリスト領域の縦サイズが変わる。Composable コンテキストでしか読めない。 */
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    /** Composable で読んだ IME を Hot Flow へ（snapshotFlow 内では WindowInsets を読めない） */
    val imeBottomPxFlow = remember { MutableStateFlow(0) }
    /** 同一フレームで collect 側に IME を先に反映（LaunchedEffect より早い） */
    SideEffect {
        imeBottomPxFlow.value = imeBottomPx
    }

    /** IME が開いていた状態から閉じた直後のみ、レイアウト確定後に一覧を下端へ寄せる */
    LaunchedEffect(Unit) {
        var prevImeBottom = imeBottomPxFlow.value
        imeBottomPxFlow.collect { imeBottom ->
            val imeJustClosed = prevImeBottom > 0 && imeBottom == 0
            prevImeBottom = imeBottom
            if (!imeJustClosed) return@collect
            delay(48)
            val cur = vm.uiState.value
            val sess = cur.activeSession()
            if (sess == null) return@collect
            if (sess.messages.isEmpty() && cur.streamingAssistant == null) return@collect
            withProgrammaticScroll {
                listState.scrollLastItemToBottomEdge()
            }
        }
    }

    /** 起動直後・会話切替後のみ末尾へ（loading のたびに再実行しない） */
    LaunchedEffect(session?.id) {
        val sid = session?.id ?: return@LaunchedEffect
        snapshotFlow { ui.loading }.first { loading -> !loading }
        if (ui.activeSession()?.id != sid) return@LaunchedEffect
        delay(48)
        val sess = ui.activeSession() ?: return@LaunchedEffect
        if (sess.id != sid) return@LaunchedEffect
        if (sess.messages.isNotEmpty() || ui.streamingAssistant != null) {
            withProgrammaticScroll {
                listState.scrollLastItemToBottomEdge()
            }
        }
    }

    /**
     * ユーザー送信直後: Lazy 末尾行が揃ったら下端へ一度寄せる。
     * ストリーム中の自動追従は別 [LaunchedEffect] のレイアウト判定のみ（ViewModel 側にスクロール用フラグなし）。
     * - 通常送信: メッセージ件数が +1 かつ末尾 user
     * - やり直し: 履歴が切り詰められると件数が減るため `c < lastCount`
     * - 末尾が user のみで件数が変わらないやり直し: `sending` が false→true に立ち上がったときだけ
     */
    LaunchedEffect(session?.id) {
        if (session?.id == null) return@LaunchedEffect
        var lastCount = -1
        var prevSending = false
        snapshotFlow {
            Triple(
                ui.loading,
                ui.activeSession()?.messages?.size ?: 0,
                ui.activeSession()?.messages?.lastOrNull()?.role ?: "",
            ) to Pair(ui.sending, ui.streamingAssistant != null)
        }.collect { (triple, sendingPair) ->
            val (loading, c, role) = triple
            val sending = sendingPair.first
            val streamingActive = sendingPair.second
            if (loading) {
                lastCount = c
                prevSending = sending
                return@collect
            }
            val tailRedoSameCount =
                sending &&
                    streamingActive &&
                    role == "user" &&
                    c == lastCount &&
                    lastCount >= 0 &&
                    !prevSending

            if (lastCount >= 0 &&
                sending &&
                streamingActive &&
                role == "user" &&
                (c == lastCount + 1 || c < lastCount || tailRedoSameCount)
            ) {
                scope.launch {
                    withProgrammaticScroll {
                        listState.scrollToTailAfterUserMessage(persistedMessageCount = c)
                    }
                }
            }
            lastCount = c
            prevSending = sending
        }
    }

    /**
     * ストリーム追従: px閾値は使わない。Lazy のフラグだけだと下端と判定がずれることがあるので、
     * 「末尾 Lazy 行が可視」のときだけ合わせに行く一本を足す。
     */
    LaunchedEffect(listState, session?.id) {
        /**
         * 戻り値が前回合わないときだけ snapshotFlow が emit。
         * 末尾に戻ったのにスクロール指標だけ前回と同じだと復帰しないので、可視行数・末尾 index も載せる。
         */
        val layoutFlow = snapshotFlow {
            val info = listState.layoutInfo
            val vis = info.visibleItemsInfo
            val sess = ui.activeSession()
            listOf(
                sess?.messages?.size ?: 0,
                ui.streamingAssistant?.length ?: -1,
                if (ui.streamingAssistant != null) 1 else 0,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                if (listState.canScrollForward) 1 else 0,
                if (listState.isScrollInProgress) 1 else 0,
                vis.size,
                vis.maxOfOrNull { it.index } ?: -1,
            )
        }
        combine(layoutFlow, imeBottomPxFlow) { keys, imeBottom ->
            keys to imeBottom
        }
            .collect { (_, imeBottom) ->
                val streamingActive = ui.streamingAssistant != null
                if (!streamingActive) return@collect
                if (imeBottom > 0) return@collect
                if (listState.isScrollInProgress) return@collect
                val info = listState.layoutInfo
                val atLazyExtentEnd = !listState.canScrollForward
                val tailRowOnScreen = info.streamingTailVisible()
                if (!atLazyExtentEnd && !tailRowOnScreen) return@collect
                withProgrammaticScroll {
                    listState.scrollLastItemToBottomEdge()
                }
            }
    }

    /** プリセットパネル表示中は入力欄のキーボードを閉じる */
    LaunchedEffect(ui.presetPanelOpen) {
        if (ui.presetPanelOpen) {
            keyboard?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    /** 下端から離れているときのみ表示。ストリーム応答表示中でも手動で末尾へ戻せる（自動追従は別の LaunchedEffect）。 */
    val showScrollToBottom = listCanScrollForward

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

    messageEditTarget?.let { (idx, _) ->
        val sid = session?.id
        AlertDialog(
            onDismissRequest = { messageEditTarget = null },
            title = { Text("メッセージを編集") },
            text = {
                TextField(
                    value = messageEditDraft,
                    onValueChange = { messageEditDraft = it },
                    minLines = 3,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = sid != null,
                    onClick = {
                        if (sid != null) {
                            vm.updateMessageText(sid, idx, messageEditDraft)
                            messageEditTarget = null
                        }
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { messageEditTarget = null }) { Text("キャンセル") }
            },
        )
    }

    messageDeleteIndex?.let { idx ->
        val sid = session?.id
        AlertDialog(
            onDismissRequest = { messageDeleteIndex = null },
            title = { Text("削除の確認") },
            text = { Text("このメッセージを削除しますか？") },
            confirmButton = {
                TextButton(
                    enabled = sid != null,
                    onClick = {
                        if (sid != null) {
                            vm.deleteMessage(sid, idx)
                            messageDeleteIndex = null
                        }
                    },
                ) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { messageDeleteIndex = null }) { Text("キャンセル") }
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
    val footerBg = if (isDark) CodexWebPalette.footerBarDark else CodexWebPalette.footerBarLight
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
                            color = if (isDark) CodexWebPalette.slate200 else CodexWebPalette.brownTitle,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    scope.launch {
                                        withProgrammaticScroll {
                                            listState.scrollToItem(0)
                                        }
                                    }
                                },
                        )
                        EmojiIconButton(
                            emoji = "☁️",
                            isDark = isDark,
                            lightSurface = Color.White,
                            onClick = { vm.drivePull() },
                        )
                        Spacer(Modifier.width(8.dp))
                        EmojiIconButton(
                            emoji = "⚙️",
                            isDark = isDark,
                            lightSurface = Color.White,
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
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        keyboard?.hide()
                                        focusManager.clearFocus(force = true)
                                    },
                                )
                            },
                    ) {
                        ChatPaperBackdrop(isDark = isDark, baseColor = chatBg) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 16.dp,
                                    bottom = listBottomPadding,
                                ),
                                verticalArrangement = Arrangement.spacedBy(32.dp),
                            ) {
                            if (messages.isEmpty() && ui.streamingAssistant == null) {
                                item {
                                    Text(
                                        "ようこそ、白い写本へ。",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (isDark) CodexWebPalette.slate300 else CodexWebPalette.slate500,
                                        modifier = Modifier.padding(start = 4.dp),
                                    )
                                }
                            }
                            itemsIndexed(
                                items = messages,
                                key = { index, msg ->
                                    "${msg.role}-${msg.text.hashCode()}-${msg.attachments.size}-$index"
                                },
                            ) { index, msg ->
                                ChatWebBubble(
                                    msg = msg,
                                    session = session,
                                    isDark = isDark,
                                    actionsEnabled = !ui.sending && session != null,
                                    onPreviewAttachment = { previewAttachment = it },
                                    onEdit = {
                                        messageEditTarget = index to msg.text
                                        messageEditDraft = msg.text
                                    },
                                    onDelete = { messageDeleteIndex = index },
                                    onRetry = if (msg.role == "user" && session != null) {
                                        { vm.regenerateAt(session.id, index) }
                                    } else {
                                        null
                                    },
                                )
                            }
                            if (ui.streamingAssistant != null) {
                                item(key = "streaming_ai") {
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
                                        withProgrammaticScroll {
                                            listState.scrollLastItemToBottomEdge()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 5.dp)
                                    .size(42.dp),
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDark) CodexWebPalette.scrollToBottomFabDark else CodexWebPalette.scrollToBottomFabLight,
                                    contentColor = if (isDark) CodexWebPalette.slate50 else CodexWebPalette.brownTitle,
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isDark) CodexWebPalette.borderFrostLight else CodexWebPalette.borderUserTint,
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
                            color = if (isDark) CodexWebPalette.slate300 else CodexWebPalette.slate600,
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
                                verticalArrangement = Arrangement.spacedBy(3.dp),
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

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp),
                            shape = RoundedCornerShape(28.dp),
                            color = if (isDark) CodexWebPalette.slate900 else CodexWebPalette.composerCapsuleLight,
                            border = BorderStroke(
                                1.dp,
                                if (isDark) CodexWebPalette.dividerSoftDark else CodexWebPalette.borderFrostLight,
                            ),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                            ) {
                                TextField(
                                    value = input,
                                    onValueChange = { input = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomStart)
                                        .padding(start = 48.dp, end = 56.dp)
                                        .heightIn(min = 44.dp, max = 140.dp),
                                    placeholder = { Text("問いを刻む") },
                                    maxLines = 6,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        disabledIndicatorColor = Color.Transparent,
                                        focusedTextColor = if (isDark) CodexWebPalette.slate50 else CodexWebPalette.slate900,
                                        unfocusedTextColor = if (isDark) CodexWebPalette.slate50 else CodexWebPalette.slate900,
                                        focusedPlaceholderColor = if (isDark) CodexWebPalette.slate400 else CodexWebPalette.slate500,
                                        unfocusedPlaceholderColor = if (isDark) CodexWebPalette.slate400 else CodexWebPalette.slate500,
                                    ),
                                )

                                Box(modifier = Modifier.align(Alignment.BottomStart)) {
                                    AttachPlusButton(
                                        isDark = isDark,
                                        onClick = { attachMenu = true },
                                        modifier = Modifier.offset(y = (-6).dp),
                                    )
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

                                Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                                    SendGlyphButton(
                                        sending = ui.sending,
                                        isDark = isDark,
                                        modifier = Modifier.offset(y = (-6).dp),
                                        onClick = {
                                            if (ui.sending) vm.stopGeneration()
                                            else {
                                                keyboard?.hide()
                                                focusManager.clearFocus(force = true)
                                                vm.sendUserMessage(input)
                                                input = ""
                                            }
                                        },
                                    )
                                }
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
                                if (isDark) CodexWebPalette.presetChromeBorderDark else CodexWebPalette.presetChromeBorderLight,
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
                                verticalArrangement = Arrangement.spacedBy(3.dp),
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
                                            focusedContainerColor = CodexWebPalette.searchFieldBg,
                                            unfocusedContainerColor = CodexWebPalette.searchFieldBg,
                                            focusedTextColor = CodexWebPalette.slate50,
                                            unfocusedTextColor = CodexWebPalette.slate50,
                                            focusedPlaceholderColor = CodexWebPalette.slate400,
                                            unfocusedPlaceholderColor = CodexWebPalette.slate400,
                                            focusedBorderColor = CodexWebPalette.searchFieldOutline,
                                            unfocusedBorderColor = CodexWebPalette.searchFieldOutline,
                                        ),
                                    )
                                }
                                PresetSidebarGroup(title = "カスタムプリセット", isDark = isDark) {
                                    val custom = ui.snapshot.personas.sortedByDescending { it.pinned }
                                    if (custom.isEmpty()) {
                                        Text(
                                            "項目がありません",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isDark) CodexWebPalette.slate400 else CodexWebPalette.brownSoft,
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
                                            color = if (isDark) CodexWebPalette.slate400 else CodexWebPalette.brownSoft,
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
                                .background(CodexWebPalette.drawerBackdrop)
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

    previewAttachment?.let { attachment ->
        AttachmentPreviewDialog(
            attachment = attachment,
            isDark = isDark,
            onDismiss = { previewAttachment = null },
        )
    }
}

@Composable
private fun PresetSidebarGroup(
    title: String,
    isDark: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by remember { mutableStateOf(true) }
    val border = if (isDark) CodexWebPalette.drawerSessionBorder else CodexWebPalette.sidebarGroupBorder
    val bg = if (isDark) CodexWebPalette.drawerSessionBgDark else CodexWebPalette.drawerSessionBgLight
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
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) CodexWebPalette.slate50 else CodexWebPalette.captionBrown,
            )
            Text(
                if (open) "▾" else "▸",
                color = if (isDark) CodexWebPalette.slate50 else CodexWebPalette.captionBrown,
            )
        }
        if (open) {
            Column(
                Modifier.padding(start = 4.dp, end = 4.dp, bottom = 3.dp),
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
    /** ライトテーマ時の面色（ヘッダー等は白、既定はすりガラス風） */
    lightSurface: Color = CodexWebPalette.iconButtonSurfaceLight,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isDark) CodexWebPalette.iconButtonSurfaceDark else lightSurface,
        border = BorderStroke(1.dp, if (isDark) CodexWebPalette.slate600 else CodexWebPalette.emojiBtnBorder),
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
            open && isDark -> CodexWebPalette.slate700
            open -> CodexWebPalette.userBorder
            isDark -> CodexWebPalette.iconButtonSurfaceDark
            else -> Color.White
        },
        border = BorderStroke(1.dp, if (isDark) CodexWebPalette.slate600 else CodexWebPalette.footerBorder),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text("📜", fontSize = 20.sp, color = if (open) Color.White else Color.Unspecified)
        }
    }
}

@Composable
private fun AttachPlusButton(
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(44.dp),
        shape = CircleShape,
        color = if (isDark) CodexWebPalette.iconButtonSurfaceDark else CodexWebPalette.iconButtonSurfaceLightMedium,
        border = BorderStroke(1.dp, if (isDark) CodexWebPalette.slate600 else CodexWebPalette.emojiBtnBorder),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text("＋", fontSize = 22.sp, color = if (isDark) CodexWebPalette.slate50 else CodexWebPalette.brownTitle)
        }
    }
}

@Composable
private fun SendGlyphButton(
    sending: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(44.dp),
        shape = CircleShape,
        color = CodexWebPalette.sendFabSurface(isDark),
        shadowElevation = 6.dp,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                if (sending) "□" else "🖋️",
                fontSize = if (sending) 22.sp else 20.sp,
                color = CodexWebPalette.sendFabGlyph(isDark),
            )
        }
    }
}

@Composable
private fun ChatWebBubble(
    msg: ChatMessage,
    session: ChatSession?,
    isDark: Boolean,
    actionsEnabled: Boolean,
    onPreviewAttachment: (MessageAttachment) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRetry: (() -> Unit)?,
) {
    val isUser = msg.role == "user"
    val sig = session?.userSignature?.takeIf { it.isNotBlank() } ?: "Blanche"
    val labelColor = if (isDark) CodexWebPalette.slate50 else CodexWebPalette.captionBrown
    val lineColor = if (isDark) CodexWebPalette.slate400 else CodexWebPalette.userBorder
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Text(
                text = if (isUser) "✦ $sig" else "✦ Codex",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = labelColor,
                letterSpacing = 0.1.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        if (isUser) {
            if (msg.attachments.isNotEmpty()) {
                FlowAttachmentChips(
                    msg = msg,
                    userAlignRight = false,
                    isDark = isDark,
                    onPreviewAttachment = onPreviewAttachment,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.Start,
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
                        color = if (isDark) CodexWebPalette.slate100 else CodexWebPalette.userMsg,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        } else {
            Text(
                text = msg.text,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp),
                color = if (isDark) CodexWebPalette.slate300 else CodexWebPalette.aiMsg,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp),
            )
        }
        if (!isUser && msg.attachments.isNotEmpty()) {
            FlowAttachmentChips(
                msg = msg,
                userAlignRight = false,
                isDark = isDark,
                onPreviewAttachment = onPreviewAttachment,
            )
        }
        Row(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .padding(start = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            TextButton(
                onClick = onEdit,
                enabled = actionsEnabled,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("編集", style = MaterialTheme.typography.labelMedium)
            }
            TextButton(
                onClick = onDelete,
                enabled = actionsEnabled,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("削除", style = MaterialTheme.typography.labelMedium)
            }
            if (isUser && onRetry != null) {
                TextButton(
                    onClick = onRetry,
                    enabled = actionsEnabled,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text("やり直し", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun FlowAttachmentChips(
    msg: ChatMessage,
    userAlignRight: Boolean,
    isDark: Boolean,
    onPreviewAttachment: (MessageAttachment) -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(top = 4.dp, bottom = 8.dp)
            .horizontalScroll(rememberScrollState())
            .fillMaxWidth(),
        horizontalArrangement = if (userAlignRight) Arrangement.End else Arrangement.Start,
    ) {
        msg.attachments.forEach { a ->
            val isImage = a.type == "image" && !a.dataUrl.isNullOrBlank()
            Surface(
                onClick = { onPreviewAttachment(a) },
                shape = RoundedCornerShape(10.dp),
                color = if (isDark) CodexWebPalette.attachmentChipBgDark else CodexWebPalette.attachmentChipBgLight,
                border = BorderStroke(1.dp, if (isDark) CodexWebPalette.attachmentChipBorderDark else CodexWebPalette.fileChipBorder),
                modifier = Modifier.padding(4.dp),
            ) {
                if (isImage) {
                    DataUrlImage(
                        dataUrl = a.dataUrl,
                        contentDescription = a.name ?: "image",
                        modifier = Modifier
                            .size(width = 92.dp, height = 64.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .widthIn(min = 100.dp, max = 180.dp)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "📎 ${a.name ?: "file"}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = a.mimeType ?: "不明な形式",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDark) CodexWebPalette.slate400 else CodexWebPalette.slate500,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreviewDialog(
    attachment: MessageAttachment,
    isDark: Boolean,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isDark) CodexWebPalette.slate900 else Color.White,
            border = BorderStroke(
                1.dp,
                if (isDark) CodexWebPalette.imageCardScrimDark else CodexWebPalette.imageCardScrimLight,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = attachment.name ?: "添付ファイル",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDark) CodexWebPalette.slate50 else CodexWebPalette.slate900,
                )
                if (attachment.type == "image" && !attachment.dataUrl.isNullOrBlank()) {
                    DataUrlImage(
                        dataUrl = attachment.dataUrl,
                        contentDescription = attachment.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 420.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDark) CodexWebPalette.sessionThumbBgDark else CodexWebPalette.slate50,
                        border = BorderStroke(
                            1.dp,
                            if (isDark) CodexWebPalette.dividerSoftDark else CodexWebPalette.imageCardScrimLight,
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("ファイル形式: ${attachment.mimeType ?: "不明"}")
                            Text("サイズ: ${if (attachment.size > 0) "${attachment.size} bytes" else "不明"}")
                            Text("内容添付: ${if (attachment.contentIncluded) "あり" else "メタデータのみ"}")
                            if (!attachment.previewText.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isDark) CodexWebPalette.sessionListActiveDark else CodexWebPalette.slate100,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isDark) CodexWebPalette.dividerSoftDark else CodexWebPalette.imageCardScrimLight,
                                    ),
                                ) {
                                    val scroll = rememberScrollState()
                                    SelectionContainer {
                                        Text(
                                            text = attachment.previewText,
                                            modifier = Modifier
                                                .heightIn(max = 320.dp)
                                                .verticalScroll(scroll)
                                                .padding(10.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isDark) CodexWebPalette.slate200 else CodexWebPalette.slate900,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("閉じる") }
                }
            }
        }
    }
}

@Composable
private fun DataUrlImage(
    dataUrl: String?,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
) {
    val bitmap = remember(dataUrl) { decodeBase64Image(dataUrl) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        AsyncImage(
            model = dataUrl,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}

private fun decodeBase64Image(dataUrl: String?): android.graphics.Bitmap? {
    if (dataUrl.isNullOrBlank()) return null
    val comma = dataUrl.indexOf(',')
    if (comma <= 0) return null
    val payload = dataUrl.substring(comma + 1)
    return runCatching {
        val bytes = Base64.decode(payload, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

@Composable
private fun StreamingAiBubble(text: String, isDark: Boolean) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Text(
                "✦ Codex",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (isDark) CodexWebPalette.slate50 else CodexWebPalette.captionBrown,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp),
            color = if (isDark) CodexWebPalette.slate300 else CodexWebPalette.aiMsg,
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
                .border(1.dp, CodexWebPalette.borderFrostLight, RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
        Surface(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(4.dp, (-4).dp)
                .size(20.dp),
            shape = CircleShape,
            color = CodexWebPalette.scrimHeavy,
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
            .background(if (isDark) CodexWebPalette.attachmentChipBgDark else CodexWebPalette.fileChipBg)
            .border(1.dp, if (isDark) CodexWebPalette.attachmentChipBorderDark else CodexWebPalette.fileChipBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
                            color = if (isDark) CodexWebPalette.slate200 else CodexWebPalette.brownTitle,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Surface(
            onClick = onRemove,
            shape = CircleShape,
            color = CodexWebPalette.scrimHeavy,
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
            .padding(vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
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
                    .padding(horizontal = 2.dp, vertical = 1.dp),
                color = if (isDark) CodexWebPalette.slate300 else CodexWebPalette.brownSoft,
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
            .padding(vertical = 0.dp),
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
                    .padding(horizontal = 2.dp, vertical = 1.dp),
                color = if (isDark) CodexWebPalette.slate300 else CodexWebPalette.brownSoft,
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
            active && isDark -> CodexWebPalette.accentBlue
            active -> CodexWebPalette.userBorder
            isDark -> CodexWebPalette.tabInactiveDarkTint
            else -> CodexWebPalette.drawerSessionBgLight
        },
        border = BorderStroke(
            1.dp,
            when {
                active && isDark -> CodexWebPalette.accentBlue
                active -> CodexWebPalette.userBorder
                isDark -> CodexWebPalette.slate600
                else -> CodexWebPalette.footerBorder
            },
        ),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 17.sp),
            color = when {
                active -> Color.White
                isDark -> CodexWebPalette.slate50
                else -> CodexWebPalette.brownTitle
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}


/** 応答ストリーム用の末尾 Lazy 行が、この瞬間の visible リストに載っている（画面上に出ている）。 */
private fun LazyListLayoutInfo.streamingTailVisible(): Boolean {
    val lastIdx = totalItemsCount - 1
    if (lastIdx < 0 || visibleItemsInfo.isEmpty()) return false
    return visibleItemsInfo.maxOf { it.index } == lastIdx
}

/**
 * ユーザー送信直後、確定メッセージ件数＋ストリーム行ぶんの Lazy 行が揃ってから末尾へ寄せる。
 */
private suspend fun LazyListState.scrollToTailAfterUserMessage(
    persistedMessageCount: Int,
) {
    val minLazyItemCount = persistedMessageCount + 1
    repeat(40) {
        coroutineContext.ensureActive()
        if (layoutInfo.totalItemsCount >= minLazyItemCount) {
            scrollLastItemToBottomEdge()
            return
        }
        withFrameNanos { }
    }
}

/**
 * 先頭で [scrollToItem] するとキャンセル時に AI 吹き出し先頭へ張り付くので、
 * まずは [scroll] のみで下端に寄せ、最終行がまだ可視範囲にないときだけ [scrollToItem]。
 * 計測の丸めで数 px 残ることがあるので、最後に [canScrollForward] が false になるまで微調整する。
 */
private suspend fun LazyListState.scrollLastItemToBottomEdge() {
    repeat(24) {
        coroutineContext.ensureActive()
        val info = layoutInfo
        val count = info.totalItemsCount
        if (count <= 0) return
        val lastIndex = count - 1
        val item =
            info.visibleItemsInfo.maxByOrNull { it.index }
                ?.takeIf { it.index == lastIndex }
        if (item != null) {
            val gap = (item.offset + item.size - info.viewportEndOffset).toFloat()
            when {
                gap > 4f -> scroll { scrollBy(gap) }
                gap > 0f -> scroll { scrollBy(gap.coerceAtLeast(2f)) }
            }
            delay(8)
            if (!canScrollForward) {
                val snap = layoutInfo
                val lip =
                    snap.visibleItemsInfo.maxByOrNull { it.index }
                        ?.takeIf { it.index == lastIndex }
                val gapAfter =
                    lip?.let { (it.offset + it.size - snap.viewportEndOffset).toFloat() } ?: gap
                if (gapAfter > 8f) scrollToItem(lastIndex)
                return
            }
        } else {
            val maxVis = info.visibleItemsInfo.maxOfOrNull { it.index } ?: -1
            when {
                maxVis < lastIndex -> {
                    scrollToItem(lastIndex)
                }
                else -> scroll { scrollBy(96f) }
            }
            delay(12)
        }
    }
    repeat(24) {
        coroutineContext.ensureActive()
        if (!canScrollForward) return
        scroll { scrollBy(8f) }
        delay(4)
    }
}

private fun readUriText(context: android.content.Context, uri: Uri): String? =
    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
