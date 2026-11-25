package com.chahan.daily

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** 設定：時間都用「一天中的第幾分鐘」。 */
class Settings(private val p: SharedPreferences) {
    var wake: Int
        get() = p.getInt("wake", 6 * 60 + 30)
        set(v) = p.edit().putInt("wake", v).apply()
    var bed: Int
        get() = p.getInt("bed", 22 * 60 + 30)
        set(v) = p.edit().putInt("bed", v).apply()
    var exercise: Int
        get() = p.getInt("exercise", 17 * 60 + 30)
        set(v) = p.edit().putInt("exercise", v).apply()
    var learn: Int
        get() = p.getInt("learn", 20 * 60)
        set(v) = p.edit().putInt("learn", v).apply()
    var waterCount: Int
        get() = p.getInt("waterCount", 3)
        set(v) = p.edit().putInt("waterCount", v).apply()

    fun enabled(kind: String) = p.getBoolean("on_$kind", true)
    fun setEnabled(kind: String, on: Boolean) = p.edit().putBoolean("on_$kind", on).apply()

    /** 內建提醒是否「固定」（固定的不能從時間表移除，要先解鎖）。預設全部固定。 */
    fun locked(kind: String) = p.getBoolean("lock_$kind", true)
    fun setLocked(kind: String, on: Boolean) = p.edit().putBoolean("lock_$kind", on).apply()

    /** 時間表一格幾分鐘：30 或 40 */
    var slotMinutes: Int
        get() = p.getInt("slot", 30)
        set(v) = p.edit().putInt("slot", v).apply()

    // ---------- 自訂行程 ----------
    fun customs(): MutableList<Custom> {
        val out = mutableListOf<Custom>()
        try {
            val arr = JSONArray(p.getString("customs", "[]"))
            for (i in 0 until arr.length()) out += Custom.from(arr.getJSONObject(i))
        } catch (e: Exception) { }
        return out
    }

    fun saveCustoms(list: List<Custom>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        p.edit().putString("customs", arr.toString()).apply()
    }

    fun putCustom(c: Custom) {
        val list = customs()
        val i = list.indexOfFirst { it.id == c.id }
        if (i >= 0) list[i] = c else list += c
        saveCustoms(list)
    }

    fun removeCustom(id: Long) = saveCustoms(customs().filter { it.id != id })

    /** 預定可睡的時數 */
    val sleepWindowHours: Double
        get() {
            var b = bed
            if (b <= wake) b += 1440
            return (wake + 1440 - b) / 60.0
        }
}

/**
 * 自訂行程。start＝一天中的第幾分鐘；slots＝佔幾格；
 * days＝星期遮罩（週一 bit0 … 週日 bit6），0 代表只有 date 那一天。
 */
data class Custom(
    val id: Long,
    var title: String,
    var note: String,
    var start: Int,
    var slots: Int,
    var days: Int,
    var date: String,
    var locked: Boolean,
    var notify: Boolean,
) {
    fun activeOn(day: LocalDate): Boolean =
        if (days == 0) date == day.toString() else (days and (1 shl (day.dayOfWeek.value - 1))) != 0

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("title", title).put("note", note).put("start", start)
        .put("slots", slots).put("days", days).put("date", date)
        .put("locked", locked).put("notify", notify)

    companion object {
        const val EVERY_DAY = 0b1111111
        const val WEEKDAYS = 0b0011111

        fun from(o: JSONObject) = Custom(
            o.getLong("id"), o.optString("title"), o.optString("note"), o.optInt("start"),
            o.optInt("slots", 1).coerceAtLeast(1), o.optInt("days", EVERY_DAY), o.optString("date"),
            o.optBoolean("locked", false), o.optBoolean("notify", true)
        )
    }
}

/** 每天的紀錄。 */
data class DayLog(
    var sleep: Double = -1.0,      // 昨晚睡了幾小時，-1 = 未填
    var energy: Int = 0,           // 1～5，0 = 未填
    var plan: String = "",
    val done: MutableSet<String> = mutableSetOf(),
)

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("daily", Context.MODE_PRIVATE)
    val settings = Settings(p)

    fun log(day: LocalDate): DayLog {
        val s = p.getString("log_$day", null) ?: return DayLog()
        return try {
            val o = JSONObject(s)
            val done = mutableSetOf<String>()
            val arr = o.optJSONArray("done") ?: JSONArray()
            for (i in 0 until arr.length()) done += arr.getString(i)
            DayLog(o.optDouble("sleep", -1.0), o.optInt("energy", 0), o.optString("plan", ""), done)
        } catch (e: Exception) {
            DayLog()
        }
    }

    fun save(day: LocalDate, l: DayLog) {
        val o = JSONObject()
            .put("sleep", l.sleep)
            .put("energy", l.energy)
            .put("plan", l.plan)
            .put("done", JSONArray(l.done.toList()))
        p.edit().putString("log_$day", o.toString()).apply()
    }

    fun update(day: LocalDate, f: (DayLog) -> Unit) {
        val l = log(day)
        f(l)
        save(day, l)
    }

    /** 最近 n 天（含今天）的紀錄，舊的在前。 */
    fun recent(today: LocalDate, n: Int): List<Pair<LocalDate, DayLog>> =
        (n - 1 downTo 0).map { today.minusDays(it.toLong()) }.map { it to log(it) }

    /** 最後一次填的睡眠時數，當作預設值 */
    fun lastSleep(today: LocalDate): Double {
        for (i in 1..14) {
            val v = log(today.minusDays(i.toLong())).sleep
            if (v >= 0) return v
        }
        return 7.0
    }

    var timerEnd: Long
        get() = p.getLong("timerEnd", 0)
        set(v) = p.edit().putLong("timerEnd", v).apply()

    var starterIndex: Int
        get() = p.getInt("starterIndex", 0)
        set(v) = p.edit().putInt("starterIndex", v).apply()
}

fun fmt(min: Int): String {
    val m = ((min % 1440) + 1440) % 1440
    return "%02d:%02d".format(m / 60, m % 60)
}
