package com.example.medinfo.ui.main

import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.medinfo.model.Hospitalization
import com.example.medinfo.model.CallNotificationDto
import com.example.medinfo.model.BleedingInfo
import com.example.medinfo.model.ArterialTourniquetInfo
import com.example.medinfo.model.VenousAccessInfo
import com.example.medinfo.model.IfaInfo
import com.example.medinfo.data.manager.CallsManager
import com.example.medinfo.data.signalr.SignalRService
import com.example.medinfo.receiver.FakeCallAlarmReceiver
import com.example.medinfo.ui.login.LoginActivity
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.graphics.Rect
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import com.example.medinfo.ui.incoming.IncomingCallActivity
import com.google.android.material.textfield.TextInputEditText
import android.os.Build
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.medinfo.databinding.ActivityMainBinding
import com.example.medinfo.databinding.DialogUserDataBinding
import com.example.medinfo.databinding.PopupMenuCustomBinding
import com.example.medinfo.ui.details.HospitalizationDetailsActivity
import com.example.medinfo.util.PermissionManager
import kotlinx.coroutines.flow.collectLatest
import androidx.recyclerview.widget.RecyclerView


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: HospitalizationAdapter

    private lateinit var viewModel: MainViewModel
    private var searchJob: Job? = null
    private var decisionTimerJob: Job? = null
    private var currentTabFilter = MainViewModel.TabFilter.REQUIRES_DECISION
    private var shouldRefreshCallsOnResume = false

    // Список для адаптера (обновляется при получении данных из ViewModel)
    private val hospitalizationList = mutableListOf<Hospitalization>()

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        setupViews()
        setupLogoutConfirmationListener()
        observeViewModel()
        viewModel.fetchCalls()
    }

    override fun onResume() {
        super.onResume()
        // Re-check permissions when returning from settings
        PermissionManager.enforcePermissions(this) {
            // Permissions granted, continue normal operation
        }

        if (shouldRefreshCallsOnResume && ::viewModel.isInitialized) {
            shouldRefreshCallsOnResume = false
            viewModel.fetchCalls()
        }

        startDecisionTimerUpdatesIfNeeded()
    }

    override fun onPause() {
        stopDecisionTimerUpdates()
        super.onPause()
    }

    private fun observeViewModel() {

        // Подписка на отфильтрованный список вызовов
        lifecycleScope.launch {
            viewModel.filteredCalls.collectLatest { calls ->
                hospitalizationList.clear()
                hospitalizationList.addAll(calls)

                if (!::adapter.isInitialized) {
                    adapter = HospitalizationAdapter(hospitalizationList) { hospitalization ->
                        if (currentTabFilter == MainViewModel.TabFilter.REQUIRES_DECISION) {
                            openDecisionScreen(hospitalization)
                        } else {
                            openHospitalizationDetails(hospitalization)
                        }
                    }
                    adapter.setShowDecisionTimer(currentTabFilter == MainViewModel.TabFilter.REQUIRES_DECISION)

                    // Объекты идут друг за другом
                    val layoutManager = LinearLayoutManager(this@MainActivity)

                    binding.recyclerView.layoutManager = layoutManager
                    binding.recyclerView.adapter = adapter

                    binding.recyclerView.addOnScrollListener(
                        object : RecyclerView.OnScrollListener() {
                            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                                super.onScrolled(recyclerView, dx, dy)

                                // Реагируем только на прокрутку вниз
                                if (dy <= 0) return

                                val totalItemCount = layoutManager.itemCount
                                val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()

                                // Если пользователь приблизился к концу списка, догружаем следующую страницу
                                if (totalItemCount > 0 && lastVisibleItemPosition >= totalItemCount - 3) {
                                    viewModel.loadNextPage()
                                }
                            }
                        }
                    )
                } else {
                    adapter.setShowDecisionTimer(currentTabFilter == MainViewModel.TabFilter.REQUIRES_DECISION)
                    adapter.notifyDataSetChanged()

                }
            }
        }

        // Подписка на состояние фильтра
        lifecycleScope.launch {
            viewModel.isFilterActive.collectLatest { isActive ->
                binding.filterButton.isSelected = isActive
            }
        }

        // Подписка на toast-сообщения
        lifecycleScope.launch {
            viewModel.toastMessage.collectLatest { message ->
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }
        }

        // Подписка на событие выхода
        lifecycleScope.launch {
            viewModel.logoutEvent.collectLatest {
                stopService(Intent(this@MainActivity, SignalRService::class.java))
                val intent = Intent(this@MainActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
    }

    // Функция для работы с кнопками
    private fun setupViews() {

        // Кнопка звонка
        binding.callButton.setOnClickListener {
            android.util.Log.i("CALL_LOG", "[MainActivity] TEST BUTTON PRESSED at ${System.currentTimeMillis()}")
            simulateIncomingCall()
            Toast.makeText(this, "Тестовый звонок", Toast.LENGTH_SHORT).show()
        }

        // Long press on call button to schedule 35-min Doze test
        binding.callButton.setOnLongClickListener {
            AlertDialog.Builder(this)
                .setTitle("Тест Doze режима")
                .setMessage("Запланировать тестовый вызов через 35 минут для проверки работы в Doze режиме?")
                .setPositiveButton("Запланировать") { _, _ ->
                    FakeCallAlarmReceiver.scheduleFakeCall(this, 35)
                }
                .setNegativeButton("Отмена", null)
                .show()
            true
        }

        // Кнопка профиля
        binding.profileButton.setOnClickListener {
            showProfilePopupWindow(it)
        }

        // Кнопка фильтров
        binding.filterButton.setOnClickListener {
            CallFiltersDialogFragment
                    .newInstance(viewModel.getCurrentFilters())
                    .show(supportFragmentManager, CallFiltersDialogFragment.TAG)
        }

        // Функции-слушатели для более сложной логики
        setupFiltersListener() // Изменение состояния фильтров
        setupTabsListener() // Переключение вкладки
        setupSearchListener() // Ввод текста и поиск
    }

    private fun setupFiltersListener() {
        supportFragmentManager.setFragmentResultListener(
                CallFiltersDialogFragment.REQUEST_KEY,
                this
        ) { _, bundle ->
            val filters =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        bundle.getSerializable(
                                CallFiltersDialogFragment.KEY_FILTERS,
                                CallFilters::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        bundle.getSerializable(CallFiltersDialogFragment.KEY_FILTERS) as? CallFilters
                    }

            viewModel.setCustomFilters(filters ?: CallFilters())
        }
    }


    private fun setupSearchListener() {
        val searchEditText = binding.searchEditText

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
                                lifecycleScope.launch {
                                    delay(250)
                                    viewModel.setSearchQuery(s?.toString().orEmpty())
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
     * Слушатель переключения вкладок "Активные" / "Архив"
     */
    private fun setupTabsListener() {
        binding.tabLayout.addOnTabSelectedListener(
                object : TabLayout.OnTabSelectedListener {
                    override fun onTabSelected(tab: TabLayout.Tab?) {
                        val tabFilter =
                                when (tab?.position) {
                                    0 -> MainViewModel.TabFilter.REQUIRES_DECISION
                                    1 -> MainViewModel.TabFilter.ACTIVE
                                    2 -> MainViewModel.TabFilter.ARCHIVE
                                    else -> MainViewModel.TabFilter.REQUIRES_DECISION
                                }

                        // При смене вкладки сразу обновляем список с учётом текущей строки поиска
                        currentTabFilter = tabFilter
                        startDecisionTimerUpdatesIfNeeded()
                        viewModel.setTabFilter(tabFilter)
                    }

                    override fun onTabUnselected(tab: TabLayout.Tab?) {
                        // Ничего не делаем
                    }

                    override fun onTabReselected(tab: TabLayout.Tab?) {
                        // При повторном нажатии можно обновить данные, но пока просто перефильтруем
                        viewModel.setTabFilter(
                                when (tab?.position) {
                                    0 -> MainViewModel.TabFilter.REQUIRES_DECISION
                                    1 -> MainViewModel.TabFilter.ACTIVE
                                    2 -> MainViewModel.TabFilter.ARCHIVE
                                    else -> MainViewModel.TabFilter.REQUIRES_DECISION
                                }.also {
                                    currentTabFilter = it
                                    startDecisionTimerUpdatesIfNeeded()
                                }
                        )
                    }
                }
        )
    }

    // Обработка кнопок при нажатии на профиль
    private fun showProfilePopupWindow(anchor: View) {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupBinding = PopupMenuCustomBinding.inflate(inflater)
        val popupWindow =
                PopupWindow(
                        popupBinding.root,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        true
                )
        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        popupBinding.userdata.setOnClickListener {
            popupWindow.dismiss()
            showUserDataDialog()
        }

        popupBinding.popupLogout.setOnClickListener {
            popupWindow.dismiss()
            ConfirmLogoutDialogFragment()
                    .show(supportFragmentManager, ConfirmLogoutDialogFragment.TAG)
        }

        popupWindow.showAsDropDown(anchor, 0, 36)
    }

    private fun showUserDataDialog() {
        val dialog = Dialog(this)
        val dialogBinding = DialogUserDataBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Получаем и устанавливаем логин и версию
        val login = viewModel.getUserLogin() ?: "Неизвестно"
        dialogBinding.userLoginText.text = login
        dialogBinding.versionText.text = getAppVersion()

        dialogBinding.buttonOk.setOnClickListener { dialog.dismiss() }

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
        viewModel.logout()
    }

    private fun openHospitalizationDetails(hospitalization: Hospitalization) {
        val details = hospitalization.details
        if (details == null) {
            Toast.makeText(this, "Нет подробных данных вызова", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, HospitalizationDetailsActivity::class.java).apply {
            putExtra(HospitalizationDetailsActivity.EXTRA_HOSPITALIZATION, details)
        }
        startActivity(intent)
    }

    private fun openDecisionScreen(hospitalization: Hospitalization) {
        val details = hospitalization.details
        if (details == null) {
            Toast.makeText(this, "Нет данных для принятия решения", Toast.LENGTH_SHORT).show()
            return
        }

        CallsManager.upsertCall(details)
        shouldRefreshCallsOnResume = true

        // Из списка "Требуют решения" открываем выбранный вызов без повторного звука и вибрации.
        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            putExtra(IncomingCallActivity.EXTRA_HOSPITALIZATION, details)
            putExtra(IncomingCallActivity.EXTRA_START_ALERTS, false)
        }
        startActivity(intent)
    }

    private fun startDecisionTimerUpdatesIfNeeded() {
        if (currentTabFilter != MainViewModel.TabFilter.REQUIRES_DECISION) {
            stopDecisionTimerUpdates()
            return
        }

        if (decisionTimerJob?.isActive == true) return

        decisionTimerJob = lifecycleScope.launch {
            while (true) {
                // Карточки должны тикать сами, без повторного открытия вызова пользователем.
                viewModel.refreshDecisionTimers()
                delay(1000)
            }
        }
    }

    private fun stopDecisionTimerUpdates() {
        decisionTimerJob?.cancel()
        decisionTimerJob = null
    }

     private fun showConfirmArchiveDialog() {
         val dialog = ConfirmArchiveDialogFragment().apply {
             arguments = Bundle().apply {
                 putInt("ITEM_ID", -1)
             }
         }
         dialog.show(supportFragmentManager, ConfirmArchiveDialogFragment.TAG)
     }

    private fun simulateIncomingCall() {
        android.util.Log.i("CALL_LOG", "[MainActivity] simulateIncomingCall() called")
        // Имитируем данные от сервера
        try {
            val mockCall = CallNotificationDto(
                fullName = "Иванов Иван Иванович",
                age = "45",
                sex = "Муж",
                reason = "Боль в груди",
                additionalInfo = "Аллергия на пенициллин",
                district = "Центральный",
                point = "Москва",
                street = "Ленина",
                house = "12",
                apartment = "45",
                enterance = 3,
                longitude = 55.7558,
                latitude = 37.6176,
                brigadeNumber = 404,
                brigadeProfile = "Кардиологическая",
                callNumber = "6/2026",
                callTime = "2026-02-19T14:30:00",
                urgency = 1,
                status = "транспортировка",
                bloodPressure = "120/80",

                // Медицинские показатели
                consciousness = "Ясное",
                convulsions = false,
                glucometry = 5,
                heartRate = 72,
                oxygenSupport = true,
                pregnant = false,
                respirationRate = 16,
                spO2 = 98,
                startDisease = 2,
                stenosis = false,
                temperature = 36.6,
                lams = 0,
                mrs = 0,
                vas = 3,

                // Кровотечение
                bleeding = BleedingInfo(
                    presence = false,
                    type = null,
                    arterialTourniquet = ArterialTourniquetInfo(
                        presence = false,
                        applicationTime = null
                    )
                ),

                // Венозный доступ
                venousAccess = VenousAccessInfo(
                    presence = true,
                    method = listOf("Периферическая вена")
                ),

                // Протезирование ДП
                ifa = IfaInfo(
                    presence = false,
                    tool = null,
                    alv = false
                ),

                // Системные поля
                messageId = 0,
                messageValue = null,

                // Идентификация
                dprm = "2026-02-19",
                ngod = 2026,
                numv = 6,
                ssmp = 10,
                team = 404,
                vozr = "45"
            )

            // Запускаем экран точно так же, как это делает SignalRService
            val intent = Intent(this, IncomingCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("CALL_DATA", mockCall)
            }

            android.util.Log.i("CALL_LOG", "[MainActivity] LAUNCHING TEST CALL SCREEN Call#: ${mockCall.callNumber}")
            startActivity(intent)
            android.util.Log.i("CALL_LOG", "[MainActivity] Test call screen launched successfully")
        }
        catch (e: Exception) {
            android.util.Log.e("CALL_LOG", "[MainActivity] FAILED to launch test call: ${e.message}", e)
            Toast.makeText(this, "Ошибка теста: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
