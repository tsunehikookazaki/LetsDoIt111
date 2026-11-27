package net.the_okazakis.letsdoit

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class MorningAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 1. 全てのアラームを再設定
        AlarmScheduler.scheduleAllAlarms(context)

        // 2. MainActivity を起動するための Intent を準備
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("from_morning_alarm", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Android 10 (API 29) 以降のバックグラウンド起動制限を回避するため、全画面通知 (Full Screen Intent) を発行
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "morning_alarm_channel"

        val channel = NotificationChannel(
            channelId,
            "起動通知",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "設定時刻にアプリを起動するための全画面通知"
            setBypassDnd(true)
        }
        notificationManager.createNotificationChannel(channel)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Let's do it")
            .setContentText("運動のチェック時間です")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)

        notificationManager.notify(1001, builder.build())

        // フォアグラウンド状態などの場合に備えて直接起動も試行
        try {
            context.startActivity(launchIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
