package com.example.neuroinfo.data

import android.content.Context
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Перехватчик OkHttp, который автоматически добавляет JWT-токен
 * в заголовок Authorization для всех исходящих запросов.
 */
class TokenInterceptor(private val context: Context) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {

        // 1. Получаем сохраненный JWT-токен из SharedPreferences
        // Используем то же имя файла, что и при сохранении в LoginActivity ("app_session")
        val sharedPrefs = context.getSharedPreferences("app_session", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("jwt_token", null)

        // 2. Получаем оригинальный запрос
        val originalRequest = chain.request()
        val builder = originalRequest.newBuilder()

        // 3. Если токен существует, добавляем его в заголовок
        if (token != null) {
            // Формат согласно спецификации: "Bearer jwt-токен"
            builder.header("Authorization", "Bearer $token")
        }

        // 4. Продолжаем выполнение запроса (с новым заголовком или без него)
        val newRequest = builder.build()
        return chain.proceed(newRequest)
    }
}