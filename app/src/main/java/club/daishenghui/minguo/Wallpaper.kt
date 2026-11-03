package club.daishenghui.minguo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** 读出来的壁纸 + 顶部/整体亮度（用于决定状态栏图标深浅） */
class WallpaperImage(val bitmap: Bitmap, val topLuminance: Float, val luminance: Float)

/** 锁屏壁纸：自选图片 / 必应每日壁纸 */
object Wallpaper {
    private const val TAG = "Wallpaper"
    private const val WORK_NAME = "bing_wallpaper"
    const val MODE_THEME = 0
    const val MODE_CUSTOM = 1
    const val MODE_BING = 2

    /** 壁纸文件变化时 +1，界面据此重新加载 */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> get() = _version

    fun customFile(context: Context) = File(context.filesDir, "wallpaper_custom.jpg")
    fun bingFile(context: Context) = File(context.filesDir, "wallpaper_bing.jpg")

    /** 通知界面重新加载壁纸 */
    fun bump() {
        _version.value++
    }

    fun fileFor(context: Context, mode: Int): File? = when (mode) {
        MODE_CUSTOM -> customFile(context)
        MODE_BING -> bingFile(context)
        else -> null
    }?.takeIf { it.exists() && it.length() > 0 }

    /** 从相册导入：缩到最长边 2560 再存成 JPEG */
    suspend fun importCustom(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val sample = sampleSize(bounds.outWidth, bounds.outHeight, 2560)
            val bmp = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@withContext false
            val rotated = rotateByExif(context, uri, bmp)
            writeAtomically(customFile(context)) { out -> rotated.compress(Bitmap.CompressFormat.JPEG, 92, out) }
            _version.value++
            true
        } catch (e: Exception) {
            Log.w(TAG, "导入图片失败", e)
            false
        }
    }

    private fun rotateByExif(context: Context, uri: Uri, bmp: Bitmap): Bitmap {
        val degrees = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                val exif = android.media.ExifInterface(it)
                when (exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        if (degrees == 0f) return bmp
        val m = android.graphics.Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    /** 屏幕实际像素（竖屏方向：宽 < 高） */
    fun screenSize(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(android.view.WindowManager::class.java)
        val (w, h) = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val b = wm.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels to dm.heightPixels
        }
        return minOf(w, h) to maxOf(w, h)
    }

    /**
     * 必应提供竖版 1080x1920 和横版 UHD 3840x2160 两种。
     * 按屏幕比例计算“铺满屏幕需要放大多少倍”，选放大最少（最清晰）的那张：
     * 普通 16:9 屏用竖版；20:9 / 21:9 长屏、折叠屏、平板用 UHD 裁切。
     */
    private fun bingCandidates(context: Context, host: String, urlbase: String): List<String> {
        val (sw, sh) = screenSize(context)
        data class C(val suffix: String, val w: Int, val h: Int)
        val cands = listOf(C("_1080x1920.jpg", 1080, 1920), C("_UHD.jpg", 3840, 2160))
        fun upscale(c: C) = maxOf(sw.toFloat() / c.w, sh.toFloat() / c.h)
        val sharpEnough = cands.firstOrNull { upscale(it) <= 1.0f }
        val ordered = if (sharpEnough != null) listOf(sharpEnough) + (cands - sharpEnough) else cands.sortedBy { upscale(it) }
        return ordered.map { "$host$urlbase${it.suffix}" }
    }

    // ======================== 必应壁纸轮换 ========================
    //
    // 在最近十几天的必应壁纸里轮换：
    // - 每次熄屏换一张（下次亮屏就是新图）；亮屏期间每 30 分钟换一张；
    // - 图片列表每天拉一次；图片下载后缓存在 cache/bing，换图时只是本地复制，不用等网络；
    // - 每次换完顺便预下载下一张，所以熄屏切换几乎是瞬间完成。

    /** 换图间隔 */
    const val BING_INTERVAL_MS = 30 * 60 * 1000L

    private val bingMutex = kotlinx.coroutines.sync.Mutex()

    private fun bingCacheDir(context: Context) = File(context.cacheDir, "bing").apply { mkdirs() }

    private fun cacheName(urlbase: String) =
        urlbase.substringAfter("id=").replace(Regex("[^A-Za-z0-9._-]"), "_").take(90).ifBlank { urlbase.hashCode().toString() } + ".jpg"

    private suspend fun hosts(context: Context): List<String> {
        val preferred = Region.bingHost(context)
        return listOf(preferred) + listOf("https://cn.bing.com", "https://www.bing.com").filter { it != preferred }
    }

    /** 图片列表（urlbase），每天拉一次；拉不到就用旧列表 */
    private suspend fun ensureList(context: Context): List<String> {
        val p = Prefs(context)
        val today = LocalDate.now().toString()
        val old = runCatching { org.json.JSONArray(p.bingList).let { a -> (0 until a.length()).map { a.getString(it) } } }
            .getOrDefault(emptyList())
        if (p.bingListDate == today && old.isNotEmpty()) return old
        val mkt = Region.bingMarket(context)
        for (host in hosts(context)) {
            val market = if (host.contains("cn.bing")) "zh-CN" else mkt
            val list = LinkedHashSet<String>()
            // 必应一次最多给 8 张：idx=0 取最近 8 天，idx=7 再取更早的 8 天
            for (idx in listOf(0, 7)) {
                runCatching {
                    val arr = JSONObject(Net.get("$host/HPImageArchive.aspx?format=js&idx=$idx&n=8&mkt=$market")).getJSONArray("images")
                    for (k in 0 until arr.length()) list.add(arr.getJSONObject(k).getString("urlbase"))
                }
            }
            if (list.isNotEmpty()) {
                p.bingList = org.json.JSONArray(list.toList()).toString()
                p.bingListDate = today
                // 清理不在列表里的旧缓存
                val keep = list.map(::cacheName).toSet()
                bingCacheDir(context).listFiles()?.forEach { if (it.name !in keep) it.delete() }
                return list.toList()
            }
        }
        return old
    }

    /** 取第 i 张的本地文件：有缓存直接用，没有就按屏幕尺寸下载 */
    private suspend fun imageFile(context: Context, urlbase: String): File? {
        val f = File(bingCacheDir(context), cacheName(urlbase))
        if (f.exists() && f.length() > 10_000) return f
        for (host in hosts(context)) {
            for (u in bingCandidates(context, host, urlbase)) {
                val bytes = runCatching { Net.bytes(u) }.getOrNull()?.takeIf { it.size > 10_000 } ?: continue
                writeAtomically(f) { it.write(bytes) }
                return f
            }
        }
        return null
    }

    /** 立刻换下一张（熄屏、30 分钟到、点“立即更新”时调用） */
    suspend fun rotateBing(context: Context): Boolean = withContext(Dispatchers.IO) {
        bingMutex.withLock {
            try {
                val p = Prefs(context)
                val list = ensureList(context)
                if (list.isEmpty()) return@withLock false
                val start = (p.bingIndex + 1).mod(list.size)
                for (k in list.indices) {
                    val idx = (start + k) % list.size
                    val f = imageFile(context, list[idx]) ?: continue
                    writeAtomically(bingFile(context)) { out -> f.inputStream().use { it.copyTo(out) } }
                    val now = System.currentTimeMillis()
                    p.bingIndex = idx
                    p.bingChangedAt = now
                    p.bingFetchedAt = now
                    p.bingDate = LocalDate.now().toString()
                    _version.value++
                    // 预下载下一张，下次切换不用等网络
                    runCatching { imageFile(context, list[(idx + 1) % list.size]) }
                    return@withLock true
                }
                false
            } catch (e: Exception) {
                Log.w(TAG, "必应壁纸更换失败", e)
                false
            }
        }
    }

    /**
     * 亮屏、打开锁屏页、后台任务时调用：没有图就下一张；距上次更换满 30 分钟就换；force = 立刻换。
     */
    suspend fun refreshBing(context: Context, force: Boolean = false): Boolean {
        val p = Prefs(context)
        val due = force || !bingFile(context).exists() ||
            System.currentTimeMillis() - p.bingChangedAt >= BING_INTERVAL_MS
        return if (due) rotateBing(context) else true
    }

    /** 读取壁纸，按目标尺寸采样，顺便算亮度 */
    suspend fun load(context: Context, mode: Int, maxW: Int, maxH: Int): WallpaperImage? = withContext(Dispatchers.IO) {
        val f = fileFor(context, mode) ?: return@withContext null
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            val sample = coverSample(bounds.outWidth, bounds.outHeight, maxW, maxH)
            val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return@withContext null
            WallpaperImage(bmp, luminance(bmp, 0f, 0.15f), luminance(bmp, 0f, 1f))
        } catch (e: Exception) {
            Log.w(TAG, "读取壁纸失败", e)
            null
        }
    }

    /** 区域平均亮度 0..1（fromY/toY 为高度比例） */
    private fun luminance(bmp: Bitmap, fromY: Float, toY: Float): Float {
        val small = Bitmap.createScaledBitmap(bmp, 16, 32, true)
        var sum = 0f
        var n = 0
        val y0 = (fromY * small.height).toInt()
        val y1 = (toY * small.height).toInt().coerceAtLeast(y0 + 1)
        for (y in y0 until y1) for (x in 0 until small.width) {
            val c = small.getPixel(x, y)
            sum += (0.2126f * Color.red(c) + 0.7152f * Color.green(c) + 0.0722f * Color.blue(c)) / 255f
            n++
        }
        return if (n == 0) 0f else sum / n
    }

    /** 采样后仍能以 Crop 方式铺满 targetW x targetH */
    private fun coverSample(w: Int, h: Int, targetW: Int, targetH: Int): Int {
        if (w <= 0 || h <= 0) return 1
        val k = maxOf(targetW.toFloat() / w, targetH.toFloat() / h)
        val needW = w * k
        val needH = h * k
        var s = 1
        while (w / (s * 2f) >= needW && h / (s * 2f) >= needH) s *= 2
        return s
    }

    private fun sampleSize(w: Int, h: Int, max: Int): Int {
        var s = 1
        while (w / (s * 2) >= max || h / (s * 2) >= max) s *= 2
        return s
    }

    private fun writeAtomically(target: File, block: (java.io.OutputStream) -> Unit) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.outputStream().use(block)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (Prefs(context).wallpaperMode != MODE_BING) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        // 每 30 分钟检查一次（满 30 分钟就换图）。UPDATE：已安装旧版（6 小时）的手机也会改成新周期
        val req = PeriodicWorkRequestBuilder<BingWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
    }
}

/** 每 30 分钟：满 30 分钟就换一张必应壁纸 */
class BingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (Wallpaper.refreshBing(applicationContext)) Result.success() else Result.retry()
}
