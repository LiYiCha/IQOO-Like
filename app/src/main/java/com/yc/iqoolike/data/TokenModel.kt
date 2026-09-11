package com.yc.iqoolike.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * iQOO 社区全量凭证数据模型（9项完整参数）
 */
data class TokenModel(
    @SerializedName("id")
    val id: String = UUID.randomUUID().toString(),

    @SerializedName("accessToken")
    val accessToken: String = "",

    @SerializedName("userId")
    val userId: Long = 0L,

    @SerializedName("expiresIn")
    val expiresIn: Long = 2592000L, // 默认 30 天

    @SerializedName("vivotoken")
    val vivotoken: String = "",

    @SerializedName("openid")
    val openid: String = "",

    @SerializedName("nickname")
    val nickname: String = "",

    @SerializedName("mobile")
    val mobile: String = "",

    @SerializedName("versionCode")
    val versionCode: Int = 0,

    @SerializedName("xVisitor")
    val xVisitor: String = "",

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis() / 1000,

    @SerializedName("source")
    val source: String = "广播" // "广播" 或 "快照"
) {
    /** 剩余秒数 */
    val remainingSeconds: Long
        get() {
            val elapsed = (System.currentTimeMillis() / 1000) - timestamp
            return (expiresIn - elapsed).coerceAtLeast(0)
        }

    /** 剩余天数 */
    val remainingDays: Long
        get() = remainingSeconds / 86400

    /** 是否已过期 */
    val isExpired: Boolean
        get() = remainingSeconds <= 0

    /** 是否临期（<= 7 天） */
    val isExpiringSoon: Boolean
        get() = !isExpired && remainingDays <= 7

    /** 剩余进度比例 (0.0 ~ 1.0) */
    val progressRatio: Float
        get() = if (expiresIn > 0) (remainingSeconds.toFloat() / expiresIn.toFloat()).coerceIn(0f, 1f) else 0f

    /** 格式化日期 */
    val formattedTime: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp * 1000))

    val shortTime: String
        get() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp * 1000))

    /** 转为 KEY=VALUE 格式 */
    fun toKeyValueString(): String {
        return buildString {
            appendLine("accessToken=$accessToken")
            appendLine("userId=$userId")
            appendLine("expiresIn=$expiresIn")
            appendLine("openid=$openid")
            appendLine("vivotoken=$vivotoken")
            appendLine("nickname=$nickname")
            appendLine("mobile=$mobile")
            appendLine("versionCode=$versionCode")
            appendLine("x-visitor=$xVisitor")
            appendLine("timestamp=$timestamp")
        }
    }

    /** 转为 JSON 格式 */
    fun toJson(): String {
        return Gson().toJson(this)
    }

    companion object {
        fun fromJson(json: String): TokenModel? {
            return try {
                Gson().fromJson(json, TokenModel::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}
