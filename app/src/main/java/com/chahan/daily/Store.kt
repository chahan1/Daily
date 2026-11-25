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

    /** 預定可睡的時數 */
    val sleepWindowHours: Double
        get() {
            var b = bed
            if (b <= wake) b += 1440
            return (wake + 1440 - b) / 60.0
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
