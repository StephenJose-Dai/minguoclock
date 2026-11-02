package club.daishenghui.minguo

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private val Accent = Color(0xFFE8C77A)
private val CardBg = Color(0xFF171C24)
private val SubText = Color(0xFF8E98A8)
private val Good = Color(0xFF5BD68A)
private val Warn = Color(0xFFFFB454)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        LockService.sync(this)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent,
                    onPrimary = Color(0xFF2A1F05),
                    background = Color(0xFF0E1116),
                    surface = CardBg,
                ),
            ) {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF0E1116)) {
                    SettingsScreen()
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }

    var lockEnabled by remember { mutableStateOf(prefs.lockEnabled) }
    var themeIndex by remember { mutableIntStateOf(prefs.themeIndex) }
    var traditional by remember { mutableStateOf(prefs.traditional) }
    var cnNumerals by remember { mutableStateOf(prefs.chineseNumerals) }
    var showSeconds by remember { mutableStateOf(prefs.showSeconds) }
    var widgetStyle by remember { mutableIntStateOf(prefs.widgetStyle) }

    // 每次回到前台重新检查权限状态
    var resumeTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumeTick++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun refreshWidgets() = ClockWidget.updateAll(ctx)

    val style = DisplayStyle(traditional, cnNumerals)
    val theme = ClockThemes[themeIndex]
    val now by rememberNow()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 标题
        Column(Modifier.padding(start = 4.dp, top = 8.dp)) {
            Text("民国时钟", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(
                RocCalendar.yearText(now.year, style) + " · " + RocCalendar.lunarLine(System.currentTimeMillis(), style).substringBefore(' '),
                fontSize = 14.sp,
                color = SubText,
            )
        }

        // 实时预览
        Box(
            Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(RoundedCornerShape(28.dp)),
        ) {
            GradientBackground(theme, Modifier.fillMaxSize()) {
                ClockFace(now, style, showSeconds, theme, Modifier.align(Alignment.Center), scale = 0.62f)
            }
        }

        // 锁屏
        Section("锁屏") {
            SwitchRow(
                title = "启用民国锁屏",
                subtitle = "熄屏后亮起时显示民国锁屏，上滑后进入系统解锁（密码/指纹照常生效）",
                checked = lockEnabled,
            ) {
                lockEnabled = it
                prefs.lockEnabled = it
                LockService.sync(ctx)
                if (it && !Settings.canDrawOverlays(ctx)) {
                    Toast.makeText(ctx, "还需要开启下方的权限，锁屏才能弹出", Toast.LENGTH_LONG).show()
                }
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = {
                    ctx.startActivity(
                        Intent(ctx, LockActivity::class.java)
                            .putExtra(LockActivity.EXTRA_PREVIEW, true)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("预览锁屏效果") }
        }

        // 外观
        Section("外观") {
            Text("主题", color = SubText, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                itemsIndexed(ClockThemes) { i, t ->
                    ThemeSwatch(t, selected = i == themeIndex) {
                        themeIndex = i
                        prefs.themeIndex = i
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("文字", color = SubText, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            ChoiceRow(listOf("繁體 民國", "简体 民国"), if (traditional) 0 else 1) {
                traditional = it == 0
                prefs.traditional = traditional
                refreshWidgets()
            }
            Spacer(Modifier.height(10.dp))
            Text("数字", color = SubText, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            ChoiceRow(listOf("115年11月2日", "一百一十五年"), if (cnNumerals) 1 else 0) {
                cnNumerals = it == 1
                prefs.chineseNumerals = cnNumerals
                refreshWidgets()
            }
            Spacer(Modifier.height(6.dp))
            SwitchRow("显示秒", null, showSeconds) {
                showSeconds = it
                prefs.showSeconds = it
                refreshWidgets()
            }
        }

        // 小组件
        Section("桌面小组件") {
            Text("样式", color = SubText, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            ChoiceRow(listOf("白字", "深色字", "卡片"), widgetStyle) {
                widgetStyle = it
                prefs.widgetStyle = it
                refreshWidgets()
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (!ClockWidget.requestPin(ctx)) {
                        Toast.makeText(ctx, "请长按桌面空白处 → 小组件 → 找到“民国时钟”", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("添加到桌面") }
        }

        // 权限
        Section("权限检查") {
            PermissionList(resumeTick) { resumeTick++ }
        }

        Text(
            "民国时钟 v1.0 · StephenJose_Dai",
            color = SubText.copy(alpha = 0.6f),
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
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
            colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color(0xFF2A1F05)),
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
            .background(Color(0xFF0F1319))
            .padding(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val bg by animateColorAsState(if (i == selected) Accent else Color.Transparent, label = "choice")
            val fg by animateColorAsState(if (i == selected) Color(0xFF2A1F05) else SubText, label = "choiceText")
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = fg, fontSize = 14.sp, fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal)
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
                .border(
                    if (selected) BorderStroke(3.dp, Accent) else BorderStroke(1.dp, Color(0x33FFFFFF)),
                    CircleShape,
                )
                .clickable(onClick = onClick),
        )
        Spacer(Modifier.height(6.dp))
        Text(theme.name, color = if (selected) Accent else SubText, fontSize = 12.sp)
    }
}

// ---------------- 权限 ----------------

private val isXiaomi: Boolean
    get() = listOf(Build.MANUFACTURER, Build.BRAND).any {
        it.equals("Xiaomi", true) || it.equals("Redmi", true) || it.equals("POCO", true)
    }

@SuppressLint("BatteryLife")
@Composable
private fun PermissionList(tick: Int, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val overlayOk = remember(tick) { Settings.canDrawOverlays(ctx) }
    val notifyOk = remember(tick) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
    val batteryOk = remember(tick) {
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    }
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onChanged() }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PermissionRow("显示在其他应用上层", "锁屏弹出必需", overlayOk) {
            ctx.safeStart(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
        }
        PermissionRow("忽略电池优化", "防止后台服务被系统杀掉", batteryOk) {
            ctx.safeStart(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")),
            )
        }
        PermissionRow("通知", "前台服务通知（可在系统里设为静默）", notifyOk) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                ctx.openAppDetails()
            }
        }

        if (isXiaomi) {
            Spacer(Modifier.height(10.dp))
            Text("小米 / HyperOS 专项（系统无法检测，请手动确认已开启）", color = Warn, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            PermissionRow("后台弹出界面 + 锁屏显示", "在“其他权限”里把这两项都设为允许", null) {
                val miui = Intent("miui.intent.action.APP_PERM_EDITOR")
                    .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                    .putExtra("extra_pkgname", ctx.packageName)
                if (!ctx.safeStart(miui, showError = false)) ctx.openAppDetails()
            }
            PermissionRow("自启动", "开机后自动恢复锁屏", null) {
                val auto = Intent().setComponent(
                    ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                )
                if (!ctx.safeStart(auto, showError = false)) ctx.openAppDetails()
            }
            Text(
                "另外建议：最近任务里下拉本应用卡片加锁；省电策略设为“无限制”。",
                color = SubText,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
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
        if (ok == true) {
            Text("已开启", color = Good, fontSize = 13.sp)
        } else if (ok == false) {
            OutlinedButton(
                onClick = onFix,
                border = BorderStroke(1.dp, Accent),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent),
            ) { Text("去开启") }
        } else {
            TextButton(onClick = onFix) { Text("去设置", color = Accent) }
        }
    }
}

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
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: Exception) {
    }
}
