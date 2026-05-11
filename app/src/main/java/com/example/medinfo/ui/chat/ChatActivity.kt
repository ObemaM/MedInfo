package com.example.medinfo.ui.chat

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.medinfo.R
import com.example.medinfo.config.ConfigManager
import com.example.medinfo.data.manager.MessagesEventBus
import com.example.medinfo.data.network.RetrofitClient
import com.example.medinfo.data.repository.HospitalizationRepository
import com.example.medinfo.databinding.ActivityChatBinding
import com.example.medinfo.model.api.MessageOrigin
import com.example.medinfo.model.api.MessageResponseDto
import com.example.medinfo.model.api.MessageType
import com.example.medinfo.model.api.PatientConditionResponseDto
import com.example.medinfo.notifications.ChatMessageNotifier
import com.example.medinfo.notifications.TestMessageSimulator
import com.example.medinfo.util.DateFormatter
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding

    private val hospitalizationRepository by lazy {
        HospitalizationRepository(RetrofitClient.apiServiceService)
    }

    private lateinit var hospitalizationId: String

    val chatId: String?
        get() = if (::hospitalizationId.isInitialized) hospitalizationId else null

    private var readOnly: Boolean = false

    // ID уже отрисованных сообщений — для дедупа: если SignalR пушит сообщение,
    // которое уже пришло через loadMessages (или прилетело дважды), игнорируем.
    private val shownMessageIds = mutableSetOf<String>()

    // Чтобы диалог состояния пациента не открывался повторно для того же сообщения
    // (например, после поворота экрана и повторной подписки на SignalR).
    private val shownConditionMessageIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val id = intent.getStringExtra(EXTRA_HOSPITALIZATION_ID)
        if (id.isNullOrBlank()) {
            Toast.makeText(this, "Не удалось открыть чат", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        hospitalizationId = id
        readOnly = intent.getBooleanExtra(EXTRA_READ_ONLY, false)

        binding.titleText.text = intent.getStringExtra(EXTRA_CHAT_TITLE)?.let { "Чат: $it" } ?: "Чат"
        binding.closeButton.setOnClickListener { finish() }
        binding.inputContainer.visibility = if (readOnly) View.GONE else View.VISIBLE
        binding.sendButton.setOnClickListener { sendMessage() }
        // DEBUG: эмулирует входящее сообщение от бригады; в боевом режиме скрыта
        // тем же флагом, что и тестовый звонок на главном экране.
        binding.debugSimulateButton.visibility =
            if (ConfigManager.testCallEnabled) View.VISIBLE else View.GONE
        binding.debugSimulateButton.setOnClickListener {
            TestMessageSimulator.simulateBrigadeMessage(this, hospitalizationId)
        }
        binding.messageEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }

        // При открытии клавиатуры NestedScrollView сжимается — без этого последние
        // сообщения уезжают за поле ввода. Возвращаем фокус на низ списка.
        binding.messagesScroll.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom != oldBottom) {
                scrollToBottom()
            }
        }
        binding.messageEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) scrollToBottom()
        }

        // При каждом возврате на экран — рефреш истории (на случай пропущенных
        // в фоне сообщений), затем подписка на realtime события из SignalR.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Параллельный
                launch {
                    MessagesEventBus.incoming.collect { message ->
                        if (message.hospitalizationId == hospitalizationId &&
                            message.id !in shownMessageIds
                        ) {
                            appendMessage(message)
                            maybeShowPatientConditionDialog(message)
                        }
                    }
                }

                launch {
                    fetchAndRenderMessages()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Если экран открылся успешно — гасим висящее в шторке уведомление по этому чату.
        // Через chatId, а не напрямую по hospitalizationId — на случай, если onCreate упал
        // в finish() до присваивания поля и lateinit ещё не инициализирован.
        chatId?.let { ChatMessageNotifier.cancelFor(this, it) }
    }

    // Сообщения PATIENT_CONDITION открывают диалог состояния пациента поверх любого другого
    // диалога. Защищаемся от повторного показа (id сообщения уже в shownConditionMessageIds).
    // Если бригада прислала несколько сообщений подряд — не складываем диалоги стопкой,
    // а закрываем предыдущий и показываем самый свежий.
    private fun maybeShowPatientConditionDialog(message: MessageResponseDto) {
        if (MessageType.fromId(message.type) != MessageType.PATIENT_CONDITION) return
        val condition = message.patientCondition ?: return
        if (!shownConditionMessageIds.add(message.id)) return
        if (supportFragmentManager.isStateSaved) return

        showPatientConditionDialog(condition, message.receptionTime)
    }

    // Открыть диалог по тапу на кнопку в пузыре чата (или из maybeShowPatientConditionDialog).
    // Любую уже открытую копию закрываем — на экране остаётся ровно одна, всегда самая последняя.
    private fun showPatientConditionDialog(
        condition: PatientConditionResponseDto,
        receptionTime: String?
    ) {
        if (supportFragmentManager.isStateSaved) return

        (supportFragmentManager.findFragmentByTag(MessageDataDialogFragment.TAG)
            as? MessageDataDialogFragment)?.dismissAllowingStateLoss()

        MessageDataDialogFragment
            .newInstance(condition, receptionTime)
            .show(supportFragmentManager, MessageDataDialogFragment.TAG)
    }

    private fun loadMessages() {
        lifecycleScope.launch {
            fetchAndRenderMessages()
        }
    }

    private suspend fun fetchAndRenderMessages() {
        showState("Загрузка сообщений...")
        try {
            val messages = withContext(Dispatchers.IO) {
                hospitalizationRepository.getMessages(hospitalizationId).content.orEmpty()
            }
            renderMessages(messages)
        } catch (e: Exception) {
            showState(e.message ?: "Не удалось загрузить сообщения")
        }
    }

    private fun sendMessage() {
        if (readOnly) return

        val text = binding.messageEditText.text?.toString().orEmpty().trim()
        if (text.isBlank()) return

        binding.sendButton.isEnabled = false
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    hospitalizationRepository.sendMessage(
                        hospitalizationId = hospitalizationId,
                        messageText = text
                    )
                }
                binding.messageEditText.setText("")
                loadMessages()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ChatActivity,
                    e.message ?: "Не удалось отправить сообщение",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                binding.sendButton.isEnabled = true
            }
        }
    }

    private fun renderMessages(messages: List<MessageResponseDto>) {
        binding.messagesContainer.removeAllViews()
        shownMessageIds.clear()

        if (messages.isEmpty()) {
            showState("Сообщений пока нет")
            return
        }

        messages.forEach { message ->
            binding.messagesContainer.addView(createMessageBubble(message))
            shownMessageIds.add(message.id)
        }

        scrollToBottom()
    }

    private fun appendMessage(message: MessageResponseDto) {
        // Если до этого был state-плейсхолдер ("Сообщений пока нет"), убираем его.
        if (binding.stateText.parent === binding.messagesContainer) {
            binding.messagesContainer.removeView(binding.stateText)
            binding.stateText.visibility = View.GONE
        }

        binding.messagesContainer.addView(createMessageBubble(message))
        shownMessageIds.add(message.id)
        scrollToBottom()
    }

    private fun scrollToBottom() {
        binding.messagesScroll.post {
            binding.messagesScroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun createMessageBubble(message: MessageResponseDto): View {
        val isOwn = MessageOrigin.fromId(message.origin) == MessageOrigin.INFORMATOR_APP
        val isPatientCondition =
            MessageType.fromId(message.type) == MessageType.PATIENT_CONDITION &&
                message.patientCondition != null

        val card = MaterialCardView(this).apply {
            radius = 12.dp().toFloat()
            cardElevation = 0f
            strokeWidth = 1.dp()
            strokeColor = resources.getColor(R.color.border_gray_1, null)
            setCardBackgroundColor(
                resources.getColor(if (isOwn) R.color.blue_3 else R.color.background_2, null)
            )
            // Ширину пузыря считаем в dp, чтобы на маленьких экранах оставались разумные поля
            // (на 320dp было слишком тесно при 0.78 * widthPixels).
            val maxBubbleWidthDp = 320
            val targetWidthPx = (resources.displayMetrics.widthPixels * 0.82f).toInt()
                .coerceAtMost(maxBubbleWidthDp.dp())
            layoutParams = LinearLayout.LayoutParams(
                targetWidthPx,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = if (isOwn) Gravity.END else Gravity.START
                setMargins(8.dp(), 6.dp(), 8.dp(), 6.dp())
            }
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
        }

        val author = TextView(this).apply {
            text = buildAuthor(message)
            setTextColor(resources.getColor(R.color.field_label_color, null))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }

        val body = TextView(this).apply {
            // Для PATIENT_CONDITION в пузыре оставляем только короткую подпись — полный набор
            // полей живёт в диалоге состояния пациента, чтобы не растягивать чат на 25 строк.
            text = if (isPatientCondition) {
                "Получены данные о состоянии пациента"
            } else {
                buildMessageBody(message)
            }
            setTextColor(resources.getColor(R.color.black_1, null))
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 4.dp()
            }
        }

        val time = TextView(this).apply {
            text = DateFormatter.formatDateTime(message.receptionTime)
            setTextColor(resources.getColor(R.color.border_gray_2, null))
            textSize = 12f
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 6.dp()
            }
        }

        container.addView(author)
        container.addView(body)
        if (isPatientCondition) {
            container.addView(buildShowConditionButton(message))
        }
        container.addView(time)
        card.addView(container)
        return card
    }

    // Вторичная кнопка под телом сообщения PATIENT_CONDITION: повторно открывает диалог
    // состояния пациента с теми же данными. Нужна, если врач закрыл авто-открывшийся диалог
    // или вернулся в чат позже и хочет посмотреть подробности.
    private fun buildShowConditionButton(message: MessageResponseDto): View {
        return com.google.android.material.button.MaterialButton(
            this,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = "Показать данные"
            textSize = 13f
            isAllCaps = false
            setTextColor(resources.getColor(R.color.main_1, null))
            strokeColor = android.content.res.ColorStateList.valueOf(
                resources.getColor(R.color.main_1, null)
            )
            cornerRadius = 10.dp()
            insetTop = 0
            insetBottom = 0
            minHeight = 40.dp()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8.dp()
            }
            setOnClickListener {
                val condition = message.patientCondition ?: return@setOnClickListener
                showPatientConditionDialog(condition, message.receptionTime)
            }
        }
    }

    private fun showState(text: String) {
        binding.messagesContainer.removeAllViews()
        binding.stateText.text = text
        binding.stateText.visibility = View.VISIBLE
        binding.messagesContainer.addView(binding.stateText)
    }

    private fun buildAuthor(message: MessageResponseDto): String {
        return when (MessageOrigin.fromId(message.origin)) {
            MessageOrigin.TABLET -> "Бригада"
            MessageOrigin.INFORMATOR_APP -> "Информатор"
            null -> "Сообщение"
        }
    }

    private fun buildMessageBody(message: MessageResponseDto): String {
        val text = message.text?.trim()
        if (!text.isNullOrBlank()) return text

        return when (MessageType.fromId(message.type)) {
            MessageType.PATIENT_CONDITION -> buildPatientConditionText(message.patientCondition)
            MessageType.TEXT -> "Сообщение без текста"
            null -> "Сообщение без текста"
        }
    }

    // Подробный набор полей живёт в диалоге MessageDataDialogFragment. В пузыре чата
    // показываем только короткую подпись, чтобы лента не превращалась в простыню.
    private fun buildPatientConditionText(condition: PatientConditionResponseDto?): String {
        return if (condition == null) {
            "Переданы данные состояния пациента"
        } else {
            "Получены данные о состоянии пациента"
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_HOSPITALIZATION_ID = "EXTRA_HOSPITALIZATION_ID"
        const val EXTRA_CHAT_TITLE = "EXTRA_CHAT_TITLE"
        const val EXTRA_READ_ONLY = "EXTRA_READ_ONLY"
    }
}
