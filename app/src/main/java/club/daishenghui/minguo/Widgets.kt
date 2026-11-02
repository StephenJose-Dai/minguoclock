package club.daishenghui.minguo

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** 所有小组件共用的刷新逻辑 */
open class BaseWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        Widgets.scope.launch {
            try {
                Quotes.maybeRotate(context, screenOn = false)
            } finally {
                Widgets.updateAll(context)
                pending.finish()
            }
        }
    }

    override fun onEnabled(context: Context) = Widgets.scheduleMidnight(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Widgets.ACTION_REFRESH,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            -> Widgets.updateAll(context)

            Widgets.ACTION_NEXT_QUOTE -> {
                val pending = goAsync()
                Widgets.scope.launch {
                    try {
                        Quotes.next(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}

/** 4x2：时分秒 + 民国日期 + 农历 + 天气 */
class ClockWidget : BaseWidget()

/** 2x2：日历方块 */
class DateWidget : BaseWidget()

/** 4x1：天气 + 建议 */
class WeatherWidget : BaseWidget()

/** 4x1：名言（点按换一句） */
class QuoteWidget : BaseWidget()

object Widgets {
    const val ACTION_REFRESH = "club.daishenghui.minguo.REFRESH_WIDGET"
    const val ACTION_NEXT_QUOTE = "club.daishenghui.minguo.NEXT_QUOTE"
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    data class Kind(val cls: Class<out AppWidgetProvider>, val hans: String, val hant: String, val en: String) {
        fun label(lang: Lang = L.current) = tr(hans, hant, en, lang)
    }

    val kinds = listOf(
        Kind(ClockWidget::class.java, "时钟（4×2）", "時鐘（4×2）", "Clock (4×2)"),
        Kind(DateWidget::class.java, "日历方块（2×2）", "日曆方塊（2×2）", "Calendar (2×2)"),
        Kind(WeatherWidget::class.java, "天气（4×1）", "天氣（4×1）", "Weather (4×1)"),
        Kind(QuoteWidget::class.java, "名言（4×1）", "名言（4×1）", "Quote (4×1)"),
    )

    fun updateAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        update(context, mgr, ClockWidget::class.java) { clock(it) }
        update(context, mgr, DateWidget::class.java) { date(it) }
        update(context, mgr, WeatherWidget::class.java) { weather(it) }
        update(context, mgr, QuoteWidget::class.java) { quote(it) }
        scheduleMidnight(context)
    }

    private fun update(context: Context, mgr: AppWidgetManager, cls: Class<out AppWidgetProvider>, build: (Context) -> RemoteViews) {
        val ids = mgr.getAppWidgetIds(ComponentName(context, cls))
        if (ids.isEmpty()) return
        val views = runCatching { build(context) }.getOrNull() ?: return
        ids.forEach { mgr.updateAppWidget(it, views) }
    }

    fun requestPin(context: Context, cls: Class<out AppWidgetProvider>): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val mgr = AppWidgetManager.getInstance(context)
        if (!mgr.isRequestPinAppWidgetSupported) return false
        return mgr.requestPinAppWidget(ComponentName(context, cls), null, null)
    }

    // ---------- 样式 ----------

    private data class Colors(val main: Int, val sub: Int, val bg: Int)

    private fun colors(context: Context) = when (Prefs(context).widgetStyle) {
        1 -> Colors(0xFF1E1E1E.toInt(), 0xB31E1E1E.toInt(), 0)
        2 -> Colors(0xFFFFFFFF.toInt(), 0xCCFFFFFF.toInt(), R.drawable.widget_card_bg)
        else -> Colors(0xFFFFFFFF.toInt(), 0xDDFFFFFF.toInt(), 0)
    }

    private fun RemoteViews.applyBg(c: Colors) = setInt(R.id.widget_root, "setBackgroundResource", c.bg)

    // ---------- 各小组件 ----------

    private fun clock(context: Context): RemoteViews {
        val p = Prefs(context)
        val style = p.style()
        val c = colors(context)
        val today = LocalDate.now()
        val rv = RemoteViews(context.packageName, R.layout.widget_clock)
        rv.applyBg(c)
        rv.setTextViewText(R.id.widget_date, RocCalendar.dateLine(today, style) + "  " + RocCalendar.weekday(today, style.lang))
        rv.setTextViewText(R.id.widget_lunar, RocCalendar.lunarLine(System.currentTimeMillis(), style))
        rv.setViewVisibility(R.id.widget_seconds, if (p.showSeconds) View.VISIBLE else View.GONE)
        rv.setTextColor(R.id.widget_time, c.main)
        rv.setTextColor(R.id.widget_date, c.main)
        rv.setTextColor(R.id.widget_seconds, c.sub)
        rv.setTextColor(R.id.widget_lunar, c.sub)

        Weather.init(context)
        val w = Weather.state.value
        if (p.weatherEnabled && w != null) {
            rv.setViewVisibility(R.id.widget_weather_row, View.VISIBLE)
            rv.setImageViewResource(R.id.widget_weather_icon, w.cond.icon(w.isDay))
            rv.setInt(R.id.widget_weather_icon, "setColorFilter", c.main)
            rv.setTextViewText(R.id.widget_weather, Weather.summary(w, style.lang) + "  ·  " + Weather.cityLabel(w, style.lang))
            rv.setTextColor(R.id.widget_weather, c.sub)
        } else {
            rv.setViewVisibility(R.id.widget_weather_row, View.GONE)
        }
        rv.setOnClickPendingIntent(R.id.widget_root, activity(context, 2, alarmsIntent(context)))
        return rv
    }

    private fun date(context: Context): RemoteViews {
        val style = Prefs(context).style()
        val c = colors(context)
        val today = LocalDate.now()
        val rv = RemoteViews(context.packageName, R.layout.widget_date)
        rv.applyBg(c)
        rv.setTextViewText(R.id.widget_ym, RocCalendar.yearMonth(today, style))
        rv.setTextViewText(R.id.widget_day, today.dayOfMonth.toString())
        rv.setTextViewText(R.id.widget_week, RocCalendar.weekday(today, style.lang))
        rv.setTextViewText(R.id.widget_lunar, RocCalendar.lunarShort(System.currentTimeMillis(), style))
        rv.setTextColor(R.id.widget_ym, c.sub)
        rv.setTextColor(R.id.widget_day, c.main)
        rv.setTextColor(R.id.widget_week, c.main)
        rv.setTextColor(R.id.widget_lunar, c.sub)
        val calendar = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val target = if (calendar.resolveActivity(context.packageManager) != null) calendar else appIntent(context)
        rv.setOnClickPendingIntent(R.id.widget_root, activity(context, 3, target))
        return rv
    }

    private fun weather(context: Context): RemoteViews {
        val p = Prefs(context)
        val lang = p.style().lang
        val c = colors(context)
        val rv = RemoteViews(context.packageName, R.layout.widget_weather)
        rv.applyBg(c)
        Weather.init(context)
        val w = Weather.state.value
        if (w == null || !p.weatherEnabled) {
            rv.setTextViewText(R.id.widget_temp, "--°")
            rv.setTextViewText(R.id.widget_cond, tr("暂无天气", "暫無天氣", "No weather yet", lang))
            rv.setTextViewText(R.id.widget_city, "")
            rv.setTextViewText(R.id.widget_tip, tr("打开 App 设置位置", "開啟 App 設定位置", "Open the app to set a location", lang))
            rv.setImageViewResource(R.id.widget_weather_icon, R.drawable.weather_cloud)
        } else {
            rv.setImageViewResource(R.id.widget_weather_icon, w.cond.icon(w.isDay))
            rv.setTextViewText(R.id.widget_temp, Weather.temp(w.temp))
            val range = w.today?.let { "  " + Weather.temp(it.min) + "/" + Weather.temp(it.max) } ?: ""
            rv.setTextViewText(R.id.widget_cond, w.cond.label(lang) + range)
            val aqi = w.aqi?.let { "  " + tr("空气", "空氣", "AQI", lang) + " " + Weather.aqiLabel(it, lang) } ?: ""
            rv.setTextViewText(R.id.widget_city, Weather.cityLabel(w, lang) + aqi)
            rv.setTextViewText(R.id.widget_tip, Weather.advice(w, lang).firstOrNull() ?: "")
        }
        rv.setInt(R.id.widget_weather_icon, "setColorFilter", c.main)
        rv.setTextColor(R.id.widget_temp, c.main)
        rv.setTextColor(R.id.widget_cond, c.main)
        rv.setTextColor(R.id.widget_city, c.sub)
        rv.setTextColor(R.id.widget_tip, c.sub)
        val miWeather = context.packageManager.getLaunchIntentForPackage("com.miui.weather2")
        rv.setOnClickPendingIntent(R.id.widget_root, activity(context, 4, miWeather ?: appIntent(context)))
        return rv
    }

    private fun quote(context: Context): RemoteViews {
        val lang = Prefs(context).style().lang
        val c = colors(context)
        val rv = RemoteViews(context.packageName, R.layout.widget_quote)
        rv.applyBg(c)
        Quotes.init(context)
        val q = Quotes.state.value?.let { Quotes.display(it, lang) }
        rv.setTextViewText(R.id.widget_quote, if (q == null) "" else "「" + q.text + "」")
        rv.setTextViewText(R.id.widget_author, if (q == null || q.author.isBlank()) "" else "—— " + q.author)
        rv.setTextColor(R.id.widget_quote, c.main)
        rv.setTextColor(R.id.widget_author, c.sub)
        val next = PendingIntent.getBroadcast(
            context, 5,
            Intent(context, QuoteWidget::class.java).setAction(ACTION_NEXT_QUOTE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        rv.setOnClickPendingIntent(R.id.widget_root, next)
        return rv
    }

    // ---------- 工具 ----------

    private fun appIntent(context: Context) = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun alarmsIntent(context: Context): Intent {
        val alarms = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (alarms.resolveActivity(context.packageManager) != null) alarms else appIntent(context)
    }

    private fun activity(context: Context, code: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** 每天零点刷新日期/农历 */
    fun scheduleMidnight(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val next = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 2_000
        val pi = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, ClockWidget::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC, next, pi)
        } else {
            am.setWindow(AlarmManager.RTC, next, 10 * 60 * 1000L, pi)
        }
    }
}
