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
import com.example.medinfo.data.signalr.SignalRService
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.medinfo.databinding.ActivityMainBinding
import com.example.medinfo.databinding.DialogUserDataBinding
import com.example.medinfo.databinding.PopupMenuCustomBinding
import kotlinx.coroutines.flow.collectLatest

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: HospitalizationAdapter

    private lateinit var viewModel: MainViewModel
    private var searchJob: Job? = null

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

    private fun observeViewModel() {
        // Подписка на отфильтрованный список вызовов
        lifecycleScope.launch {
            viewModel.filteredCalls.collectLatest { calls ->
                hospitalizationList.clear()
                hospitalizationList.addAll(calls)

                if (!::adapter.isInitialized) {
                    adapter =
                            HospitalizationAdapter(hospitalizationList) { }
                    binding.recyclerView.adapter = adapter
                    binding.recyclerView.layoutManager = LinearLayoutManager(this@MainActivity)
                } else {
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
            simulateIncomingCall()
            Toast.makeText(this, "Тестовый звонок", Toast.LENGTH_SHORT).show()
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
                                    0 -> MainViewModel.TabFilter.ACTIVE
                                    1 -> MainViewModel.TabFilter.ARCHIVE
                                    else -> MainViewModel.TabFilter.ACTIVE
                                }

                        // При смене вкладки сразу обновляем список с учётом текущей строки поиска
                        viewModel.setTabFilter(tabFilter)
                    }

                    override fun onTabUnselected(tab: TabLayout.Tab?) {
                        // Ничего не делаем
                    }

                    override fun onTabReselected(tab: TabLayout.Tab?) {
                        // При повторном нажатии можно обновить данные, но пока просто перефильтруем
                        viewModel.setTabFilter(
                                when (tab?.position) {
                                    1 -> MainViewModel.TabFilter.ARCHIVE
                                    else -> MainViewModel.TabFilter.ACTIVE
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

        popupWindow.showAsDropDown(anchor, 0, 24)
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

     private fun showConfirmArchiveDialog() {
         val dialog = ConfirmArchiveDialogFragment().apply {
             arguments = Bundle().apply {
                 putInt("ITEM_ID", -1)
             }
         }
         dialog.show(supportFragmentManager, ConfirmArchiveDialogFragment.TAG)
     }

    private fun simulateIncomingCall() {
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

            startActivity(intent)
        }
        catch (e: Exception) {
            Toast.makeText(this, "Ошибка теста: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
