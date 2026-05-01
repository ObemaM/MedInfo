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
import androidx.lifecycle.lifecycleScope
import com.example.medinfo.R
import com.example.medinfo.data.network.RetrofitClient
import com.example.medinfo.data.repository.HospitalizationRepository
import com.example.medinfo.databinding.ActivityChatBinding
import com.example.medinfo.model.api.MessageOrigin
import com.example.medinfo.model.api.MessageResponseDto
import com.example.medinfo.model.api.MessageType
import com.example.medinfo.model.api.PatientConditionResponseDto
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
    private var readOnly: Boolean = false

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
        binding.messageEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }

        loadMessages()
    }

    private fun loadMessages() {
        showState("Загрузка сообщений...")

        lifecycleScope.launch {
            try {
                val messages = withContext(Dispatchers.IO) {
                    hospitalizationRepository.getMessages(hospitalizationId).content.orEmpty()
                }
                renderMessages(messages)
            } catch (e: Exception) {
                showState(e.message ?: "Не удалось загрузить сообщения")
            }
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

        if (messages.isEmpty()) {
            showState("Сообщений пока нет")
            return
        }

        messages.sortedBy { it.receptionTime }.forEach { message ->
            binding.messagesContainer.addView(createMessageBubble(message))
        }

        binding.messagesScroll.post {
            binding.messagesScroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun createMessageBubble(message: MessageResponseDto): View {
        val isOwn = MessageOrigin.fromId(message.origin) == MessageOrigin.INFORMATOR_APP
        val card = MaterialCardView(this).apply {
            radius = 12.dp().toFloat()
            cardElevation = 0f
            strokeWidth = 1.dp()
            strokeColor = resources.getColor(R.color.border_gray_1, null)
            setCardBackgroundColor(
                resources.getColor(if (isOwn) R.color.blue_2 else R.color.background_2, null)
            )
            layoutParams = LinearLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.78f).toInt(),
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
            text = buildMessageBody(message)
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
            MessageType.PATIENT_CONDITION -> buildPatientConditionText(message.patientCondition)
            MessageType.TEXT -> "Сообщение без текста"
            null -> "Сообщение без текста"
        }
    }

    private fun buildPatientConditionText(condition: PatientConditionResponseDto?): String {
        if (condition == null) return "Переданы данные состояния пациента"

        // Пока показываем жизненно важные показатели как текст; позже это можно заменить карточкой состояния.
        return buildList {
            add("Переданы данные состояния пациента")
            condition.consciousness?.let { add("Сознание: $it") }
            condition.bloodPressure?.let { add("АД: $it") }
            condition.heartRate?.let { add("Пульс: $it") }
            condition.respirationRate?.let { add("ЧДД: $it") }
            condition.temperature?.let { add("Температура: $it") }
            condition.spO2?.let { add("SpO2: $it") }
            condition.glucometry?.let { add("Глюкоза: $it") }
        }.joinToString("\n")
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_HOSPITALIZATION_ID = "EXTRA_HOSPITALIZATION_ID"
        const val EXTRA_CHAT_TITLE = "EXTRA_CHAT_TITLE"
        const val EXTRA_READ_ONLY = "EXTRA_READ_ONLY"
    }
}
