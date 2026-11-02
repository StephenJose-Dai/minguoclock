package club.daishenghui.minguo

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private fun bool(k: String, def: Boolean) = sp.getBoolean(k, def)
    private fun putBool(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()
    private fun int(k: String, def: Int) = sp.getInt(k, def)
    private fun putInt(k: String, v: Int) = sp.edit().putInt(k, v).apply()
    private fun str(k: String) = sp.getString(k, null)
    private fun putStr(k: String, v: String?) = sp.edit().putString(k, v).apply()
    private fun long(k: String) = sp.getLong(k, 0L)
    private fun putLong(k: String, v: Long) = sp.edit().putLong(k, v).apply()

    fun clearAll() = sp.edit().clear().commit()

    // ---- 锁屏 ----
    var lockEnabled: Boolean get() = bool("lock_enabled", false); set(v) = putBool("lock_enabled", v)

    /** 0 = 保留系统密码（覆盖在系统锁屏上），1 = 完全替代（系统锁屏设为“无”，悬浮窗锁屏） */
    var lockMode: Int get() = int("lock_mode", MODE_REPLACE); set(v) = putInt("lock_mode", v)

    /** 完全替代模式下的锁屏密码（加盐哈希），空 = 不设密码 */
    var pinHash: String get() = str("pin_hash") ?: ""; set(v) = putStr("pin_hash", v)
    var pinSalt: String get() = str("pin_salt") ?: ""; set(v) = putStr("pin_salt", v)
    var pinLength: Int get() = int("pin_len", 0); set(v) = putInt("pin_len", v)

    /** 悬浮窗锁屏当前是否处于锁定状态（服务崩溃重启后据此恢复锁屏，防止绕过） */
    var overlayLocked: Boolean get() = bool("overlay_locked", false); set(v) = sp.edit().putBoolean("overlay_locked", v).commit().let { }

    /** 通知栏常驻民国日期 */
    var dateNotification: Boolean get() = bool("date_notification", true); set(v) = putBool("date_notification", v)

    // ---- 语言与日期 ----
    /** 0 跟随系统，1 简体，2 繁體，3 English */
    var language: Int get() = int("language", 0); set(v) = putInt("language", v)
    var fullEra: Boolean get() = bool("full_era", false); set(v) = putBool("full_era", v)
    var chineseNumerals: Boolean get() = bool("chinese_numerals", false); set(v) = putBool("chinese_numerals", v)
    var showSeconds: Boolean get() = bool("show_seconds", true); set(v) = putBool("show_seconds", v)

    fun style() = DisplayStyle(L.resolve(language), fullEra, chineseNumerals)

    // ---- 外观 ----
    var themeIndex: Int
        get() = int("theme", 0).coerceIn(ClockThemes.indices)
        set(v) = putInt("theme", v)

    /** 0 主题渐变，1 自选图片，2 必应每日壁纸 */
    var wallpaperMode: Int get() = int("wallpaper_mode", 0); set(v) = putInt("wallpaper_mode", v)
    var wallpaperDim: Int get() = int("wallpaper_dim", 20); set(v) = putInt("wallpaper_dim", v)
    var bingCopyright: String get() = str("bing_copyright") ?: ""; set(v) = putStr("bing_copyright", v)
    var bingDate: String get() = str("bing_date") ?: ""; set(v) = putStr("bing_date", v)
    var bingSize: String get() = str("bing_size") ?: ""; set(v) = putStr("bing_size", v)
    var showBingInfo: Boolean get() = bool("show_bing_info", true); set(v) = putBool("show_bing_info", v)

    /** 锁屏签名（单行跑马灯，不限字数） */
    var signature: String get() = str("signature") ?: ""; set(v) = putStr("signature", v)

    /** 锁屏布局 JSON（元素位置/字号/字体 + 贴图） */
    var layoutJson: String? get() = str("layout_json"); set(v) = putStr("layout_json", v)

    /** 0 = 透明白字，1 = 透明深色字，2 = 半透明卡片 */
    var widgetStyle: Int get() = int("widget_style", 0); set(v) = putInt("widget_style", v)

    // ---- 名言 ----
    var quoteEnabled: Boolean get() = bool("quote_enabled", true); set(v) = putBool("quote_enabled", v)

    /** 0 内置，1 一言（在线），2 混合 */
    var quoteSource: Int get() = int("quote_source", 2); set(v) = putInt("quote_source", v)

    /** 换句间隔（分钟），0 = 每次亮屏 */
    var quoteInterval: Int get() = int("quote_interval", 60); set(v) = putInt("quote_interval", v)
    var quoteCurrent: String? get() = str("quote_current"); set(v) = putStr("quote_current", v)
    var quoteChangedAt: Long get() = long("quote_changed_at"); set(v) = putLong("quote_changed_at", v)
    var quoteDeck: String get() = str("quote_deck") ?: ""; set(v) = putStr("quote_deck", v)
    var quoteSeen: String get() = str("quote_seen") ?: ""; set(v) = putStr("quote_seen", v)

    // ---- 天气 ----
    var weatherEnabled: Boolean get() = bool("weather_enabled", true); set(v) = putBool("weather_enabled", v)
    var autoLocate: Boolean get() = bool("auto_locate", true); set(v) = putBool("auto_locate", v)
    var useSystemWeather: Boolean get() = bool("use_system_weather", true); set(v) = putBool("use_system_weather", v)
    /** 当前位置的国家码与省市区（腾讯天气、地区判断用） */
    var placeCountry: String get() = str("place_country") ?: ""; set(v) = putStr("place_country", v)
    var placeProvince: String get() = str("place_province") ?: ""; set(v) = putStr("place_province", v)
    var placeCity: String get() = str("place_city") ?: ""; set(v) = putStr("place_city", v)
    var placeDistrict: String get() = str("place_district") ?: ""; set(v) = putStr("place_district", v)

    /** 线路：0 自动判断，1 强制中国大陆，2 强制境外 */
    var regionOverride: Int get() = int("region_override", 0); set(v) = putInt("region_override", v)

    var lat: Double
        get() = str("weather_lat")?.toDoubleOrNull() ?: Double.NaN
        set(v) = putStr("weather_lat", v.toString())

    var lon: Double
        get() = str("weather_lon")?.toDoubleOrNull() ?: Double.NaN
        set(v) = putStr("weather_lon", v.toString())

    var cityName: String get() = str("weather_city") ?: ""; set(v) = putStr("weather_city", v)
    var locatedAt: Long get() = long("located_at"); set(v) = putLong("located_at", v)
    var weatherCache: String? get() = str("weather_cache_v3"); set(v) = putStr("weather_cache_v3", v)

    companion object {
        const val NAME = "settings"
        const val MODE_KEEP_SYSTEM = 0
        const val MODE_REPLACE = 1
    }
}
