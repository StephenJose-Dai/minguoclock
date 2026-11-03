package club.daishenghui.minguo

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings

/**
 * 下一个闹钟。
 *
 * 有两个来源，按顺序：
 * 1. AlarmManager.nextAlarmClock：系统记录的“下一个闹钟”。任何应用都能往里写
 *    （日历、待办、健康等），所以要看是谁设的：
 *    - 设定者是时钟应用 → 是闹钟；
 *    - 设定者拿不到（部分系统的时钟把 showIntent 留空，或经系统代设，显示为 android）→
 *      只要系统闹钟文字（见 2）也有值，就认为是闹钟；
 *    - 设定者是别的应用 → 不是闹钟，忽略。
 * 2. Settings.System.NEXT_ALARM_FORMATTED：时钟应用写给系统的“下一个闹钟”文字
 *    （小米、华为等的时钟仍然会写），只在 1 拿不到时用来显示原文。
 *
 * 判断不了的情况可以在“权限检查 → 运行状态”里看诊断信息。
 */
object Alarms {
    /** 常见品牌的时钟应用包名 */
    private val KNOWN_CLOCKS = setOf(
        "com.android.deskclock",            // AOSP、小米/红米（HyperOS / MIUI）
        "com.google.android.deskclock",     // Google 时钟
        "com.miui.clock",                   // 小米部分版本
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

    /** 时钟应用写给系统的下一个闹钟文字，如“周二 07:30”；没有则 null */
    @Suppress("DEPRECATION")
    fun formatted(context: Context): String? =
        runCatching { Settings.System.getString(context.contentResolver, Settings.System.NEXT_ALARM_FORMATTED) }
            .getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    sealed class Next {
        /** 拿到了准确时间 */
        data class At(val millis: Long) : Next()

        /** 只拿到了时钟应用写的文字 */
        data class Text(val text: String) : Next()
    }

    fun next(context: Context): Next? {
        val info = systemInfo(context)
        val text = formatted(context)
        if (info != null) {
            val creator = info.showIntent?.creatorPackage
            when {
                creator != null && creator in clocks(context) -> return Next.At(info.triggerTime)
                (creator == null || creator in SYSTEM_CREATORS) && text != null -> return Next.At(info.triggerTime)
            }
        }
        return text?.let { Next.Text(it) }
    }

    /** 只要准确时间（锁屏服务判断“闹钟是否正在响”用） */
    fun nextMillis(context: Context): Long? = (next(context) as? Next.At)?.millis

    /** 诊断信息：系统记录的下一个提醒、设定者、时钟文字、最终判断 */
    fun diagnose(context: Context): String {
        val info = systemInfo(context)
        val creator = info?.showIntent?.creatorPackage
        val time = info?.let {
            java.time.Instant.ofEpochMilli(it.triggerTime).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                .toString().replace('T', ' ').take(16)
        }
        val result = when (val n = next(context)) {
            is Next.At -> tr("显示", "顯示", "Shown")
            is Next.Text -> tr("显示（文字）", "顯示（文字）", "Shown (text)") + "：" + n.text
            null -> tr("不显示", "不顯示", "Hidden")
        }
        return tr("系统下一个提醒：", "系統下一個提醒：", "System next alarm: ") + (time ?: "—") + "\n" +
            tr("设定者：", "設定者：", "Set by: ") + (creator ?: "—") + "\n" +
            tr("时钟文字：", "時鐘文字：", "Clock text: ") + (formatted(context) ?: "—") + "\n" +
            tr("锁屏闹钟：", "鎖定畫面鬧鐘：", "Lock screen: ") + result
    }
}
