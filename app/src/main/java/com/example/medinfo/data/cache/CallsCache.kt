package com.example.medinfo.data.cache

import android.content.Context
import com.example.medinfo.model.Hospitalization
import com.example.medinfo.util.DateFormatter.parseCallTimeMillis
import com.example.medinfo.util.toSha256
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class CallsCache(private val context: Context) {

    private val gson = Gson()

    private fun cacheFile(userLogin: String): File {
        // Кэш в Sha256, чтобы не было конфликтов имен
        val key = userLogin.trim().lowercase().toSha256()
        return File(context.filesDir, "calls_cache_$key.json")
    }

    fun readCalls(userLogin: String): List<Hospitalization>? {
        return try {
            // Получает кэш
            val file = cacheFile(userLogin)

            if (!file.exists())
                return null

            val json = file.readText(Charsets.UTF_8)

            if (json.isBlank())
                return null

            // Сохраняет данные кэша
            val type = object : TypeToken<List<Hospitalization>>() {}.type
            gson.fromJson<List<Hospitalization>>(json, type)
        } catch (e: Exception) {
            null
        }
    }

    // Проверка на наличие вызова в кэше (в активных)
    fun containsCallNumber(userLogin: String, callNumber: String): Boolean {
        val normalized = callNumber.trim().lowercase()
        if (normalized.isBlank()) return false

        val list = readCalls(userLogin) ?: return false
        return list.any { h ->
            // Проверка, что вызов не в архиве
            val status = h.status?.trim()?.lowercase().orEmpty()
            if (status.contains("архив")) return@any false
            val hn =
                if (h.dayNumber != null && h.yearNumber != null) {
                    "${h.dayNumber}/${h.yearNumber}"
                } else { null }
            val hnNormalized = hn?.trim()?.lowercase()
            // TODO: Почему проверка на id, нужна ли?
            val idNormalized = h.id.trim().lowercase()
            hnNormalized == normalized || idNormalized == normalized
        }
    }

    // Перезапись всего кэша после добавления пользователя
    fun writeCalls(userLogin: String, calls: List<Hospitalization>) {
        try {
            val file = cacheFile(userLogin)
            val json = gson.toJson(calls)
            file.writeText(json, Charsets.UTF_8)
        } catch (e: Exception) {}
    }

    // Очистка кэша
    fun clear(userLogin: String) {
        try {
            cacheFile(userLogin).delete()
        } catch (e: Exception) {}
    }

    // Очистка старых записей
    fun cleanupOldCache(calls: List<Hospitalization>): List<Hospitalization> {
        val twoMonthsAgo = System.currentTimeMillis() - (60L * 24 * 60 * 60 * 1000)
        return calls.filter { call ->
            val callMillis = parseCallTimeMillis(call.callTime) ?: return@filter true
            callMillis > twoMonthsAgo
        }
    }

}