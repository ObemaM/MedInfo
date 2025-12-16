package com.example.neuroinfo.data

import com.example.neuroinfo.model.*
import retrofit2.http.*
import retrofit2.Response


interface API {

    // Логин
    @POST("/api/informator/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<ApiResponse<String>>

    // Список вызовов
    @GET("/api/informator/get-calls")
    suspend fun getCalls(
        @Query("pageNumber") pageNumber: Int, // Номер страницы
        @Query("pageSize") pageSize: Int, // Размер одной страницы
        @Query("getCount") getCount: Boolean // Флаг получения общего количества
    ): Response<ApiResponse<CallListContent>>

    // Ответ на вызов
    @POST("/api/informator/answer-call")
    suspend fun answerCall(
        @Body request: CallAnswerRequest
    ): Response<ApiResponse<Void>>
}