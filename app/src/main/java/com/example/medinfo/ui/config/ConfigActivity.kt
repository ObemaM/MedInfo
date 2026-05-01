package com.example.medinfo.ui.config

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.medinfo.config.ConfigManager
import com.example.medinfo.data.network.RetrofitClient
import com.example.medinfo.databinding.ActivityConfigBinding
import com.google.android.material.textfield.TextInputLayout

class ConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfigBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fillFields(ConfigManager.getConfig())
        setupListeners()
    }

    private fun setupListeners() {
        binding.serverBaseUrlEditText.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    updateSignalRPreview(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) = Unit
            }
        )

        binding.saveButton.setOnClickListener {
            saveConfig()
        }

        binding.resetDefaultsButton.setOnClickListener {
            fillFields(ConfigManager.AppConfig.defaults())
            Toast.makeText(this, "Заполнены значения по умолчанию", Toast.LENGTH_SHORT).show()
        }

        binding.cancelButton.setOnClickListener {
            finish()
        }
    }

    private fun fillFields(config: ConfigManager.AppConfig) {
        // Экран редактирует только рабочие настройки; служебные ключи конфига сохраняем как есть.
        binding.serverBaseUrlEditText.setText(config.serverBaseUrl)
        binding.connectTimeoutEditText.setText(config.httpConnectTimeoutSeconds.toString())
        binding.readTimeoutEditText.setText(config.httpReadTimeoutSeconds.toString())
        binding.maxCallDurationEditText.setText((config.maxCallDurationMs / MILLIS_IN_MINUTE).toString())
        binding.fakeCallDelayEditText.setText(config.fakeCallDelayMinutes.toString())
        binding.disableSignalrSwitch.isChecked = config.testModeDisableSignalR
        updateSignalRPreview(config.serverBaseUrl)
        clearErrors()
    }

    private fun saveConfig() {
        clearErrors()

        val baseUrl = binding.serverBaseUrlEditText.text?.toString().orEmpty().trim().trimEnd('/')
        if (!isValidBaseUrl(baseUrl)) {
            binding.serverBaseUrlLayout.error = "Введите адрес вида http://server:port или https://server"
            return
        }

        val connectTimeout = readInt(
            layout = binding.connectTimeoutLayout,
            rawValue = binding.connectTimeoutEditText.text?.toString(),
            fieldName = "Таймаут подключения",
            range = 1..120
        ) ?: return

        val readTimeout = readInt(
            layout = binding.readTimeoutLayout,
            rawValue = binding.readTimeoutEditText.text?.toString(),
            fieldName = "Таймаут ответа",
            range = 1..120
        ) ?: return

        val maxCallDurationMinutes = readInt(
            layout = binding.maxCallDurationLayout,
            rawValue = binding.maxCallDurationEditText.text?.toString(),
            fieldName = "Таймер принятия решения",
            range = 1..240
        ) ?: return

        val fakeCallDelayMinutes = readInt(
            layout = binding.fakeCallDelayLayout,
            rawValue = binding.fakeCallDelayEditText.text?.toString(),
            fieldName = "Задержка тестового вызова",
            range = 1..1440
        ) ?: return

        val current = ConfigManager.getConfig()
        val newConfig = current.copy(
            serverBaseUrl = baseUrl,
            // SignalR-хаб сейчас всегда живет рядом с API, поэтому не даем руками разнести адреса.
            signalrHubUrl = "$baseUrl/notifications",
            httpConnectTimeoutSeconds = connectTimeout,
            httpReadTimeoutSeconds = readTimeout,
            maxCallDurationMs = maxCallDurationMinutes * MILLIS_IN_MINUTE,
            testModeDisableSignalR = binding.disableSignalrSwitch.isChecked,
            fakeCallDelayMinutes = fakeCallDelayMinutes
        )

        try {
            ConfigManager.save(this, newConfig)
            RetrofitClient.init(applicationContext)
            Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Не удалось сохранить настройки: ${e.message ?: "неизвестная ошибка"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun readInt(
        layout: TextInputLayout,
        rawValue: String?,
        fieldName: String,
        range: IntRange
    ): Int? {
        val value = rawValue?.trim()?.toIntOrNull()
        if (value == null || value !in range) {
            layout.error = "$fieldName: значение от ${range.first} до ${range.last}"
            return null
        }
        return value
    }

    private fun isValidBaseUrl(value: String): Boolean {
        if (value.isBlank()) return false
        val uri = Uri.parse(value)
        val scheme = uri.scheme ?: return false
        return (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
    }

    private fun updateSignalRPreview(baseUrl: String) {
        val normalized = baseUrl.trim().trimEnd('/')
        binding.signalrUrlText.text =
            if (normalized.isBlank()) {
                "SignalR: адрес появится после ввода сервера"
            } else {
                "SignalR: $normalized/notifications"
            }
    }

    private fun clearErrors() {
        binding.serverBaseUrlLayout.error = null
        binding.connectTimeoutLayout.error = null
        binding.readTimeoutLayout.error = null
        binding.maxCallDurationLayout.error = null
        binding.fakeCallDelayLayout.error = null
    }

    private companion object {
        const val MILLIS_IN_MINUTE = 60_000L
    }
}
