package com.chitala.reader

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("chitala_prefs", Context.MODE_PRIVATE)

    var isDarkTheme: Boolean
        get() = prefs.getBoolean("dark_theme", false)
        set(value) = prefs.edit().putBoolean("dark_theme", value).apply()

    var fontSize: Int
        get() = prefs.getInt("font_size", 14)
        set(value) = prefs.edit().putInt("font_size", value).apply()

    fun addRecentFile(name: String, uri: String) {
        val recent = getRecentFiles().toMutableList()
        recent.removeAll { it.uri == uri }
        recent.add(0, RecentFile(name, uri, System.currentTimeMillis()))
        if (recent.size > 20) recent.removeLast()
        val arr = JSONArray()
        recent.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("recent_files", arr.toString()).apply()
    }

    fun getRecentFiles(): List<RecentFile> {
        val json = prefs.getString("recent_files", "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<RecentFile>()
        for (i in 0 until arr.length()) {
            list.add(RecentFile.fromJson(arr.getJSONObject(i)))
        }
        return list
    }

    fun clearRecentFiles() {
        prefs.edit().remove("recent_files").apply()
    }

    data class RecentFile(
        val name: String,
        val uri: String,
        val timestamp: Long
    ) {
        fun toJson(): org.json.JSONObject {
            return org.json.JSONObject().apply {
                put("name", name)
                put("uri", uri)
                put("ts", timestamp)
            }
        }

        companion object {
            fun fromJson(obj: org.json.JSONObject): RecentFile {
                return RecentFile(
                    name = obj.getString("name"),
                    uri = obj.getString("uri"),
                    timestamp = obj.getLong("ts")
                )
            }
        }
    }
}
