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
import java.time.LocalDate
import java.time.ZoneId

/**
 * 桌面小组件。时分秒由系统 TextClock 自己走（省电、准确），
 * 民国日期和农历每天零点刷新一次。
 */
class ClockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = buildViews(context)
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
        scheduleMidnight(context)
    }

    override fun onEnabled(context: Context) = scheduleMidnight(context)

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(refreshIntent(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            -> updateAll(context)
        }
    }

    companion object {
        private const val ACTION_REFRESH = "club.daishenghui.minguo.REFRESH_WIDGET"

        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, ClockWidget::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { mgr.updateAppWidget(it, views) }
            scheduleMidnight(context)
        }

        /** 请求把小组件固定到桌面（启动器支持时） */
        fun requestPin(context: Context): Boolean {
            val mgr = AppWidgetManager.getInstance(context)
            if (!mgr.isRequestPinAppWidgetSupported) return false
            return mgr.requestPinAppWidget(ComponentName(context, ClockWidget::class.java), null, null)
        }

        private fun buildViews(context: Context): RemoteViews {
            val prefs = Prefs(context)
            val style = prefs.style()
            val today = LocalDate.now()
            val rv = RemoteViews(context.packageName, R.layout.widget_clock)

            rv.setTextViewText(R.id.widget_date, RocCalendar.dateLine(today, style) + "  " + RocCalendar.weekday(today))
            rv.setTextViewText(R.id.widget_lunar, RocCalendar.lunarLine(System.currentTimeMillis(), style))
            rv.setViewVisibility(R.id.widget_seconds, if (prefs.showSeconds) View.VISIBLE else View.GONE)

            val main: Int
            val sub: Int
            val bg: Int
            when (prefs.widgetStyle) {
                1 -> { main = 0xFF1E1E1E.toInt(); sub = 0xB31E1E1E.toInt(); bg = 0 }
                2 -> { main = 0xFFFFFFFF.toInt(); sub = 0xCCFFFFFF.toInt(); bg = R.drawable.widget_card_bg }
                else -> { main = 0xFFFFFFFF.toInt(); sub = 0xDDFFFFFF.toInt(); bg = 0 }
            }
            rv.setInt(R.id.widget_root, "setBackgroundResource", bg)
            rv.setTextColor(R.id.widget_time, main)
            rv.setTextColor(R.id.widget_date, main)
            rv.setTextColor(R.id.widget_seconds, sub)
            rv.setTextColor(R.id.widget_lunar, sub)

            rv.setOnClickPendingIntent(R.id.widget_root, clickIntent(context))
            return rv
        }

        /** 点小组件打开系统闹钟，没有的话打开本应用 */
        private fun clickIntent(context: Context): PendingIntent {
            val alarms = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val target = if (alarms.resolveActivity(context.packageManager) != null) {
                alarms
            } else {
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return PendingIntent.getActivity(
                context, 2, target, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        private fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, ClockWidget::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun scheduleMidnight(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java)
            val next = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 2_000
            val pi = refreshIntent(context)
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC, next, pi)
            } else {
                am.setWindow(AlarmManager.RTC, next, 10 * 60 * 1000L, pi)
            }
        }
    }
}
