package com.example.medinfo.data.repository

import com.example.medinfo.data.network.ApiService
import com.example.medinfo.model.ApiResponse
import com.example.medinfo.model.api.ConfirmReceptionRequestDto
import com.example.medinfo.model.api.GetHospitalizationsFiltersRequestDto
import com.example.medinfo.model.api.GetHospitalizationsRequestDto
import com.example.medinfo.model.api.GetHospitalizationsResponseDto
import com.example.medinfo.model.api.GetMessagesRequestDto
import com.example.medinfo.model.api.MessageResponseDto
import com.example.medinfo.model.api.ReceptionNotificationType
import com.example.medinfo.model.api.SaveDecisionRequestDto
import com.example.medinfo.model.api.SendMessageRequestDto
import java.io.IOException
import retrofit2.Response

class HospitalizationRepository(private val apiServiceService: ApiService) {

    // POST запрос для получения списка госпитализаций
    suspend fun getHospitalizations(
        pageNumber: Int,
        pageSize: Int,
        getCount: Boolean,
        filters: GetHospitalizationsFiltersRequestDto? = null
    ): ApiResponse<GetHospitalizationsResponseDto> {
        val request = GetHospitalizationsRequestDto(
            pageNumber = pageNumber,
            pageSize = pageSize,
            getCount = getCount,
            filters = filters
        )

        return handleApiResponse(
            response = apiServiceService.getHospitalizations(request)
        )
    }

    // POST запрос для получения истории сообщений по госпитализации
    suspend fun getMessages(
        hospitalizationId: String
    ): ApiResponse<List<MessageResponseDto>> { // Массив записей
        val request = GetMessagesRequestDto(hospitalizationId = hospitalizationId)

        return handleApiResponse(
            response = apiServiceService.getMessages(request)
        )
    }

    // POST запрос для отправки текстового сообщения по госпитализации
    suspend fun sendMessage(
        hospitalizationId: String,
        messageText: String
    ): ApiResponse<Unit> {
        val request = SendMessageRequestDto(
            hospitalizationId = hospitalizationId,
            messageText = messageText
        )

        return handleApiResponse(
            response = apiServiceService.sendMessage(request)
        )
    }

    // POST запрос для подтверждения получения уведомлений
    suspend fun confirmReception(
        type: ReceptionNotificationType,
        ids: List<String>
    ): ApiResponse<Unit> {
        val request = ConfirmReceptionRequestDto(
            type = type.id,
            ids = ids
        )

        return handleApiResponse(
            response = apiServiceService.confirmReception(request)
        )
    }

    // POST запрос для сохранения решения по госпитализации
    suspend fun saveDecision(
        hospitalizationId: String,
        decisionId: Int
    ): ApiResponse<Unit> {
        val request = SaveDecisionRequestDto(
            hospitalizationId = hospitalizationId,
            decisionId = decisionId
        )

        return handleApiResponse(
            response = apiServiceService.saveDecision(request)
        )
    }

    private fun <T> handleApiResponse(response: Response<ApiResponse<T>>): ApiResponse<T> {
        val body = response.body()

        if (response.isSuccessful && body?.success == true) {
            return body
        }

        if (response.isSuccessful && body == null) {
            throw IOException("Пустой ответ от сервера")
        }

        // Если сервер вернул текст ошибки в стандартной обертке, используем его
        val errorMessage = body?.messages?.firstOrNull()
        throw IOException(errorMessage ?: "Ошибка HTTP ${response.code()}")
    }
}
