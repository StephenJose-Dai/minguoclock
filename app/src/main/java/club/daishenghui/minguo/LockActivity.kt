package club.daishenghui.minguo

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * 锁屏 Activity，三种用途：
 * - 保留系统密码模式：熄屏时由 [LockService] 拉起，盖在系统锁屏上；
 * - 预览（EXTRA_PREVIEW）；
 * - 编辑布局（EXTRA_EDIT）。
 */
class LockActivity : ComponentActivity() {

    private val uiState = mutableStateOf(LockUi.ACTIVITY)
    private var ui: LockUi
        get() = uiState.value
        set(v) {
            uiState.value = v
        }
    private var wasLockedWhenShown = false
    private val resetSignal = mutableIntStateOf(0)

    /** 用户用指纹/人脸直接解锁了系统锁屏，就一起退出 */
    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ui == LockUi.ACTIVITY && wasLockedWhenShown) finishQuietly()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        ui = uiFrom(intent)
        // 关闭时不做任何动画（Android 14+ 要提前声明）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        }
        L.refresh(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }
        // 锁屏状态下屏蔽返回键；预览/编辑允许返回
        onBackPressedDispatcher.addCallback(this) { if (ui != LockUi.ACTIVITY) finishQuietly() }

        ContextCompat.registerReceiver(
            this, userPresentReceiver, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        setContent {
            LockContent(
                ui = uiState.value,
                resetKey = resetSignal.intValue,
                onUnlock = { unlock() },
                onEmergency = { LockActions.emergencyCall(this) },
                onCamera = { LockActions.openCamera(this, secure = ui == LockUi.ACTIVITY) },
                onStatusIcons = { dark ->
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = dark
                        isAppearanceLightNavigationBars = dark
                    }
                },
                onExitEdit = { finishQuietly() },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 编辑/预览中被熄屏重新拉起时，保持当前界面
        if (ui == LockUi.ACTIVITY) ui = uiFrom(intent)
        resetSignal.intValue++
    }

    override fun onResume() {
        super.onResume()
        wasLockedWhenShown = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        L.refresh(this)
        // 锁屏可见 = App 在前台：可以定位、刷新天气、换名言
        lifecycleScope.launch { Weather.refreshIfStale(this@LockActivity, inForeground = true) }
        lifecycleScope.launch { Quotes.maybeRotate(this@LockActivity, screenOn = false) }
        if (Prefs(this).wallpaperMode == Wallpaper.MODE_BING) {
            lifecycleScope.launch { Wallpaper.refreshBing(this@LockActivity) }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(userPresentReceiver)
        super.onDestroy()
    }

    private fun unlock() {
        val km = getSystemService(KeyguardManager::class.java)
        if (ui == LockUi.ACTIVITY && km.isKeyguardLocked) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() = finishQuietly()
                    override fun onDismissError() = finishQuietly()
                    override fun onDismissCancelled() {
                        resetSignal.intValue++
                    }
                })
            } else {
                // Android 7.x：用旧的窗口标记请求解锁；有密码时系统弹出验证，解锁后由 USER_PRESENT 关闭本界面
                @Suppress("DEPRECATION")
                window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
                if (!km.isKeyguardSecure) finishQuietly()
            }
        } else {
            finishQuietly()
        }
    }

    /**
     * 立刻关闭锁屏页。以前用淡出动画，系统解锁后锁屏页还要淡出几百毫秒，看起来像“先闪一下 App 背景再到桌面”。
     * 现在：去掉窗口动画、无动画关闭，解锁后直接看到桌面。
     */
    private fun finishQuietly() {
        if (isFinishing) return
        window.setWindowAnimations(0)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_PREVIEW = "preview"
        const val EXTRA_EDIT = "edit"

        private fun uiFrom(i: Intent?) = when {
            i?.getBooleanExtra(EXTRA_EDIT, false) == true -> LockUi.EDIT
            i?.getBooleanExtra(EXTRA_PREVIEW, false) == true -> LockUi.PREVIEW
            else -> LockUi.ACTIVITY
        }

        fun open(context: Context, edit: Boolean) {
            context.startActivity(
                Intent(context, LockActivity::class.java)
                    .putExtra(if (edit) EXTRA_EDIT else EXTRA_PREVIEW, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/** 紧急呼叫 / 相机，两种锁屏共用 */
object LockActions {
    fun emergencyCall(context: Context): Boolean {
        // 系统紧急拨号界面（锁屏状态下可用，只能拨打紧急号码）；没有时退回普通拨号盘
        val intents = listOf(
            Intent("com.android.phone.EmergencyDialer.DIAL"),
            Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:")),
        )
        for (i in intents) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (ignored: Exception) {
            }
        }
        Toast.makeText(context, tr("无法打开紧急拨号", "無法開啟緊急撥號", "Can't open emergency dialer"), Toast.LENGTH_SHORT).show()
        return false
    }

    /** secure = true 时用安全相机（锁屏状态下只能拍照，看不到相册） */
    fun openCamera(context: Context, secure: Boolean): Boolean {
        val action = if (secure) MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE else MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
        return try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (ignored: Exception) {
            Toast.makeText(context, tr("无法打开相机", "無法開啟相機", "Can't open camera"), Toast.LENGTH_SHORT).show()
            false
        }
    }
}
