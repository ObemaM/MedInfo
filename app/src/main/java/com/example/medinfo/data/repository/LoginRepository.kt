package com.example.medinfo.data.repository

import com.example.medinfo.data.network.ApiService
import com.example.medinfo.model.ApiResponse
import com.example.medinfo.model.api.AuthRequestDto
import java.io.IOException

class LoginRepository(private val apiServiceService: ApiService) {

    // POST запрос на аутентификацию
    suspend fun login(login: String, passwordHash: String): ApiResponse<String> {
        val request = AuthRequestDto(login = login, password = passwordHash)
        val response = apiServiceService.login(request)
        val body = response.body()

        // Обработка успешного ответа
        if (response.isSuccessful) {
            body?.let { return it }
            throw IOException("Пустой ответ от сервера")
        }

        // Если сервер вернул текст ошибки в стандартной обертке, используем его
        val errorMessage = body?.messages?.firstOrNull()
        throw IOException(errorMessage ?: "Ошибка HTTP ${response.code()}")
    }
}
