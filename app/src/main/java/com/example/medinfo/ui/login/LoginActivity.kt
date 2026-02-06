package com.example.medinfo.ui.login

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import com.example.medinfo.data.LoginRepository
import com.example.medinfo.data.RetrofitClient
import com.example.medinfo.data.TokenInterceptor
import com.example.medinfo.services.SignalRService
import com.example.medinfo.ui.main.MainActivity
import com.example.medinfo.util.toSha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import com.example.medinfo.databinding.ActivityLoginBinding
import androidx.core.content.edit

class LoginActivity: AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var sharedPreferences: SharedPreferences

    private val loginRepository by lazy { LoginRepository(RetrofitClient.apiService) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("app_session", Context.MODE_PRIVATE)

        // Если версия андроида 13 и выше, то запрос на уведомления
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.app.ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                101
            )
        }

        // Автоматический вход
        if (sharedPreferences.getBoolean("isLoggedIn", false)) {
            Log.d("SignalR", "Автоматический вход: запускаем сервис")
            startSignalRService()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        binding.loginButton.setOnClickListener {
            val inputText = binding.login.text?.toString()
            val inputPassword = binding.password.text?.toString()

            if (inputPassword.isNullOrBlank() || inputText.isNullOrBlank()) {
                hideKeyboard()
                binding.errorTextView.text = "Введите логин и пароль"
                binding.errorTextView.visibility = View.VISIBLE
                return@setOnClickListener
            }

            // Уничтожается при уничтожении Activity
            lifecycleScope.launch {
                val inputPasswordHash = inputPassword.toSha256()
                try {
                    val result = withContext(Dispatchers.IO) {
                        loginRepository.login(inputText, inputPasswordHash)
                    }

                    if (result.success && result.content != null) {
                        saveSession(inputText, result.content)
                        Log.d("SignalR", "Успешный логин: запускаем сервис") // <-- Добавить лог
                        startSignalRService()
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    } else {
                        hideKeyboard()
                        val errorMessage = result.messages.firstOrNull() ?: "Неизвестная ошибка аутентификации"
                        binding.errorTextView.text = errorMessage
                        binding.errorTextView.visibility = View.VISIBLE
                    }
                } catch (e: Exception) {
                    hideKeyboard()
                    binding.errorTextView.text = "Ошибка подключения к серверу: ${e.message}"
                    binding.errorTextView.visibility = View.VISIBLE
                    e.printStackTrace()
                }
            }
        }
    }

    // Метод для запуска SignalR сервиса
    private fun startSignalRService() {
        Log.d("SignalR", "Вызов метода startSignalRService")
        val serviceIntent = Intent(this, SignalRService::class.java)
        try {
            startForegroundService(serviceIntent)
            Log.d("SignalR", "Команда startForegroundService отправлена в систему")
        } catch (e: Exception) {
            Log.e("SignalR", "ОШИБКА при запуске сервиса: ${e.message}")
        }
    }


    // Убирает клавиатуру
    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val view = currentFocus ?: window.decorView
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    // Сохраняет сессию и JWT токен
    private fun saveSession(login: String, token: String) {
        TokenInterceptor.saveToken(this, token)
        sharedPreferences.edit {
            putBoolean("isLoggedIn", true)
            putString("user_login", login)
        }
    }
}