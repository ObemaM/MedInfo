package com.example.medinfo.data.repository

import com.example.medinfo.data.network.ApiService
import com.example.medinfo.model.ApiResponse
import com.example.medinfo.model.api.GetHospitalizationsFiltersRequestDto
import com.example.medinfo.model.api.GetHospitalizationsRequestDto
import com.example.medinfo.model.api.GetHospitalizationsResponseDto
import java.io.IOException

class HospitalizationRepository(private val apiServiceService: ApiService) {

    // POST запрос для получения госпитализаций
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

        val response = apiServiceService.getHospitalizations(request)
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
