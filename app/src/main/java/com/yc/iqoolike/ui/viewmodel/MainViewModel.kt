package com.yc.iqoolike.ui.viewmodel

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yc.iqoolike.IQOOApplication
import com.yc.iqoolike.data.SecurityUtil
import com.yc.iqoolike.data.TokenModel
import com.yc.iqoolike.data.TokenRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val repository: TokenRepository = IQOOApplication.instance.repository

    val latestToken: StateFlow<TokenModel?> = repository.latestToken
    val historyList: StateFlow<List<TokenModel>> = repository.historyList
    val isBypassSignatureEnabled: StateFlow<Boolean> = repository.isBypassSignatureEnabled

    fun setBypassSignatureEnabled(enabled: Boolean) {
        repository.setBypassSignatureEnabled(enabled)
    }

    private val _isModuleActive = MutableStateFlow(false)
    val isModuleActive: StateFlow<Boolean> = _isModuleActive.asStateFlow()

    private val _isTargetInstalled = MutableStateFlow(false)
    val isTargetInstalled: StateFlow<Boolean> = _isTargetInstalled.asStateFlow()

    private val _isTargetRunning = MutableStateFlow(false)
    val isTargetRunning: StateFlow<Boolean> = _isTargetRunning.asStateFlow()

    private val _isFetching = MutableStateFlow(false)
    val isFetching: StateFlow<Boolean> = _isFetching.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private var currentNonce: String = ""
    private var timeoutJob: Job? = null

    init {
        setModuleActive(isModuleActiveNative())
    }

    /**
     * 模块自检测（Hook 框架生效时会被替换返回 true）
     */
    fun setModuleActive(active: Boolean) {
        _isModuleActive.value = active
    }

    fun isModuleActiveNative(): Boolean {
        return false // 由 Xposed/libxposed hook 替换为 true
    }

    /**
     * 刷新环境状态（目标 App 安装与进程存活状态）
     */
    fun refreshEnvironmentStatus(context: Context) {
        try {
            val pm = context.packageManager
            pm.getPackageInfo(TARGET_PKG, 0)
            _isTargetInstalled.value = true
        } catch (e: Exception) {
            _isTargetInstalled.value = false
        }

        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningProcesses = am.runningAppProcesses
            _isTargetRunning.value = runningProcesses?.any { it.processName.contains(TARGET_PKG) } == true
        } catch (e: Exception) {
            _isTargetRunning.value = false
        }
    }

    /**
     * 发起主动拉取凭证指令
     */
    fun fetchToken(context: Context) {
        if (_isFetching.value) return
        _isFetching.value = true
        _statusMessage.value = "正在向 iQOO 社区发送拉取指令..."

        currentNonce = SecurityUtil.generateNonce()

        try {
            val pullIntent = Intent(ACTION_PULL).apply {
                setPackage(TARGET_PKG)
                putExtra("nonce", currentNonce)
            }
            context.sendBroadcast(pullIntent)
            Log.i(TAG, "已发送显式拉取广播: $ACTION_PULL, nonce=$currentNonce")

            // 启动 15 秒超时倒计时
            timeoutJob?.cancel()
            timeoutJob = viewModelScope.launch {
                delay(15000L)
                if (_isFetching.value) {
                    _isFetching.value = false
                    _statusMessage.value = "请求超时：目标进程可能未启动或 Hook 未命中"
                }
            }
        } catch (e: Exception) {
            _isFetching.value = false
            _statusMessage.value = "发送广播失败: ${e.message}"
        }
    }

    /**
     * 处理收到的回传凭证结果
     */
    fun onResultReceived(json: String?, sig: String?, nonce: String?) {
        if (json.isNullOrEmpty()) return

        // 校验 HMAC 签名防伪造
        val isValidSig = SecurityUtil.verifySignature(json, sig ?: "")
        if (!isValidSig) {
            Log.w(TAG, "接收到的结果 HMAC 签名校验未通过，丢弃数据")
            return
        }

        val token = TokenModel.fromJson(json)
        if (token != null) {
            timeoutJob?.cancel()
            repository.saveToken(token)
            _isFetching.value = false
            _statusMessage.value = "获取成功（来源：${token.source}）"
            Log.i(TAG, "成功保存来自 Hook 的 Token: userId=${token.userId}")
        }
    }

    /**
     * 拉起 iQOO 社区（仅拉起进程，无需手动登录）
     */
    fun launchTargetApp(context: Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(TARGET_PKG)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                _statusMessage.value = "已唤起 iQOO 社区进程"
            } else {
                _statusMessage.value = "未找到 iQOO 社区应用"
            }
        } catch (e: Exception) {
            _statusMessage.value = "唤起失败: ${e.message}"
        }
    }

    fun clearHistory() {
        repository.clearHistory()
    }

    fun removeHistoryItem(item: TokenModel) {
        repository.removeHistoryItem(item)
    }

    companion object {
        private const val TAG = "IQOO_MainViewModel"
        const val TARGET_PKG = "com.iqoo.bbs"
        const val ACTION_PULL = "iqoobbs.action.PULL"
    }
}
