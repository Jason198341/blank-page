package com.blank.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * 디자인 토큰. 화면을 하나하나 칠하지 않고 여기서 전파한다.
 * 근거: Obsidian Minimal(콘텐츠 우선·크롬 최소·차분한 딥다크),
 *      Material 3 Expressive(더 굵고 큰 타이포·8dp 그리드·둥근 모서리·회색 계조).
 */

// 표면 — 순검정 대신 회색 계조로 깊이를 준다. 읽기·인출 화면만 더 가라앉힌다.
val Bg = Color(0xFF121214)
val BgReading = Color(0xFF0F0F11)   // 노트 읽기
val BgRecall = Color(0xFF0A0A0B)    // 인출(가장 깊게 — 여기 들어오면 몰입)
val CardBg = Color(0xFF1B1B1F)
val CardBgRaised = Color(0xFF232327)
val Outline = Color(0xFF2A2A30)

// 포인트 — 기본이 아니라 강조 한 곳에만.
val Accent = Color(0xFF8168FF)
val AccentPressed = Color(0xFF5F49C7)

// 글자 — 대비를 살짝 낮춰 장시간 읽기에 편하게.
val TextPrimary = Color(0xFFEDEDF2)
val TextSecondary = Color(0xFF9A9AA6)
val TextTertiary = Color(0xFF66666F)

// 상태
val StatePlanned = Color(0xFF8168FF)
val StateToday = Color(0xFFAB93FF)
val StateDone = Color(0xFF4CC38A)
val StateMissed = Color(0xFFE5484D)
val StateVoid = Color(0xFF66666F)

// 비교결과
val DiffHit = Color(0xFF4CC38A)
val DiffMissed = Color(0xFFE5A03D)
val DiffWrong = Color(0xFFE5484D)
val DiffExtra = Color(0xFF5AA9E6)

val RoundBadgeBg = listOf(
    Color(0xFF8168FF), Color(0xFF6E58DB), Color(0xFF564399), Color(0xFF3C2F6B), Color(0xFF2B2247)
)
val RoundBadgeFg = listOf(
    Color(0xFFFFFFFF), Color(0xFFF0ECFF), Color(0xFFDCD4FF), Color(0xFFC4B8F5), Color(0xFFA99BE0)
)
val RoundDotAlpha = listOf(1.0f, 0.85f, 0.72f, 0.60f, 0.50f)
val RoundDotSize = listOf(7, 6, 6, 5, 5)

// 모서리 — Expressive 는 더 둥글다. 카드는 넉넉히, 주 버튼은 pill.
val PillShape = RoundedCornerShape(percent = 50)

private val BlankShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

private val BlankColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF261F42),
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

// 타이포 — Expressive: 디스플레이·헤드라인을 더 굵고 크게, 본문은 읽기용 행간을 넉넉히.
private val BlankTypography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
)

@Composable
fun BlankTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BlankColors,
        typography = BlankTypography,
        shapes = BlankShapes,
        content = content
    )
}
