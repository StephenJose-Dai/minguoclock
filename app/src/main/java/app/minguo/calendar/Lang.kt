package app.minguo.calendar

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

enum class Lang { HANS, HANT, EN }

/** 界面语言：默认跟随系统，App 里手动设置过就用手动的 */
object L {
    /** 当前语言（Compose 状态，切换后界面自动刷新） */
    var current by mutableStateOf(Lang.HANS)
        private set

    /**
     * setting: 0 跟随系统，1 简体，2 繁體。只支持简繁两种（以前的 3 = English 当作跟随系统）。
     */
    fun resolve(setting: Int): Lang = when (setting) {
        1 -> Lang.HANS
        2 -> Lang.HANT
        else -> fromSystem()
    }

    /** 跟随系统：繁体地区/繁体系统用繁體，其它（包括英文等非中文系统）都用简体 */
    fun fromSystem(): Lang {
        val loc = Locale.getDefault()
        if (loc.language != "zh") return if (loc.country in setOf("TW", "HK", "MO")) Lang.HANT else Lang.HANS
        return when {
            loc.script == "Hant" -> Lang.HANT
            loc.script == "Hans" -> Lang.HANS
            loc.country in setOf("TW", "HK", "MO") -> Lang.HANT
            else -> Lang.HANS
        }
    }

    fun refresh(context: Context): Lang {
        current = resolve(Prefs(context).language)
        return current
    }

    /** 非 Compose 场景（小组件、通知）直接取 */
    fun of(context: Context): Lang = resolve(Prefs(context).language)
}

/** 三语文本：简体 / 繁體 / English */
fun tr(hans: String, hant: String, en: String, lang: Lang = L.current): String = when (lang) {
    Lang.HANS -> hans
    Lang.HANT -> hant
    Lang.EN -> en
}
