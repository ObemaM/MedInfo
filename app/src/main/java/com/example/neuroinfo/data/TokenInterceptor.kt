package com.example.neuroinfo.data

import android.content.Context
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Перехватчик OkHttp, который автоматически добавляет JWT-токен
 * в заголовок Authorization для всех исходящих запросов.
 *
 * Также предоставляет статические методы для сохранения и удаления токена.
 */
class TokenInterceptor(private val context: Context) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        // 1. Получаем сохраненный JWT-токен из SharedPreferences
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = sharedPrefs.getString(KEY_JWT_TOKEN, null)

        // 2. Получаем оригинальный запрос и добавляем заголовки
        val originalRequest = chain.request()
        val builder = originalRequest.newBuilder()
            .header("Content-Type", "application/json")

        // 3. Если токен существует, добавляем его в заголовок Authorization
        if (token != null) {
            // Формат согласно спецификации: "Bearer jwt-токен"
            builder.header("Authorization", "Bearer $token")
        }

        // 4. Продолжаем выполнение запроса (с новым заголовком или без него)
        val newRequest = builder.build()
        return chain.proceed(newRequest)
    }

    /**
     * Companion object для централизованного управления токеном.
     */
    companion object {
        private const val PREFS_NAME = "app_session"
        private const val KEY_JWT_TOKEN = "jwt_token"

        /**
         * Сохраняет JWT-токен в SharedPreferences.
         * @param context Контекст для доступа к SharedPreferences.
         * @param token Токен для сохранения.
         */
        fun saveToken(context: Context, token: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_JWT_TOKEN, token).apply()
        }

        /**
         * Удаляет JWT-токен из SharedPreferences.
         * @param context Контекст для доступа к SharedPreferences.
         */
        fun clearToken(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_JWT_TOKEN).apply()
        }
    }
}
