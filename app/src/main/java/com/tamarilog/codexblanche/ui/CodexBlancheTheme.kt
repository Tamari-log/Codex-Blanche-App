package com.tamarilog.codexblanche.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.tamarilog.codexblanche.ui.theme.CodexWebPalette

private val SandLight = lightColorScheme(
    primary = CodexWebPalette.brownTitle,
    onPrimary = CodexWebPalette.innerShell,
    primaryContainer = CodexWebPalette.footerBarLight,
    onPrimaryContainer = CodexWebPalette.brownTitle,
    secondary = CodexWebPalette.brownSecondary,
    background = CodexWebPalette.bodyShell,
    surface = CodexWebPalette.innerShell,
    onSurface = CodexWebPalette.brownTitle,
)

private val SandDark = darkColorScheme(
    primary = CodexWebPalette.footerBarLight,
    onPrimary = CodexWebPalette.slate900,
    primaryContainer = CodexWebPalette.slate700,
    background = CodexWebPalette.chatAreaDark,
    surface = CodexWebPalette.slate800,
    onSurface = CodexWebPalette.slate200,
)

private val CodexTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = 3.sp,
        lineHeight = 26.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 16.sp,
        lineHeight = 26.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
    ),
)

@Composable
fun CodexBlancheTheme(
    themePreference: String = "",
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themePreference) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    val scheme = if (dark) SandDark else SandLight
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = CodexTypography,
        content = content,
    )
}
