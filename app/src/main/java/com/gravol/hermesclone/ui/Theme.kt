package com.gravol.hermesclone.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.gravol.hermesclone.R

// Palette sampled PIXEL-FROM-THE-OFFICIAL-APK screenshots (01-launch / 15-turn):
// bg #000022, warm-white text #F4F1EB, warm-gray muted #ABA9A5, live blue #447CFF
// (session dots + "Working"), purple #8B5CF6 (avatars, title pill), circle/pill bg
// #0C1A3C, user bubble #272740, mic circle #172D62, tree line #302F47.
val HmBackground = Color(0xFF000022)
val HmSurface = Color(0xFF0C1A3C)
val HmRowHighlight = Color(0xFF1D2761)
val HmPurple = Color(0xFF8B5CF6)
val HmGreen = Color(0xFF66DD4B)
val HmTeal = Color(0xFF4BDDC7)
val HmLiveBlue = Color(0xFF447CFF)
val HmTextPrimary = Color(0xFFF4F1EB)
val HmTextMuted = Color(0xFFABA9A5)
val HmDanger = Color(0xFFFF6B6B)
val HmUserBubble = Color(0xFF272740)
val HmToolPill = Color(0xFF0C1A3C)
// pixel-sampled from official 2026-11-24: collapsed-turn pill bg + check circle
val HmTurnPill = Color(0xFF17313F)
val HmTurnCheck = Color(0xFF29A551)
val HmBorder = Color(0xFF2A3A6A)
val HmTreeLine = Color(0xFF2C3A57)
val HmAccentCircle = Color(0xFF172D62)
// vc49 parity pass (2026-11-26): stop circle sampled #402B43 with a cream square glyph;
// markdown table card header #131331 / body #070727 (from official shots-tour-2026-11-26).
val HmStopCircle = Color(0xFF402B43)
val HmStopSquare = Color(0xFFF4F1EB)
val HmTableHeader = Color(0xFF131331)
val HmTableBody = Color(0xFF070727)

// Official default font setting: "Rounded — Nunito, soft rounded shapes · Default"
// (enum c30 in decompiled sources; default constant c30.o).
val HmFontFamily = FontFamily(
    Font(R.font.nunito),
    Font(R.font.nunito_italic, style = FontStyle.Italic),
)

// This material3 version has no Typography(defaultFontFamily=...), so every slot
// carries Nunito explicitly — Text() defaults then inherit it.
private val hmType = TextStyle(fontFamily = HmFontFamily)
private val HmTypography = Typography(
    displayLarge = hmType, displayMedium = hmType, displaySmall = hmType,
    headlineLarge = hmType, headlineMedium = hmType, headlineSmall = hmType,
    titleLarge = TextStyle(fontFamily = HmFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = HmFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    titleSmall = TextStyle(fontFamily = HmFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = HmFontFamily, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = HmFontFamily, fontSize = 15.sp),
    bodySmall = TextStyle(fontFamily = HmFontFamily, fontSize = 13.sp, color = HmTextMuted),
    labelLarge = TextStyle(fontFamily = HmFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = HmFontFamily, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = HmFontFamily, fontSize = 11.sp, color = HmTextMuted),
)

val avatarPalette = listOf(HmPurple, HmTeal, HmGreen, Color(0xFF5B8DEF), Color(0xFFE45B9A))

private val HmColors = darkColorScheme(
    primary = HmPurple,
    onPrimary = Color.White,
    secondary = HmTeal,
    onSecondary = HmBackground,
    tertiary = HmGreen,
    background = HmBackground,
    onBackground = HmTextPrimary,
    surface = HmSurface,
    onSurface = HmTextPrimary,
    surfaceVariant = HmRowHighlight,
    onSurfaceVariant = HmTextMuted,
    error = HmDanger,
    onError = Color.White,
    outline = HmBorder,
)

@Composable
fun HermesCloneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HmColors,
        typography = HmTypography,
        content = content,
    )
}
