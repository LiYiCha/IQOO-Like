package com.yc.iqoolike.hook

import android.app.Activity
import android.content.Context
import android.util.Log
import com.yc.iqoolike.data.AppLogger
import com.yc.iqoolike.data.TokenModel
import java.lang.ref.WeakReference
import java.lang.reflect.Modifier

/**
 * 主动静默换票触发器
 * 反射调用 iQOO 社区内部账号换票入口 (纯静默通道，绝不触发 UI 界面跳转)
 */
object TokenTrigger {

    private const val TAG = "IQOO_TokenTrigger"
    var cachedActivity: WeakReference<Activity>? = null
    var cachedContext: WeakReference<Context>? = null

    /**
     * 获取类的有效单例或默认实例
     */
    private fun getTargetInstance(clazz: Class<*>): Any? {
        return try {
            val singletonField = clazz.declaredFields.firstOrNull {
                Modifier.isStatic(it.modifiers) && it.type == clazz
            }
            if (singletonField != null) {
                singletonField.isAccessible = true
                singletonField.get(null)
            } else {
                val constructor = clazz.declaredConstructors.firstOrNull { it.parameterTypes.isEmpty() }
                constructor?.isAccessible = true
                constructor?.newInstance()
            }
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * 直接从宿主 SpUserSettings 提取已存 Token
     */
    fun extractCachedSnapshot(classLoader: ClassLoader, context: Context?): TokenModel? {
        val safeCtx = context ?: cachedContext?.get() ?: cachedActivity?.get()
        return try {
            val spCClass = Class.forName("com.leaf.data_safe_save.sp.c", false, classLoader)
            val hMethod = spCClass.getDeclaredMethod("h")
            val spUserSettings = hMethod.invoke(null) ?: return null

            val mMethod = spUserSettings.javaClass.getDeclaredMethod("m")
            val tokenInfo = mMethod.invoke(spUserSettings) ?: return null

            val getAccessToken = tokenInfo.javaClass.getDeclaredMethod("getAccessToken")
            val accessToken = getAccessToken.invoke(tokenInfo) as? String ?: ""
            if (accessToken.isEmpty()) return null

            val getUserId = tokenInfo.javaClass.getDeclaredMethod("getUserId")
            val userId = (getUserId.invoke(tokenInfo) as? Number)?.toLong() ?: 0L

            val getExpiresIn = tokenInfo.javaClass.getDeclaredMethod("getExpiresIn")
            val expiresIn = (getExpiresIn.invoke(tokenInfo) as? Number)?.toLong() ?: 2592000L

            var openid = ""
            try {
                val f9055dField = spUserSettings.javaClass.getDeclaredField("f9055d")
                f9055dField.isAccessible = true
                openid = f9055dField.get(null) as? String ?: ""
            } catch (e: Throwable) {
                // Ignore
            }
            if (openid.isEmpty()) {
                try {
                    val iMethod = spUserSettings.javaClass.getMethod("i", String::class.java)
                    openid = iMethod.invoke(spUserSettings, "key_openid") as? String ?: ""
                } catch (e: Throwable) {
                    // Ignore
                }
            }

            val tokenModel = TokenModel(
                accessToken = accessToken,
                userId = userId,
                expiresIn = expiresIn,
                vivotoken = "",
                openid = openid,
                nickname = "",
                mobile = "",
                versionCode = 0,
                xVisitor = "",
                timestamp = System.currentTimeMillis() / 1000,
                source = "宿主本地缓存"
            )
            AppLogger.i(safeCtx, TAG, "✓ 成功从宿主本地缓存读取 Token: userId=$userId, token=${accessToken.take(8)}...")
            tokenModel
        } catch (t: Throwable) {
            AppLogger.w(safeCtx, TAG, "读取宿主内部已存 Token 异常: ${t.message}")
            null
        }
    }

    /**
     * 主动触发换票 (纯静默双通道)
     */
    fun trigger(classLoader: ClassLoader, context: Context? = null): Boolean {
        val safeCtx = context ?: cachedContext?.get() ?: cachedActivity?.get()
        try {
            AppLogger.i(safeCtx, TAG, "开始执行主动静默换票流程...")

            // 1. 如果宿主本地已有有效 Token 快照，立即秒级回传
            val cachedToken = extractCachedSnapshot(classLoader, safeCtx)
            if (cachedToken != null && safeCtx != null) {
                AppLogger.i(safeCtx, TAG, "✓ 发现宿主本地已存有效 Token，立即秒级回传伴侣！")
                HookInterceptors.sendResultBroadcast(safeCtx, cachedToken)
            }

            // 2. 静默触发底层网络换票流程
            val baMClass = Class.forName("ba.m", false, classLoader)
            val instance = getTargetInstance(baMClass)

            // 优先通道 1: 调用零参静默入口 ba.m.c()
            val methodC = baMClass.declaredMethods.firstOrNull { it.name == "c" && it.parameterTypes.isEmpty() }
            if (methodC != null) {
                methodC.isAccessible = true
                val target = if (Modifier.isStatic(methodC.modifiers)) null else instance
                methodC.invoke(target)
                AppLogger.i(safeCtx, TAG, "✓ 成功调用 ba.m.c() 触发静默检测/换票")
                return true
            }

            // 优先通道 2: 调用静默自动登录 ba.m.b(null)
            val methodB = baMClass.declaredMethods.firstOrNull { it.name == "b" && it.parameterTypes.size == 1 }
            if (methodB != null) {
                methodB.isAccessible = true
                val target = if (Modifier.isStatic(methodB.modifiers)) null else instance
                methodB.invoke(target, null)
                AppLogger.i(safeCtx, TAG, "✓ 成功调用 ba.m.b(null) 触发自动登录换票")
                return true
            }

            AppLogger.w(safeCtx, TAG, "未找到适用的静默换票入口")
            return false
        } catch (t: Throwable) {
            AppLogger.e(safeCtx, TAG, "主动静默换票异常", t)
            return false
        }
    }
}
