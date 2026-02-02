package com.example.neuroinfo.data

import com.example.neuroinfo.model.*
import java.io.IOException

class CallRepository(private val apiService: API) {

    // Получение списка вызовов
    suspend fun getCalls(pageNumber: Int, pageSize: Int, getCount: Boolean): Result<CallListContent> {
        return try {
            // response.body()?.content - это CallListContent (содержит calls и count)
            val response = apiService.getCalls(pageNumber, pageSize, getCount)

            if (response.isSuccessful && response.body()?.success == true && response.body()?.content != null) {
                Result.success(response.body()!!.content!!)
            } else {
                val errorMsg = response.body()?.messages?.firstOrNull() ?: "Ошибка ${response.code()}"
                Result.failure(IOException(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Ответ на вызов (Принять/Отказаться)
    suspend fun answerCall(callId: String, decision: String, userComment: String): Result<Unit> {
        return try {
            val request = CallAnswerRequest(callId, decision)
            val response = apiService.answerCall(request)

            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(Unit) // Успех, не возвращаем данных
            } else {
                val errorMsg = response.body()?.messages?.firstOrNull() ?: "Ошибка ${response.code()}"
                Result.failure(IOException(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}