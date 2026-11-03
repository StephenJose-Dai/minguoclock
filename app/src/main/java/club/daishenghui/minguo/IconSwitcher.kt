package club.daishenghui.minguo

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * 桌面图标与应用名称。
 *
 * - manifest 里 9 种图标 × 6 个内置名称（简体 3 + 繁體 3）= 54 个 activity-alias（.L{图标}N{名称}），
 *   每个别名有自己的 icon 和 label，同一时间只启用一个。
 *   所以换图标、改名称都是真正改了桌面上那个图标，不会多出快捷方式。
 * - 图标和名称互相独立：换图标时保留当前名称，改名称时保留当前图标。
 * - 只能选内置图标和内置名称，不支持自定义图片。
 * - 长按图标出现的菜单（更换图标 / 修改应用名称）是动态快捷方式，见 publishShortcuts。
 * - “关于”页显示的名称和图标取 displayName / displayIcon。
 */
object IconSwitcher {
    /** group：0 = 通用（无文字），1 = 简体，2 = 繁體（台灣） */
    data class Style(val index: Int, val preview: Int, val zh: String, val hant: String, val en: String, val group: Int)

    val styles = listOf(
        Style(0, R.drawable.icon_preview_0, "默认", "預設", "Default", 0),
        Style(1, R.drawable.icon_preview_1, "宣纸", "宣紙", "Paper", 0),
        Style(2, R.drawable.icon_preview_2, "朱砂", "朱砂", "Cinnabar", 0),
        Style(3, R.drawable.icon_preview_3, "青瓷", "青瓷", "Celadon", 0),
        Style(4, R.drawable.icon_preview_4, "墨金·民", "墨金·民", "Ink & gold", 0),
        Style(6, R.drawable.icon_preview_6, "民国", "民国", "Minguo (S)", 1),
        Style(7, R.drawable.icon_preview_7, "纪年", "纪年", "Era (S)", 1),
        Style(5, R.drawable.icon_preview_5, "民國", "民國", "Minguo (T)", 2),
        Style(8, R.drawable.icon_preview_8, "紀年", "紀年", "Era (T)", 2),
    )

    /** group：1 = 简体，2 = 繁體（台灣）。index 与 strings.xml 的 launcher_name_N 对应 */
    data class Name(val index: Int, val text: String, val group: Int)

    val names = listOf(
        Name(0, "民国纪年", 1),
        Name(1, "民国时钟", 1),
        Name(2, "民国数位时钟表", 1),
        Name(3, "民國紀年", 2),
        Name(4, "民國時鐘", 2),
        Name(5, "民國數位時鐘錶", 2),
    )

    private val iconIds get() = styles.map { it.index }
    private val nameIds get() = names.map { it.index }

    fun style(index: Int) = styles.firstOrNull { it.index == index } ?: styles[0]

    private fun alias(context: Context, icon: Int, name: Int) =
        ComponentName(context.packageName, "club.daishenghui.minguo.L${icon}N$name")

    private fun isOn(pm: PackageManager, context: Context, ic: Int, n: Int) =
        when (pm.getComponentEnabledSetting(alias(context, ic, n))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> ic == 0 && n == 0 // manifest 里默认启用的那个
            else -> false
        }

    /** 当前启用的入口：(图标, 名称)。默认 (0, 0) */
    private fun currentAlias(context: Context): Pair<Int, Int> {
        val pm = context.packageManager
        for (ic in iconIds) for (n in nameIds) if (isOn(pm, context, ic, n)) return ic to n
        return 0 to 0
    }

    /**
     * 启用 (icon, name) 这个入口，停用当前启用的入口。
     * 只改动这两个（以前是 18 个全部设一遍，桌面会连续收到很多变化通知，图标和长按菜单容易乱）。
     */
    private fun switchAlias(context: Context, icon: Int, name: Int) {
        val pm = context.packageManager
        val on = buildList { for (ic in iconIds) for (n in nameIds) if (isOn(pm, context, ic, n)) add(ic to n) }
        if (on == listOf(icon to name)) return
        // 先启用新的，再停用旧的，避免出现“没有入口”的瞬间
        pm.setComponentEnabledSetting(alias(context, icon, name), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        for ((ic, n) in on) if (ic != icon || n != name) {
            pm.setComponentEnabledSetting(alias(context, ic, n), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
        // 旧入口被停用时，系统会把挂在它上面的长按菜单一起删掉，所以要马上挂到新入口上重新发布；
        // 桌面处理组件变化需要一点时间，1.5 秒和 5 秒后各再发布一次，确保长按能出来
        val app = context.applicationContext
        publishShortcuts(app, force = true)
        val h = Handler(Looper.getMainLooper())
        h.postDelayed({ publishShortcuts(app, force = true) }, 1_500)
        h.postDelayed({ publishShortcuts(app, force = true) }, 5_000)
    }

    // ------------------------------------------------------------------ 名称

    fun currentName(context: Context): Int = currentAlias(context).second

    fun displayName(context: Context): String = (names.firstOrNull { it.index == currentName(context) } ?: names[0]).text

    /** 只改名称：图标保持不变 */
    fun applyName(context: Context, name: Int) = switchAlias(context, current(context), name)

    // ------------------------------------------------------------------ 图标

    fun current(context: Context): Int = currentAlias(context).first

    /** “关于”页用的图标：当前内置样式 */
    fun displayIcon(context: Context): Bitmap =
        BitmapFactory.decodeResource(context.resources, style(current(context)).preview)

    /** 只换图标：名称保持不变 */
    fun applyBuiltin(context: Context, index: Int) = switchAlias(context, index, currentName(context))

    // ------------------------------------------------------------------ 长按菜单

    /**
     * 长按桌面图标弹出的菜单：更换图标、修改应用名称。
     *
     * 菜单必须挂在“当前启用的那个入口”上（setActivity）：不指定时系统默认挂到 manifest 里第一个入口，
     * 换过图标/名称后那个入口是停用的，发布会失败，长按就什么都没有。
     * 入口、语言没变且菜单还在时跳过，避免触发系统对后台发布次数的限制。
     */
    fun publishShortcuts(context: Context, force: Boolean = false) {
        try {
            val lang = L.refresh(context)
            val (icon, name) = currentAlias(context)
            val target = alias(context, icon, name)
            val sig = "${target.className}|$lang"
            val p = Prefs(context)
            val present = ShortcutManagerCompat.getDynamicShortcuts(context).size >= 2
            if (!force && present && p.shortcutsSig == sig) return

            fun item(id: String, label: String, iconRes: Int, mode: String) =
                ShortcutInfoCompat.Builder(context, id)
                    .setShortLabel(label)
                    .setLongLabel(label)
                    .setIcon(IconCompat.createWithResource(context, iconRes))
                    .setActivity(target)
                    .setIntent(
                        Intent(context, IconPickerActivity::class.java)
                            .setAction(Intent.ACTION_VIEW)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            .putExtra(IconPickerActivity.EXTRA_MODE, mode),
                    )
                    .build()

            // 先清掉挂在旧入口上的，再挂到当前入口
            ShortcutManagerCompat.removeAllDynamicShortcuts(context)
            val ok = ShortcutManagerCompat.setDynamicShortcuts(
                context,
                listOf(
                    item("change_icon", tr("更换图标", "更換圖示", "Change icon", lang), R.drawable.icon_preview_0, IconPickerActivity.MODE_ICON),
                    item("change_name", tr("修改应用名称", "修改應用程式名稱", "Rename app", lang), R.drawable.icon_preview_5, IconPickerActivity.MODE_NAME),
                ),
            )
            if (ok) p.shortcutsSig = sig
        } catch (e: Exception) {
            Log.w("IconSwitcher", "发布长按菜单失败", e)
        }
    }
}
