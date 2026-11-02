package club.daishenghui.minguo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机 / 应用更新后：恢复锁屏服务并刷新小组件 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                ClockWidget.updateAll(context)
                LockService.sync(context)
            }
        }
    }
}
