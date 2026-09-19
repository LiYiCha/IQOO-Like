package com.yc.iqoolike.data

/**
 * 实时日志条目模型
 */
data class LogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: String = "INFO", // INFO, WARN, ERROR, DEBUG
    val tag: String = "IQOO",
    val message: String
)
