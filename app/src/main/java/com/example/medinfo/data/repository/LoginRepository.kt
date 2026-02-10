package com.example.medinfo.data.repository

import android.util.Log
import com.example.medinfo.data.network.ApiService
import com.example.medinfo.model.ApiResponse
import com.example.medinfo.model.LoginRequest
import java.io.IOException

class LoginRepository(private val apiServiceService: ApiService){

    // POST запрос на аутентификацию
    suspend fun login(login: String, passwordHash: String): ApiResponse<String> {
        val request = LoginRequest(login = login, password = passwordHash)
        val response = apiServiceService.login(request)

        // Обработка запроса
        if (response.isSuccessful) {
            response.body()?.let { return it }
            Log.e("LoginRepository", "Пустой ответ от сервера при логине пользователя: $login")
            throw IOException("Пустой ответ от сервера")
        }
        else {
            Log.e("LoginRepository", "Ошибка HTTP ${response.code()} при логине пользователя: $login")
            // Возвращает код запроса в случае ошибки
            throw IOException("Ошибка HTTP ${response.code()}")
        }
    }
}