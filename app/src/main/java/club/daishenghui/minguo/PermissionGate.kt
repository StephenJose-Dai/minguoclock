package club.daishenghui.minguo

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

/**
 * 启动时的权限检查：缺什么要什么，用户拒绝任何一项就退出 App；
 * 下次打开再检查，仍然缺的继续要。
 *
 * 普通权限（通知、位置、后台位置）用系统弹窗；
 * 特殊权限（悬浮窗、忽略电池优化）要跳到系统设置，回来时仍未开启就算拒绝。
 * 被系统标记为“不再询问”的普通权限，也改为跳到应用设置里开启。
 */
object Perms {
    /**
     * 必需权限。位置只要授权了（「仅在使用中允许」即可，精确或大致都算）就通过，不会退出；
     * 锁屏服务以定位类型运行，锁屏、熄屏时也能定位。
     * 精确位置（GPS）是加分项：只给了大致位置时，每次启动会额外提示一次开启精确位置，拒绝也不退出。
     * 「始终允许」是可选项。
     */
    enum class P { NOTIFY, LOCATION, OVERLAY, BATTERY }

    private fun granted(c: Context, perm: String) =
        ContextCompat.checkSelfPermission(c, perm) == PackageManager.PERMISSION_GRANTED

    fun has(c: Context, p: P): Boolean = when (p) {
        P.NOTIFY -> if (Build.VERSION.SDK_INT >= 33) {
            granted(c, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            NotificationManagerCompat.from(c).areNotificationsEnabled()
        }
        // 精确或大致都算已授权（HyperOS 的弹窗里如果关了「精确位置」，系统只给大致位置）
        P.LOCATION -> granted(c, Manifest.permission.ACCESS_FINE_LOCATION) || granted(c, Manifest.permission.ACCESS_COARSE_LOCATION)
        P.OVERLAY -> Settings.canDrawOverlays(c)
        P.BATTERY -> c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)
    }

    /** 按顺序列出缺少的权限 */
    fun missing(c: Context): List<P> = P.entries.filter { !has(c, it) }

    /** 普通权限对应的系统权限名；null = 只能去设置里开 */
    fun runtimeName(p: P): String? = when (p) {
        P.NOTIFY -> if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null
        P.LOCATION -> Manifest.permission.ACCESS_FINE_LOCATION
        else -> null
    }

    /** 一次弹窗里要申请的权限：精确位置必须和大致位置一起申请（Android 12 起） */
    fun requestSet(p: P): Array<String> = when (p) {
        P.LOCATION -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        else -> listOfNotNull(runtimeName(p)).toTypedArray()
    }

    fun title(p: P) = when (p) {
        P.NOTIFY -> tr("通知", "通知", "Notifications")
        P.LOCATION -> tr("位置", "位置", "Location")
        P.OVERLAY -> tr("显示在其他应用上层（悬浮窗）", "顯示在其他應用程式上層", "Display over other apps")
        P.BATTERY -> tr("忽略电池优化", "忽略電池最佳化", "Ignore battery optimization")
    }

    fun reason(p: P) = when (p) {
        P.NOTIFY -> tr("通知栏显示民国日期、农历和天气，并让锁屏服务常驻。", "通知欄顯示民國日期、農曆和天氣，並讓鎖定畫面服務常駐。", "Shows the ROC date and weather in notifications and keeps the lock screen running.")
        P.LOCATION -> tr("定位当地天气。「仅在使用中允许」即可；建议打开「精确位置」，才能用 GPS 准确定位。", "定位當地天氣。「僅在使用期間允許」即可；建議開啟「精確位置」，才能用 GPS 準確定位。", "Finds local weather. \"While using the app\" is enough; turn on \"Precise\" so GPS can be used.")
        P.OVERLAY -> tr("民国锁屏要显示在系统锁屏上层。", "民國鎖定畫面要顯示在系統鎖定畫面上層。", "The ROC lock screen draws over the system lock screen.")
        P.BATTERY -> tr("防止锁屏服务被系统在后台杀掉。", "防止鎖定畫面服務被系統在背景關閉。", "Keeps the lock screen service from being killed.")
    }

    /** 特殊权限 / “不再询问”的权限 → 对应的设置页 */
    fun settingsIntent(c: Context, p: P): List<Intent> {
        val pkg = Uri.parse("package:${c.packageName}")
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        return when (p) {
            P.OVERLAY -> listOf(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg), details)
            P.BATTERY -> listOf(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                details,
            )
            P.NOTIFY -> if (Build.VERSION.SDK_INT >= 26) {
                listOf(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName), details)
            } else {
                listOf(details)
            }
            else -> listOf(details)
        }
    }

    /** 本次启动是否已经提示过开启精确位置（进程级，下次启动再提示） */
    @Volatile
    var preciseAskedThisLaunch = false

    fun markAsked(c: Context, perm: String) {
        val p = Prefs(c)
        val set = p.askedPermissions.split(',').filter { it.isNotBlank() }.toMutableSet()
        if (set.add(perm)) p.askedPermissions = set.joinToString(",")
    }

    /** 问过且系统不再弹窗（用户选了“不再询问”或拒绝两次） */
    fun blocked(a: Activity, perm: String): Boolean =
        perm in Prefs(a).askedPermissions.split(',') && !ActivityCompat.shouldShowRequestPermissionRationale(a, perm)
}

private val GateGold = Color(0xFFE8C77A)
private val GateBg = Color(0xFF0E1116)
private val GateSub = Color(0xFF8E98A8)

@Composable
fun PermissionGate(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as Activity

    // 每次回到前台重新检查
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    var missing by remember { mutableStateOf(Perms.missing(ctx)) }
    /** 正在设置页里等待开启的权限：回来后仍没开就退出 */
    var waitingSettings by remember { mutableStateOf<Perms.P?>(null) }
    /** 设置页打不开的权限（个别系统没有该页面），本次不再强制 */
    var skipped by remember { mutableStateOf(setOf<Perms.P>()) }
    var requesting by remember { mutableStateOf(false) }
    /** 正在弹系统授权框的权限 */
    var pendingRuntime by remember { mutableStateOf<Perms.P?>(null) }

    fun exit() {
        Toast.makeText(ctx, tr("缺少必要权限，已退出", "缺少必要權限，已退出", "Required permission denied. Exiting."), Toast.LENGTH_LONG).show()
        activity.finishAndRemoveTask()
    }

    LaunchedEffect(tick) {
        val w = waitingSettings
        if (w != null) {
            delay(600) // 部分系统从设置页返回后权限状态有延迟
            missing = Perms.missing(ctx)
            waitingSettings = null
            if (w in missing) exit()
        } else if (!requesting) {
            missing = Perms.missing(ctx)
        }
    }

    val runtime = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        requesting = false
        val p = pendingRuntime
        pendingRuntime = null
        // 以实际状态为准：位置只给了「大致」也算没授权
        if (p != null && !Perms.has(ctx, p)) exit() else missing = Perms.missing(ctx)
    }

    // 只给了大致位置：每次启动提示一次升级为精确位置（GPS），拒绝也不退出
    val precise = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ -> }

    val todo = missing.filter { it !in skipped }
    if (todo.isEmpty()) {
        LaunchedEffect(Unit) {
            if (!Perms.preciseAskedThisLaunch && !Weather.hasPreciseLocation(ctx)) {
                Perms.preciseAskedThisLaunch = true
                precise.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }
        content()
        return
    }
    val cur = todo.first()
    val perm = Perms.runtimeName(cur)
    val viaSettings = perm == null || Perms.blocked(activity, perm)

    fun openSettings() {
        for (i in Perms.settingsIntent(ctx, cur)) {
            try {
                waitingSettings = cur
                activity.startActivity(i)
                return
            } catch (e: ActivityNotFoundException) {
                waitingSettings = null
            } catch (e: SecurityException) {
                waitingSettings = null
            }
        }
        skipped = skipped + cur
    }

    // 普通权限：直接弹系统授权框
    LaunchedEffect(cur, viaSettings) {
        if (!viaSettings && perm != null && !requesting && waitingSettings == null) {
            requesting = true
            pendingRuntime = cur
            Perms.markAsked(ctx, perm)
            runtime.launch(Perms.requestSet(cur))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(GateBg)
            .systemBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(tr("需要以下权限", "需要以下權限", "Permissions needed"), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        todo.forEach { p ->
            Text(
                (if (p == cur) "▶ " else "　 ") + Perms.title(p),
                color = if (p == cur) GateGold else GateSub,
                fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(Perms.reason(cur), color = Color.White, fontSize = 14.sp, lineHeight = 21.sp)
        if (viaSettings) {
            Spacer(Modifier.height(8.dp))
            Text(
                tr("将打开系统设置，请开启后返回；不开启将退出应用。", "將開啟系統設定，請開啟後返回；不開啟將退出應用程式。", "System settings will open — turn it on and come back, or the app will exit."),
                color = GateSub, fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { exit() }, modifier = Modifier.weight(1f)) {
                Text(tr("拒绝并退出", "拒絕並退出", "Deny and exit"), color = GateSub)
            }
            Button(
                onClick = {
                    if (viaSettings) {
                        openSettings()
                    } else if (perm != null && !requesting) {
                        requesting = true
                        pendingRuntime = cur
                        Perms.markAsked(ctx, perm)
                        runtime.launch(Perms.requestSet(cur))
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = GateGold, contentColor = Color(0xFF2A1F05)),
            ) { Text(if (viaSettings) tr("去开启", "去開啟", "Open settings") else tr("授权", "授權", "Allow")) }
        }
    }
}
