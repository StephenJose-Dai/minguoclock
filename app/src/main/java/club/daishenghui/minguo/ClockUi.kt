package club.daishenghui.minguo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.ZoneId

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
fun GradientBackground(
    theme: ClockTheme,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "bg")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(20_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "bgT",
    )
    Box(
        modifier = modifier.drawBehind {
            val w = size.width
            val h = size.height
            drawRect(Brush.linearGradient(theme.colors, start = Offset(w * t, 0f), end = Offset(w * (1f - t), h)))
            val c = Offset(w * (0.15f + 0.7f * t), h * (0.25f + 0.1f * t))
            val r = w * 0.95f
            drawCircle(Brush.radialGradient(listOf(theme.glow, Color.Transparent), center = c, radius = r), radius = r, center = c)
        },
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
) {
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
                    Text(
                        text = c.toString(),
                        color = color,
                        fontSize = fontSize,
                        fontWeight = weight,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                    )
                }
            }
        }
    }
}

/** 时钟主体：时分秒 + 民国日期 + 农历 */
@Composable
fun ClockFace(
    now: LocalDateTime,
    style: DisplayStyle,
    showSeconds: Boolean,
    theme: ClockTheme,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    val ctx = LocalContext.current
    val is24 = DateFormat.is24HourFormat(ctx)
    val (hm, ampm) = RocCalendar.hourMinute(now, is24)
    val today = now.toLocalDate()
    val dateText = remember(today, style) { RocCalendar.dateLine(today, style) + "  " + RocCalendar.weekday(today) }
    val lunarText = remember(today, style) {
        RocCalendar.lunarLine(now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), style)
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            if (ampm != null) {
                Text(
                    ampm,
                    color = theme.sub,
                    fontSize = (20 * scale).sp,
                    modifier = Modifier.padding(end = (6 * scale).dp, bottom = (20 * scale).dp),
                )
            }
            AnimatedDigits(hm, fontSize = (92 * scale).sp, color = theme.text, weight = FontWeight.Light)
            if (showSeconds) {
                AnimatedDigits(
                    now.second.toString().padStart(2, '0'),
                    fontSize = (30 * scale).sp,
                    color = theme.sub,
                    modifier = Modifier.padding(start = (8 * scale).dp, bottom = (18 * scale).dp),
                )
            }
        }
        Spacer(Modifier.height((6 * scale).dp))
        Text(dateText, color = theme.text, fontSize = (20 * scale).sp, letterSpacing = (2 * scale).sp)
        Spacer(Modifier.height((6 * scale).dp))
        Text(lunarText, color = theme.sub, fontSize = (15 * scale).sp, letterSpacing = (1.5f * scale).sp)
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
        ContextCompat.registerReceiver(
            ctx, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { ctx.unregisterReceiver(receiver) }
    }
    return state
}

/** 小电池图标 + 百分比 */
@Composable
fun BatteryIndicator(info: BatteryInfo, color: Color, traditional: Boolean) {
    if (info.percent < 0) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        val fillColor = when {
            info.charging -> Color(0xFF4CD964)
            info.percent <= 15 -> Color(0xFFFF5E57)
            else -> color
        }
        Canvas(Modifier.size(width = 24.dp, height = 12.dp)) {
            val body = Size(size.width - 3.dp.toPx(), size.height)
            drawRoundRect(color, size = body, cornerRadius = CornerRadius(3.dp.toPx()), style = Stroke(1.2.dp.toPx()))
            drawRoundRect(
                color,
                topLeft = Offset(body.width + 0.8.dp.toPx(), size.height * 0.3f),
                size = Size(1.6.dp.toPx(), size.height * 0.4f),
                cornerRadius = CornerRadius(1.dp.toPx()),
            )
            val pad = 2.2.dp.toPx()
            drawRoundRect(
                fillColor,
                topLeft = Offset(pad, pad),
                size = Size((body.width - pad * 2) * info.percent / 100f, body.height - pad * 2),
                cornerRadius = CornerRadius(1.5.dp.toPx()),
            )
        }
        Spacer(Modifier.width(6.dp))
        val label = if (info.charging) (if (traditional) "充電中 " else "充电中 ") else ""
        Text("$label${info.percent}%", color = color, fontSize = 13.sp)
    }
}
