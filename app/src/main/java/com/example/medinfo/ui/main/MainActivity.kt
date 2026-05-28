package com.example.medinfo.ui.main

import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.medinfo.R
import com.example.medinfo.config.ConfigManager
import com.example.medinfo.model.Hospitalization
import com.example.medinfo.data.manager.CallsManager
import com.example.medinfo.data.manager.SignalRConnectionState
import com.example.medinfo.data.manager.SignalRConnectionStatus
import com.example.medinfo.data.signalr.SignalRService
import com.example.medinfo.receiver.FakeCallAlarmReceiver
import com.example.medinfo.util.TestCallFactory
import com.example.medinfo.ui.login.LoginActivity
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
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.medinfo.databinding.ActivityMainBinding
import com.example.medinfo.databinding.DialogUserDataBinding
import com.example.medinfo.databinding.PopupMenuCustomBinding
import com.example.medinfo.databinding.PopupTabMenuBinding
import com.example.medinfo.ui.details.HospitalizationDetailsActivity
import com.example.medinfo.util.DialogSizing
import com.example.medinfo.util.PermissionManager
import kotlinx.coroutines.flow.collectLatest
import androidx.recyclerview.widget.RecyclerView


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: HospitalizationAdapter

    private lateinit var viewModel: MainViewModel
    private var searchJob: Job? = null
    private var decisionTimerJob: Job? = null
    private var currentTabFilter = MainViewModel.TabFilter.ACTIVE

    private var currentDecisionCount = 0
    private var tabMenuPopupWindow: PopupWindow? = null
    private var shouldRefreshCallsOnResume = false
    private var isFirstPageLoading = true
    private var isFilterActive = false
    private var isSearchActive = false

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

        lifecycleScope.launch {
            val startTab = viewModel.resolveStartTab()
            selectTabFilter(startTab)
        }
    }

    override fun onResume() {
        super.onResume()

        PermissionManager.enforcePermissions(this) {}

        if (shouldRefreshCallsOnResume && ::viewModel.isInitialized) {
            shouldRefreshCallsOnResume = false
            viewModel.fetchCalls()
        }

        // Если пользователь зашёл в настройки и переключил флаг, тут же отражаем это на UI.
        applyTestCallVisibility()

        startDecisionTimerUpdatesIfNeeded()
    }

    private fun applyTestCallVisibility() {
        val show = ConfigManager.testCallEnabled
        binding.callButton.isVisible = show
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
                val callsSnapshot = calls.toList()
                updateEmptyState()

                if (!::adapter.isInitialized) {
                    adapter = HospitalizationAdapter(callsSnapshot) { hospitalization ->
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

                    // Если пользователь уже в самом верху списка — после вставки нового вызова
                    // подскролливаем к нему. Если листает/находится ниже — позицию не трогаем.
                    val wasAtTop = !binding.recyclerView.canScrollVertically(-1)
                    adapter.submitItems(callsSnapshot)
                    if (wasAtTop) {
                        binding.recyclerView.post {
                            binding.recyclerView.scrollToPosition(0)
                        }
                    }
                }
            }
        }

        // Счётчик "требуют решения" живёт в CallsManager и не зависит от текущей вкладки и поиска.
        lifecycleScope.launch {
            CallsManager.calls.collectLatest { decisionCalls ->
                currentDecisionCount = decisionCalls.size
                updateSelectedTabTitle()
            }
        }

        // Подписка на состояние фильтра
        lifecycleScope.launch {
            viewModel.isFirstPageLoading.collectLatest { isLoading ->
                isFirstPageLoading = isLoading
                updateEmptyState()
            }
        }

        lifecycleScope.launch {
            viewModel.isFilterActive.collectLatest { isActive ->
                isFilterActive = isActive
                binding.filterButton.isSelected = isActive
                updateEmptyState()
            }
        }

        lifecycleScope.launch {
            viewModel.isSearchActive.collectLatest { isActive ->
                isSearchActive = isActive
                updateEmptyState()
            }
        }

        // Маленький индикатор в шапке показывает, живо ли SignalR-подключение к серверу.
        lifecycleScope.launch {
            SignalRConnectionState.status.collectLatest { status ->
                updateConnectionStatus(status)
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

        // Кнопка тестового звонка видна только при включённом флаге testCallEnabled в конфиге.
        applyTestCallVisibility()

        binding.callButton.setOnClickListener {
            android.util.Log.i("CALL_LOG", "[MainActivity] TEST BUTTON PRESSED at ${System.currentTimeMillis()}")
            simulateIncomingCall()
        }

        // Тест doze мода
        binding.callButton.setOnLongClickListener {
            if (!ConfigManager.testCallEnabled) {
                Toast.makeText(
                    this,
                    "Тестовый вызов выключен. Включите его в настройках.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnLongClickListener true
            }
            AlertDialog.Builder(this)
                .setTitle("Тест Doze режима")
                .setMessage("Запланировать тестовый вызов через ${ConfigManager.fakeCallDelayMinutes} минут для проверки работы в Doze режиме?")
                .setPositiveButton("Запланировать") { _, _ ->
                    FakeCallAlarmReceiver.scheduleFakeCall(this)
                }
                .setNegativeButton("Отмена", null)
                .show()
            true
        }

        // Кнопка выхода — прямой logout через диалог подтверждения, без промежуточного попап-меню.
        binding.logoutButton.setOnClickListener {
            ConfirmLogoutDialogFragment()
                .show(supportFragmentManager, ConfirmLogoutDialogFragment.TAG)
        }

        // Кнопка профиля — сразу диалог с данными пользователя (попап больше не нужен).
        binding.profileButton.setOnClickListener {
            showUserDataDialog()
        }

        // Кнопка фильтров
        binding.filterButton.setOnClickListener {
            CallFiltersDialogFragment
                    .newInstance(viewModel.getCurrentFilters())
                    .show(supportFragmentManager, CallFiltersDialogFragment.TAG)
        }

        // Функции-слушатели для более сложной логики
        setupFiltersListener() // Изменение состояния фильтров
        setupTabMenuListener() // Переключение вкладки
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

    private fun setupTabMenuListener() {
        updateSelectedTabTitle()
        binding.tabMenuButton.setOnClickListener {
            showTabMenuPopup(it)
        }
    }

    // Открывает popup с тремя пунктами фильтра НАД нижней плашкой.
    private fun showTabMenuPopup(anchor: View) {
        tabMenuPopupWindow?.takeIf { it.isShowing }?.dismiss()

        val popupBinding = PopupTabMenuBinding.inflate(layoutInflater)
        bindTabMenuItem(
                popupBinding.requiresDecisionMenuItem,
                MainViewModel.TabFilter.REQUIRES_DECISION,
                isFirst = true,
                isLast = false
        )
        bindTabMenuItem(
                popupBinding.activeMenuItem,
                MainViewModel.TabFilter.ACTIVE,
                isFirst = false,
                isLast = false
        )
        bindTabMenuItem(
                popupBinding.archiveMenuItem,
                MainViewModel.TabFilter.ARCHIVE,
                isFirst = false,
                isLast = true
        )
        updateDecisionCountBadge(popupBinding.requiresDecisionCountBadge)

        val popupWindow =
                PopupWindow(
                        popupBinding.root,
                        anchor.width,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        true
                ).apply {
                    setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    isOutsideTouchable = true
                    elevation = dp(8).toFloat()
                }

        popupBinding.root.measure(
                View.MeasureSpec.makeMeasureSpec(anchor.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        tabMenuPopupWindow = popupWindow
        popupWindow.showAsDropDown(
                anchor,
                0,
                -anchor.height - popupBinding.root.measuredHeight - dp(8)
        )
    }

    private fun bindTabMenuItem(
        item: TextView,
        tabFilter: MainViewModel.TabFilter,
        isFirst: Boolean,
        isLast: Boolean
    ) {
        val isSelected = currentTabFilter == tabFilter
        item.text = getTabTitle(tabFilter)
        item.isSelected = isSelected
        item.background = createTabItemBackground(tabFilter, isSelected, isFirst, isLast)
        item.setTextColor(getColor(if (isSelected) R.color.main_1 else R.color.gray_1))
        item.setOnClickListener {
            tabMenuPopupWindow?.dismiss()
            selectTabFilter(tabFilter)
        }
    }

    private fun selectTabFilter(tabFilter: MainViewModel.TabFilter) {
        currentTabFilter = tabFilter
        updateSelectedTabTitle()
        updateEmptyState()
        startDecisionTimerUpdatesIfNeeded()
        viewModel.setTabFilter(tabFilter)
    }

    private fun updateEmptyState() {
        val isEmpty = hospitalizationList.isEmpty()
        val showLoading = isFirstPageLoading && isEmpty
        val showEmpty = !isFirstPageLoading && isEmpty
        val hasActiveConstraints = isFilterActive || isSearchActive

        binding.emptyStateText.isVisible = showLoading || showEmpty
        binding.recyclerView.isVisible = !isEmpty
        if (showLoading) {
            binding.emptyStateText.text = "Загрузка..."
        } else if (showEmpty) {
            binding.emptyStateText.text = when (currentTabFilter) {
                MainViewModel.TabFilter.REQUIRES_DECISION ->
                    if (hasActiveConstraints) {
                        "Нет вызовов, требующих решения, соответствующих фильтрам"
                    } else {
                        "Нет вызовов, требующих решения"
                    }
                MainViewModel.TabFilter.ACTIVE ->
                    if (hasActiveConstraints) {
                        "Нет активных вызовов, соответствующих фильтрам"
                    } else {
                        "Нет активных вызовов"
                    }
                MainViewModel.TabFilter.ARCHIVE ->
                    if (hasActiveConstraints) {
                        "Нет архивных вызовов, соответствующих фильтрам"
                    } else {
                        "Архив пуст"
                    }
            }
        }
    }

    private fun updateConnectionStatus(status: SignalRConnectionStatus) {
        applyConnectionStatus(binding.profileConnectionStatusDot, null, status)
    }

    private fun applyConnectionStatus(
        dot: View,
        statusText: TextView?,
        status: SignalRConnectionStatus
    ) {
        val colorRes = when (status) {
            SignalRConnectionStatus.CONNECTED -> R.color.green_1
            SignalRConnectionStatus.CONNECTING,
            SignalRConnectionStatus.RECONNECTING -> R.color.yellow_1
            SignalRConnectionStatus.DISCONNECTED -> R.color.red_1
        }
        val description = when (status) {
            SignalRConnectionStatus.CONNECTED -> "Связь с сервером есть"
            SignalRConnectionStatus.CONNECTING -> "Подключение к серверу"
            SignalRConnectionStatus.RECONNECTING -> "Восстановление связи с сервером"
            SignalRConnectionStatus.DISCONNECTED -> "Нет связи с сервером"
        }
        val shortText = when (status) {
            SignalRConnectionStatus.CONNECTED -> "Подключено"
            SignalRConnectionStatus.CONNECTING -> "Подключение"
            SignalRConnectionStatus.RECONNECTING -> "Переподключение"
            SignalRConnectionStatus.DISCONNECTED -> "Нет связи"
        }

        dot.background = createConnectionStatusDot(getColor(colorRes))
        dot.contentDescription = description
        statusText?.text = shortText
    }

    private fun createConnectionStatusDot(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun updateSelectedTabTitle() {
        binding.selectedTabText.text = getTabTitle(currentTabFilter)

        val showCount = currentTabFilter == MainViewModel.TabFilter.REQUIRES_DECISION &&
                currentDecisionCount > 0
        binding.selectedTabCountText.isVisible = showCount
        if (showCount) {
            binding.selectedTabCountText.text = getDecisionCountText()
        }
    }

    private fun updateDecisionCountBadge(badge: TextView) {
        val showCount = currentDecisionCount > 0
        badge.isVisible = showCount
        if (showCount) {
            badge.text = getDecisionCountText()
        }
    }

    // Сервер всегда отдаёт максимум pageSize=40 элементов на страницу. Если в локальной
    // очереди достигли потолка — реальное число может быть больше, поэтому показываем "39+".
    private fun getDecisionCountText(): String {
        val pageSize = MainViewModel.DEFAULT_PAGE_SIZE
        return if (currentDecisionCount >= pageSize) {
            "${pageSize - 1}+"
        } else {
            currentDecisionCount.toString()
        }
    }

    private fun getTabTitle(tabFilter: MainViewModel.TabFilter): String {
        return when (tabFilter) {
            MainViewModel.TabFilter.REQUIRES_DECISION -> getString(R.string.menu_requires_decision)
            MainViewModel.TabFilter.ACTIVE -> getString(R.string.menu_active)
            MainViewModel.TabFilter.ARCHIVE -> getString(R.string.menu_archive)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    // Вспомогательные акцентные цвета и круглый маркер. Сейчас не используются —
    // оставлены на случай возврата цветных точек слева от пунктов popup-меню.
    private fun getTabAccent(tabFilter: MainViewModel.TabFilter): Int {
        return when (tabFilter) {
            MainViewModel.TabFilter.REQUIRES_DECISION -> getColor(R.color.red_1)
            MainViewModel.TabFilter.ACTIVE -> getColor(R.color.green_1)
            MainViewModel.TabFilter.ARCHIVE -> getColor(R.color.main_1)
        }
    }

    private fun createTabMarker(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setSize(dp(9), dp(9))
            setBounds(0, 0, dp(9), dp(9))
        }
    }

    // Подсветка выбранного пункта popup'а должна совпадать со скруглением самой карточки
    private fun createTabItemBackground(
        tabFilter: MainViewModel.TabFilter,
        isSelected: Boolean,
        isFirst: Boolean,
        isLast: Boolean
    ): GradientDrawable {
        val radius = dp(24).toFloat()
        val backgroundColor =
                if (isSelected) getTabSelectedBackground(tabFilter) else Color.TRANSPARENT

        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(backgroundColor)
            cornerRadii =
                    floatArrayOf(
                            if (isFirst) radius else 0f,
                            if (isFirst) radius else 0f,
                            if (isFirst) radius else 0f,
                            if (isFirst) radius else 0f,
                            if (isLast) radius else 0f,
                            if (isLast) radius else 0f,
                            if (isLast) radius else 0f,
                            if (isLast) radius else 0f
                    )
        }
    }

    private fun getTabSelectedBackground(tabFilter: MainViewModel.TabFilter): Int {
        return when (tabFilter) {
            MainViewModel.TabFilter.REQUIRES_DECISION,
            MainViewModel.TabFilter.ACTIVE,
            MainViewModel.TabFilter.ARCHIVE -> getColor(R.color.tab_selected_bg)
        }
    }

    // TODO: legacy — попап-меню профиля заменено прямой кнопкой выхода в шапке.
    //  Метод и layout popup_menu_custom.xml оставлены на случай, если потребуется
    //  вернуть пункт "Данные пользователя". Можно безопасно удалить позже.
    @Suppress("unused")
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
        val connectionStatusJob =
            lifecycleScope.launch {
                SignalRConnectionState.status.collectLatest { status ->
                    applyConnectionStatus(
                        dialogBinding.dialogConnectionStatusDot,
                        dialogBinding.connectionStatusText,
                        status
                    )
                }
            }

        dialogBinding.buttonOk.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener { connectionStatusJob.cancel() }

        // Адаптивная ширина, центрирование и ограничение высоты — задаём до show().
        DialogSizing.apply(dialog.window, dialogBinding.root, dialogBinding.scrollView)
        dialog.show()
    }

    private fun getAppVersion(): String? {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName
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

        shouldRefreshCallsOnResume = true

        // Из списка "Требуют решения" открываем выбранный вызов без повторного звука и вибрации.
        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            putExtra(IncomingCallActivity.EXTRA_HOSPITALIZATION, details)
            putExtra(IncomingCallActivity.EXTRA_HOSPITALIZATION_ID, details.id)
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
                // Каждую секунду обновляем только текст таймера, а не весь список карточек.
                if (::adapter.isInitialized) {
                    adapter.refreshDecisionTimersOnly()
                }
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
        if (!ConfigManager.testCallEnabled) {
            Toast.makeText(
                this,
                "Тестовый вызов выключен. Включите его в настройках.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        android.util.Log.i("CALL_LOG", "[MainActivity] simulateIncomingCall() called")
        try {
            val mockHospitalization = TestCallFactory.buildMockHospitalization()

            // Запускаем экран точно так же, как это делает SignalRService после миграции на новый DTO.
            val intent = Intent(this, IncomingCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(IncomingCallActivity.EXTRA_HOSPITALIZATION, mockHospitalization)
                putExtra(IncomingCallActivity.EXTRA_IS_TEST_CALL, true)
            }

            android.util.Log.i(
                "CALL_LOG",
                "[MainActivity] LAUNCHING TEST CALL SCREEN Call#: ${mockHospitalization.call.dayNumber}/${mockHospitalization.call.yearNumber}"
            )
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("CALL_LOG", "[MainActivity] FAILED to launch test call: ${e.message}", e)
            Toast.makeText(this, "Ошибка теста: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
