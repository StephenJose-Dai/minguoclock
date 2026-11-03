package app.minguo.calendar

import android.icu.util.Calendar as IcuCalendar
import android.icu.util.ChineseCalendar
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 显示风格
 * @param fullEra true = 中華民國 / 中华民国，false = 民國 / 民国
 * @param chineseNumerals true = 一百一十五年（仅中文）
 */
data class DisplayStyle(val lang: Lang, val fullEra: Boolean, val chineseNumerals: Boolean) {
    val traditional: Boolean get() = lang == Lang.HANT
}

/** 民国纪年 + 农历 的格式化工具 */
object RocCalendar {
    private const val DIGITS = "〇一二三四五六七八九"
    private val WEEK_ZH = arrayOf("一", "二", "三", "四", "五", "六", "日")
    private val WEEK_EN = arrayOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val MONTH_EN = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private const val STEMS = "甲乙丙丁戊己庚辛壬癸"
    private const val BRANCHES = "子丑寅卯辰巳午未申酉戌亥"
    private const val ZODIAC_T = "鼠牛虎兔龍蛇馬羊猴雞狗豬"
    private const val ZODIAC_S = "鼠牛虎兔龙蛇马羊猴鸡狗猪"
    private val ZODIAC_EN = arrayOf("Rat", "Ox", "Tiger", "Rabbit", "Dragon", "Snake", "Horse", "Goat", "Monkey", "Rooster", "Dog", "Pig")
    private val STEMS_EN = arrayOf("Jia", "Yi", "Bing", "Ding", "Wu", "Ji", "Geng", "Xin", "Ren", "Gui")
    private val BRANCHES_EN = arrayOf("Zi", "Chou", "Yin", "Mao", "Chen", "Si", "Wu", "Wei", "Shen", "You", "Xu", "Hai")
    private val LUNAR_MONTH_T = arrayOf("正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "臘")
    private val LUNAR_MONTH_S = arrayOf("正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊")

    /** 1..999 转中文数字：115 -> 一百一十五，105 -> 一百零五 */
    fun chineseNumber(n: Int): String {
        if (n < 0) return "负" + chineseNumber(-n)
        if (n < 10) return DIGITS[n].toString()
        if (n < 20) return "十" + (if (n % 10 == 0) "" else DIGITS[n % 10].toString())
        if (n < 100) return DIGITS[n / 10].toString() + "十" + (if (n % 10 == 0) "" else DIGITS[n % 10].toString())
        if (n < 1000) {
            val rest = n % 100
            val head = DIGITS[n / 100].toString() + "百"
            return when {
                rest == 0 -> head
                rest < 10 -> head + "零" + DIGITS[rest]
                rest < 20 -> head + "一" + chineseNumber(rest)
                else -> head + chineseNumber(rest)
            }
        }
        return n.toString()
    }

    private fun num(n: Int, s: DisplayStyle) =
        if (s.chineseNumerals && s.lang != Lang.EN) chineseNumber(n) else n.toString()

    /** 民國 / 中華民國 / 民国 / 中华民国 / ROC / Republic of China */
    fun era(s: DisplayStyle): String = when (s.lang) {
        Lang.HANT -> if (s.fullEra) "中華民國" else "民國"
        Lang.HANS -> if (s.fullEra) "中华民国" else "民国"
        Lang.EN -> if (s.fullEra) "Republic of China" else "ROC"
    }

    /** 民國115年 / 中華民國115年 / ROC 115 */
    fun yearText(gregorianYear: Int, s: DisplayStyle): String {
        val roc = gregorianYear - 1911
        if (s.lang == Lang.EN) {
            return if (roc >= 1) "${era(s)} $roc" else "${1 - roc} yr before ${era(s)}"
        }
        return if (roc >= 1) era(s) + num(roc, s) + "年" else era(s) + "前" + num(1 - roc, s) + "年"
    }

    /** 民國115年11月2日 / ROC 115 · Nov 2 */
    fun dateLine(date: LocalDate, s: DisplayStyle): String =
        if (s.lang == Lang.EN) {
            yearText(date.year, s) + " · " + MONTH_EN[date.monthValue - 1] + " " + date.dayOfMonth
        } else {
            yearText(date.year, s) + num(date.monthValue, s) + "月" + num(date.dayOfMonth, s) + "日"
        }

    /** 民國115年11月 / ROC 115 · Nov（日历小组件用） */
    fun yearMonth(date: LocalDate, s: DisplayStyle): String =
        if (s.lang == Lang.EN) {
            yearText(date.year, s) + " · " + MONTH_EN[date.monthValue - 1]
        } else {
            yearText(date.year, s) + num(date.monthValue, s) + "月"
        }

    /** 农历短格式：九月廿四 / 9/24 */
    fun lunarShort(epochMillis: Long, s: DisplayStyle): String {
        val line = lunarLine(epochMillis, s)
        return if (s.lang == Lang.EN) line.substringBefore(" ·").removePrefix("Lunar ") else line.substringAfter(' ')
    }

    /** 星期一 / Monday */
    fun weekday(date: LocalDate, lang: Lang): String {
        val i = date.dayOfWeek.value - 1
        return if (lang == Lang.EN) WEEK_EN[i] else "星期" + WEEK_ZH[i]
    }

    /** 農曆丙午馬年 九月廿四 / Lunar 9/24 · Bing-Wu, Year of the Horse */
    fun lunarLine(epochMillis: Long, s: DisplayStyle): String {
        val cc = ChineseCalendar()
        cc.timeInMillis = epochMillis
        val cycle = cc.get(IcuCalendar.YEAR) - 1 // 0..59，0 = 甲子
        val month = cc.get(IcuCalendar.MONTH)     // 0..11
        val leap = cc.get(IcuCalendar.IS_LEAP_MONTH) == 1
        val day = cc.get(IcuCalendar.DAY_OF_MONTH)

        if (s.lang == Lang.EN) {
            val leapText = if (leap) " (leap)" else ""
            return "Lunar ${month + 1}/$day$leapText · ${STEMS_EN[cycle % 10]}-${BRANCHES_EN[cycle % 12]}, " +
                "Year of the ${ZODIAC_EN[cycle % 12]}"
        }
        val trad = s.traditional
        val ganzhi = STEMS[cycle % 10].toString() + BRANCHES[cycle % 12]
        val zodiac = (if (trad) ZODIAC_T else ZODIAC_S)[cycle % 12]
        val prefix = if (trad) "農曆" else "农历"
        val leapText = if (leap) (if (trad) "閏" else "闰") else ""
        val monthName = (if (trad) LUNAR_MONTH_T else LUNAR_MONTH_S)[month]
        return "$prefix$ganzhi${zodiac}年 $leapText${monthName}月${lunarDay(day)}"
    }

    private fun lunarDay(d: Int): String = when {
        d == 10 -> "初十"
        d == 20 -> "二十"
        d == 30 -> "三十"
        d < 10 -> "初" + DIGITS[d]
        d < 20 -> "十" + DIGITS[d - 10]
        else -> "廿" + DIGITS[d - 20]
    }

    /** 时间：返回 (时:分, 上午/下午/AM/PM 或 null) */
    fun hourMinute(t: LocalDateTime, is24: Boolean, lang: Lang): Pair<String, String?> {
        val m = t.minute.toString().padStart(2, '0')
        return if (is24) {
            (t.hour.toString().padStart(2, '0') + ":" + m) to null
        } else {
            val h = (t.hour + 11) % 12 + 1
            val am = t.hour < 12
            ("$h:$m") to (if (lang == Lang.EN) (if (am) "AM" else "PM") else (if (am) "上午" else "下午"))
        }
    }

    /** 相对日期：今天 / 明天 / 后天 / 星期X */
    fun relativeDay(date: LocalDate, today: LocalDate, lang: Lang): String =
        when (ChronoUnit.DAYS.between(today, date)) {
            0L -> tr("今天", "今天", "Today", lang)
            1L -> tr("明天", "明天", "Tomorrow", lang)
            2L -> tr("后天", "後天", weekday(date, lang), lang)
            else -> weekday(date, lang)
        }

    /** 下一个闹钟的描述：明天 07:00 */
    fun alarmText(triggerMillis: Long, now: LocalDateTime, is24: Boolean, lang: Lang): String {
        val t = Instant.ofEpochMilli(triggerMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
        val (hm, ampm) = hourMinute(t, is24, lang)
        val time = if (ampm == null) hm else if (lang == Lang.EN) "$hm $ampm" else "$ampm$hm"
        return relativeDay(t.toLocalDate(), now.toLocalDate(), lang) + " " + time
    }
}
