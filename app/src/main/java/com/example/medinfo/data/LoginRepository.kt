package com.example.medinfo.data

import com.example.medinfo.model.ApiResponse // Используем универсальный ответ
import com.example.medinfo.model.LoginRequest
import java.io.IOException


class LoginRepository(private val apiService: API){

    // POST запрос на аутентификацию
    suspend fun login(login: String, passwordHash: String): ApiResponse<String> {
        val request = LoginRequest(login = login, password = passwordHash)
        val response = apiService.login(request)

        // Обработка запроса
        if (response.isSuccessful) {
            return response.body() ?: throw IOException("Пустой ответ от сервера")
        }
        else {
            // Возвращает код запроса в случае ошибки
            throw IOException ("Ошибка HTTP ${response.code()}")
        }
    }
}