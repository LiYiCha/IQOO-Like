package com.yc.iqoolike.hook

import android.app.Activity
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.yc.iqoolike.data.Constants
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.ref.WeakReference
import java.util.LinkedHashMap

/**
 * 经典 Xposed 入口（精确挂钩 pb.a.a(Map, boolean) 与 ba.n.m(yb.d)）
 */
class ClassicHookEntry : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        val packageName = lpparam.packageName

        // 1. 伴侣模块自激活检测
        if (packageName == Constants.TARGET_MODULE_PKG) {
            try {
                XposedHelpers.findAndHookMethod(
                    "${Constants.TARGET_MODULE_PKG}.MainActivity",
                    lpparam.classLoader,
                    "isModuleActive",
                    object : XC_MethodReplacement() {
                        override fun replaceHookedMethod(param: MethodHookParam): Any = true
                    }
                )
            } catch (t: Throwable) {
                // Ignore
            }
            return
        }

        // 2. 目标 iQOO 社区进程
        if (packageName != Constants.TARGET_APP_PKG) return

        XposedBridge.log("$TAG: 命中 iQOO 社区进程: ${lpparam.processName}")
        val cl = lpparam.classLoader

        // A. 拦截 Activity.onCreate 缓存 Context 并注册广播
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Activity",
                cl,
                "onCreate",
                Bundle::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as? Activity ?: return
                        TokenTrigger.cachedActivity = WeakReference(activity)
                        TokenTrigger.cachedContext = WeakReference(activity.applicationContext)
                        registerReceiverIfNeeded(activity, cl)
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Hook Activity.onCreate 失败: ${t.message}")
        }

        // B. 精准拦截点 1: pb.a.a(Map, boolean)
        try {
            XposedHelpers.findAndHookMethod(
                "pb.a",
                cl,
                "a",
                Map::class.java,
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val arg0 = param.args[0] as? Map<*, *> ?: return
                        if (arg0.containsKey("vivotoken")) {
                            val copyMap = LinkedHashMap<Any?, Any?>(arg0)
                            HookInterceptors.onCapturePlainMap(copyMap, TokenTrigger.cachedContext?.get())
                        }
                    }
                }
            )
            XposedBridge.log("$TAG: ✓ 精准拦截点 1 (pb.a.a) 挂钩就绪")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: 精准拦截点 1 (pb.a.a) 挂钩失败: ${t.message}")
        }

        // C. 精准拦截点 2: ba.n.m(yb.d)
        try {
            val dClass = XposedHelpers.findClass("yb.d", cl)
            XposedHelpers.findAndHookMethod(
                "ba.n",
                cl,
                "m",
                dClass,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val wrapper = param.args[0] ?: return
                        val context = TokenTrigger.cachedContext?.get()
                        HookInterceptors.onCaptureTokenResponse(wrapper, cl, context)

                        // 宿主界面 Toast 直观反馈
                        if (context != null) {
                            Handler(Looper.getMainLooper()).post {
                                try {
                                    Toast.makeText(context, "🎉 iQOO Token 捕获成功！", Toast.LENGTH_LONG).show()
                                } catch (e: Throwable) {
                                    // Ignore
                                }
                            }
                        }
                    }
                }
            )
            XposedBridge.log("$TAG: ✓ 精准拦截点 2 (ba.n.m(yb.d)) 挂钩就绪")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: 精准拦截点 2 (ba.n.m) 挂钩失败: ${t.message}")
        }
    }

    companion object {
        const val TAG = "IQOOLike_Classic"
        private var isReceiverRegistered = false

        @Synchronized
        fun registerReceiverIfNeeded(context: Context, classLoader: ClassLoader) {
            if (isReceiverRegistered) return
            try {
                val receiver = HookReceiver(classLoader)
                val filter = IntentFilter(Constants.ACTION_PULL)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.applicationContext.registerReceiver(
                        receiver,
                        filter,
                        Context.RECEIVER_EXPORTED
                    )
                } else {
                    context.applicationContext.registerReceiver(receiver, filter)
                }
                isReceiverRegistered = true
                Log.i(TAG, "✓ 动态注册主动触发接收器成功 (ACTION_PULL)")
            } catch (t: Throwable) {
                Log.e(TAG, "动态注册广播接收器异常", t)
            }
        }
    }
}
