package com.example.medinfo.ui.login

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import com.example.medinfo.data.repository.LoginRepository
import com.example.medinfo.data.network.RetrofitClient
import com.example.medinfo.data.network.TokenInterceptor
import com.example.medinfo.data.signalr.SignalRService
import com.example.medinfo.ui.config.ConfigActivity
import com.example.medinfo.ui.main.MainActivity
import com.example.medinfo.util.toSha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import com.example.medinfo.databinding.ActivityLoginBinding
import androidx.core.content.edit
import com.example.medinfo.ui.incoming.IncomingCallPermissionHelper
import com.example.medinfo.util.PermissionManager

class LoginActivity: AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var sharedPreferences: SharedPreferences

    private val loginRepository by lazy { LoginRepository(RetrofitClient.apiServiceService) }
    private var isLoginInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("app_session", Context.MODE_PRIVATE)

        binding.loginButton.setOnClickListener {
            if (isLoginInProgress) return@setOnClickListener

            val inputText = binding.login.text?.toString()
            val inputPassword = binding.password.text?.toString()

            if (inputPassword.isNullOrBlank() || inputText.isNullOrBlank()) {
                hideKeyboard()
                binding.errorTextView.text = "Введите логин и пароль"
                binding.errorTextView.visibility = View.VISIBLE
                return@setOnClickListener
            }

            if (isLocalConfigLogin(inputText, inputPassword)) {
                // Локальный админ-вход открывает только настройки и не создает серверную сессию.
                hideKeyboard()
                binding.errorTextView.visibility = View.GONE
                startActivity(Intent(this, ConfigActivity::class.java))
                return@setOnClickListener
            }

            // Уничтожается при уничтожении Activity
            lifecycleScope.launch {
                setLoginLoading(true)
                val inputPasswordHash = inputPassword.toSha256()
                try {
                    val result = withContext(Dispatchers.IO) {
                        loginRepository.login(inputText, inputPasswordHash)
                    }

                    if (result.success && result.content != null) {
                        setLoginLoading(false)
                        // Проверяем разрешения перед входом
                        PermissionManager.enforcePermissions(this@LoginActivity) {
                            saveSession(inputText, result.content)
                            startSignalRService()
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finish()
                        }
                    } else {
                        val errorMessage = result.messages.firstOrNull() ?: "Неизвестная ошибка аутентификации"
                        showLoginError(errorMessage)
                    }
                } catch (e: Exception) {
                    showLoginError("Ошибка подключения к серверу. Проверьте интернет-соединение.")
                    e.printStackTrace()
                }
            }
        }
    }

    private fun setLoginLoading(loading: Boolean) {
        isLoginInProgress = loading
        binding.loginProgress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.loginButton.isEnabled = !loading
        binding.login.isEnabled = !loading
        binding.password.isEnabled = !loading
        if (loading) {
            hideKeyboard()
            binding.errorTextView.visibility = View.GONE
        }
    }

    private fun showLoginError(message: String) {
        setLoginLoading(false)
        hideKeyboard()
        binding.errorTextView.text = message
        binding.errorTextView.visibility = View.VISIBLE
    }

    // Метод для запуска SignalR сервиса
    private fun startSignalRService() {
        val serviceIntent = Intent(this, SignalRService::class.java)
        try {
            startForegroundService(serviceIntent)
        } catch (e: Exception) {
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

    private fun isLocalConfigLogin(login: String, password: String): Boolean {
        // Временный простой доступ к конфигу: позже пароль можно вынести в защищенную настройку.
        return login.trim().equals(CONFIG_LOGIN, ignoreCase = true) && password == CONFIG_PASSWORD
    }

    // Called when returning from settings to continue permission checks
    override fun onResume() {
        super.onResume()
        // Re-check permissions when returning from settings
        PermissionManager.enforcePermissions(this) {
            proceedWithLoginFlow()
        }
    }

    private var loginFlowStarted = false

    private fun proceedWithLoginFlow() {
        if (loginFlowStarted) return
        loginFlowStarted = true

        // Continue with login setup
        IncomingCallPermissionHelper.ensurePermissions(this)

        // Все runtime-разрешения одним запросом; PermissionManager заблокирует вход при отказе.
        val runtimePermissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            add(android.Manifest.permission.CALL_PHONE)
        }
        if (runtimePermissions.isNotEmpty()) {
            androidx.core.app.ActivityCompat.requestPermissions(
                this,
                runtimePermissions.toTypedArray(),
                101
            )
        }

        // Check auto-login
        if (sharedPreferences.getBoolean("isLoggedIn", false)) {
            startSignalRService()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private companion object {
        const val CONFIG_LOGIN = "admin"
        const val CONFIG_PASSWORD = "0000"
    }
}
