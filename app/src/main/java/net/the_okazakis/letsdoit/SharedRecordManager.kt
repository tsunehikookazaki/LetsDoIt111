package net.the_okazakis.letsdoit

import android.content.Context
import android.os.Environment
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * LetsDoIt専用：SharedPreferences（同期データキャッシュ）と
 * 外部ストレージ（他のアプリとの共有データ）の両方を管理するマネージャー
 */
object SharedRecordManager {
    private const val PREF_NAME = "letsdoit_stats_prefs"
    private const val KEY_STATS = "stats_cache"
    private const val FOLDER_NAME = "LetsDoIt"
    private const val FILE_NAME = "stats.json"

    /**
     * 指定したアプリのステータスを更新する（ローカルファイルとキャッシュの両方）
     */
    fun updateStats(context: Context, appKey: String, value: String) {
        // 1. 最新の全データを取得（キャッシュとファイルをマージしたもの）
        val stats = getAllStats(context)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        
        // 2. 今日のデータを更新
        val dayData = stats.optJSONObject(today) ?: JSONObject()
        dayData.put(appKey, value)
        dayData.put("last_update", SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()))
        stats.put(today, dayData)

        // 3. 更新したデータをキャッシュとファイルの両方に保存
        saveAllStats(context, stats)
    }

    /**
     * キャッシュとローカルファイルをマージした全統計データを取得する
     */
    fun getAllStats(context: Context): JSONObject {
        // 1. キャッシュを取得
        val merged = getCachedStats(context)

        // 2. ローカルファイルを取得してマージ
        try {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), FOLDER_NAME)
            val file = File(dir, FILE_NAME)
            if (file.exists()) {
                val fileJson = JSONObject(file.readText())
                val keys = fileJson.keys()
                while (keys.hasNext()) {
                    val dateKey = keys.next()
                    val fileDayData = fileJson.optJSONObject(dateKey) ?: JSONObject()
                    val mergedDayData = merged.optJSONObject(dateKey) ?: JSONObject()
                    
                    // 日付ごとの各アプリのデータをマージ
                    val apps = fileDayData.keys()
                    while (apps.hasNext()) {
                        val appKey = apps.next()
                        mergedDayData.put(appKey, fileDayData.get(appKey))
                    }
                    merged.put(dateKey, mergedDayData)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return merged
    }

    /**
     * SharedPreferencesにあるキャッシュのみを取得
     */
    private fun getCachedStats(context: Context): JSONObject {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_STATS, "{}")
        return try {
            JSONObject(jsonStr ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
    }

    /**
     * JSONObjectをキャッシュ(SharedPreferences)に保存し、
     * 必要に応じて共有ファイル(stats.json)にも書き出す
     */
    fun saveAllStats(context: Context, stats: JSONObject) {
        // 1. SharedPreferencesを更新
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_STATS, stats.toString()).apply()

        // 2. 共有ファイル(stats.json)にも反映（他アプリへの通知用）
        try {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), FOLDER_NAME)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, FILE_NAME)

            // 既存のファイル内容とマージ（ローカルのみのデータを消さないように）
            val currentFileJson = if (file.exists()) {
                try { JSONObject(file.readText()) } catch (e: Exception) { JSONObject() }
            } else {
                JSONObject()
            }

            // stats の内容を currentFileJson に上書きマージ
            val keys = stats.keys()
            while (keys.hasNext()) {
                val dateKey = keys.next()
                val statsDayData = stats.optJSONObject(dateKey) ?: JSONObject()
                val fileDayData = currentFileJson.optJSONObject(dateKey) ?: JSONObject()
                
                val apps = statsDayData.keys()
                while (apps.hasNext()) {
                    val appKey = apps.next()
                    fileDayData.put(appKey, statsDayData.get(appKey))
                }
                currentFileJson.put(dateKey, fileDayData)
            }
            file.writeText(currentFileJson.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getNickname(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString("nickname", "") ?: ""
    }

    fun setNickname(context: Context, nickname: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("nickname", nickname).apply()
    }
}
