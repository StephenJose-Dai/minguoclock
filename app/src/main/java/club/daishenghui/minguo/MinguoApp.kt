package club.daishenghui.minguo

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * 进程守护：
 * - 崩溃时 2 秒后自动拉起服务（锁屏状态会恢复）；
 * - 每 15 分钟自检一次，服务不在就重新启动；
 * - 被“强行停止”后 Android 会冻结一切自动恢复，需重新打开 App 或重启手机。
 */
class MinguoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        L.refresh(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Log.e("MinguoApp", "崩溃，2 秒后自动重启", e)
            runCatching { scheduleRestart(this) }
            if (previous != null) previous.uncaughtException(t, e) else exitProcess(2)
        }
        Watchdog.schedule(this)
    }

    companion object {
        fun scheduleRestart(context: Context, delayMs: Long = 2_000) {
            if (!LockService.shouldRun(context)) return
            val intent = Intent(context, LockService::class.java)
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(context, 99, intent, flags)
            } else {
                PendingIntent.getService(context, 99, intent, flags)
            }
            val am = context.getSystemService(AlarmManager::class.java)
            val at = System.currentTimeMillis() + delayMs
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
    }
}

object Watchdog {
    private const val WORK_NAME = "watchdog"

    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES).build(),
        )
    }
}

class WatchdogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        LockService.sync(applicationContext)
        return Result.success()
    }
}
