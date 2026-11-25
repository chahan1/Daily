package com.chahan.daily

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.PowerManager
import android.provider.Settings as SysSettings
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var root: LinearLayout
    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        Alarms.ensureChannel(this)
        root = vbox(16)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }
        setContentView(scroll)
        if (Build.VERSION.SDK_INT >= 33 && !Alarms.canNotify(this)) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        Alarms.scheduleNext(this)
        render()
    }

    override fun onPause() {
        super.onPause()
        timer?.cancel()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        render()
    }

    private fun today(): LocalDate = Reminders.currentDay(store.settings)

    private fun render() {
        timer?.cancel()
        root.removeAllViews()
        val day = today()
        val log = store.log(day)

        // 標題列
        val head = hbox()
        val titleBox = vbox()
        titleBox.addView(label("每日動力", 26f, true))
        titleBox.addView(label(day.format(DateTimeFormatter.ofPattern("M 月 d 日 EEEE", Locale.TAIWAN)),
            14f, colorId = R.color.text_sub))
        head.addView(titleBox.lp(0, weight = 1f))
        head.addView(pill("設定") { startActivity(Intent(this, SettingsActivity::class.java)) })
        head.setPadding(0, dpi(8), 0, dpi(16))
        root.addView(head)

        warnings()
        morningCard(day, log)
        planCard(day, log)
        starterCard(log)
        timelineCard(day, log)
        statsCard(day)
    }

    // ---------- 權限提醒 ----------
    private fun warnings() {
        val pm = getSystemService(PowerManager::class.java)
        val notif = Alarms.canNotify(this)
        val battery = pm.isIgnoringBatteryOptimizations(packageName)
        if (notif && battery) return
        val c = card()
        c.background = rounded(col(R.color.card), 18f, col(R.color.brand))
        c.addView(label("⚠️ 還差一步，提醒才會準時", 16f, true))
        if (!notif) {
            c.addView(label("通知權限還沒開。", 14f, colorId = R.color.text_sub).lp(top = 6))
            c.addView(pill("開啟通知", true) {
                startActivity(Intent(SysSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(SysSettings.EXTRA_APP_PACKAGE, packageName))
            }.lp(top = 8))
        }
        if (!battery) {
            c.addView(label("手機的省電功能可能會擋掉提醒。小米手機另外要到「應用程式資訊」把省電策略設成「無限制」、開啟「自動啟動」。",
                14f, colorId = R.color.text_sub).lp(top = 10))
            val row = hbox()
            row.addView(pill("取消省電限制", true) {
                try {
                    startActivity(Intent(SysSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")))
                } catch (e: Exception) { }
            }.lp(end = 8))
            row.addView(pill("應用程式資訊") {
                startActivity(Intent(SysSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")))
            })
            c.addView(row.lp(top = 8))
        }
        root.addView(c)
    }

    // ---------- 早上紀錄：睡眠＋精神 ----------
    private fun morningCard(day: LocalDate, log: DayLog) {
        val c = card()
        c.addView(label("昨晚睡了幾小時", 16f, true))
        var sleep = if (log.sleep >= 0) log.sleep else store.lastSleep(day)
        val value = label("", 28f, true).apply { gravity = Gravity.CENTER; minWidth = dpi(110) }
        val hint = label("", 13f, colorId = R.color.text_sub)
        fun show(saved: Boolean) {
            value.text = "%.1f".format(sleep)
            hint.text = when {
                !saved -> "按 − ＋ 調整就會記下來"
                sleep < 7 -> "低於 7 小時。研究建議成人每晚至少睡 7 小時，今天早點睡。"
                else -> "睡滿 7 小時 👍"
            }
        }
        val row = hbox()
        row.addView(pill("−") {
            sleep = (sleep - 0.5).coerceAtLeast(0.0)
            store.update(day) { it.sleep = sleep }; show(true)
        })
        row.addView(value)
        row.addView(pill("＋") {
            sleep = (sleep + 0.5).coerceAtMost(14.0)
            store.update(day) { it.sleep = sleep }; show(true)
        })
        row.addView(label("小時", 14f, colorId = R.color.text_sub).lp().apply { setPadding(dpi(10), 0, 0, 0) })
        c.addView(row.lp(top = 8))
        c.addView(hint.lp(top = 6))
        show(log.sleep >= 0)

        c.addView(label("今天的精神", 16f, true).lp(top = 16))
        val eRow = hbox()
        val names = listOf("很差", "差", "普通", "好", "很好")
        for (i in 1..5) {
            val b = TextView(this).apply {
                text = "$i\n${names[i - 1]}"
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dpi(8), 0, dpi(8))
                val on = log.energy == i
                setTextColor(col(if (on) R.color.white else R.color.text))
                background = rounded(col(if (on) R.color.brand else R.color.soft), 12f)
                setOnClickListener {
                    store.update(day) { it.energy = i; it.done += "checkin" }
                    render()
                }
            }
            eRow.addView(b.lp(0, weight = 1f, end = if (i < 5) 6 else 0))
        }
        c.addView(eRow.lp(ViewGroup.LayoutParams.MATCH_PARENT, top = 8))
        root.addView(c)
    }

    // ---------- 如果…就… ----------
    private fun planCard(day: LocalDate, log: DayLog) {
        val c = card()
        c.addView(label("📝 今天的「如果…就…」", 16f, true))
        c.addView(label("事先想好「在什麼情境，做哪件事」，比只說「我要努力」更容易真的開始。",
            13f, colorId = R.color.text_sub).lp(top = 4))
        val et = EditText(this).apply {
            hint = "如果午飯後坐下，我就複習 10 句英文"
            setText(log.plan)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(col(R.color.text))
            setHintTextColor(col(R.color.text_sub))
            background = rounded(col(R.color.soft), 12f)
            setPadding(dpi(12), dpi(10), dpi(12), dpi(10))
            minLines = 2
            gravity = Gravity.TOP
        }
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val t = s?.toString() ?: ""
                store.update(day) {
                    it.plan = t
                    if (t.isNotBlank()) it.done += "plan"
                }
            }
        })
        c.addView(et.lp(ViewGroup.LayoutParams.MATCH_PARENT, top = 10))
        root.addView(c)
    }

    // ---------- 提不起勁 ----------
    private fun starterCard(log: DayLog) {
        val c = card()
        c.addView(label("😮‍💨 提不起勁？", 16f, true))
        c.addView(label("先動手，動力通常隨後才來。挑一件 5 分鐘的小事開始。",
            13f, colorId = R.color.text_sub).lp(top = 4))
        val idea = label("", 16f).apply {
            background = rounded(col(R.color.soft), 12f)
            setPadding(dpi(14), dpi(12), dpi(14), dpi(12))
        }
        val list = buildList {
            if (log.plan.isNotBlank()) add("照你今天的計畫：${log.plan}")
            addAll(Reminders.starters)
        }
        fun showIdea() { idea.text = list[store.starterIndex % list.size] }
        showIdea()
        c.addView(idea.lp(ViewGroup.LayoutParams.MATCH_PARENT, top = 10))

        val countdown = label("", 30f, true).apply { gravity = Gravity.CENTER }
        val row = hbox()
        val startBtn = pill("開始 5 分鐘", true) {}
        val other = pill("換一個") { store.starterIndex = store.starterIndex + 1; showIdea() }
        row.addView(startBtn.lp(0, weight = 1f, end = 8))
        row.addView(other)
        c.addView(row.lp(ViewGroup.LayoutParams.MATCH_PARENT, top = 10))
        c.addView(countdown.lp(ViewGroup.LayoutParams.MATCH_PARENT, top = 4))

        fun tick() {
            timer?.cancel()
            val left = store.timerEnd - System.currentTimeMillis()
            if (left <= 0) {
                countdown.text = ""
                startBtn.text = "開始 5 分鐘"
                return
            }
            startBtn.text = "停止"
            timer = object : CountDownTimer(left, 500) {
                override fun onTick(ms: Long) {
                    val s = (ms / 1000).toInt()
                    countdown.text = "%d:%02d".format(s / 60, s % 60)
                }
                override fun onFinish() {
                    countdown.text = "5 分鐘到了！想繼續就繼續 💪"
                    startBtn.text = "再 5 分鐘"
                }
            }.start()
        }
        startBtn.setOnClickListener {
            if (store.timerEnd > System.currentTimeMillis()) {
                Alarms.cancelTimer(this); timer?.cancel()
                countdown.text = ""; startBtn.text = "開始 5 分鐘"
            } else {
                Alarms.startTimer(this, 5); tick()
            }
        }
        tick()
        root.addView(c)
    }

    // ---------- 今天的提醒 ----------
    private fun timelineCard(day: LocalDate, log: DayLog) {
        val c = card()
        c.addView(label("今天的提醒", 16f, true))
        c.addView(label("點項目看「為什麼」，點右邊圓圈標記完成。", 13f, colorId = R.color.text_sub).lp(top = 4))
        val items = Reminders.itemsFor(day, store.settings)
        val now = LocalDateTime.now()
        val nextKey = items.firstOrNull { it.at.isAfter(now) && it.key !in log.done }?.key
        if (items.isEmpty()) c.addView(label("所有提醒都關掉了，可以到「設定」打開。", 14f).lp(top = 10))
        for (item in items) {
            val done = item.key in log.done
            val past = item.at.isBefore(now)
            val row = hbox()
            row.setPadding(dpi(10), dpi(10), dpi(6), dpi(10))
            if (item.key == nextKey) row.background = rounded(col(R.color.soft), 12f)
            val time = label(item.at.format(DateTimeFormatter.ofPattern("HH:mm")), 14f,
                colorId = R.color.text_sub).apply { minWidth = dpi(52) }
            val title = label("${item.kind.emoji}  ${item.kind.title}", 15f,
                colorId = if (done || (past && item.key != nextKey)) R.color.text_sub else R.color.text)
            if (item.key == nextKey) title.typeface = android.graphics.Typeface.DEFAULT_BOLD
            val check = TextView(this).apply {
                text = if (done) "✓" else ""
                gravity = Gravity.CENTER
                setTextColor(col(R.color.white))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                background = if (done) rounded(col(R.color.done), 14f)
                else rounded(col(R.color.card), 14f, col(R.color.text_sub))
                setOnClickListener { _ ->
                    store.update(day) { l ->
                        if (done) l.done.remove(item.key) else l.done.add(item.key)
                    }
                    render()
                }
            }
            row.addView(time)
            row.addView(title.lp(0, weight = 1f))
            row.addView(check.lp(dpi(28), dpi(28)))
            row.setOnClickListener { _ -> showWhy(item) }
            c.addView(row.lp(ViewGroup.LayoutParams.MATCH_PARENT, top = 2))
        }
        root.addView(c)
    }

    private fun showWhy(item: Item) {
        val b = AlertDialog.Builder(this)
            .setTitle("${item.kind.emoji} ${item.kind.title}")
            .setMessage("${item.kind.body}\n\n為什麼：\n${item.kind.why}")
            .setPositiveButton("知道了", null)
        when (item.kind.key) {
            "learn" -> b.setNeutralButton("打開英文筆記本") { _, _ ->
                startActivity(Alarms.engIntent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            "exercise" -> Alarms.walkIntent(this)?.let { i ->
                b.setNeutralButton("打開運動 App") { _, _ -> startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
        b.show()
    }

    // ---------- 睡眠與精神的關係 ----------
    private fun statsCard(day: LocalDate) {
        val c = card()
        c.addView(label("睡眠與精神", 16f, true))
        c.addView(label("長條＝睡了幾小時，數字＝當天精神分數（最近 14 天）", 13f,
            colorId = R.color.text_sub).lp(top = 4))
        val chart = SleepChart(this)
        chart.data = store.recent(day, 14).map { (d, l) ->
            Triple(d.dayOfMonth.toString(), l.sleep, l.energy)
        }
        c.addView(chart.lp(ViewGroup.LayoutParams.MATCH_PARENT, top = 8))

        val month = store.recent(day, 30).map { it.second }.filter { it.sleep >= 0 && it.energy > 0 }
        val enough = month.filter { it.sleep >= 7 }
        val short = month.filter { it.sleep < 7 }
        val text = if (enough.isEmpty() || short.isEmpty()) {
            "累積幾天「睡眠時數」和「精神分數」後，這裡會比較睡滿 7 小時和不到 7 小時的日子，精神平均差多少。（目前有 ${month.size} 天完整紀錄）"
        } else {
            val a = enough.map { it.energy }.average()
            val b = short.map { it.energy }.average()
            "最近 30 天：\n睡滿 7 小時的日子，精神平均 %.1f 分（%d 天）\n不到 7 小時的日子，精神平均 %.1f 分（%d 天）"
                .format(a, enough.size, b, short.size)
        }
        c.addView(label(text, 14f).lp(top = 8))
        root.addView(c)
    }
}
