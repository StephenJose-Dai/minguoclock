package club.daishenghui.minguo

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var lockEnabled: Boolean
        get() = sp.getBoolean(K_LOCK, false)
        set(v) = sp.edit().putBoolean(K_LOCK, v).apply()

    var themeIndex: Int
        get() = sp.getInt(K_THEME, 0).coerceIn(ClockThemes.indices)
        set(v) = sp.edit().putInt(K_THEME, v).apply()

    var traditional: Boolean
        get() = sp.getBoolean(K_TRAD, true)
        set(v) = sp.edit().putBoolean(K_TRAD, v).apply()

    var chineseNumerals: Boolean
        get() = sp.getBoolean(K_CN_NUM, false)
        set(v) = sp.edit().putBoolean(K_CN_NUM, v).apply()

    var showSeconds: Boolean
        get() = sp.getBoolean(K_SECONDS, true)
        set(v) = sp.edit().putBoolean(K_SECONDS, v).apply()

    /** 0 = 透明白字，1 = 透明深色字，2 = 半透明卡片 */
    var widgetStyle: Int
        get() = sp.getInt(K_WIDGET, 0)
        set(v) = sp.edit().putInt(K_WIDGET, v).apply()

    fun style() = DisplayStyle(traditional, chineseNumerals)

    private companion object {
        const val K_LOCK = "lock_enabled"
        const val K_THEME = "theme"
        const val K_TRAD = "traditional"
        const val K_CN_NUM = "chinese_numerals"
        const val K_SECONDS = "show_seconds"
        const val K_WIDGET = "widget_style"
    }
}
