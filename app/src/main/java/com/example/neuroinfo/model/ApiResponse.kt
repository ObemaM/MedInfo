    package com.example.neuroinfo.model

    /**
     * Общая структура ответа от API.
     */
    data class ApiResponse<T>(
        val success: Boolean, // Флаг, показывающий успешность операции [cite: 344]
        val messages: List<String>, // Массив сообщений об ошибке [cite: 344]
        val content: T? // Полезная нагрузка (например, JWT-токен или null) [cite: 344]
    )