package app.minguo.calendar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class UpdateInfo(val versionCode: Long, val versionName: String, val url: String, val notes: String)

/** 清空缓存、重置、检查更新、反馈 */
object Maintenance {

    fun versionName(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

    fun versionCode(context: Context): Long =
        runCatching { PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0)) }.getOrDefault(0L)

    private fun dirSize(f: File?): Long = when {
        f == null || !f.exists() -> 0L
        f.isFile -> f.length()
        else -> f.listFiles()?.sumOf { dirSize(it) } ?: 0L
    }

    /** 缓存：系统缓存目录 + 必应壁纸 + 天气缓存 */
    suspend fun cacheSize(context: Context): Long = withContext(Dispatchers.IO) {
        dirSize(context.cacheDir) + dirSize(Wallpaper.bingFile(context)) + (Prefs(context).weatherCache?.length ?: 0)
    }

    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024f)
        else -> "%.1f MB".format(bytes / 1024f / 1024f)
    }

    /** 清空缓存：不影响设置、布局、签名、自选壁纸、贴图和字体 */
    suspend fun clearCache(context: Context) = withContext(Dispatchers.IO) {
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        Wallpaper.bingFile(context).delete()
        val p = Prefs(context)
        p.bingDate = ""
        p.weatherCache = null
        Quotes.clearOnlineHistory(context)
        Fonts.clearCache()
        Weather.reset()
        Wallpaper.bump()
        Widgets.updateAll(context)
    }

    /** 恢复出厂：清空所有设置和文件 */
    suspend fun resetAll(context: Context) = withContext(Dispatchers.IO) {
        WorkManager.getInstance(context).cancelAllWork()
        context.stopService(Intent(context, LockService::class.java))
        Prefs(context).clearAll()
        context.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        LayoutStore.forget()
        Weather.reset()
        Quotes.reset()
        Fonts.clearCache()
        Wallpaper.bump()
        L.refresh(context)
        Widgets.updateAll(context)
        Watchdog.schedule(context)
        LockService.sync(context)
    }

    /** 检查更新：读取 [AppConfig.UPDATE_URL]，有新版本返回信息，否则 null */
    suspend fun checkUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val o = JSONObject(Net.get(AppConfig.UPDATE_URL))
        val info = UpdateInfo(
            o.optLong("versionCode"),
            o.optString("versionName"),
            o.optString("url"),
            o.optString("notes"),
        )
        if (info.versionCode > versionCode(context)) info else null
    }

    fun openUrl(context: Context, url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun feedback(context: Context) {
        val info = "\n\n----\n" +
            "App: ${versionName(context)} (${versionCode(context)})\n" +
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
            "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        if (AppConfig.FEEDBACK_EMAIL.isNotBlank()) {
            val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + AppConfig.FEEDBACK_EMAIL))
                .putExtra(Intent.EXTRA_SUBJECT, tr("民国纪年反馈", "民國紀年回饋", "ROC Calendar feedback"))
                .putExtra(Intent.EXTRA_TEXT, info)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(i) }.isSuccess) return
        }
        openUrl(context, AppConfig.FEEDBACK_URL)
    }
}
