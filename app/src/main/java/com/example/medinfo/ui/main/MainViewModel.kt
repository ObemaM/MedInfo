package com.example.medinfo.ui.main

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.medinfo.data.cache.CallsCache
import com.example.medinfo.data.manager.CallsManager
import com.example.medinfo.data.network.RetrofitClient
import com.example.medinfo.data.network.TokenInterceptor
import com.example.medinfo.data.repository.HospitalizationRepository
import com.example.medinfo.model.Hospitalization
import com.example.medinfo.model.api.GetHospitalizationsFiltersRequestDto
import com.example.medinfo.model.api.HospitalizationDecision
import com.example.medinfo.model.api.HospitalizationResponseDto
import com.example.medinfo.model.api.HospitalizationStatus
import com.example.medinfo.ui.incoming.IncomingCallRinger
import com.example.medinfo.util.DateFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // Загрузка данных с нового API госпитализаций
    private val hospitalizationRepository = HospitalizationRepository(RetrofitClient.apiServiceService)

    // Кэш пока нужен для очистки при выходе из аккаунта и legacy-сценариев
    private val callsCache = CallsCache(application)

    // Какой список сейчас показываем: требуют решения, активные или архив
    enum class TabFilter {
        REQUIRES_DECISION,
        ACTIVE,
        ARCHIVE
    }

    // Полный список текущей серверной выборки
    private val allCalls = mutableListOf<Hospitalization>()

    // Отслеживание выбранной вкладки
    private var currentTabFilter = TabFilter.REQUIRES_DECISION

    // Отслеживание поисковой строки
    private var currentSearchQuery = ""

    // Текущие пользовательские фильтры
    private var currentFilters = CallFilters()

    // filteredCalls для RecyclerView
    private val _filteredCalls = MutableStateFlow<List<Hospitalization>>(emptyList())
    val filteredCalls: StateFlow<List<Hospitalization>> = _filteredCalls.asStateFlow()

    private val _isFilterActive = MutableStateFlow(false)
    val isFilterActive: StateFlow<Boolean> = _isFilterActive.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private val _logoutEvent = MutableSharedFlow<Unit>()
    val logoutEvent: SharedFlow<Unit> = _logoutEvent.asSharedFlow()

    // Метод оставлен со старым именем, чтобы пока не ломать MainActivity
    fun fetchCalls(forceFullReload: Boolean = false) {
        viewModelScope.launch {
            try {
                val pageSize = if (forceFullReload) FULL_RELOAD_PAGE_SIZE else DEFAULT_PAGE_SIZE
                val response = withContext(Dispatchers.IO) {
                    hospitalizationRepository.getHospitalizations(
                        pageNumber = FIRST_PAGE,
                        pageSize = pageSize,
                        getCount = true,
                        filters = createServerFilters(currentTabFilter)
                    )
                }

                val hospitalizations = response.content
                    ?.hospitalizations
                    ?.map { it.toUiHospitalization() }
                    .orEmpty()

                updateCalls(hospitalizations)
            } catch (e: Exception) {
                updateCalls(emptyList())
                _toastMessage.emit(
                    "Ошибка сети при загрузке списка госпитализаций: ${e.message ?: "неизвестная ошибка"}"
                )
            }
        }
    }

    fun setTabFilter(tab: TabFilter) {
        currentTabFilter = tab
        fetchCalls()
    }

    fun setSearchQuery(query: String) {
        currentSearchQuery = query
        applyFilters()
    }

    fun setCustomFilters(filters: CallFilters) {
        currentFilters = filters
        applyFilters()
    }

    fun logout() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            IncomingCallRinger.stop()
            CallsManager.clearAll()
            TokenInterceptor.clearToken(app)
            val sharedPrefs = app.getSharedPreferences("app_session", Context.MODE_PRIVATE)
            sharedPrefs.getString("user_login", null)?.let { login ->
                callsCache.clear(login)
            }
            sharedPrefs.edit().remove("isLoggedIn").remove("user_login").apply()
            _logoutEvent.emit(Unit)
        }
    }

    fun getCurrentFilters(): CallFilters = currentFilters

    fun getUserLogin(): String? {
        return getApplication<Application>()
            .getSharedPreferences("app_session", Context.MODE_PRIVATE)
            .getString("user_login", null)
    }

    // Формируем серверные фильтры для рабочих вкладок
    private fun createServerFilters(tab: TabFilter): GetHospitalizationsFiltersRequestDto {
        return when (tab) {
            TabFilter.REQUIRES_DECISION ->
                GetHospitalizationsFiltersRequestDto(
                    decisions = listOf(HospitalizationDecision.NONE.id),

                    // Проверяем, чтобы вызовы не были завершенными
                    statuses = listOf(
                        HospitalizationStatus.CREW_EN_ROUTE.id,
                        HospitalizationStatus.CREW_ON_SITE.id
                    )
                )

            TabFilter.ACTIVE ->
                GetHospitalizationsFiltersRequestDto(
                    statuses = listOf(
                        HospitalizationStatus.CREW_EN_ROUTE.id,
                        HospitalizationStatus.CREW_ON_SITE.id
                    )
                )

            TabFilter.ARCHIVE ->
                GetHospitalizationsFiltersRequestDto(
                    statuses = listOf(
                        HospitalizationStatus.COMPLETED.id,
                        HospitalizationStatus.REFERRED_TO_OTHER_LPU.id
                    )
                )
        }
    }

    private fun updateCalls(calls: List<Hospitalization>) {
        allCalls.clear()
        allCalls.addAll(calls)
        prepareCallsForSearch(allCalls)
        applyFilters()
    }

    private fun applyFilters() {
        val lowerCaseQuery = currentSearchQuery.lowercase().trim()

        _isFilterActive.value = currentFilters.isActive()

        val baseListWithCustomFilters =
            allCalls.filter { call ->
                matchesCustomFilters(call, currentFilters)
            }

        val filteredList =
            if (lowerCaseQuery.isEmpty()) {
                baseListWithCustomFilters
            } else {
                baseListWithCustomFilters.filter { call ->
                    if (call.searchCache.isNullOrBlank()) {
                        call.searchCache = buildSearchIndex(call)
                    }
                    call.searchCache?.contains(lowerCaseQuery) == true
                }
            }

        _filteredCalls.value = filteredList
    }

    private fun matchesCustomFilters(
        call: Hospitalization,
        filters: CallFilters
    ): Boolean {
        if (filters.urgencyFrom != null) {
            val urgency = call.urgency ?: return false
            if (urgency < filters.urgencyFrom) return false
        }
        if (filters.urgencyTo != null) {
            val urgency = call.urgency ?: return false
            if (urgency > filters.urgencyTo) return false
        }

        if (filters.sex != SexFilter.ANY) {
            val sex = call.sex?.lowercase(Locale.getDefault())?.trim().orEmpty()
            val isMale = sex.contains("муж") || sex.contains("male") || sex == "м"
            val isFemale = sex.contains("жен") || sex.contains("female") || sex == "ж"
            when (filters.sex) {
                SexFilter.MALE -> if (!isMale) return false
                SexFilter.FEMALE -> if (!isFemale) return false
                SexFilter.ANY -> Unit
            }
        }

        val age = parseAge(call.age)
        if (filters.ageFrom != null) {
            val value = age ?: return false
            if (value < filters.ageFrom) return false
        }
        if (filters.ageTo != null) {
            val value = age ?: return false
            if (value > filters.ageTo) return false
        }

        if (filters.dateFromMillis != null || filters.dateToMillis != null) {
            val callMillis = DateFormatter.parseCallTimeMillis(call.callTime) ?: return false
            if (filters.dateFromMillis != null && callMillis < filters.dateFromMillis) return false
            if (filters.dateToMillis != null && callMillis > filters.dateToMillis) return false
        }

        if (filters.callDayFrom != null || filters.callDayTo != null) {
            val day = call.dayNumber ?: return false
            if (filters.callDayFrom != null && day < filters.callDayFrom) return false
            if (filters.callDayTo != null && day > filters.callDayTo) return false
        }

        if (filters.callYearFrom != null || filters.callYearTo != null) {
            val year = call.yearNumber ?: return false
            if (filters.callYearFrom != null && year < filters.callYearFrom) return false
            if (filters.callYearTo != null && year > filters.callYearTo) return false
        }

        return true
    }

    private fun parseAge(age: String?): Int? {
        if (age.isNullOrBlank()) return null
        return age.trim().toIntOrNull()
    }

    private fun prepareCallsForSearch(calls: List<Hospitalization>) {
        calls.forEach { call ->
            if (call.formattedCallTime.isNullOrBlank()) {
                call.formattedCallTime = DateFormatter.formatDateTime(call.callTime)
            }
            if (call.searchCache.isNullOrBlank()) {
                call.searchCache = buildSearchIndex(call)
            }
        }
    }

    private fun buildSearchIndex(call: Hospitalization): String {
        val age = call.age?.trim().orEmpty()
        val ageVariants =
            if (age.isNotEmpty()) {
                listOf("$age лет", "$age год", "$age года")
            } else {
                emptyList()
            }

        val formattedTime =
            call.formattedCallTime
                ?: DateFormatter.formatDateTime(call.callTime).also { value ->
                    call.formattedCallTime = value
                }

        val callNumber =
            if (call.dayNumber != null && call.yearNumber != null) {
                "${call.dayNumber}/${call.yearNumber}"
            } else {
                null
            }

        return buildList {
            add(call.patientFullName)
            add(call.patientName)
            add(call.patientSurname)
            add(call.patientPatronymic)
            add(age)
            addAll(ageVariants)
            add(call.sex)
            add(call.reason)
            add(call.yearNumber)
            add(call.dayNumber)
            add(callNumber)
            add(call.district)
            add(call.point)
            add(call.street)
            add(call.house)
            add(call.apartment)
            add(call.comment)
            add(formattedTime)
        }
            .filterNotNull()
            .joinToString(separator = " ")
            .lowercase()
    }

    // Временный mapper: новый DTO приводим к старой UI-модели, чтобы не переписывать весь экран сразу
    private fun HospitalizationResponseDto.toUiHospitalization(): Hospitalization {
        val responseCall = call

        return Hospitalization(
            id = id,
            patientFullName = listOfNotNull(
                responseCall.patientSurname,
                responseCall.patientName,
                responseCall.patientPatronymic
            ).joinToString(" ").ifBlank { null },
            patientName = responseCall.patientName,
            patientSurname = responseCall.patientSurname,
            patientPatronymic = responseCall.patientPatronymic,
            age = responseCall.age,
            sex = responseCall.sex,
            reason = responseCall.reason,
            additionalInfo = responseCall.additionalInfo,
            district = responseCall.district,
            point = responseCall.point,
            street = responseCall.street,
            house = responseCall.house,
            apartment = responseCall.apartment,
            entrance = responseCall.entrance,
            comment = responseCall.comment,
            longitude = responseCall.longitude,
            latitude = responseCall.latitude,
            brigadeNumber = responseCall.brigadeNumber,
            brigadeProfile = responseCall.brigadeProfile,
            seniorFullName = responseCall.seniorFullName,
            dayNumber = responseCall.dayNumber,
            yearNumber = responseCall.yearNumber,
            status = statusName,
            callTime = responseCall.callTime,
            urgency = responseCall.urgency,
            isArchived = HospitalizationStatus.fromId(statusId)?.isArchive == true
        )
    }

    private companion object {
        const val FIRST_PAGE = 1
        const val DEFAULT_PAGE_SIZE = 20
        const val FULL_RELOAD_PAGE_SIZE = 200
    }
}
