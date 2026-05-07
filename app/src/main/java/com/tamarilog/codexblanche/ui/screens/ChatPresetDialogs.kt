package com.tamarilog.codexblanche.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tamarilog.codexblanche.data.resolveEffectiveSettings
import com.tamarilog.codexblanche.data.model.AppSettings
import com.tamarilog.codexblanche.data.model.ChatSession
import com.tamarilog.codexblanche.data.model.ContextLimits
import com.tamarilog.codexblanche.data.model.ModelOptions
import com.tamarilog.codexblanche.data.model.Persona
import com.tamarilog.codexblanche.data.normalizeThinkingLevel
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt

@Composable
fun SessionAiConfigDialog(
    session: ChatSession,
    global: AppSettings,
    onDismiss: () -> Unit,
    onSave: (
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
    ) -> Unit,
) {
    val o = session.overrides
    val eff = resolveEffectiveSettings(session, global)
    var provider by remember(session.id) {
        mutableStateOf(
            when (o.provider) {
                "openai" -> "openai"
                "gemini" -> "gemini"
                else -> if (global.provider == "openai") "openai" else "gemini"
            },
        )
    }
    var geminiModel by remember(session.id) { mutableStateOf(o.geminiModel ?: global.geminiModel) }
    var openaiModel by remember(session.id) { mutableStateOf(o.openaiModel ?: global.openaiModel) }
    var thinking by remember(session.id) { mutableStateOf(normalizeThinkingLevel(o.thinkingLevel ?: global.thinkingLevel)) }
    var allowGemini by remember(session.id) { mutableStateOf(o.allowGeminiSearch ?: global.allowGeminiSearch) }
    var allowOpenai by remember(session.id) { mutableStateOf(o.allowOpenaiSearch ?: global.allowOpenaiSearch) }
    var temperature by remember(session.id) { mutableDoubleStateOf(o.temperature ?: eff.temperature) }
    val maxCap = ContextLimits.forProvider(provider)
    var maxTokens by remember(session.id) {
        mutableIntStateOf((o.maxTokens ?: global.maxTokens).coerceIn(256, maxCap))
    }
    var systemPrompt by remember(session.id) { mutableStateOf(session.systemPrompt) }
    var userSignature by remember(session.id) { mutableStateOf(session.userSignature) }

    LaunchedEffect(provider) {
        val lim = ContextLimits.forProvider(provider)
        maxTokens = maxTokens.coerceIn(256, lim)
    }

    var provMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var thinkMenu by remember { mutableStateOf(false) }
    val modelPairs = if (provider == "openai") ModelOptions.openai else ModelOptions.gemini
    val currentModel = if (provider == "openai") openaiModel else geminiModel

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("会話設定を編集") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("この会話に適用されます。", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                ProviderModelRows(
                    provider = provider,
                    onProvider = { provider = it; provMenu = false },
                    providerMenuOpen = provMenu,
                    onProviderMenuOpen = { provMenu = true },
                    onProviderMenuDismiss = { provMenu = false },
                    modelLabel = modelPairs.find { it.first == currentModel }?.second ?: currentModel,
                    modelMenuOpen = modelMenu,
                    onModelMenuOpen = { modelMenu = true },
                    onModelMenuDismiss = { modelMenu = false },
                    modelPairs = modelPairs,
                    onPickModel = { v ->
                        if (provider == "openai") openaiModel = v else geminiModel = v
                        modelMenu = false
                    },
                )
                ThinkingRow(
                    thinking = thinking,
                    thinkMenuOpen = thinkMenu,
                    onOpenThink = { thinkMenu = true },
                    onDismissThink = { thinkMenu = false },
                    onPick = { thinking = it; thinkMenu = false },
                )
                OutlinedTextField(
                    value = userSignature,
                    onValueChange = { userSignature = it },
                    label = { Text("署名 (任意)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(allowGemini, onCheckedChange = { allowGemini = it })
                    Text("Gemini の検索を許可", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(allowOpenai, onCheckedChange = { allowOpenai = it })
                    Text("OpenAI の検索を許可", style = MaterialTheme.typography.bodySmall)
                }
                Text("温度 ${"%.1f".format(temperature)}", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = temperature.toFloat().coerceIn(0f, 2f),
                    onValueChange = { temperature = it.toDouble() },
                    valueRange = 0f..2f,
                )
                Text("コンテキスト長（トークン） $maxTokens", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = maxTokens.toFloat(),
                    onValueChange = { v ->
                        val stepped = ((v / 256f).roundToInt() * 256).coerceIn(256, maxCap)
                        maxTokens = stepped
                    },
                    valueRange = 256f..maxCap.toFloat(),
                    steps = tokenSliderSteps(maxCap),
                )
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("システムプロンプト (任意)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    shape = RoundedCornerShape(8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        provider,
                        geminiModel.ifBlank { null },
                        openaiModel.ifBlank { null },
                        thinking,
                        allowGemini,
                        allowOpenai,
                        temperature,
                        maxTokens,
                        systemPrompt,
                        userSignature,
                    )
                    onDismiss()
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}

@Composable
fun CustomPersonaAiConfigDialog(
    persona: Persona,
    global: AppSettings,
    onDismiss: () -> Unit,
    onSave: (
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
    ) -> Unit,
) {
    val s = persona.settings
    fun str(key: String, fallback: String) = s[key]?.jsonPrimitive?.content ?: fallback
    fun dbl(key: String, fallback: Double) = s[key]?.jsonPrimitive?.doubleOrNull ?: fallback
    fun intk(key: String, fallback: Int) = s[key]?.jsonPrimitive?.intOrNull ?: fallback
    fun bool(key: String, fallback: Boolean) = s[key]?.jsonPrimitive?.booleanOrNull ?: fallback

    var nameDraft by remember(persona.id) { mutableStateOf(persona.name) }
    var provider by remember(persona.id) {
        mutableStateOf(
            when (str("provider", global.provider)) {
                "openai" -> "openai"
                else -> "gemini"
            },
        )
    }
    var geminiModel by remember(persona.id) { mutableStateOf(str("geminiModel", global.geminiModel)) }
    var openaiModel by remember(persona.id) { mutableStateOf(str("openaiModel", global.openaiModel)) }
    var thinking by remember(persona.id) {
        mutableStateOf(normalizeThinkingLevel(str("thinkingLevel", global.thinkingLevel)))
    }
    var allowGemini by remember(persona.id) { mutableStateOf(bool("allowGeminiSearch", global.allowGeminiSearch)) }
    var allowOpenai by remember(persona.id) { mutableStateOf(bool("allowOpenaiSearch", global.allowOpenaiSearch)) }
    var temperature by remember(persona.id) { mutableDoubleStateOf(dbl("temperature", global.temperature)) }
    val maxCap = ContextLimits.forProvider(provider)
    var maxTokens by remember(persona.id) {
        mutableIntStateOf(intk("maxTokens", global.maxTokens).coerceIn(256, maxCap))
    }
    var systemPrompt by remember(persona.id) { mutableStateOf(str("systemPrompt", global.systemPrompt)) }
    var userSignature by remember(persona.id) {
        mutableStateOf(str("userSignature", global.userSignature))
    }

    LaunchedEffect(provider) {
        val lim = ContextLimits.forProvider(provider)
        maxTokens = maxTokens.coerceIn(256, lim)
    }

    var provMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var thinkMenu by remember { mutableStateOf(false) }
    val modelPairs = if (provider == "openai") ModelOptions.openai else ModelOptions.gemini
    val currentModel = if (provider == "openai") openaiModel else geminiModel

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("会話設定を編集") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("このカスタムプリセットに適用されます。", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    label = { Text("プリセット名") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )
                ProviderModelRows(
                    provider = provider,
                    onProvider = { provider = it; provMenu = false },
                    providerMenuOpen = provMenu,
                    onProviderMenuOpen = { provMenu = true },
                    onProviderMenuDismiss = { provMenu = false },
                    modelLabel = modelPairs.find { it.first == currentModel }?.second ?: currentModel,
                    modelMenuOpen = modelMenu,
                    onModelMenuOpen = { modelMenu = true },
                    onModelMenuDismiss = { modelMenu = false },
                    modelPairs = modelPairs,
                    onPickModel = { v ->
                        if (provider == "openai") openaiModel = v else geminiModel = v
                        modelMenu = false
                    },
                )
                ThinkingRow(
                    thinking = thinking,
                    thinkMenuOpen = thinkMenu,
                    onOpenThink = { thinkMenu = true },
                    onDismissThink = { thinkMenu = false },
                    onPick = { thinking = it; thinkMenu = false },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(allowGemini, onCheckedChange = { allowGemini = it })
                    Text("Gemini の検索を許可", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(allowOpenai, onCheckedChange = { allowOpenai = it })
                    Text("OpenAI の検索を許可", style = MaterialTheme.typography.bodySmall)
                }
                Text("温度 ${"%.1f".format(temperature)}", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = temperature.toFloat().coerceIn(0f, 2f),
                    onValueChange = { temperature = it.toDouble() },
                    valueRange = 0f..2f,
                )
                Text("コンテキスト長（トークン） $maxTokens", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = maxTokens.toFloat(),
                    onValueChange = { v ->
                        val stepped = ((v / 256f).roundToInt() * 256).coerceIn(256, maxCap)
                        maxTokens = stepped
                    },
                    valueRange = 256f..maxCap.toFloat(),
                    steps = tokenSliderSteps(maxCap),
                )
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("システムプロンプト (任意)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    shape = RoundedCornerShape(8.dp),
                )
                OutlinedTextField(
                    value = userSignature,
                    onValueChange = { userSignature = it },
                    label = { Text("署名 (任意)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        nameDraft,
                        provider,
                        geminiModel,
                        openaiModel,
                        thinking,
                        allowGemini,
                        allowOpenai,
                        temperature,
                        maxTokens,
                        systemPrompt,
                        userSignature,
                    )
                    onDismiss()
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}

private fun tokenSliderSteps(maxCap: Int): Int {
    val raw = (maxCap - 256) / 256 - 1
    return raw.coerceAtLeast(0)
}

@Composable
private fun ProviderModelRows(
    provider: String,
    onProvider: (String) -> Unit,
    providerMenuOpen: Boolean,
    onProviderMenuOpen: () -> Unit,
    onProviderMenuDismiss: () -> Unit,
    modelLabel: String,
    modelMenuOpen: Boolean,
    onModelMenuOpen: () -> Unit,
    onModelMenuDismiss: () -> Unit,
    modelPairs: List<Pair<String, String>>,
    onPickModel: (String) -> Unit,
) {
    Column {
        Box(Modifier.fillMaxWidth()) {
            TextButton(onClick = onProviderMenuOpen, modifier = Modifier.fillMaxWidth()) {
                Text(if (provider == "openai") "ChatGPT (OpenAI)" else "Gemini")
            }
            DropdownMenu(expanded = providerMenuOpen, onDismissRequest = onProviderMenuDismiss) {
                DropdownMenuItem(text = { Text("Gemini") }, onClick = { onProvider("gemini") })
                DropdownMenuItem(text = { Text("ChatGPT (OpenAI)") }, onClick = { onProvider("openai") })
            }
        }
        Box(Modifier.fillMaxWidth()) {
            TextButton(onClick = onModelMenuOpen, modifier = Modifier.fillMaxWidth()) { Text(modelLabel) }
            DropdownMenu(expanded = modelMenuOpen, onDismissRequest = onModelMenuDismiss) {
                modelPairs.forEach { (v, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { onPickModel(v) })
                }
            }
        }
    }
}

@Composable
private fun ThinkingRow(
    thinking: String,
    thinkMenuOpen: Boolean,
    onOpenThink: () -> Unit,
    onDismissThink: () -> Unit,
    onPick: (String) -> Unit,
) {
    val label = when (thinking) {
        "low" -> "シンキング: 低い"
        "high" -> "シンキング: 高い"
        else -> "シンキング: 普通"
    }
    Box(Modifier.fillMaxWidth()) {
        TextButton(onClick = onOpenThink, modifier = Modifier.fillMaxWidth()) { Text(label) }
        DropdownMenu(expanded = thinkMenuOpen, onDismissRequest = onDismissThink) {
            DropdownMenuItem(text = { Text("シンキング: 低い") }, onClick = { onPick("low") })
            DropdownMenuItem(text = { Text("シンキング: 普通") }, onClick = { onPick("medium") })
            DropdownMenuItem(text = { Text("シンキング: 高い") }, onClick = { onPick("high") })
        }
    }
}
