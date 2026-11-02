package club.daishenghui.minguo

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * 桌面图标与应用名称。
 *
 * - manifest 里 6 种图标 × 3 个内置名称 = 18 个 activity-alias（.L{图标}N{名称}），
 *   每个别名有自己的 icon 和 label，同一时间只启用一个。
 *   所以换图标、改名称都是真正改了桌面上那个图标，不会多出快捷方式。
 * - 图标和名称互相独立：换图标时保留当前名称，改名称时保留当前图标。
 * - 只能选内置图标和内置名称，不支持自定义图片。
 * - 长按图标出现的菜单（更换图标 / 修改应用名称）是动态快捷方式，见 publishShortcuts。
 * - “关于”页显示的名称和图标取 displayName / displayIcon。
 */
object IconSwitcher {
    data class Style(val index: Int, val preview: Int, val zh: String, val hant: String, val en: String)

    val styles = listOf(
        Style(0, R.drawable.icon_preview_0, "默认", "預設", "Default"),
        Style(1, R.drawable.icon_preview_1, "宣纸", "宣紙", "Paper"),
        Style(2, R.drawable.icon_preview_2, "朱砂", "朱砂", "Cinnabar"),
        Style(3, R.drawable.icon_preview_3, "青瓷", "青瓷", "Celadon"),
        Style(4, R.drawable.icon_preview_4, "墨金·民", "墨金·民", "Ink & gold"),
        Style(5, R.drawable.icon_preview_5, "民國", "民國", "Minguo"),
    )

    data class Name(val index: Int, val hans: String, val hant: String, val en: String)

    /** 内置名称，顺序与 strings.xml 的 launcher_name_0..2 一致 */
    val names = listOf(
        Name(0, "民国纪年", "民國紀年", "ROC Calendar"),
        Name(1, "民国时钟", "民國時鐘", "Minguo Clock"),
        Name(2, "民国数位时钟表", "民國數位時鐘錶", "ROC Digital Clock"),
    )

    private fun alias(context: Context, icon: Int, name: Int) =
        ComponentName(context.packageName, "club.daishenghui.minguo.L${icon}N$name")

    /** 当前启用的别名：(图标, 名称)。默认 (0, 0) */
    private fun currentAlias(context: Context): Pair<Int, Int> {
        val pm = context.packageManager
        for (ic in styles.indices) for (n in names.indices) {
            val on = when (pm.getComponentEnabledSetting(alias(context, ic, n))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> ic == 0 && n == 0
                else -> false
            }
            if (on) return ic to n
        }
        return 0 to 0
    }

    /** 启用 (icon, name) 这个别名，停用其它 17 个 */
    private fun switchAlias(context: Context, icon: Int, name: Int) {
        if (currentAlias(context) == (icon to name)) return
        val pm = context.packageManager
        // 先启用新的，再停用其它，避免出现“没有入口”的瞬间
        pm.setComponentEnabledSetting(alias(context, icon, name), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        for (ic in styles.indices) for (n in names.indices) if (ic != icon || n != name) {
            pm.setComponentEnabledSetting(alias(context, ic, n), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }

    // ------------------------------------------------------------------ 名称

    fun currentName(context: Context): Int = currentAlias(context).second

    fun displayName(context: Context): String = names[currentName(context)].let { tr(it.hans, it.hant, it.en) }

    /** 只改名称：图标保持不变 */
    fun applyName(context: Context, name: Int) = switchAlias(context, current(context), name)

    // ------------------------------------------------------------------ 图标

    fun current(context: Context): Int = currentAlias(context).first

    /** “关于”页用的图标：当前内置样式 */
    fun displayIcon(context: Context): Bitmap =
        BitmapFactory.decodeResource(context.resources, styles[current(context)].preview)

    /** 只换图标：名称保持不变 */
    fun applyBuiltin(context: Context, index: Int) = switchAlias(context, index, currentName(context))

    // ------------------------------------------------------------------ 长按菜单

    /** 长按桌面图标弹出的菜单：更换图标、修改应用名称。语言切换后重新发布即可更新文字。 */
    fun publishShortcuts(context: Context) = runCatching {
        val lang = L.refresh(context)
        fun item(id: String, label: String, icon: Int, mode: String) =
            ShortcutInfoCompat.Builder(context, id)
                .setShortLabel(label)
                .setIcon(IconCompat.createWithResource(context, icon))
                .setIntent(
                    Intent(context, IconPickerActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .putExtra(IconPickerActivity.EXTRA_MODE, mode),
                )
                .build()

        ShortcutManagerCompat.setDynamicShortcuts(
            context,
            listOf(
                item("change_icon", tr("更换图标", "更換圖示", "Change icon", lang), R.drawable.icon_preview_0, IconPickerActivity.MODE_ICON),
                item("change_name", tr("修改应用名称", "修改應用程式名稱", "Rename app", lang), R.drawable.icon_preview_5, IconPickerActivity.MODE_NAME),
            ),
        )
    }
}
