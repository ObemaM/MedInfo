package com.example.medinfo.config

import android.content.Context
import org.json.JSONObject
import java.io.File

object ConfigManager {

    private const val CONFIG_FILE_NAME = "config.json"

    enum class DecisionTriggerMode {
        HOSPITALIZATION,
        PATIENT_CONDITION
    }

    private var config: AppConfig = AppConfig.defaults()

    data class AppConfig(
        val serverBaseUrl: String,
        val signalrHubUrl: String,
        val httpConnectTimeoutSeconds: Int,
        val httpReadTimeoutSeconds: Int,
        val maxCallDurationMs: Long,
        val sessionPrefsName: String,
        val jwtTokenKey: String,
        val testModeDisableSignalR: Boolean,
        // Глобальный тумблер тестового вызова: когда выключен, кнопка имитации звонка
        // на главном экране и Doze-таймер FakeCallAlarmReceiver не работают.
        // По умолчанию выключен — чтобы в боевой сборке нельзя было случайно дёрнуть.
        val testCallEnabled: Boolean,
        val fakeCallDelayMinutes: Int,
        val notificationIdService: Int,
        val notificationChannelIdService: String,
        val decisionTriggerMode: DecisionTriggerMode
    ) {
        companion object {
            fun defaults(): AppConfig {
                return AppConfig(
                    serverBaseUrl = "http://46.146.213.95:27234",
                    signalrHubUrl = "http://46.146.213.95:27234/notifications",
                    httpConnectTimeoutSeconds = 20,
                    httpReadTimeoutSeconds = 20,
                    maxCallDurationMs = 2_700_000L,
                    sessionPrefsName = "app_session",
                    jwtTokenKey = "jwt_token",
                    testModeDisableSignalR = false,
                    testCallEnabled = false,
                    fakeCallDelayMinutes = 35,
                    notificationIdService = 101,
                    notificationChannelIdService = "MedInfo_SignalR_Service",
                    decisionTriggerMode = DecisionTriggerMode.HOSPITALIZATION
                )
            }
        }
    }

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

    @Synchronized
    fun save(context: Context, newConfig: AppConfig) {
        val normalizedConfig = newConfig.copy(
            serverBaseUrl = newConfig.serverBaseUrl.trimEnd('/'),
            signalrHubUrl = "${newConfig.serverBaseUrl.trimEnd('/')}/notifications"
        )

        val root = JSONObject().apply {
            put(
                "server",
                JSONObject().apply {
                    put("baseUrl", normalizedConfig.serverBaseUrl)
                    put("signalrHubUrl", normalizedConfig.signalrHubUrl)
                }
            )
            put(
                "timeouts",
                JSONObject().apply {
                    put("httpConnectTimeoutSeconds", normalizedConfig.httpConnectTimeoutSeconds)
                    put("httpReadTimeoutSeconds", normalizedConfig.httpReadTimeoutSeconds)
                }
            )
            put(
                "intervals",
                JSONObject().apply {
                    put("maxCallDurationMs", normalizedConfig.maxCallDurationMs)
                }
            )
            put(
                "storage",
                JSONObject().apply {
                    put("sessionPrefsName", normalizedConfig.sessionPrefsName)
                    put("jwtTokenKey", normalizedConfig.jwtTokenKey)
                }
            )
            put(
                "testing",
                JSONObject().apply {
                    put("disableSignalR", normalizedConfig.testModeDisableSignalR)
                    put("testCallEnabled", normalizedConfig.testCallEnabled)
                    put("fakeCallDelayMinutes", normalizedConfig.fakeCallDelayMinutes)
                }
            )
            put(
                "notifications",
                JSONObject().apply {
                    put("serviceNotificationId", normalizedConfig.notificationIdService)
                    put("serviceChannelId", normalizedConfig.notificationChannelIdService)
                }
            )
            put(
                "decisionFlow",
                JSONObject().apply {
                    put("triggerMode", normalizedConfig.decisionTriggerMode.name)
                }
            )
        }

        val internalFile = getInternalConfigFile(context)
        internalFile.writeText(root.toString(2))
        config = normalizedConfig
    }

    val serverBaseUrl: String get() = config.serverBaseUrl

    // Всегда собираем адрес нового SignalR-хаба из базового адреса сервера.
    val signalrHubUrl: String
        get() = "${config.serverBaseUrl.trimEnd('/')}/notifications"
    val httpConnectTimeoutSeconds: Int get() = config.httpConnectTimeoutSeconds
    val httpReadTimeoutSeconds: Int get() = config.httpReadTimeoutSeconds
    val maxCallDurationMs: Long get() = config.maxCallDurationMs
    val sessionPrefsName: String get() = config.sessionPrefsName
    val jwtTokenKey: String get() = config.jwtTokenKey
    val testModeDisableSignalR: Boolean get() = config.testModeDisableSignalR
    val testCallEnabled: Boolean get() = config.testCallEnabled
    val fakeCallDelayMinutes: Int get() = config.fakeCallDelayMinutes
    val notificationIdService: Int get() = config.notificationIdService
    val notificationChannelIdService: String get() = config.notificationChannelIdService
    val decisionTriggerMode: DecisionTriggerMode get() = config.decisionTriggerMode

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
        val testing = root.optJSONObject("testing")
        val notifications = root.optJSONObject("notifications")
        val decisionFlow = root.optJSONObject("decisionFlow")

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
            jwtTokenKey = storage?.optString("jwtTokenKey")?.takeIf { it.isNotBlank() } ?: defaults.jwtTokenKey,
            testModeDisableSignalR = testing?.optBoolean("disableSignalR", defaults.testModeDisableSignalR) ?: defaults.testModeDisableSignalR,
            testCallEnabled = testing?.optBoolean("testCallEnabled", defaults.testCallEnabled) ?: defaults.testCallEnabled,
            fakeCallDelayMinutes = testing?.optInt("fakeCallDelayMinutes", defaults.fakeCallDelayMinutes) ?: defaults.fakeCallDelayMinutes,
            notificationIdService = notifications?.optInt("serviceNotificationId", defaults.notificationIdService) ?: defaults.notificationIdService,
            notificationChannelIdService = notifications?.optString("serviceChannelId")?.takeIf { it.isNotBlank() } ?: defaults.notificationChannelIdService,
            decisionTriggerMode = parseDecisionTriggerMode(
                decisionFlow?.optString("triggerMode"),
                defaults.decisionTriggerMode
            )
        )
    }

    private fun parseDecisionTriggerMode(
        rawValue: String?,
        fallback: DecisionTriggerMode
    ): DecisionTriggerMode {
        val normalized = rawValue?.trim()?.uppercase() ?: return fallback
        return DecisionTriggerMode.entries.firstOrNull { it.name == normalized } ?: fallback
    }
}
