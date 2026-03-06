package com.example.medinfo.config

import android.content.Context
import org.json.JSONObject
import java.io.File

object ConfigManager {

    private const val CONFIG_FILE_NAME = "config.json"

    private var config: AppConfig = AppConfig.defaults()

    data class AppConfig(
        val serverBaseUrl: String,
        val signalrHubUrl: String,
        val httpConnectTimeoutSeconds: Int,
        val httpReadTimeoutSeconds: Int,
        val maxCallDurationMs: Long,
        val sessionPrefsName: String,
        val jwtTokenKey: String
    ) {
        companion object {
            fun defaults(): AppConfig {
                return AppConfig(
                    serverBaseUrl = "http://46.146.213.95:27234",
                    signalrHubUrl = "http://46.146.213.95:27234/call",
                    httpConnectTimeoutSeconds = 20,
                    httpReadTimeoutSeconds = 20,
                    maxCallDurationMs = 2_400_000L,
                    sessionPrefsName = "app_session",
                    jwtTokenKey = "jwt_token"
                )
            }
        }
    }

    /**
     * Loads config from internal storage if present.
     * On first run copies the seed config from assets into internal storage.
     */
    @Synchronized
    fun initialize(context: Context) {
        val internalFile = File(context.filesDir, CONFIG_FILE_NAME)

        if (!internalFile.exists()) {
            try {
                context.assets.open(CONFIG_FILE_NAME).use { input ->
                    internalFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (_: Exception) {
                config = AppConfig.defaults()
                return
            }
        }

        loadFromFile(internalFile)
    }

    @Synchronized
    fun reload(context: Context) {
        val internalFile = File(context.filesDir, CONFIG_FILE_NAME)
        if (!internalFile.exists()) {
            config = AppConfig.defaults()
            return
        }
        loadFromFile(internalFile)
    }

    @Synchronized
    fun getInternalConfigFile(context: Context): File {
        return File(context.filesDir, CONFIG_FILE_NAME)
    }

    @Synchronized
    fun getConfig(): AppConfig = config

    val serverBaseUrl: String get() = config.serverBaseUrl
    val signalrHubUrl: String get() = config.signalrHubUrl
    val httpConnectTimeoutSeconds: Int get() = config.httpConnectTimeoutSeconds
    val httpReadTimeoutSeconds: Int get() = config.httpReadTimeoutSeconds
    val maxCallDurationMs: Long get() = config.maxCallDurationMs
    val sessionPrefsName: String get() = config.sessionPrefsName
    val jwtTokenKey: String get() = config.jwtTokenKey

    private fun loadFromFile(file: File) {
        config = try {
            val json = file.readText()
            parseConfig(json)
        } catch (_: Exception) {
            AppConfig.defaults()
        }
    }

    private fun parseConfig(jsonString: String): AppConfig {
        val root = JSONObject(jsonString)

        val server = root.optJSONObject("server")
        val timeouts = root.optJSONObject("timeouts")
        val intervals = root.optJSONObject("intervals")
        val storage = root.optJSONObject("storage")

        val defaults = AppConfig.defaults()

        return AppConfig(
            serverBaseUrl = server?.optString("baseUrl")?.takeIf { it.isNotBlank() } ?: defaults.serverBaseUrl,
            signalrHubUrl = server?.optString("signalrHubUrl")?.takeIf { it.isNotBlank() } ?: defaults.signalrHubUrl,
            httpConnectTimeoutSeconds = timeouts?.optInt("httpConnectTimeoutSeconds", defaults.httpConnectTimeoutSeconds)
                ?: defaults.httpConnectTimeoutSeconds,
            httpReadTimeoutSeconds = timeouts?.optInt("httpReadTimeoutSeconds", defaults.httpReadTimeoutSeconds)
                ?: defaults.httpReadTimeoutSeconds,
            maxCallDurationMs = intervals?.optLong("maxCallDurationMs", defaults.maxCallDurationMs) ?: defaults.maxCallDurationMs,
            sessionPrefsName = storage?.optString("sessionPrefsName")?.takeIf { it.isNotBlank() } ?: defaults.sessionPrefsName,
            jwtTokenKey = storage?.optString("jwtTokenKey")?.takeIf { it.isNotBlank() } ?: defaults.jwtTokenKey
        )
    }
}
