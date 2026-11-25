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
import androidx.appcompat.widget.SwitchCompat
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
        val s = store.settings
        val slot = s.slotMinutes
        val c = card()
        c.addView(label("今天的時間表", 16f, true))
        c.addView(label("🔒 是固定的項目。空格點「＋」加入行程，點行程可修改、延長、縮短或刪除。每格 $slot 分鐘。",
            13f, colorId = R.color.text_sub).lp(top = 4))

        val items = Reminders.itemsFor(day, s)
        val now = LocalDateTime.now()
        val start0 = day.atStartOfDay()
        fun rel(i: Item) = java.time.Duration.between(start0, i.at).toMinutes().toInt()
        val wake = s.wake
        var bed = s.bed
        if (bed <= wake) bed += 1440
        val builtins = items.filter { it.custom == null }
        val customs = items.filter { it.custom != null }
        val nextKey = items.firstOrNull { it.at.isAfter(now) && it.key !in log.done }?.key
        val n = (bed - wake + slot - 1) / slot

        for (i in 0..n) {
            val a = wake + i * slot
            val b = if (i == n) Int.MAX_VALUE else a + slot
            val row = hbox()
            row.gravity = Gravity.TOP
            val nowIn = now.isAfter(start0.plusMinutes(a.toLong())) && now.isBefore(start0.plusMinutes(minOf(b, a + slot).toLong()))
            row.addView(label(fmt(a), 13f, nowIn, if (nowIn) R.color.brand else R.color.text_sub)
                .apply { minWidth = dpi(50); setPadding(0, dpi(9), 0, 0) })
            val cell = vbox()
            // 這一格內的內建提醒
            builtins.filter { rel(it) in a until b }.forEach { cell.addView(builtinRow(day, it, log, it.key == nextKey)) }
            // 從這一格開始的自訂行程
            val starting = customs.filter { rel(it) in a until b }
            starting.forEach { cell.addView(customRow(day, it, log, slot, it.key == nextKey)) }
            // 被前面行程延續佔用
            val covering = customs.filter { val r = rel(it); r < a && r + it.custom!!.slots * slot > a }
            covering.forEach { it2 ->
                cell.addView(label("↳ ${it2.kind.title}", 13f, colorId = R.color.text_sub).apply {
                    setPadding(dpi(10), dpi(8), 0, dpi(8))
                    setOnClickListener { editCustom(day, it2.custom, a) }
                })
            }
            if (i < n && starting.isEmpty() && covering.isEmpty()) {
                cell.addView(label("＋", 16f, colorId = R.color.text_sub).apply {
                    setPadding(dpi(10), dpi(6), 0, dpi(6))
                })
                row.setOnClickListener { editCustom(day, null, a) }
            }
            row.addView(cell.lp(0, weight = 1f))
            c.addView(row.lp(ViewGroup.LayoutParams.MATCH_PARENT))
            if (i < n) c.addView(android.view.View(this).apply {
                setBackgroundColor(col(R.color.soft))
            }.lp(ViewGroup.LayoutParams.MATCH_PARENT, dpi(1)))
        }
        root.addView(c)
    }

    private fun checkCircle(day: LocalDate, key: String, done: Boolean): TextView = TextView(this).apply {
        text = if (done) "✓" else ""
        gravity = Gravity.CENTER
        setTextColor(col(R.color.white))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        background = if (done) rounded(col(R.color.done), 13f)
        else rounded(col(R.color.card), 13f, col(R.color.text_sub))
        setOnClickListener {
            store.update(day) { l -> if (done) l.done.remove(key) else l.done.add(key) }
            render()
        }
    }

    private fun builtinRow(day: LocalDate, item: Item, log: DayLog, next: Boolean): LinearLayout {
        val done = item.key in log.done
        val row = hbox()
        row.setPadding(dpi(10), dpi(7), dpi(4), dpi(7))
        if (next) row.background = rounded(col(R.color.soft), 10f)
        val lock = if (store.settings.locked(item.kind.key)) "🔒 " else ""
        val t = label("$lock${item.kind.emoji} ${item.kind.title}  ${item.at.format(DateTimeFormatter.ofPattern("HH:mm"))}",
            14f, next, if (done) R.color.text_sub else R.color.text)
        row.addView(t.lp(0, weight = 1f))
        row.addView(checkCircle(day, item.key, done).lp(dpi(26), dpi(26)))
        row.setOnClickListener { showWhy(item) }
        return row
    }

    private fun customRow(day: LocalDate, item: Item, log: DayLog, slot: Int, next: Boolean): LinearLayout {
        val cu = item.custom!!
        val done = item.key in log.done
        val row = hbox()
        row.setPadding(dpi(10), dpi(8), dpi(4), dpi(8))
        row.background = rounded(col(R.color.soft), 10f, if (next) col(R.color.brand) else null)
        val box = vbox()
        val lock = if (cu.locked) "🔒 " else ""
        val bell = if (cu.notify) "" else " 🔕"
        box.addView(label("$lock📌 ${item.kind.title}$bell", 15f, true, if (done) R.color.text_sub else R.color.text))
        val end = cu.start + cu.slots * slot
        val repeat = when {
            cu.days == 0 -> "只有今天"
            cu.days == Custom.EVERY_DAY -> "每天"
            cu.days == Custom.WEEKDAYS -> "平日"
            else -> "每週 " + (0..6).filter { cu.days and (1 shl it) != 0 }.joinToString("、") { WEEK[it] }
        }
        box.addView(label("${fmt(cu.start)}–${fmt(end)}・$repeat" +
            (if (cu.note.isNotBlank()) "\n${cu.note}" else ""), 12f, colorId = R.color.text_sub))
        row.addView(box.lp(0, weight = 1f))
        row.addView(checkCircle(day, item.key, done).lp(dpi(26), dpi(26)))
        row.setOnClickListener { editCustom(day, cu, cu.start) }
        return row
    }

    /** 新增或修改自訂行程。c == null 代表新增，startMin 是點的那一格。 */
    private fun editCustom(day: LocalDate, c0: Custom?, startMin: Int) {
        val s = store.settings
        val slot = s.slotMinutes
        if (c0 != null && c0.locked) {
            AlertDialog.Builder(this)
                .setTitle("🔒 ${c0.title}")
                .setMessage("這個行程已固定（${fmt(c0.start)} 開始，${c0.slots * slot} 分鐘）。要修改或刪除，先解鎖。")
                .setPositiveButton("關閉", null)
                .setNeutralButton("解鎖") { _, _ ->
                    c0.locked = false; store.settings.putCustom(c0); render(); editCustom(day, c0, startMin)
                }
                .show()
            return
        }
        val c = c0?.copy() ?: Custom(System.currentTimeMillis(), "", "", startMin % 1440, 1,
            Custom.EVERY_DAY, day.toString(), false, true)

        val box = vbox(20)
        val title = EditText(this).apply {
            hint = "要做什麼（例如：午睡、背單字、整理房間）"
            setText(c.title); setSingleLine()
        }
        val note = EditText(this).apply { hint = "備註（可不填）"; setText(c.note) }
        box.addView(title.lp(ViewGroup.LayoutParams.MATCH_PARENT))
        box.addView(note.lp(ViewGroup.LayoutParams.MATCH_PARENT))

        // 開始時間與長度（以格為單位）
        val timeLabel = label("", 16f, true)
        fun showTime() { timeLabel.text = "${fmt(c.start)}–${fmt(c.start + c.slots * slot)}（${c.slots} 格，${c.slots * slot} 分鐘）" }
        showTime()
        box.addView(timeLabel.lp(top = 12))
        val r1 = hbox()
        r1.addView(label("開始", 14f).lp(0, weight = 1f))
        r1.addView(pill("提早") { c.start = ((c.start - slot) % 1440 + 1440) % 1440; showTime() }.lp(end = 6))
        r1.addView(pill("延後") { c.start = (c.start + slot) % 1440; showTime() })
        box.addView(r1.lp(ViewGroup.LayoutParams.MATCH_PARENT, top = 8))
        val r2 = hbox()
        r2.addView(label("長度", 14f).lp(0, weight = 1f))
        r2.addView(pill("− 1 格") { c.slots = (c.slots - 1).coerceAtLeast(1); showTime() }.lp(end = 6))
        r2.addView(pill("＋ 1 格") { c.slots = (c.slots + 1).coerceAtMost(24); showTime() })
        box.addView(r2.lp(ViewGroup.LayoutParams.MATCH_PARENT, top = 8))

        // 重複
        box.addView(label("重複", 14f, true).lp(top = 14))
        val weekRow = hbox()
        val modeRow = hbox()
        fun paint() {
            for (i in 0 until weekRow.childCount) {
                val v = weekRow.getChildAt(i) as TextView
                val on = c.days != 0 && (c.days and (1 shl i)) != 0
                v.setTextColor(col(if (on) R.color.white else R.color.text))
                v.background = rounded(col(if (on) R.color.brand else R.color.soft), 10f)
            }
            val modes = listOf(Custom.EVERY_DAY, Custom.WEEKDAYS, 0)
            for (i in 0 until modeRow.childCount) {
                val v = modeRow.getChildAt(i) as TextView
                val on = c.days == modes[i]
                v.setTextColor(col(if (on) R.color.white else R.color.text))
                v.background = rounded(col(if (on) R.color.brand else R.color.soft), 22f)
            }
        }
        listOf("每天" to Custom.EVERY_DAY, "平日" to Custom.WEEKDAYS, "只有今天" to 0).forEach { (name, v) ->
            modeRow.addView(pill(name) { c.days = v; if (v == 0) c.date = day.toString(); paint() }.lp(end = 6))
        }
        box.addView(modeRow.lp(top = 6))
        for (i in 0..6) {
            val t = TextView(this).apply {
                text = WEEK[i]; gravity = Gravity.CENTER
                setPadding(0, dpi(8), 0, dpi(8))
                setOnClickListener {
                    c.days = c.days xor (1 shl i)
                    paint()
                }
            }
            weekRow.addView(t.lp(0, weight = 1f, end = if (i < 6) 4 else 0))
        }
        box.addView(weekRow.lp(ViewGroup.LayoutParams.MATCH_PARENT, top = 8))
        paint()

        val notifySw = SwitchCompat(this).apply { text = "開始時跳通知"; isChecked = c.notify }
        val lockSw = SwitchCompat(this).apply { text = "🔒 固定（之後要先解鎖才能改）"; isChecked = c.locked }
        box.addView(notifySw.lp(ViewGroup.LayoutParams.MATCH_PARENT, top = 12))
        box.addView(lockSw.lp(ViewGroup.LayoutParams.MATCH_PARENT, top = 4))

        val dlg = AlertDialog.Builder(this)
            .setTitle(if (c0 == null) "加入行程" else "修改行程")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("儲存") { _, _ ->
                c.title = title.text.toString().trim()
                c.note = note.text.toString().trim()
                c.notify = notifySw.isChecked
                c.locked = lockSw.isChecked
                if (c.days == 0) c.date = day.toString()
                store.settings.putCustom(c)
                Alarms.scheduleNext(this); render()
            }
            .setNegativeButton("取消", null)
        if (c0 != null) dlg.setNeutralButton("刪除") { _, _ ->
            store.settings.removeCustom(c0.id); Alarms.scheduleNext(this); render()
        }
        dlg.show()
    }

    private fun showWhy(item: Item) {
        val s = store.settings
        val key = item.kind.key
        val locked = s.locked(key)
        val box = vbox(20)
        box.addView(label(item.kind.body, 15f))
        box.addView(label("為什麼：\n${item.kind.why}", 14f, colorId = R.color.text_sub).lp(top = 10))
        var dlg: AlertDialog? = null
        when (key) {
            "learn" -> box.addView(pill("打開英文筆記本", true) {
                startActivity(Alarms.engIntent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); dlg?.dismiss()
            }.lp(top = 12))
            "exercise" -> Alarms.walkIntent(this)?.let { i ->
                box.addView(pill("打開運動 App", true) {
                    startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); dlg?.dismiss()
                }.lp(top = 12))
            }
        }
        val row = hbox()
        row.addView(pill(if (locked) "🔓 解鎖" else "🔒 固定") {
            s.setLocked(key, !locked); dlg?.dismiss(); render()
        }.lp(end = 8))
        if (!locked) row.addView(pill("從時間表移除") {
            s.setEnabled(key, false); Alarms.scheduleNext(this); dlg?.dismiss(); render()
        })
        box.addView(row.lp(top = 14))
        if (locked) box.addView(label("固定的項目要先解鎖才能移除。移除後可以到「設定」再打開。",
            12f, colorId = R.color.text_sub).lp(top = 6))
        dlg = AlertDialog.Builder(this)
            .setTitle("${item.kind.emoji} ${item.kind.title}")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("知道了", null)
            .show()
    }

    companion object {
        val WEEK = listOf("一", "二", "三", "四", "五", "六", "日")
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
