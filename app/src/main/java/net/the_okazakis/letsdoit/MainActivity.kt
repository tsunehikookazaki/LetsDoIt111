package net.the_okazakis.letsdoit

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*
import com.google.android.material.switchmaterial.SwitchMaterial
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper

class MainActivity : AppCompatActivity() {

    private lateinit var tvDate: TextView
    private lateinit var tvStrechYesterday: TextView
    private lateinit var tvStrechToday: TextView
    private lateinit var tvStepYesterday: TextView
    private lateinit var tvStepToday: TextView
    private lateinit var tvBriskYesterday: TextView
    private lateinit var tvBriskToday: TextView
    // ★マシントレーニング用のTextViewを宣言
    private lateinit var tvMachineYesterday: TextView
    private lateinit var tvMachineToday: TextView

    private lateinit var btnStatusCheck: Button
    private lateinit var switchVoice: SwitchMaterial
    private lateinit var prefs: SharedPreferences

    private lateinit var speechManager: SpeechManager
    private val speechHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.app_name)

        tvDate = findViewById(R.id.tvDate)
        tvStrechYesterday = findViewById(R.id.tvStrechYesterday)
        tvStrechToday = findViewById(R.id.tvStrechToday)
        tvStepYesterday = findViewById(R.id.tvStepYesterday)
        tvStepToday = findViewById(R.id.tvStepToday)
        tvBriskYesterday = findViewById(R.id.tvBriskYesterday)
        tvBriskToday = findViewById(R.id.tvBriskToday)
        // ★XMLのIDと紐付け
        tvMachineYesterday = findViewById(R.id.tvMachineYesterday)
        tvMachineToday = findViewById(R.id.tvMachineToday)

        btnStatusCheck = findViewById(R.id.btnStatusCheck)

        val btnSettings = findViewById<Button>(R.id.btnSyncSettings)
        btnSettings.visibility = android.view.View.VISIBLE
        btnSettings.text = "名前・敬称設定"
        btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        findViewById<Button>(R.id.btnRefresh).setOnClickListener {
            updateDisplay()
            android.widget.Toast.makeText(this, "ローカルから状況を更新しました", android.widget.Toast.LENGTH_SHORT).show()
        }

        speechManager = SpeechManager(this)

        updateDisplay()
        scheduleMorningCheck()

        btnStatusCheck.setOnClickListener {
            announceAllStatus()
        }

        // 音声設定の初期化
        switchVoice = findViewById(R.id.switchVoice)
        prefs = getSharedPreferences("LetsDoItPrefs", MODE_PRIVATE)

        // 前回の状態を復元 (デフォルトは ON)
        switchVoice.isChecked = prefs.getBoolean("voice_enabled", true)

        // 切り替え時に保存
        switchVoice.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("voice_enabled", isChecked).apply()
            if (isChecked) {
                android.widget.Toast.makeText(this, "音声案内: ON", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(this, "音声案内: OFF", android.widget.Toast.LENGTH_SHORT).show()
                speechManager.stop() // OFFにした時に今喋っているのを止める
            }
        }
    }

    private fun scheduleMorningCheck() {
        val workRequest = androidx.work.PeriodicWorkRequestBuilder<MorningCheckWorker>(24, java.util.concurrent.TimeUnit.HOURS)
            .setInitialDelay(calculateInitialDelay(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()

        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "MorningCheck",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun calculateInitialDelay(): Long {
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 8)   // 8時
        calendar.set(Calendar.MINUTE, 0)        // 0分
        calendar.set(Calendar.SECOND, 0)        // 0秒
        if (calendar.timeInMillis <= now) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis - now
    }

    override fun onResume() {
        super.onResume()
        updateDisplay()

        // 0.5秒待ってから発声開始
        speechHandler.postDelayed({
            announceAllStatus()
        }, 500)
    }

    override fun onPause() {
        super.onPause()
        speechHandler.removeCallbacksAndMessages(null)
        speechManager.stop()
    }

    private fun showSettingsDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("名前（ひらがな）の設定")

        val layout = android.widget.LinearLayout(this)
        layout.orientation = android.widget.LinearLayout.VERTICAL
        layout.setPadding(60, 30, 60, 30)

        val tvLabel = android.widget.TextView(this).apply {
            text = "ひらがなで「なまえ＋敬称」を入力してください\n（例：おかちゃんさん、せんせい）"
            setPadding(0, 0, 0, 20)
        }
        layout.addView(tvLabel)

        val etNickname = android.widget.EditText(this)
        etNickname.hint = "例：おかちゃんさん"
        etNickname.setText(SharedRecordManager.getNickname(this))
        layout.addView(etNickname)

        builder.setView(layout)
        builder.setPositiveButton("保存") { _, _ ->
            val name = etNickname.text.toString().trim()
            if (name.isNotEmpty()) {
                SharedRecordManager.setNickname(this, name)
                android.widget.Toast.makeText(this, "設定を保存しました", android.widget.Toast.LENGTH_SHORT).show()
                updateDisplay()
            }
        }
        builder.setNegativeButton("キャンセル", null)
        builder.show()
    }

    private fun announceAllStatus() {
        if (!switchVoice.isChecked) return

        val allStats = SharedRecordManager.getAllStats(this)
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)

        val todayData = allStats.optJSONObject(todayKey)
        val yesterdayData = allStats.optJSONObject(yesterdayKey)

        val nickname = SharedRecordManager.getNickname(this)
        val namePart = if (nickname.isNotEmpty()) "${nickname}、" else ""

        // 昨日の状況をアナウンス
        val yesterdayDone = isAnyExerciseDone(yesterdayData)
        if (yesterdayDone) {
            speechManager.speak("${namePart}昨日はバッチリ運動していましたね！")
        } else {
            speechManager.speak("${namePart}昨日は運動の記録がありませんでした。")
        }

        // 少し間を置いて今日の状況をアナウンス
        speechHandler.postDelayed({
            val todayDone = isAnyExerciseDone(todayData)
            if (todayDone) {
                speechManager.speak("今日は運動していますね。その調子で頑張りましょう！")
            } else {
                speechManager.speak("今日はまだ運動の記録がありません。少しだけ体を動かしてみませんか？")
            }
        }, 4000)
    }

    // ★マシントレーニング(machine)も判定対象に含めるよう修正
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

    private fun updateDisplay() {
        val dateStr = SimpleDateFormat("yyyy年M月d日 (E)", Locale.JAPANESE).format(Date())
        tvDate.text = dateStr

        val allStats = SharedRecordManager.getAllStats(this)

        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val todayData = allStats.optJSONObject(todayKey)

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        val yesterdayData = allStats.optJSONObject(yesterdayKey)

        setStatus(tvStrechYesterday, yesterdayData?.optString("strech"), "昨日")
        setStatus(tvStrechToday, todayData?.optString("strech"), "今日")

        setStatus(tvStepYesterday, yesterdayData?.optString("step"), "昨日")
        setStatus(tvStepToday, todayData?.optString("step"), "今日")

        setStatus(tvBriskYesterday, yesterdayData?.optString("brisk"), "昨日")
        setStatus(tvBriskToday, todayData?.optString("brisk"), "今日")

        // ★マシントレーニングの表示設定を追加
        setStatus(tvMachineYesterday, yesterdayData?.optString("machine"), "昨日")
        setStatus(tvMachineToday, todayData?.optString("machine"), "今日")
    }

    private fun setStatus(view: TextView, value: String?, prefix: String) {
        if (value == null || value == "未実施" || value.trim().isEmpty() || value == "null") {
            view.text = "$prefix: 未実施"
            view.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        } else {
            view.text = "$prefix: 実施済み"
            view.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        }
    }

    override fun onDestroy() {
        speechManager.shutdown()
        super.onDestroy()
    }
}