package club.daishenghui.minguo

import android.app.AlarmManager
import android.app.Notification
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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 常驻前台服务：
 * 1. 熄屏时上锁：完全替代模式显示悬浮窗锁屏；保留系统密码模式拉起 [LockActivity]；
 * 2. 来电、闹钟响起时让开，结束后重新锁上；
 * 3. 通知栏常驻“民国日期 + 农历 + 天气”；
 * 4. 亮屏时按设置换名言、刷新天气和壁纸。
 */
class LockService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: LockOverlay? = null
    private var lastContent = ""
    private var relockAfterCall = false
    private var pendingAlarm = 0L
    private var networkWatch: Any? = null
    private var modeListener: Any? = null // AudioManager.OnModeChangedListener（Android 12+）

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_SCREEN_ON -> onScreenOn()
                Intent.ACTION_TIME_TICK, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                    updateNotification()
                    checkAlarm()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        L.refresh(this)
        Weather.init(this)
        Quotes.init(this)
        LayoutStore.init(this)
        startAsForeground()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        watchCallState()
        networkWatch = Region.watchNetwork(this)

        // 崩溃/被回收后重启：如果之前处于锁定状态，立刻重新锁上，防止借机绕过
        val p = Prefs(this)
        val pm = getSystemService(PowerManager::class.java)
        if (p.lockEnabled && p.lockMode == Prefs.MODE_REPLACE && (p.overlayLocked || !pm.isInteractive)) {
            showOverlay()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!shouldRun(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        L.refresh(this)
        lastContent = ""
        updateNotification()
        // 关闭锁屏或切到“保留系统密码”时，收起悬浮窗
        val p = Prefs(this)
        if (!p.lockEnabled || p.lockMode != Prefs.MODE_REPLACE) overlay?.hide()
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (modeListener as? AudioManager.OnModeChangedListener)?.let { getSystemService(AudioManager::class.java).removeOnModeChangedListener(it) }
        }
        handler.removeCallbacksAndMessages(null)
        Region.unwatch(this, networkWatch)
        overlay?.destroy()
        scope.cancel()
        super.onDestroy()
    }

    // ---------- 上锁 ----------

    private fun onScreenOff() {
        val p = Prefs(this)
        if (!p.lockEnabled || inCall()) return
        if (p.lockMode == Prefs.MODE_REPLACE) {
            showOverlay()
        } else {
            startLockActivity()
        }
    }

    private fun onScreenOn() {
        scope.launch { Quotes.maybeRotate(this@LockService, screenOn = true) }
        val p = Prefs(this)
        if (p.lockEnabled && p.lockMode == Prefs.MODE_REPLACE && overlay?.isShowing == true) {
            // 悬浮窗模式下由服务负责刷新（没有前台 Activity，定位需要“后台位置”权限）
            scope.launch { Weather.refreshIfStale(this@LockService, inForeground = false) }
            if (p.wallpaperMode == Wallpaper.MODE_BING) scope.launch { Wallpaper.refreshBing(this@LockService) }
        }
    }

    private fun showOverlay() {
        val o = overlay ?: LockOverlay(this).also {
            it.onUnlocked = { relockAfterCall = false }
            it.onTemporarilyHidden = { relockAfterCall = Security.hasPin(this) }
            overlay = it
        }
        o.show()
        pendingAlarm = getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime ?: 0L
    }

    private fun startLockActivity() {
        val intent = Intent(this, LockActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
        )
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "无法拉起锁屏界面（检查“显示在其他应用上层 / 后台弹出界面”权限）", e)
        }
    }

    /** 闹钟响起时让开，否则会挡住闹钟界面 */
    private fun checkAlarm() {
        val o = overlay ?: return
        if (o.isShowing && pendingAlarm > 0 && System.currentTimeMillis() >= pendingAlarm - 1_000) {
            pendingAlarm = 0
            o.hideTemporarily()
            relockAfterCall = false // 闹钟关掉后，下次熄屏再锁
        }
    }

    // ---------- 通话 ----------

    private fun inCall(): Boolean {
        val mode = getSystemService(AudioManager::class.java).mode
        return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION || mode == AudioManager.MODE_RINGTONE
    }

    private fun watchCallState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val l = AudioManager.OnModeChangedListener { onCallStateChanged() }
            getSystemService(AudioManager::class.java).addOnModeChangedListener(mainExecutor, l)
            modeListener = l
        } else {
            handler.post(object : Runnable {
                override fun run() {
                    if (overlay?.isShowing == true || relockAfterCall) onCallStateChanged()
                    handler.postDelayed(this, 1_500)
                }
            })
        }
    }

    private fun onCallStateChanged() {
        val o = overlay ?: return
        if (inCall()) {
            if (o.isShowing) {
                o.hideTemporarily()
                relockAfterCall = true
            }
        } else if (relockAfterCall) {
            relockAfterCall = false
            val p = Prefs(this)
            if (p.lockEnabled && p.lockMode == Prefs.MODE_REPLACE) o.show()
        }
    }

    // ---------- 通知栏民国日期 ----------

    private fun content(): Pair<String, String> {
        val p = Prefs(this)
        val style = p.style()
        val today = LocalDate.now()
        val title = RocCalendar.dateLine(today, style) + "  " + RocCalendar.weekday(today, style.lang)
        val lunar = RocCalendar.lunarLine(System.currentTimeMillis(), style)
        val w = Weather.state.value
        val text = if (p.weatherEnabled && w != null) {
            lunar + "  ·  " + Weather.summary(w, style.lang) + "  " + Weather.cityLabel(w, style.lang)
        } else {
            lunar
        }
        return title to text
    }

    private fun buildNotification(): Notification {
        val (title, text) = content()
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_clock)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .build()
    }

    private fun updateNotification() {
        val (title, text) = content()
        val key = title + text
        if (key == lastContent) return
        lastContent = key
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun startAsForeground() {
        // 通知渠道是 Android 8.0 才有的
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.deleteNotificationChannel(OLD_CHANNEL_ID)
            val lang = L.of(this)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, tr("民国日期", "民國日期", "ROC date", lang), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                    description = tr("通知栏常驻显示民国日期、农历和天气", "通知欄常駐顯示民國日期、農曆和天氣", "Shows the ROC date, lunar date and weather", lang)
                },
            )
        }
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    companion object {
        private const val TAG = "LockService"
        private const val OLD_CHANNEL_ID = "lock_service"
        private const val CHANNEL_ID = "roc_date"
        private const val NOTIFICATION_ID = 1

        fun shouldRun(context: Context) = Prefs(context).let { it.lockEnabled || it.dateNotification }

        /** 根据设置启动 / 停止 / 刷新服务 */
        fun sync(context: Context) {
            val intent = Intent(context, LockService::class.java)
            if (shouldRun(context)) {
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (e: Exception) {
                    Log.w(TAG, "启动服务失败", e)
                }
            } else {
                context.stopService(intent)
            }
        }
    }
}
