package com.tamarilog.codexblanche.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tamarilog.codexblanche.ui.theme.CodexWebTextures

/**
 * Web の chat-bg 相当：ベース色の上に羊皮紙 / ダークマターのテクスチャを重ねる。
 *（multiply の近似としてアルファブレンド）
 */
@Composable
fun ChatPaperBackdrop(
    isDark: Boolean,
    baseColor: Color,
    content: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val url = if (isDark) CodexWebTextures.PAPER_DARK else CodexWebTextures.PAPER_LIGHT
    val textureAlpha = if (isDark) 0.48f else 0.42f

    Box(Modifier.fillMaxSize().background(baseColor)) {
        AsyncImage(
            model = ImageRequest.Builder(ctx)
                .data(url)
                .crossfade(300)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = textureAlpha },
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize()) {
            content()
        }
    }
}
