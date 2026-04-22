package com.example.medinfo.model.api

// POST /auth/login
data class AuthRequestDto(
    val login: String,
    val password: String
)
