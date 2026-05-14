package com.example.medinfo.ui.chat

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.medinfo.util.PermissionManager
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
import com.example.medinfo.notifications.ChatMessageNotifier
import com.example.medinfo.notifications.TestMessageSimulator
import com.example.medinfo.util.DateFormatter
import com.example.medinfo.util.PatientConditionFields
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

    // Для звонка бригаде
    private var currentBrigadePhone: String? = null

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
        binding.callButton.setOnClickListener { callToTablet() }
        binding.inputContainer.visibility = if (readOnly) View.GONE else View.VISIBLE
        binding.sendButton.setOnClickListener { sendMessage() }

        // T — текстовое сообщение, PC — PATIENT_CONDITION с phoneNumber. Видны только в test-режиме.
        // В чате PC отображается обычным текстовым сообщением; карточка на экране решения обновляется отдельно.
        val debugVisibility = if (ConfigManager.testCallEnabled) View.VISIBLE else View.GONE
        binding.debugSimulateButton.visibility = debugVisibility
        binding.debugSimulateConditionButton.visibility = debugVisibility
        binding.debugSimulateButton.setOnClickListener {
            TestMessageSimulator.simulateBrigadeMessage(this, hospitalizationId)
        }
        binding.debugSimulateConditionButton.setOnClickListener {
            TestMessageSimulator.simulatePatientCondition(this, hospitalizationId)
        }
        binding.messageEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }

        // При открытии клавиатуры скролл сжимается — возвращаем фокус на низ списка.
        binding.messagesScroll.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom != oldBottom) {
                scrollToBottom()
            }
        }
        binding.messageEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) scrollToBottom()
        }

        // На каждый возврат: рефреш истории + подписка на realtime из SignalR.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Параллельный
                launch {
                    MessagesEventBus.incoming.collect { message ->
                        if (message.hospitalizationId == hospitalizationId &&
                            message.id !in shownMessageIds
                        ) {
                            appendMessage(message)

                            // Обновление номера
                            applyBrigadePhone(message.phoneNumber)
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

        // Гасим висящее в шторке уведомление (через chatId — безопасно к неинициализированному lateinit).
        chatId?.let { ChatMessageNotifier.cancelFor(this, it) }
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

            // В истории чата ищем самое последнее сообщение, у которого был телефон, и берём его
            val phone = messages.lastOrNull { !it.phoneNumber.isNullOrBlank() }?.phoneNumber
            applyBrigadePhone(phone)
        } catch (e: Exception) {
            showState(e.message ?: "Не удалось загрузить сообщения")
        }
    }

    // Обновляет номер бригады. "Доступность" проверяется в callToTablet() (null → toast).
    private fun applyBrigadePhone(phone: String?) {
        if (phone.isNullOrBlank()) return
        currentBrigadePhone = phone
    }

    private fun sanitizePhone(raw: String): String {
        // Оставляем только цифры и опциональный ведущий +.
        val trimmed = raw.trim()
        val plus = if (trimmed.startsWith("+")) "+" else ""
        return plus + trimmed.filter { it.isDigit() }
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

    private fun callToTablet() {
        val phone = currentBrigadePhone
        if (phone.isNullOrBlank()) {
            Toast.makeText(this, "Номер бригады ещё не получен", Toast.LENGTH_SHORT).show()
            return
        }

        // Страховка: даже если PermissionManager пропустил, проверяем CALL_PHONE здесь.
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Нет разрешения на совершение звонков", Toast.LENGTH_SHORT).show()
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.CALL_PHONE),
                PermissionManager.REQUEST_CODE_CALL_PHONE
            )
            return
        }

        val sanitized = sanitizePhone(phone)
        val intent = Intent(Intent.ACTION_CALL, "tel:$sanitized".toUri())
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "На устройстве нет приложения для звонков", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            // На случай, если разрешение было отозвано во время работы приложения.
            Toast.makeText(this, "Нет разрешения на совершение звонков", Toast.LENGTH_SHORT).show()
        }
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
            // PATIENT_CONDITION остаётся обычным сообщением в чате: без диалогов и плашек,
            // но с теми же значимыми полями, которые показываем на экране принятия решения.
            text = if (isPatientCondition) buildPatientConditionText(message) else buildMessageBody(message)
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
        container.addView(time)
        card.addView(container)
        return card
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
            MessageType.PATIENT_CONDITION -> buildPatientConditionText(message)
            MessageType.TEXT -> "Сообщение без текста"
            null -> "Сообщение без текста"
        }
    }

    private fun buildPatientConditionText(message: MessageResponseDto): String {
        return buildString {
            append("Получены данные о состоянии пациента")
            if (!message.phoneNumber.isNullOrBlank()) {
                append("\nТелефон бригады: ${message.phoneNumber}")
            }

            val conditionText = PatientConditionFields.formatForChat(message.patientCondition)
            if (conditionText.isNotBlank()) {
                append("\n")
                append(conditionText)
            }
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_HOSPITALIZATION_ID = "EXTRA_HOSPITALIZATION_ID"
        const val EXTRA_CHAT_TITLE = "EXTRA_CHAT_TITLE"
        const val EXTRA_READ_ONLY = "EXTRA_READ_ONLY"
    }
}
