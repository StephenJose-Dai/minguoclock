package club.daishenghui.minguo

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 下一个闹钟（只认时钟应用里设的闹钟）。
 *
 * 判断顺序：
 * A. 系统“最早的提醒”是时钟应用设的 → 直接用（系统自己算的，最准，已考虑重复日、跳过、贪睡）；
 * B. 被其它应用的提醒挡住时 → 读时钟应用的闹钟列表（小米等开放了 content://com.android.deskclock/alarm）补位，
 *    只取“已开启”的闹钟；列表里没有“是否开启”这一栏就不采信（否则会把关着的预设闹钟算进去）；
 * C. 列表读不到 → 用下面记录的办法。
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

    // ---------------- 0. 直接读时钟应用的闹钟列表 ----------------

    private val CLOCK_PROVIDERS = listOf(
        "content://com.android.deskclock/alarm",
        "content://com.android.deskclock/alarms",
    )

    /** 读取结果：available = 能读到列表且有“是否开启”栏；next = 最近一次响铃；count = 已开启数量；rows = 诊断用的原始行 */
    private data class ProviderResult(val available: Boolean, val next: Long?, val count: Int, val rows: List<String> = emptyList(), val columns: String = "")

    /**
     * 重复日 daysofweek 是位掩码：bit0 = 周一 … bit6 = 周日；0 = 只响一次。
     * 算出从现在起最近的一次响铃时间。
     */
    private fun nextOccurrence(hour: Int, minute: Int, days: Int, now: LocalDateTime): LocalDateTime {
        val time = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        for (add in 0..7) {
            val d = LocalDate.from(now).plusDays(add.toLong())
            val at = LocalDateTime.of(d, time)
            if (!at.isAfter(now)) continue
            if (days == 0) return at
            val bit = d.dayOfWeek.value - 1 // 周一 = 0
            if (days and (1 shl bit) != 0) return at
        }
        return LocalDateTime.of(LocalDate.from(now).plusDays(1), time)
    }

    private fun fromClockApp(context: Context): ProviderResult {
        val now = LocalDateTime.now()
        for (u in CLOCK_PROVIDERS) {
            val c = runCatching { context.contentResolver.query(Uri.parse(u), null, null, null, null) }.getOrNull() ?: continue
            c.use { cur ->
                fun col(vararg names: String) = names.map { cur.getColumnIndex(it) }.firstOrNull { it >= 0 } ?: -1
                val hi = col("hour")
                val mi = col("minutes", "minute")
                val di = col("daysofweek", "days_of_week", "daysOfWeek")
                val ei = col("enabled", "enable", "isEnabled", "is_enabled")
                val columns = cur.columnNames.joinToString(",")
                // 没有时间或“是否开启”栏，就不采信
                if (hi < 0 || mi < 0 || ei < 0) return ProviderResult(false, null, 0, emptyList(), columns)
                var best: LocalDateTime? = null
                var count = 0
                val raw = ArrayList<String>()
                while (cur.moveToNext()) {
                    val h = cur.getInt(hi)
                    val m = cur.getInt(mi)
                    val days = if (di >= 0) cur.getInt(di) else 0
                    val on = cur.getInt(ei) != 0
                    if (raw.size < 8) raw.add("%02d:%02d %s %s".format(h, m, if (on) "开" else "关", if (days == 0) "单次" else "重复$days"))
                    if (!on) continue
                    count++
                    val at = nextOccurrence(h, m, days, now)
                    if (best == null || at.isBefore(best)) best = at
                }
                return ProviderResult(true, best?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(), count, raw, columns)
            }
        }
        return ProviderResult(false, null, 0)
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
        // B. 被其它应用的提醒挡住：用时钟应用的闹钟列表补位（只取已开启、且不早于系统最早提醒的）
        val app = fromClockApp(context)
        if (app.available) {
            val n = app.next?.takeIf { it >= info.triggerTime - 60_000 }
            p.clockAlarmAt = n ?: 0L
            return n
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

    /** 诊断信息：系统记录的下一个提醒、设定者、时钟文字、最终判断 */
    fun diagnose(context: Context): String {
        val info = systemInfo(context)
        fun fmt(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDateTime()
            .toString().replace('T', ' ').take(16)
        val creator = info?.showIntent?.creatorPackage
        val kind = info?.let {
            if (classify(context, it) == Kind.CLOCK) tr("时钟闹钟", "時鐘鬧鐘", "clock alarm") else tr("其它应用的提醒（忽略）", "其他應用程式的提醒（忽略）", "other app (ignored)")
        }
        val shown = next(context)
        val app = fromClockApp(context)
        val appLine = if (app.available) {
            tr("可读取，已开启 ${app.count} 个", "可讀取，已開啟 ${app.count} 個", "readable, ${app.count} enabled") +
                (if (app.rows.isNotEmpty()) "\n　" + app.rows.joinToString("；") else "")
        } else if (app.columns.isNotEmpty()) {
            tr("读到了但没有“是否开启”栏，不采信", "讀到了但沒有「是否開啟」欄，不採信", "readable but no enabled column") + "\n　" + app.columns
        } else {
            tr("系统不开放", "系統不開放", "not available")
        }
        return tr("时钟应用闹钟列表：", "時鐘應用程式鬧鐘列表：", "Clock app alarms: ") + appLine + "\n" +
            tr("系统最早的提醒：", "系統最早的提醒：", "System earliest: ") + (info?.let { fmt(it.triggerTime) } ?: "—") +
            (kind?.let { "（$it）" } ?: "") + "\n" +
            tr("设定者：", "設定者：", "Set by: ") + (creator ?: "—") + "\n" +
            tr("时钟文字：", "時鐘文字：", "Clock text: ") + (formatted(context) ?: "—") + "\n" +
            tr("锁屏显示的闹钟：", "鎖定畫面顯示的鬧鐘：", "Shown alarm: ") + (shown?.let(::fmt) ?: tr("不显示", "不顯示", "none"))
    }
}
