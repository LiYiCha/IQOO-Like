package com.yc.iqoolike.data

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 跨进程统一日志记录器
 * 支持将宿主进程与伴侣应用内部的运行状态、挂钩轨迹、参数与异常通过 IPC 实时传送至伴侣界面
 */
object AppLogger {
    private var inAppCallback: ((LogEntry) -> Unit)? = null

    fun registerInAppCallback(callback: (LogEntry) -> Unit) {
        inAppCallback = callback
    }

    fun log(context: Context?, level: String, tag: String, msg: String) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = msg
        )

        // 1. 输出至 Android 基础 Logcat
        when (level) {
            "ERROR" -> Log.e(tag, msg)
            "WARN" -> Log.w(tag, msg)
            else -> Log.i(tag, msg)
        }

        // 2. 若在伴侣应用主进程内，直接通过回调派发
        inAppCallback?.invoke(entry)

        // 3. 跨进程广播至伴侣应用
        if (context != null) {
            try {
                val intent = Intent(Constants.ACTION_LOG).apply {
                    setPackage(Constants.TARGET_MODULE_PKG)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtra("level", level)
                    putExtra("tag", tag)
                    putExtra("message", msg)
                    putExtra("time", entry.timestamp)
                }
                context.sendBroadcast(intent)
            } catch (t: Throwable) {
                // 忽略跨进程发送异常
            }
        }
    }

    fun i(context: Context?, tag: String, msg: String) = log(context, "INFO", tag, msg)
    fun w(context: Context?, tag: String, msg: String) = log(context, "WARN", tag, msg)
    fun e(context: Context?, tag: String, msg: String, tr: Throwable? = null) {
        val fullMsg = if (tr != null) "$msg\n${Log.getStackTraceString(tr)}" else msg
        log(context, "ERROR", tag, fullMsg)
    }
}
