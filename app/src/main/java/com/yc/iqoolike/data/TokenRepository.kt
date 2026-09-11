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
    private val gson = Gson()

    private val _latestToken = MutableStateFlow<TokenModel?>(null)
    val latestToken: StateFlow<TokenModel?> = _latestToken.asStateFlow()

    private val _historyList = MutableStateFlow<List<TokenModel>>(emptyList())
    val historyList: StateFlow<List<TokenModel>> = _historyList.asStateFlow()

    init {
        loadFromCache()
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

        @Volatile
        private var INSTANCE: TokenRepository? = null

        fun getInstance(context: Context): TokenRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TokenRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
