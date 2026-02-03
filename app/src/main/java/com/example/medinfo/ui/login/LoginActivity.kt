package com.example.medinfo.ui.login

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.medinfo.R
import com.example.medinfo.data.LoginRepository
import com.example.medinfo.data.RetrofitClient
import com.example.medinfo.data.TokenInterceptor
import com.example.medinfo.services.SignalRService
import com.example.medinfo.ui.main.MainActivity
import com.example.medinfo.util.toSha256
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var loginEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var errorTextView: TextView
    private lateinit var sharedPreferences: SharedPreferences

    private val loginRepository by lazy { LoginRepository(RetrofitClient.apiService) }
    private val mainScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("app_session", Context.MODE_PRIVATE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.app.ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                101
            )
        }
        // 1. Автоматический вход
        if (sharedPreferences.getBoolean("isLoggedIn", false)) {
            Log.d("SignalR", "Автоматический вход: запускаем сервис")
            startSignalRService()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        loginEditText = findViewById(R.id.login)
        passwordEditText = findViewById(R.id.password)
        loginButton = findViewById(R.id.loginButton)
        errorTextView = findViewById(R.id.errorTextView)

        loginButton.setOnClickListener {
            errorTextView.visibility = View.GONE
            val inputLogin = loginEditText.text.toString()
            val inputPassword = passwordEditText.text.toString()

            if (inputLogin.isBlank() || inputPassword.isBlank()) {
                hideKeyboard()
                errorTextView.text = "Введите логин и пароль"
                errorTextView.visibility = View.VISIBLE
                return@setOnClickListener
            }

            mainScope.launch {
                val inputPasswordHash = inputPassword.toSha256()
                try {
                    val result = withContext(Dispatchers.IO) {
                        loginRepository.login(inputLogin, inputPasswordHash)
                    }

                    if (result.success && result.content != null) {
                        saveSession(inputLogin, result.content)
                        Log.d("SignalR", "Успешный логин: запускаем сервис") // <-- Добавить лог
                        startSignalRService()
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    } else {
                        hideKeyboard()
                        val errorMessage = result.messages?.firstOrNull() ?: "Неизвестная ошибка аутентификации"
                        errorTextView.text = errorMessage
                        errorTextView.visibility = View.VISIBLE
                    }
                } catch (e: Exception) {
                    hideKeyboard()
                    errorTextView.text = "Ошибка подключения к серверу: ${e.message}"
                    errorTextView.visibility = View.VISIBLE
                    e.printStackTrace()
                }
            }
        }
    }

    // Метод для запуска SignalR сервиса
    private fun startSignalRService() {
        Log.d("SignalR", "Вызов метода startSignalRService") // <-- Добавить лог
        val serviceIntent = Intent(this, SignalRService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Log.d("SignalR", "Команда startForegroundService отправлена в систему")
        } catch (e: Exception) {
            Log.e("SignalR", "ОШИБКА при запуске сервиса: ${e.message}")
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val view = currentFocus ?: window.decorView
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun saveSession(login: String, token: String) {
        TokenInterceptor.saveToken(this, token)
        with(sharedPreferences.edit()) {
            putBoolean("isLoggedIn", true)
            putString("user_login", login)
            apply()
        }
    }
}