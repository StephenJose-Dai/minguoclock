package club.daishenghui.minguo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.time.LocalDateTime

private val White = Color(0xFFFFFFFF)
private val WhiteSub = Color(0xD9FFFFFF)

/** 每秒（对齐整秒）刷新一次的当前时间 */
@Composable
fun rememberNow(): State<LocalDateTime> {
    val state = remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val n = LocalDateTime.now()
            state.value = n
            delay(1000L - n.nano / 1_000_000)
        }
    }
    return state
}

/** 缓慢流动的渐变背景 + 柔光 */
@Composable
fun GradientBackground(theme: ClockTheme, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "bg")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(20_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "bgT",
    )
    Box(
        modifier.drawBehind {
            val w = size.width
            val h = size.height
            drawRect(Brush.linearGradient(theme.colors, start = Offset(w * t, 0f), end = Offset(w * (1f - t), h)))
            val c = Offset(w * (0.15f + 0.7f * t), h * (0.25f + 0.1f * t))
            val r = w * 0.95f
            drawCircle(Brush.radialGradient(listOf(theme.glow, Color.Transparent), center = c, radius = r), radius = r, center = c)
        },
    )
}

/** 锁屏外观：文字颜色、状态栏图标是否用深色、是否加文字阴影 */
data class LockLook(val text: Color, val sub: Color, val darkStatusIcons: Boolean, val shadow: Boolean)

/** 锁屏背景：主题渐变 / 自选图片 / 必应壁纸（加暗化遮罩） */
@Composable
fun LockBackground(
    mode: Int,
    theme: ClockTheme,
    dimPercent: Int,
    modifier: Modifier = Modifier,
    onLook: (LockLook) -> Unit,
) {
    val ctx = LocalContext.current
    val version by Wallpaper.version.collectAsState()
    val (sw, sh) = remember { Wallpaper.screenSize(ctx) }
    var image by remember { mutableStateOf<WallpaperImage?>(null) }
    LaunchedEffect(mode, version) {
        image = if (mode == Wallpaper.MODE_THEME) null else Wallpaper.load(ctx, mode, sw, sh)
    }

    val img = image
    Box(modifier) {
        if (img == null) {
            GradientBackground(theme, Modifier.fillMaxSize())
            val darkIcons = theme.text.lum() < 0.5f
            LaunchedEffect(theme, darkIcons) { onLook(LockLook(theme.text, theme.sub, darkIcons, shadow = false)) }
        } else {
            Image(
                img.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            val dim = dimPercent.coerceIn(0, 70) / 100f
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = dim * 0.8f),
                                Color.Black.copy(alpha = dim * 0.5f),
                                Color.Black.copy(alpha = (dim + 0.25f).coerceAtMost(0.8f)),
                            ),
                        ),
                    ),
            )
            val topAfterDim = img.topLuminance * (1f - dim * 0.8f)
            LaunchedEffect(img, dim) { onLook(LockLook(White, WhiteSub, topAfterDim > 0.62f, shadow = true)) }
        }
    }
}

private fun Color.lum(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** 给子内容统一加文字阴影（图片壁纸时提升可读性） */
@Composable
fun ShadowText(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current.copy(shadow = Shadow(Color(0x73000000), Offset(0f, 2f), 10f)),
        content = content,
    )
}

/** 每一位数字单独做上下滚动动画 */
@Composable
fun AnimatedDigits(
    text: String,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Light,
    family: FontFamily = FontFamily.Default,
) {
    val base = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
    Row(modifier) {
        text.forEachIndexed { i, ch ->
            key(i) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        (slideInVertically { it / 2 } + fadeIn(tween(250))) togetherWith
                            (slideOutVertically { -it / 2 } + fadeOut(tween(200))) using
                            SizeTransform(clip = false)
                    },
                    label = "digit",
                ) { c ->
                    Text(text = c.toString(), color = color, fontSize = fontSize, fontWeight = weight, fontFamily = family, style = base)
                }
            }
        }
    }
}

data class BatteryInfo(val percent: Int, val charging: Boolean)

@Composable
fun rememberBattery(): State<BatteryInfo> {
    val ctx = LocalContext.current
    val state = remember { mutableStateOf(BatteryInfo(-1, false)) }
    DisposableEffect(ctx) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                state.value = BatteryInfo(
                    if (level >= 0) level * 100 / scale else -1,
                    status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
                )
            }
        }
        ContextCompat.registerReceiver(ctx, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { ctx.unregisterReceiver(receiver) }
    }
    return state
}
