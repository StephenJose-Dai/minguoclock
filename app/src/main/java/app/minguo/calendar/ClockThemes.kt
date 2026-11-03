package app.minguo.calendar

import androidx.compose.ui.graphics.Color

data class ClockTheme(
    val name: String,
    val colors: List<Color>,
    val glow: Color,
    val text: Color,
    val sub: Color,
)

private val White = Color(0xFFFFFFFF)
private val WhiteSub = Color(0xB3FFFFFF)

val ClockThemes = listOf(
    ClockTheme("墨夜", listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)), Color(0x335EC8F2), White, WhiteSub),
    ClockTheme("晨曦", listOf(Color(0xFF6A3093), Color(0xFFC94B7D), Color(0xFFFF9A6B)), Color(0x40FFD3A5), White, WhiteSub),
    ClockTheme("青瓷", listOf(Color(0xFF0B3C49), Color(0xFF1F6F6B), Color(0xFF71B280)), Color(0x33C8F7DC), White, WhiteSub),
    ClockTheme("极光", listOf(Color(0xFF070B25), Color(0xFF16305C), Color(0xFF117A65)), Color(0x4038F5B0), White, WhiteSub),
    ClockTheme("朱砂", listOf(Color(0xFF2B0606), Color(0xFF7A1515), Color(0xFFB83227)), Color(0x33FFC48A), White, WhiteSub),
    ClockTheme("宣纸", listOf(Color(0xFFF7F1E3), Color(0xFFEADFC8), Color(0xFFDCCBA8)), Color(0x40FFFFFF), Color(0xFF3B2A1A), Color(0xA63B2A1A)),
)
