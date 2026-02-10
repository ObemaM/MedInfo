package com.example.medinfo.data.network

import com.example.medinfo.model.ApiResponse
import com.example.medinfo.model.CallAnswerRequest
import com.example.medinfo.model.CallListContent
import com.example.medinfo.model.LoginRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

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

    // TODO: Ответ на вызов
    @POST("/api/informator/answer-call")
    suspend fun answerCall(
        @Body request: CallAnswerRequest
    ): Response<ApiResponse<Void>>

    // TODO: Для ответа в звонке
    @POST("api/calls/{id}/answer")
    suspend fun answerCallWithComment(
        @Path("id") id: Int,
        @Query("decision") decision: String,
        @Query("comment") comment: String // Добавляем поле для сообщения
    ): Response<Unit>
}