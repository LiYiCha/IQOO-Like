package com.yc.iqoolike.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.yc.iqoolike.data.Constants
import com.yc.iqoolike.data.TokenModel
import java.io.File

/**
 * 运行在宿主 com.iqoo.bbs 进程内的广播接收器
 * 接收来自伴侣 App 的主动拉取指令与心跳探测
 */
class HookReceiver(private val classLoader: ClassLoader) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || context == null) return
        val action = intent.action
        Log.d(TAG, "宿主进程收到广播: $action")

        when (action) {
            Constants.ACTION_PING -> {
                Log.i(TAG, "宿主收到伴侣 App 心跳探测 PING，回复 PONG")
                com.yc.iqoolike.data.AppLogger.i(context, TAG, "收到伴侣心跳探测 PING，回复 PONG (PID: ${android.os.Process.myPid()})")
                sendPongBroadcast(context)
            }
            Constants.ACTION_PULL -> {
                val nonce = intent.getStringExtra("nonce") ?: ""
                Log.i(TAG, "收到模块主动拉取触发请求, nonce=$nonce")
                com.yc.iqoolike.data.AppLogger.i(context, TAG, "收到主动拉取触发请求 (nonce=$nonce)，启动凭证提取通道")

                // 1. 尝试从内部快照文件回传
                try {
                    val snapshotFile = File(context.filesDir, Constants.FILE_SNAPSHOT_NAME)
                    if (snapshotFile.exists()) {
                        val cachedJson = snapshotFile.readText()
                        val token = TokenModel.fromJson(cachedJson)
                        if (token != null) {
                            com.yc.iqoolike.data.AppLogger.i(context, TAG, "✓ 发现宿主内部快照文件，立即回传凭据 (userId=${token.userId})")
                            HookInterceptors.sendResultBroadcast(context, token.copy(source = "宿主快照"), nonce)
                        }
                    }
                } catch (e: Throwable) {
                    // Ignore
                }

                // 2. 触发系统底层静默换票 (会自动尝试读取 SpUserSettings)
                TokenTrigger.trigger(classLoader, context)
            }
        }
    }

    companion object {
        private const val TAG = "IQOO_HookReceiver"

        fun sendPongBroadcast(context: Context) {
            try {
                val pongIntent = Intent(Constants.ACTION_PONG).apply {
                    setPackage(Constants.TARGET_MODULE_PKG)
                    putExtra("isModuleActive", true)
                    putExtra("isTargetRunning", true)
                    putExtra("pid", android.os.Process.myPid())
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                }
                context.sendBroadcast(pongIntent)
                Log.i(TAG, "✓ 已向伴侣 App 回传心跳 PONG (PID: ${android.os.Process.myPid()})")
                com.yc.iqoolike.data.AppLogger.i(context, TAG, "✓ 已向伴侣 App 上报心跳 PONG (PID: ${android.os.Process.myPid()})")
            } catch (t: Throwable) {
                Log.e(TAG, "发送 PONG 广播异常", t)
                com.yc.iqoolike.data.AppLogger.e(context, TAG, "发送 PONG 广播异常", t)
            }
        }
    }
}
