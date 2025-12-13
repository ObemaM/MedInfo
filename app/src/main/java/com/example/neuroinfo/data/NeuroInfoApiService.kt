package com.example.neuroinfo.data

import com.example.neuroinfo.model.*
import retrofit2.http.*
import retrofit2.Response

/**
 * Интерфейс Retrofit, определяющий все конечные точки для NeuroInfo API.
 */
interface NeuroInfoApiService {

    // 1. АУТЕНТИФИКАЦИЯ
    @POST("/api/informator/login")
    suspend fun login(
        @Body request: LoginRequest // Json body
    ): Response<ApiResponse<String>> // <-- Обратите внимание на закрывающую скобку здесь!

    // 2. ПОЛУЧЕНИЕ СПИСКА ВЫЗОВОВ
    @GET("/api/informator/get-calls")
    suspend fun getCalls(
        @Query("pageNumber") pageNumber: Int, // Номер страницы
        @Query("pageSize") pageSize: Int, // Размер одной страницы
        @Query("getCount") getCount: Boolean // Флаг получения общего количества
    ): Response<CallListResponse> // <-- Обратите внимание на закрывающую скобку здесь!

    // 3. ОТВЕТ НА ВЫЗОВ
    @POST("/api/informator/answer-call")
    suspend fun answerCall(
        @Body request: CallAnswerRequest
    ): Response<ApiResponse<Void>>
}