package club.daishenghui.minguo

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * 经纬度 → 国家 / 省 / 市 / 区。
 *
 * 1. 中国大陆：填了高德 Key 就先用高德逆地理编码（国内服务，准到街道）；
 *    没填或失败时用内置离线表（assets/cn_districts.tsv，全国 2890 个区县的中心坐标，来源阿里云 DataV），
 *    找最近的区县——不联网、不需要翻墙，瞬间完成；
 * 2. 港澳台、海外：再用 BigDataCloud 免费接口（境外服务，大陆网络可能慢）；
 * 3. 手机定位完全失败时：ipFallback 用国内的 myip.ipip.net 按网络 IP 大致定位到城市。
 */
object Geo {
    /** country 为 ISO 国家码：中国大陆 CN，台湾 TW，香港 HK，澳门 MO */
    data class Place(val country: String, val province: String, val city: String, val district: String) {
        /** 显示用：福州市 鼓楼区 */
        val label: String
            get() = listOf(city.ifBlank { province }, district).filter { it.isNotBlank() }.distinct().joinToString(" ")
    }

    // ---------------- 离线表 ----------------

    private class Row(val province: String, val city: String, val district: String, val lat: Double, val lon: Double)

    @Volatile
    private var rows: List<Row>? = null

    private fun table(context: Context): List<Row> = rows ?: synchronized(this) {
        rows ?: runCatching {
            context.assets.open("cn_districts.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                var provinces = emptyList<String>()
                val out = ArrayList<Row>(3000)
                lines.forEach { line ->
                    if (line.startsWith("#")) {
                        provinces = line.substring(1).split('|')
                    } else if (line.isNotBlank()) {
                        val f = line.split('\t')
                        if (f.size >= 5) out.add(Row(provinces[f[0].toInt()], f[1], f[2], f[3].toDouble(), f[4].toDouble()))
                    }
                }
                out
            }
        }.onFailure { Log.w("Geo", "离线区县表读取失败", it) }.getOrDefault(emptyList()).also { rows = it }
    }

    private fun countryOf(province: String) = when {
        province.startsWith("台湾") -> "TW"
        province.startsWith("香港") -> "HK"
        province.startsWith("澳门") -> "MO"
        else -> "CN"
    }

    /** 台湾本岛 + 澎湖的大致范围（离福建很近，不能只看“最近的区县”） */
    private fun inTaiwan(lat: Double, lon: Double) = lat in 21.8..25.4 && lon in 119.3..122.1

    /** 近似距离（公里），够用来找最近的区县 */
    private fun km(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val x = (lon2 - lon1) * cos(Math.toRadians((lat1 + lat2) / 2))
        val y = lat2 - lat1
        return sqrt(x * x + y * y) * 111.2
    }

    /** 离线查：最近的区县在 150 公里内才算（再远就是境外） */
    fun offline(context: Context, lat: Double, lon: Double): Place? {
        if (inTaiwan(lat, lon)) return Place("TW", "台湾省", "", "")
        var best: Row? = null
        var bestKm = Double.MAX_VALUE
        for (r in table(context)) {
            val d = km(lat, lon, r.lat, r.lon)
            if (d < bestKm) {
                bestKm = d
                best = r
            }
        }
        val b = best ?: return null
        if (bestKm > 150) return null
        return Place(countryOf(b.province), b.province, b.city, b.district)
    }

    /** 上次反查用的来源（运行状态显示）：amap / offline / bigdatacloud */
    @Volatile
    var lastSource = ""
        private set

    /** 经纬度 → 地点：大陆先高德（有 Key 时），再离线表；港澳台/海外联网查，失败就用离线结果 */
    fun reverse(context: Context, lat: Double, lon: Double): Place? {
        val off = offline(context, lat, lon)
        if (off != null && off.country == "CN") {
            amap(context, lat, lon).place?.let {
                lastSource = "amap"
                return it
            }
            lastSource = "offline"
            return off
        }
        online(lat, lon)?.let {
            lastSource = "bigdatacloud"
            return it
        }
        lastSource = "offline"
        return off
    }

    // ---------------- 高德逆地理编码 ----------------

    class AmapResult(val place: Place?, val address: String, val error: String?)

    /** 当前生效的高德配置：App 里填的优先，否则用 Config.kt */
    private fun amapConfig(context: Context): Triple<String, String, String> {
        val p = Prefs(context)
        val key = p.amapKey.trim().ifBlank { AppConfig.AMAP_KEY.trim() }
        val secret = if (p.amapKey.isNotBlank()) p.amapSecret.trim() else AppConfig.AMAP_SECRET.trim()
        val url = p.amapUrl.trim().ifBlank { AppConfig.AMAP_REGEO_URL }
        return Triple(key, secret, url)
    }

    fun amapConfigured(context: Context) = amapConfig(context).first.isNotBlank()

    /** 高德逆地理编码。lat/lon 为手机定位的 WGS-84 坐标，内部转成高德用的 GCJ-02 */
    fun amap(context: Context, lat: Double, lon: Double): AmapResult {
        val (key, secret, base) = amapConfig(context)
        if (key.isBlank()) return AmapResult(null, "", "未填写高德 Key")
        return try {
            val (glat, glon) = wgs84ToGcj02(lat, lon)
            val params = sortedMapOf(
                "key" to key,
                "location" to String.format(Locale.US, "%.6f,%.6f", glon, glat),
                "extensions" to "base",
                "output" to "JSON",
            )
            val query = params.entries.joinToString("&") { "${it.key}=${it.value}" }
            // 开了数字签名：sig = MD5(按参数名排序后的“参数=值&…” + 私钥)
            val sig = if (secret.isNotBlank()) "&sig=" + md5(query + secret) else ""
            val sep = if (base.contains('?')) "&" else "?"
            val o = JSONObject(Net.get("$base$sep$query$sig", timeoutMs = 5_000))
            if (o.optString("status") != "1") {
                return AmapResult(null, "", o.optString("info") + " (" + o.optString("infocode") + ")")
            }
            val rg = o.optJSONObject("regeocode") ?: return AmapResult(null, "", "没有结果")
            val ac = rg.optJSONObject("addressComponent") ?: return AmapResult(null, "", "没有结果")
            // 高德没有值时返回空数组 []，optString 会得到 "[]"
            fun str(k: String) = ac.optString(k).takeIf { it.isNotBlank() && it != "[]" }.orEmpty()
            val province = str("province")
            if (province.isBlank()) return AmapResult(null, rg.optString("formatted_address"), "坐标不在中国境内")
            val city = str("city").ifBlank { province } // 直辖市 city 为空
            val place = Place(countryOf(province), province, city, str("district"))
            AmapResult(place, rg.optString("formatted_address"), null)
        } catch (e: Exception) {
            Log.i("Geo", "高德反查失败：${e.message}")
            AmapResult(null, "", e.javaClass.simpleName + ": " + (e.message ?: ""))
        }
    }

    private fun md5(s: String): String =
        java.security.MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /**
     * WGS-84（GPS 国际标准）→ GCJ-02（国内地图使用的坐标）。国内两者相差几百米。
     * 公开的通用换算公式；境外坐标不换算。
     */
    fun wgs84ToGcj02(lat: Double, lon: Double): Pair<Double, Double> {
        if (lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271) return lat to lon
        val a = 6378245.0
        val ee = 0.00669342162296594323
        fun tLat(x: Double, y: Double): Double {
            var r = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(Math.abs(x))
            r += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0
            r += (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0
            r += (160.0 * Math.sin(y / 12.0 * Math.PI) + 320 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0
            return r
        }
        fun tLon(x: Double, y: Double): Double {
            var r = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(Math.abs(x))
            r += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0
            r += (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0
            r += (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0
            return r
        }
        var dLat = tLat(lon - 105.0, lat - 35.0)
        var dLon = tLon(lon - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * Math.PI
        var magic = Math.sin(radLat)
        magic = 1 - ee * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((a * (1 - ee)) / (magic * sqrtMagic) * Math.PI)
        dLon = (dLon * 180.0) / (a / sqrtMagic * Math.cos(radLat) * Math.PI)
        return (lat + dLat) to (lon + dLon)
    }

    private fun online(lat: Double, lon: Double): Place? = runCatching {
        val url = String.format(
            Locale.US,
            "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=%.5f&longitude=%.5f&localityLanguage=zh",
            lat, lon,
        )
        val o = JSONObject(Net.get(url, timeoutMs = 6_000))
        val country = o.optString("countryCode").uppercase()
        if (country.isBlank()) return@runCatching null

        // administrative 按层级排列：2 国家，4 省，5 地级市，6 区县
        val admin = o.optJSONObject("localityInfo")?.optJSONArray("administrative")
        fun level(n: Int): String {
            if (admin == null) return ""
            for (i in 0 until admin.length()) {
                val a = admin.optJSONObject(i) ?: continue
                if (a.optInt("adminLevel") == n) return a.optString("name")
            }
            return ""
        }
        val province = o.optString("principalSubdivision").ifBlank { level(4) }
        val city = o.optString("city").ifBlank { level(5) }.ifBlank { province }
        val district = o.optString("locality").takeIf { it != city && it != province }.orEmpty().ifBlank { level(6) }
        Place(country, province, city, district.takeIf { it != city }.orEmpty())
    }.onFailure { Log.i("Geo", "联网反查失败：${it.message}") }.getOrNull()

    // ---------------- IP 兜底 ----------------

    class IpResult(val place: Place, val lat: Double, val lon: Double)

    /**
     * 手机定位完全拿不到时，按网络出口 IP 大致定位到城市（myip.ipip.net，国内服务，免注册）。
     * 返回格式如：“当前 IP：1.2.3.4  来自于：中国 福建 漳州  电信”。开了代理/VPN 时会定位到代理所在地。
     */
    fun ipFallback(context: Context): IpResult? = runCatching {
        val text = Net.get("https://myip.ipip.net", timeoutMs = 6_000)
        val parts = text.substringAfter("来自于：", "").trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.size < 2 || parts[0] != "中国") return@runCatching null
        val prov = parts[1]
        val city = parts.getOrNull(2).orEmpty()
        val rowsInProv = table(context).filter { it.province.startsWith(prov) }
        if (rowsInProv.isEmpty()) return@runCatching null
        // 同一个市里优先取“区”（市中心），没有就取第一个
        val inCity = rowsInProv.filter { city.isNotBlank() && it.city.startsWith(city) }
        val r = (inCity.firstOrNull { it.district.endsWith("区") } ?: inCity.firstOrNull() ?: rowsInProv.first())
        // IP 只能准到城市，区县留空
        IpResult(Place(countryOf(r.province), r.province, r.city, ""), r.lat, r.lon)
    }.onFailure { Log.i("Geo", "IP 定位失败：${it.message}") }.getOrNull()
}
