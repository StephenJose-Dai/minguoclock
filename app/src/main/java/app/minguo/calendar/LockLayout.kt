package app.minguo.calendar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** 锁屏上可拖动的元素。x/y 是元素中心在屏幕安全区域内的比例位置，baseSp 为默认字号 */
enum class Elem(val hans: String, val hant: String, val en: String, val x: Float, val y: Float, val baseSp: Float) {
    TIME("时间", "時間", "Time", 0.5f, 0.20f, 92f),
    DATE("日期", "日期", "Date", 0.5f, 0.31f, 20f),
    LUNAR("农历", "農曆", "Lunar date", 0.5f, 0.355f, 15f),
    WEATHER("天气", "天氣", "Weather", 0.5f, 0.42f, 16f),
    ADVICE("天气建议", "天氣建議", "Weather tip", 0.5f, 0.46f, 13f),
    ALARM("闹钟", "鬧鐘", "Alarm", 0.5f, 0.50f, 13f),
    QUOTE("名言", "名言", "Quote", 0.5f, 0.60f, 15f),
    SIGNATURE("签名", "簽名", "Signature", 0.5f, 0.72f, 16f);

    fun label(lang: Lang = L.current) = tr(hans, hant, en, lang)
}

/** 字重：0 细，1 常规，2 粗 */
data class ElemStyle(
    val x: Float,
    val y: Float,
    val scale: Float = 1f,
    val font: String = Fonts.DEFAULT,
    val weight: Int = -1,
    val visible: Boolean = true,
)

/** 贴图：文件存在 filesDir/stickers，x/y 为中心比例位置，widthDp 为显示宽度 */
data class Sticker(val id: String, val file: String, val x: Float, val y: Float, val widthDp: Float = 72f)

data class LockLayout(val elems: Map<Elem, ElemStyle>, val stickers: List<Sticker>) {
    fun style(e: Elem): ElemStyle = elems[e] ?: ElemStyle(e.x, e.y)

    fun with(e: Elem, s: ElemStyle) = copy(elems = elems + (e to s))

    fun toJson(): String = JSONObject().apply {
        put("elems", JSONObject().apply {
            elems.forEach { (e, s) ->
                put(e.name, JSONObject().apply {
                    put("x", s.x.toDouble()); put("y", s.y.toDouble()); put("s", s.scale.toDouble())
                    put("f", s.font); put("w", s.weight); put("v", s.visible)
                })
            }
        })
        put("stickers", JSONArray().apply {
            stickers.forEach {
                put(JSONObject().apply {
                    put("id", it.id); put("file", it.file); put("x", it.x.toDouble())
                    put("y", it.y.toDouble()); put("w", it.widthDp.toDouble())
                })
            }
        })
    }.toString()

    companion object {
        fun default() = LockLayout(Elem.values().associateWith { ElemStyle(it.x, it.y) }, emptyList())

        fun fromJson(s: String): LockLayout {
            val o = JSONObject(s)
            val eo = o.optJSONObject("elems") ?: JSONObject()
            val elems = Elem.values().associateWith { e ->
                val j = eo.optJSONObject(e.name)
                if (j == null) {
                    ElemStyle(e.x, e.y)
                } else {
                    ElemStyle(
                        j.optDouble("x", e.x.toDouble()).toFloat(),
                        j.optDouble("y", e.y.toDouble()).toFloat(),
                        j.optDouble("s", 1.0).toFloat(),
                        j.optString("f", Fonts.DEFAULT),
                        j.optInt("w", -1),
                        j.optBoolean("v", true),
                    )
                }
            }
            val arr = o.optJSONArray("stickers") ?: JSONArray()
            val stickers = (0 until arr.length()).map {
                val j = arr.getJSONObject(it)
                Sticker(
                    j.getString("id"), j.getString("file"),
                    j.optDouble("x", 0.5).toFloat(), j.optDouble("y", 0.1).toFloat(),
                    j.optDouble("w", 72.0).toFloat(),
                )
            }
            return LockLayout(elems, stickers)
        }
    }
}

object LayoutStore {
    private val _state = MutableStateFlow(LockLayout.default())
    val state: StateFlow<LockLayout> get() = _state
    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        Prefs(context).layoutJson?.let { j -> runCatching { LockLayout.fromJson(j) }.getOrNull()?.let { _state.value = it } }
    }

    fun save(context: Context, layout: LockLayout) {
        Prefs(context).layoutJson = layout.toJson()
        _state.value = layout
        // 删掉不再使用的贴图文件
        val used = layout.stickers.map { it.file }.toSet()
        stickerDir(context).listFiles()?.forEach { if (it.name !in used) it.delete() }
    }

    fun reset(context: Context) {
        Prefs(context).layoutJson = null
        _state.value = LockLayout.default()
        stickerDir(context).deleteRecursively()
    }

    fun forget() {
        _state.value = LockLayout.default()
        loaded = false
    }

    fun stickerDir(context: Context) = File(context.filesDir, "stickers").apply { mkdirs() }

    fun stickerFile(context: Context, s: Sticker) = File(stickerDir(context), s.file)

    /** 导入贴图（保留透明背景，存为 PNG，最长边 1024） */
    suspend fun importSticker(context: Context, uri: Uri): Sticker? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 1024 || bounds.outHeight / (sample * 2) >= 1024) sample *= 2
            val bmp = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            val id = UUID.randomUUID().toString().take(8)
            val name = "sticker_$id.png"
            File(stickerDir(context), name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Sticker(id, name, 0.5f, 0.12f)
        }.getOrNull()
    }
}
