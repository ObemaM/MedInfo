package com.example.neuroinfo.ui.main

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.neuroinfo.R
import com.example.neuroinfo.adapter.HospitalizationAdapter
import com.example.neuroinfo.data.CallRepository
import com.example.neuroinfo.data.CallsCache
import com.example.neuroinfo.data.RetrofitClient
import com.example.neuroinfo.data.TokenInterceptor
import com.example.neuroinfo.model.Hospitalization
import com.example.neuroinfo.ui.login.LoginActivity
import com.google.android.material.tabs.TabLayout
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.graphics.Rect
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.inputmethod.EditorInfo
import com.example.neuroinfo.model.CallNotificationDto
import com.example.neuroinfo.ui.incoming.IncomingCallActivity
import com.example.neuroinfo.util.DateFormatter
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: HospitalizationAdapter
    private lateinit var tabLayout: TabLayout
    private lateinit var searchEditText:
            TextInputEditText

    private val callRepository = CallRepository(RetrofitClient.apiService)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var searchJob: Job? = null
    private val callsCache by lazy { CallsCache(applicationContext) }

    // Какой список сейчас показываем: Активные или Архив
    private enum class TabFilter {
        ACTIVE,
        ARCHIVE
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v != null && v is TextInputEditText) {
                val outRect = Rect()
                v.getGlobalVisibleRect(outRect)

                // Если тапнули ВНЕ поля поиска
                if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    v.clearFocus()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private var currentTabFilter: TabFilter = TabFilter.ACTIVE

    // Список для отображения (фильтрованный)
    private val hospitalizationList = mutableListOf<Hospitalization>()
    // Полный список всех загруженных вызовов
    private val allHospitalizationList = mutableListOf<Hospitalization>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupViews()
        setupLogoutConfirmationListener()
        fetchCalls()
    }

    private fun setupViews() {
        recyclerView = findViewById(R.id.recyclerView)

        val profileButton = findViewById<ImageButton>(R.id.profile_button)
        profileButton.setOnClickListener { showProfilePopupWindow(it) }

        tabLayout = findViewById(R.id.tab_layout)
        setupTabsListener()
        setupSearchListener()
    }


    private fun setupSearchListener() {
        searchEditText =
                findViewById<TextInputEditText>(
                        R.id.search_edit_text
                )

        searchEditText.addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int
                    ) {}
                    override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                    ) {
                        searchJob?.cancel()
                        searchJob =
                                mainScope.launch {
                                    delay(250)
                                    applyFilters(s?.toString().orEmpty())
                                }
                    }
                    override fun afterTextChanged(s: Editable?) {}
                }
        )

        searchEditText.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                            actionId == EditorInfo.IME_ACTION_DONE
            ) {
                // Скрываем клавиатуру
                val imm =
                        getSystemService(INPUT_METHOD_SERVICE) as
                                InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                // Снимаем фокус
                v.clearFocus()
                true
            } else {
                false
            }
        }
    }

    /**
     * Общий метод применения всех фильтров:
     * - текущая вкладка (Активные / Архив)
     * - строка поиска
     */
    private fun applyFilters(query: String = searchEditText.text?.toString().orEmpty()) {
        val lowerCaseQuery = query.lowercase().trim()

        // 1. Сначала фильструем по вкладке
        val baseList =
                when (currentTabFilter) {
                    TabFilter.ACTIVE ->
                            allHospitalizationList.filter { call ->
                                // В "Активные" попадают все, у кого статус НЕ "архив"
                                val status = call.status?.lowercase()?.trim()
                                status == null || !status.contains("архив")
                            }
                    TabFilter.ARCHIVE ->
                            allHospitalizationList.filter { call ->
                                // В "Архив" попадают все, у кого статус содержит "архив"
                                val status = call.status?.lowercase()?.trim()
                                status?.contains("архив") == true
                            }
                }

        // 2. Затем накладываем текстовый поиск (если он есть)
        val filteredList =
                if (lowerCaseQuery.isEmpty()) {
                    baseList
                } else {
                    baseList.filter { call ->
                        if (call.searchCache.isNullOrBlank()) {
                            call.searchCache = buildSearchIndex(call)
                        }
                        call.searchCache?.contains(lowerCaseQuery) == true
                    }
                }

        // Обновляем список для адаптера
        hospitalizationList.clear()
        hospitalizationList.addAll(filteredList)

        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
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

        return buildList {
                    add(call.patientFullName)
                    add(call.patientName)
                    add(call.patientSurname)
                    add(call.patientPatronymic)
                    add(age)
                    addAll(ageVariants)
                    add(call.sex)
                    add(call.reason)
                    add(call.callNumber)
                    add(call.district)
                    add(call.point)
                    add(call.street)
                    add(call.house)
                    add(call.apartment)
                    add(call.comment)
                    add(call.formattedCallTime)
                }
                .filterNotNull()
                .joinToString(separator = " ")
                .lowercase()
    }

    /**
     * Слушатель переключения вкладок "Активные" / "Архив"
     */
    private fun setupTabsListener() {
        tabLayout.addOnTabSelectedListener(
                object : TabLayout.OnTabSelectedListener {
                    override fun onTabSelected(tab: TabLayout.Tab?) {
                        currentTabFilter =
                                when (tab?.position) {
                                    0 -> TabFilter.ACTIVE
                                    1 -> TabFilter.ARCHIVE
                                    else -> TabFilter.ACTIVE
                                }

                        // При смене вкладки сразу обновляем список с учётом текущей строки поиска
                        applyFilters()
                    }

                    override fun onTabUnselected(tab: TabLayout.Tab?) {
                        // Ничего не делаем
                    }

                    override fun onTabReselected(tab: TabLayout.Tab?) {
                        // При повторном нажатии можно обновить данные, но пока просто перефильтруем
                        applyFilters()
                    }
                }
        )
    }

    // Метод форматирования даты (взят из адаптера)
    private fun formatDateTime(dateTime: String?): String {
        if (dateTime.isNullOrEmpty()) return ""

        return try {
            val inputFormat =
                SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss",
                        Locale.getDefault()
                )
            val outputFormat =
                SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

            val date = inputFormat.parse(dateTime)
            outputFormat.format(date!!)
        } catch (e: Exception) {
            return dateTime
        }
    }

    private fun showProfilePopupWindow(anchor: View) {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView = inflater.inflate(R.layout.popup_menu_custom, null)


        val popupWindow =
                PopupWindow(
                        popupView,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        true
                )
        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val testButton = popupView.findViewById<TextView>(R.id.popup_test)
        // Настройка кнопок в кастомном меню

        val userDataButton = popupView.findViewById<TextView>(R.id.userdata)
        val logoutButton = popupView.findViewById<TextView>(R.id.popup_logout)

        testButton.setOnClickListener {
            simulateIncomingCall()
            Toast.makeText(this, "Тестовый звонок", Toast.LENGTH_SHORT).show()
            popupWindow.dismiss()
        }

        userDataButton.setOnClickListener {
            popupWindow.dismiss()
            showUserDataDialog()
        }

        logoutButton.setOnClickListener {
            popupWindow.dismiss()
            ConfirmLogoutDialogFragment()
                    .show(supportFragmentManager, ConfirmLogoutDialogFragment.TAG)
        }

        popupWindow.showAsDropDown(anchor)
    }

    private fun showUserDataDialog() {
        val dialog = Dialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_user_data, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Находим поля и кнопки в диалоге
        val loginTextView = view.findViewById<TextView>(R.id.user_login_text)
        val version = view.findViewById<TextView>(R.id.version_text)
        val okButton = view.findViewById<MaterialButton>(R.id.button_ok)

        // Получаем и устанавливаем логин и версию
        val login =
                getSharedPreferences("app_session", MODE_PRIVATE)
                        .getString("user_login", "Неизвестно")
        loginTextView.text = login
        version.text = getAppVersion()

        okButton.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun getAppVersion(): String? {
        return try {
            @Suppress("DEPRECATION") packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            "-"
        }
    }

    private fun setupLogoutConfirmationListener() {
        supportFragmentManager.setFragmentResultListener(
                ConfirmLogoutDialogFragment.REQUEST_KEY,
                this
        ) { _, bundle ->
            if (bundle.getBoolean(ConfirmLogoutDialogFragment.KEY_CONFIRMED_LOGOUT)) {
                logout()
            }
        }
    }

    private fun logout() {
        TokenInterceptor.clearToken(this)
        val sharedPrefs = getSharedPreferences("app_session", MODE_PRIVATE)
        sharedPrefs.getString("user_login", null)?.let { login ->
            callsCache.clear(login)
        }
        sharedPrefs.edit().remove("isLoggedIn").apply()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

     private fun showConfirmArchiveDialog() {
         val dialog = ConfirmArchiveDialogFragment().apply {
             arguments = Bundle().apply {
                 putInt("ITEM_ID", -1)
             }
         }
         dialog.show(supportFragmentManager, ConfirmArchiveDialogFragment.TAG)
     }

    private fun fetchCalls() {
        mainScope.launch {
            try {
                val sharedPrefs = getSharedPreferences("app_session", MODE_PRIVATE)
                val userLogin = sharedPrefs.getString("user_login", null)

                val cachedCalls =
                        if (!userLogin.isNullOrBlank()) {
                            withContext(Dispatchers.IO) {
                                callsCache.readCalls(userLogin)
                            }
                        } else {
                            null
                        }

                val callsToShow =
                        if (!cachedCalls.isNullOrEmpty()) {
                            cachedCalls
                        } else {
                            val result =
                                    withContext(Dispatchers.IO) {
                                        callRepository.getCalls(
                                                pageNumber = 1,
                                                pageSize = 2000,
                                                getCount = true
                                        )
                                    }

                            if (result.isSuccess) {
                                val content = result.getOrThrow()
                                content.calls
                            } else {
                                val error =
                                        result.exceptionOrNull()?.message
                                                ?: "Не удалось загрузить список вызовов."
                                Toast.makeText(this@MainActivity, "Ошибка: $error", Toast.LENGTH_LONG).show()
                                emptyList()
                            }
                        }

                prepareCallsForSearch(callsToShow)

                if (cachedCalls.isNullOrEmpty() && !userLogin.isNullOrBlank() && callsToShow.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        callsCache.writeCalls(userLogin, callsToShow)
                    }
                }

                // Сохраняем в ПОЛНЫЙ список
                allHospitalizationList.clear()
                allHospitalizationList.addAll(callsToShow)

                // Сразу применяем фильтры (вкладка Активные/Архив + поиск),
                // чтобы при первом запуске "Активные" не показывали архив.
                applyFilters()

                if (!::adapter.isInitialized) {
                    adapter =
                            HospitalizationAdapter(hospitalizationList) { call ->
                                showConfirmArchiveDialog()
                            }
                    recyclerView.adapter = adapter
                    recyclerView.layoutManager = LinearLayoutManager(this@MainActivity)
                } else {
                    adapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                Toast.makeText(
                                this@MainActivity,
                                "Ошибка сети при загрузке данных.",
                                Toast.LENGTH_LONG
                        )
                        .show()
                e.printStackTrace()
            }
        }
    }
    private fun simulateIncomingCall() {
        // Имитируем данные от сервера
        try {
            val mockCall = CallNotificationDto(
                fullName = "Иванов Иван Иванович",
                age = "age",
                sex = "sex",
                reason = "reason",
                district = "district",
                point = "point",
                street = "street",
                house = "House",
                apartment = "1",
                entrance = 1,
                longitude = 1.2,
                latitude = 2.3,
                brigadeNumber = 12,
                brigadeProfile = "Profile",
                callNumber = "123456",
                callTime = "callTime",
                urgency = 1,
                status = "status",
                additionalInfo = ""
            )

            // Запускаем экран точно так же, как это делает SignalRService
            val intent = Intent(this, IncomingCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("CALL_DATA", mockCall)
            }

            startActivity(intent)
        }
        catch (e: Exception) {
            Toast.makeText(this, "Ошибка теста: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("TestCall", "CRASH: ", e)
        }
    }
}
