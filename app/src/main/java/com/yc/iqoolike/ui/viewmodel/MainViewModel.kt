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

    val isModuleActive: StateFlow<Boolean> = repository.isModuleActive
    val targetPid: StateFlow<Int> = repository.targetPid

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
        if (isModuleActiveNative()) {
            setModuleActive(true)
        }
    }

    /**
     * 模块自检测设置（仅在确认激活时更新，防止被假信号覆盖）
     */
    fun setModuleActive(active: Boolean) {
        if (active) {
            repository.setModuleActive(true)
        }
    }

    fun isModuleActiveNative(): Boolean {
        return false // 由 Xposed/libxposed hook 替换为 true
    }

    /**
     * 收到宿主进程回传的心跳 PONG
     */
    fun onPongReceived(pid: Int) {
        repository.updateHeartbeat(pid)
        _isTargetRunning.value = true
        _statusMessage.value = "✓ 模块已在宿主中就绪 (PID: $pid)"
        Log.i(TAG, "收到宿主回传 PONG 心跳，已标记宿主存活且模块激活 (PID: $pid)")
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

        // 检查最近是否有心跳（60秒内收到过PONG，标记目标运行中）
        val lastHb = repository.lastHeartbeat.value
        val pid = repository.targetPid.value
        if (pid > 0 && System.currentTimeMillis() - lastHb < 60000L) {
            _isTargetRunning.value = true
        }

        // 发送 PING 广播主动探测宿主进程与 Hook 激活状态（完美绕过 Android 14 getRunningAppProcesses 跨进程权限盲区）
        try {
            val pingIntent = Intent(com.yc.iqoolike.data.Constants.ACTION_PING).apply {
                setPackage(TARGET_PKG)
            }
            context.sendBroadcast(pingIntent)
        } catch (e: Exception) {
            // Ignore
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
