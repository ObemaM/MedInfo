package com.example.medinfo.data.repository

import com.example.medinfo.data.network.ApiService
import com.example.medinfo.model.CallAnswerRequest
import com.example.medinfo.model.CallListContent
import java.io.IOException

class CallRepository(private val apiServiceService: ApiService) {

    // Получение списка вызовов
    suspend fun getCalls(pageNumber: Int, pageSize: Int, getCount: Boolean): Result<CallListContent> {
        val response = apiServiceService.getCalls(pageNumber, pageSize, getCount)

        // response.body - это CallListContent (содержит calls и count)
        val body = response.body()

        // Проверка на то, все ли нормально с JSON
        return if (response.isSuccessful && body?.success == true && body.content != null) {
            Result.success(body.content)
        } else {
            val errorMsg = body?.messages?.firstOrNull() ?: "Ошибка ${response.code()}"
            Result.failure(IOException(errorMsg))
        }
    }

    // TODO: Ответ на вызов (Принять/Отказаться)
    suspend fun answerCall(callId: String, decision: String, userComment: String): Result<Unit> {
        val request = CallAnswerRequest(callId, decision)
        val response = apiServiceService.answerCall(request)

        val body = response.body()

        // Проверка на то, все ли нормально с JSON
        return if (response.isSuccessful && body?.success == true) {
            Result.success(Unit)
        } else {
            val errorMsg = body?.messages?.firstOrNull() ?: "Ошибка ${response.code()}"
            Result.failure(IOException(errorMsg))
        }
    }
}