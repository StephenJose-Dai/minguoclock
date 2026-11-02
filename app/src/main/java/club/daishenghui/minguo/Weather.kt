package club.daishenghui.minguo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/** 统一的天气现象（小米天气代码、WMO 代码都映射到这里） */
enum class Cond(val hans: String, val hant: String, val en: String, @DrawableRes val dayIcon: Int, @DrawableRes val nightIcon: Int) {
    CLEAR("晴", "晴", "Clear", R.drawable.weather_sun, R.drawable.weather_moon),
    MOSTLY_CLEAR("晴间多云", "晴間多雲", "Mostly clear", R.drawable.weather_partly, R.drawable.weather_moon),
    PARTLY("多云", "多雲", "Partly cloudy", R.drawable.weather_partly, R.drawable.weather_cloud),
    OVERCAST("阴", "陰", "Overcast", R.drawable.weather_cloud, R.drawable.weather_cloud),
    FOG("雾", "霧", "Fog", R.drawable.weather_fog, R.drawable.weather_fog),
    HAZE("霾", "霾", "Haze", R.drawable.weather_fog, R.drawable.weather_fog),
    DUST("浮尘", "浮塵", "Dust", R.drawable.weather_fog, R.drawable.weather_fog),
    SAND("沙尘暴", "沙塵暴", "Sandstorm", R.drawable.weather_fog, R.drawable.weather_fog),
    DRIZZLE("毛毛雨", "毛毛雨", "Drizzle", R.drawable.weather_rain, R.drawable.weather_rain),
    LIGHT_RAIN("小雨", "小雨", "Light rain", R.drawable.weather_rain, R.drawable.weather_rain),
    RAIN("中雨", "中雨", "Rain", R.drawable.weather_rain, R.drawable.weather_rain),
    HEAVY_RAIN("大雨", "大雨", "Heavy rain", R.drawable.weather_rain, R.drawable.weather_rain),
    STORM_RAIN("暴雨", "暴雨", "Torrential rain", R.drawable.weather_rain, R.drawable.weather_rain),
    SHOWER("阵雨", "陣雨", "Showers", R.drawable.weather_rain, R.drawable.weather_rain),
    FREEZING_RAIN("冻雨", "凍雨", "Freezing rain", R.drawable.weather_rain, R.drawable.weather_rain),
    SLEET("雨夹雪", "雨夾雪", "Sleet", R.drawable.weather_snow, R.drawable.weather_snow),
    LIGHT_SNOW("小雪", "小雪", "Light snow", R.drawable.weather_snow, R.drawable.weather_snow),
    SNOW("中雪", "中雪", "Snow", R.drawable.weather_snow, R.drawable.weather_snow),
    HEAVY_SNOW("大雪", "大雪", "Heavy snow", R.drawable.weather_snow, R.drawable.weather_snow),
    BLIZZARD("暴雪", "暴雪", "Blizzard", R.drawable.weather_snow, R.drawable.weather_snow),
    SNOW_SHOWER("阵雪", "陣雪", "Snow showers", R.drawable.weather_snow, R.drawable.weather_snow),
    THUNDER("雷阵雨", "雷陣雨", "Thunderstorm", R.drawable.weather_thunder, R.drawable.weather_thunder),
    HAIL("冰雹", "冰雹", "Hail", R.drawable.weather_thunder, R.drawable.weather_thunder),
    WIND("大风", "大風", "Windy", R.drawable.weather_cloud, R.drawable.weather_cloud),
    UNKNOWN("--", "--", "--", R.drawable.weather_cloud, R.drawable.weather_cloud);

    fun label(lang: Lang) = tr(hans, hant, en, lang)

    @DrawableRes
    fun icon(isDay: Boolean = true) = if (isDay) dayIcon else nightIcon

    val isRain: Boolean
        get() = this in setOf(DRIZZLE, LIGHT_RAIN, RAIN, HEAVY_RAIN, STORM_RAIN, SHOWER, FREEZING_RAIN, THUNDER, SLEET, HAIL)
    val isSnow: Boolean get() = this in setOf(SLEET, LIGHT_SNOW, SNOW, HEAVY_SNOW, BLIZZARD, SNOW_SHOWER)

    companion object {
        fun fromWmo(code: Int): Cond = when (code) {
            0 -> CLEAR
            1 -> MOSTLY_CLEAR
            2 -> PARTLY
            3 -> OVERCAST
            45, 48 -> FOG
            51, 53, 55 -> DRIZZLE
            56, 57, 66, 67 -> FREEZING_RAIN
            61 -> LIGHT_RAIN
            63 -> RAIN
            65, 82 -> HEAVY_RAIN
            71, 77 -> LIGHT_SNOW
            73 -> SNOW
            75 -> HEAVY_SNOW
            80, 81 -> SHOWER
            85, 86 -> SNOW_SHOWER
            95 -> THUNDER
            96, 99 -> HAIL
            else -> UNKNOWN
        }

        /** 中国气象局天气现象代码（腾讯天气、中国天气网通用） */
        fun fromCma(code: String): Cond = when (code.trim().toIntOrNull() ?: -1) {
            0 -> CLEAR
            1 -> PARTLY
            2 -> OVERCAST
            3 -> SHOWER
            4 -> THUNDER
            5 -> HAIL
            6 -> SLEET
            7 -> LIGHT_RAIN
            8, 21 -> RAIN
            9, 22 -> HEAVY_RAIN
            10, 11, 12, 23, 24, 25 -> STORM_RAIN
            13 -> SNOW_SHOWER
            14 -> LIGHT_SNOW
            15, 26 -> SNOW
            16, 27 -> HEAVY_SNOW
            17, 28 -> BLIZZARD
            18, 32, 49, 57, 58 -> FOG
            19 -> FREEZING_RAIN
            20, 31 -> SAND
            29, 30 -> DUST
            53, 54, 55, 56 -> HAZE
            301 -> RAIN
            302 -> SNOW
            else -> UNKNOWN
        }

        /** 小米天气 weather_type（与小米主题文件中的分类一致） */
        fun fromXiaomi(type: Int): Cond = when (type) {
            0 -> CLEAR
            1 -> PARTLY
            2 -> OVERCAST
            3 -> FOG
            4, 5, 6 -> STORM_RAIN
            7 -> THUNDER
            8 -> SHOWER
            9 -> HEAVY_RAIN
            10 -> RAIN
            11 -> LIGHT_RAIN
            12 -> SLEET
            13 -> BLIZZARD
            14 -> SNOW_SHOWER
            15 -> HEAVY_SNOW
            16 -> SNOW
            17 -> LIGHT_SNOW
            18, 19 -> SAND
            20, 21, 23 -> DUST
            22 -> HAIL
            24 -> HAZE
            25 -> FREEZING_RAIN
            else -> UNKNOWN
        }
    }
}

data class DayForecast(val date: LocalDate, val cond: Cond, val max: Double, val min: Double, val rainChance: Int?)

data class WeatherData(
    val city: String,
    val temp: Double,
    val feelsLike: Double,
    val humidity: Int,
    val windKmh: Double,
    val cond: Cond,
    val isDay: Boolean,
    val days: List<DayForecast>,
    val aqi: Int?,
    val uvMax: Double,
    val fromSystem: Boolean,
    val fetchedAt: Long,
    /** 数据来源，如“小米天气 · 腾讯天气” */
    val sources: List<String> = emptyList(),
    /** 短时预报一句话（如“约 2 小时后可能有降水”） */
    val keypoint: String = "",
    /** 生活指数（腾讯：穿衣、雨伞、紫外线、感冒…） */
    val lifeIndex: List<String> = emptyList(),
    /** 气象预警标题 */
    val alerts: List<String> = emptyList(),
) {
    val today: DayForecast? get() = days.firstOrNull { it.date == LocalDate.now() }

    fun toJson(): String = JSONObject().apply {
        put("city", city); put("temp", temp); put("feels", feelsLike); put("hum", humidity)
        put("wind", windKmh); put("cond", cond.name); put("day", isDay); put("aqi", aqi ?: -1)
        put("uv", uvMax); put("sys", fromSystem); put("at", fetchedAt)
        put("src", JSONArray(sources)); put("kp", keypoint)
        put("life", JSONArray(lifeIndex)); put("alerts", JSONArray(alerts))
        put("days", JSONArray().apply {
            days.forEach {
                put(JSONObject().apply {
                    put("d", it.date.toString()); put("c", it.cond.name); put("max", it.max)
                    put("min", it.min); put("p", it.rainChance ?: -1)
                })
            }
        })
    }.toString()

    companion object {
        fun fromJson(s: String): WeatherData {
            val o = JSONObject(s)
            val arr = o.optJSONArray("days") ?: JSONArray()
            val days = (0 until arr.length()).map {
                val d = arr.getJSONObject(it)
                DayForecast(
                    LocalDate.parse(d.getString("d")),
                    runCatching { Cond.valueOf(d.getString("c")) }.getOrDefault(Cond.UNKNOWN),
                    d.optDouble("max"), d.optDouble("min"),
                    d.optInt("p", -1).takeIf { p -> p >= 0 },
                )
            }.filter { !it.date.isBefore(LocalDate.now()) }
            return WeatherData(
                city = o.optString("city"),
                temp = o.optDouble("temp"),
                feelsLike = o.optDouble("feels"),
                humidity = o.optInt("hum", -1),
                windKmh = o.optDouble("wind"),
                cond = runCatching { Cond.valueOf(o.getString("cond")) }.getOrDefault(Cond.UNKNOWN),
                isDay = o.optBoolean("day", true),
                days = days,
                aqi = o.optInt("aqi", -1).takeIf { it >= 0 },
                uvMax = o.optDouble("uv"),
                fromSystem = o.optBoolean("sys"),
                fetchedAt = o.optLong("at"),
                sources = o.optJSONArray("src").strings(),
                keypoint = o.optString("kp"),
                lifeIndex = o.optJSONArray("life").strings(),
                alerts = o.optJSONArray("alerts").strings(),
            )
        }
    }
}

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter { it.isNotBlank() }

data class CityResult(
    val name: String,
    val region: String,
    val lat: Double,
    val lon: Double,
    val country: String = "",
    val admin1: String = "",
    val admin2: String = "",
)

/**
 * 天气。
 * - 当前天气优先读手机自带的小米天气，保证和手机上看到的一致；
 * - 位置在中国大陆：腾讯天气（免注册，中央气象台数据）提供预报、逐时降水、生活指数、预警、空气质量；
 * - 境外或腾讯失败：Open-Meteo（免注册）；
 * - 开启自动定位时，锁屏亮起（App 处于前台）时更新位置。
 */
object Weather {
    private const val TAG = "Weather"
    private const val STALE_MS = 30 * 60 * 1000L
    private const val RELOCATE_MS = 20 * 60 * 1000L
    private const val WORK_NAME = "weather_refresh"
    const val SRC_XIAOMI = "xiaomi"
    const val SRC_TENCENT = "tencent"
    const val SRC_OPEN_METEO = "open-meteo"

    fun sourceLabel(src: String, lang: Lang) = when (src) {
        SRC_XIAOMI -> tr("小米天气", "小米天氣", "Xiaomi Weather", lang)
        SRC_TENCENT -> tr("腾讯天气", "騰訊天氣", "Tencent Weather", lang)
        else -> "Open-Meteo"
    }

    private val _state = MutableStateFlow<WeatherData?>(null)
    val state: StateFlow<WeatherData?> get() = _state
    private var loaded = false
    private val mutex = Mutex()

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val raw = Prefs(context).weatherCache ?: return
        _state.value = runCatching { WeatherData.fromJson(raw) }.getOrNull()
    }

    fun hasLocation(context: Context) = Prefs(context).let { !it.lat.isNaN() && !it.lon.isNaN() }

    /** 清空内存中的天气（清缓存/重置用） */
    fun reset() {
        _state.value = null
        loaded = false
    }

    fun hasLocationPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundLocation(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    // ---------- 刷新 ----------

    /**
     * @param inForeground App 当前是否在前台（锁屏/设置页可见），前台时才允许定位
     */
    suspend fun refresh(context: Context, inForeground: Boolean = false): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val p = Prefs(context)
            if (!p.weatherEnabled) return@withContext false

            // 1. 自动定位
            val canLocate = p.autoLocate && hasLocationPermission(context) && (inForeground || hasBackgroundLocation(context))
            if (canLocate && (p.lat.isNaN() || System.currentTimeMillis() - p.locatedAt > RELOCATE_MS)) {
                locate(context)?.let { r ->
                    setLocation(context, r.name, r.location.latitude, r.location.longitude, r.place)
                    p.locatedAt = System.currentTimeMillis()
                }
            }

            // 2. 手机自带天气：自动定位开启或尚未设置位置时才用（避免和手动选择的城市对不上）
            val sys = if (p.useSystemWeather && (p.autoLocate || p.lat.isNaN())) querySystemWeather(context) else null

            // 3. 预报：位置在中国大陆 → 腾讯天气（免注册、数据来自中央气象台）；否则 / 失败 → Open-Meteo
            val hasLoc = !p.lat.isNaN() && !p.lon.isNaN()
            val lang = L.of(context)
            if (hasLoc && p.placeCountry.isBlank()) {
                Geo.reverse(p.lat, p.lon)?.let { savePlace(context, it) }
            }
            val tencent = if (hasLoc && p.placeCountry == "CN" && p.placeProvince.isNotBlank()) {
                runCatching { fetchTencent(p.placeProvince, p.placeCity, p.placeDistrict, p.cityName, lang) }
                    .onFailure { Log.w(TAG, "腾讯天气获取失败", it) }
                    .getOrNull()
            } else {
                null
            }
            val om = tencent ?: if (hasLoc) {
                runCatching { fetchOpenMeteo(p.lat, p.lon, p.cityName) }
                    .onFailure { Log.w(TAG, "Open-Meteo 获取失败", it) }
                    .getOrNull()
            } else {
                null
            }

            val merged = when {
                om != null && sys != null -> om.copy(
                    city = sys.city.ifBlank { om.city },
                    temp = sys.temp,
                    cond = sys.cond,
                    fromSystem = true,
                    sources = listOf(SRC_XIAOMI) + om.sources,
                )
                om != null -> om
                sys != null -> sys
                else -> return@withContext false
            }
            if (merged.city.isNotBlank() && p.cityName.isBlank()) p.cityName = merged.city
            p.weatherCache = merged.toJson()
            _state.value = merged
            loaded = true
            Widgets.updateAll(context)
            true
        }
    }

    suspend fun refreshIfStale(context: Context, inForeground: Boolean) {
        init(context)
        val last = _state.value?.fetchedAt ?: 0L
        if (System.currentTimeMillis() - last > STALE_MS) refresh(context, inForeground)
    }

    // ---------- 手机自带天气（小米天气） ----------

    private val SYSTEM_URIS = listOf("content://weather/actualWeatherData/1", "content://weather/weather")

    /** 小米天气是否可读（设置页用来显示状态） */
    fun systemWeatherAvailable(context: Context): Boolean = querySystemWeather(context) != null

    private fun querySystemWeather(context: Context): WeatherData? {
        for (uri in SYSTEM_URIS) {
            try {
                context.contentResolver.query(Uri.parse(uri), null, null, null, null)?.use { c ->
                    if (!c.moveToFirst()) return@use
                    val type = c.intOrNull("weather_type") ?: return@use
                    var temp = c.strOrNull("temperature")?.let { Regex("-?\\d+(\\.\\d+)?").find(it)?.value?.toDoubleOrNull() }
                        ?: return@use
                    // temperature_unit：1 = 摄氏，其它 = 华氏
                    val unit = c.intOrNull("temperature_unit")
                    if (unit != null && unit != 1) temp = (temp - 32) / 1.8
                    return WeatherData(
                        city = c.strOrNull("city_name") ?: "",
                        temp = temp,
                        feelsLike = Double.NaN,
                        humidity = -1,
                        windKmh = Double.NaN,
                        cond = Cond.fromXiaomi(type),
                        isDay = java.time.LocalTime.now().hour in 6..18,
                        days = emptyList(),
                        aqi = null,
                        uvMax = Double.NaN,
                        fromSystem = true,
                        fetchedAt = System.currentTimeMillis(),
                        sources = listOf(SRC_XIAOMI),
                    )
                }
            } catch (e: Exception) {
                Log.i(TAG, "读取系统天气失败：$uri ${e.javaClass.simpleName}")
            }
        }
        return null
    }

    private fun Cursor.intOrNull(col: String): Int? {
        val i = getColumnIndex(col)
        return if (i < 0 || isNull(i)) null else runCatching { getInt(i) }.getOrNull()
    }

    private fun Cursor.strOrNull(col: String): String? {
        val i = getColumnIndex(col)
        return if (i < 0 || isNull(i)) null else runCatching { getString(i) }.getOrNull()
    }

    // ---------- Open-Meteo ----------

    private fun fetchOpenMeteo(lat: Double, lon: Double, city: String): WeatherData {
        val body = Net.get(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,uv_index_max" +
                "&minutely_15=precipitation&forecast_minutely_15=8" +
                "&timezone=auto&forecast_days=4",
        )
        val o = JSONObject(body)
        val cur = o.getJSONObject("current")
        val daily = o.getJSONObject("daily")
        val times = daily.getJSONArray("time")
        val codes = daily.getJSONArray("weather_code")
        val maxs = daily.getJSONArray("temperature_2m_max")
        val mins = daily.getJSONArray("temperature_2m_min")
        val pops = daily.optJSONArray("precipitation_probability_max")
        val uvs = daily.optJSONArray("uv_index_max")
        val days = (0 until times.length()).map { i ->
            DayForecast(
                LocalDate.parse(times.getString(i)),
                Cond.fromWmo(codes.optInt(i)),
                maxs.optDouble(i),
                mins.optDouble(i),
                pops?.let { if (it.isNull(i)) null else it.optInt(i) },
            )
        }
        val aqi = runCatching {
            val aq = JSONObject(
                Net.get("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon&current=us_aqi"),
            )
            aq.getJSONObject("current").optInt("us_aqi", -1).takeIf { it >= 0 }
        }.getOrNull()

        // 未来 2 小时（8 个 15 分钟）降水 → 一句话
        val keypoint = o.optJSONObject("minutely_15")?.optJSONArray("precipitation")?.let { arr ->
            val values = (0 until arr.length()).map { if (arr.isNull(it)) 0.0 else arr.optDouble(it) }
            val lang = L.current
            val first = values.indexOfFirst { it >= 0.1 }
            when {
                values.isEmpty() -> ""
                first < 0 -> tr("未来两小时预计无降水", "未來兩小時預計無降雨", "No rain expected in the next 2 hours", lang)
                first == 0 -> tr("当前有降水", "目前有降雨", "Precipitation now", lang)
                else -> tr("约 ${first * 15} 分钟后可能开始下雨", "約 ${first * 15} 分鐘後可能開始下雨", "Rain may start in about ${first * 15} min", lang)
            }
        } ?: ""

        return WeatherData(
            keypoint = keypoint,
            city = city,
            temp = cur.optDouble("temperature_2m"),
            feelsLike = cur.optDouble("apparent_temperature"),
            humidity = cur.optInt("relative_humidity_2m", -1),
            windKmh = cur.optDouble("wind_speed_10m"),
            cond = Cond.fromWmo(cur.optInt("weather_code")),
            isDay = cur.optInt("is_day", 1) == 1,
            days = days,
            aqi = aqi,
            uvMax = uvs?.let { if (it.length() > 0 && !it.isNull(0)) it.optDouble(0) else Double.NaN } ?: Double.NaN,
            fromSystem = false,
            fetchedAt = System.currentTimeMillis(),
            sources = listOf(SRC_OPEN_METEO),
        )
    }

    // ---------- 腾讯天气（免注册） ----------

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** 去掉行政区后缀：福建省→福建，广西壮族自治区→广西，福州市→福州 */
    private fun shortName(s: String) = s.replace(Regex("(壮族|回族|维吾尔)?自治区$|特别行政区$|省$|市$"), "")

    private fun fetchTencent(province: String, city: String, district: String, label: String, lang: Lang): WeatherData {
        fun call(pv: String, ct: String, ds: String): JSONObject? {
            val url = "https://wis.qq.com/weather/common?source=pc" +
                "&weather_type=" + enc("observe|forecast_1h|forecast_24h|index|alarm|tips|air") +
                "&province=" + enc(pv) + "&city=" + enc(ct) + (if (ds.isNotBlank()) "&county=" + enc(ds) else "")
            val data = JSONObject(Net.get(url, mapOf("Referer" to "https://tianqi.qq.com/"))).optJSONObject("data") ?: return null
            val obs = data.optJSONObject("observe") ?: return null
            return if (obs.optString("degree").isBlank()) null else data
        }
        val ct = city.ifBlank { province }
        val data = call(province, ct, district)
            ?: call(province, ct, "")
            ?: call(shortName(province), shortName(ct), "")
            ?: error("腾讯天气无数据：$province $ct")

        val hant = lang == Lang.HANT
        fun zh(s: String) = if (hant) Quotes.toHant(s) else s

        val obs = data.getJSONObject("observe")
        val nowCond = Cond.fromCma(obs.optString("weather_code"))
        val hour = java.time.LocalTime.now().hour

        // 逐日：forecast_24h 的 "0" 是昨天，只保留今天及以后
        val today = LocalDate.now()
        val f24 = data.optJSONObject("forecast_24h")
        val days = f24?.keys()?.asSequence()?.mapNotNull { k -> f24.optJSONObject(k) }
            ?.mapNotNull { d ->
                val date = runCatching { LocalDate.parse(d.optString("time")) }.getOrNull() ?: return@mapNotNull null
                DayForecast(
                    date,
                    Cond.fromCma(d.optString("day_weather_code")),
                    d.optString("max_degree").toDoubleOrNull() ?: Double.NaN,
                    d.optString("min_degree").toDoubleOrNull() ?: Double.NaN,
                    null,
                )
            }?.filter { !it.date.isBefore(today) }?.sortedBy { it.date }?.toList() ?: emptyList()

        // 逐小时：算出接下来 6 小时会不会下雨
        val f1h = data.optJSONObject("forecast_1h")
        val hours = f1h?.keys()?.asSequence()?.mapNotNull { k -> f1h.optJSONObject(k) }
            ?.sortedBy { it.optString("update_time") }?.toList() ?: emptyList()
        val nowStamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHH"))
        val upcoming = hours.filter { it.optString("update_time").take(10) > nowStamp }.take(6)
        val rainAt = upcoming.indexOfFirst { Cond.fromCma(it.optString("weather_code")).let { c -> c.isRain || c.isSnow } }
        val keypoint = when {
            nowCond.isRain || nowCond.isSnow -> tr("当前有降水", "目前有降雨", "Precipitation now", lang)
            rainAt >= 0 -> tr("约 ${rainAt + 1} 小时后可能有降水", "約 ${rainAt + 1} 小時後可能有降雨", "Precipitation possible in about ${rainAt + 1} h", lang)
            upcoming.isNotEmpty() -> tr("未来 6 小时无降水", "未來 6 小時無降雨", "No precipitation in the next 6 hours", lang)
            else -> ""
        }

        // 生活指数（只在中文界面显示原文，英文界面用内置建议）
        val life = if (lang == Lang.EN) {
            emptyList()
        } else {
            val idx = data.optJSONObject("index")
            listOf("clothes", "umbrella", "ultraviolet", "cold", "comfort", "carwash").mapNotNull { key ->
                val o = idx?.optJSONObject(key) ?: return@mapNotNull null
                val name = o.optString("name").removeSuffix("指数")
                val info = o.optString("info")
                if (name.isBlank() || info.isBlank()) null else zh("$name：$info")
            }
        }

        val alarmObj = data.optJSONObject("alarm")
        val alerts = alarmObj?.keys()?.asSequence()?.mapNotNull { alarmObj.optJSONObject(it) }
            ?.mapNotNull { a ->
                val t = a.optString("type_name")
                val lv = a.optString("level_name")
                if (t.isBlank()) null else zh(t + lv + "预警")
            }?.toList() ?: emptyList()

        val air = data.optJSONObject("air")
        val aqi = air?.optString("aqi")?.toDoubleOrNull()?.roundToInt()

        return WeatherData(
            city = zh(label),
            temp = obs.optString("degree").toDoubleOrNull() ?: Double.NaN,
            feelsLike = Double.NaN,
            humidity = obs.optString("humidity").toIntOrNull() ?: -1,
            windKmh = Double.NaN,
            cond = nowCond,
            isDay = hour in 6..18,
            days = days,
            aqi = aqi,
            uvMax = Double.NaN,
            fromSystem = false,
            fetchedAt = System.currentTimeMillis(),
            sources = listOf(SRC_TENCENT),
            keypoint = keypoint,
            lifeIndex = life,
            alerts = alerts,
        )
    }

    private fun savePlace(context: Context, place: Geo.Place) {
        val p = Prefs(context)
        p.placeCountry = place.country
        p.placeProvince = place.province
        p.placeCity = place.city
        p.placeDistrict = place.district
        if (p.cityName.isBlank()) p.cityName = place.label
    }

    /** 城市搜索（支持中文，如“福州”“臺北”） */
    suspend fun searchCity(query: String, lang: Lang): List<CityResult> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val l = if (lang == Lang.EN) "en" else "zh"
        val body = Net.get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=10&language=$l&format=json")
        val arr = JSONObject(body).optJSONArray("results") ?: return@withContext emptyList()
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val region = listOf(o.optString("admin1"), o.optString("country"))
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" · ")
            CityResult(
                o.getString("name"), region, o.getDouble("latitude"), o.getDouble("longitude"),
                o.optString("country_code"), o.optString("admin1"), o.optString("admin2"),
            )
        }
    }

    fun setLocation(context: Context, name: String, lat: Double, lon: Double, place: Geo.Place? = null) {
        val p = Prefs(context)
        p.cityName = name
        p.lat = lat
        p.lon = lon
        p.placeCountry = place?.country ?: ""
        p.placeProvince = place?.province ?: ""
        p.placeCity = place?.city ?: ""
        p.placeDistrict = place?.district ?: ""
    }

    /** 搜索结果 → 省市区（腾讯天气要用） */
    fun placeOf(c: CityResult): Geo.Place {
        // 结果是区县时 admin2 是所属城市；是城市时 admin2 为空
        val city = c.admin2.ifBlank { c.name }
        val district = if (c.admin2.isNotBlank() && c.admin2 != c.name) c.name else ""
        return Geo.Place(c.country.uppercase(), c.admin1, city, district)
    }

    // ---------- 定位 ----------

    class Located(val name: String, val location: Location, val place: Geo.Place?)

    /** 定位并反查省市区 */
    @SuppressLint("MissingPermission")
    suspend fun locate(context: Context): Located? {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }.filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

        fun lastKnown() = providers
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }

        var loc: Location? = lastKnown()?.takeIf { System.currentTimeMillis() - it.time < 15 * 60 * 1000L }

        if (loc == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            for (provider in providers.filter { it != LocationManager.PASSIVE_PROVIDER }) {
                loc = withTimeoutOrNull(12_000) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        val signal = CancellationSignal()
                        cont.invokeOnCancellation { signal.cancel() }
                        try {
                            lm.getCurrentLocation(provider, signal, context.mainExecutor) { cont.resume(it) }
                        } catch (e: Exception) {
                            cont.resume(null)
                        }
                    }
                }
                if (loc != null) break
            }
        }
        val found = loc ?: lastKnown() ?: return null
        val place = withContext(Dispatchers.IO) { Geo.reverse(found.latitude, found.longitude) } ?: geocoderPlace(context, found)
        return Located(place?.label ?: "", found, place)
    }

    /** BigDataCloud 不可用时，用系统自带的 Geocoder 反查 */
    @Suppress("DEPRECATION")
    private suspend fun geocoderPlace(context: Context, loc: Location): Geo.Place? = withContext(Dispatchers.IO) {
        runCatching {
            if (!Geocoder.isPresent()) return@runCatching null
            val a = Geocoder(context, Locale.CHINA).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
                ?: return@runCatching null
            Geo.Place(a.countryCode ?: "", a.adminArea ?: "", a.locality ?: a.subAdminArea ?: "", a.subLocality ?: "")
        }.getOrNull()
    }

    // ---------- 定时刷新 ----------

    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (!Prefs(context).weatherEnabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val req = PeriodicWorkRequestBuilder<WeatherWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    // ---------- 显示 ----------

    fun temp(t: Double): String = if (t.isNaN()) "--°" else "${t.roundToInt()}°"

    fun cityLabel(w: WeatherData, lang: Lang) = w.city.ifBlank { tr("当前位置", "目前位置", "Current location", lang) }

    /** 一行摘要：多云 18°  15°/22° */
    fun summary(w: WeatherData, lang: Lang): String {
        val range = w.today?.let { "  ${temp(it.min)}/${temp(it.max)}" } ?: ""
        return "${w.cond.label(lang)} ${temp(w.temp)}$range"
    }

    fun aqiLabel(aqi: Int, lang: Lang): String = when {
        aqi <= 50 -> tr("优", "優", "Good", lang)
        aqi <= 100 -> tr("良", "良", "Moderate", lang)
        aqi <= 150 -> tr("轻度污染", "輕度污染", "Unhealthy (sensitive)", lang)
        aqi <= 200 -> tr("中度污染", "中度污染", "Unhealthy", lang)
        aqi <= 300 -> tr("重度污染", "重度污染", "Very unhealthy", lang)
        else -> tr("严重污染", "嚴重污染", "Hazardous", lang)
    }

    /** 天气建议，按重要程度排序 */
    fun advice(w: WeatherData, lang: Lang): List<String> {
        val out = mutableListOf<String>()
        w.alerts.forEach { out += "⚠ $it" }
        if (w.keypoint.isNotBlank()) out += w.keypoint
        val today = w.today
        val rain = today?.rainChance ?: 0
        val todayRain = w.cond.isRain || today?.cond?.isRain == true

        if (w.cond == Cond.THUNDER || w.cond == Cond.HAIL) {
            out += tr("有雷雨，尽量待在室内，远离高处和大树", "有雷雨，盡量待在室內，遠離高處和大樹", "Thunderstorms — stay indoors, avoid high ground and trees", lang)
        }
        if (todayRain && rain >= 40 || w.cond.isRain) {
            out += tr("今天有雨，出门记得带伞", "今天有雨，出門記得帶傘", "Rain expected — take an umbrella", lang)
        } else if (rain >= 30) {
            out += tr("可能有阵雨，建议备把伞", "可能有陣雨，建議備把傘", "Chance of showers — consider an umbrella", lang)
        }
        if (w.cond.isSnow || today?.cond?.isSnow == true) {
            out += tr("有雪，路面湿滑，注意出行安全", "有雪，路面濕滑，注意出行安全", "Snow — roads may be slippery", lang)
        }
        w.aqi?.let {
            if (it > 150) {
                out += tr("空气较差，减少户外活动，外出戴口罩", "空氣較差，減少戶外活動，外出戴口罩", "Poor air — limit time outdoors, wear a mask", lang)
            } else if (it > 100) {
                out += tr("空气一般，敏感人群减少户外活动", "空氣普通，敏感族群減少戶外活動", "Moderate air — sensitive groups take care", lang)
            }
        }
        if (w.cond == Cond.FOG || w.cond == Cond.HAZE || w.cond == Cond.SAND || w.cond == Cond.DUST) {
            out += tr("能见度低，开车注意减速慢行", "能見度低，開車注意減速慢行", "Low visibility — drive carefully", lang)
        }
        val t = if (!w.temp.isNaN()) w.temp else today?.max
        if (w.lifeIndex.isNotEmpty()) {
            out += w.lifeIndex
        } else if (t != null && !t.isNaN()) {
            out += clothing(t, lang)
        }
        if (today != null && today.max - today.min >= 10) {
            out += tr("昼夜温差大，注意增减衣物", "晝夜溫差大，注意增減衣物", "Big temperature swing — dress in layers", lang)
        }
        if (w.lifeIndex.isNotEmpty()) {
            // 已有生活指数（含紫外线）
        } else if (w.uvMax >= 8) {
            out += tr("紫外线很强，外出注意防晒", "紫外線很強，外出注意防曬", "Very high UV — wear sunscreen", lang)
        } else if (w.uvMax >= 6) {
            out += tr("紫外线较强，适当防晒", "紫外線較強，適當防曬", "High UV — sun protection advised", lang)
        }
        if (w.windKmh >= 38) {
            out += tr("风力较大，注意高空坠物", "風力較大，注意高空墜物", "Strong wind — watch for falling objects", lang)
        }
        return out
    }

    private fun clothing(t: Double, lang: Lang): String = when {
        t < 5 -> tr("天冷，穿羽绒服或厚外套", "天冷，穿羽絨衣或厚外套", "Cold — wear a down jacket or heavy coat", lang)
        t < 12 -> tr("较冷，毛衣加外套", "偏冷，毛衣加外套", "Chilly — sweater and jacket", lang)
        t < 18 -> tr("微凉，带件外套", "微涼，帶件外套", "Cool — bring a jacket", lang)
        t < 25 -> tr("舒适，长袖或薄外套", "舒適，長袖或薄外套", "Comfortable — long sleeves or a light layer", lang)
        t < 30 -> tr("较热，短袖为宜", "偏熱，短袖為宜", "Warm — short sleeves", lang)
        else -> tr("炎热，注意防暑多喝水", "炎熱，注意防暑多喝水", "Hot — stay cool and hydrated", lang)
    }
}

/** 每小时在后台刷新一次天气 */
class WeatherWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val p = Prefs(ctx)
        if (!p.weatherEnabled) return Result.success()
        if (!Weather.hasLocation(ctx) && !p.useSystemWeather) return Result.success()
        return if (Weather.refresh(ctx, inForeground = false)) Result.success() else Result.retry()
    }
}
