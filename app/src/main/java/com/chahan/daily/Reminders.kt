package com.chahan.daily

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 一種提醒（只收中等以上證據的建議）。 */
data class Kind(
    val key: String,
    val emoji: String,
    val title: String,
    val body: String,
    val why: String,
)

/** 某一天某個時間點的一則提醒。key 例如 "water2"，kindKey 例如 "water"。 */
data class Item(
    val key: String,
    val kind: Kind,
    val day: LocalDate,   // 這一天的「起床日」
    val at: LocalDateTime,
    val custom: Custom? = null,   // 自訂行程才有
) {
    val notify: Boolean get() = custom?.notify ?: true
    val locked: Boolean get() = custom?.locked ?: false

    val millis: Long get() = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

object Reminders {

    val kinds: List<Kind> = listOf(
        Kind(
            "light", "☀️", "出門曬太陽",
            "到戶外或窗邊曬 10～30 分鐘日光",
            "早晨的日光會把身體時鐘往前調：晚上比較早想睡、早上比較好起床。戶外日光約 25,000 勒克斯，室內只有約 500。（中等證據）"
        ),
        Kind(
            "plan", "📝", "寫今天的「如果…就…」",
            "例如：如果午飯後坐下，我就複習 10 句英文",
            "「執行意圖」：事先想好在什麼情境做什麼事。94 個研究的統合分析顯示，對達成目標的效果 d = 0.65，對「開始動手」特別有效。（強證據）"
        ),
        Kind(
            "water", "💧", "喝一杯水",
            "起來倒杯水，順便站起來動一動",
            "33 個研究的統合分析：脫水約達體重 2% 時，注意力和複雜工作的表現明顯變差。（中等證據）"
        ),
        Kind(
            "caffeine", "☕", "咖啡因到此為止",
            "接下來不喝咖啡、濃茶、能量飲料",
            "睡前 6 小時喝 400 毫克咖啡因，仍讓睡眠少了超過 1 小時，而且本人察覺不到（Drake 2013）。咖啡因半衰期 2～10 小時，所以這裡提前到睡前 8 小時。（中等證據）"
        ),
        Kind(
            "exercise", "🏃", "運動時間",
            "去公園快走慢跑，輕鬆的強度就有效",
            "70 個隨機試驗的統合分析：規律運動讓活力上升、疲勞下降；低強度和中強度都有效。單次運動就能提升注意力和執行功能。（強證據）"
        ),
        Kind(
            "learn", "📚", "學習時間",
            "先自我測驗，再看答案；把內容分散在不同天練",
            "分散練習、交錯練習、自我測驗：當下覺得比較吃力，長期卻記得更牢（Bjork 的「有益的困難」）。學起來吃力，常代表方法有效。（強證據）"
        ),
        Kind(
            "checkin", "⭐", "幫今天的精神打分",
            "打開 App，按 1～5 分",
            "每天記下睡了多久和精神好壞，幾週後就能看到自己睡多少時狀態最好。"
        ),
        Kind(
            "winddown", "📵", "放下手機，準備睡覺",
            "30 分鐘後上床，現在開始收尾",
            "睡眠不足時，人會比較不願意付出腦力，情緒也更焦慮（154 個研究的統合分析）。提早收尾，才睡得滿 7 小時。（強證據）"
        ),
        Kind(
            "bed", "🌙", "上床睡覺",
            "關燈睡覺，明天會更有勁",
            "美國睡眠醫學會建議成人每晚至少睡 7 小時；睡眠時間越不規律，健康風險越高。（強證據）"
        ),
    )

    fun kind(key: String): Kind = kinds.first { it.key == key }

    /** 起床日 day 的所有提醒（已過濾掉關閉的），依時間排序。 */
    fun itemsFor(day: LocalDate, s: Settings): List<Item> {
        val start = day.atStartOfDay()
        val wake = s.wake
        var bed = s.bed
        if (bed <= wake) bed += 1440            // 睡覺時間過了午夜
        fun at(min: Int) = start.plusMinutes(min.toLong())
        // 運動、學習時間若早於起床時間，視為隔天凌晨
        fun inDay(min: Int) = if (min < wake - 180) min + 1440 else min

        val list = mutableListOf<Item>()
        fun add(key: String, kindKey: String, min: Int) {
            if (s.enabled(kindKey)) list += Item(key, kind(kindKey), day, at(min))
        }
        add("light", "light", wake + 10)
        add("plan", "plan", wake + 30)
        val n = s.waterCount
        if (n > 0) {
            val from = wake + 150
            val to = bed - 180
            for (i in 0 until n) {
                val m = if (n == 1) (from + to) / 2 else from + (to - from) * i / (n - 1)
                add("water$i", "water", m)
            }
        }
        add("caffeine", "caffeine", bed - 480)
        add("exercise", "exercise", inDay(s.exercise))
        add("learn", "learn", inDay(s.learn))
        add("checkin", "checkin", bed - 60)
        add("winddown", "winddown", bed - 30)
        add("bed", "bed", bed)

        // 自訂行程
        for (c in s.customs()) {
            if (!c.activeOn(day)) continue
            val k = Kind("c_${c.id}", "📌", c.title.ifBlank { "（未命名）" },
                c.note.ifBlank { "照你排好的時間開始" }, "這是你自己排進時間表的行程。")
            list += Item("c_${c.id}", k, day, at(inDay(c.start)), c)
        }
        return list.sortedBy { it.at }
    }

    /** 現在屬於哪一個「起床日」：從起床前 3 小時算起的 24 小時。 */
    fun currentDay(s: Settings, now: LocalDateTime = LocalDateTime.now()): LocalDate {
        val dayStart = now.toLocalDate().atStartOfDay().plusMinutes((s.wake - 180).toLong())
        return if (now.isBefore(dayStart)) now.toLocalDate().minusDays(1) else now.toLocalDate()
    }

    /** 下一批要響的提醒（同一時間可能有好幾則）。 */
    fun nextItems(s: Settings, afterMillis: Long): List<Item> {
        val today = LocalDate.now()
        val all = (-1..2).flatMap { itemsFor(today.plusDays(it.toLong()), s) }
            .filter { it.millis > afterMillis && it.notify }
            .sortedBy { it.millis }
        if (all.isEmpty()) return emptyList()
        val first = all.first().millis
        return all.filter { it.millis == first }
    }

    /** 「提不起勁」時的 5 分鐘小事。 */
    val starters = listOf(
        "只做 5 分鐘，時間到了就可以停。通常開始之後就停不下來。",
        "把要做的事拆成「第一個最小動作」，現在只做那一步。",
        "出門走 5 分鐘、曬曬太陽，回來再決定要不要做。",
        "喝一杯水，站起來伸展 2 分鐘。",
        "打開英文筆記本，只聽 3 句就好。",
        "把桌面清出一塊空間，把要用的東西擺好。",
        "傳個訊息給一位熟人，聊兩句。",
        "寫下：「如果＿＿＿，我就＿＿＿。」",
        "把要做的事大聲說出來：「我現在要做＿＿＿的第一步。」",
    )
}
