package club.daishenghui.minguo

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * 桌面小组件（一个整块）：时分秒 + 民国日期 + 农历 + 天气 + 名言。
 * 点天气打开本 App 的天气页，点名言换一句，点其他地方打开本 App。
 */
class ClockWidget : AppWidgetProvider() {
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

    /** 用户拖动调整了小组件大小：按新尺寸重新排版 */
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        Widgets.updateAll(context)
    }

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

object Widgets {
    const val ACTION_REFRESH = "club.daishenghui.minguo.REFRESH_WIDGET"
    const val ACTION_NEXT_QUOTE = "club.daishenghui.minguo.NEXT_QUOTE"
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 运行状态用：上次成功更新的时间、上次的错误 */
    @Volatile
    var lastUpdate = 0L
        private set

    @Volatile
    var lastError: String? = null
        private set

    /** 本应用（本安装包）放在桌面上的小组件数量。桌面上看得到、这里却是 0，说明那个小组件属于另一个安装包（如正式版/调试版） */
    fun placedCount(context: Context): Int =
        runCatching { AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, ClockWidget::class.java)).size }
            .getOrDefault(0)

    fun updateAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, ClockWidget::class.java))
        if (ids.isNotEmpty()) {
            // 每个小组件按自己的尺寸排版（字号跟着宽高缩放）
            ids.forEach { id ->
                try {
                    mgr.updateAppWidget(id, build(context, mgr.getAppWidgetOptions(id)))
                    lastUpdate = System.currentTimeMillis()
                    lastError = null
                } catch (e: Exception) {
                    lastError = e.javaClass.simpleName + ": " + (e.message ?: "")
                    // 更新失败时桌面会一直留着旧样子（比如旧版的卡片底框），所以退一步只画时间和日期，保证样式更新到位
                    Log.e("Widgets", "小组件更新失败，改用简化版", e)
                    runCatching { mgr.updateAppWidget(id, fallback(context)) }
                }
            }
        }
        scheduleMidnight(context)
    }

    fun requestPin(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val mgr = AppWidgetManager.getInstance(context)
        if (!mgr.isRequestPinAppWidgetSupported) return false
        return mgr.requestPinAppWidget(ComponentName(context, ClockWidget::class.java), null, null)
    }

    /**
     * 按小组件当前尺寸算字号缩放比例。默认 4×3（约 250×180dp）为 1.0，
     * 宽度由桌面网格限制、不会超过屏幕；高度在 widget_clock_info.xml 里限制了上限。
     */
    private fun scaleFor(options: Bundle?): Float {
        val w = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
        val h = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
        if (w <= 0 || h <= 0) return 1f
        return minOf(w / 250f, h / 180f).coerceIn(0.55f, 1.6f)
    }

    /** 背景：始终全透明，不留任何底色和边框 */
    @Suppress("UNUSED_PARAMETER")
    private fun applyBackground(rv: RemoteViews, style: Int) {
        rv.setInt(R.id.widget_root, "setBackgroundResource", 0)
        rv.setInt(R.id.widget_root, "setBackgroundColor", android.graphics.Color.TRANSPARENT)
    }

    /** 简化版：只有时间、日期，透明背景 */
    private fun fallback(context: Context): RemoteViews {
        val p = Prefs(context)
        val rv = RemoteViews(context.packageName, R.layout.widget_clock)
        applyBackground(rv, p.widgetStyle)
        val today = LocalDate.now()
        rv.setTextViewText(R.id.widget_date, RocCalendar.dateLine(today, p.style()))
        rv.setViewVisibility(R.id.widget_weather_row, View.GONE)
        rv.setViewVisibility(R.id.widget_quote, View.GONE)
        return rv
    }

    private fun build(context: Context, options: Bundle?): RemoteViews {
        val p = Prefs(context)
        val style = p.style()
        val lang = style.lang
        // 0 = 白字，1 = 深色字；背景始终透明，没有任何底框
        val dark = p.widgetStyle == 1
        val main = if (dark) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt()
        val sub = if (dark) 0xB31E1E1E.toInt() else 0xDDFFFFFF.toInt()
        val today = LocalDate.now()
        val rv = RemoteViews(context.packageName, R.layout.widget_clock)
        applyBackground(rv, p.widgetStyle)
        val k = scaleFor(options)
        fun size(id: Int, sp: Float) = rv.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sp * k)
        size(R.id.widget_time, 56f)
        size(R.id.widget_seconds, 22f)
        size(R.id.widget_date, 16f)
        size(R.id.widget_lunar, 13f)
        size(R.id.widget_weather, 13f)
        size(R.id.widget_quote, 14f)

        rv.setTextViewText(R.id.widget_date, RocCalendar.dateLine(today, style) + "  " + RocCalendar.weekday(today, lang))
        rv.setTextViewText(R.id.widget_lunar, RocCalendar.lunarLine(System.currentTimeMillis(), style))
        rv.setViewVisibility(R.id.widget_seconds, if (p.showSeconds) View.VISIBLE else View.GONE)
        rv.setTextColor(R.id.widget_time, main)
        rv.setTextColor(R.id.widget_date, main)
        rv.setTextColor(R.id.widget_seconds, sub)
        rv.setTextColor(R.id.widget_lunar, sub)

        // 天气
        Weather.init(context)
        val w = Weather.state.value
        if (p.weatherEnabled && w != null) {
            rv.setViewVisibility(R.id.widget_weather_row, View.VISIBLE)
            rv.setImageViewResource(R.id.widget_weather_icon, w.cond.icon(w.isDay))
            rv.setInt(R.id.widget_weather_icon, "setColorFilter", main)
            val tip = Weather.advice(w, lang).firstOrNull()
            rv.setTextViewText(
                R.id.widget_weather,
                Weather.summary(w, lang) + "  ·  " + Weather.cityLabel(w, lang) + (if (tip != null) "\n$tip" else ""),
            )
            rv.setTextColor(R.id.widget_weather, sub)
        } else {
            rv.setViewVisibility(R.id.widget_weather_row, View.GONE)
        }

        // 名言（不显示出处）
        Quotes.init(context)
        val q = Quotes.state.value?.let { Quotes.display(it, lang) }
        if (q != null) {
            rv.setViewVisibility(R.id.widget_quote, View.VISIBLE)
            rv.setTextViewText(R.id.widget_quote, "「" + q.text + "」")
            rv.setTextColor(R.id.widget_quote, main)
        } else {
            rv.setViewVisibility(R.id.widget_quote, View.GONE)
        }

        rv.setOnClickPendingIntent(R.id.widget_root, openApp(context, 2, null))
        rv.setOnClickPendingIntent(R.id.widget_weather_row, openApp(context, 3, MainActivity.PAGE_WEATHER))
        rv.setOnClickPendingIntent(
            R.id.widget_quote,
            PendingIntent.getBroadcast(
                context, 5,
                Intent(context, ClockWidget::class.java).setAction(ACTION_NEXT_QUOTE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        return rv
    }

    /** 打开本 App（可指定页面） */
    private fun openApp(context: Context, code: Int, page: String?): PendingIntent {
        val i = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (page != null) i.putExtra(MainActivity.EXTRA_PAGE, page)
        return PendingIntent.getActivity(context, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

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
