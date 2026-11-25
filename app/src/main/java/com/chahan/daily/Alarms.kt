package com.chahan.daily

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate

object Alarms {
    const val CHANNEL = "daily"
    const val ACTION_FIRE = "com.chahan.daily.FIRE"
    const val ACTION_TIMER = "com.chahan.daily.TIMER"
    const val ACTION_DONE = "com.chahan.daily.DONE"
    const val ENG_PKG = "tw.chahan.engnotebook"
    const val ENG_URL = "https://chahan1.github.io/"
    const val WALK_PKG = "tw.chahan.walkcoach"

    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    private fun setAlarm(ctx: Context, at: Long, pi: PendingIntent) {
        val am = am(ctx)
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    /** 排下一批提醒（只排一個鬧鐘，響了再排下一個）。 */
    fun scheduleNext(ctx: Context) {
        val s = Store(ctx).settings
        val next = Reminders.nextItems(s, System.currentTimeMillis() + 1000)
        val intent = Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_FIRE)
        if (next.isEmpty()) {
            val pi = PendingIntent.getBroadcast(ctx, 1, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            if (pi != null) am(ctx).cancel(pi)
            return
        }
        intent.putExtra("day", next.first().day.toString())
        intent.putExtra("keys", next.map { it.key }.toTypedArray())
        intent.putExtra("at", next.first().millis)
        val pi = PendingIntent.getBroadcast(ctx, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        setAlarm(ctx, next.first().millis, pi)
    }

    fun startTimer(ctx: Context, minutes: Int) {
        val end = System.currentTimeMillis() + minutes * 60_000L
        Store(ctx).timerEnd = end
        val pi = PendingIntent.getBroadcast(ctx, 2,
            Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_TIMER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        setAlarm(ctx, end, pi)
    }

    fun cancelTimer(ctx: Context) {
        Store(ctx).timerEnd = 0
        val pi = PendingIntent.getBroadcast(ctx, 2,
            Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_TIMER),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pi != null) am(ctx).cancel(pi)
    }

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "每日提醒", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "一天中該做的事：曬太陽、運動、學習、睡覺…"
            nm.createNotificationChannel(ch)
        }
    }

    fun notifId(day: LocalDate, key: String) = (day.toString() + key).hashCode()

    /** 打開英文筆記本（有裝 App 就開 App，否則開網頁）。 */
    fun engIntent(ctx: Context): Intent =
        ctx.packageManager.getLaunchIntentForPackage(ENG_PKG)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse(ENG_URL))

    fun walkIntent(ctx: Context): Intent? = ctx.packageManager.getLaunchIntentForPackage(WALK_PKG)

    fun canNotify(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    fun show(ctx: Context, item: Item) {
        if (!canNotify(ctx)) return
        ensureChannel(ctx)
        val id = notifId(item.day, item.key)
        val open = PendingIntent.getActivity(ctx, id,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val done = PendingIntent.getBroadcast(ctx, id,
            Intent(ctx, ActionReceiver::class.java).setAction(ACTION_DONE)
                .putExtra("day", item.day.toString()).putExtra("key", item.key),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(ctx, R.color.brand))
            .setContentTitle("${item.kind.emoji} ${item.kind.title}")
            .setContentText(item.kind.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.kind.body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "✓ 完成", done)

        when (item.kind.key) {
            "learn" -> {
                val pi = PendingIntent.getActivity(ctx, id + 1,
                    engIntent(ctx).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                b.addAction(0, "打開英文筆記本", pi)
            }
            "exercise" -> walkIntent(ctx)?.let {
                val pi = PendingIntent.getActivity(ctx, id + 1,
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                b.addAction(0, "打開運動 App", pi)
            }
        }
        try {
            NotificationManagerCompat.from(ctx).notify(id, b.build())
        } catch (e: SecurityException) { }
    }

    fun showTimerDone(ctx: Context) {
        if (!canNotify(ctx)) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(ctx, 99,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(ctx, R.color.brand))
            .setContentTitle("⏱️ 5 分鐘到了")
            .setContentText("做得好！想繼續就繼續，想停也可以停。")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(99, n)
        } catch (e: SecurityException) { }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Alarms.ACTION_TIMER -> {
                Store(ctx).timerEnd = 0
                Alarms.showTimerDone(ctx)
            }
            Alarms.ACTION_FIRE -> {
                val store = Store(ctx)
                val dayStr = intent.getStringExtra("day")
                val keys = intent.getStringArrayExtra("keys")
                val at = intent.getLongExtra("at", 0)
                // 鬧鐘延遲超過 2 小時就不補發（例如手機關機很久）
                if (dayStr != null && keys != null &&
                    System.currentTimeMillis() - at < 2 * 3600_000L) {
                    val day = LocalDate.parse(dayStr)
                    val done = store.log(day).done
                    Reminders.itemsFor(day, store.settings)
                        .filter { it.key in keys && it.key !in done }
                        .forEach { Alarms.show(ctx, it) }
                }
                Alarms.scheduleNext(ctx)
            }
        }
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Alarms.ACTION_DONE) return
        val day = LocalDate.parse(intent.getStringExtra("day") ?: return)
        val key = intent.getStringExtra("key") ?: return
        Store(ctx).update(day) { it.done += key }
        NotificationManagerCompat.from(ctx).cancel(Alarms.notifId(day, key))
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Alarms.scheduleNext(ctx)
        val end = Store(ctx).timerEnd
        if (end > System.currentTimeMillis()) {
            Alarms.startTimer(ctx, ((end - System.currentTimeMillis()) / 60_000L).toInt().coerceAtLeast(1))
        }
    }
}
