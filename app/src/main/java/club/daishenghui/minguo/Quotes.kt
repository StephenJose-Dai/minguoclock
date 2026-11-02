package club.daishenghui.minguo

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class Quote(val text: String, val author: String)

/**
 * 锁屏名言：
 * - 内置名言洗牌轮换，全部出现过一遍之前不会重复；
 * - 可选“一言”在线获取（https://hitokoto.cn），记录最近 300 条避免重复；
 * - 繁体界面自动转成繁体（Android 10+）。
 */
object Quotes {
    private const val TAG = "Quotes"
    private const val INTERVAL_MS = 30 * 60 * 1000L
    private val mutex = Mutex()

    private val _state = MutableStateFlow<Quote?>(null)
    val state: StateFlow<Quote?> get() = _state
    private var loaded = false

    /** 内置名言（简体），格式：内容|出处 */
    private val BUILTIN = listOf(
        "愿你出走半生，归来仍是少年。|网络流行语",
        "学而不思则罔，思而不学则殆。|《论语》",
        "知之者不如好之者，好之者不如乐之者。|《论语》",
        "三人行，必有我师焉。|《论语》",
        "己所不欲，勿施于人。|《论语》",
        "逝者如斯夫，不舍昼夜。|《论语》",
        "士不可以不弘毅，任重而道远。|《论语》",
        "岁寒，然后知松柏之后凋也。|《论语》",
        "敏而好学，不耻下问。|《论语》",
        "温故而知新，可以为师矣。|《论语》",
        "言必信，行必果。|《论语》",
        "君子坦荡荡，小人长戚戚。|《论语》",
        "欲速则不达，见小利则大事不成。|《论语》",
        "吾日三省吾身。|《论语》",
        "君子和而不同。|《论语》",
        "工欲善其事，必先利其器。|《论语》",
        "人无远虑，必有近忧。|《论语》",
        "千里之行，始于足下。|老子",
        "知人者智，自知者明。|老子",
        "上善若水，水善利万物而不争。|老子",
        "天下难事，必作于易；天下大事，必作于细。|老子",
        "合抱之木，生于毫末；九层之台，起于累土。|老子",
        "天行健，君子以自强不息。|《周易》",
        "地势坤，君子以厚德载物。|《周易》",
        "路漫漫其修远兮，吾将上下而求索。|屈原",
        "亦余心之所善兮，虽九死其犹未悔。|屈原",
        "不积跬步，无以至千里；不积小流，无以成江海。|荀子",
        "锲而不舍，金石可镂。|荀子",
        "青，取之于蓝，而青于蓝。|荀子",
        "生于忧患，死于安乐。|孟子",
        "穷则独善其身，达则兼济天下。|孟子",
        "富贵不能淫，贫贱不能移，威武不能屈。|孟子",
        "吾生也有涯，而知也无涯。|庄子",
        "君子之交淡若水。|庄子",
        "人生天地之间，若白驹过隙，忽然而已。|庄子",
        "天生我材必有用，千金散尽还复来。|李白",
        "长风破浪会有时，直挂云帆济沧海。|李白",
        "人生得意须尽欢，莫使金樽空对月。|李白",
        "大鹏一日同风起，扶摇直上九万里。|李白",
        "浮生若梦，为欢几何？|李白",
        "会当凌绝顶，一览众山小。|杜甫",
        "读书破万卷，下笔如有神。|杜甫",
        "海内存知己，天涯若比邻。|王勃",
        "落霞与孤鹜齐飞，秋水共长天一色。|王勃",
        "欲穷千里目，更上一层楼。|王之涣",
        "春风得意马蹄疾，一日看尽长安花。|孟郊",
        "沉舟侧畔千帆过，病树前头万木春。|刘禹锡",
        "莫道桑榆晚，为霞尚满天。|刘禹锡",
        "野火烧不尽，春风吹又生。|白居易",
        "行到水穷处，坐看云起时。|王维",
        "莫愁前路无知己，天下谁人不识君。|高适",
        "黄沙百战穿金甲，不破楼兰终不还。|王昌龄",
        "春蚕到死丝方尽，蜡炬成灰泪始干。|李商隐",
        "夕阳无限好，只是近黄昏。|李商隐",
        "曾经沧海难为水，除却巫山不是云。|元稹",
        "劝君莫惜金缕衣，劝君惜取少年时。|杜秋娘",
        "花开堪折直须折，莫待无花空折枝。|杜秋娘",
        "醉后不知天在水，满船清梦压星河。|唐温如",
        "时人不识凌云木，直待凌云始道高。|杜荀鹤",
        "山重水复疑无路，柳暗花明又一村。|陆游",
        "纸上得来终觉浅，绝知此事要躬行。|陆游",
        "问渠那得清如许？为有源头活水来。|朱熹",
        "等闲识得东风面，万紫千红总是春。|朱熹",
        "人生自古谁无死？留取丹心照汗青。|文天祥",
        "但愿人长久，千里共婵娟。|苏轼",
        "竹杖芒鞋轻胜马，谁怕？一蓑烟雨任平生。|苏轼",
        "回首向来萧瑟处，归去，也无风雨也无晴。|苏轼",
        "莫听穿林打叶声，何妨吟啸且徐行。|苏轼",
        "人生如逆旅，我亦是行人。|苏轼",
        "腹有诗书气自华。|苏轼",
        "博观而约取，厚积而薄发。|苏轼",
        "人间有味是清欢。|苏轼",
        "此心安处是吾乡。|苏轼",
        "且将新火试新茶，诗酒趁年华。|苏轼",
        "不畏浮云遮望眼，自缘身在最高层。|王安石",
        "先天下之忧而忧，后天下之乐而乐。|范仲淹",
        "不以物喜，不以己悲。|范仲淹",
        "众里寻他千百度，蓦然回首，那人却在，灯火阑珊处。|辛弃疾",
        "我见青山多妩媚，料青山见我应如是。|辛弃疾",
        "青山遮不住，毕竟东流去。|辛弃疾",
        "莫等闲，白了少年头，空悲切。|岳飞",
        "三十功名尘与土，八千里路云和月。|岳飞",
        "宝剑锋从磨砺出，梅花香自苦寒来。|《警世贤文》",
        "业精于勤，荒于嬉；行成于思，毁于随。|韩愈",
        "人生若只如初见。|纳兰性德",
        "采菊东篱下，悠然见南山。|陶渊明",
        "盛年不重来，一日难再晨。及时当勉励，岁月不待人。|陶渊明",
        "少壮不努力，老大徒伤悲。|《长歌行》",
        "落红不是无情物，化作春泥更护花。|龚自珍",
        "我劝天公重抖擞，不拘一格降人才。|龚自珍",
        "千磨万击还坚劲，任尔东西南北风。|郑燮",
        "粉骨碎身浑不怕，要留清白在人间。|于谦",
        "苟利国家生死以，岂因祸福避趋之。|林则徐",
        "海纳百川，有容乃大；壁立千仞，无欲则刚。|林则徐",
        "非淡泊无以明志，非宁静无以致远。|诸葛亮",
        "勿以恶小而为之，勿以善小而不为。|刘备",
        "老骥伏枥，志在千里；烈士暮年，壮心不已。|曹操",
        "世事洞明皆学问，人情练达即文章。|曹雪芹",
        "万物静观皆自得，四时佳兴与人同。|程颢",
        "宠辱不惊，看庭前花开花落；去留无意，望天上云卷云舒。|《幽窗小记》",
        "学如逆水行舟，不进则退。|古谚",
        "路遥知马力，日久见人心。|古谚",
        "精诚所至，金石为开。|《后汉书》",
        "有志者事竟成。|《后汉书》",
        "失之东隅，收之桑榆。|《后汉书》",
        "桃李不言，下自成蹊。|《史记》",
        "燕雀安知鸿鹄之志哉。|陈胜",
        "人固有一死，或重于泰山，或轻于鸿毛。|司马迁",
        "不经一番寒彻骨，怎得梅花扑鼻香。|黄檗禅师",
        "但行好事，莫问前程。|古语",
        "山有木兮木有枝，心悦君兮君不知。|《越人歌》",
        "世界以痛吻我，要我报之以歌。|泰戈尔",
        "生如夏花之绚烂，死如秋叶之静美。|泰戈尔",
        "天空没有留下翅膀的痕迹，但我已经飞过。|泰戈尔",
        "心有猛虎，细嗅蔷薇。|萨松",
        "知识就是力量。|培根",
        "我思故我在。|笛卡尔",
        "未经审视的人生不值得过。|苏格拉底",
        "人不能两次踏进同一条河流。|赫拉克利特",
        "给我一个支点，我就能撬起地球。|阿基米德",
        "想象力比知识更重要。|爱因斯坦",
        "如果冬天来了，春天还会远吗？|雪莱",
        "世上只有一种英雄主义，就是在认清生活的真相后依然热爱生活。|罗曼·罗兰",
        "我们都在阴沟里，但仍有人仰望星空。|王尔德",
        "凡是过往，皆为序章。|莎士比亚",
        "人生而自由，却无往不在枷锁之中。|卢梭",
        "读一本好书，就是和许多高尚的人谈话。|歌德",
    ).map { val (t, a) = it.split('|', limit = 2); Quote(t, a) }

    val builtinCount: Int get() = BUILTIN.size

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        _state.value = Prefs(context).quoteCurrent?.let { decode(it) } ?: run {
            val q = BUILTIN.first()
            save(context, q)
            q
        }
    }

    /**
     * 到时间就换一句。
     * @param screenOn 是否由“亮屏”触发（间隔设为“每次亮屏”时只在亮屏时换）
     */
    suspend fun maybeRotate(context: Context, screenOn: Boolean) {
        init(context)
        // 固定：每 30 分钟换一句（内置 + 一言混合）
        if (System.currentTimeMillis() - Prefs(context).quoteChangedAt >= INTERVAL_MS) next(context)
    }

    /** 立即换下一句 */
    suspend fun next(context: Context) = mutex.withLock {
        init(context)
        val p = Prefs(context)
        val online = Math.random() < 0.5
        val q = (if (online) fetchHitokoto(context) else null) ?: nextBuiltin(context)
        save(context, q)
        Widgets.updateAll(context)
    }

    private fun nextBuiltin(context: Context): Quote {
        val p = Prefs(context)
        var deck = p.quoteDeck.split(',').mapNotNull { it.toIntOrNull() }.filter { it in BUILTIN.indices }
        if (deck.isEmpty()) {
            val currentIndex = _state.value?.let { BUILTIN.indexOf(it) } ?: -1
            deck = BUILTIN.indices.shuffled().let { d ->
                // 新一轮的第一句不要和刚才那句一样
                if (d.first() == currentIndex && d.size > 1) d.drop(1) + d.first() else d
            }
        }
        val idx = deck.first()
        p.quoteDeck = deck.drop(1).joinToString(",")
        return BUILTIN[idx]
    }

    private suspend fun fetchHitokoto(context: Context): Quote? = withContext(Dispatchers.IO) {
        val p = Prefs(context)
        val seen = p.quoteSeen.split(',').filter { it.isNotEmpty() }.toMutableList()
        // 按 IP 选国内 / 海外线路，失败换另一条
        val preferred = Region.hitokotoHost(context)
        val hosts = listOf(preferred) + listOf("https://v1.hitokoto.cn", "https://international.v1.hitokoto.cn").filter { it != preferred }
        var hostIndex = 0
        repeat(4) {
            try {
                val host = hosts[hostIndex.coerceAtMost(hosts.lastIndex)]
                val o = JSONObject(Net.get("$host/?c=d&c=i&c=k&encode=json&charset=utf-8", timeoutMs = 5_000))
                val text = o.optString("hitokoto").trim()
                if (text.isEmpty()) return@repeat
                val key = text.hashCode().toString(16)
                if (key in seen) return@repeat
                val who = o.optString("from_who").takeIf { it.isNotBlank() && it != "null" }
                val from = o.optString("from").takeIf { it.isNotBlank() && it != "null" }
                val author = when {
                    who != null && from != null -> "$who《$from》"
                    who != null -> who
                    from != null -> "《$from》"
                    else -> ""
                }
                seen += key
                p.quoteSeen = seen.takeLast(300).joinToString(",")
                return@withContext Quote(text, author)
            } catch (e: Exception) {
                Log.i(TAG, "一言获取失败：${e.message}")
                hostIndex++
                if (hostIndex > hosts.lastIndex) return@withContext null
            }
        }
        null
    }

    private fun save(context: Context, q: Quote) {
        val p = Prefs(context)
        p.quoteCurrent = q.text + "\u0001" + q.author
        p.quoteChangedAt = System.currentTimeMillis()
        _state.value = q
    }

    private fun decode(s: String): Quote? = s.split('\u0001', limit = 2).takeIf { it.size == 2 }?.let { Quote(it[0], it[1]) }

    /** 按界面语言显示：繁体转换（Android 10+） */
    fun display(q: Quote, lang: Lang): Quote {
        if (lang != Lang.HANT || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return q
        return Quote(toHant(q.text), toHant(q.author))
    }

    private val hantCache = HashMap<String, String>()

    fun toHant(s: String): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || s.isEmpty()) return s
        return hantCache.getOrPut(s) {
            runCatching { android.icu.text.Transliterator.getInstance("Hans-Hant").transliterate(s) }.getOrDefault(s)
        }
    }

    fun clearOnlineHistory(context: Context) {
        Prefs(context).quoteSeen = ""
    }

    fun reset() {
        _state.value = null
        loaded = false
    }
}
