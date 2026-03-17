package com.example.medinfo.ui.main

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.medinfo.data.repository.CallRepository
import com.example.medinfo.data.cache.CallsCache
import com.example.medinfo.data.manager.CallsManager
import com.example.medinfo.data.network.RetrofitClient
import com.example.medinfo.data.network.TokenInterceptor
import com.example.medinfo.model.Hospitalization
import com.example.medinfo.util.DateFormatter
import com.example.medinfo.ui.incoming.IncomingCallRinger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // Загрузка данных с API
    private val callRepository = CallRepository(RetrofitClient.apiServiceService)

    // Кэш
    private val callsCache = CallsCache(application)

    // Какой список сейчас показываем: Активные или Архив
    enum class TabFilter {
        ACTIVE,
        ARCHIVE
    }

    // Полный список всех вызовов
    private val allCalls = mutableListOf<Hospitalization>()

    // Отслеживание выбранной вкладки (активное/архив)
    private var currentTabFilter = TabFilter.ACTIVE

    // Отслеживание поисковой строки
    private var currentSearchQuery = ""

    // Текущие пользовательские фильтры
    private var currentFilters = CallFilters()


    // Состояния для UI

    // filteredCalls для RecyclerView
    private val _filteredCalls = MutableStateFlow<List<Hospitalization>>(emptyList())

    // Для UI
    val filteredCalls: StateFlow<List<Hospitalization>> = _filteredCalls.asStateFlow()

    private val _isFilterActive = MutableStateFlow(false)
    val isFilterActive: StateFlow<Boolean> = _isFilterActive.asStateFlow()

    // Уведомление

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    // Событие выхода из аккаунта
    private val _logoutEvent = MutableSharedFlow<Unit>()
    val logoutEvent: SharedFlow<Unit> = _logoutEvent.asSharedFlow()

    // Загрузка вызовов
    // forceFullReload=true - загружает 2000 записей
    // forceFullReload=false - загружает 500 записей (для оптиммизации)
    fun fetchCalls(forceFullReload: Boolean = false) {
        viewModelScope.launch {
            try {
                val userLogin = getUserLogin()

                val cachedCalls =
                    if (!userLogin.isNullOrBlank()) {
                        withContext(Dispatchers.IO) {
                            callsCache.readCalls(userLogin)
                        }
                    }
                    else {
                        null
                    }

                // Если кэш есть - показываем сразу
                if (!cachedCalls.isNullOrEmpty()) {
                    updateCalls(cachedCalls)
                }

                // Определяем размер страницы: 500 если кэш есть и не требуется полная загрузка
                val pageSize = if (!cachedCalls.isNullOrEmpty() && !forceFullReload) {
                    500  // Новые записи
                } else {
                    2000 // Полная загрузка
                }

                // Параллельно запрашиваем данные с API
                val apiResult =
                    withContext(Dispatchers.IO) {
                        callRepository.getCalls(
                                pageNumber = 1,
                                pageSize = pageSize,
                                getCount = true
                        )
                    }

                if (apiResult.isSuccess) {
                    val apiCalls = apiResult.getOrThrow().calls
                    val callsToShow =
                        if (cachedCalls.isNullOrEmpty()) {
                            apiCalls
                        } else {
                            mergeCalls(apiCalls, cachedCalls)
                        }

                    // Очищаем старые записи (старше 2 месяцев)
                    val filteredCalls = callsCache.cleanupOldCache(callsToShow)
                    updateCalls(filteredCalls)

                    if (!userLogin.isNullOrBlank()) {
                        withContext(Dispatchers.IO) {
                            callsCache.writeCalls(userLogin, filteredCalls)
                        }
                    }
                } else {
                    val error =
                            apiResult.exceptionOrNull()?.message
                                    ?: "Не удалось загрузить список вызовов."
                    _toastMessage.emit("Ошибка: $error")

                    if (cachedCalls.isNullOrEmpty()) {
                        updateCalls(emptyList())
                    }
                }
            } catch (e: Exception) {
                _toastMessage.emit("Ошибка сети при загрузке данных.")
            }
        }
    }

    fun setTabFilter(tab: TabFilter) {
        currentTabFilter = tab
        applyFilters()
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

    // --- Внутренняя логика ---

    private fun updateCalls(calls: List<Hospitalization>) {
        allCalls.clear()
        allCalls.addAll(calls)
        prepareCallsForSearch(allCalls)
        applyFilters()
    }

    private fun applyFilters() {
        val lowerCaseQuery = currentSearchQuery.lowercase().trim()

        _isFilterActive.value = currentFilters.isActive()

        // 1. Сначала фильструем по вкладке
        val baseList =
                when (currentTabFilter) {
                    TabFilter.ACTIVE ->
                            allCalls.filter { call ->
                                // В "Активные" попадают все, у кого статус НЕ "архив"
                                val status = call.status?.lowercase()?.trim()
                                status == null || !status.contains("архив")
                            }
                    TabFilter.ARCHIVE ->
                            allCalls.filter { call ->
                                // В "Архив" попадают все, у кого статус содержит "архив"
                                val status = call.status?.lowercase()?.trim()
                                status?.contains("архив") == true
                            }
                }

        // 2. Фильтры из окна
        val baseListWithCustomFilters =
                baseList.filter { call ->
                    matchesCustomFilters(call, currentFilters)
                }

        // 3. Затем накладываем текстовый поиск (если он есть)
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
            val isMale = sex.contains("муж") || sex.contains("male")
            val isFemale = sex.contains("жен") || sex.contains("female")
            when (filters.sex) {
                SexFilter.MALE -> if (!isMale) return false
                SexFilter.FEMALE -> if (!isFemale) return false
                SexFilter.ANY -> Unit
            }
        }

        val age = parseAge(call.age)
        if (filters.ageFrom != null) {
            val v = age ?: return false
            if (v < filters.ageFrom) return false
        }
        if (filters.ageTo != null) {
            val v = age ?: return false
            if (v > filters.ageTo) return false
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
                        ?: DateFormatter.formatDateTime(call.callTime).also { v ->
                            call.formattedCallTime = v
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

    private fun mergeCalls(
            apiCalls: List<Hospitalization>,
            cachedCalls: List<Hospitalization>
    ): List<Hospitalization> {
        val byId = LinkedHashMap<String, Hospitalization>()
        apiCalls.forEach { call ->
            byId[call.id] = call
        }
        cachedCalls.forEach { call ->
            if (!byId.containsKey(call.id)) {
                byId[call.id] = call
            }
        }
        return byId.values.toList()
    }
}
