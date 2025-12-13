package com.example.neuroinfo.data

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // 💡 Обязательно замените на ваш реальный адрес сервера
    private const val BASE_URL = "http://46.146.213.95:27234"

    // Переменная для хранения JWT токена
    private var jwtToken: String? = null

    // lazy - инициализируется только при первом обращении
    val apiService: NeuroInfoApiService by lazy {
        createRetrofitInstance().create(NeuroInfoApiService::class.java)
    }

    // 💡 Функция, которая отсутствовала
    private fun createRetrofitInstance(): Retrofit {
        // Interceptor для добавления JWT токена в заголовок Authorization
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
                .header("Content-Type", "application/json")

            // Добавляем токен в формате "Bearer <токен>"
            jwtToken?.let { token ->
                requestBuilder.header("Authorization", "Bearer $token")
            }

            val request = requestBuilder.build()
            chain.proceed(request)
        }

        // Logging Interceptor (для отладки)
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun setToken(token: String) {
        jwtToken = token
    }

    fun clearToken() {
        jwtToken = null
    }
}