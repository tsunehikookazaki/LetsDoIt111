package net.the_okazakis.letsdoit

import android.app.TimePickerDialog
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.util.*

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
    private lateinit var btnTimeSettings1: Button
    private lateinit var btnTimeSettings2: Button
    private lateinit var btnTimeSettings3: Button
    private lateinit var switchVoice: SwitchMaterial
    private lateinit var switchAutoStart: SwitchMaterial
    private lateinit var switchTime1: SwitchMaterial
    private lateinit var switchTime2: SwitchMaterial
    private lateinit var switchTime3: SwitchMaterial
    private lateinit var prefs: SharedPreferences

    private lateinit var speechManager: SpeechManager
    private val speechHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ロック画面でも画面をオンにして表示・音声再生できるようにする設定
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }

        // Android 13 (API 33) 以降の通知パーミッション要求
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }

        // Android 12 (API 31) 以降の正確なアラーム設定権限の確認
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

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
        btnTimeSettings1 = findViewById(R.id.btnTimeSettings1)
        btnTimeSettings2 = findViewById(R.id.btnTimeSettings2)
        btnTimeSettings3 = findViewById(R.id.btnTimeSettings3)

        updateTimeButtonsText()

        btnTimeSettings1.setOnClickListener { showTimePickerDialog(0) }
        btnTimeSettings2.setOnClickListener { showTimePickerDialog(1) }
        btnTimeSettings3.setOnClickListener { showTimePickerDialog(2) }

        val btnSettings = findViewById<Button>(R.id.btnSyncSettings)
        btnSettings.visibility = android.view.View.VISIBLE
        btnSettings.text = "名前・敬称設定"
        btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        findViewById<Button>(R.id.btnRefresh).setOnClickListener {
            updateDisplay()
            Toast.makeText(this, "ローカルから状況を更新しました", Toast.LENGTH_SHORT).show()
        }

        speechManager = SpeechManager(this)

        updateDisplay()
        
        // 設定された時刻に起動するアラームをスケジュール
        AlarmScheduler.scheduleMorningAlarm(this)

        if (intent.getBooleanExtra("from_morning_alarm", false)) {
            showAlarmNotification()
        }

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
                Toast.makeText(this, "音声案内: ON", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "音声案内: OFF", Toast.LENGTH_SHORT).show()
                speechManager.stop() // OFFにした時に今喋っているのを止める
            }
        }

        // 自動起動設定の初期化
        switchAutoStart = findViewById(R.id.switchAutoStart)
        switchAutoStart.isChecked = AlarmScheduler.isAlarmEnabled(this)

        switchAutoStart.setOnCheckedChangeListener { _, isChecked ->
            AlarmScheduler.setAlarmEnabled(this, isChecked)
            if (isChecked) {
                Toast.makeText(this, "指定時刻の自動起動: ON", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "指定時刻の自動起動: OFF（全アラームを解除しました）", Toast.LENGTH_SHORT).show()
            }
        }

        // 個別時間スイッチの初期化
        val timeSwitches = arrayOf(
            findViewById<SwitchMaterial>(R.id.switchTime1),
            findViewById<SwitchMaterial>(R.id.switchTime2),
            findViewById<SwitchMaterial>(R.id.switchTime3)
        )
        switchTime1 = timeSwitches[0]
        switchTime2 = timeSwitches[1]
        switchTime3 = timeSwitches[2]

        for (i in 0..2) {
            timeSwitches[i].isChecked = AlarmScheduler.isAlarmTimeEnabled(this, i)
            timeSwitches[i].setOnCheckedChangeListener { _, isChecked ->
                AlarmScheduler.setAlarmTimeEnabled(this, i, isChecked)
                val statusText = if (isChecked) "ON" else "OFF"
                Toast.makeText(this, "起動時間${i + 1}の通知を $statusText に変更しました", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateTimeButtonsText() {
        val buttons = arrayOf(btnTimeSettings1, btnTimeSettings2, btnTimeSettings3)
        for (i in 0..2) {
            val hour = AlarmScheduler.getAlarmHour(this, i)
            val minute = AlarmScheduler.getAlarmMinute(this, i)
            val timeStr = String.format(Locale.JAPANESE, "%02d:%02d", hour, minute)
            buttons[i].text = "起動時間${i + 1}の設定 (現在 $timeStr)"
        }
    }

    private fun showTimePickerDialog(index: Int) {
        val currentHour = AlarmScheduler.getAlarmHour(this, index)
        val currentMinute = AlarmScheduler.getAlarmMinute(this, index)

        val timePickerDialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                AlarmScheduler.saveAlarmTime(this, index, hourOfDay, minute)
                updateTimeButtonsText()
                val timeStr = String.format(Locale.JAPANESE, "%02d:%02d", hourOfDay, minute)
                Toast.makeText(this, "起動時間${index + 1}を $timeStr に設定しました", Toast.LENGTH_SHORT).show()
            },
            currentHour,
            currentMinute,
            true
        )
        timePickerDialog.show()
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

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AlarmScheduler.scheduleAllAlarms(this)
        updateDisplay()
        if (intent.getBooleanExtra("from_morning_alarm", false)) {
            showAlarmNotification()
        }
        speechHandler.removeCallbacksAndMessages(null)
        speechHandler.postDelayed({
            announceAllStatus()
        }, 500)
    }

    private fun showAlarmNotification() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "morning_alarm_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "起動通知",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "設定時刻の通知"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val builder = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Let's do it")
            .setContentText("運動のチェック時間です")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(1001, builder.build())
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
                Toast.makeText(this, "設定を保存しました", Toast.LENGTH_SHORT).show()
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

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_help -> {
                showHelpDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showHelpDialog() {
        val message = "1.バッテリー最適化の解除（無制限） スマホの 設定 → アプリ→ Let's do it → バッテリー から 「無制限」（または 「最適化しない」）に設定してください。\n\n" +
                "2.おやすみ時間モード / マナーモードの割り込み許可　おやすみ時間モード中でも音声案内を聞きたい場合は、通知の割り込み許可を設定してください。\n\n" +
                "アプリの画面を表示するため。以下の設定をしてください。\n" +
                "スマフォの設定→アプリ→Let's do it 1.21→他のアプリの上に重ねて表示→ボタンを右にして許可\n\n" +
                "左のボタンで起動の個別ON OFFができます"

        val textView = android.widget.TextView(this).apply {
            text = message
            setTextColor(android.graphics.Color.BLACK)
            textSize = 16f
            setPadding(60, 40, 60, 20)
            setLineSpacing(0f, 1.2f)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("設定・使い方ガイド")
            .setView(textView)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroy() {
        speechManager.shutdown()
        super.onDestroy()
    }
}
