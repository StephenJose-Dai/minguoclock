package app.minguo.calendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机 / 应用更新后：恢复服务、定时任务和小组件 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Widgets.updateAll(context)
                LockService.sync(context)
                Weather.schedule(context)
                Wallpaper.schedule(context)
                Watchdog.schedule(context)
            }
        }
    }
}
