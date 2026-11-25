package com.chahan.daily

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import java.time.LocalDate
import java.time.LocalDateTime

class SettingsActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        root = vbox(16)
        setContentView(ScrollView(this).apply { addView(root) })
        render()
    }

    override fun onPause() {
        super.onPause()
        Alarms.scheduleNext(this)
    }

    private fun render() {
        root.removeAllViews()
        val s = store.settings

        val head = hbox()
        head.addView(pill("← 返回") { finish() })
        head.addView(label("設定", 22f, true).lp().apply { setPadding(dpi(14), 0, 0, 0) })
        head.setPadding(0, dpi(8), 0, dpi(16))
        root.addView(head)

        // 時間
        val t = card()
        t.addView(label("作息時間", 16f, true))
        t.addView(label("其他提醒會依起床、睡覺時間自動排好。", 13f, colorId = R.color.text_sub).lp(top = 4))
        t.addView(timeRow("起床", s.wake) { s.wake = it })
        t.addView(timeRow("睡覺", s.bed) { s.bed = it })
        t.addView(timeRow("運動", s.exercise) { s.exercise = it })
        t.addView(timeRow("學習", s.learn) { s.learn = it })
        val win = s.sleepWindowHours
        t.addView(label(
            if (win < 7.5) "⚠️ 目前預留 %.1f 小時睡眠。入睡通常要一點時間，建議預留 7.5 小時以上，才睡得滿 7 小時。".format(win)
            else "預留 %.1f 小時睡眠 👍".format(win),
            13f, colorId = R.color.text_sub).lp(top = 10))
        root.addView(t)

        // 時間表一格幾分鐘
        val g = card()
        g.addView(label("🗓️ 時間表一格幾分鐘", 16f, true))
        g.addView(label("自訂行程的開始時間和長度都以「格」為單位調整。", 13f, colorId = R.color.text_sub).lp(top = 4))
        val gr = hbox()
        for (m in listOf(30, 40)) {
            gr.addView(pill("$m 分鐘", s.slotMinutes == m) { s.slotMinutes = m; render() }.lp(end = 8))
        }
        g.addView(gr.lp(top = 8))
        root.addView(g)

        // 喝水次數
        val w = card()
        w.addView(label("💧 一天提醒喝水幾次", 16f, true))
        val row = hbox()
        row.addView(pill("−") { s.waterCount = (s.waterCount - 1).coerceAtLeast(0); render() })
        row.addView(label("${s.waterCount} 次", 20f, true).lp().apply { setPadding(dpi(18), 0, dpi(18), 0) })
        row.addView(pill("＋") { s.waterCount = (s.waterCount + 1).coerceAtMost(8); render() })
        w.addView(row.lp(top = 8))
        root.addView(w)

        // 開關
        val k = card()
        k.addView(label("要哪些提醒", 16f, true))
        for (kind in Reminders.kinds) {
            val sw = SwitchCompat(this).apply {
                text = "${kind.emoji}  ${kind.title}"
                textSize = 15f
                setTextColor(col(R.color.text))
                isChecked = s.enabled(kind.key)
                setOnCheckedChangeListener { _, on -> s.setEnabled(kind.key, on) }
                setPadding(0, dpi(6), 0, dpi(6))
            }
            k.addView(sw.lp(ViewGroup.LayoutParams.MATCH_PARENT, top = 4))
        }
        root.addView(k)

        // 測試
        val x = card()
        x.addView(label("測試", 16f, true))
        x.addView(label("馬上跳一則提醒，確認通知有出現。", 13f, colorId = R.color.text_sub).lp(top = 4))
        x.addView(pill("送出測試通知", true) {
            val day = LocalDate.now()
            Alarms.show(this, Item("test", Reminders.kind("light"), day, LocalDateTime.now()))
        }.lp(top = 8))
        root.addView(x)
    }

    private fun timeRow(name: String, min: Int, set: (Int) -> Unit): LinearLayout {
        val row = hbox()
        row.setPadding(0, dpi(10), 0, 0)
        row.addView(label(name, 15f).lp(0, weight = 1f))
        row.addView(pill(fmt(min)) {
            TimePickerDialog(this, { _, h, m ->
                set(h * 60 + m)
                render()
            }, min / 60, min % 60, true).show()
        })
        return row
    }
}
