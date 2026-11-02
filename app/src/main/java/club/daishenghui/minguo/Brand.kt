package club.daishenghui.minguo

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** 手机品牌 / 系统识别，以及各家“自启动、后台、电池”设置页入口 */
object Brand {
    enum class Kind { XIAOMI, HUAWEI, HONOR, OPPO, VIVO, SAMSUNG, MEIZU, OTHER }

    val kind: Kind by lazy {
        val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        when {
            listOf("xiaomi", "redmi", "poco").any { it in m } -> Kind.XIAOMI
            "honor" in m -> Kind.HONOR
            "huawei" in m -> Kind.HUAWEI
            listOf("oppo", "realme", "oneplus").any { it in m } -> Kind.OPPO
            listOf("vivo", "iqoo").any { it in m } -> Kind.VIVO
            "samsung" in m -> Kind.SAMSUNG
            "meizu" in m -> Kind.MEIZU
            else -> Kind.OTHER
        }
    }

    val isXiaomi: Boolean get() = kind == Kind.XIAOMI

    /** 鸿蒙 2/3/4（兼容安卓应用）。HarmonyOS NEXT / 5 起不再支持安卓 APK。 */
    val isHarmonyOS: Boolean by lazy {
        runCatching {
            val c = Class.forName("com.huawei.system.BuildEx")
            "harmony".equals(c.getMethod("getOsBrand").invoke(c)?.toString(), ignoreCase = true)
        }.getOrDefault(false)
    }

    fun name(lang: Lang = L.current): String {
        val base = when (kind) {
            Kind.XIAOMI -> tr("小米 / 红米（HyperOS / MIUI）", "小米 / 紅米（HyperOS / MIUI）", "Xiaomi / Redmi (HyperOS / MIUI)", lang)
            Kind.HUAWEI -> tr("华为", "華為", "Huawei", lang)
            Kind.HONOR -> tr("荣耀（MagicOS）", "榮耀（MagicOS）", "Honor (MagicOS)", lang)
            Kind.OPPO -> tr("OPPO / 一加 / realme（ColorOS）", "OPPO / 一加 / realme（ColorOS）", "OPPO / OnePlus / realme (ColorOS)", lang)
            Kind.VIVO -> tr("vivo / iQOO（OriginOS）", "vivo / iQOO（OriginOS）", "vivo / iQOO (OriginOS)", lang)
            Kind.SAMSUNG -> tr("三星（One UI）", "三星（One UI）", "Samsung (One UI)", lang)
            Kind.MEIZU -> tr("魅族（Flyme）", "魅族（Flyme）", "Meizu (Flyme)", lang)
            Kind.OTHER -> Build.MANUFACTURER
        }
        val os = if (isHarmonyOS) " · HarmonyOS" else ""
        return "$base$os · Android ${Build.VERSION.RELEASE}"
    }

    /** 一个设置入口：标题、说明、可尝试的跳转（依次尝试，都失败则打开应用详情） */
    class Shortcut(val title: String, val desc: String, val intents: List<Intent>)

    private fun comp(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))

    fun shortcuts(context: Context, lang: Lang = L.current): List<Shortcut> {
        val pkg = context.packageName
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
        return when (kind) {
            Kind.XIAOMI -> listOf(
                Shortcut(
                    tr("后台弹出界面 + 锁屏显示", "背景彈出介面 + 鎖定畫面顯示", "Background pop-ups + Show on lock screen", lang),
                    tr("在“其他权限”里把这两项都设为允许", "在「其他權限」裡把這兩項都設為允許", "Allow both under Other permissions", lang),
                    listOf(
                        Intent("miui.intent.action.APP_PERM_EDITOR")
                            .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                            .putExtra("extra_pkgname", pkg),
                        details,
                    ),
                ),
                Shortcut(
                    tr("自启动", "自動啟動", "Autostart", lang),
                    tr("开机和异常后自动恢复", "開機和異常後自動恢復", "Restore after reboot or crash", lang),
                    listOf(comp("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"), details),
                ),
                Shortcut(
                    tr("省电策略：无限制", "省電策略：無限制", "Battery saver: No restrictions", lang),
                    tr("应用信息 → 省电策略", "應用程式資訊 → 省電策略", "App info → Battery saver", lang),
                    listOf(details),
                ),
            )
            Kind.HUAWEI, Kind.HONOR -> listOf(
                Shortcut(
                    tr("应用启动管理", "應用程式啟動管理", "App launch", lang),
                    tr("关闭“自动管理”，打开允许自启动、关联启动、后台活动", "關閉「自動管理」，開啟允許自動啟動、關聯啟動、背景活動", "Turn off Manage automatically; allow auto-launch, secondary launch and run in background", lang),
                    listOf(
                        comp("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                        comp("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                        comp("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
                        comp("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
                        details,
                    ),
                ),
                Shortcut(
                    tr("锁屏显示 / 后台弹窗", "鎖定畫面顯示 / 背景彈窗", "Lock screen display / background pop-ups", lang),
                    tr("应用信息 → 权限 → 其他权限（部分机型）", "應用程式資訊 → 權限 → 其他權限（部分機型）", "App info → Permissions (some models)", lang),
                    listOf(details),
                ),
            )
            Kind.OPPO -> listOf(
                Shortcut(
                    tr("自启动 / 关联启动", "自動啟動 / 關聯啟動", "Auto-launch", lang),
                    tr("允许本应用自启动", "允許本應用程式自動啟動", "Allow auto-launch", lang),
                    listOf(
                        comp("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
                        comp("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                        comp("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                        comp("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                        comp("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
                        details,
                    ),
                ),
                Shortcut(
                    tr("耗电管理：允许后台运行", "耗電管理：允許背景執行", "Battery: allow background activity", lang),
                    tr("应用信息 → 耗电管理 → 允许完全后台行为", "應用程式資訊 → 耗電管理 → 允許完全背景行為", "App info → Battery usage → Allow background activity", lang),
                    listOf(details),
                ),
                Shortcut(
                    tr("锁屏显示", "鎖定畫面顯示", "Show on lock screen", lang),
                    tr("应用信息 → 权限 → 锁屏显示", "應用程式資訊 → 權限 → 鎖定畫面顯示", "App info → Permissions → Lock screen", lang),
                    listOf(details),
                ),
            )
            Kind.VIVO -> listOf(
                Shortcut(
                    tr("后台高耗电 / 自启动", "背景高耗電 / 自動啟動", "High background power / autostart", lang),
                    tr("允许本应用后台高耗电和自启动", "允許本應用程式背景高耗電和自動啟動", "Allow both", lang),
                    listOf(
                        comp("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                        comp("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                        comp("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                        details,
                    ),
                ),
                Shortcut(
                    tr("后台弹出界面 + 锁屏显示", "背景彈出介面 + 鎖定畫面顯示", "Background pop-ups + lock screen", lang),
                    tr("应用信息 → 权限 → 单项权限", "應用程式資訊 → 權限 → 單項權限", "App info → Permissions", lang),
                    listOf(details),
                ),
            )
            Kind.SAMSUNG -> listOf(
                Shortcut(
                    tr("电池：不受限制", "電池：不受限制", "Battery: Unrestricted", lang),
                    tr("并确保不在“深度睡眠应用程序”列表里", "並確保不在「深度睡眠應用程式」清單裡", "And keep it out of Deep sleeping apps", lang),
                    listOf(
                        comp("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                        comp("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.battery.BatteryActivity"),
                        details,
                    ),
                ),
            )
            Kind.MEIZU -> listOf(
                Shortcut(
                    tr("后台管理：允许后台运行", "背景管理：允許背景執行", "Allow background running", lang),
                    tr("手机管家 → 后台管理", "手機管家 → 背景管理", "Security → Background", lang),
                    listOf(comp("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"), details),
                ),
            )
            Kind.OTHER -> listOf(
                Shortcut(
                    tr("自启动 / 后台运行", "自動啟動 / 背景執行", "Autostart / background", lang),
                    tr("在应用信息里允许自启动、后台运行，电池设为不限制", "在應用程式資訊裡允許自動啟動、背景執行，電池設為不限制", "Allow autostart and background running; set battery to unrestricted", lang),
                    listOf(details),
                ),
            )
        }
    }

    /** 依次尝试，成功返回 true */
    fun open(context: Context, s: Shortcut): Boolean {
        for (i in s.intents) {
            val ok = runCatching { context.startActivity(Intent(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            if (ok) return true
        }
        return false
    }
}
