package com.example.medinfo.util

import java.security.MessageDigest
import java.math.BigInteger

fun String.toSha256(): String {
    return try {
        // Получаем экземпляр MessageDigest для SHA-256
        val md = MessageDigest.getInstance("SHA-256")

        // Вычисляем хэш
        val hash = md.digest(this.toByteArray())

        // Преобразуем байты в шестнадцатеричную строку (64 символа)
        BigInteger(1, hash).toString(16).padStart(64, '0')

    } catch (e: Exception) {
        // В случае ошибки возвращаем пустую строку
        ""
    }
}