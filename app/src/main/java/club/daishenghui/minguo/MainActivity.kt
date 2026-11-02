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

private enum class Page { HOME, SETTINGS, LOCK_MODE, APPEARANCE, DATE_LANG, WEATHER, QUOTES, WIDGETS, PERMISSIONS, ABOUT }

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
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(primary = Accent, onPrimary = OnAccent, background = PageBg, surface = CardBg),
            ) {
                Surface(Modifier.fillMaxSize(), color = PageBg) { App() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        L.refresh(this)
    }
}

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
                Page.QUOTES -> QuotesPage()
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
        Page.HOME -> tr("民国时钟", "民國時鐘", "Minguo Clock")
        Page.SETTINGS -> tr("设置", "設定", "Settings")
        Page.LOCK_MODE -> tr("锁屏模式与密码", "鎖定畫面模式與密碼", "Lock mode and PIN")
        Page.APPEARANCE -> tr("壁纸与字体", "桌布與字型", "Wallpaper and fonts")
        Page.DATE_LANG -> tr("日期与语言", "日期與語言", "Date and language")
        Page.WEATHER -> tr("天气", "天氣", "Weather")
        Page.QUOTES -> tr("名言", "名言", "Quotes")
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
            if (q.author.isNotBlank()) Text("—— ${q.author}", color = SubText, fontSize = 12.sp)
        }
        Row {
            TextButton(onClick = { scope.launch { Quotes.next(ctx) } }) { Text(tr("换一句", "換一句", "Next"), color = Accent) }
            TextButton(onClick = { go(Page.QUOTES) }) { Text(tr("名言设置", "名言設定", "Quote settings"), color = Accent) }
        }
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
        MenuRow(tr("名言", "名言", "Quotes"), null) { go(Page.QUOTES) }
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
    Section(tr("网络线路", "網路線路", "Network route")) {
        var override by remember { mutableIntStateOf(prefs.regionOverride) }
        var regionText by remember { mutableStateOf(Region.describe()) }
        LaunchedEffect(override) {
            Region.invalidate()
            Region.isMainland(ctx)
            regionText = Region.describe()
        }
        ChoiceRow(listOf(tr("自动判断", "自動判斷", "Auto"), tr("中国大陆", "中國大陸", "Mainland"), tr("境外", "境外", "Overseas")), override) {
            override = it
            prefs.regionOverride = it
        }
        Spacer(Modifier.height(6.dp))
        Paragraph(
            tr("当前：", "目前：", "Now: ") + regionText + "\n" +
                tr(
                    "自动判断顺序：出口 IP → 定位 → SIM 卡 / 时区。大陆走 cn.bing.com、v1.hitokoto.cn；境外走 www.bing.com、international.v1.hitokoto.cn。",
                    "自動判斷順序：出口 IP → 定位 → SIM 卡 / 時區。大陸走 cn.bing.com、v1.hitokoto.cn；境外走 www.bing.com、international.v1.hitokoto.cn。",
                    "Auto order: exit IP → location → SIM / time zone. Mainland uses cn.bing.com and v1.hitokoto.cn; overseas uses www.bing.com and international.v1.hitokoto.cn.",
                ),
        )
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
                    "系统锁屏改为「无」后，民国锁屏就是唯一的锁屏：亮屏、上滑解锁全程看不到系统时间；按 Home、返回都关不掉。\n代价：指纹/人脸需要系统锁屏密码，关掉后无法使用；锁屏时仍可下拉通知栏和控制中心。建议在下方设置锁屏密码。",
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
        Section(tr("锁屏密码", "鎖定畫面密碼", "PIN")) {
            Text(
                if (hasPin) tr("已设置（${prefs.pinLength} 位数字）", "已設定（${prefs.pinLength} 位數字）", "Set (${prefs.pinLength} digits)") else tr("未设置：上滑直接解锁", "未設定：上滑直接解鎖", "Not set: swipe unlocks directly"),
                color = if (hasPin) Good else SubText,
                fontSize = 14.sp,
            )
            Row {
                TextButton(onClick = { setPin = true }) { Text(if (hasPin) tr("修改密码", "修改密碼", "Change PIN") else tr("设置密码", "設定密碼", "Set PIN"), color = Accent) }
                if (hasPin) {
                    TextButton(onClick = {
                        Security.clearPin(ctx)
                        hasPin = false
                    }) { Text(tr("清除密码", "清除密碼", "Remove PIN"), color = Danger) }
                }
            }
            Paragraph(tr("设置密码后，锁屏右下角的相机入口会隐藏（防止借相机进入相册）。", "設定密碼後，鎖定畫面右下角的相機入口會隱藏（防止藉相機進入相簿）。", "With a PIN set, the camera shortcut is hidden."))
        }
    }

    if (setPin) {
        PinSetupDialog(onDismiss = { setPin = false }) { pin ->
            Security.setPin(ctx, pin)
            hasPin = true
            setPin = false
            toast(ctx, tr("密码已设置", "密碼已設定", "PIN set"))
        }
    }
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
    var showBingInfo by remember { mutableStateOf(prefs.showBingInfo) }
    var bingCopyright by remember { mutableStateOf(prefs.bingCopyright) }
    var busy by remember { mutableStateOf(false) }
    val fontsVersion by Fonts.version.collectAsState()
    val fonts = remember(fontsVersion) { Fonts.list(ctx).filter { it.id.startsWith("file:") } }

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
            bingCopyright = prefs.bingCopyright
            if (!ok) toast(ctx, tr("必应壁纸下载失败，请检查网络", "Bing 桌布下載失敗，請檢查網路", "Couldn't download the Bing wallpaper"))
        }
    }

    Section(tr("锁屏壁纸", "鎖定畫面桌布", "Wallpaper")) {
        ChoiceRow(listOf(tr("主题渐变", "主題漸層", "Gradient"), tr("自选图片", "自選圖片", "My photo"), tr("必应每日", "Bing 每日", "Bing daily")), wallMode) { m ->
            wallMode = m
            prefs.wallpaperMode = m
            Wallpaper.schedule(ctx)
            if (m == Wallpaper.MODE_CUSTOM && Wallpaper.fileFor(ctx, m) == null) {
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
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
            else -> {
                if (bingCopyright.isNotBlank()) Paragraph(bingCopyright)
                val (sw, sh) = remember { Wallpaper.screenSize(ctx) }
                Paragraph(
                    tr("按屏幕 ${sw}×${sh} 自动选择竖版或超清版本，每天更新", "依螢幕 ${sw}×${sh} 自動選擇直式或超清版本，每天更新", "Picks the best size for your ${sw}×${sh} screen, daily"),
                )
                TextButton(onClick = { refreshBing(true) }) { Text(tr("立即更新", "立即更新", "Update now"), color = Accent) }
                SwitchRow(tr("锁屏显示图片说明", "鎖定畫面顯示圖片說明", "Show photo caption"), null, showBingInfo) {
                    showBingInfo = it
                    prefs.showBingInfo = it
                }
            }
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
    var useSystem by remember { mutableStateOf(prefs.useSystemWeather) }
    var city by remember { mutableStateOf(prefs.cityName) }
    var busy by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var systemOk by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(useSystem) {
        systemOk = if (useSystem) withContext(Dispatchers.IO) { Weather.systemWeatherAvailable(ctx) } else null
    }

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

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) locateNow() else toast(ctx, tr("没有位置权限，可以改用“搜索城市”", "沒有位置權限，可以改用「搜尋城市」", "No location permission — search a city instead"))
    }
    fun locateWithPermission() {
        if (Weather.hasLocationPermission(ctx)) locateNow() else permLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
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

    if (enabled) {
        Section(tr("数据来源", "資料來源", "Data sources")) {
            Paragraph(
                tr(
                    "全部免注册、免申请：\n① 小米手机：当前温度和天气现象直接读小米天气，和手机上看到的一致；\n② 位置在中国大陆：腾讯天气（中央气象台数据）提供 7 天预报、逐小时降水、生活指数、预警和空气质量；\n③ 境外或腾讯不可用：Open-Meteo（含未来 2 小时降水预报）。\n省市区由 BigDataCloud 免费反查。",
                    "全部免註冊、免申請：\n① 小米手機：目前溫度和天氣現象直接讀小米天氣，和手機上看到的一致；\n② 位置在中國大陸：騰訊天氣（中央氣象台資料）提供 7 天預報、逐小時降雨、生活指數、預警和空氣品質；\n③ 境外或騰訊不可用：Open-Meteo（含未來 2 小時降雨預報）。\n省市區由 BigDataCloud 免費反查。",
                    "No sign-up needed: ① Xiaomi phones read Xiaomi Weather for current conditions; ② in mainland China, Tencent Weather (China Meteorological data) provides forecast, hourly rain, life index, alerts and AQI; ③ elsewhere, Open-Meteo. Place names via BigDataCloud.",
                ),
            )
            if (isXiaomi) {
                Spacer(Modifier.height(8.dp))
                SwitchRow(
                    tr("读取小米天气", "讀取小米天氣", "Read Xiaomi Weather"),
                    when (systemOk) {
                        true -> tr("已读取到小米天气 ✓", "已讀取到小米天氣 ✓", "Xiaomi Weather available ✓")
                        false -> tr("读取不到（系统可能不开放给第三方）", "讀取不到（系統可能不開放給第三方）", "Not available on this system")
                        null -> tr("关闭", "關閉", "Off")
                    },
                    useSystem,
                ) {
                    useSystem = it
                    prefs.useSystemWeather = it
                    refreshNow()
                }
            }
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
            val src = w.sources.joinToString(" · ") { Weather.sourceLabel(it, lang) }
            Text(
                tr("更新于", "更新於", "Updated", lang) + " ${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}" + (if (src.isNotEmpty()) " · $src" else ""),
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
// 名言
// =====================================================================

@Composable
private fun QuotesPage() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()
    val lang = L.current
    val quote by Quotes.state.collectAsState()
    var enabled by remember { mutableStateOf(prefs.quoteEnabled) }
    var source by remember { mutableIntStateOf(prefs.quoteSource) }
    var interval by remember { mutableIntStateOf(prefs.quoteInterval) }
    val intervals = listOf(0, 30, 60, 180, 1440)

    Section(tr("名言", "名言", "Quotes")) {
        SwitchRow(tr("锁屏显示名言", "鎖定畫面顯示名言", "Show quotes"), null, enabled) {
            enabled = it
            prefs.quoteEnabled = it
            Widgets.updateAll(ctx)
        }
        quote?.let { Quotes.display(it, lang) }?.let { q ->
            Spacer(Modifier.height(6.dp))
            Text("「${q.text}」", color = Color.White, fontSize = 15.sp, lineHeight = 22.sp)
            if (q.author.isNotBlank()) Text("—— ${q.author}", color = SubText, fontSize = 12.sp)
            TextButton(onClick = { scope.launch { Quotes.next(ctx) } }) { Text(tr("换一句", "換一句", "Next"), color = Accent) }
        }
    }
    Section(tr("来源", "來源", "Source")) {
        ChoiceRow(listOf(tr("内置", "內建", "Built-in"), tr("一言(在线)", "一言(線上)", "Hitokoto"), tr("混合", "混合", "Mixed")), source) {
            source = it
            prefs.quoteSource = it
        }
        Spacer(Modifier.height(6.dp))
        Paragraph(
            tr(
                "内置 ${Quotes.builtinCount} 句经典名言，全部轮完一遍之前不会重复；一言来自 hitokoto.cn，会记住最近 300 句避免重复。",
                "內建 ${Quotes.builtinCount} 句經典名言，全部輪完一遍之前不會重複；一言來自 hitokoto.cn，會記住最近 300 句避免重複。",
                "${Quotes.builtinCount} built-in quotes never repeat until all are shown; Hitokoto (hitokoto.cn) avoids the last 300.",
            ),
        )
    }
    Section(tr("更新频率", "更新頻率", "Frequency")) {
        ChoiceRow(
            listOf(tr("每次亮屏", "每次亮屏", "Each wake"), tr("30分钟", "30分鐘", "30 min"), tr("1小时", "1小時", "1 h"), tr("3小时", "3小時", "3 h"), tr("每天", "每天", "Daily")),
            intervals.indexOf(interval).coerceAtLeast(0),
        ) {
            interval = intervals[it]
            prefs.quoteInterval = interval
        }
    }
}

// =====================================================================
// 小组件
// =====================================================================

@Composable
private fun WidgetsPage() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var style by remember { mutableIntStateOf(prefs.widgetStyle) }

    Section(tr("样式", "樣式", "Style")) {
        ChoiceRow(listOf(tr("白字", "白字", "Light"), tr("深色字", "深色字", "Dark"), tr("卡片", "卡片", "Card")), style) {
            style = it
            prefs.widgetStyle = it
            Widgets.updateAll(ctx)
        }
    }
    Section(tr("添加到桌面", "加入桌面", "Add to home screen")) {
        Widgets.kinds.forEach { k ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(k.label(), color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    if (!Widgets.requestPin(ctx, k.cls)) {
                        toast(ctx, tr("请长按桌面空白处 → 小组件 → 找到“民国”", "請長按桌面空白處 → 小工具 → 找到「民國」", "Long-press the home screen → Widgets"))
                    }
                }) { Text(tr("添加", "加入", "Add"), color = Accent) }
            }
        }
        Paragraph(tr("名言小组件点一下换一句；天气小组件点开小米天气；日历方块点开日历。", "名言小工具點一下換一句；天氣小工具點開小米天氣；日曆方塊點開日曆。", "Tap the quote widget for a new quote; weather opens Xiaomi Weather; calendar opens your calendar."))
    }
}

// =====================================================================
// 权限
// =====================================================================

private val isXiaomi: Boolean get() = Brand.isXiaomi

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
    val locationOk = remember(t) { Weather.hasLocationPermission(ctx) }
    val bgLocationOk = remember(t) { Weather.hasBackgroundLocation(ctx) }
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { localTick++ }
    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { localTick++ }

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
            PermissionRow(tr("位置", "位置", "Location"), tr("自动定位天气", "自動定位天氣", "Local weather"), locationOk) {
                locLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (locationOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                PermissionRow(
                    tr("后台位置", "背景位置", "Background location"),
                    tr("选“始终允许”：完全替代模式和后台更新都需要", "選「一律允許」：完全取代模式和背景更新都需要", "Choose \"Allow all the time\" — needed in Replace mode"),
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

// =====================================================================
// 关于
// =====================================================================

@Composable
private fun AboutPage() {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF1B2A38)),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, modifier = Modifier.size(84.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(tr("民国时钟", "民國時鐘", "Minguo Clock"), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text("v" + Maintenance.versionName(ctx), color = SubText, fontSize = 13.sp)
    }
    Section(tr("本机", "本機", "This device")) {
        val region by produceState(Region.describe(), Unit) { Region.isMainland(ctx); value = Region.describe() }
        Paragraph(Brand.name() + "\n" + tr("网络线路：", "網路線路：", "Network: ") + region)
    }
    Section(tr("作者", "作者", "Author")) {
        Text(AppConfig.AUTHOR, color = Color.White, fontSize = 15.sp)
        TextButton(onClick = { Maintenance.openUrl(ctx, AppConfig.BLOG) }) { Text(AppConfig.BLOG, color = Accent) }
    }
    Section(tr("数据来源", "資料來源", "Data")) {
        Paragraph(
            tr(
                "天气：小米天气、腾讯天气、Open-Meteo（地名反查：BigDataCloud）\n城市搜索：Open-Meteo Geocoding\n壁纸：必应每日图片（按 IP 自动选国内 / 国际节点）\n名言：内置经典名言、一言 hitokoto.cn（按 IP 自动选国内 / 海外线路）\n农历：Android 系统 ICU 历法",
                "天氣：小米天氣、騰訊天氣、Open-Meteo（地名反查：BigDataCloud）\n城市搜尋：Open-Meteo Geocoding\n桌布：Bing 每日圖片（依 IP 自動選國內 / 國際節點）\n名言：內建經典名言、一言 hitokoto.cn（依 IP 自動選國內 / 海外線路）\n農曆：Android 系統 ICU 曆法",
                "Weather: Xiaomi Weather, Tencent Weather, Open-Meteo (places: BigDataCloud)\nCity search: Open-Meteo Geocoding\nWallpaper: Bing (China / global endpoint by IP)\nQuotes: built-in, hitokoto.cn (China / overseas line by IP)\nLunar calendar: Android ICU",
            ),
        )
    }
    Section(tr("版权", "版權", "Copyright")) {
        Paragraph("© 2026 ${AppConfig.AUTHOR}. All rights reserved.")
        Row {
            TextButton(onClick = { Maintenance.feedback(ctx) }) { Text(tr("反馈", "意見回饋", "Feedback"), color = Accent) }
        }
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
