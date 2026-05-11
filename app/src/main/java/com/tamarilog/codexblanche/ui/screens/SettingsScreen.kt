package com.tamarilog.codexblanche.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.tamarilog.codexblanche.ui.theme.CodexWebPalette
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.tamarilog.codexblanche.ChatViewModel
import com.tamarilog.codexblanche.util.AppInstallerDigest
import com.tamarilog.codexblanche.data.CodexJson
import com.tamarilog.codexblanche.data.ConversationWorldExtract
import com.tamarilog.codexblanche.data.model.AppSettings
import com.tamarilog.codexblanche.data.model.ModelOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class SettingsDest(val id: String) {
    Root("settings-view-root"),
    Account("view-account"),
    AiRoot("view-ai-root"),
    NewSessionModel("view-new-session-model"),
    AiModel("view-ai-model"),
    AiBehavior("view-ai-behavior"),
    AiContext("view-ai-context"),
    Display("view-display"),
    AdvancedRoot("view-advanced-root"),
    DevRoot("view-dev-root"),
    DevLogs("view-dev-logs"),
    DevConvExport("view-dev-conversation-export"),
    DevConvHistoryOnly("view-dev-conversation-history-only-export"),
}

private fun SettingsDest.title(): String = when (this) {
    SettingsDest.Root -> "設定"
    SettingsDest.Account -> "アカウント / 連携"
    SettingsDest.AiRoot -> "カスタムプリセット作成"
    SettingsDest.NewSessionModel -> "新規会話時のモデル設定"
    SettingsDest.AiModel -> "モデル設定"
    SettingsDest.AiBehavior -> "振る舞い設定画面"
    SettingsDest.AiContext -> "コンテキスト / 署名設定画面"
    SettingsDest.Display -> "表示設定"
    SettingsDest.AdvancedRoot -> "詳細設定"
    SettingsDest.DevRoot -> "開発者向け"
    SettingsDest.DevLogs -> "ログ"
    SettingsDest.DevConvExport -> "会話JSON取り出し"
    SettingsDest.DevConvHistoryOnly -> "世界設定なし会話JSON取り出し"
}

private object SettingsUi {
    val screenBgLight = CodexWebPalette.settingsBgLight
    val screenBgDark = CodexWebPalette.chatAreaDark
    val headerDividerLight = CodexWebPalette.slate300
    val headerDividerDark = CodexWebPalette.slate700
    val titleLight = CodexWebPalette.slate800
    val titleDark = CodexWebPalette.slate50
    val h2Light = CodexWebPalette.slate900
    val h2Dark = Color.White
    val navBorderLight = CodexWebPalette.slate300
    val navBorderDark = CodexWebPalette.slate600
    val navBgLight = Color.White
    val navBgDark = CodexWebPalette.slate800
    val navTextLight = CodexWebPalette.slate900
    val navTextDark = CodexWebPalette.slate50
    val secondaryNavBg = CodexWebPalette.slate800
    val secondaryNavFg = Color.White
    val backBtnBgLight = CodexWebPalette.slate200
    val backBtnBgDark = CodexWebPalette.slate700
    val backBtnFgLight = CodexWebPalette.slate900
    val backBtnFgDark = CodexWebPalette.slate50
    val hint = CodexWebPalette.slate500
    val hintDark = CodexWebPalette.slate300
    val emeraldBtn = Color(0xFF047857)
    val neutralBtn = CodexWebPalette.slate500
    val indigoBtn = Color(0xFF4F46E5)
    val chipBgDark = CodexWebPalette.slate700
    val chipBgLight = CodexWebPalette.slate200
    val chipBgLightAlt = CodexWebPalette.slate100
    val mutedActionBtn = CodexWebPalette.slate600
    val amberLight = Color(0xFFB45309)
    val amberDark = Color(0xFFFCD34D)
}

private fun contextTokenLimit(provider: String): Int =
    if (provider == "openai") 5000 else 15000

@Composable
fun SettingsScreen(
    vm: ChatViewModel,
    onBack: () -> Unit,
) {
    val ui by vm.uiState.collectAsState()
    var draft by remember { mutableStateOf(ui.snapshot.settings) }
    LaunchedEffect(ui.snapshot.settings) {
        draft = ui.snapshot.settings
    }

    val isDark = when (draft.theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val screenBg = if (isDark) SettingsUi.screenBgDark else SettingsUi.screenBgLight
    val headerDivider = if (isDark) SettingsUi.headerDividerDark else SettingsUi.headerDividerLight
    val h2Color = if (isDark) SettingsUi.h2Dark else SettingsUi.h2Light

    val stack = remember { mutableStateListOf(SettingsDest.Root) }
    val current = stack.last()

    fun persistAndExit() {
        vm.updateSettings(draft)
        onBack()
    }

    fun popNav() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex) else persistAndExit()
    }

    BackHandler {
        if (stack.size > 1) stack.removeAt(stack.lastIndex) else persistAndExit()
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val act = context as? Activity
        val data = result.data
        if (data != null) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                vm.onGoogleSignedIn(account, act ?: context)
                return@rememberLauncherForActivityResult
            } catch (e: ApiException) {
                val hint = when (e.statusCode) {
                CommonStatusCodes.DEVELOPER_ERROR ->
                    "GCP「認証情報」の Android 用 OAuth に、下の「GCP用…コピー」と同じパッケージ名・SHA-1を登録してください（プロジェクト違い・タイプ違いに注意）。"
                    CommonStatusCodes.NETWORK_ERROR -> "ネットワークを確認してください。"
                    CommonStatusCodes.SIGN_IN_REQUIRED -> "もう一度サインインしてください。"
                    12501 -> "サインインがキャンセルされました。"
                    else -> e.message?.takeIf { it.isNotBlank() }.orEmpty()
                }
                val suffix = if (hint.isNotEmpty()) "\n$hint" else ""
                Toast.makeText(
                    context,
                    "Googleサインイン失敗（コード ${e.statusCode}）$suffix",
                    Toast.LENGTH_LONG,
                ).show()
                vm.onGoogleSignedIn(null)
                return@rememberLauncherForActivityResult
            }
        }
        if (result.resultCode != Activity.RESULT_OK) {
            Toast.makeText(
                context,
                "Googleサインインが完了しませんでした（result=${result.resultCode}）。\n" +
                    "コード10のときは設定の「GCP用…コピー」で SHA-1 がコンソールと一致するか確認してください。",
                Toast.LENGTH_LONG,
            ).show()
        }
        vm.onGoogleSignedIn(null)
    }

    var personaNameDraft by remember { mutableStateOf("") }

    var devConvUri by remember { mutableStateOf<Uri?>(null) }
    var devConvName by remember { mutableStateOf("") }
    var devConvStatus by remember { mutableStateOf("未実行") }
    val pickDevConv = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        devConvUri = uri
        devConvName = uri?.lastPathSegment?.substringAfterLast(':')?.ifBlank { "source.json" } ?: "source.json"
        devConvStatus = if (uri != null) "選択中: $devConvName" else "未実行"
    }

    var devHistUri by remember { mutableStateOf<Uri?>(null) }
    var devHistName by remember { mutableStateOf("") }
    var devHistStatus by remember { mutableStateOf("未実行") }
    val pickDevHist = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        devHistUri = uri
        devHistName = uri?.lastPathSegment?.substringAfterLast(':')?.ifBlank { "source.json" } ?: "source.json"
        devHistStatus = if (uri != null) "選択中: $devHistName" else "未実行"
    }

    Column(Modifier.fillMaxSize().background(screenBg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (stack.size > 1) {
                Button(
                    onClick = { popNav() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDark) SettingsUi.backBtnBgDark else SettingsUi.backBtnBgLight,
                        contentColor = if (isDark) SettingsUi.backBtnFgDark else SettingsUi.backBtnFgLight,
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text("← 戻る", fontWeight = FontWeight.Bold)
                }
            }
            Text(
                current.title(),
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = h2Color,
            )
        }
        HorizontalDivider(thickness = 1.dp, color = headerDivider)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (current) {
                SettingsDest.Root -> SettingsRoot(
                    isDark = isDark,
                    onPick = { dest ->
                        stack.add(dest)
                    },
                    onNewChat = {
                        vm.updateSettings(draft)
                        vm.newSession()
                        onBack()
                    },
                    onReturnToLibrary = { persistAndExit() },
                )

                SettingsDest.Account -> SettingsAccount(
                    isDark = isDark,
                    draft = draft,
                    onDraft = { draft = it },
                    driveStatus = ui.driveStatus,
                    onCopyGcpAndroidOAuth = {
                        val sha = AppInstallerDigest.sha1ColonUpper(context) ?: "（取得失敗）"
                        val text = "${context.packageName}\n$sha"
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("GCP Android OAuth", text))
                        Toast.makeText(
                            context,
                            "パッケージ名とSHA-1をコピーしました。GCPの「Android」OAuthクライアントに同じ値を登録してください。",
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                    onGoogleConnect = {
                        googleLauncher.launch(vm.googleSignInClient().signInIntent)
                    },
                    onGoogleDisconnect = { vm.googleSignOut() },
                )

                SettingsDest.AiRoot -> SettingsAiRoot(
                    isDark = isDark,
                    draft = draft,
                    onDraft = { draft = it },
                    personaNameDraft = personaNameDraft,
                    onPersonaName = { personaNameDraft = it },
                    onSavePersona = {
                        vm.saveCustomPersona(personaNameDraft, draft)
                        personaNameDraft = ""
                        Toast.makeText(context, "プリセットを保存しました", Toast.LENGTH_SHORT).show()
                    },
                    onOpen = { stack.add(it) },
                )

                SettingsDest.NewSessionModel -> SettingsNewSessionModel(isDark = isDark, draft = draft, onDraft = { draft = it })
                SettingsDest.AiModel -> SettingsAiModel(isDark = isDark, draft = draft, onDraft = { draft = it })
                SettingsDest.AiBehavior -> SettingsAiBehavior(isDark = isDark, draft = draft, onDraft = { draft = it })
                SettingsDest.AiContext -> SettingsAiContext(isDark = isDark, draft = draft, onDraft = { draft = it })

                SettingsDest.Display -> SettingsDisplay(
                    isDark = isDark,
                    resolvedDark = isDark,
                    draft = draft,
                    onToggleTheme = { vm.toggleTheme() },
                    onDraft = { draft = it },
                )

                SettingsDest.AdvancedRoot -> SettingsAdvancedRoot(isDark = isDark) { stack.add(it) }

                SettingsDest.DevRoot -> SettingsDevRoot(isDark = isDark) { stack.add(it) }

                SettingsDest.DevLogs -> SettingsDevLogs(
                    isDark = isDark,
                    logs = ui.devLogs,
                    onNewSession = { vm.newSession() },
                )

                SettingsDest.DevConvExport -> SettingsDevConvExport(
                    isDark = isDark,
                    status = devConvStatus,
                    onPick = { pickDevConv.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    onRun = {
                        val uri = devConvUri
                        if (uri == null) {
                            Toast.makeText(context, "先にJSONファイルを選択してください。", Toast.LENGTH_SHORT).show()
                            return@SettingsDevConvExport
                        }
                        scope.launch {
                            val text = withContext(Dispatchers.IO) { readUriText(context, uri) }
                            if (text.isNullOrBlank()) {
                                devConvStatus = "抽出失敗: ファイルを読み取れません"
                                return@launch
                            }
                            val root = runCatching { CodexJson.parseToJsonElement(text) }.getOrNull()
                            if (root == null) {
                                devConvStatus = "抽出失敗: JSONを解析できません"
                                return@launch
                            }
                            val groups = ConversationWorldExtract.extractFullByWorld(root)
                            if (groups.isEmpty()) {
                                devConvStatus = "会話形式JSONを検出できませんでした。"
                                return@launch
                            }
                            val (count, dir) = ConversationWorldExtract.writeGroupedWorldExports(
                                context,
                                groups,
                                devConvName.ifBlank { "source.json" },
                                historyOnly = false,
                            )
                            devConvStatus = "${count}件の世界設定ごとにJSONを分けて保存しました。"
                            if (dir != null) {
                                Toast.makeText(
                                    context,
                                    "保存先: ${dir.absolutePath}",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                )

                SettingsDest.DevConvHistoryOnly -> SettingsDevConvHistoryOnly(
                    isDark = isDark,
                    status = devHistStatus,
                    onPick = { pickDevHist.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    onRun = {
                        val uri = devHistUri
                        if (uri == null) {
                            Toast.makeText(context, "先にJSONファイルを選択してください。", Toast.LENGTH_SHORT).show()
                            return@SettingsDevConvHistoryOnly
                        }
                        scope.launch {
                            val text = withContext(Dispatchers.IO) { readUriText(context, uri) }
                            if (text.isNullOrBlank()) {
                                devHistStatus = "抽出失敗: ファイルを読み取れません"
                                return@launch
                            }
                            val root = runCatching { CodexJson.parseToJsonElement(text) }.getOrNull()
                            if (root == null) {
                                devHistStatus = "抽出失敗: JSONを解析できません"
                                return@launch
                            }
                            val groups = ConversationWorldExtract.extractHistoryOnlyByWorld(root)
                            if (groups.isEmpty()) {
                                devHistStatus = "会話履歴を検出できませんでした。"
                                return@launch
                            }
                            val (count, dir) = ConversationWorldExtract.writeGroupedWorldExports(
                                context,
                                groups,
                                devHistName.ifBlank { "source.json" },
                                historyOnly = true,
                            )
                            devHistStatus = "${count}件の世界設定ごとに会話履歴を分けて保存しました。"
                            if (dir != null) {
                                Toast.makeText(
                                    context,
                                    "保存先: ${dir.absolutePath}",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsRoot(
    isDark: Boolean,
    onPick: (SettingsDest) -> Unit,
    onNewChat: () -> Unit,
    onReturnToLibrary: () -> Unit,
) {
    SettingsNavRow(isDark, primary = true, onClick = { onPick(SettingsDest.Account) }) {
        Text("アカウント / 連携", fontWeight = FontWeight.Bold)
    }
    SettingsNavRow(isDark, primary = true, onClick = { onPick(SettingsDest.AiRoot) }) {
        Text("カスタムプリセット作成", fontWeight = FontWeight.Bold)
    }
    SettingsNavRow(isDark, primary = true, onClick = { onPick(SettingsDest.Display) }) {
        Text("表示設定", fontWeight = FontWeight.Bold)
    }
    SettingsNavRow(isDark, primary = true, onClick = { onPick(SettingsDest.AdvancedRoot) }) {
        Text("詳細設定", fontWeight = FontWeight.Bold)
    }
    SettingsNavRow(isDark, primary = true, onClick = { onPick(SettingsDest.NewSessionModel) }) {
        Text("新規会話時のモデル設定", fontWeight = FontWeight.Bold)
    }
    SettingsNavRow(isDark, primary = true, onClick = onNewChat) {
        Text("新しい会話を開始", fontWeight = FontWeight.Bold)
    }
    SettingsNavRow(isDark, primary = false, onClick = onReturnToLibrary) {
        Text("書庫に戻る", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsAccount(
    isDark: Boolean,
    draft: AppSettings,
    onDraft: (AppSettings) -> Unit,
    driveStatus: String,
    onCopyGcpAndroidOAuth: () -> Unit,
    onGoogleConnect: () -> Unit,
    onGoogleDisconnect: () -> Unit,
) {
    SettingsSectionTitle(isDark, "API連携")
    OutlinedTextField(
        value = draft.geminiApiKey,
        onValueChange = { onDraft(draft.copy(geminiApiKey = it)) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Gemini API Key") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        shape = RoundedCornerShape(12.dp),
    )
    OutlinedTextField(
        value = draft.openaiApiKey,
        onValueChange = { onDraft(draft.copy(openaiApiKey = it)) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("OpenAI API Key") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        shape = RoundedCornerShape(12.dp),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = draft.rememberApiKeys,
            onCheckedChange = { onDraft(draft.copy(rememberApiKeys = it)) },
        )
        Text(
            "APIキーをこの端末に保存する（localStorage）",
            style = MaterialTheme.typography.bodySmall,
            color = if (isDark) SettingsUi.navTextDark else SettingsUi.navTextLight,
        )
    }

    SettingsSectionTitle(isDark, "Google連携")
    Text(
        "Google サインイン / Drive 用。コード10のときは GCP の Android OAuth と、この端末の SHA-1 が一致しているか確認してください。",
        style = MaterialTheme.typography.bodySmall,
        color = if (isDark) SettingsUi.hintDark else SettingsUi.hint,
    )
    Button(
        onClick = onCopyGcpAndroidOAuth,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isDark) SettingsUi.navBgDark else SettingsUi.navBgLight,
            contentColor = if (isDark) SettingsUi.navTextDark else SettingsUi.navTextLight,
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Text("GCP用: パッケージ名と SHA-1 をコピー", fontWeight = FontWeight.Bold)
    }
    OutlinedTextField(
        value = draft.driveFolderName,
        onValueChange = { onDraft(draft.copy(driveFolderName = it)) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Driveフォルダ名（既定: CodexBlanche）") },
        shape = RoundedCornerShape(12.dp),
    )
    OutlinedTextField(
        value = draft.driveFileName,
        onValueChange = { onDraft(draft.copy(driveFileName = it)) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Driveファイル名（既定: codex_data.json）") },
        shape = RoundedCornerShape(12.dp),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onGoogleConnect,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = SettingsUi.emeraldBtn, contentColor = Color.White),
            shape = RoundedCornerShape(10.dp),
        ) { Text("Google接続", fontWeight = FontWeight.Bold) }
        Button(
            onClick = onGoogleDisconnect,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = SettingsUi.neutralBtn, contentColor = Color.White),
            shape = RoundedCornerShape(10.dp),
        ) { Text("接続解除", fontWeight = FontWeight.Bold) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = draft.rememberGoogleLogin,
            onCheckedChange = { onDraft(draft.copy(rememberGoogleLogin = it)) },
        )
        Text(
            "この端末でログインしたままにする",
            style = MaterialTheme.typography.bodySmall,
            color = if (isDark) SettingsUi.navTextDark else SettingsUi.navTextLight,
        )
    }
    Text(
        driveStatus,
        style = MaterialTheme.typography.bodySmall,
        color = if (isDark) SettingsUi.hintDark else SettingsUi.hint,
    )
}

@Composable
private fun SettingsAiRoot(
    isDark: Boolean,
    draft: AppSettings,
    onDraft: (AppSettings) -> Unit,
    personaNameDraft: String,
    onPersonaName: (String) -> Unit,
    onSavePersona: () -> Unit,
    onOpen: (SettingsDest) -> Unit,
) {
    SettingsNavRow(isDark, primary = true, onClick = { onOpen(SettingsDest.AiModel) }) {
        Text("モデル設定", fontWeight = FontWeight.Bold)
    }
    SettingsNavRow(isDark, primary = true, onClick = { onOpen(SettingsDest.AiBehavior) }) {
        Text("振る舞い（プロンプト・温度）設定画面", fontWeight = FontWeight.Bold)
    }
    SettingsNavRow(isDark, primary = true, onClick = { onOpen(SettingsDest.AiContext) }) {
        Text("コンテキスト / 署名設定画面", fontWeight = FontWeight.Bold)
    }
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = draft.allowGeminiSearch,
                    onCheckedChange = { onDraft(draft.copy(allowGeminiSearch = it)) },
                )
                Text(
                    "Gemini の検索を許可",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) SettingsUi.navTextDark else SettingsUi.navTextLight,
                )
            }
            Text(
                "APIの無料枠では使用できません",
                style = MaterialTheme.typography.bodySmall,
                color = if (isDark) SettingsUi.amberDark else SettingsUi.amberLight,
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = draft.allowOpenaiSearch,
                    onCheckedChange = { onDraft(draft.copy(allowOpenaiSearch = it)) },
                )
                Text(
                    "OpenAI の検索を許可",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) SettingsUi.navTextDark else SettingsUi.navTextLight,
                )
            }
        }
    }
    OutlinedTextField(
        value = personaNameDraft,
        onValueChange = onPersonaName,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("保存するプリセット名") },
        shape = RoundedCornerShape(12.dp),
    )
    Button(
        onClick = onSavePersona,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = SettingsUi.indigoBtn, contentColor = Color.White),
        shape = RoundedCornerShape(10.dp),
    ) { Text("プリセット保存", fontWeight = FontWeight.Bold) }
}

@Composable
private fun SettingsNewSessionModel(
    isDark: Boolean,
    draft: AppSettings,
    onDraft: (AppSettings) -> Unit,
) {
    SettingsSectionTitle(isDark, "新規会話時のモデル設定")
    Text(
        "「新しい会話を開始」時に使うモデルを設定します。",
        style = MaterialTheme.typography.bodySmall,
        color = if (isDark) SettingsUi.hintDark else SettingsUi.hint,
    )
    var provMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    val nsProvider = draft.newSessionProvider.ifBlank { draft.provider }
    val modelPairs = if (nsProvider == "openai") ModelOptions.openai else ModelOptions.gemini
    val nsModel = if (nsProvider == "openai") draft.newSessionOpenaiModel else draft.newSessionGeminiModel

    SettingsSelectButton(
        isDark = isDark,
        label = if (nsProvider == "openai") "ChatGPT (OpenAI)" else "Gemini",
        expanded = provMenu,
        onExpand = { provMenu = true },
        onDismiss = { provMenu = false },
    ) {
        DropdownMenuItem(
            text = { Text("Gemini") },
            onClick = {
                onDraft(draft.copy(newSessionProvider = "gemini"))
                provMenu = false
            },
        )
        DropdownMenuItem(
            text = { Text("ChatGPT (OpenAI)") },
            onClick = {
                onDraft(draft.copy(newSessionProvider = "openai"))
                provMenu = false
            },
        )
    }
    val modelLabel = modelPairs.find { it.first == nsModel }?.second ?: nsModel
    SettingsSelectButton(
        isDark = isDark,
        label = modelLabel,
        expanded = modelMenu,
        onExpand = { modelMenu = true },
        onDismiss = { modelMenu = false },
    ) {
        modelPairs.forEach { (value, name) ->
            DropdownMenuItem(
                text = { Text(name) },
                onClick = {
                    onDraft(
                        if (nsProvider == "openai") {
                            draft.copy(newSessionOpenaiModel = value)
                        } else {
                            draft.copy(newSessionGeminiModel = value)
                        },
                    )
                    modelMenu = false
                },
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = draft.newSessionAllowGeminiSearch,
            onCheckedChange = { onDraft(draft.copy(newSessionAllowGeminiSearch = it)) },
        )
        Text(
            "新規会話で Gemini の検索を許可",
            style = MaterialTheme.typography.bodySmall,
            color = if (isDark) SettingsUi.navTextDark else SettingsUi.navTextLight,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = draft.newSessionAllowOpenaiSearch,
            onCheckedChange = { onDraft(draft.copy(newSessionAllowOpenaiSearch = it)) },
        )
        Text(
            "新規会話で OpenAI の検索を許可",
            style = MaterialTheme.typography.bodySmall,
            color = if (isDark) SettingsUi.navTextDark else SettingsUi.navTextLight,
        )
    }
}

@Composable
private fun SettingsAiModel(
    isDark: Boolean,
    draft: AppSettings,
    onDraft: (AppSettings) -> Unit,
) {
    SettingsSectionTitle(isDark, "モデル設定")
    var provMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var thinkMenu by remember { mutableStateOf(false) }
    val modelPairs = if (draft.provider == "openai") ModelOptions.openai else ModelOptions.gemini
    val currentModel = if (draft.provider == "openai") draft.openaiModel else draft.geminiModel

    SettingsSelectButton(
        isDark = isDark,
        label = if (draft.provider == "openai") "ChatGPT (OpenAI)" else "Gemini",
        expanded = provMenu,
        onExpand = { provMenu = true },
        onDismiss = { provMenu = false },
    ) {
        DropdownMenuItem(
            text = { Text("Gemini") },
            onClick = {
                onDraft(draft.copy(provider = "gemini"))
                provMenu = false
            },
        )
        DropdownMenuItem(
            text = { Text("ChatGPT (OpenAI)") },
            onClick = {
                onDraft(draft.copy(provider = "openai"))
                provMenu = false
            },
        )
    }
    val modelLabel = modelPairs.find { it.first == currentModel }?.second ?: currentModel
    SettingsSelectButton(
        isDark = isDark,
        label = modelLabel,
        expanded = modelMenu,
        onExpand = { modelMenu = true },
        onDismiss = { modelMenu = false },
    ) {
        modelPairs.forEach { (value, name) ->
            DropdownMenuItem(
                text = { Text(name) },
                onClick = {
                    onDraft(
                        if (draft.provider == "openai") draft.copy(openaiModel = value)
                        else draft.copy(geminiModel = value),
                    )
                    modelMenu = false
                },
            )
        }
    }
    if (draft.provider == "openai") {
        val thinkLabel = when (draft.thinkingLevel) {
            "low" -> "シンキングレベル: 低い"
            "high" -> "シンキングレベル: 高い"
            else -> "シンキングレベル: 普通"
        }
        SettingsSelectButton(
            isDark = isDark,
            label = thinkLabel,
            expanded = thinkMenu,
            onExpand = { thinkMenu = true },
            onDismiss = { thinkMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text("シンキングレベル: 低い") },
                onClick = {
                    onDraft(draft.copy(thinkingLevel = "low"))
                    thinkMenu = false
                },
            )
            DropdownMenuItem(
                text = { Text("シンキングレベル: 普通") },
                onClick = {
                    onDraft(draft.copy(thinkingLevel = "medium"))
                    thinkMenu = false
                },
            )
            DropdownMenuItem(
                text = { Text("シンキングレベル: 高い") },
                onClick = {
                    onDraft(draft.copy(thinkingLevel = "high"))
                    thinkMenu = false
                },
            )
        }
    }
}

@Composable
private fun SettingsAiBehavior(
    isDark: Boolean,
    draft: AppSettings,
    onDraft: (AppSettings) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        SettingsSectionTitle(isDark, "振る舞い（プロンプト・温度）")
        OutlinedTextField(
            value = draft.systemPrompt,
            onValueChange = { onDraft(draft.copy(systemPrompt = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("システムプロンプト") },
            minLines = 6,
            shape = RoundedCornerShape(12.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = { onDraft(draft.copy(systemPrompt = "")) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) SettingsUi.chipBgDark else SettingsUi.chipBgLight,
                    contentColor = if (isDark) Color.White else CodexWebPalette.slate900,
                ),
                shape = RoundedCornerShape(10.dp),
            ) { Text("システムプロンプトを空にする") }
        }
        Text(
            "温度 ${"%.1f".format(draft.temperature)}",
            style = MaterialTheme.typography.bodySmall,
            color = if (isDark) SettingsUi.navTextDark else SettingsUi.navTextLight,
        )
        Slider(
            value = draft.temperature.toFloat().coerceIn(0f, 2f),
            onValueChange = { onDraft(draft.copy(temperature = it.toDouble())) },
            valueRange = 0f..2f,
        )
    }
}

@Composable
private fun SettingsAiContext(
    isDark: Boolean,
    draft: AppSettings,
    onDraft: (AppSettings) -> Unit,
) {
    SettingsSectionTitle(isDark, "コンテキスト / 署名")
    OutlinedTextField(
        value = draft.userSignature,
        onValueChange = { onDraft(draft.copy(userSignature = it)) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("ユーザー署名（例: Blanche）") },
        shape = RoundedCornerShape(12.dp),
    )
    val maxCap = contextTokenLimit(draft.provider)
    val coercedTokens = draft.maxTokens.coerceIn(256, maxCap)
    Text(
        "コンテキスト長（トークン） $coercedTokens",
        style = MaterialTheme.typography.bodySmall,
        color = if (isDark) SettingsUi.navTextDark else SettingsUi.navTextLight,
    )
    Slider(
        value = coercedTokens.toFloat(),
        onValueChange = { v ->
            val stepped = ((v / 256f).roundToInt() * 256).coerceIn(256, maxCap)
            onDraft(draft.copy(maxTokens = stepped))
        },
        valueRange = 256f..maxCap.toFloat(),
        steps = ((maxCap - 256) / 256) - 1,
    )
}

@Composable
private fun SettingsDisplay(
    isDark: Boolean,
    resolvedDark: Boolean,
    draft: AppSettings,
    onToggleTheme: () -> Unit,
    onDraft: (AppSettings) -> Unit,
) {
    SettingsSectionTitle(isDark, "ライト / ダークモード")
    Button(
        onClick = onToggleTheme,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isDark) SettingsUi.chipBgDark else SettingsUi.chipBgLightAlt,
            contentColor = if (isDark) Color.White else CodexWebPalette.slate900,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            if (resolvedDark) "☀️ ライトモードへ" else "🌙 ダークモードへ",
            fontWeight = FontWeight.Bold,
        )
    }
    SettingsSectionTitle(isDark, "文字の速さ")
    var speedMenu by remember { mutableStateOf(false) }
    val speedLabel = when (draft.renderSpeed) {
        "batch" -> "まとめて"
        "slow" -> "遅い"
        "fast" -> "早い"
        "live" -> "ライブストリーミング"
        else -> "普通"
    }
    SettingsSelectButton(
        isDark = isDark,
        label = speedLabel,
        expanded = speedMenu,
        onExpand = { speedMenu = true },
        onDismiss = { speedMenu = false },
    ) {
        listOf(
            "batch" to "まとめて",
            "slow" to "遅い",
            "normal" to "普通",
            "fast" to "早い",
            "live" to "ライブストリーミング",
        ).forEach { (v, label) ->
            DropdownMenuItem(
                text = { Text(label) },
                onClick = {
                    onDraft(draft.copy(renderSpeed = v))
                    speedMenu = false
                },
            )
        }
    }
}

@Composable
private fun SettingsAdvancedRoot(
    isDark: Boolean,
    onOpen: (SettingsDest) -> Unit,
) {
    SettingsNavRow(isDark, primary = true, onClick = { onOpen(SettingsDest.DevRoot) }) {
        Text("開発者向け", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsDevRoot(
    isDark: Boolean,
    onOpen: (SettingsDest) -> Unit,
) {
    SettingsNavRow(isDark, primary = true, onClick = { onOpen(SettingsDest.DevLogs) }) {
        Text("ログ", fontWeight = FontWeight.Bold)
    }
    SettingsNavRow(isDark, primary = true, onClick = { onOpen(SettingsDest.DevConvExport) }) {
        Text("会話JSON取り出し", fontWeight = FontWeight.Bold)
    }
    SettingsNavRow(isDark, primary = true, onClick = { onOpen(SettingsDest.DevConvHistoryOnly) }) {
        Text("世界設定を含めず会話JSONを取り出す", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsDevLogs(
    isDark: Boolean,
    logs: List<com.tamarilog.codexblanche.DevLogEntry>,
    onNewSession: () -> Unit,
) {
    SettingsSectionTitle(isDark, "ログ（開発者向けエラーログなど）表示画面")
    Text(
        "APIキーは「この端末に保存」をONにするとlocalStorageへ保存されます。OFF時はsessionStorageのみ利用します。",
        style = MaterialTheme.typography.bodySmall,
        color = if (isDark) SettingsUi.hintDark else SettingsUi.hint,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (e in logs.asReversed()) {
            Text(
                "[${e.level}] ${e.text}",
                style = MaterialTheme.typography.bodySmall,
                color = if (isDark) SettingsUi.navTextDark else SettingsUi.navTextLight,
            )
        }
    }
    Button(
        onClick = onNewSession,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = SettingsUi.mutedActionBtn, contentColor = Color.White),
        shape = RoundedCornerShape(10.dp),
    ) { Text("新規会話を開始", fontWeight = FontWeight.Bold) }
}

@Composable
private fun SettingsDevConvExport(
    isDark: Boolean,
    status: String,
    onPick: () -> Unit,
    onRun: () -> Unit,
) {
    SettingsSectionTitle(isDark, "会話JSON取り出し")
    Text(
        "Googleクラウド等から取得した履歴JSONを選択し、会話形式JSONのみを世界設定ごとに抽出します。",
        style = MaterialTheme.typography.bodySmall,
        color = if (isDark) SettingsUi.hintDark else SettingsUi.hint,
    )
    Button(
        onClick = onPick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isDark) SettingsUi.chipBgDark else SettingsUi.chipBgLight,
            contentColor = if (isDark) Color.White else CodexWebPalette.slate900,
        ),
        shape = RoundedCornerShape(10.dp),
    ) { Text("ファイル選択", fontWeight = FontWeight.Bold) }
    Button(
        onClick = onRun,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = SettingsUi.indigoBtn, contentColor = Color.White),
        shape = RoundedCornerShape(10.dp),
    ) { Text("実行", fontWeight = FontWeight.Bold) }
    Text(
        status,
        style = MaterialTheme.typography.bodySmall,
        color = if (isDark) SettingsUi.hintDark else SettingsUi.hint,
    )
}

@Composable
private fun SettingsDevConvHistoryOnly(
    isDark: Boolean,
    status: String,
    onPick: () -> Unit,
    onRun: () -> Unit,
) {
    SettingsSectionTitle(isDark, "世界設定を含めず会話JSONを取り出す")
    Text(
        "会話履歴JSONから、会話履歴（messages）のみを抽出して保存します。",
        style = MaterialTheme.typography.bodySmall,
        color = if (isDark) SettingsUi.hintDark else SettingsUi.hint,
    )
    Button(
        onClick = onPick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isDark) SettingsUi.chipBgDark else SettingsUi.chipBgLight,
            contentColor = if (isDark) Color.White else CodexWebPalette.slate900,
        ),
        shape = RoundedCornerShape(10.dp),
    ) { Text("ファイル選択", fontWeight = FontWeight.Bold) }
    Button(
        onClick = onRun,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = SettingsUi.indigoBtn, contentColor = Color.White),
        shape = RoundedCornerShape(10.dp),
    ) { Text("実行", fontWeight = FontWeight.Bold) }
    Text(
        status,
        style = MaterialTheme.typography.bodySmall,
        color = if (isDark) SettingsUi.hintDark else SettingsUi.hint,
    )
}

@Composable
private fun SettingsSectionTitle(isDark: Boolean, text: String) {
    Text(
        text,
        fontSize = 17.6.sp,
        fontWeight = FontWeight.Bold,
        color = if (isDark) SettingsUi.titleDark else SettingsUi.titleLight,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingsNavRow(
    isDark: Boolean,
    primary: Boolean,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = if (isDark) SettingsUi.navBorderDark else SettingsUi.navBorderLight
    val bg = when {
        !primary && isDark -> SettingsUi.secondaryNavBg
        !primary -> SettingsUi.navBgLight
        isDark -> SettingsUi.navBgDark
        else -> SettingsUi.navBgLight
    }
    val fg = when {
        !primary && isDark -> SettingsUi.secondaryNavFg
        !primary -> SettingsUi.navTextLight
        isDark -> SettingsUi.navTextDark
        else -> SettingsUi.navTextLight
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, border), RoundedCornerShape(16.dp))
            .background(bg, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            content()
        }
    }
}

@Composable
private fun SettingsSelectButton(
    isDark: Boolean,
    label: String,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    menu: @Composable () -> Unit,
) {
    val border = if (isDark) SettingsUi.navBorderDark else SettingsUi.navBorderLight
    val bg = if (isDark) SettingsUi.chipBgDark else Color.White
    val fg = if (isDark) Color.White else CodexWebPalette.slate900
    BoxWithDropdown(
        expanded = expanded,
        onDismiss = onDismiss,
        anchor = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, border), RoundedCornerShape(12.dp))
                    .background(bg, RoundedCornerShape(12.dp))
                    .clickable { onExpand() }
                    .padding(12.dp),
            ) {
                Text(label, color = fg, modifier = Modifier.weight(1f))
            }
        },
        menu = menu,
    )
}

@Composable
private fun BoxWithDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    anchor: @Composable () -> Unit,
    menu: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth()) {
        anchor()
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            menu()
        }
    }
}

private fun readUriText(context: android.content.Context, uri: Uri): String? =
    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
