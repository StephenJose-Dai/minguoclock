package club.daishenghui.minguo

import android.icu.util.Calendar as IcuCalendar
import android.icu.util.ChineseCalendar
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 显示风格：繁/简，阿拉伯数字/中文数字 */
data class DisplayStyle(val traditional: Boolean, val chineseNumerals: Boolean)

/** 民国纪年 + 农历 的格式化工具 */
object RocCalendar {
    private const val DIGITS = "〇一二三四五六七八九"
    private val WEEK = arrayOf("一", "二", "三", "四", "五", "六", "日")
    private const val STEMS = "甲乙丙丁戊己庚辛壬癸"
    private const val BRANCHES = "子丑寅卯辰巳午未申酉戌亥"
    private const val ZODIAC_T = "鼠牛虎兔龍蛇馬羊猴雞狗豬"
    private const val ZODIAC_S = "鼠牛虎兔龙蛇马羊猴鸡狗猪"
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

    private fun num(n: Int, s: DisplayStyle) = if (s.chineseNumerals) chineseNumber(n) else n.toString()

    fun era(s: DisplayStyle) = if (s.traditional) "民國" else "民国"

    /** 民國115年 / 民國前1年 */
    fun yearText(gregorianYear: Int, s: DisplayStyle): String {
        val roc = gregorianYear - 1911
        return if (roc >= 1) era(s) + num(roc, s) + "年" else era(s) + "前" + num(1 - roc, s) + "年"
    }

    /** 民國115年11月2日 */
    fun dateLine(date: LocalDate, s: DisplayStyle): String =
        yearText(date.year, s) + num(date.monthValue, s) + "月" + num(date.dayOfMonth, s) + "日"

    /** 星期一 */
    fun weekday(date: LocalDate): String = "星期" + WEEK[date.dayOfWeek.value - 1]

    /** 農曆丙午馬年 九月廿四 */
    fun lunarLine(epochMillis: Long, s: DisplayStyle): String {
        val cc = ChineseCalendar()
        cc.timeInMillis = epochMillis
        val cycle = cc.get(IcuCalendar.YEAR) - 1 // 0..59，0 = 甲子
        val month = cc.get(IcuCalendar.MONTH)     // 0..11
        val leap = cc.get(IcuCalendar.IS_LEAP_MONTH) == 1
        val day = cc.get(IcuCalendar.DAY_OF_MONTH)

        val ganzhi = STEMS[cycle % 10].toString() + BRANCHES[cycle % 12]
        val zodiac = (if (s.traditional) ZODIAC_T else ZODIAC_S)[cycle % 12]
        val prefix = if (s.traditional) "農曆" else "农历"
        val leapText = if (leap) (if (s.traditional) "閏" else "闰") else ""
        val monthName = (if (s.traditional) LUNAR_MONTH_T else LUNAR_MONTH_S)[month]
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

    /** 时间：返回 (时:分, 上午/下午 或 null) */
    fun hourMinute(t: LocalDateTime, is24: Boolean): Pair<String, String?> {
        val m = t.minute.toString().padStart(2, '0')
        return if (is24) {
            (t.hour.toString().padStart(2, '0') + ":" + m) to null
        } else {
            val h = (t.hour + 11) % 12 + 1
            ("$h:$m") to (if (t.hour < 12) "上午" else "下午")
        }
    }

    /** 下一个闹钟的描述：明天 07:00 */
    fun alarmText(triggerMillis: Long, now: LocalDateTime, is24: Boolean, s: DisplayStyle): String {
        val zone = ZoneId.systemDefault()
        val t = Instant.ofEpochMilli(triggerMillis).atZone(zone).toLocalDateTime()
        val days = ChronoUnit.DAYS.between(now.toLocalDate(), t.toLocalDate())
        val dayText = when (days) {
            0L -> "今天"
            1L -> "明天"
            2L -> if (s.traditional) "後天" else "后天"
            else -> weekday(t.toLocalDate())
        }
        val (hm, ampm) = hourMinute(t, is24)
        return "$dayText ${ampm ?: ""}$hm"
    }
}
