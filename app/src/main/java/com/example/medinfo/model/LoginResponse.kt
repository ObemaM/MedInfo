package com.example.medinfo.model

data class LoginResponse(
    // Флаг, показывающий успешность операции
    val success: Boolean = false,
    // Массив сообщений об ошибке (при success = false)
    val messages: List<String>?,
    // JWT-токен при успешной аутентификации (при success = true)
    val content: String?
)