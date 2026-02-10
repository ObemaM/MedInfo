    package com.example.medinfo.model

    // Общая структура ответа от ApiService.
    data class ApiResponse<T>(
        val success: Boolean, // Флаг, показывающий успешность операции
        val messages: List<String>, // Массив сообщений об ошибке
        val content: T? // Полезная нагрузка (например, JWT-токен или null)
    )