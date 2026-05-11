package com.tamarilog.codexblanche.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * アプリ全体の色トークン（ライトは羊皮紙トーン、ダークはスレート系で統一）。
 */
object CodexWebPalette {
    // --- Paper / shell（ライト）---
    val bodyShell = Color(0xFFE3CFAA)
    val innerShell = Color(0xFFE8D8B3)
    val chatAreaLight = Color(0xFFE8D8B3)
    /** 設定画面ライト時のベース（チャットの羊皮紙と同系、ヘッダー白帯とは別） */
    val settingsBgLight = Color(0xFFF2E6D0)
    val footerBarLight = Color(0xFFE0CBA0)
    /** ライト時コンポーザー内カプセル（真っ白にならないよう羊皮紙寄り） */
    val composerCapsuleLight = Color(0xFFF5EBD8)

    // --- ダーク基調 ---
    val chatAreaDark = Color(0xFF0F172A)
    val footerBarDark = Color(0xFF1E293B)

    // --- Slate 系（ライト枠・ダーク UI 共用）---
    val slate900 = Color(0xFF0F172A)
    val slate800 = Color(0xFF1E293B)
    val slate700 = Color(0xFF334155)
    val slate600 = Color(0xFF475569)
    val slate500 = Color(0xFF64748B)
    val slate400 = Color(0xFF94A3B8)
    val slate300 = Color(0xFFCBD5E1)
    val slate200 = Color(0xFFE2E8F0)
    val slate100 = Color(0xFFF1F5F9)
    val slate50 = Color(0xFFF8FAFC)

    // --- ブラウン / インク（メッセージ・見出し）---
    val userMsg = Color(0xFF1F1306)
    val userBorder = Color(0xFF7B4F24)
    val aiMsg = Color(0xFF2F2111)
    val captionBrown = Color(0xFF4A2C12)
    val brownTitle = Color(0xFF40260F)
    val brownSoft = Color(0xFF8B7355)
    val brownSecondary = Color(0xFF6B5340)

    // --- クローム ---
    val footerBorder = Color(0xFFB89D74)
    val headerBorderLight = Color(0xFFD8C4A8)
    val headerBorderDark = slate700
    val emojiBtnBorder = Color(0xCCB89D74)

    val presetPanelLight = innerShell
    val presetPanelDark = slate800
    val searchFieldBg = Color(0xA60F172A)
    val sidebarGroupBorder = Color(0xA6B89D74)
    val drawerBackdrop = Color(0x700F172A)
    val fileChipBorder = Color(0x598B5E34)
    val fileChipBg = Color(0x85FFFFFF)

    val accentBlue = Color(0xFF2563EB)
    val scrimHeavy = Color(0xE60F172A)

    // --- 半透明・グラデ UI（Compose ARGB をトークン化）---
    val scrollToBottomFabDark = Color(0xEB1E293B)
    val scrollToBottomFabLight = Color(0xE6FFFFFF)
    val borderFrostLight = Color(0x99CBD5E1)
    val borderUserTint = Color(0x807B4F24)
    val dividerSoftDark = Color(0x66547569)
    val presetChromeBorderDark = Color(0xE647486B)
    val presetChromeBorderLight = Color(0xB3B89D74)
    val drawerSessionBorder = Color(0xD947486B)
    val drawerSessionBgDark = Color(0x730F172A)
    val drawerSessionBgLight = Color(0x59FFFFFF)
    val iconButtonSurfaceDark = Color(0xCC1E293B)
    val iconButtonSurfaceLight = Color(0x73FFFFFF)
    val iconButtonSurfaceLightMedium = Color(0x8CFFFFFF)
    val attachmentChipBgDark = Color(0x591E293B)
    val attachmentChipBgLight = Color(0x80FFFFFF)
    val attachmentChipBorderDark = Color(0x6694A3B8)
    val sessionThumbBgDark = Color(0x661E293B)
    val sessionListActiveDark = Color(0x991E293B)
    val tabInactiveDarkTint = Color(0xB81E293B)
    val searchFieldOutline = Color(0x8F94A3B8)
    val imageCardScrimDark = Color(0x80547569)
    val imageCardScrimLight = Color(0x66CBD5E1)

    /** 送信 FAB：ライト=ダーク色面+白記号、ダーク=明るい面+濃い記号（従来どおり） */
    fun sendFabSurface(isDark: Boolean) = if (isDark) slate100 else slate800

    fun sendFabGlyph(isDark: Boolean) = if (isDark) slate900 else Color.White
}
