package com.example.medinfo.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // Адрес сервера
    private const val BASE_URL = "http://46.146.213.95:27234"

    lateinit var apiService: API

    // Объявление Retrofit клиента
    fun init(context: Context) {
        apiService = functionRetrofit(context).create(API::class.java)
    }

    // Настройка Retrofit
    private fun functionRetrofit(context: Context): Retrofit {

        // Для отладки логина
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY // Логи в виде Body
        }

        // Регистрируем перехватчики
        val client = OkHttpClient.Builder()
            .addInterceptor(TokenInterceptor(context)) // Используем наш перехватчик токена
            .addInterceptor(loggingInterceptor)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        // Собираем Retrofit
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
