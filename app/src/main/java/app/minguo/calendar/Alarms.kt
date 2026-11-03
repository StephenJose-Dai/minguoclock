package app.minguo.calendar

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings
import java.time.Instant
import java.time.ZoneId

/**
 * 下一个闹钟（只认时钟应用里设的闹钟）。
 *
 * 锁屏上已不再显示闹钟；这里只给“完全替代”模式用：闹钟响时让锁屏让开。
 *
 * 系统只提供 AlarmManager.nextAlarmClock = “所有应用里最早的那一个提醒”，任何应用都能写
 * （日历、待办、健康、某些 App 的定时任务）。所以：
 * 1. 最早的那个是时钟应用设的 → 就是闹钟，显示，并记下来；
 *    设定者拿不到（部分系统把 showIntent 留空或由系统代设）时，用时钟应用写给系统的“下一个闹钟文字”
 *    核对时间：文字里的时:分和它一致才认。只是“文字存在”不算（那段文字可能是很久以前留下的旧值）。
 * 2. 最早的那个是别的应用设的 → 不显示它；但如果之前记下的时钟闹钟还没到点、且不早于这个提醒，
 *    说明时钟闹钟只是被挡在后面，继续显示记下的那个。
 *    如果记下的闹钟比当前最早的提醒还早，说明它已经被关掉/删掉，清除。
 * 3. 系统里一个提醒都没有 → 没有闹钟，清除记录。
 */
object Alarms {
    /** 常见品牌的时钟应用包名 */
    private val KNOWN_CLOCKS = setOf(
        "com.android.deskclock",            // AOSP、小米/红米（HyperOS / MIUI）
        "com.google.android.deskclock",     // Google 时钟
        "com.miui.clock",
        "com.xiaomi.clock",
        "com.sec.android.app.clockpackage", // 三星
        "com.huawei.deskclock",             // 华为
        "com.hihonor.deskclock",            // 荣耀
        "com.coloros.alarmclock",           // OPPO / realme
        "com.oplus.alarmclock",             // OPPO / 一加（新）
        "com.oneplus.deskclock",            // 一加（旧）
        "com.android.BBKClock",             // vivo / iQOO
        "com.vivo.alarmclock",
        "com.meizu.flyme.alarmclock",       // 魅族
        "com.zui.deskclock",                // 联想 ZUI
        "com.lenovo.deskclock",
        "com.asus.deskclock",               // 华硕
        "com.transsion.deskclock",          // 传音
    )

    /** 系统代设时显示的“设定者” */
    private val SYSTEM_CREATORS = setOf("android", "com.android.systemui")

    @Volatile
    private var clockPackages: Set<String>? = null

    /** 内置名单 + 本机所有能“显示闹钟 / 设置闹钟”的应用 */
    private fun clocks(context: Context): Set<String> = clockPackages ?: run {
        val pm = context.packageManager
        val found = listOf(AlarmClock.ACTION_SHOW_ALARMS, AlarmClock.ACTION_SET_ALARM).flatMap { action ->
            runCatching { pm.queryIntentActivities(Intent(action), 0).map { it.activityInfo.packageName } }
                .getOrDefault(emptyList())
        }
        (KNOWN_CLOCKS + found).also { clockPackages = it }
    }

    private fun systemInfo(context: Context): AlarmManager.AlarmClockInfo? =
        runCatching { context.getSystemService(AlarmManager::class.java)?.nextAlarmClock }.getOrNull()

    /** 时钟应用写给系统的下一个闹钟文字（可能是旧值，只用来核对时间） */
    @Suppress("DEPRECATION")
    private fun formatted(context: Context): String? =
        runCatching { Settings.System.getString(context.contentResolver, Settings.System.NEXT_ALARM_FORMATTED) }
            .getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    /** 文字里有没有这个时间（24 小时制 07:00 / 7:00，或 12 小时制 7:00） */
    private fun textMatches(text: String?, millis: Long): Boolean {
        if (text == null) return false
        val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()
        val h12 = if (t.hour % 12 == 0) 12 else t.hour % 12
        val m = "%02d".format(t.minute)
        return listOf("%02d:$m".format(t.hour), "${t.hour}:$m", "$h12:$m").any { text.contains(it) }
    }

    private enum class Kind { CLOCK, OTHER }

    private fun classify(context: Context, info: AlarmManager.AlarmClockInfo): Kind {
        val creator = info.showIntent?.creatorPackage
        return when {
            creator != null && creator in clocks(context) -> Kind.CLOCK
            (creator == null || creator in SYSTEM_CREATORS) && textMatches(formatted(context), info.triggerTime) -> Kind.CLOCK
            else -> Kind.OTHER
        }
    }

    /** 下一个闹钟的响铃时间；没有设闹钟则 null。每次调用都会顺便更新记录 */
    fun next(context: Context): Long? {
        val p = Prefs(context)
        val now = System.currentTimeMillis()
        val info = systemInfo(context)
        if (info == null) {
            // 系统里一个提醒都没有 = 没有任何闹钟
            p.clockAlarmAt = 0L
            return null
        }
        // A. 系统最早的提醒就是时钟闹钟：最准
        if (classify(context, info) == Kind.CLOCK) {
            p.clockAlarmAt = info.triggerTime
            return info.triggerTime
        }
        // 最早的是别的应用的提醒：用之前记下的时钟闹钟（被挡在后面）
        val saved = p.clockAlarmAt
        return when {
            saved <= now -> {
                p.clockAlarmAt = 0L
                null
            }
            saved < info.triggerTime -> {
                // 记下的闹钟比“最早的提醒”还早，说明它已经不存在了
                p.clockAlarmAt = 0L
                null
            }
            else -> saved
        }
    }
}
