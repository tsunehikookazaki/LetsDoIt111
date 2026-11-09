package net.the_okazakis.letsdoit

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.*

class MorningCheckWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    // **************朝 MainActivity で指定した時間に発声する
    override fun doWork(): Result {
        val speechManager = SpeechManager(applicationContext)

        // 前日のデータを確認
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)

        val allStats = SharedRecordManager.getAllStats(applicationContext)
        val yesterdayData = allStats.optJSONObject(yesterdayKey)

        // ★ 昨日の状況をアナウンス（マシントレーニングも含めて判定）
        val yesterdayDone = isAnyExerciseDone(yesterdayData)
        if (yesterdayDone) {
            speechManager.speak("おはようございます。昨日はバッチリ運動していましたね！")
        } else {
            speechManager.speak("おはようございます。昨日は運動の記録がありませんでした。")
        }

        // 今日の状況（朝なので基本はまだのはずだが一応確認）
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val todayData = allStats.optJSONObject(todayKey)
        val todayDone = isAnyExerciseDone(todayData)

        if (!todayDone) {
            speechManager.speak("今日は少しだけ体を動かしてみませんか？")
        } else {
            speechManager.speak("今日もしっかり運動を継続していきましょう！")
        }

        // TTSの初期化に時間がかかる場合があるため、少し待機してからシャットダウン（簡易的）
        Thread.sleep(10000)
        speechManager.shutdown()

        return Result.success()
    }

    // ★ マシントレーニング(machine)も含めて運動したかどうかを判定するヘルパー関数
    private fun isAnyExerciseDone(data: org.json.JSONObject?): Boolean {
        if (data == null) return false
        val keys = arrayOf("strech", "step", "brisk", "machine")
        for (key in keys) {
            val value = data.optString(key, "")
            if (value.isNotEmpty() && value != "未実施" && value != "null") {
                return true
            }
        }
        return false
    }
}