package club.daishenghui.minguo

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock

/**
 * 下一个闹钟。
 *
 * 系统的 AlarmManager.nextAlarmClock 会返回“任何应用”用闹钟接口定的提醒
 * （日历、待办、健康、某些 App 的定时任务都会用），所以没设闹钟也可能有值。
 * 这里只认时钟类应用设的闹钟：按设定者的包名判断。
 */
object Alarms {
    /** 常见品牌的时钟应用包名 */
    private val KNOWN_CLOCKS = setOf(
        "com.android.deskclock",            // AOSP、小米/红米（HyperOS / MIUI）
        "com.google.android.deskclock",     // Google 时钟
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

    /** 下一个由时钟应用设的闹钟的响铃时间；没有则 null */
    fun next(context: Context): Long? {
        val info = runCatching { context.getSystemService(AlarmManager::class.java)?.nextAlarmClock }.getOrNull() ?: return null
        val creator = info.showIntent?.creatorPackage ?: return null
        return if (creator in clocks(context)) info.triggerTime else null
    }
}
