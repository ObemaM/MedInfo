package com.example.neuroinfo.model

data class LoginRequest(
    // Логин пользователя
    val login: String,
    // Хэш пароля SHA256 (вычисляется в Activity)
    val password: String
)