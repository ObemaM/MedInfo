package com.example.neuroinfo.util

import java.security.MessageDigest
import java.math.BigInteger

/**
 * Расширение для String, вычисляет хэш SHA256.
 *
 * @return 64-символьная шестнадцатеричная строка.
 */
fun String.toSha256(): String {
    return try {
        // Получаем экземпляр MessageDigest для SHA-256
        val md = MessageDigest.getInstance("SHA-256")

        // Вычисляем хэш
        val hash = md.digest(this.toByteArray())

        // Преобразуем байты в 16-ричную строку (64 символа)
        BigInteger(1, hash).toString(16).padStart(64, '0')

    } catch (e: Exception) {
        e.printStackTrace()
        // В случае ошибки возвращаем пустую строку
        ""
    }
}