package com.example.medinfo.data.network

import com.example.medinfo.model.ApiResponse
import com.example.medinfo.model.CallAnswerRequest
import com.example.medinfo.model.CallListContent
import com.example.medinfo.model.api.AuthRequestDto
import com.example.medinfo.model.api.CallResponseDto
import com.example.medinfo.model.api.ConfirmReceptionRequestDto
import com.example.medinfo.model.api.GetHospitalizationsRequestDto
import com.example.medinfo.model.api.GetHospitalizationsResponseDto
import com.example.medinfo.model.api.GetMessagesRequestDto
import com.example.medinfo.model.api.MessageResponseDto
import com.example.medinfo.model.api.SaveDecisionRequestDto
import com.example.medinfo.model.api.SendMessageRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    // Аутентификация
    @POST("/auth/login")
    suspend fun login(
        @Body request: AuthRequestDto
    ): Response<ApiResponse<String>>

    // Получение списка госпитализаций
    @POST("/hospitalizations/get-hospitalizations")
    suspend fun getHospitalizations(
        @Body request: GetHospitalizationsRequestDto
    ): Response<ApiResponse<GetHospitalizationsResponseDto>>

    @GET("/hospitalizations/get-in-service-calls")
    suspend fun getInServiceCalls(): Response<ApiResponse<List<CallResponseDto>>>

    // Получение истории сообщений по госпитализации
    @POST("/hospitalizations/get-messages")
    suspend fun getMessages(
        @Body request: GetMessagesRequestDto
    ): Response<ApiResponse<List<MessageResponseDto>>>

    // Отправка текстового сообщения по госпитализации
    @POST("/hospitalizations/send-message")
    suspend fun sendMessage(
        @Body request: SendMessageRequestDto
    ): Response<ApiResponse<Unit>>

    // Подтверждение получения уведомлений
    @POST("/hospitalizations/confirm-reception")
    suspend fun confirmReception(
        @Body request: ConfirmReceptionRequestDto
    ): Response<ApiResponse<Unit>>

    // Сохранение решения по госпитализации
    @POST("/hospitalizations/save-decision")
    suspend fun saveDecision(
        @Body request: SaveDecisionRequestDto
    ): Response<ApiResponse<Unit>>

    // Список вызовов по старому серверному контракту
    @GET("/api/informator/get-calls")
    suspend fun getCalls(
        @Query("pageNumber") pageNumber: Int, // Номер страницы
        @Query("pageSize") pageSize: Int, // Размер одной страницы
        @Query("getCount") getCount: Boolean // Флаг получения общего количества
    ): Response<ApiResponse<CallListContent>>

    // TODO: Ответ на вызов по старому серверному контракту
    @POST("/api/informator/answer-call")
    suspend fun answerCall(
        @Body request: CallAnswerRequest
    ): Response<ApiResponse<Void>>

    // TODO: Для ответа в звонке по старому серверному контракту
    @POST("api/calls/{id}/answer")
    suspend fun answerCallWithComment(
        @Path("id") id: Int,
        @Query("decision") decision: String,
        @Query("comment") comment: String // Добавляем поле для сообщения
    ): Response<Unit>
}
