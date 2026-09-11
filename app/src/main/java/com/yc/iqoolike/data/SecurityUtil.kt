package com.yc.iqoolike.data

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 安全签名与 IPC 鉴权工具
 * 基于 iQOO 社区原生反编译 HMAC-SHA256 算法标准
 */
object SecurityUtil {

    /** 原生反编译密钥 (iqoo.txt L80) */
    const val DEFAULT_APP_SECRET = "1aa0fe59f218c000e3bd533c33e8f27a"
    const val APPID = "1001"

    /**
     * 计算 HMAC-SHA256 签名
     */
    fun hmacSha256(data: String, key: String = DEFAULT_APP_SECRET): String {
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            val secretKeySpec = SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
            mac.init(secretKeySpec)
            val bytes = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 生成随机 Nonce 防重放
     */
    fun generateNonce(): String {
        val random = SecureRandom()
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
    }

    /**
     * 校验 IPC 签名
     */
    fun verifySignature(data: String, signature: String, key: String = DEFAULT_APP_SECRET): Boolean {
        val expected = hmacSha256(data, key)
        return expected.isNotEmpty() && expected == signature
    }

    /**
     * 生成 iQOO 社区原生 API 请求头 Sign
     */
    fun generateIqooSignHeader(bodyJsonStr: String, requestTime: Long, appid: String = APPID, key: String = DEFAULT_APP_SECRET): String {
        val stringToSign = "POST&/api/v3/users/vivo/app&&$bodyJsonStr&appid=$appid&timestamp=$requestTime"
        val signature = hmacSha256(stringToSign, key)
        return "IQOO-HMAC-SHA256 appid=$appid,timestamp=$requestTime,signature=$signature"
    }
}
