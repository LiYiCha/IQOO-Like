package com.yc.iqoolike.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Token 数据仓储管理
 */
class TokenRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("iqoo_token_prefs", Context.MODE_PRIVATE)
    private val settingsPrefs: SharedPreferences = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _latestToken = MutableStateFlow<TokenModel?>(null)
    val latestToken: StateFlow<TokenModel?> = _latestToken.asStateFlow()

    private val _historyList = MutableStateFlow<List<TokenModel>>(emptyList())
    val historyList: StateFlow<List<TokenModel>> = _historyList.asStateFlow()

    private val _isBypassSignatureEnabled = MutableStateFlow(
        settingsPrefs.getBoolean(Constants.KEY_BYPASS_SIGNATURE, true)
    )
    val isBypassSignatureEnabled: StateFlow<Boolean> = _isBypassSignatureEnabled.asStateFlow()

    private val _isModuleActive = MutableStateFlow(
        settingsPrefs.getBoolean(Constants.KEY_MODULE_ACTIVE, false)
    )
    val isModuleActive: StateFlow<Boolean> = _isModuleActive.asStateFlow()

    private val _targetPid = MutableStateFlow(
        settingsPrefs.getInt(Constants.KEY_TARGET_PID, 0)
    )
    val targetPid: StateFlow<Int> = _targetPid.asStateFlow()

    private val _lastHeartbeat = MutableStateFlow(
        settingsPrefs.getLong(Constants.KEY_LAST_HEARTBEAT, 0L)
    )
    val lastHeartbeat: StateFlow<Long> = _lastHeartbeat.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    init {
        loadFromCache()
        AppLogger.registerInAppCallback { addLog(it) }
    }

    fun addLog(entry: LogEntry) {
        val current = _logs.value.toMutableList()
        current.add(entry)
        if (current.size > MAX_LOGS) {
            current.removeAt(0)
        }
        _logs.value = current
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    /**
     * 设置模块激活状态并持久化
     */
    fun setModuleActive(active: Boolean) {
        _isModuleActive.value = active
        settingsPrefs.edit().putBoolean(Constants.KEY_MODULE_ACTIVE, active).apply()
    }

    /**
     * 更新心跳数据 (PID 与当前时间戳)
     */
    fun updateHeartbeat(pid: Int) {
        val now = System.currentTimeMillis()
        _isModuleActive.value = true
        _targetPid.value = pid
        _lastHeartbeat.value = now
        settingsPrefs.edit()
            .putBoolean(Constants.KEY_MODULE_ACTIVE, true)
            .putInt(Constants.KEY_TARGET_PID, pid)
            .putLong(Constants.KEY_LAST_HEARTBEAT, now)
            .apply()
    }

    /**
     * 更新签名校验绕过开关（同步至 SharedPreferences 与标记文件）
     */
    fun setBypassSignatureEnabled(enabled: Boolean) {
        _isBypassSignatureEnabled.value = enabled
        settingsPrefs.edit().putBoolean(Constants.KEY_BYPASS_SIGNATURE, enabled).apply()

        try {
            val internalFlag = File(context.filesDir, Constants.FLAG_BYPASS_DISABLED)
            val externalDir = context.getExternalFilesDir(null)
            val externalFlag = if (externalDir != null) File(externalDir, Constants.FLAG_BYPASS_DISABLED) else null

            if (enabled) {
                if (internalFlag.exists()) internalFlag.delete()
                externalFlag?.let { if (it.exists()) it.delete() }
            } else {
                if (!internalFlag.exists()) internalFlag.createNewFile()
                externalFlag?.let { if (!it.exists()) it.createNewFile() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 保存最新 Token，并加入历史记录
     */
    fun saveToken(token: TokenModel) {
        _latestToken.value = token
        prefs.edit().putString(KEY_LATEST_TOKEN, token.toJson()).apply()

        // 写入本地内部快照文件
        try {
            val file = File(context.filesDir, FILE_SNAPSHOT_NAME)
            file.writeText(token.toJson())
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 添加到历史记录（去重：若已存在相同 accessToken 则更新）
        val currentHistory = _historyList.value.toMutableList()
        currentHistory.removeAll { it.accessToken == token.accessToken }
        currentHistory.add(0, token)
        if (currentHistory.size > MAX_HISTORY) {
            currentHistory.subList(MAX_HISTORY, currentHistory.size).clear()
        }
        _historyList.value = currentHistory

        saveHistoryToPrefs(currentHistory)
    }

    /**
     * 从本地缓存加载
     */
    fun loadFromCache() {
        val json = prefs.getString(KEY_LATEST_TOKEN, null)
        if (!json.isNullOrEmpty()) {
            _latestToken.value = TokenModel.fromJson(json)
        } else {
            // 尝试读取本地快照文件
            val file = File(context.filesDir, FILE_SNAPSHOT_NAME)
            if (file.exists()) {
                val fileContent = file.readText()
                val token = TokenModel.fromJson(fileContent)
                if (token != null) {
                    _latestToken.value = token
                }
            }
        }

        val historyJson = prefs.getString(KEY_HISTORY_LIST, null)
        if (!historyJson.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<List<TokenModel>>() {}.type
                val list: List<TokenModel> = gson.fromJson(historyJson, type)
                _historyList.value = list
            } catch (e: Exception) {
                _historyList.value = emptyList()
            }
        }
    }

    /**
     * 清空历史记录
     */
    fun clearHistory() {
        _historyList.value = emptyList()
        prefs.edit().remove(KEY_HISTORY_LIST).apply()
    }

    /**
     * 删除单条历史
     */
    fun removeHistoryItem(item: TokenModel) {
        val current = _historyList.value.toMutableList()
        current.removeAll { it.accessToken == item.accessToken && it.timestamp == item.timestamp }
        _historyList.value = current
        saveHistoryToPrefs(current)
    }

    private fun saveHistoryToPrefs(list: List<TokenModel>) {
        val json = gson.toJson(list)
        prefs.edit().putString(KEY_HISTORY_LIST, json).apply()
    }

    companion object {
        private const val KEY_LATEST_TOKEN = "key_latest_token"
        private const val KEY_HISTORY_LIST = "key_history_list"
        const val FILE_SNAPSHOT_NAME = "iqoo_token.json"
        private const val MAX_HISTORY = 50
        private const val MAX_LOGS = 300

        @Volatile
        private var INSTANCE: TokenRepository? = null

        fun getInstance(context: Context): TokenRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TokenRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
