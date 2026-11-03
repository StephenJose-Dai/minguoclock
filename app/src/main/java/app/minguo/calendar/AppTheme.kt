package app.minguo.calendar

import android.app.Activity
import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import java.time.LocalTime

/**
 * App 界面主题：暗黑（默认，黑金配色）/ 白天 / 自动（按时间：6:00–18:00 白天，其余暗黑）。
 * 只影响 App 的设置界面；锁屏的颜色跟随壁纸，不受影响。
 */
object AppTheme {
    const val DARK = 0
    const val LIGHT = 1
    const val AUTO = 2

    /** 自动模式的白天时段 */
    private const val DAY_START = 6
    private const val DAY_END = 18

    /** 当前是否暗黑（Compose 状态，切换后所有界面自动刷新） */
    var isDark by mutableStateOf(true)
        private set

    fun resolve(mode: Int): Boolean = when (mode) {
        LIGHT -> false
        AUTO -> LocalTime.now().hour !in DAY_START until DAY_END
        else -> true
    }

    fun refresh(context: Context) {
        isDark = resolve(Prefs(context).themeMode)
    }
}

/** 两套配色：暗黑 = 黑金；白天 = 米白底 + 深金色（在浅色背景上更清楚） */
object Palette {
    private val d get() = AppTheme.isDark
    val accent get() = if (d) Color(0xFFE8C77A) else Color(0xFF9A6B12)
    val onAccent get() = if (d) Color(0xFF2A1F05) else Color(0xFFFFFFFF)
    val page get() = if (d) Color(0xFF0E1116) else Color(0xFFF6F2EA)
    val card get() = if (d) Color(0xFF171C24) else Color(0xFFFFFFFF)
    val field get() = if (d) Color(0xFF0F1319) else Color(0xFFEFE9DE)
    val text get() = if (d) Color(0xFFFFFFFF) else Color(0xFF1F1A14)
    val sub get() = if (d) Color(0xFF8E98A8) else Color(0xFF6E6658)
    val good get() = if (d) Color(0xFF5BD68A) else Color(0xFF1E9E57)
    val warn get() = if (d) Color(0xFFFFB454) else Color(0xFFC77700)
    val danger get() = if (d) Color(0xFFFF6B6B) else Color(0xFFD93C3C)
    val hairline get() = if (d) Color(0x33FFFFFF) else Color(0x22000000)
}

/**
 * 套用主题：Material 配色、状态栏/导航栏图标深浅、窗口底色；自动模式下每分钟检查一次是否该切换。
 */
@Composable
fun AppMaterialTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = AppTheme.isDark
    LaunchedEffect(Unit) {
        while (true) {
            AppTheme.refresh(ctx)
            delay(60_000)
        }
    }
    (ctx as? Activity)?.let { activity ->
        SideEffect {
            activity.window.setBackgroundDrawable(ColorDrawable(Palette.page.toArgb()))
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = Palette.accent, onPrimary = Palette.onAccent,
            background = Palette.page, onBackground = Palette.text,
            surface = Palette.card, onSurface = Palette.text,
            surfaceVariant = Palette.field, onSurfaceVariant = Palette.sub,
        )
    } else {
        lightColorScheme(
            primary = Palette.accent, onPrimary = Palette.onAccent,
            background = Palette.page, onBackground = Palette.text,
            surface = Palette.card, onSurface = Palette.text,
            surfaceVariant = Palette.field, onSurfaceVariant = Palette.sub,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
