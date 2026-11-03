package club.daishenghui.minguo

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone

/**
 * 按当前出口 IP 判断在不在中国大陆，用来选接口：
 * - 大陆：cn.bing.com、v1.hitokoto.cn（国内线路，快）
 * - 境外（含港澳台）：www.bing.com、international.v1.hitokoto.cn（海外线路，快）
 *
 * 判断顺序（可在设置里手动指定）：
 * 1. 出口 IP：Cloudflare（loc=国家码）→ myip.ipip.net。接口快不快取决于网络出口，所以 IP 最准
 *    （例如人在大陆但开了境外代理，就该走境外接口）；
 * 2. 定位：已知经纬度时反查国家码（中国大陆 CN，港澳台各有自己的代码）；
 * 3. SIM 卡网络国家 / 系统时区。
 * 结果缓存 6 小时，切换网络（Wi-Fi ↔ 流量、开关代理）后重新判断。
 */
object Region {
    private const val TAG = "Region"
    private const val CACHE_MS = 6 * 60 * 60 * 1000L

    @Volatile private var mainland: Boolean? = null
    @Volatile private var checkedAt = 0L
    @Volatile private var lastSource = ""

    /** 设置页显示用：如 “中国大陆（Cloudflare）” */
    fun describe(lang: Lang = L.current): String {
        val m = mainland ?: return tr("检测中…", "偵測中…", "Detecting…", lang)
        val where = if (m) tr("中国大陆", "中國大陸", "Mainland China", lang) else tr("境外", "境外", "Outside mainland China", lang)
        return if (lastSource.isEmpty()) where else "$where（$lastSource）"
    }

    fun invalidate() {
        checkedAt = 0L
    }

    suspend fun isMainland(context: Context): Boolean = withContext(Dispatchers.IO) {
        when (Prefs(context).regionOverride) {
            1 -> {
                lastSource = tr("手动指定", "手動指定", "manual")
                mainland = true
                return@withContext true
            }
            2 -> {
                lastSource = tr("手动指定", "手動指定", "manual")
                mainland = false
                return@withContext false
            }
        }
        mainland?.let { if (System.currentTimeMillis() - checkedAt < CACHE_MS && lastSource != tr("手动指定", "手動指定", "manual")) return@withContext it }
        val detected = byCloudflare() ?: byIpip() ?: byLocation(context)
        val result = detected ?: guess(context)
        if (detected == null) lastSource = tr("SIM 卡 / 时区", "SIM 卡 / 時區", "SIM / time zone")
        mainland = result
        checkedAt = System.currentTimeMillis()
        Log.i(TAG, "mainland=$result source=$lastSource")
        result
    }

    private fun byCloudflare(): Boolean? {
        for (u in listOf("https://www.cloudflare.com/cdn-cgi/trace", "https://1.1.1.1/cdn-cgi/trace")) {
            val loc = runCatching { Net.get(u, timeoutMs = 3_000) }.getOrNull()
                ?.let { Regex("(?m)^loc=([A-Z]{2})").find(it)?.groupValues?.get(1) }
            if (loc != null) {
                lastSource = "Cloudflare $loc"
                return loc == "CN"
            }
        }
        return null
    }

    /** myip.ipip.net 返回如：当前 IP：1.2.3.4  来自于：中国 福建 福州  电信 */
    private fun byIpip(): Boolean? {
        val t = runCatching { Net.get("https://myip.ipip.net", timeoutMs = 3_000) }.getOrNull() ?: return null
        val from = t.substringAfter("来自于：", "").trim()
        if (from.isEmpty()) return null
        lastSource = "ipip.net"
        if (!from.startsWith("中国")) return false
        return listOf("台湾", "香港", "澳门").none { from.contains(it) }
    }

    /** 按定位反查国家码 */
    private fun byLocation(context: Context): Boolean? {
        val p = Prefs(context)
        var country = p.placeCountry
        if (country.isBlank() && !p.lat.isNaN() && !p.lon.isNaN()) {
            country = Geo.reverse(context, p.lat, p.lon)?.country.orEmpty()
        }
        if (country.isBlank()) return null
        lastSource = tr("定位", "定位", "location") + " $country"
        return country == "CN"
    }

    private fun guess(context: Context): Boolean {
        val tm = context.getSystemService(TelephonyManager::class.java)
        val iso = runCatching { tm?.networkCountryIso }.getOrNull()?.lowercase()
        if (!iso.isNullOrEmpty()) return iso == "cn"
        return TimeZone.getDefault().id in setOf("Asia/Shanghai", "Asia/Chongqing", "Asia/Harbin", "Asia/Urumqi", "Asia/Kashgar")
    }

    /** 网络切换时重新判断（在常驻服务里注册） */
    fun watchNetwork(context: Context): Any? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = invalidate()
            override fun onLost(network: Network) = invalidate()
        }
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) cm.registerDefaultNetworkCallback(cb)
            cb
        }.getOrNull()
    }

    fun unwatch(context: Context, token: Any?) {
        val cb = token as? ConnectivityManager.NetworkCallback ?: return
        runCatching { context.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) }
    }

    // ---------- 各接口地址 ----------

    suspend fun bingHost(context: Context) = if (isMainland(context)) "https://cn.bing.com" else "https://www.bing.com"

    /** 必应地区：大陆 zh-CN；境外按界面语言（繁体用 zh-TW，英文用 en-US） */
    suspend fun bingMarket(context: Context): String = if (isMainland(context)) {
        "zh-CN"
    } else {
        when (L.of(context)) {
            Lang.HANT -> "zh-TW"
            Lang.EN -> "en-US"
            Lang.HANS -> "zh-CN"
        }
    }

    suspend fun hitokotoHost(context: Context) =
        if (isMainland(context)) "https://v1.hitokoto.cn" else "https://international.v1.hitokoto.cn"
}
