package club.daishenghui.minguo

import android.Manifest
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

private val Accent = Color(0xFFE8C77A)
private val OnAccent = Color(0xFF2A1F05)
private val PageBg = Color(0xFF0E1116)
private val CardBg = Color(0xFF171C24)
private val FieldBg = Color(0xFF0F1319)
private val SubText = Color(0xFF8E98A8)
private val Good = Color(0xFF5BD68A)
private val Warn = Color(0xFFFFB454)
private val Danger = Color(0xFFFF6B6B)

private enum class Page { HOME, SETTINGS, LOCK_MODE, APPEARANCE, DATE_LANG, WEATHER, WIDGETS, PERMISSIONS, ABOUT }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        L.refresh(this)
        LayoutStore.init(this)
        Weather.init(this)
        Quotes.init(this)
        LockService.sync(this)
        Weather.schedule(this)
        Wallpaper.schedule(this)
        Watchdog.schedule(this)
        requestedPage.value = intent?.getStringExtra(EXTRA_PAGE)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(primary = Accent, onPrimary = OnAccent, background = PageBg, surface = CardBg),
            ) {
                Surface(Modifier.fillMaxSize(), color = PageBg) { PermissionGate { App() } }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedPage.value = intent.getStringExtra(EXTRA_PAGE)
    }

    override fun onResume() {
        super.onResume()
        L.refresh(this)
        IconSwitcher.publishShortcuts(this)
        // 在前台时同步一次服务：刚授权位置后，服务会升级为可在锁屏时定位
        LockService.sync(this)
    }

    companion object {
        /** 从小组件等处打开指定页面 */
        const val EXTRA_PAGE = "page"
        const val PAGE_WEATHER = "weather"
    }
}

/** 外部请求打开的页面（小组件点天气 → 天气页） */
private val requestedPage = mutableStateOf<String?>(null)

// =====================================================================
// 导航
// =====================================================================

@Composable
private fun App() {
    var page by rememberSaveable { mutableStateOf(Page.HOME) }
    val ctx = LocalContext.current
    // 每次回到前台重新检查权限等状态
    var resumeTick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumeTick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(Unit) { Weather.refreshIfStale(ctx, inForeground = true) }
    val req = requestedPage.value
    LaunchedEffect(req) {
        if (req == MainActivity.PAGE_WEATHER) page = Page.WEATHER
        if (req != null) requestedPage.value = null
    }

    BackHandler(enabled = page != Page.HOME) {
        page = if (page == Page.SETTINGS) Page.HOME else Page.SETTINGS
    }
    val go: (Page) -> Unit = { page = it }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        TopBar(page, onBack = { page = if (page == Page.SETTINGS) Page.HOME else Page.SETTINGS }, onSettings = { page = Page.SETTINGS })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (page) {
                Page.HOME -> HomePage(go)
                Page.SETTINGS -> SettingsPage(go)
                Page.LOCK_MODE -> LockModePage(resumeTick)
                Page.APPEARANCE -> AppearancePage()
                Page.DATE_LANG -> DateLangPage()
                Page.WEATHER -> WeatherPage()
                Page.WIDGETS -> WidgetsPage()
                Page.PERMISSIONS -> PermissionsPage(resumeTick)
                Page.ABOUT -> AboutPage()
            }
            Footer()
        }
    }
}

@Composable
private fun TopBar(page: Page, onBack: () -> Unit, onSettings: () -> Unit) {
    val title = when (page) {
        Page.HOME -> IconSwitcher.displayName(LocalContext.current)
        Page.SETTINGS -> tr("设置", "設定", "Settings")
        Page.LOCK_MODE -> tr("锁屏模式与密码", "鎖定畫面模式與密碼", "Lock mode and PIN")
        Page.APPEARANCE -> tr("壁纸与字体", "桌布與字型", "Wallpaper and fonts")
        Page.DATE_LANG -> tr("日期与语言", "日期與語言", "Date and language")
        Page.WEATHER -> tr("天气", "天氣", "Weather")
        Page.WIDGETS -> tr("桌面小组件", "桌面小工具", "Widgets")
        Page.PERMISSIONS -> tr("权限检查", "權限檢查", "Permissions")
        Page.ABOUT -> tr("关于", "關於", "About")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (page != Page.HOME) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("返回", "返回", "Back"), tint = Color.White) }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(title, color = Color.White, fontSize = if (page == Page.HOME) 24.sp else 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (page == Page.HOME) {
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, tr("设置", "設定", "Settings"), tint = Color.White) }
        }
    }
}

@Composable
private fun Footer() {
    Text(
        "© 2026 ${AppConfig.AUTHOR}",
        color = SubText.copy(alpha = 0.6f),
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    )
}

// =====================================================================
// 首页
// =====================================================================

@Composable
private fun HomePage(go: (Page) -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()
    val lang = L.current
    var lockEnabled by remember { mutableStateOf(prefs.lockEnabled) }
    var signature by remember { mutableStateOf(prefs.signature) }
    val quote by Quotes.state.collectAsState()
    val now by rememberNow()
    val style = prefs.style()

    Text(
        RocCalendar.dateLine(now.toLocalDate(), style) + " · " + RocCalendar.lunarShort(System.currentTimeMillis(), style),
        color = SubText,
        fontSize = 14.sp,
        modifier = Modifier.padding(start = 4.dp),
    )

    // 小预览：按手机屏幕比例缩小
    val (sw, sh) = remember { Wallpaper.screenSize(ctx) }
    val density = LocalDensity.current
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val h = 380.dp
        val w = h * (sw.toFloat() / sh)
        val screenHdp = with(density) { sh.toDp() }
        Box(
            Modifier
                .size(w, h)
                .clip(RoundedCornerShape(26.dp))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(26.dp)),
        ) {
            LockContent(ui = LockUi.MINI, sizeScale = h / screenHdp)
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = { LockActivity.open(ctx, edit = true) },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
        ) { Text(tr("编辑锁屏布局", "編輯鎖定畫面", "Edit layout")) }
        FilledTonalButton(onClick = { LockActivity.open(ctx, edit = false) }, modifier = Modifier.weight(1f)) {
            Text(tr("全屏预览", "全螢幕預覽", "Preview"))
        }
    }

    Section(tr("锁屏", "鎖定畫面", "Lock screen")) {
        SwitchRow(tr("启用民国锁屏", "啟用民國鎖定畫面", "Enable ROC lock screen"), null, lockEnabled) {
            lockEnabled = it
            prefs.lockEnabled = it
            LockService.sync(ctx)
        }
        MenuRow(
            tr("锁屏模式", "鎖定畫面模式", "Lock mode"),
            if (prefs.lockMode == Prefs.MODE_REPLACE) {
                tr("完全替代系统锁屏", "完全取代系統鎖定畫面", "Replace system lock screen")
            } else {
                tr("保留系统密码", "保留系統密碼", "Keep system PIN")
            },
        ) { go(Page.LOCK_MODE) }
    }

    Section(tr("锁屏签名", "鎖定畫面簽名", "Signature")) {
        OutlinedTextField(
            value = signature,
            onValueChange = {
                signature = it
                prefs.signature = it
            },
            placeholder = { Text(tr("写点什么，显示在锁屏上…", "寫點什麼，顯示在鎖定畫面上…", "Write something…")) },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, cursorColor = Accent),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            tr(
                "${signature.length} 字 · 不限字数，单行显示、字号固定，超长自动向左滚动；位置和字体在“编辑锁屏布局”里调整",
                "${signature.length} 字 · 不限字數，單行顯示、字級固定，超長自動向左捲動；位置和字型在「編輯鎖定畫面」裡調整",
                "${signature.length} chars · no limit, single line with marquee; move it in Edit layout",
            ),
            color = SubText,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }

    Section(tr("名言", "名言", "Quote")) {
        val q = quote?.let { Quotes.display(it, lang) }
        if (q != null) {
            Text("「${q.text}」", color = Color.White, fontSize = 15.sp, lineHeight = 22.sp)
        }
        TextButton(onClick = { scope.launch { Quotes.next(ctx) } }) { Text(tr("换一句", "換一句", "Next"), color = Accent) }
    }

    Section(tr("快捷设置", "快捷設定", "Shortcuts")) {
        MenuRow(tr("壁纸与字体", "桌布與字型", "Wallpaper and fonts"), null) { go(Page.APPEARANCE) }
        MenuRow(tr("天气", "天氣", "Weather"), null) { go(Page.WEATHER) }
        MenuRow(tr("桌面小组件", "桌面小工具", "Widgets"), null) { go(Page.WIDGETS) }
        MenuRow(tr("权限检查", "權限檢查", "Permissions"), null) { go(Page.PERMISSIONS) }
    }
}

// =====================================================================
// 设置菜单
// =====================================================================

@Composable
private fun SettingsPage(go: (Page) -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()
    var dateNotify by remember { mutableStateOf(prefs.dateNotification) }
    var cacheSize by remember { mutableLongStateOf(-1L) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { cacheSize = Maintenance.cacheSize(ctx) }

    Section(tr("锁屏与外观", "鎖定畫面與外觀", "Lock screen and look")) {
        MenuRow(tr("锁屏模式与密码", "鎖定畫面模式與密碼", "Lock mode and PIN"), null) { go(Page.LOCK_MODE) }
        MenuRow(tr("壁纸与字体", "桌布與字型", "Wallpaper and fonts"), null) { go(Page.APPEARANCE) }
        MenuRow(tr("编辑锁屏布局", "編輯鎖定畫面", "Edit layout"), tr("拖动位置、字号、字体、添加图片", "拖曳位置、字級、字型、加入圖片", "Move, resize, fonts, images")) {
            LockActivity.open(ctx, edit = true)
        }
        MenuRow(tr("日期与语言", "日期與語言", "Date and language"), null) { go(Page.DATE_LANG) }
    }
    Section(tr("内容", "內容", "Content")) {
        MenuRow(tr("天气", "天氣", "Weather"), null) { go(Page.WEATHER) }
        MenuRow(tr("桌面小组件", "桌面小工具", "Widgets"), null) { go(Page.WIDGETS) }
        SwitchRow(
            tr("通知栏民国日期", "通知欄民國日期", "ROC date in notifications"),
            tr("下拉通知栏显示民国日期、农历和天气", "下拉通知欄顯示民國日期、農曆和天氣", "Pull down to see ROC date, lunar date and weather"),
            dateNotify,
        ) {
            dateNotify = it
            prefs.dateNotification = it
            LockService.sync(ctx)
        }
    }
    Section(tr("系统", "系統", "System")) {
        MenuRow(tr("权限检查", "權限檢查", "Permissions"), null) { go(Page.PERMISSIONS) }
        MenuRow(
            tr("清空缓存", "清除快取", "Clear cache"),
            if (cacheSize >= 0) Maintenance.formatSize(cacheSize) else "…",
        ) { dialog = "cache" }
        MenuRow(tr("重置", "重設", "Reset"), tr("恢复全部默认设置", "還原全部預設設定", "Restore all defaults")) { dialog = "reset" }
        MenuRow(tr("检查更新", "檢查更新", "Check for updates"), tr("当前版本", "目前版本", "Current") + " " + Maintenance.versionName(ctx)) {
            scope.launch {
                busy = true
                val r = runCatching { Maintenance.checkUpdate(ctx) }
                busy = false
                r.onSuccess { info ->
                    if (info == null) toast(ctx, tr("已是最新版本", "已是最新版本", "You're up to date")) else update = info
                }.onFailure { toast(ctx, tr("检查失败，请稍后再试", "檢查失敗，請稍後再試", "Couldn't check for updates")) }
            }
        }
        MenuRow(tr("反馈", "意見回饋", "Feedback"), null) { Maintenance.feedback(ctx) }
        MenuRow(tr("关于", "關於", "About"), null) { go(Page.ABOUT) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
    }

    when (dialog) {
        "cache" -> ConfirmDialog(
            tr("清空缓存", "清除快取", "Clear cache"),
            tr("将删除必应壁纸、天气缓存等临时数据（不影响设置、布局、签名和自选壁纸）。", "將刪除 Bing 桌布、天氣快取等暫存資料（不影響設定、版面、簽名和自選桌布）。", "Removes the Bing wallpaper, weather cache and temporary files. Settings and layout are kept."),
            onDismiss = { dialog = null },
        ) {
            dialog = null
            scope.launch {
                Maintenance.clearCache(ctx)
                cacheSize = Maintenance.cacheSize(ctx)
                toast(ctx, tr("已清空", "已清除", "Cleared"))
            }
        }
        "reset" -> ConfirmDialog(
            tr("重置", "重設", "Reset"),
            tr("所有设置、锁屏布局、签名、密码、壁纸、贴图和导入的字体都会被清除，无法恢复。", "所有設定、版面、簽名、密碼、桌布、貼圖和匯入的字型都會被清除，無法復原。", "All settings, layout, signature, PIN, wallpapers, images and fonts will be erased."),
            danger = true,
            onDismiss = { dialog = null },
        ) {
            dialog = null
            scope.launch {
                Maintenance.resetAll(ctx)
                toast(ctx, tr("已恢复默认设置", "已還原預設設定", "Reset complete"))
                (ctx as? ComponentActivity)?.recreate()
            }
        }
    }

    update?.let { info ->
        AlertDialog(
            onDismissRequest = { update = null },
            containerColor = CardBg,
            title = { Text(tr("发现新版本", "發現新版本", "Update available") + " " + info.versionName, color = Color.White) },
            text = { Text(info.notes.ifBlank { "—" }, color = SubText) },
            confirmButton = {
                TextButton(onClick = {
                    update = null
                    Maintenance.openUrl(ctx, info.url)
                }) { Text(tr("下载", "下載", "Download"), color = Accent) }
            },
            dismissButton = { TextButton(onClick = { update = null }) { Text(tr("以后再说", "稍後", "Later"), color = SubText) } },
        )
    }
}

// =====================================================================
// 锁屏模式与密码
// =====================================================================

@Composable
private fun LockModePage(tick: Int) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var enabled by remember { mutableStateOf(prefs.lockEnabled) }
    var mode by remember { mutableIntStateOf(prefs.lockMode) }
    var hasPin by remember { mutableStateOf(Security.hasPin(ctx)) }
    var setPin by remember { mutableStateOf(false) }
    var setPattern by remember { mutableStateOf(false) }
    val systemSecure = remember(tick) { ctx.getSystemService(KeyguardManager::class.java).isDeviceSecure }

    Section(tr("锁屏", "鎖定畫面", "Lock screen")) {
        SwitchRow(tr("启用民国锁屏", "啟用民國鎖定畫面", "Enable ROC lock screen"), null, enabled) {
            enabled = it
            prefs.lockEnabled = it
            LockService.sync(ctx)
        }
        Spacer(Modifier.height(8.dp))
        ChoiceRow(listOf(tr("完全替代", "完全取代", "Replace"), tr("保留系统密码", "保留系統密碼", "Keep system PIN")), if (mode == Prefs.MODE_REPLACE) 0 else 1) {
            mode = if (it == 0) Prefs.MODE_REPLACE else Prefs.MODE_KEEP_SYSTEM
            prefs.lockMode = mode
            LockService.sync(ctx)
        }
        Spacer(Modifier.height(10.dp))
        if (mode == Prefs.MODE_REPLACE) {
            Paragraph(
                tr(
                    "系统锁屏改为「无」后，民国锁屏就是唯一的锁屏：亮屏、上滑解锁全程看不到系统时间；按 Home、返回都关不掉。\n代价：指纹/人脸需要系统锁屏密码，关掉后无法使用；锁屏时仍可下拉通知栏和控制中心。关掉系统锁屏后，可以在下方设置图案或数字密码。",
                    "系統鎖定畫面改為「無」後，民國鎖定畫面就是唯一的鎖定畫面：亮屏、上滑解鎖全程看不到系統時間；按 Home、返回都關不掉。\n代價：指紋/臉部辨識需要系統密碼，關掉後無法使用；鎖定時仍可下拉通知欄和控制中心。建議在下方設定鎖定畫面密碼。",
                    "With the system lock set to None, this becomes the only lock screen: the system clock never shows and Home/Back can't dismiss it.\nTrade-off: fingerprint/face need a system PIN, so they won't work; the notification shade can still be pulled down. Set a PIN below.",
                ),
            )
            Spacer(Modifier.height(10.dp))
            StatusLine(
                ok = !systemSecure,
                text = if (systemSecure) {
                    tr("系统锁屏密码仍然开启，会出现两层锁屏。请改为「无」。", "系統鎖定畫面密碼仍開啟，會出現兩層鎖定畫面。請改為「無」。", "The system lock is still on — set it to None to avoid two lock screens.")
                } else {
                    tr("系统锁屏已关闭 ✓", "系統鎖定畫面已關閉 ✓", "System lock is off ✓")
                },
            )
            TextButton(onClick = { ctx.safeStart(Intent(Settings.ACTION_SECURITY_SETTINGS)) }) {
                Text(tr("打开系统锁屏设置", "開啟系統鎖定畫面設定", "Open system lock settings"), color = Accent)
            }
        } else {
            Paragraph(
                tr(
                    "民国锁屏盖在系统锁屏上：指纹/人脸可以直接解锁；上滑时会出现系统的密码界面（这一步会看到系统时间，无法避免）。",
                    "民國鎖定畫面蓋在系統鎖定畫面上：指紋/臉部可以直接解鎖；上滑時會出現系統密碼介面（這一步會看到系統時間，無法避免）。",
                    "Shown on top of the system lock: fingerprint/face unlock directly; swiping shows the system PIN screen (with its clock).",
                ),
            )
        }
    }

    if (mode == Prefs.MODE_REPLACE) {
        Section(tr("锁屏密码", "鎖定畫面密碼", "Lock password")) {
            if (systemSecure) {
                // 系统锁屏还开着：不允许在 App 里设密码，否则会出现两层锁屏
                StatusLine(
                    ok = false,
                    text = tr("请先关闭系统锁屏，才能在这里设置密码", "請先關閉系統鎖定畫面，才能在這裡設定密碼", "Turn off the system lock first to set a password here"),
                )
                Paragraph(
                    tr(
                        "到系统设置 → 锁屏 → 锁屏方式，改为「无」，回到这里就可以设置图案或数字密码。",
                        "到系統設定 → 鎖定畫面 → 螢幕鎖定方式，改為「無」，回到這裡就可以設定圖案或數字密碼。",
                        "In system settings, set the screen lock to None, then come back to set a pattern or PIN.",
                    ),
                )
                TextButton(onClick = { ctx.safeStart(Intent(Settings.ACTION_SECURITY_SETTINGS)) }) {
                    Text(tr("打开系统锁屏设置", "開啟系統鎖定畫面設定", "Open system lock settings"), color = Accent)
                }
            } else {
                val isPattern = prefs.pinType == Security.TYPE_PATTERN
                Text(
                    when {
                        !hasPin -> tr("未设置：上滑直接解锁", "未設定：上滑直接解鎖", "Not set: swipe unlocks directly")
                        isPattern -> tr("已设置：图案", "已設定：圖案", "Set: pattern")
                        else -> tr("已设置：${prefs.pinLength} 位数字密码", "已設定：${prefs.pinLength} 位數字密碼", "Set: ${prefs.pinLength}-digit PIN")
                    },
                    color = if (hasPin) Good else SubText,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(tr("选择密码类型", "選擇密碼類型", "Choose a type"), color = SubText, fontSize = 13.sp)
                Row {
                    TextButton(onClick = { setPin = true }) { Text(tr("数字密码", "數字密碼", "PIN"), color = Accent) }
                    TextButton(onClick = { setPattern = true }) { Text(tr("图案", "圖案", "Pattern"), color = Accent) }
                    if (hasPin) {
                        TextButton(onClick = {
                            Security.clearPin(ctx)
                            hasPin = false
                        }) { Text(tr("清除密码", "清除密碼", "Remove"), color = Danger) }
                    }
                }
            }
        }
    }

    if (setPin) {
        PinSetupDialog(onDismiss = { setPin = false }) { pin ->
            Security.setPin(ctx, pin, Security.TYPE_PIN)
            hasPin = true
            setPin = false
            toast(ctx, tr("数字密码已设置", "數字密碼已設定", "PIN set"))
        }
    }
    if (setPattern) {
        PatternSetupDialog(onDismiss = { setPattern = false }) { key ->
            Security.setPin(ctx, key, Security.TYPE_PATTERN)
            hasPin = true
            setPattern = false
            toast(ctx, tr("图案已设置", "圖案已設定", "Pattern set"))
        }
    }
}

/** 设置图案：画两次一致才保存 */
@Composable
private fun PatternSetupDialog(onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var hint by remember { mutableStateOf(tr("绘制解锁图案（至少连接 $PATTERN_MIN 个点）", "繪製解鎖圖案（至少連接 $PATTERN_MIN 個點）", "Draw a pattern (at least $PATTERN_MIN dots)")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = { Text(tr("设置图案", "設定圖案", "Set pattern"), color = Color.White) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(hint, color = SubText, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                PatternPad(size = 240.dp) { nodes ->
                    val key = nodes.patternKey()
                    when {
                        nodes.size < PATTERN_MIN -> {
                            hint = tr("至少连接 $PATTERN_MIN 个点，请重画", "至少連接 $PATTERN_MIN 個點，請重畫", "Connect at least $PATTERN_MIN dots")
                            false
                        }
                        first == null -> {
                            first = key
                            hint = tr("再画一次确认", "再畫一次確認", "Draw again to confirm")
                            true
                        }
                        first == key -> {
                            onDone(key)
                            true
                        }
                        else -> {
                            first = null
                            hint = tr("两次不一致，请重新绘制", "兩次不一致，請重新繪製", "Didn't match — start over")
                            false
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("取消", "取消", "Cancel"), color = SubText) } },
    )
}

@Composable
private fun PinSetupDialog(onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = { Text(tr("设置锁屏密码", "設定鎖定畫面密碼", "Set PIN"), color = Color.White) },
        text = {
            Column {
                PinField(first, tr("4–8 位数字", "4–8 位數字", "4–8 digits")) { first = it; error = "" }
                Spacer(Modifier.height(8.dp))
                PinField(second, tr("再输入一次", "再輸入一次", "Repeat")) { second = it; error = "" }
                if (error.isNotEmpty()) Text(error, color = Danger, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    first.length !in 4..8 -> tr("请输入 4–8 位数字", "請輸入 4–8 位數字", "Use 4–8 digits")
                    first != second -> tr("两次输入不一致", "兩次輸入不一致", "PINs don't match")
                    else -> ""
                }
                if (error.isEmpty()) onDone(first)
            }) { Text(tr("确定", "確定", "OK"), color = Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("取消", "取消", "Cancel"), color = SubText) } },
    )
}

@Composable
private fun PinField(value: String, hint: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(8)) },
        singleLine = true,
        placeholder = { Text(hint) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, cursorColor = Accent),
        modifier = Modifier.fillMaxWidth(),
    )
}

// =====================================================================
// 壁纸与字体
// =====================================================================

@Composable
private fun AppearancePage() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()
    var themeIndex by remember { mutableIntStateOf(prefs.themeIndex) }
    var wallMode by remember { mutableIntStateOf(prefs.wallpaperMode) }
    var dim by remember { mutableFloatStateOf(prefs.wallpaperDim.toFloat()) }
    var busy by remember { mutableStateOf(false) }
    val fontsVersion by Fonts.version.collectAsState()
    val allFonts = remember(fontsVersion) { Fonts.list(ctx) }
    val fonts = allFonts.filter { it.id.startsWith("file:") }
    val builtinFonts = allFonts.filter { !it.id.startsWith("file:") }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                val ok = Wallpaper.importCustom(ctx, uri)
                busy = false
                if (!ok) toast(ctx, tr("图片读取失败", "圖片讀取失敗", "Couldn't read that image"))
            }
        }
    }
    val pickFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                if (Fonts.import(ctx, uri) == null) toast(ctx, tr("不是有效的字体文件", "不是有效的字型檔", "Not a valid font file"))
            }
        }
    }
    fun refreshBing(force: Boolean) {
        scope.launch {
            busy = true
            val ok = Wallpaper.refreshBing(ctx, force)
            busy = false
            if (!ok) toast(ctx, tr("必应壁纸下载失败，请检查网络", "Bing 桌布下載失敗，請檢查網路", "Couldn't download the Bing wallpaper"))
        }
    }

    Section(tr("锁屏壁纸", "鎖定畫面桌布", "Wallpaper")) {
        ChoiceRow(listOf(tr("主题渐变", "主題漸層", "Gradient"), tr("自选图片", "自選圖片", "My photo"), tr("必应每日", "Bing 每日", "Bing daily")), wallMode) { m ->
            wallMode = m
            prefs.wallpaperMode = m
            Wallpaper.schedule(ctx)
            if (m == Wallpaper.MODE_BING) refreshBing(false)
        }
        Spacer(Modifier.height(12.dp))
        when (wallMode) {
            Wallpaper.MODE_THEME -> LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                itemsIndexed(ClockThemes) { i, t ->
                    ThemeSwatch(t, selected = i == themeIndex) {
                        themeIndex = i
                        prefs.themeIndex = i
                    }
                }
            }
            Wallpaper.MODE_CUSTOM -> Button(
                onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(tr("从相册选择图片", "從相簿選擇圖片", "Choose from gallery")) }
            else -> TextButton(onClick = { refreshBing(true) }) { Text(tr("立即更新", "立即更新", "Update now"), color = Accent) }
        }
        if (wallMode != Wallpaper.MODE_THEME) {
            Spacer(Modifier.height(8.dp))
            Text(tr("壁纸暗化", "桌布調暗", "Dim") + "  ${dim.roundToInt()}%", color = SubText, fontSize = 13.sp)
            Slider(
                value = dim,
                onValueChange = { dim = it },
                onValueChangeFinished = { prefs.wallpaperDim = dim.roundToInt() },
                valueRange = 0f..70f,
                colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
            )
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
    }

    Section(tr("字体", "字型", "Fonts")) {
        Paragraph(
            tr(
                "每个元素（时间、日期、名言、签名…）的字体、字号、粗细都在“编辑锁屏布局”里点选后设置。这里管理导入的字体文件（TTF / OTF）。",
                "每個元素（時間、日期、名言、簽名…）的字型、字級、粗細都在「編輯鎖定畫面」裡點選後設定。這裡管理匯入的字型檔（TTF / OTF）。",
                "Set each element's font, size and weight in Edit layout. Manage imported TTF/OTF fonts here.",
            ),
        )
        Spacer(Modifier.height(8.dp))
        Text(tr("内置字体", "內建字型", "Built-in fonts"), color = SubText, fontSize = 13.sp)
        builtinFonts.forEach { f ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(f.label(), color = SubText, fontSize = 13.sp, modifier = Modifier.width(96.dp))
                Text(
                    tr("民国一一五年 12:45", "民國一一五年 12:45", "ROC 115 12:45"),
                    color = Color.White,
                    fontSize = 17.sp,
                    fontFamily = Fonts.family(ctx, f.id),
                    maxLines = 1,
                )
            }
        }
        if (fonts.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(tr("导入的字体", "匯入的字型", "Imported fonts"), color = SubText, fontSize = 13.sp)
        }
        fonts.forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.label(), color = Color.White, fontSize = 15.sp, fontFamily = Fonts.family(ctx, f.id), modifier = Modifier.weight(1f))
                TextButton(onClick = { Fonts.delete(ctx, f.id) }) { Text(tr("删除", "刪除", "Delete"), color = Danger) }
            }
        }
        Row {
            TextButton(onClick = { pickFont.launch(arrayOf("font/ttf", "font/otf", "font/*", "application/x-font-ttf", "application/octet-stream", "*/*")) }) {
                Text("+ " + tr("导入字体", "匯入字型", "Import font"), color = Accent)
            }
            TextButton(onClick = { LockActivity.open(ctx, edit = true) }) { Text(tr("编辑锁屏布局", "編輯鎖定畫面", "Edit layout"), color = Accent) }
        }
    }
}

// =====================================================================
// 日期与语言
// =====================================================================

@Composable
private fun DateLangPage() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val lang = L.current
    var language by remember { mutableIntStateOf(prefs.language) }
    var fullEra by remember { mutableStateOf(prefs.fullEra) }
    var cnNumerals by remember { mutableStateOf(prefs.chineseNumerals) }
    var showSeconds by remember { mutableStateOf(prefs.showSeconds) }
    val style = DisplayStyle(lang, fullEra, cnNumerals)
    val today = LocalDate.now()
    fun refreshOutputs() {
        Widgets.updateAll(ctx)
        LockService.sync(ctx)
    }

    Section(tr("语言", "語言", "Language")) {
        ChoiceRow(listOf(tr("跟随系统", "跟隨系統", "System"), "简体", "繁體", "English"), language) {
            language = it
            prefs.language = it
            L.refresh(ctx)
            refreshOutputs()
        }
        Spacer(Modifier.height(6.dp))
        Paragraph(tr("默认跟随系统语言；在这里手动选择后以手动为准。", "預設跟隨系統語言；在這裡手動選擇後以手動為準。", "Follows the system by default; a manual choice here takes priority."))
    }
    Section(tr("纪年", "紀年", "Era name")) {
        ChoiceRow(
            listOf(RocCalendar.yearText(today.year, style.copy(fullEra = false)), RocCalendar.yearText(today.year, style.copy(fullEra = true))),
            if (fullEra) 1 else 0,
        ) {
            fullEra = it == 1
            prefs.fullEra = fullEra
            refreshOutputs()
        }
        if (lang != Lang.EN) {
            Spacer(Modifier.height(12.dp))
            Label(tr("数字", "數字", "Numerals"))
            ChoiceRow(
                listOf(
                    RocCalendar.dateLine(today, style.copy(chineseNumerals = false)).removePrefix(RocCalendar.era(style)),
                    RocCalendar.dateLine(today, style.copy(chineseNumerals = true)).removePrefix(RocCalendar.era(style)),
                ),
                if (cnNumerals) 1 else 0,
            ) {
                cnNumerals = it == 1
                prefs.chineseNumerals = cnNumerals
                refreshOutputs()
            }
        }
        Spacer(Modifier.height(8.dp))
        SwitchRow(tr("显示秒", "顯示秒", "Show seconds"), null, showSeconds) {
            showSeconds = it
            prefs.showSeconds = it
            refreshOutputs()
        }
        Spacer(Modifier.height(4.dp))
        Paragraph(tr("预览：", "預覽：", "Preview: ") + RocCalendar.dateLine(today, style) + "  " + RocCalendar.weekday(today, lang))
    }
}

// =====================================================================
// 天气
// =====================================================================

@Composable
private fun WeatherPage() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()
    val lang = L.current
    val weather by Weather.state.collectAsState()
    var enabled by remember { mutableStateOf(prefs.weatherEnabled) }
    var autoLocate by remember { mutableStateOf(prefs.autoLocate) }
    var city by remember { mutableStateOf(prefs.cityName) }
    var busy by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

    fun refreshNow() {
        scope.launch {
            busy = true
            val ok = Weather.refresh(ctx, inForeground = true)
            busy = false
            city = prefs.cityName
            if (!ok) toast(ctx, tr("天气获取失败，请检查网络或位置", "天氣取得失敗，請檢查網路或位置", "Couldn't get weather"))
        }
    }

    fun locateNow() {
        scope.launch {
            busy = true
            val r = Weather.locate(ctx)
            if (r == null) {
                busy = false
                toast(ctx, tr("定位失败：请打开系统定位，或改用“搜索城市”", "定位失敗：請開啟系統定位，或改用「搜尋城市」", "Location failed — turn on Location or search a city"))
                return@launch
            }
            Weather.setLocation(ctx, r.name, r.location.latitude, r.location.longitude, r.place)
            prefs.locatedAt = System.currentTimeMillis()
            city = r.name
            busy = false
            refreshNow()
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        if (Weather.hasLocationPermission(ctx)) locateNow() else toast(ctx, tr("没有位置权限，可以改用“搜索城市”", "沒有位置權限，可以改用「搜尋城市」", "No location permission — search a city instead"))
    }
    fun locateWithPermission() {
        if (Weather.hasLocationPermission(ctx)) locateNow() else permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    Section(tr("天气", "天氣", "Weather")) {
        SwitchRow(tr("显示天气", "顯示天氣", "Show weather"), tr("锁屏、通知栏、小组件显示天气与建议", "鎖定畫面、通知欄、小工具顯示天氣與建議", "On the lock screen, notification and widgets"), enabled) {
            enabled = it
            prefs.weatherEnabled = it
            Weather.schedule(ctx)
            Widgets.updateAll(ctx)
            if (it) refreshNow()
        }
        if (!enabled) return@Section
        SwitchRow(tr("自动定位", "自動定位", "Auto location"), tr("亮屏时自动更新位置，显示当地天气", "亮屏時自動更新位置，顯示當地天氣", "Updates your location when the screen turns on"), autoLocate) {
            autoLocate = it
            prefs.autoLocate = it
            if (it) locateWithPermission()
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocationOn, null, tint = Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(city.ifBlank { tr("还没有位置", "還沒有位置", "No location yet") }, color = if (city.isBlank()) SubText else Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { locateWithPermission() }) { Text(tr("定位", "定位", "Locate"), color = Accent) }
            TextButton(onClick = { showSearch = true }) { Text(tr("搜索城市", "搜尋城市", "Search"), color = Accent) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp), color = Accent)
        weather?.let {
            Spacer(Modifier.height(8.dp))
            WeatherCard(it, lang) { refreshNow() }
        }
    }

    if (showSearch) {
        CitySearchDialog(lang, onDismiss = { showSearch = false }) { c ->
            showSearch = false
            autoLocate = false
            prefs.autoLocate = false
            Weather.setLocation(ctx, c.name, c.lat, c.lon, Weather.placeOf(c))
            city = c.name
            refreshNow()
        }
    }
}

@Composable
private fun WeatherCard(w: WeatherData, lang: Lang, onRefresh: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(FieldBg)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(w.cond.icon(w.isDay)), null, tint = Accent, modifier = Modifier.size(42.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(Weather.temp(w.temp), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Light)
                val feels = if (w.feelsLike.isNaN()) "" else " · " + tr("体感 ", "體感 ", "feels ", lang) + Weather.temp(w.feelsLike)
                Text(w.cond.label(lang) + feels, color = SubText, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (w.humidity >= 0) Text(tr("湿度", "濕度", "Humidity", lang) + " ${w.humidity}%", color = SubText, fontSize = 12.sp)
                if (!w.windKmh.isNaN()) Text(tr("风速", "風速", "Wind", lang) + " ${w.windKmh.roundToInt()} km/h", color = SubText, fontSize = 12.sp)
                w.aqi?.let { Text(tr("空气", "空氣", "AQI", lang) + " ${Weather.aqiLabel(it, lang)} $it", color = SubText, fontSize = 12.sp) }
            }
        }
        if (w.days.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            val today = LocalDate.now()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                w.days.take(4).forEach { d ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val label = RocCalendar.relativeDay(d.date, today, lang)
                        Text(
                            when (lang) {
                                Lang.EN -> label.take(3)
                                Lang.HANT -> label.replace("星期", "週")
                                Lang.HANS -> label.replace("星期", "周")
                            },
                            color = SubText,
                            fontSize = 12.sp,
                        )
                        Icon(painterResource(d.cond.icon()), d.cond.label(lang), tint = Color.White, modifier = Modifier.padding(vertical = 4.dp).size(22.dp))
                        Text(Weather.temp(d.min) + "/" + Weather.temp(d.max), color = Color.White, fontSize = 12.sp)
                        d.rainChance?.let { Text(tr("降水", "降雨", "Rain", lang) + " $it%", color = SubText, fontSize = 10.sp) }
                    }
                }
            }
        }
        val tips = Weather.advice(w, lang)
        if (tips.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(tr("生活建议", "生活建議", "Tips", lang), color = Accent, fontSize = 12.sp)
            tips.forEach { Text("· $it", color = Color.White, fontSize = 13.sp, lineHeight = 19.sp) }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val t = Instant.ofEpochMilli(w.fetchedAt).atZone(ZoneId.systemDefault()).toLocalTime()
            Text(
                tr("更新于", "更新於", "Updated", lang) + " ${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}",
                color = SubText,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRefresh) { Text(tr("刷新", "重新整理", "Refresh", lang), color = Accent) }
        }
    }
}

@Composable
private fun CitySearchDialog(lang: Lang, onDismiss: () -> Unit, onPick: (CityResult) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CityResult>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }

    fun search() {
        if (query.isBlank()) {
            status = tr("先输入城市名", "先輸入城市名稱", "Enter a city name", lang)
            return
        }
        status = tr("搜索中…", "搜尋中…", "Searching…", lang)
        scope.launch {
            status = try {
                results = Weather.searchCity(query, lang)
                if (results.isEmpty()) tr("没有找到，换个写法试试（如“臺北”或“Taipei”）", "找不到，換個寫法試試（如「台北」或「Taipei」）", "No results — try another spelling", lang) else null
            } catch (e: Exception) {
                tr("搜索失败，请检查网络", "搜尋失敗，請檢查網路", "Search failed", lang)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = { Text(tr("搜索城市", "搜尋城市", "Search city", lang), color = Color.White) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        status = null
                    },
                    singleLine = true,
                    placeholder = { Text(tr("福州", "臺北", "Taipei", lang)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    trailingIcon = { IconButton(onClick = { search() }) { Icon(Icons.Filled.Search, tr("搜索", "搜尋", "Search", lang)) } },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, cursorColor = Accent),
                    modifier = Modifier.fillMaxWidth(),
                )
                status?.let { Text(it, color = SubText, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
                Column(
                    Modifier
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    results.forEach { c ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onPick(c) }
                                .padding(horizontal = 4.dp, vertical = 10.dp),
                        ) {
                            Text(c.name, color = Color.White, fontSize = 15.sp)
                            if (c.region.isNotBlank()) Text(c.region, color = SubText, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("关闭", "關閉", "Close", lang), color = Accent) } },
    )
}

// =====================================================================
// 小组件
// =====================================================================

@Composable
private fun WidgetsPage() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var style by remember { mutableIntStateOf(prefs.widgetStyle) }
    // 进入本页时刷新一次桌面小组件，确保显示的是当前样式
    LaunchedEffect(Unit) { Widgets.updateAll(ctx) }

    Section(tr("民国纪年小组件", "民國紀年小工具", "ROC Calendar widget")) {
        Paragraph(
            tr(
                "一个小组件包含时间、民国日期、农历、天气和名言。点天气打开本应用的天气页，点名言换一句，点其它地方打开本应用。",
                "一個小工具包含時間、民國日期、農曆、天氣和名言。點天氣開啟本應用程式的天氣頁，點名言換一句，點其他地方開啟本應用程式。",
                "One widget with time, ROC date, lunar date, weather and a quote. Tap weather for the weather page, the quote for a new one, anywhere else to open the app.",
            ),
        )
        Spacer(Modifier.height(10.dp))
        Text(tr("样式", "樣式", "Style"), color = SubText, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        ChoiceRow(listOf(tr("白字", "白字", "Light"), tr("深色字", "深色字", "Dark")), style) {
            style = it
            prefs.widgetStyle = it
            Widgets.updateAll(ctx)
        }
        Spacer(Modifier.height(4.dp))
        Paragraph(
            tr(
                "背景始终透明、没有边框。白字适合深色壁纸，深色字适合浅色壁纸。\n大小：在桌面长按小组件拖动边框调整，宽度最多到屏幕宽，高度最小 100dp、最大 360dp，文字会跟着缩放。",
                "背景始終透明、沒有邊框。白字適合深色桌布，深色字適合淺色桌布。\n大小：在桌面長按小工具拖動邊框調整，寬度最多到螢幕寬，高度最小 100dp、最大 360dp，文字會跟著縮放。",
                "Always transparent, no frame. Light text for dark wallpapers, dark text for light ones.\nSize: long-press the widget and drag its edges; width up to the screen width, height 100–360dp, text scales.",
            ),
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                if (!Widgets.requestPin(ctx)) {
                    toast(ctx, tr("请长按桌面空白处 → 小组件 → 找到“民国纪年”", "請長按桌面空白處 → 小工具 → 找到「民國紀年」", "Long-press the home screen → Widgets → ROC Calendar"))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
        ) { Text(tr("添加到桌面", "加入桌面", "Add to home screen")) }
    }
}

// =====================================================================
// 权限
// =====================================================================

@SuppressLint("BatteryLife")
@Composable
private fun PermissionsPage(tick: Int) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var localTick by remember { mutableIntStateOf(0) }
    val t = tick + localTick
    val overlayOk = remember(t) { Settings.canDrawOverlays(ctx) }
    val notifyOk = remember(t) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
    val batteryOk = remember(t) { ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName) }
    val locationOk = remember(t) { Weather.hasPreciseLocation(ctx) }
    val bgLocationOk = remember(t) { Weather.hasBackgroundLocation(ctx) }
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { localTick++ }
    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { localTick++ }
    val preciseLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { localTick++ }

    Section(tr("必需", "必要", "Required")) {
        PermissionRow(tr("显示在其他应用上层", "顯示在其他應用程式上層", "Display over other apps"), tr("锁屏显示必需", "鎖定畫面顯示必需", "Required for the lock screen"), overlayOk) {
            ctx.safeStart(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
        }
        PermissionRow(tr("忽略电池优化", "忽略電池最佳化", "Ignore battery optimization"), tr("防止后台被杀", "防止背景被關閉", "Keeps the service alive"), batteryOk) {
            ctx.safeStart(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
        }
        PermissionRow(tr("通知", "通知", "Notifications"), tr("通知栏民国日期", "通知欄民國日期", "ROC date notification"), notifyOk) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else ctx.openAppDetails()
        }
    }
    if (prefs.weatherEnabled) {
        Section(tr("天气定位", "天氣定位", "Weather location")) {
            PermissionRow(tr("精确位置", "精確位置", "Precise location"), tr("GPS 准确定位天气", "GPS 準確定位天氣", "GPS for accurate weather"), locationOk) {
                preciseLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            if (locationOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                PermissionRow(
                    tr("后台位置", "背景位置", "Background location"),
                    tr("可选：选“始终允许”后熄屏时也能按当前位置更新天气；不开则用最近一次的位置", "可選：選「一律允許」後熄屏時也能依目前位置更新天氣；不開則用最近一次的位置", "Optional: lets weather follow you while the screen is off; otherwise the last location is used"),
                    bgLocationOk,
                ) { locLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
            }
        }
    }
    Section(Brand.name()) {
        Paragraph(tr("以下几项系统无法检测，请逐项点开确认已允许。", "以下幾項系統無法偵測，請逐項點開確認已允許。", "These can't be detected — open each and confirm.", ))
        Brand.shortcuts(ctx).forEach { sc ->
            PermissionRow(sc.title, sc.desc, null) {
                if (!Brand.open(ctx, sc)) ctx.openAppDetails()
            }
        }
        if (Brand.isHarmonyOS) {
            Spacer(Modifier.height(6.dp))
            Paragraph(
                tr(
                    "已识别为鸿蒙系统（兼容安卓应用的 HarmonyOS 2/3/4）。纯血鸿蒙 HarmonyOS NEXT / 5 及以上不能安装 APK。",
                    "已識別為鴻蒙系統（相容 Android 應用的 HarmonyOS 2/3/4）。純血鴻蒙 HarmonyOS NEXT / 5 以上不能安裝 APK。",
                    "HarmonyOS 2/3/4 detected (Android-compatible). HarmonyOS NEXT / 5+ can't install APKs.",
                ),
            )
        }
    }
    RunStatusSection(t)
    Section(tr("关于进程守护", "關於程序守護", "About keep-alive")) {
        Paragraph(
            tr(
                "本应用不会出现在最近任务里。崩溃或被系统回收后会自动重启；但在“设置/手机管家”里强行停止后，Android 会冻结所有自动恢复，需要重新打开 App 或重启手机。",
                "本應用不會出現在最近任務裡。當機或被系統回收後會自動重啟；但在「設定/手機管家」裡強制停止後，Android 會凍結所有自動恢復，需要重新開啟 App 或重新開機。",
                "The app is hidden from Recents and restarts itself after crashes. A Force stop freezes it until you open the app again or reboot.",
            ),
        )
    }
}

/**
 * 运行状态：用来确认名言、必应壁纸、天气、定位有没有自动更新，以及闹钟为什么显示 / 不显示。
 * 每次回到这个页面都会刷新。
 */
@Composable
private fun RunStatusSection(tick: Int) {
    val ctx = LocalContext.current
    val p = remember(tick) { Prefs(ctx) }
    val weather by Weather.state.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    val now = System.currentTimeMillis()
    fun fmt(ms: Long): String {
        if (ms <= 0L) return tr("还没有", "還沒有", "Never")
        val t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDateTime()
        val mins = (now - ms) / 60_000
        val ago = when {
            mins < 1 -> tr("刚刚", "剛剛", "just now")
            mins < 60 -> tr("$mins 分钟前", "$mins 分鐘前", "$mins min ago")
            mins < 1440 -> tr("${mins / 60} 小时前", "${mins / 60} 小時前", "${mins / 60} h ago")
            else -> tr("${mins / 1440} 天前", "${mins / 1440} 天前", "${mins / 1440} d ago")
        }
        return "%02d-%02d %02d:%02d（%s）".format(t.monthValue, t.dayOfMonth, t.hour, t.minute, ago)
    }
    val lines = remember(tick, refresh, weather) {
        buildString {
            append(tr("名言上次更换：", "名言上次更換：", "Quote changed: ")).append(fmt(p.quoteChangedAt)).append('\n')
            append(tr("　规则：每次熄屏换一句；亮屏期间每 30 分钟换一句", "　規則：每次熄屏換一句；亮屏期間每 30 分鐘換一句", "  Rule: new quote on every screen-off, and every 30 min while on")).append("\n\n")
            if (p.wallpaperMode == Wallpaper.MODE_BING) {
                val total = runCatching { org.json.JSONArray(p.bingList).length() }.getOrDefault(0)
                append(tr("必应壁纸上次更换：", "Bing 桌布上次更換：", "Bing wallpaper changed: ")).append(fmt(p.bingChangedAt)).append('\n')
                append(tr("当前第 ", "目前第 ", "Showing ")).append(p.bingIndex + 1).append(" / ").append(total)
                    .append(tr(" 张（最近十几天的必应壁纸轮换）", " 張（最近十幾天的 Bing 桌布輪換）", " (recent Bing wallpapers)")).append('\n')
                append(tr("　规则：每次熄屏换一张；亮屏期间每 30 分钟换一张", "　規則：每次熄屏換一張；亮屏期間每 30 分鐘換一張", "  Rule: new wallpaper on every screen-off, and every 30 min while on")).append("\n\n")
            } else {
                append(tr("必应壁纸：未使用（壁纸页选“必应每日”后生效）", "Bing 桌布：未使用（桌布頁選「Bing 每日」後生效）", "Bing wallpaper: not in use")).append("\n\n")
            }
            append(tr("天气上次更新：", "天氣上次更新：", "Weather updated: ")).append(fmt(weather?.fetchedAt ?: 0L)).append('\n')
            append(tr("上次定位：", "上次定位：", "Last located: ")).append(fmt(p.locatedAt)).append('\n')
            val src = p.locSource
            if (src.isNotBlank()) {
                val name = when {
                    src.startsWith("passive") -> tr("被动定位（其它应用的位置）", "被動定位（其他應用程式的位置）", "passive (from other apps)")
                    src == "gps" -> "GPS"
                    src == "network" -> tr("网络定位（Wi-Fi/基站）", "網路定位（Wi-Fi/基地台）", "network (Wi-Fi/cell)")
                    src == "fused" -> tr("融合定位", "融合定位", "fused")
                    else -> src
                }
                append(tr("　方式：", "　方式：", "  Source: ")).append(name)
                if (p.locAccuracy >= 0) append(tr("，精度约 ", "，精度約 ", ", about ")).append(p.locAccuracy).append(tr(" 米", " 公尺", " m"))
                append('\n')
            }
            append(tr("锁屏时可定位：", "鎖定時可定位：", "Can locate when locked: "))
                .append(if (LockService.locationActive || Weather.hasBackgroundLocation(ctx)) tr("是", "是", "Yes") else tr("否（打开一次 App 后生效）", "否（開啟一次 App 後生效）", "No (open the app once)"))
                .append("\n\n")
            append(tr("小组件样式：", "小工具樣式：", "Widget style: ")).append(
                when (p.widgetStyle) {
                    1 -> tr("深色字（透明）", "深色字（透明）", "Dark (transparent)")
                    else -> tr("白字（透明）", "白字（透明）", "Light (transparent)")
                },
            ).append('\n')
            val placed = Widgets.placedCount(ctx)
            append(tr("本应用在桌面上的小组件：", "本應用程式在桌面上的小工具：", "Widgets placed by this app: ")).append(placed).append('\n')
            append(tr("小组件上次更新：", "小工具上次更新：", "Widget updated: ")).append(fmt(Widgets.lastUpdate)).append('\n')
            Widgets.lastError?.let { append(tr("小组件更新出错：", "小工具更新出錯：", "Widget error: ")).append(it).append('\n') }
            append(tr("本应用：", "本應用程式：", "This app: ")).append(ctx.packageName).append(" v").append(Maintenance.versionName(ctx)).append("\n\n")
            append(Alarms.diagnose(ctx))
        }
    }
    Section(tr("运行状态", "執行狀態", "Status")) {
        Text(lines, color = Color.White, fontSize = 13.sp, lineHeight = 20.sp)
        TextButton(onClick = { refresh++ }) { Text(tr("刷新", "重新整理", "Refresh"), color = Accent) }
    }
}

// =====================================================================
// 关于
// =====================================================================

@Composable
private fun AboutPage() {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // 名称和图标跟随“长按图标 → 更换图标 / 修改应用名称”的设置
        val icon = remember { IconSwitcher.displayIcon(ctx).asImageBitmap() }
        Image(icon, null, modifier = Modifier.size(84.dp).clip(RoundedCornerShape(22.dp)))
        Spacer(Modifier.height(10.dp))
        Text(IconSwitcher.displayName(ctx), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text("v" + Maintenance.versionName(ctx), color = SubText, fontSize = 13.sp)
    }
    Section(tr("作者", "作者", "Author")) {
        Text(AppConfig.AUTHOR, color = Color.White, fontSize = 15.sp)
        TextButton(onClick = { Maintenance.openUrl(ctx, AppConfig.BLOG) }) { Text(AppConfig.BLOG, color = Accent) }
    }
    Section(tr("版权", "版權", "Copyright")) {
        Paragraph("© 2026 ${AppConfig.AUTHOR}. All rights reserved.")
    }
}

// =====================================================================
// 通用组件
// =====================================================================

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = SubText, fontSize = 13.sp)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Paragraph(text: String) {
    Text(text, color = SubText, fontSize = 12.sp, lineHeight = 18.sp)
}

@Composable
private fun StatusLine(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning, null, tint = if (ok) Good else Warn, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = if (ok) Good else Warn, fontSize = 13.sp)
    }
}

@Composable
private fun MenuRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = SubText, fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SubText)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = SubText, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = OnAccent),
        )
    }
}

/** 胶囊式单选 */
@Composable
private fun ChoiceRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FieldBg)
            .padding(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val bg by animateColorAsState(if (i == selected) Accent else Color.Transparent, label = "choice")
            val fg by animateColorAsState(if (i == selected) OnAccent else SubText, label = "choiceText")
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 2.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = fg, fontSize = 12.sp, maxLines = 1, fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun ThemeSwatch(theme: ClockTheme, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(theme.colors))
                .border(if (selected) BorderStroke(3.dp, Accent) else BorderStroke(1.dp, Color(0x33FFFFFF)), CircleShape)
                .clickable(onClick = onClick),
        )
        Spacer(Modifier.height(6.dp))
        Text(theme.name, color = if (selected) Accent else SubText, fontSize = 12.sp)
    }
}

/** ok = null 表示无法检测 */
@Composable
private fun PermissionRow(title: String, desc: String, ok: Boolean?, onFix: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (ok == true) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = when (ok) {
                true -> Good
                false -> Warn
                null -> SubText
            },
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            Text(desc, color = SubText, fontSize = 12.sp)
        }
        when (ok) {
            true -> Text(tr("已开启", "已開啟", "On"), color = Good, fontSize = 13.sp)
            false -> OutlinedButton(
                onClick = onFix,
                border = BorderStroke(1.dp, Accent),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent),
            ) { Text(tr("去开启", "去開啟", "Grant")) }
            null -> TextButton(onClick = onFix) { Text(tr("去设置", "去設定", "Open"), color = Accent) }
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, danger: Boolean = false, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = { Text(title, color = Color.White) },
        text = { Text(text, color = SubText) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(tr("确定", "確定", "OK"), color = if (danger) Danger else Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("取消", "取消", "Cancel"), color = SubText) } },
    )
}

private fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

private fun Context.safeStart(intent: Intent, showError: Boolean = true): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    if (showError) openAppDetails()
    false
} catch (e: SecurityException) {
    if (showError) openAppDetails()
    false
}

private fun Context.openAppDetails() {
    try {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (ignored: Exception) {
    }
}
