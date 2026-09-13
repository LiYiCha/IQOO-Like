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

        // ★★★ 第 1 顺位：首先执行系统签名校验绕过与防崩挂钩（在 ContentProvider 与 Application 启动前生效）★★★
        SignatureBypassHook.applyClassic(cl)

        // A0. 拦截 Application.onCreate 最早期初始化上下文并广播存活心跳
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application",
                cl,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val app = param.thisObject as? android.app.Application ?: return
                        TokenTrigger.cachedContext = WeakReference(app.applicationContext)
                        registerReceiverIfNeeded(app, cl)
                        HookReceiver.sendPongBroadcast(app.applicationContext)
                        XposedBridge.log("$TAG: ✓ Application.onCreate 启动就绪，已发送激活存活心跳")
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Hook Application.onCreate 失败: ${t.message}")
        }

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
                        HookReceiver.sendPongBroadcast(activity.applicationContext)
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Hook Activity.onCreate 失败: ${t.message}")
        }

        // B0. 精准请求入口: ba.m.a(ContextWrapper, AccountInfo, e, f)
        try {
            val accountInfoClass = XposedHelpers.findClass("com.leaf.account.AccountInfo", cl)
            val eClass = XposedHelpers.findClass("ba.e", cl)
            val fClass = XposedHelpers.findClass("ba.f", cl)
            XposedHelpers.findAndHookMethod(
                "ba.m",
                cl,
                "a",
                android.content.ContextWrapper::class.java,
                accountInfoClass,
                eClass,
                fClass,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val accountInfo = param.args[1] ?: return
                        val openid = XposedHelpers.callMethod(accountInfo, "getOpenid") as? String ?: ""
                        val username = XposedHelpers.callMethod(accountInfo, "getUsername") as? String ?: ""
                        val phonenum = XposedHelpers.callMethod(accountInfo, "getPhonenum") as? String ?: ""
                        val vivotoken = XposedHelpers.callMethod(accountInfo, "getVivotoken") as? String ?: ""
                        val map = LinkedHashMap<String, Any>()
                        map["openid"] = openid
                        map["nickname"] = username
                        map["mobile"] = phonenum
                        map["vivotoken"] = vivotoken
                        HookInterceptors.onCapturePlainMap(map, TokenTrigger.cachedContext?.get())
                        XposedBridge.log("$TAG: ✓ 精准请求入口 ba.m.a 成功截取 AccountInfo: openid=$openid")
                    }
                }
            )
            XposedBridge.log("$TAG: ✓ 精准请求入口 1 (ba.m.a) 挂钩就绪")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: 挂钩 ba.m.a 异常: ${t.message}")
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
                val filter = IntentFilter().apply {
                    addAction(Constants.ACTION_PULL)
                    addAction(Constants.ACTION_PING)
                }
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
                Log.i(TAG, "✓ 动态注册主动触发与心跳接收器成功")
                // 立即主动向伴侣 App 上报一次存活与激活心跳
                HookReceiver.sendPongBroadcast(context.applicationContext)
            } catch (t: Throwable) {
                Log.e(TAG, "动态注册广播接收器异常", t)
            }
        }
    }
}
