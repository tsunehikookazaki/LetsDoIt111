package net.the_okazakis.letsdoit

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * 他の運動アプリ（Step, Briskwalk, WorkoutStretch）から
 * 運動記録を直接受け取るための窓口クラス。
 */
class StatsProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "net.the_okazakis.letsdoit.stats"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/stats")
    }

    override fun onCreate(): Boolean = true

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        if (values == null) return null
        
        val context = context ?: return null
        val appKey = values.getAsString("app_key")
        val value = values.getAsString("value")

        if (appKey != null && value != null) {
            // 直接 SharedRecordManager を介してデータを保存する
            SharedRecordManager.updateStats(context, appKey, value)
            
            // 変更を通知（LetsDoItが起動していれば自動更新できるようにする準備）
            context.contentResolver.notifyChange(uri, null)
            return Uri.withAppendedPath(uri, appKey)
        }
        return null
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        // 今回は書き込み専用とするため、クエリは未実装
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun getType(uri: Uri): String? = "vnd.android.cursor.item/vnd.net.the_okazakis.stats"
}
