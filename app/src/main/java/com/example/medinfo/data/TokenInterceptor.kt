package com.example.medinfo.data

import android.content.Context
import okhttp3.Interceptor
import okhttp3.Response
import androidx.core.content.edit

// Перехватчик OkHttp - добавляет JWT-токен в заголовок Authorization для всех исходящих запросов
class TokenInterceptor(private val context: Context) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {

        // Получаем сохраненный JWT-токен из SharedPreferences
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = sharedPrefs.getString(KEY_JWT_TOKEN, null)

        // Получаем оригинальный запрос и добавляем заголовки
        val originalRequest = chain.request()
        val builder = originalRequest.newBuilder()
            .header("Content-Type", "application/json")

        // Если токен существует, добавляем его в заголовок Authorization
        if (token != null) {
            // Добавляем заголовок с JWT
            builder.header("Authorization", "Bearer $token")
        }

        // Продолжаем выполнение запроса (с новым заголовком или без него)
        val newRequest = builder.build()
        return chain.proceed(newRequest)
    }

    companion object {
        private const val PREFS_NAME = "app_session"
        private const val KEY_JWT_TOKEN = "jwt_token"

        // Сохраняет JWT-токен в SharedPreferences
        fun saveToken(context: Context, token: String) {
            // context - Контекст для доступа к SharedPreferences
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // token - Токен для сохранения
            prefs.edit { putString(KEY_JWT_TOKEN, token) }
        }

        // Удаляет JWT-токен из SharedPreferences
        fun clearToken(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit { remove(KEY_JWT_TOKEN) }
        }
    }
}
