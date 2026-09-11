package com.yc.iqoolike.hook

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.yc.iqoolike.data.Constants
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.ref.WeakReference
import java.util.LinkedHashMap

/**
 * 现代 libxposed 入口（原生支持 API 100+，完美兼容现代 NPatch / LSPatch / LSPosed）
 */
class ModernHookEntry : XposedModule() {

    override fun onPackageReady(param: PackageReadyParam) {
        val packageName = param.packageName
        val classLoader = param.classLoader

        // 1. 伴侣模块自检测
        if (packageName == Constants.TARGET_MODULE_PKG) {
            try {
                val mainActClass = Class.forName("${Constants.TARGET_MODULE_PKG}.MainActivity", false, classLoader)
                val method = mainActClass.getDeclaredMethod("isModuleActive")
                hook(method).intercept(Hooker { true })
                Log.i(TAG, "✓ 现代 libxposed: 自激活已挂钩")
            } catch (t: Throwable) {
                Log.e(TAG, "自激活挂钩异常", t)
            }
            return
        }

        // 2. 目标 iQOO 社区进程
        if (packageName == Constants.TARGET_APP_PKG) {
            Log.i(TAG, "✓ 现代 libxposed: 正在挂钩目标包: $packageName")
            initModernHooks(classLoader)
        }
    }

    private fun initModernHooks(classLoader: ClassLoader) {
        try {
            // A. 精准挂钩 Activity.onCreate 缓存上下文并注册主动触发接收器
            val activityClass = Class.forName("android.app.Activity", false, classLoader)
            val onCreateMethod = activityClass.getDeclaredMethod("onCreate", Bundle::class.java)
            hook(onCreateMethod).intercept(Hooker { chain ->
                val result = chain.proceed()
                val activity = chain.thisObject as? Activity
                if (activity != null) {
                    TokenTrigger.cachedActivity = WeakReference(activity)
                    TokenTrigger.cachedContext = WeakReference(activity.applicationContext)
                    ClassicHookEntry.registerReceiverIfNeeded(activity, classLoader)
                }
                result
            })

            // B. 精准拦截点 1: pb.a.a(java.util.Map, boolean)
            // 入参为明文 Map，在执行 RSA 分块加密前截取
            try {
                val pbClass = Class.forName("pb.a", false, classLoader)
                val aMethod = pbClass.getDeclaredMethod("a", Map::class.java, Boolean::class.javaPrimitiveType)
                hook(aMethod).intercept(Hooker { chain ->
                    val arg0 = chain.getArg(0)
                    if (arg0 is Map<*, *> && arg0.containsKey("vivotoken")) {
                        // 立即深拷贝原始数据，不带出 Chain 引用
                        val copyMap = LinkedHashMap<Any?, Any?>(arg0)
                        HookInterceptors.onCapturePlainMap(copyMap, TokenTrigger.cachedContext?.get())
                    }
                    chain.proceed()
                })
                Log.i(TAG, "✓ 拦截点 1 (pb.a.a(Map, boolean)) 精准挂钩成功")
            } catch (t: Throwable) {
                Log.e(TAG, "拦截点 1 (pb.a.a) 挂钩失败", t)
            }

            // C. 精准拦截点 2: ba.n.m(yb.d)
            // 响应回调，参数为 exact 类型 yb.d，状态码为 0 时返回 TokenInfo
            try {
                val nClass = Class.forName("ba.n", false, classLoader)
                val dClass = Class.forName("yb.d", false, classLoader)
                val mMethod = nClass.getDeclaredMethod("m", dClass)
                hook(mMethod).intercept(Hooker { chain ->
                    val result = chain.proceed()
                    val wrapper = chain.getArg(0)
                    if (wrapper != null) {
                        HookInterceptors.onCaptureTokenResponse(wrapper, classLoader, TokenTrigger.cachedContext?.get())
                    }
                    result
                })
                Log.i(TAG, "✓ 拦截点 2 (ba.n.m(yb.d)) 精准挂钩成功")
            } catch (t: Throwable) {
                Log.e(TAG, "拦截点 2 (ba.n.m(yb.d)) 挂钩失败", t)
            }

        } catch (t: Throwable) {
            Log.e(TAG, "现代 libxposed 初始化异常", t)
        }
    }

    companion object {
        const val TAG = "IQOOLike_Modern"
    }
}
