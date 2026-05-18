package com.example.medinfo.ui.main

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.medinfo.config.ConfigManager
import com.example.medinfo.data.manager.CallsManager
import com.example.medinfo.data.manager.HospitalizationEventBus
import com.example.medinfo.data.network.RetrofitClient
import com.example.medinfo.data.network.TokenInterceptor
import com.example.medinfo.data.repository.HospitalizationRepository
import com.example.medinfo.model.Hospitalization
import com.example.medinfo.model.api.GetHospitalizationsFiltersRequestDto
import com.example.medinfo.model.api.HospitalizationDecision
import com.example.medinfo.model.api.HospitalizationResponseDto
import com.example.medinfo.model.api.HospitalizationStatus
import com.example.medinfo.ui.incoming.IncomingCallRinger
import com.example.medinfo.util.CallLog
import com.example.medinfo.util.DateFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // Загрузка данных с нового API госпитализаций
    private val hospitalizationRepository = HospitalizationRepository(RetrofitClient.apiServiceService)

    // Какой список сейчас показываем: требуют решения, активные или архив
    enum class TabFilter {
        REQUIRES_DECISION,
        ACTIVE,
        ARCHIVE
    }

    // Полный список текущей серверной выборки
    private val allCalls = mutableListOf<Hospitalization>()

    // Номер текущей загруженной страницы
    private var currentPage = FIRST_PAGE

    // Размер страницы, которую запрашиваем у сервера
    private val pageSize = DEFAULT_PAGE_SIZE

    // Защита от повторной одновременной подгрузки
    private var isLoadingNextPage = false

    // Есть ли у сервера еще страницы для загрузки
    private var hasMorePages = true

    // Уже загруженные элементы (серверные страницы + realtime-добавления)
    private val loadedCalls = mutableListOf<Hospitalization>()

    // Сколько элементов реально пришло с сервера постранично
    private var serverLoadedCount = 0

    // Отслеживание выбранной вкладки
    private var currentTabFilter = TabFilter.ACTIVE

    // Решает, какую вкладку открыть при старте: REQUIRES_DECISION если есть такие вызовы,
    // иначе ACTIVE. Фильтрация по PATIENT_CONDITION (если включён режим) делается на сервере,
    // поэтому достаточно проверить count в ответе.
    suspend fun resolveStartTab(): TabFilter {
        return try {
            val response = withContext(Dispatchers.IO) {
                hospitalizationRepository.getHospitalizations(
                    pageNumber = FIRST_PAGE,
                    pageSize = 1,
                    getCount = true,
                    filters = createServerFilters(TabFilter.REQUIRES_DECISION)
                )
            }

            if ((response.content?.count ?: 0) > 0) {
                TabFilter.REQUIRES_DECISION
            } else {
                TabFilter.ACTIVE
            }
        } catch (e: Exception) {
            _toastMessage.emit("Не удалось проверить вызовы, требующие решения")
            TabFilter.ACTIVE
        }
    }

    private fun activeStatusIds(): List<Int> {
        return listOf(
            HospitalizationStatus.CREW_EN_ROUTE.id,
            HospitalizationStatus.CREW_ON_SITE.id
        )
    }

    private fun isPatientConditionDecisionMode(): Boolean {
        return ConfigManager.decisionTriggerMode == ConfigManager.DecisionTriggerMode.PATIENT_CONDITION
    }


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

    init {
        // Список слушает realtime-обновления госпитализаций из SignalR и обновляет статусы.
        viewModelScope.launch {
            HospitalizationEventBus.updates.collectLatest { applyRealtimeHospitalizationUpdates(it) }
        }
    }

    // Загрузка для первой страницы
    fun fetchCalls() {
        // Очищаем список loadedCalls и начинаем с первой страницы
        currentPage = FIRST_PAGE
        hasMorePages = true
        isLoadingNextPage = false
        loadedCalls.clear()

        loadPage(page = FIRST_PAGE, resetBeforeLoad = true)
    }

    // Загрузка для следующих страниц
    fun loadNextPage() {
        if (isLoadingNextPage || !hasMorePages) return
        loadPage(page = currentPage + 1, resetBeforeLoad = false)
    }

    private fun loadPage(page: Int, resetBeforeLoad: Boolean) {
        viewModelScope.launch {
            // Если и так идет загрузка, то выходим из корутины, не запуская еще одну
            if (isLoadingNextPage) return@launch

            isLoadingNextPage = true

            try {
                val response = withContext(Dispatchers.IO) {
                    hospitalizationRepository.getHospitalizations(
                        pageNumber = page,
                        pageSize = pageSize,
                        getCount = true,
                        filters = createServerFilters(currentTabFilter)
                    )
                }

                val content = response.content
                // Фильтрация по PATIENT_CONDITION делается через filters.hasPatientCondition
                val hospitalizations = content?.hospitalizations.orEmpty()
                // Кэшируем загруженные госпитализации для realtime-сценария: активный вызов уже есть
                // в списке, а сообщение с данными пациента приходит позже и должно поднять его в решения.
                HospitalizationEventBus.remember(hospitalizations)
                CallLog.event(
                    source = "MainViewModel",
                    message = "loaded hospitalizations tab=$currentTabFilter page=$page count=${hospitalizations.size} total=${content?.count ?: "unknown"}"
                )

                if (currentTabFilter == TabFilter.REQUIRES_DECISION) {
                    CallsManager.syncDecisionCallsFromServer(hospitalizations)
                }

                val newCalls = hospitalizations.map { it.toUiHospitalization() }

                if (resetBeforeLoad){
                    loadedCalls.clear()
                    serverLoadedCount = 0
                }

                // Добавляем к общему списку вызовов новые вызовы
                loadedCalls.addAll(newCalls)
                serverLoadedCount += newCalls.size
                currentPage = page

                val totalCount = content?.count

                // Проверка можно ли подгружать новые страницы
                hasMorePages =
                    if (totalCount != null) {
                        serverLoadedCount < totalCount
                    } else {
                        // Если запросили 20, а пришло 7, значит больше вызовов нет => hasMorePages = false
                        newCalls.size >= pageSize
                    }

                updateCalls(loadedCalls.toList())
            } catch (e: Exception) {
                if (resetBeforeLoad) {
                    updateCalls(emptyList())
                }

                _toastMessage.emit(
                    "Ошибка сети при загрузке списка госпитализаций: ${e.message ?: "неизвестная ошибка"}"
                )
            } finally {
                isLoadingNextPage = false
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

    fun refreshDecisionTimers() {
        if (currentTabFilter != TabFilter.REQUIRES_DECISION) return

        // Пересчет идет локально раз в секунду: сервер заново дергать для таймера не нужно.
        allCalls.forEach { call ->
            call.decisionRemainingMillis = call.details?.let { details ->
                calculateDecisionRemainingMillis(details)
            }
        }
        applyFilters()
    }

    fun logout() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            IncomingCallRinger.stop()
            CallsManager.clearAll()
            TokenInterceptor.clearToken(app)
            val sharedPrefs = app.getSharedPreferences("app_session", Context.MODE_PRIVATE)
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
                    statuses = activeStatusIds(),
                    decisions = listOf(HospitalizationDecision.NONE.id),
                    // В режиме PATIENT_CONDITION сервер сам отдаёт только вызовы с данными пациента.
                    hasPatientCondition = if (isPatientConditionDecisionMode()) true else null
                )

            TabFilter.ACTIVE ->
                GetHospitalizationsFiltersRequestDto(
                    statuses = activeStatusIds()
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

    // Точечно обновляет уже загруженные вызовы данными realtime-уведомлений SignalR.
    // Пагинация и позиция прокрутки не сбрасываются: заменяем только реально изменившиеся вызовы.
    // Если статус вызова сменился и он больше не подходит вкладке, applyFilters его уберёт.
    private fun applyRealtimeHospitalizationUpdates(updated: List<HospitalizationResponseDto>) {
        var changed = false
        updated.forEach { dto ->
            val index = loadedCalls.indexOfFirst { it.id == dto.id }
            if (index != -1) {
                // Вызов уже в списке — обновляем, только если реально изменился.
                if (loadedCalls[index].details != dto) {
                    loadedCalls[index] = dto.toUiHospitalization()
                    changed = true
                }
            } else {
                // Вызова в списке нет — добавляем.
                loadedCalls.add(dto.toUiHospitalization())
                changed = true
            }
        }
        if (changed) {
            updateCalls(loadedCalls.toList())
        }
    }

    private fun applyFilters() {
        val lowerCaseQuery = currentSearchQuery.lowercase().trim()

        _isFilterActive.value = currentFilters.isActive()

        val baseListWithTabFilter = allCalls.filter { call ->
            matchesCurrentTab(call)
        }

        val baseListWithCustomFilters =
            baseListWithTabFilter.filter { call ->
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

        _filteredCalls.value = sortForCurrentTab(filteredList)
    }

    private fun matchesCurrentTab(call: Hospitalization): Boolean {
        val details = call.details
        return when (currentTabFilter) {
            TabFilter.REQUIRES_DECISION ->
                call.decisionId == HospitalizationDecision.NONE.id &&
                    details?.let { HospitalizationStatus.fromId(it.statusId)?.isActive == true } != false

            TabFilter.ACTIVE ->
                details?.let { HospitalizationStatus.fromId(it.statusId)?.isActive == true }
                    ?: !call.isArchived

            TabFilter.ARCHIVE ->
                details?.let { HospitalizationStatus.fromId(it.statusId)?.isArchive == true }
                    ?: call.isArchived
        }
    }

    private fun sortForCurrentTab(calls: List<Hospitalization>): List<Hospitalization> {
        return when (currentTabFilter) {
            TabFilter.REQUIRES_DECISION ->
                calls.sortedWith(
                    // Врачу сначала показываем вызовы, у которых быстрее закончится время на решение.
                    compareBy<Hospitalization> {
                        it.decisionRemainingMillis ?: Long.MAX_VALUE
                    }.thenBy {
                        it.creationSortMillis()
                    }
                )

            TabFilter.ACTIVE,
            TabFilter.ARCHIVE -> sortByCreationTimeNewestFirst(calls)
        }
    }

    // Сначала элементы с валидной датой (свежие вверху), затем элементы без даты
    // в стабильном порядке по id. Long.MAX_VALUE используется как "хвост" для битых дат.
    private fun sortByCreationTimeNewestFirst(calls: List<Hospitalization>): List<Hospitalization> {
        return calls.sortedWith(
            compareBy<Hospitalization> { it.creationSortMillis() == Long.MAX_VALUE }
                .thenByDescending { it.creationSortMillis() }
                .thenByDescending { it.id }
        )
    }

    // Время создания госпитализации — основной ключ. Если его нет, fallback на callTime.
    // Если оба пустые/невалидные — Long.MAX_VALUE, чтобы сортировка отправила вниз.
    private fun Hospitalization.creationSortMillis(): Long {
        return DateFormatter.parseCallTimeMillis(creationTime)
            ?: DateFormatter.parseCallTimeMillis(callTime)
            ?: Long.MAX_VALUE
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
            creationTime = creationTime,
            urgency = responseCall.urgency,
            isNotificationSent = isNotificationSent,
            isArchived = HospitalizationStatus.fromId(statusId)?.isArchive == true,
            decisionId = decisionId,
            decisionName = decisionName,
            decisionRemainingMillis = calculateDecisionRemainingMillis(this),
            details = this
        )
    }

    private fun calculateDecisionRemainingMillis(
        hospitalization: HospitalizationResponseDto
    ): Long? {
        // Если вызов уже есть в локальной очереди, используем ее монотонный таймер без скачков системного времени.
        CallsManager.getRemainingIgnoreMillis(hospitalization.id)?.let { return it }

        // Первичный расчет нужен только для серверных уведомлений с известным notificationTime.
        // Если времени уведомления нет, CallsManager создаст локальный якорь при получении вызова.
        val startedAtMillis = DateFormatter.parseCallTimeMillis(hospitalization.notificationTime)
            ?: return null

        val deadlineMillis = startedAtMillis + ConfigManager.maxCallDurationMs
        return (deadlineMillis - System.currentTimeMillis())
            .coerceIn(0L, ConfigManager.maxCallDurationMs)
    }

    companion object {
        private const val FIRST_PAGE = 1
        const val DEFAULT_PAGE_SIZE = 40
    }
}
