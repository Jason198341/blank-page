package com.blank.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 표면
val Bg = Color(0xFF121212)
/** 재현 화면만 더 검다 — 탭바를 숨기고 여기 들어오면 다른 데로 못 샌다는 신호 */
val BgRecall = Color(0xFF0A0A0B)
val CardBg = Color(0xFF1E1E1E)
val CardBgRaised = Color(0xFF242426)
val Outline = Color(0xFF2E2E33)

// 포인트
val Accent = Color(0xFF7C5CFF)
val AccentPressed = Color(0xFF5B45B8)

// 글자
val TextPrimary = Color(0xFFECECEE)
val TextSecondary = Color(0xFFA0A0A8)
val TextTertiary = Color(0xFF6E6E76)

// 상태
val StatePlanned = Color(0xFF7C5CFF)
val StateToday = Color(0xFFA98BFF)
val StateDone = Color(0xFF4CC38A)
val StateMissed = Color(0xFFE5484D)
val StateVoid = Color(0xFF6E6E76)

// 비교결과
val DiffHit = Color(0xFF4CC38A)
val DiffMissed = Color(0xFFE5A03D)
val DiffWrong = Color(0xFFE5484D)
val DiffExtra = Color(0xFF5AA9E6)

/** 회차 배지 색 (D0 이 가장 밝고, 멀어질수록 가라앉는다) */
val RoundBadgeBg = listOf(
    Color(0xFF7C5CFF), Color(0xFF6A4FDB), Color(0xFF584099), Color(0xFF3E2F6B), Color(0xFF2C2247)
)
val RoundBadgeFg = listOf(
    Color(0xFFFFFFFF), Color(0xFFF0ECFF), Color(0xFFDCD4FF), Color(0xFFC4B8F5), Color(0xFFA99BE0)
)
/** 캘린더 점: 회차가 멀수록 옅다 */
val RoundDotAlpha = listOf(1.0f, 0.85f, 0.72f, 0.60f, 0.50f)
/** 캘린더 점 지름(dp) */
val RoundDotSize = listOf(6, 6, 5, 5, 4)

private val BlankColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A2140),
    onPrimaryContainer = TextPrimary,
    secondary = Accent,
    background = Bg,
    onBackground = TextPrimary,
    surface = CardBg,
    onSurface = TextPrimary,
    surfaceVariant = CardBgRaised,
    onSurfaceVariant = TextSecondary,
    outline = Outline,
    error = StateMissed
)

private val BlankTypography = Typography(
    displaySmall = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold)
)

@Composable
fun BlankTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BlankColors, // 항상 다크
        typography = BlankTypography,
        content = content
    )
}
