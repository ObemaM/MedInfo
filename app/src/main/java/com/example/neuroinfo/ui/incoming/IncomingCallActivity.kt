package com.example.neuroinfo.ui.incoming

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.neuroinfo.R
import com.example.neuroinfo.data.CallRepository
import com.example.neuroinfo.data.RetrofitClient
import com.example.neuroinfo.model.CallNotificationDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IncomingCallActivity : AppCompatActivity() {

    private val callRepository = CallRepository(RetrofitClient.apiService)
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private lateinit var callData: CallNotificationDto

    // 💡 Привязка к ВАШИМ ID из XML
    private lateinit var patientInfoTextView: TextView
    private lateinit var acceptButton: Button
    private lateinit var rejectButton: Button


    // Переменная для воспроизведения звука, если вы её используете
    // private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incoming_call)

        // 💡 Активация экрана
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        )

        // 1. Привязка View-элементов по ВАШИМ ID
        patientInfoTextView = findViewById(R.id.patientInfo)    // Ваш TextView
        acceptButton = findViewById(R.id.acceptButton)          // Ваша кнопка Принять
        rejectButton = findViewById(R.id.rejectButton)          // Ваша кнопка Отказаться

        // 2. Получение данных вызова
        // Проверяем, что объект CallNotificationDto действительно Serializable
        callData = intent.getSerializableExtra("CALL_DATA") as? CallNotificationDto
            ?: run { finish(); return }

        // 3. Отображение данных
        // 💡 Отображаем все детали вызова в одном TextView
        patientInfoTextView.text = formatCallDetails(callData)

        // 4. Обработчики кнопок
        acceptButton.setOnClickListener {
            handleCallAnswer(true)
        }
        rejectButton.setOnClickListener {
            handleCallAnswer(false)
        }

        // 5. Остановка звука из Service (если Service его запускал)
        // stopCallSound()
    }

    /**
     * Форматирует данные о вызове для отображения в одном TextView.
     */
    private fun formatCallDetails(data: CallNotificationDto): String {
        return buildString {
            append("Пациент: ${data.fullName ?: "Неизвестно"}\n") // FullName [cite: 642]
            append("Возраст: ${data.age ?: "Н/Д"}, Пол: ${data.sex ?: "Н/Д"}\n") // Age [cite: 643], Sex [cite: 644]
            append("--- Адрес ---\n")
            append("Район: ${data.district ?: "Н/Д"}\n") // District [cite: 647]
            append("Улица: ${data.street ?: "Н/Д"}, Дом: ${data.house ?: "Н/Д"}\n") // Street [cite: 649], House [cite: 650]
            append("--- Причина ---\n")
            append("${data.reason ?: "Не указана"}") // Reason [cite: 645]
        }
    }


    private fun handleCallAnswer(accepted: Boolean) {
        val callId = callData.callNumber ?: run { finish(); return }

        // 💡 Логин пользователя здесь не нужен, так как токен уже в заголовке
        // val personalNumber = getSharedPreferences("app_session", Context.MODE_PRIVATE)
        //     .getString("user_login", "") ?: ""

        // 1. Определяем строковое решение
        val decisionString = if (accepted) "Accept" else "Reject"

        mainScope.launch {
            try {
                // Вызываем API для ответа, возвращает Result<Unit>
                val result = withContext(Dispatchers.IO) {
                    callRepository.answerCall(callId, decisionString)
                }

                // 2. ИСПОЛЬЗУЕМ МЕТОДЫ Result<T> для обработки:
                if (result.isSuccess) { // ✅ Заменяет response.success
                    val message = if (accepted) "Вызов принят." else "Вызов отклонен."
                    Toast.makeText(this@IncomingCallActivity, message, Toast.LENGTH_SHORT).show()
                } else {
                    // 💡 Извлекаем сообщение об ошибке из исключения
                    val error = result.exceptionOrNull()?.message ?: "Неизвестная ошибка сервера."
                    Toast.makeText(this@IncomingCallActivity, "Ошибка: $error", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                // Ошибка сети или другая критическая ошибка
                Toast.makeText(this@IncomingCallActivity, "Ошибка сети. Проверьте подключение.", Toast.LENGTH_LONG).show()
            } finally {
                // Всегда закрываем Activity
                finish()
            }
        }
    }
}
