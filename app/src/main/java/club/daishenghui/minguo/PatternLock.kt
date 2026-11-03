package club.daishenghui.minguo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** 图案至少要连的点数 */
const val PATTERN_MIN = 4

/**
 * 3×3 图案锁。手指划过的点依次连起来，松手时回调 onComplete(点序号 0–8)。
 * onComplete 返回 true = 接受（清空图案），false = 错误（红色显示 0.7 秒后清空）。
 * 跨过中间点时（如 0→2 经过 1），自动把中间点也连上，和系统图案锁一致。
 */
@Composable
fun PatternPad(
    size: Dp = 270.dp,
    color: Color = Color.White,
    errorColor: Color = Color(0xFFFF6B6B),
    enabled: Boolean = true,
    onComplete: (List<Int>) -> Boolean,
) {
    val picked = remember { mutableStateListOf<Int>() }
    var finger by remember { mutableStateOf<Offset?>(null) }
    var error by remember { mutableStateOf(false) }
    val complete by rememberUpdatedState(onComplete)

    LaunchedEffect(error) {
        if (error) {
            delay(700)
            picked.clear()
            error = false
        }
    }

    Canvas(
        Modifier
            .size(size)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val cell = this.size.width / 3f
                fun center(i: Int) = Offset(cell * (i % 3) + cell / 2, cell * (i / 3) + cell / 2)
                fun hit(p: Offset): Int? = (0..8).firstOrNull { (center(it) - p).getDistance() < cell * 0.32f }
                fun add(i: Int) {
                    if (i in picked) return
                    val last = picked.lastOrNull()
                    if (last != null) {
                        // 跨过的中间点
                        val (r1, c1) = last / 3 to last % 3
                        val (r2, c2) = i / 3 to i % 3
                        if ((r1 + r2) % 2 == 0 && (c1 + c2) % 2 == 0) {
                            val mid = ((r1 + r2) / 2) * 3 + (c1 + c2) / 2
                            if (mid != last && mid != i && mid !in picked) picked.add(mid)
                        }
                    }
                    picked.add(i)
                }
                detectDragGestures(
                    onDragStart = { p ->
                        if (error) return@detectDragGestures
                        picked.clear()
                        finger = p
                        hit(p)?.let(::add)
                    },
                    onDrag = { change, _ ->
                        if (error) return@detectDragGestures
                        change.consume()
                        finger = change.position
                        hit(change.position)?.let(::add)
                    },
                    onDragEnd = {
                        finger = null
                        if (picked.isEmpty() || error) return@detectDragGestures
                        if (complete(picked.toList())) picked.clear() else error = true
                    },
                    onDragCancel = {
                        finger = null
                        picked.clear()
                    },
                )
            },
    ) {
        val cell = this.size.width / 3f
        fun center(i: Int) = Offset(cell * (i % 3) + cell / 2, cell * (i / 3) + cell / 2)
        val c = if (error) errorColor else color
        // 连线
        val stroke = cell * 0.06f
        for (k in 1 until picked.size) {
            drawLine(c.copy(alpha = 0.7f), center(picked[k - 1]), center(picked[k]), stroke, StrokeCap.Round)
        }
        val f = finger
        if (f != null && picked.isNotEmpty() && !error) {
            drawLine(c.copy(alpha = 0.5f), center(picked.last()), f, stroke, StrokeCap.Round)
        }
        // 9 个点
        for (i in 0..8) {
            val on = i in picked
            drawCircle(c.copy(alpha = if (on) 0.25f else 0.12f), radius = cell * 0.26f, center = center(i))
            drawCircle(if (on) c else color.copy(alpha = 0.75f), radius = cell * if (on) 0.09f else 0.06f, center = center(i))
        }
    }
}

/** 图案 → 存储用的字符串 */
fun List<Int>.patternKey(): String = joinToString("")
