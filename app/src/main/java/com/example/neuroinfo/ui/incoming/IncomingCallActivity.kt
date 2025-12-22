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
import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.EditText
import com.example.neuroinfo.ui.main.MainActivity
import com.google.android.material.textfield.TextInputEditText


class IncomingCallActivity : AppCompatActivity() {


    private var wakeLock: PowerManager.WakeLock? = null
    private val callRepository = CallRepository(RetrofitClient.apiService)

    private var vibrator: Vibrator? = null
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private lateinit var callData: CallNotificationDto
    private lateinit var commentEditText: TextInputEditText

    // 💡 Привязка к ВАШИМ ID из XML
    private lateinit var patientInfoTextView: TextView
    private lateinit var acceptButton: Button
    private lateinit var rejectButton: Button


    // Переменная для воспроизведения звука
    private var mediaPlayer: MediaPlayer? = null
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        acquireWakeLock()
        setContentView(R.layout.activity_incoming_call)
        setContentView(R.layout.activity_incoming_call)
        commentEditText = findViewById(R.id.comment_edit_text)

        // 💡 Активация экрана: добавляем флаг совместимости для старых версий
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            )
        }

        // 1. Привязка View-элементов по ВАШИМ ID
        patientInfoTextView = findViewById(R.id.patientInfo)
        acceptButton = findViewById(R.id.acceptButton)
        rejectButton = findViewById(R.id.rejectButton)

        // 2. Получение данных вызова
        // 💡 Добавил проверку версии API для получения Serializable (требование Android 13+)
        callData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("CALL_DATA", CallNotificationDto::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("CALL_DATA") as? CallNotificationDto
        } ?: run { finish(); return }

        // 3. Отображение данных
        patientInfoTextView.text = formatCallDetails(callData)

        // 4. Обработчики кнопок
        acceptButton.setOnClickListener {
            handleCallAnswer(true)
        }
        rejectButton.setOnClickListener {
            handleCallAnswer(false)
        }
        val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        mediaPlayer = MediaPlayer.create(this, notification)
        mediaPlayer?.isLooping = true
        mediaPlayer?.start()
        startVibration()
    }
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
            "NeuroInfo:IncomingCallWakeLock"
        )

        // Захватываем замок на 5 минут (врачу хватит времени проснуться и нажать кнопку)
        wakeLock?.acquire(5 * 60 * 1000L)
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
    private fun startVibration() {
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        if (vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Создаем ритм: 0мс пауза, 500мс вибро, 500мс пауза...
                // -1 — не повторять, 0 — повторять бесконечно
                val pattern = longArrayOf(0, 500, 500)
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                // Для старых версий (просто ритм и повтор)
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 500, 500), 0)
            }
        }
    }

    private fun stopVibration() {
        vibrator?.cancel()
    }

    private fun handleCallAnswer(accepted: Boolean) {

        val userComment = commentEditText.text.toString()
        // 💡 Используем CallNumber как уникальный ID вызова для API
        val callId = callData.callNumber ?: run {
            Toast.makeText(this, "ID вызова отсутствует", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        val decisionString = if (accepted) "Accept" else "Reject"

        val comment = commentEditText.text.toString()

        mainScope.launch {
            try {
                // Вызываем API для ответа
                val result = withContext(Dispatchers.IO) {
                    callRepository.answerCall(callId, decisionString, comment)
                }

                if (result.isSuccess) {
                    val message = if (accepted) "Вызов принят." else "Вызов отклонен."
                    Toast.makeText(this@IncomingCallActivity, message, Toast.LENGTH_SHORT).show()


                    // 💡 Если приняли, можно сразу открыть MainActivity, чтобы увидеть детали
                    if (accepted) {
                        val intent = Intent(this@IncomingCallActivity, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    }
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Ошибка сервера."
                    Toast.makeText(this@IncomingCallActivity, "Ошибка: $error", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@IncomingCallActivity, "Ошибка сети: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                finish()
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        mediaPlayer?.stop() // Обязательно останавливаем при закрытии экрана
        stopVibration()
        mediaPlayer?.release()
    }
}
