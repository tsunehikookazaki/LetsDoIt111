package net.the_okazakis.letsdoit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MorningAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 1. 明日の朝8時のアラームを再設定
        AlarmScheduler.scheduleMorningAlarm(context)

        // 2. MainActivity を起動（画面をオンにして音声案内を実行させる）
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("from_morning_alarm", true)
        }
        context.startActivity(launchIntent)
    }
}
