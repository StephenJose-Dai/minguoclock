package club.daishenghui.minguo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * 常驻前台服务：监听屏幕熄灭，熄屏瞬间拉起 [LockActivity]，
 * 这样亮屏时首先看到的就是民国锁屏。
 */
class LockService : Service() {

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) showLockScreen()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        ContextCompat.registerReceiver(
            this, screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Prefs(this).lockEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        super.onDestroy()
    }

    private fun showLockScreen() {
        if (!Prefs(this).lockEnabled) return
        // 通话中（贴脸熄屏）不弹锁屏
        val mode = getSystemService(AudioManager::class.java).mode
        if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION || mode == AudioManager.MODE_RINGTONE) return

        val intent = Intent(this, LockActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
        )
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "无法拉起锁屏界面（检查“显示在其他应用上层 / 后台弹出界面”权限）", e)
        }
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "锁屏服务", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
                description = "保持民国锁屏在后台运行"
            },
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_clock)
            .setContentTitle("民国锁屏已启用")
            .setContentText("点按打开设置")
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(open)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "LockService"
        private const val CHANNEL_ID = "lock_service"
        private const val NOTIFICATION_ID = 1

        /** 根据设置启动或停止服务 */
        fun sync(context: Context) {
            val intent = Intent(context, LockService::class.java)
            if (Prefs(context).lockEnabled) {
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (e: Exception) {
                    Log.w(TAG, "启动锁屏服务失败", e)
                }
            } else {
                context.stopService(intent)
            }
        }
    }
}
