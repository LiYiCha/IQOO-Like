package com.yc.iqoolike.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.yc.iqoolike.data.TokenModel
import com.yc.iqoolike.data.TokenRepository
import java.io.File

/**
 * 运行在宿主 com.iqoo.bbs 进程内的广播接收器
 * 接收来自伴侣 App 的主动拉取指令
 */
class HookReceiver(private val classLoader: ClassLoader) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || context == null) return
        val action = intent.action
        Log.d(TAG, "宿主进程收到广播: $action")

        if (ACTION_PULL == action) {
            val nonce = intent.getStringExtra("nonce") ?: ""
            Log.i(TAG, "收到模块主动拉取触发请求, nonce=$nonce")

            // 1. 如果已有现成快照，先快速回传一份（让 UI 立即有显示）
            try {
                val snapshotFile = File(context.filesDir, TokenRepository.FILE_SNAPSHOT_NAME)
                if (snapshotFile.exists()) {
                    val cachedJson = snapshotFile.readText()
                    val token = TokenModel.fromJson(cachedJson)
                    if (token != null) {
                        HookInterceptors.sendResultBroadcast(context, token.copy(source = "宿主快照"), nonce)
                    }
                }
            } catch (e: Throwable) {
                // Ignore
            }

            // 2. 触发系统底层静默换票网络请求
            TokenTrigger.trigger(classLoader)
        }
    }

    companion object {
        private const val TAG = "IQOO_HookReceiver"
        const val ACTION_PULL = "iqoobbs.action.PULL"
    }
}
