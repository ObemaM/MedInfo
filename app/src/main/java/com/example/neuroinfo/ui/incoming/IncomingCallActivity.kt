package com.example.neuroinfo.ui.incoming

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.neuroinfo.R
import com.example.neuroinfo.data.CallRepository
import com.example.neuroinfo.data.CallsManager // Наш синглтон для очереди
import com.example.neuroinfo.data.RetrofitClient
import com.example.neuroinfo.model.CallNotificationDto
import com.example.neuroinfo.ui.main.MainActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IncomingCallActivity : AppCompatActivity() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val callRepository = CallRepository(RetrofitClient.apiService)
    private var vibrator: Vibrator? = null
    private var mediaPlayer: MediaPlayer? = null

    private lateinit var messageEditText: TextInputEditText
    private lateinit var sideTabsRecyclerView: RecyclerView
    private lateinit var sideAdapter: SideTabsAdapter // Создадим далее

    private var currentCall: CallNotificationDto? = null
    private var countdownTimer: CountDownTimer? = null
    private lateinit var timerTextView: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Настройка отображения поверх блокировки (WakeLock + Keyguard)
        setupLockScreenFlags()

        setContentView(R.layout.activity_incoming_call)

        // 2. Инициализация UI
        messageEditText = findViewById(R.id.message_edit_text)
        sideTabsRecyclerView = findViewById(R.id.rv_side_tabs)

        val btnAccept = findViewById<MaterialButton>(R.id.button_confirm)
        val btnReject = findViewById<MaterialButton>(R.id.button_reject)

        // 3. Настройка боковой панели (корешков)
        setupSidePanel()

        // 4. Подписка на очередь вызовов
        observeCallsQueue()

        // 5. Кнопки
        btnAccept.setOnClickListener { handleCallAnswer(true) }
        btnReject.setOnClickListener { handleCallAnswer(false) }

        // 6. Звук и вибрация
        startAlerts()
        // Таймер на n сек
        timerTextView = findViewById(R.id.tv_timer)
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)
                ?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        acquireWakeLock()
    }

    private fun setupSidePanel() {
        sideAdapter = SideTabsAdapter { selectedCall ->
            displayCallDetails(selectedCall)
        }
        sideTabsRecyclerView.layoutManager = LinearLayoutManager(this)
        sideTabsRecyclerView.adapter = sideAdapter
    }

    private fun observeCallsQueue() {
        // Используем Coroutines для наблюдения за Flows в CallsManager
        lifecycleScope.launch {
            CallsManager.calls.collect { list ->
                if (list.isEmpty()) {
                    stopAlerts()
                    finish() // Если вызовов нет — закрываем экран
                } else {
                    sideAdapter.submitList(list)
                    // Если сейчас ничего не выбрано — показываем первый из списка
                    if (currentCall == null) {
                        displayCallDetails(list[0])
                    }
                }
            }
        }
    }

    private fun displayCallDetails(call: CallNotificationDto) {
        currentCall = call

        // Используем ID из вашего item_hospitalization.xml
        val infoBlock = findViewById<View>(R.id.patient_info_block)

        infoBlock.findViewById<TextView>(R.id.call_number_text).text = "Вызов №${call.callNumber}"
//        infoBlock.findViewById<TextView>(R.id.call_patient_text).text = call.fullName ?: "Неизвестно"
        infoBlock.findViewById<TextView>(R.id.call_address_text).text =
            "${call.street ?: "Н/Д"}, ${call.house ?: "Н/Д"}"

        infoBlock.findViewById<TextView>(R.id.urgency_data).text = "Срочность: ${call.urgency ?: "Н/Д"}"

        // Очищаем поле комментария при переключении между пациентами
        startVisualCountdown(30)
        messageEditText.setText("")
    }
    private fun startVisualCountdown(seconds: Int) {
        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(seconds * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secRemaining = millisUntilFinished / 1000
                timerTextView.text = "Осталось времени: 00:${String.format("%02d", secRemaining)}"

                // Если осталось меньше 10 сек — красим в красный
                if (secRemaining <= 10) {
                    timerTextView.setTextColor(Color.RED)
                } else {
                    timerTextView.setTextColor(Color.BLACK)
                }
            }

            override fun onFinish() {
                timerTextView.text = "ВРЕМЯ ИСТЕКЛО"
            }
        }.start()
    }
    private fun handleCallAnswer(accepted: Boolean) {
        val call = currentCall ?: return
        val callId = call.callNumber ?: return
        val comment = messageEditText.text.toString()
        val decision = if (accepted) "Accept" else "Reject"

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    callRepository.answerCall(callId, decision, comment)
                }

                if (result.isSuccess) {
                    Toast.makeText(this@IncomingCallActivity, "Отправлено", Toast.LENGTH_SHORT).show()
                    // Удаляем этот вызов из локальной очереди
                    CallsManager.removeCall(callId)
                    currentCall = null // Сбрасываем выбор, чтобы подхватился следующий из очереди
                } else {
                    Toast.makeText(this@IncomingCallActivity, "Ошибка сервера", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("CallAnswer", "Error: ${e.message}")
            }
        }
    }

    // --- Вспомогательные методы (Звук/Вибро) ---

    private fun startAlerts() {
        // Звук
        val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        mediaPlayer = MediaPlayer.create(this, notification)
        mediaPlayer?.isLooping = true
        mediaPlayer?.start()

        // Вибрация
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val pattern = longArrayOf(0, 500, 1000)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopAlerts() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "NeuroInfo:WakeLock"
        )
        wakeLock?.acquire(3 * 60 * 1000L) // 3 минуты
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlerts()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        countdownTimer?.cancel()
    }
}