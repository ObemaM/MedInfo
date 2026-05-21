package com.example.medinfo.util

import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

object DateFormatter {
    private val inputFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    }
    private val outputFormat = ThreadLocal.withInitial {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    }
    private val apiOutputFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    }

    // Расшифровка даты
    fun formatDateTime(dateTime: String?): String {

        if (dateTime.isNullOrEmpty()) return "Время не указано"

        try {
            val normalized =
                    if (dateTime.length >= 19) {
                        dateTime.take(19)

                    } else {
                        dateTime
                    }
            val date = inputFormat.get()?.parse(normalized)
            return if (date != null) {
                outputFormat.get()?.format(date) ?: dateTime
            } else {
                dateTime
            }
        }

        // Если что-то пошло не так, то возвращаем в исходном виде
        catch (e: Exception) {
            return dateTime
        }
    }

    // Парсит дату вызова в миллисекунды (для фильтрации по дате)
    fun parseCallTimeMillis(callTime: String?): Long? {
        if (callTime.isNullOrBlank()) return null

        parseIsoOffsetMillis(callTime)?.let { return it }
        parseIsoLocalMillis(callTime)?.let { return it }

        return parseLegacyMillis(callTime)
    }

    fun formatApiDateTime(millis: Long?): String? {
        if (millis == null) return null
        return apiOutputFormat.get()?.format(Date(millis))
    }

    private fun parseIsoOffsetMillis(value: String): Long? {
        return try {
            OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .toInstant()
                .toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }

    private fun parseIsoLocalMillis(value: String): Long? {
        return try {
            LocalDateTime.parse(value.take(19), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }

    private fun parseLegacyMillis(value: String): Long? {
        return try {
            // Если длина даты больше 19, то сокращаем до 19, чтобы точно парсилось
            val normalized =
                if (value.length >= 19) {
                    value.take(19)
                } else {
                    value
                }
            inputFormat.get()?.parse(normalized)?.time
        } catch (_: Exception) {
            null
        }
    }
}
