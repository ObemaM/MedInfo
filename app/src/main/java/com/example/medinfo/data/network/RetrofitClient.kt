package com.example.medinfo.data.network

import android.content.Context
import com.example.medinfo.config.ConfigManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    lateinit var apiServiceService: ApiService

    // Объявление Retrofit клиента
    fun init(context: Context) {
        apiServiceService = functionRetrofit(context).create(ApiService::class.java)
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
            .connectTimeout(ConfigManager.httpConnectTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(ConfigManager.httpReadTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .build()

        // Собираем Retrofit
        return Retrofit.Builder()
            .baseUrl(ConfigManager.serverBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}