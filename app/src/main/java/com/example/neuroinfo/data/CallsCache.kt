package com.example.neuroinfo.data

import android.content.Context
import android.util.Log
import com.example.neuroinfo.model.Hospitalization
import com.example.neuroinfo.util.toSha256
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class CallsCache(private val context: Context) {

    private val gson = Gson()

    private fun cacheFile(userLogin: String): File {
        val key = userLogin.trim().lowercase().toSha256()
        return File(context.filesDir, "calls_cache_$key.json")
    }

    fun readCalls(userLogin: String): List<Hospitalization>? {
        return try {
            val file = cacheFile(userLogin)
            if (!file.exists()) return null
            val json = file.readText(Charsets.UTF_8)
            if (json.isBlank()) return null

            val type = object : TypeToken<List<Hospitalization>>() {}.type
            gson.fromJson<List<Hospitalization>>(json, type)
        } catch (e: Exception) {
            Log.w("CallsCache", "readCalls failed (login=$userLogin)", e)
            null
        }
    }

    fun writeCalls(userLogin: String, calls: List<Hospitalization>) {
        try {
            val file = cacheFile(userLogin)
            val json = gson.toJson(calls)
            file.writeText(json, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w("CallsCache", "writeCalls failed (login=$userLogin, size=${calls.size})", e)
        }
    }

    fun clear(userLogin: String) {
        try {
            cacheFile(userLogin).delete()
        } catch (_: Exception) {
        }
    }
}
