package club.daishenghui.minguo

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

data class FontEntry(val id: String, val hans: String, val hant: String, val en: String) {
    fun label(lang: Lang = L.current) = tr(hans, hant, en, lang)
}

/** 字体：系统自带几种 + 用户导入的 TTF/OTF */
object Fonts {
    const val DEFAULT = "default"
    private const val FILE_PREFIX = "file:"

    private val builtins = listOf(
        FontEntry(DEFAULT, "默认", "預設", "Default"),
        FontEntry("sans", "黑体", "黑體", "Sans"),
        FontEntry("serif", "宋体", "宋體", "Serif"),
        FontEntry("mono", "等宽", "等寬", "Mono"),
        FontEntry("cursive", "手写(英文)", "手寫(英文)", "Cursive"),
    )

    /** 导入/删除字体后 +1 */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> get() = _version

    private val cache = HashMap<String, FontFamily>()

    fun dir(context: Context) = File(context.filesDir, "fonts").apply { mkdirs() }

    fun list(context: Context): List<FontEntry> = builtins + (dir(context).listFiles()?.sortedBy { it.name } ?: emptyList()).map {
        val n = it.nameWithoutExtension
        FontEntry(FILE_PREFIX + it.name, n, n, n)
    }

    fun family(context: Context, id: String): FontFamily = when (id) {
        "sans" -> FontFamily.SansSerif
        "serif" -> FontFamily.Serif
        "mono" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        DEFAULT -> FontFamily.Default
        else -> if (id.startsWith(FILE_PREFIX)) {
            cache.getOrPut(id) {
                val f = File(dir(context), id.removePrefix(FILE_PREFIX))
                runCatching { FontFamily(Typeface.createFromFile(f)) }.getOrDefault(FontFamily.Default)
            }
        } else {
            FontFamily.Default
        }
    }

    fun weight(w: Int, default: FontWeight): FontWeight = when (w) {
        0 -> FontWeight.Light
        1 -> FontWeight.Normal
        2 -> FontWeight.Bold
        else -> default
    }

    /** 导入字体文件，返回字体 id */
    suspend fun import(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            var name = "font_${System.currentTimeMillis()}.ttf"
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { n -> name = n.replace(Regex("[^\\w.\\-\\u4e00-\\u9fa5]"), "_") }
            }
            if (!name.endsWith(".ttf", true) && !name.endsWith(".otf", true) && !name.endsWith(".ttc", true)) name += ".ttf"
            val target = File(dir(context), name)
            context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                ?: return@runCatching null
            // 验证是否为有效字体
            val tf = runCatching { Typeface.createFromFile(target) }.getOrNull()
            if (tf == null) {
                target.delete()
                return@runCatching null
            }
            cache.remove(FILE_PREFIX + name)
            _version.value++
            FILE_PREFIX + name
        }.getOrNull()
    }

    fun delete(context: Context, id: String) {
        if (!id.startsWith(FILE_PREFIX)) return
        File(dir(context), id.removePrefix(FILE_PREFIX)).delete()
        cache.remove(id)
        _version.value++
    }

    fun clearCache() = cache.clear()
}
