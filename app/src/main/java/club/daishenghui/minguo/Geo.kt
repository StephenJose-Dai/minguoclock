package club.daishenghui.minguo

import android.util.Log
import org.json.JSONObject
import java.util.Locale

/**
 * 经纬度 → 国家 / 省 / 市 / 区。
 * 用 BigDataCloud 的客户端免费接口（免注册、免 Key，官方允许 App 端直接调用）。
 */
object Geo {
    /** country 为 ISO 国家码：中国大陆 CN，台湾 TW，香港 HK，澳门 MO */
    data class Place(val country: String, val province: String, val city: String, val district: String) {
        /** 显示用：福州市 鼓楼区 */
        val label: String
            get() = listOf(city.ifBlank { province }, district).filter { it.isNotBlank() }.distinct().joinToString(" ")
    }

    fun reverse(lat: Double, lon: Double): Place? = runCatching {
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
    }.onFailure { Log.i("Geo", "反查失败：${it.message}") }.getOrNull()
}
