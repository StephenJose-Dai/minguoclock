package app.minguo.calendar

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * 完全替代模式的锁屏：一个全屏悬浮窗。
 * 系统锁屏设为“无”之后，它就是唯一的锁屏；按 Home / 返回 / 最近任务都关不掉，
 * 只有上滑（+ 密码）才能解锁。
 */
class LockOverlay(private val ctx: Context) : LifecycleOwner, SavedStateRegistryOwner {

    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    private var view: ComposeView? = null
    private val reset = mutableIntStateOf(0)

    /** 解锁后回调 */
    var onUnlocked: () -> Unit = {}

    /** 因紧急呼叫/相机/闹钟/来电临时让开 */
    var onTemporarilyHidden: () -> Unit = {}

    val isShowing: Boolean get() = view != null

    init {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    fun show() {
        if (!Settings.canDrawOverlays(ctx)) {
            Log.w(TAG, "没有悬浮窗权限，无法显示锁屏")
            return
        }
        Prefs(ctx).overlayLocked = true
        if (view != null) {
            reset.intValue++
            return
        }
        val v = ComposeView(ctx).apply {
            setViewTreeLifecycleOwner(this@LockOverlay)
            setViewTreeSavedStateRegistryOwner(this@LockOverlay)
            setContent {
                LockContent(
                    ui = LockUi.OVERLAY,
                    resetKey = reset.intValue,
                    requirePin = true,
                    onUnlock = {
                        hide()
                        onUnlocked()
                    },
                    onEmergency = {
                        hideTemporarily()
                        LockActions.emergencyCall(ctx)
                    },
                    onCamera = {
                        hideTemporarily()
                        LockActions.openCamera(ctx, secure = false)
                    },
                    onStatusIcons = { dark -> setStatusIcons(dark) },
                )
            }
        }
        @Suppress("DEPRECATION")
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE // Android 7.x
            },
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            screenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            title = "MinguoLock"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // 铺满整个屏幕（含状态栏、挖孔区），内容自己按 insets 避让
                setFitInsetsTypes(0)
                setFitInsetsIgnoringVisibility(true)
            }
        }
        try {
            wm.addView(v, lp)
            view = v
            registry.currentState = Lifecycle.State.RESUMED
        } catch (e: Exception) {
            Log.w(TAG, "添加锁屏悬浮窗失败", e)
        }
    }

    fun hide() {
        Prefs(ctx).overlayLocked = false
        removeView()
    }

    /** 临时让开（紧急呼叫、相机、闹钟、来电），下次熄屏会重新锁上 */
    fun hideTemporarily() {
        removeView()
        onTemporarilyHidden()
    }

    private fun removeView() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        if (registry.currentState.isAtLeast(Lifecycle.State.CREATED)) registry.currentState = Lifecycle.State.CREATED
    }

    fun destroy() {
        removeView()
        registry.currentState = Lifecycle.State.DESTROYED
    }

    private fun setStatusIcons(dark: Boolean) {
        // 悬浮窗通常无法控制状态栏图标颜色，这里尽力而为
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                view?.windowInsetsController?.setSystemBarsAppearance(
                    if (dark) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                )
            }
        }
    }

    private companion object {
        const val TAG = "LockOverlay"
    }
}
