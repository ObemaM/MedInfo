package com.example.medinfo.data

import com.example.medinfo.model.ApiResponse // Используем универсальный ответ
import com.example.medinfo.model.LoginRequest
import java.io.IOException

class LoginRepository(
    private val apiService: API
) {
    /**
     * Выполняет POST-запрос на аутентификацию.
     * @return Объект ApiResponse<String>, содержащий статус и токен.
     */
    // ✅ 1. МЕНЯЕМ ВОЗВРАЩАЕМЫЙ ТИП НА УНИВЕРСАЛЬНЫЙ
    suspend fun login(login: String, passwordHash: String): ApiResponse<String> {
        val request = LoginRequest(login = login, password = passwordHash)

        try {
            val response = apiService.login(request)

            if (response.isSuccessful) {
                // ✅ 2. ИЗВЛЕКАЕМ ТЕЛО ОТВЕТА (ApiResponse<String>)
                return response.body()
                    ?: throw IOException("Пустой ответ от сервера при успешном коде.")

            } else {
                // Обработка ошибок, если код ответа не 2xx
                val errorMsg = "Ошибка HTTP ${response.code()}"

                // ВАЖНО: При ошибке 401/403/500 сервер, вероятно, не пришлет тело ApiResponse,
                // поэтому мы генерируем свою ошибку, которую обработает Activity
                throw IOException(errorMsg)
            }
        } catch (e: Exception) {
            // Ошибки сети, таймаут, или IOException, вызванный выше
            // Перебрасываем, чтобы LoginActivity обработала ее как ошибку сети
            throw e
        }
    }
}