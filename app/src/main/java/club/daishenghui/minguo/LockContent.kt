@file:OptIn(ExperimentalFoundationApi::class)

package club.daishenghui.minguo

import android.app.AlarmManager
import android.graphics.BitmapFactory
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** 锁屏内容的几种用法 */
enum class LockUi {
    /** 保留系统密码模式：Activity 盖在系统锁屏上 */
    ACTIVITY,

    /** 完全替代模式：悬浮窗 */
    OVERLAY,

    /** 设置页点“预览” */
    PREVIEW,

    /** 编辑布局 */
    EDIT,

    /** 首页小预览（不可交互） */
    MINI,
}

private val Accent = Color(0xFFE8C77A)
private val PanelBg = Color(0xF0151A22)
private val PanelSub = Color(0xFF8E98A8)

/** 把元素中心放在父容器 (fx, fy) 比例位置，并限制在容器内 */
fun Modifier.placeAt(fx: Float, fy: Float): Modifier = this.layout { measurable, constraints ->
    val w = constraints.maxWidth
    val h = constraints.maxHeight
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val x = (fx * w - p.width / 2f).roundToInt().coerceIn(0, max(0, w - p.width))
    val y = (fy * h - p.height / 2f).roundToInt().coerceIn(0, max(0, h - p.height))
    layout(w, h) { p.place(x, y) }
}

private fun keyOf(e: Elem) = "E:" + e.name
private fun keyOf(s: Sticker) = "S:" + s.id

/**
 * 拖动 + 双指缩放。手指移动超过系统触摸阈值才算拖动（轻点不会触发，留给“点击选中”），
 * 拖动开始/结束时回调，用来在拖动期间隐藏参数面板。
 */
private fun Modifier.dragZoom(
    key: Any,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onChange: (Offset, Float) -> Unit,
): Modifier = this.pointerInput(key) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var moving = false
        var total = Offset.Zero
        val slop = viewConfiguration.touchSlop
        try {
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.none { it.pressed }) break
                val zoom = event.calculateZoom()
                val pan = event.calculatePan()
                if (!moving) {
                    total += pan
                    if (total.getDistance() > slop || (event.changes.size > 1 && zoom != 1f)) {
                        moving = true
                        onStart()
                    }
                }
                if (moving) {
                    onChange(pan, zoom)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } finally {
            if (moving) onEnd()
        }
    }
}

@Composable
fun LockContent(
    ui: LockUi,
    resetKey: Int = 0,
    requirePin: Boolean = false,
    sizeScale: Float = 1f,
    onUnlock: () -> Unit = {},
    onEmergency: () -> Unit = {},
    onCamera: () -> Unit = {},
    onStatusIcons: (Boolean) -> Unit = {},
    onExitEdit: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val prefs = remember {
        LayoutStore.init(ctx)
        Quotes.init(ctx)
        Weather.init(ctx)
        Prefs(ctx)
    }
    val lang = L.current
    val style = prefs.style()
    val theme = ClockThemes[prefs.themeIndex]
    val now by rememberNow()
    val battery by rememberBattery()
    val weather by Weather.state.collectAsState()
    val quote by Quotes.state.collectAsState()
    val saved by LayoutStore.state.collectAsState()
    val fontsVersion by Fonts.version.collectAsState()
    val scope = rememberCoroutineScope()

    val editing = ui == LockUi.EDIT
    val interactive = ui == LockUi.ACTIVITY || ui == LockUi.OVERLAY || ui == LockUi.PREVIEW
    var draft by remember { mutableStateOf(saved) }
    val layout = if (editing) draft else saved
    var selected by remember { mutableStateOf<String?>(null) }
    var dragging by remember { mutableStateOf(false) }

    var look by remember { mutableStateOf(LockLook(theme.text, theme.sub, darkStatusIcons = false, shadow = false)) }
    LaunchedEffect(look.darkStatusIcons) { onStatusIcons(look.darkStatusIcons) }

    val offset = remember { Animatable(0f) }
    var heightPx by remember { mutableFloatStateOf(1f) }
    var area by remember { mutableStateOf(IntSize(1, 1)) }
    var showPin by remember { mutableStateOf(false) }
    LaunchedEffect(resetKey) {
        showPin = false
        offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }
    val unlockAction by rememberUpdatedState {
        if (requirePin && Security.hasPin(ctx)) showPin = true else onUnlock()
    }

    val is24 = DateFormat.is24HourFormat(ctx)
    val alarm = remember(now.minute, lang) {
        // 只显示时钟应用设的闹钟；没设闹钟时为 null，锁屏上就不显示
        when (val n = Alarms.next(ctx)) {
            is Alarms.Next.At -> RocCalendar.alarmText(n.millis, now, is24, lang)
            is Alarms.Next.Text -> n.text
            null -> null
        }
    }

    val swipe = if (interactive && !showPin) {
        Modifier.pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragEnd = {
                    scope.launch {
                        if (-offset.value > heightPx * 0.2f) {
                            if (requirePin && Security.hasPin(ctx)) {
                                offset.animateTo(0f, tween(150))
                                showPin = true
                            } else {
                                offset.animateTo(-heightPx * 0.6f, tween(180))
                                unlockAction()
                            }
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
        }
    } else {
        Modifier
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
            .then(swipe),
    ) {
        LockBackground(prefs.wallpaperMode, theme, prefs.wallpaperDim, Modifier.fillMaxSize()) { look = it }

        ShadowText(look.shadow) {
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = offset.value * 0.55f
                        alpha = 1f - (-offset.value / (heightPx * 0.45f)).coerceIn(0f, 1f)
                    }
                    .then(if (ui == LockUi.MINI) Modifier.padding((10 * sizeScale).dp) else Modifier.safeDrawingPadding())
                    .padding(horizontal = (12 * sizeScale).dp, vertical = (8 * sizeScale).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ---------- 可拖动区域 ----------
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onSizeChanged { area = it }
                        .then(if (editing) Modifier.pointerInput(Unit) { detectTapGestures { selected = null } } else Modifier),
                ) {
                    layout.stickers.forEach { s ->
                        key(keyOf(s)) {
                            StickerView(
                                s, sizeScale, editing, selected == keyOf(s),
                                onSelect = { selected = keyOf(s) },
                                onDragState = { dragging = it },
                                onTransform = { pan, zoom ->
                                    val a = area
                                    draft = draft.copy(stickers = draft.stickers.map {
                                        if (it.id != s.id) it else it.copy(
                                            x = (it.x + pan.x / a.width).coerceIn(0f, 1f),
                                            y = (it.y + pan.y / a.height).coerceIn(0f, 1f),
                                            widthDp = (it.widthDp * zoom).coerceIn(20f, 400f),
                                        )
                                    })
                                },
                            )
                        }
                    }

                    Elem.values().forEach { e ->
                        val st = layout.style(e)
                        val hasData = when (e) {
                            Elem.WEATHER -> prefs.weatherEnabled && weather != null
                            Elem.ADVICE -> prefs.weatherEnabled && weather?.let { Weather.advice(it, lang).isNotEmpty() } == true
                            Elem.ALARM -> alarm != null
                            Elem.QUOTE -> quote != null
                            Elem.SIGNATURE -> prefs.signature.isNotBlank()
                            else -> true
                        }
                        if (editing || (st.visible && hasData)) {
                            key(keyOf(e)) {
                                val family = remember(st.font, fontsVersion) { Fonts.family(ctx, st.font) }
                                val sizeSp = e.baseSp * st.scale * sizeScale
                                val weight = Fonts.weight(st.weight, if (e == Elem.TIME) FontWeight.Light else FontWeight.Normal)
                                val isSel = selected == keyOf(e)
                                Box(
                                    Modifier
                                        .placeAt(st.x, st.y)
                                        .then(
                                            if (editing) {
                                                Modifier
                                                    .alpha(if (st.visible) 1f else 0.35f)
                                                    .border(if (isSel) 2.dp else 1.dp, if (isSel) Accent else Color(0x73FFFFFF), RoundedCornerShape(8.dp))
                                                    .pointerInput(e) { detectTapGestures { selected = keyOf(e) } }
                                                    .dragZoom(e, onStart = { dragging = true }, onEnd = { dragging = false }) { pan, zoom ->
                                                        run {
                                                            val a = area
                                                            val s0 = draft.style(e)
                                                            draft = draft.with(
                                                                e,
                                                                s0.copy(
                                                                    x = (s0.x + pan.x / a.width).coerceIn(0f, 1f),
                                                                    y = (s0.y + pan.y / a.height).coerceIn(0f, 1f),
                                                                    scale = (s0.scale * zoom).coerceIn(0.4f, 3f),
                                                                ),
                                                            )
                                                        }
                                                    }
                                                    .padding(4.dp)
                                            } else {
                                                Modifier
                                            },
                                        ),
                                ) {
                                    val text = look.text
                                    val sub = look.sub
                                    when (e) {
                                        Elem.TIME -> {
                                            val (hm, ampm) = RocCalendar.hourMinute(now, is24, lang)
                                            Row(verticalAlignment = Alignment.Bottom) {
                                                if (ampm != null && lang != Lang.EN) {
                                                    Text(ampm, color = sub, fontSize = (sizeSp * 0.22f).sp, fontFamily = family, modifier = Modifier.padding(end = 4.dp, bottom = (sizeSp * 0.2f).dp))
                                                }
                                                AnimatedDigits(hm, sizeSp.sp, text, weight = weight, family = family)
                                                if (ampm != null && lang == Lang.EN) {
                                                    Text(ampm, color = sub, fontSize = (sizeSp * 0.22f).sp, fontFamily = family, modifier = Modifier.padding(start = 4.dp, bottom = (sizeSp * 0.2f).dp))
                                                }
                                                if (prefs.showSeconds) {
                                                    AnimatedDigits(
                                                        now.second.toString().padStart(2, '0'),
                                                        (sizeSp * 0.33f).sp,
                                                        sub,
                                                        Modifier.padding(start = (sizeSp * 0.08f).dp, bottom = (sizeSp * 0.18f).dp),
                                                        weight = weight,
                                                        family = family,
                                                    )
                                                }
                                            }
                                        }
                                        Elem.DATE -> {
                                            val d = now.toLocalDate()
                                            Text(
                                                RocCalendar.dateLine(d, style) + "  " + RocCalendar.weekday(d, lang),
                                                color = text, fontSize = sizeSp.sp, fontFamily = family, fontWeight = weight,
                                                letterSpacing = (sizeSp * 0.08f).sp, textAlign = TextAlign.Center,
                                            )
                                        }
                                        Elem.LUNAR -> {
                                            val lunar = remember(now.toLocalDate(), style) { RocCalendar.lunarLine(System.currentTimeMillis(), style) }
                                            Text(lunar, color = sub, fontSize = sizeSp.sp, fontFamily = family, fontWeight = weight, letterSpacing = (sizeSp * 0.08f).sp)
                                        }
                                        Elem.WEATHER -> {
                                            val w = weather
                                            if (w == null) {
                                                Text(e.label(lang), color = sub, fontSize = sizeSp.sp)
                                            } else {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(painterResource(w.cond.icon(w.isDay)), null, tint = text, modifier = Modifier.size((sizeSp * 1.35f).dp))
                                                    Spacer(Modifier.width((sizeSp * 0.45f).dp))
                                                    // 每一段内部用不换行空格连起来，避免“漳州市 芗城区”这类词被拆开
                                                    val nb = '\u00A0'
                                                    val range = w.today?.let { "  " + Weather.temp(it.min) + "$nb/$nb" + Weather.temp(it.max) } ?: ""
                                                    val aqi = w.aqi?.let { "  " + tr("空气", "空氣", "AQI", lang) + nb + Weather.aqiLabel(it, lang) } ?: ""
                                                    val main = w.cond.label(lang) + nb + Weather.temp(w.temp) + range + aqi
                                                    val city = Weather.cityLabel(w, lang).replace(' ', nb)
                                                    // 一行放得下就“… · 城市”，放不下就把城市整体放到第二行
                                                    var twoLines by remember(main, city, sizeSp) { mutableStateOf(false) }
                                                    Text(
                                                        if (twoLines) main + "\n" + city else "$main  ·  $city",
                                                        color = text, fontSize = sizeSp.sp, fontFamily = family, fontWeight = weight,
                                                        onTextLayout = { r -> if (!twoLines && r.lineCount > 1) twoLines = true },
                                                    )
                                                }
                                            }
                                        }
                                        Elem.ADVICE -> {
                                            val tip = weather?.let { Weather.advice(it, lang).firstOrNull() } ?: e.label(lang)
                                            Text(tip, color = sub, fontSize = sizeSp.sp, fontFamily = family, fontWeight = weight, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.9f))
                                        }
                                        Elem.ALARM -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Filled.Notifications, null, tint = sub, modifier = Modifier.size((sizeSp * 1.15f).dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(alarm ?: tr("闹钟（设了闹钟才显示）", "鬧鐘（設了鬧鐘才顯示）", "Alarm (shown only when one is set)", lang), color = sub, fontSize = sizeSp.sp, fontFamily = family, fontWeight = weight)
                                            }
                                        }
                                        Elem.QUOTE -> {
                                            val q = quote?.let { Quotes.display(it, lang) }
                                            Column(Modifier.fillMaxWidth(0.86f), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    if (q == null) e.label(lang) else "「" + q.text + "」",
                                                    color = text, fontSize = sizeSp.sp, fontFamily = family, fontWeight = weight,
                                                    lineHeight = (sizeSp * 1.5f).sp, textAlign = TextAlign.Center,
                                                )
                                            }
                                        }
                                        Elem.SIGNATURE -> {
                                            // 单行，字号固定；超出宽度时向左跑马灯
                                            Text(
                                                prefs.signature.ifBlank { e.label(lang) }.replace('\n', ' '),
                                                color = text, fontSize = sizeSp.sp, fontFamily = family, fontWeight = weight,
                                                maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
                                                modifier = Modifier
                                                    .fillMaxWidth(0.86f)
                                                    .basicMarquee(iterations = Int.MAX_VALUE, velocity = 36.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ---------- 底部固定信息 ----------
                if (ui != LockUi.MINI && !editing) {
                    if (battery.charging && battery.percent >= 0) {
                        Text(tr("正在充电", "正在充電", "Charging", lang) + " · ${battery.percent}%", color = look.sub, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                    }
                    BottomBar(
                        look, lang,
                        hint = if (ui == LockUi.PREVIEW) tr("上滑结束预览", "上滑結束預覽", "Swipe up to exit", lang) else tr("向上滑动解锁", "向上滑動解鎖", "Swipe up to unlock", lang),
                        showCamera = !(ui == LockUi.OVERLAY && Security.hasPin(ctx)),
                        onEmergency = onEmergency,
                        onCamera = onCamera,
                    )
                }
            }
        }

        if (editing) {
            EditPanel(
                draft = draft,
                selected = if (dragging) null else selected,
                lang = lang,
                onDraft = { draft = it },
                onSelect = { selected = it },
                onSave = {
                    LayoutStore.save(ctx, draft)
                    onExitEdit()
                },
                onCancel = onExitEdit,
            )
        }

        if (showPin) {
            val done = {
                showPin = false
                onUnlock()
            }
            if (Security.type(ctx) == Security.TYPE_PATTERN) {
                PatternUnlock(lang, onCancel = { showPin = false }, onSuccess = done)
            } else {
                PinPad(lang, onCancel = { showPin = false }, onSuccess = done)
            }
        }
    }
}

@Composable
private fun StickerView(
    s: Sticker,
    sizeScale: Float,
    editing: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDragState: (Boolean) -> Unit,
    onTransform: (Offset, Float) -> Unit,
) {
    val ctx = LocalContext.current
    var bmp by remember(s.file) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(s.file) {
        bmp = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(LayoutStore.stickerFile(ctx, s).path)?.asImageBitmap() }.getOrNull()
        }
    }
    val b = bmp ?: return
    val onSel by rememberUpdatedState(onSelect)
    val onTr by rememberUpdatedState(onTransform)
    val onDrag by rememberUpdatedState(onDragState)
    Image(
        b,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .placeAt(s.x, s.y)
            .then(
                if (editing) {
                    Modifier
                        .border(if (isSelected) 2.dp else 1.dp, if (isSelected) Accent else Color(0x73FFFFFF), RoundedCornerShape(6.dp))
                        .pointerInput(s.id) { detectTapGestures { onSel() } }
                        .dragZoom(s.id, onStart = { onDrag(true) }, onEnd = { onDrag(false) }) { pan, zoom -> onTr(pan, zoom) }
                } else {
                    Modifier
                },
            )
            .width((s.widthDp * sizeScale).dp)
            .aspectRatio(b.width.toFloat() / b.height.coerceAtLeast(1)),
    )
}

@Composable
private fun BottomBar(
    look: LockLook,
    lang: Lang,
    hint: String,
    showCamera: Boolean,
    onEmergency: () -> Unit,
    onCamera: () -> Unit,
) {
    val chip = if (look.darkStatusIcons) Color(0x22000000) else Color(0x26FFFFFF)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Text(
                tr("紧急呼叫", "緊急呼叫", "Emergency", lang),
                color = look.text,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(chip)
                    .clickable(onClick = onEmergency)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
        UnlockHint(hint, look.text, look.sub)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            // 右下角不显示相机入口（按需求去掉）
        }
    }
}

@Composable
private fun UnlockHint(text: String, color: Color, sub: Color) {
    val bob by rememberInfiniteTransition(label = "hint").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer {
            translationY = -bob * 8.dp.toPx()
            alpha = 0.55f + 0.45f * bob
        },
    ) {
        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
        Text(text, color = sub, fontSize = 12.sp, letterSpacing = 2.sp)
    }
}

// ======================== 编辑面板 ========================

@Composable
private fun EditPanel(
    draft: LockLayout,
    selected: String?,
    lang: Lang,
    onDraft: (LockLayout) -> Unit,
    onSelect: (String?) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val fontsVersion by Fonts.version.collectAsState()
    val fonts = remember(fontsVersion) { Fonts.list(ctx) }
    val currentDraft by rememberUpdatedState(draft)

    val pickSticker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                LayoutStore.importSticker(ctx, uri)?.let { s ->
                    onDraft(currentDraft.copy(stickers = currentDraft.stickers + s))
                    onSelect(keyOf(s))
                }
            }
        }
    }
    val pickFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val id = Fonts.import(ctx, uri)
                val sel = selected
                if (id != null && sel != null && sel.startsWith("E:")) {
                    val e = Elem.valueOf(sel.removePrefix("E:"))
                    onDraft(currentDraft.with(e, currentDraft.style(e).copy(font = id)))
                } else if (id == null) {
                    android.widget.Toast.makeText(ctx, tr("不是有效的字体文件", "不是有效的字型檔", "Not a valid font file", lang), android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(10.dp),
    ) {
        // 顶部操作条
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PanelBg)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text(tr("取消", "取消", "Cancel", lang), color = Color.White) }
            TextButton(onClick = { pickSticker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text(tr("添加图片", "加入圖片", "Add image", lang), color = Accent)
            }
            TextButton(onClick = {
                onDraft(LockLayout.default().copy(stickers = currentDraft.stickers))
                onSelect(null)
            }) { Text(tr("还原位置", "還原位置", "Reset", lang), color = Accent) }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF2A1F05)),
            ) { Text(tr("保存", "儲存", "Save", lang)) }
        }
        Text(
            tr("拖动移动位置 · 双指缩放大小 · 点选后可改字体", "拖曳移動位置 · 雙指縮放大小 · 點選後可改字型", "Drag to move · pinch to resize · tap to edit font", lang),
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            modifier = Modifier
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x66000000))
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.weight(1f))

        val sel = selected ?: return@Column
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(PanelBg)
                // 吞掉面板上的点击，避免穿透到下面取消选择
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(14.dp),
        ) {
            if (sel.startsWith("E:")) {
                val e = Elem.valueOf(sel.removePrefix("E:"))
                val st = draft.style(e)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.label(lang), color = Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onDraft(draft.with(e, st.copy(visible = !st.visible))) }) {
                        Text(if (st.visible) tr("隐藏", "隱藏", "Hide", lang) else tr("显示", "顯示", "Show", lang), color = Accent)
                    }
                    IconButton(onClick = { onSelect(null) }) { Icon(Icons.Filled.Close, null, tint = Color.White) }
                }
                Text(tr("字号", "字級", "Size", lang) + "  ${(e.baseSp * st.scale).roundToInt()}sp", color = PanelSub, fontSize = 12.sp)
                Slider(
                    value = st.scale,
                    onValueChange = { onDraft(draft.with(e, st.copy(scale = it))) },
                    valueRange = 0.4f..3f,
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
                )
                Text(tr("字体", "字型", "Font", lang), color = PanelSub, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(fonts, key = { it.id }) { f ->
                        Chip(f.label(lang), st.font == f.id) { onDraft(draft.with(e, st.copy(font = f.id))) }
                    }
                    item {
                        Chip("+ " + tr("导入字体", "匯入字型", "Import", lang), false) {
                            pickFont.launch(arrayOf("font/ttf", "font/otf", "font/*", "application/x-font-ttf", "application/octet-stream", "*/*"))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(tr("粗细", "粗細", "Weight", lang), color = PanelSub, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(-1 to tr("默认", "預設", "Auto", lang), 0 to tr("细", "細", "Light", lang), 1 to tr("常规", "標準", "Regular", lang), 2 to tr("粗", "粗", "Bold", lang)).forEach { (w, label) ->
                        Chip(label, st.weight == w) { onDraft(draft.with(e, st.copy(weight = w))) }
                    }
                }
            } else {
                val id = sel.removePrefix("S:")
                val s = draft.stickers.firstOrNull { it.id == id } ?: return@Column
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("图片", "圖片", "Image", lang), color = Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        onDraft(draft.copy(stickers = draft.stickers.filter { it.id != id }))
                        onSelect(null)
                    }) { Text(tr("删除", "刪除", "Delete", lang), color = Color(0xFFFF6B6B)) }
                    IconButton(onClick = { onSelect(null) }) { Icon(Icons.Filled.Close, null, tint = Color.White) }
                }
                Text(tr("大小", "大小", "Size", lang) + "  ${s.widthDp.roundToInt()}dp", color = PanelSub, fontSize = 12.sp)
                Slider(
                    value = s.widthDp,
                    onValueChange = { v -> onDraft(draft.copy(stickers = draft.stickers.map { if (it.id == id) it.copy(widthDp = v) else it })) },
                    valueRange = 20f..400f,
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
                )
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color(0xFF2A1F05) else Color.White,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Accent else Color(0x33FFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

// ======================== 密码键盘 ========================

@Composable
private fun PinPad(lang: Lang, onCancel: () -> Unit, onSuccess: () -> Unit) {
    val ctx = LocalContext.current
    val len = remember { Prefs(ctx).pinLength.coerceIn(4, 8) }
    var input by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var fails by remember { mutableIntStateOf(0) }
    var lockedUntil by remember { mutableLongStateOf(0L) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(lockedUntil) {
        while (System.currentTimeMillis() < lockedUntil) {
            val left = (lockedUntil - System.currentTimeMillis()) / 1000 + 1
            message = tr("请 $left 秒后再试", "請 $left 秒後再試", "Try again in ${left}s", lang)
            delay(500)
        }
        if (lockedUntil != 0L) message = ""
    }

    fun press(d: Char) {
        if (System.currentTimeMillis() < lockedUntil || input.length >= len) return
        input += d
        if (input.length == len) {
            if (Security.check(ctx, input)) {
                onSuccess()
            } else {
                fails++
                input = ""
                message = tr("密码错误", "密碼錯誤", "Wrong PIN", lang)
                if (fails >= 5) {
                    lockedUntil = System.currentTimeMillis() + 30_000
                    fails = 0
                }
                scope.launch {
                    for (x in listOf(20f, -20f, 14f, -14f, 6f, 0f)) shake.animateTo(x, tween(45))
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.safeDrawingPadding()) {
            Text(tr("输入锁屏密码", "輸入鎖定畫面密碼", "Enter PIN", lang), color = Color.White, fontSize = 18.sp)
            Spacer(Modifier.height(18.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.graphicsLayer { translationX = shake.value },
            ) {
                repeat(len) { i ->
                    Box(
                        Modifier
                            .size(13.dp)
                            .clip(CircleShape)
                            .background(if (i < input.length) Color.White else Color(0x40FFFFFF)),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(message, color = Color(0xFFFF8A80), fontSize = 13.sp, modifier = Modifier.height(20.dp))
            Spacer(Modifier.height(16.dp))
            val rows = listOf("123", "456", "789")
            rows.forEach { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) { r.forEach { d -> PinKey(d.toString()) { press(d) } } }
                Spacer(Modifier.height(16.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                PinKey(tr("取消", "取消", "Cancel", lang), small = true) { onCancel() }
                PinKey("0") { press('0') }
                PinKey(tr("删除", "刪除", "Delete", lang), small = true) { if (input.isNotEmpty()) input = input.dropLast(1) }
            }
        }
    }
}

/** 图案解锁：连错 5 次锁 30 秒，和数字密码一致 */
@Composable
private fun PatternUnlock(lang: Lang, onCancel: () -> Unit, onSuccess: () -> Unit) {
    val ctx = LocalContext.current
    var message by remember { mutableStateOf("") }
    var fails by remember { mutableIntStateOf(0) }
    var lockedUntil by remember { mutableLongStateOf(0L) }
    var locked by remember { mutableStateOf(false) }

    LaunchedEffect(lockedUntil) {
        while (System.currentTimeMillis() < lockedUntil) {
            locked = true
            val left = (lockedUntil - System.currentTimeMillis()) / 1000 + 1
            message = tr("请 $left 秒后再试", "請 $left 秒後再試", "Try again in ${left}s", lang)
            delay(500)
        }
        locked = false
        if (lockedUntil != 0L) message = ""
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.safeDrawingPadding()) {
            Text(tr("绘制解锁图案", "繪製解鎖圖案", "Draw unlock pattern", lang), color = Color.White, fontSize = 18.sp)
            Spacer(Modifier.height(10.dp))
            Text(message, color = Color(0xFFFF8A80), fontSize = 13.sp, modifier = Modifier.height(20.dp))
            Spacer(Modifier.height(16.dp))
            PatternPad(enabled = !locked) { nodes ->
                if (Security.check(ctx, nodes.patternKey())) {
                    onSuccess()
                    true
                } else {
                    fails++
                    message = if (nodes.size < PATTERN_MIN) {
                        tr("至少连接 $PATTERN_MIN 个点", "至少連接 $PATTERN_MIN 個點", "Connect at least $PATTERN_MIN dots", lang)
                    } else {
                        tr("图案错误", "圖案錯誤", "Wrong pattern", lang)
                    }
                    if (fails >= 5) {
                        lockedUntil = System.currentTimeMillis() + 30_000
                        fails = 0
                    }
                    false
                }
            }
            Spacer(Modifier.height(24.dp))
            PinKey(tr("取消", "取消", "Cancel", lang), small = true) { onCancel() }
        }
    }
}

@Composable
private fun PinKey(label: String, small: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(74.dp)
            .clip(CircleShape)
            .background(if (small) Color.Transparent else Color(0x26FFFFFF))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = if (small) 15.sp else 28.sp, fontWeight = FontWeight.Light)
    }
}
