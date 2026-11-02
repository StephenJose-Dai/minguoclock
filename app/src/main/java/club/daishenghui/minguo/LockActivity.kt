package club.daishenghui.minguo

import android.app.AlarmManager
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch

/**
 * 民国锁屏界面。屏幕熄灭时由 [LockService] 拉起，盖在系统锁屏之上；
 * 上滑后调用系统的解锁流程（有密码/指纹时会弹出验证）。
 */
class LockActivity : ComponentActivity() {

    private var preview = false
    private var wasLockedWhenShown = false
    private val resetSignal = mutableIntStateOf(0)

    /** 用户用指纹/人脸直接解锁了系统锁屏，就一起退出 */
    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!preview && wasLockedWhenShown) finishQuietly()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preview = intent.getBooleanExtra(EXTRA_PREVIEW, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        // 锁屏状态下屏蔽返回键；预览模式允许返回
        onBackPressedDispatcher.addCallback(this) { if (preview) finishQuietly() }

        ContextCompat.registerReceiver(
            this, userPresentReceiver, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        setContent {
            LockScreen(
                prefs = remember { Prefs(this) },
                preview = preview,
                resetKey = resetSignal.intValue,
                onUnlock = { unlock() },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        preview = intent.getBooleanExtra(EXTRA_PREVIEW, false)
        resetSignal.intValue++
    }

    override fun onResume() {
        super.onResume()
        wasLockedWhenShown = getSystemService(KeyguardManager::class.java).isKeyguardLocked
    }

    override fun onDestroy() {
        unregisterReceiver(userPresentReceiver)
        super.onDestroy()
    }

    private fun unlock() {
        val km = getSystemService(KeyguardManager::class.java)
        if (!preview && km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = finishQuietly()
                override fun onDismissError() = finishQuietly()
                override fun onDismissCancelled() {
                    resetSignal.intValue++
                }
            })
        } else {
            finishQuietly()
        }
    }

    private fun finishQuietly() {
        if (isFinishing) return
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    companion object {
        const val EXTRA_PREVIEW = "preview"
    }
}

@Composable
private fun LockScreen(prefs: Prefs, preview: Boolean, resetKey: Int, onUnlock: () -> Unit) {
    val theme = ClockThemes[prefs.themeIndex]
    val style = prefs.style()
    val now by rememberNow()
    val battery by rememberBattery()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnUnlock by rememberUpdatedState(onUnlock)

    val offset = remember { Animatable(0f) }
    var heightPx by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(resetKey) { offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }

    val alarm = remember(now.minute) {
        ctx.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime?.let {
            RocCalendar.alarmText(it, now, DateFormat.is24HourFormat(ctx), style)
        }
    }

    GradientBackground(
        theme,
        Modifier
            .fillMaxSize()
            .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (-offset.value > heightPx * 0.2f) {
                                offset.animateTo(-heightPx * 0.6f, tween(180))
                                currentOnUnlock()
                            } else {
                                offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            }
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f) } },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        scope.launch { offset.snapTo((offset.value + dy).coerceIn(-heightPx, 0f)) }
                    },
                )
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = offset.value * 0.55f
                    alpha = 1f - (-offset.value / (heightPx * 0.45f)).coerceIn(0f, 1f)
                }
                .systemBarsPadding()
                .padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 顶部：锁 + 电量
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = theme.sub, modifier = Modifier.size(18.dp))
                BatteryIndicator(battery, theme.sub, style.traditional)
            }

            Spacer(Modifier.weight(0.32f))
            ClockFace(now, style, prefs.showSeconds, theme)

            if (alarm != null) {
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Notifications, contentDescription = null, tint = theme.sub, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(alarm, color = theme.sub, fontSize = 14.sp)
                }
            }

            Spacer(Modifier.weight(0.68f))
            UnlockHint(
                text = when {
                    preview -> if (style.traditional) "向上滑動 結束預覽" else "向上滑动 结束预览"
                    style.traditional -> "向上滑動解鎖"
                    else -> "向上滑动解锁"
                },
                theme = theme,
            )
        }
    }
}

@Composable
private fun UnlockHint(text: String, theme: ClockTheme) {
    val bob by rememberInfiniteTransition(label = "hint").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer {
            translationY = -bob * 10.dp.toPx()
            alpha = 0.55f + 0.45f * bob
        },
    ) {
        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = theme.text, modifier = Modifier.size(28.dp))
        Text(text, color = theme.sub, fontSize = 14.sp, letterSpacing = 3.sp)
    }
}
