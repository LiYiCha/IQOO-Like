package com.yc.iqoolike.hook

import android.content.Context
import android.content.Intent
import android.util.Log
import com.yc.iqoolike.data.SecurityUtil
import com.yc.iqoolike.data.TokenModel
import com.yc.iqoolike.data.TokenRepository
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Hook 拦截数据处理器
 * 负责组装明文参数与响应 Token，并完成持久化、日志输出与 IPC 广播回传
 */
object HookInterceptors {

    private const val TAG = "IQOO_HookInterceptors"
    const val LOGCAT_TOKEN_TAG = "IQOO_TOKEN"
    const val MODULE_PKG = "com.yc.iqoolike"
    const val ACTION_RESULT = "iqoobbs.action.RESULT"

    // 内存暂存明文 Map 参数
    private val cachedParams = ConcurrentHashMap<String, Any>()

    /**
     * 处理拦截点 1: pb.a.a(Map, boolean)
     * 捕获 RSA 加密前的明文 Map
     */
    fun onCapturePlainMap(rawMap: Map<*, *>, context: Context?) {
        try {
            Log.d(TAG, "捕获到加密前明文 Map: keys = ${rawMap.keys}")
            for ((k, v) in rawMap) {
                if (k != null && v != null) {
                    cachedParams[k.toString()] = v
                }
            }

            Log.i(TAG, "✓ 成功提取明文参数: vivotoken=${cachedParams["vivotoken"]}, openid=${cachedParams["openid"]}")
        } catch (t: Throwable) {
            Log.e(TAG, "处理明文 Map 异常", t)
        }
    }

    /**
     * 处理拦截点 2: ba.n.m(yb.d)
     * 捕获服务端返回的 Token 响应
     */
    fun onCaptureTokenResponse(wrapper: Any, classLoader: ClassLoader, context: Context?) {
        try {
            Log.d(TAG, "开始解析响应回调数据包...")
            val field = wrapper.javaClass.declaredFields.firstOrNull { it.name == "f18790a" } ?: wrapper.javaClass.declaredFields.firstOrNull()
            if (field == null) {
                Log.w(TAG, "未找到响应体字段 f18790a")
                return
            }
            field.isAccessible = true
            val body = field.get(wrapper) ?: return

            // 反射调用 rb.m.a(body) 判定状态码
            val rbmClass = Class.forName("rb.m", false, classLoader)
            val methodA = rbmClass.declaredMethods.firstOrNull { it.name == "a" && it.parameterTypes.size == 1 }
            val code = methodA?.invoke(null, body) as? Int ?: -1

            Log.d(TAG, "响应状态码 code = $code")
            if (code == 0) {
                // 反射调用 rb.m.b(body) 获取 TokenInfo 实例
                val methodB = rbmClass.declaredMethods.firstOrNull { it.name == "b" && it.parameterTypes.size == 1 }
                val tokenInfo = methodB?.invoke(null, body) ?: return

                val tokenInfoClass = tokenInfo.javaClass
                val getAccessToken = tokenInfoClass.getMethod("getAccessToken")
                val getUserId = tokenInfoClass.getMethod("getUserId")
                val getExpiresIn = tokenInfoClass.getMethod("getExpiresIn")

                val accessToken = getAccessToken.invoke(tokenInfo) as? String ?: ""
                val userId = (getUserId.invoke(tokenInfo) as? Number)?.toLong() ?: 0L
                val expiresIn = (getExpiresIn.invoke(tokenInfo) as? Number)?.toLong() ?: 2592000L

                // 尝试提取 x-visitor
                val xVisitor = fetchXVisitor(classLoader, context)

                // 组装全量数据包
                val tokenModel = TokenModel(
                    accessToken = accessToken,
                    userId = userId,
                    expiresIn = expiresIn,
                    vivotoken = cachedParams["vivotoken"]?.toString() ?: "",
                    openid = cachedParams["openid"]?.toString() ?: "",
                    nickname = cachedParams["nickname"]?.toString() ?: "",
                    mobile = cachedParams["mobile"]?.toString() ?: "",
                    versionCode = (cachedParams["versionCode"] as? Number)?.toInt() ?: 0,
                    xVisitor = xVisitor,
                    timestamp = System.currentTimeMillis() / 1000,
                    source = "Hook实时捕获"
                )

                // 核心：在 Logcat 打印全量凭据 JSON，方便免 Root / ADB / Shizuku 快速读取
                Log.i(LOGCAT_TOKEN_TAG, "==================== iQOO TOKEN START ====================")
                Log.i(LOGCAT_TOKEN_TAG, tokenModel.toJson())
                Log.i(LOGCAT_TOKEN_TAG, "==================== iQOO TOKEN END ======================")

                // 1. 本地双重容灾落盘 (内部目录 + 外部沙盒目录，方便无 Root 查看)
                val safeContext = context ?: TokenTrigger.cachedContext?.get() ?: TokenTrigger.cachedActivity?.get()
                if (safeContext != null) {
                    saveLocalSnapshot(safeContext, tokenModel)
                    // 2. 显式广播回传给伴侣 App
                    sendResultBroadcast(safeContext, tokenModel)
                }
            } else {
                Log.w(TAG, "换票响应失败，状态码非0: $code")
                com.yc.iqoolike.data.AppLogger.w(context, TAG, "换票响应失败，服务端状态码非0: $code")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "处理 Token 响应异常", t)
            com.yc.iqoolike.data.AppLogger.e(context, TAG, "处理 Token 响应异常", t)
        }
    }

    /**
     * 获取设备指纹 x-visitor
     */
    private fun fetchXVisitor(classLoader: ClassLoader, context: Context?): String {
        return try {
            val rboClass = Class.forName("rb.o", false, classLoader)
            val aMethod = rboClass.getDeclaredMethod("a")
            aMethod.isAccessible = true
            val oInstance = aMethod.invoke(null)
            val fField = oInstance?.javaClass?.declaredFields?.firstOrNull { it.name == "f15782a" }
            fField?.isAccessible = true
            fField?.get(oInstance)?.toString() ?: ""
        } catch (e: Throwable) {
            ""
        }
    }

    /**
     * 写入宿主 filesDir 和 外部沙盒目录，方便无 Root 用户读取
     */
    private fun saveLocalSnapshot(context: Context, token: TokenModel) {
        val json = token.toJson()

        // 内部存储: /data/user/0/com.iqoo.bbs/files/iqoo_token.json
        try {
            val internalFile = File(context.filesDir, TokenRepository.FILE_SNAPSHOT_NAME)
            internalFile.writeText(json)
            Log.i(TAG, "✓ 内部快照已写入: ${internalFile.absolutePath}")
            com.yc.iqoolike.data.AppLogger.i(context, TAG, "✓ 内部快照已写入: ${internalFile.absolutePath}")
        } catch (e: Throwable) {
            Log.e(TAG, "写入内部快照失败", e)
            com.yc.iqoolike.data.AppLogger.e(context, TAG, "写入内部快照失败", e)
        }

        // 外部沙盒: /sdcard/Android/data/com.iqoo.bbs/files/iqoo_token.json (免Root文件管理器可读)
        try {
            val externalDir = context.getExternalFilesDir(null)
            if (externalDir != null) {
                val externalFile = File(externalDir, TokenRepository.FILE_SNAPSHOT_NAME)
                externalFile.writeText(json)
                Log.i(TAG, "✓ 免Root沙盒快照已写入: ${externalFile.absolutePath}")
            }
        } catch (e: Throwable) {
            // Ignore
        }
    }

    /**
     * 显式广播回传模块 App
     */
    fun sendResultBroadcast(context: Context, token: TokenModel, nonce: String = "") {
        try {
            val json = token.toJson()
            val sig = SecurityUtil.hmacSha256(json)

            val intent = Intent(ACTION_RESULT).apply {
                setPackage(MODULE_PKG)
                putExtra("json", json)
                putExtra("sig", sig)
                if (nonce.isNotEmpty()) {
                    putExtra("nonce", nonce)
                }
            }
            context.sendBroadcast(intent)
            Log.i(TAG, "✓ 已向 $MODULE_PKG 发送结果广播")
            com.yc.iqoolike.data.AppLogger.i(context, TAG, "✓ 成功回传 Token 凭据广播给伴侣 (userId=${token.userId}, 来源=${token.source})")
        } catch (e: Throwable) {
            Log.e(TAG, "发送结果广播失败", e)
            com.yc.iqoolike.data.AppLogger.e(context, TAG, "发送结果广播失败", e)
        }
    }
}
