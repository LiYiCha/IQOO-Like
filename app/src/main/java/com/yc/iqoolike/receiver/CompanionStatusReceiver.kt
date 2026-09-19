package com.yc.iqoolike.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.yc.iqoolike.IQOOApplication
import com.yc.iqoolike.data.Constants
import com.yc.iqoolike.data.SecurityUtil
import com.yc.iqoolike.data.TokenModel

/**
 * 伴侣 App 静态注册的全局广播接收器
 * 接收来自宿主 com.iqoo.bbs 进程的心跳 PONG 与结果 RESULT
 * 无论伴侣 App 是否在前台运行，均能持久化保存激活态与 Token 凭据
 */
class CompanionStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return
        Log.d(TAG, "收到全局广播: $action")

        val repository = try {
            IQOOApplication.instance.repository
        } catch (t: Throwable) {
            com.yc.iqoolike.data.TokenRepository.getInstance(context)
        }

        when (action) {
            Constants.ACTION_PONG -> {
                val pid = intent.getIntExtra("pid", 0)
                Log.i(TAG, "✓ 收到宿主进程心跳 PONG (PID: $pid)，持久化写入激活状态")
                repository.updateHeartbeat(pid)
            }
            Constants.ACTION_RESULT -> {
                val json = intent.getStringExtra("json")
                val sig = intent.getStringExtra("sig")
                val nonce = intent.getStringExtra("nonce")

                if (!json.isNullOrEmpty()) {
                    if (SecurityUtil.verifySignature(json, sig ?: "")) {
                        val token = TokenModel.fromJson(json)
                        if (token != null) {
                            Log.i(TAG, "✓ 收到有效凭证广播，持久化保存: userId=${token.userId}")
                            repository.saveToken(token)
                            repository.setModuleActive(true)
                        }
                    } else {
                        Log.w(TAG, "接收到的结果 HMAC 签名不匹配，丢弃")
                    }
                }
            }
            Constants.ACTION_LOG -> {
                val level = intent.getStringExtra("level") ?: "INFO"
                val tag = intent.getStringExtra("tag") ?: "IQOO"
                val message = intent.getStringExtra("message") ?: ""
                val time = intent.getLongExtra("time", System.currentTimeMillis())
                if (message.isNotEmpty()) {
                    val entry = com.yc.iqoolike.data.LogEntry(
                        timestamp = time,
                        level = level,
                        tag = tag,
                        message = message
                    )
                    repository.addLog(entry)
                }
            }
        }
    }

    companion object {
        private const val TAG = "CompanionStatusReceiver"
    }
}
