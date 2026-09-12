package com.yc.iqoolike.hook

import android.content.Context
import android.util.Log
import com.yc.iqoolike.data.Constants
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.io.File

/**
 * 系统签名校验绕过与防崩核心拦截器
 * 针对 iQOO 社区在被 NPatch 重签、二次打包或在虚拟机/多开环境中由于签名变化导致的启动闪退问题
 * 提供 5 重深层防御机制，且作为第 1 顺位在应用初始化最早期执行
 */
object SignatureBypassHook {
    const val TAG = "IQOOLike_Bypass"

    /**
     * 判断是否开启签名校验与防崩绕过
     * 默认返回 true（开箱即用，高可靠性）。仅当用户在伴侣设置中明确关闭时才跳过。
     */
    fun isBypassEnabled(): Boolean {
        return try {
            // 1. 尝试通过 XSharedPreferences 读取配置（LSPosed / EdXposed 跨进程支持）
            val xsp = XSharedPreferences(Constants.TARGET_MODULE_PKG, Constants.PREFS_NAME)
            xsp.makeWorldReadable()
            xsp.reload()
            if (!xsp.getBoolean(Constants.KEY_BYPASS_SIGNATURE, true)) {
                return false
            }

            // 2. 检查禁用标志文件（双保险判断）
            val internalFlag = File("/data/data/${Constants.TARGET_MODULE_PKG}/files/${Constants.FLAG_BYPASS_DISABLED}")
            if (internalFlag.exists()) return false

            val externalFlag = File("/sdcard/Android/data/${Constants.TARGET_MODULE_PKG}/files/${Constants.FLAG_BYPASS_DISABLED}")
            if (externalFlag.exists()) return false

            true
        } catch (t: Throwable) {
            // 发生任何跨 UID 权限限制或环境异常时，默认开启以保证不闪退
            true
        }
    }

    /**
     * 经典 Xposed API 挂钩实现（第 1 顺位同步执行）
     */
    fun applyClassic(classLoader: ClassLoader) {
        if (!isBypassEnabled()) {
            XposedBridge.log("$TAG: 签名校验绕过开关已关闭，跳过处理")
            return
        }

        XposedBridge.log("$TAG: === [第 1 顺位] 启动执行系统签名校验绕过与防崩挂钩 ===")

        // 防御 1: com.bbk.account.base.utils.AccountUtils.isSystemSign -> 恒返回 true
        try {
            XposedHelpers.findAndHookMethod(
                "com.bbk.account.base.utils.AccountUtils",
                classLoader,
                "isSystemSign",
                Context::class.java,
                String::class.java,
                XC_MethodReplacement.returnConstant(true)
            )
            XposedBridge.log("$TAG: ✓ [1/5] AccountUtils.isSystemSign -> 强制返回 true")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: ⚠️ [1/5] Hook isSystemSign 失败: ${t.message}")
        }

        // 防御 2: com.bbk.account.base.data.AccountAppPackageInfo.appIsSystemApp -> 恒返回 true
        try {
            XposedHelpers.findAndHookMethod(
                "com.bbk.account.base.data.AccountAppPackageInfo",
                classLoader,
                "appIsSystemApp",
                XC_MethodReplacement.returnConstant(true)
            )
            XposedBridge.log("$TAG: ✓ [2/5] AccountAppPackageInfo.appIsSystemApp -> 强制返回 true")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: ⚠️ [2/5] Hook appIsSystemApp 失败: ${t.message}")
        }

        // 防御 3: com.bbk.account.base.presenter.AccountProviderLoginPresenter.registerDbListener -> 置空拦截
        // 关键防护：阻断 registerContentObserver(content://com.bbk.account.accountinfo/accountinfo) 抛出的 SecurityException
        try {
            XposedHelpers.findAndHookMethod(
                "com.bbk.account.base.presenter.AccountProviderLoginPresenter",
                classLoader,
                "registerDbListener",
                XC_MethodReplacement.DO_NOTHING
            )
            XposedBridge.log("$TAG: ✓ [3/5] AccountProviderLoginPresenter.registerDbListener -> 拦截置空(彻底切断 SecurityException 闪退根因)")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: ⚠️ [3/5] Hook registerDbListener 失败: ${t.message}")
        }

        // 防御 4: com.bbk.account.base.utils.AccountUtils.isVivoPhone -> 恒返回 true
        // 解决在虚拟机、双开沙箱或非 vivo/iQOO 真机上出现的 AccountLoginErrorImp 拦截
        try {
            XposedHelpers.findAndHookMethod(
                "com.bbk.account.base.utils.AccountUtils",
                classLoader,
                "isVivoPhone",
                XC_MethodReplacement.returnConstant(true)
            )
            XposedBridge.log("$TAG: ✓ [4/5] AccountUtils.isVivoPhone -> 强制返回 true (完美兼容虚拟机/非vivo机型)")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: ⚠️ [4/5] Hook isVivoPhone 失败: ${t.message}")
        }

        // 防御 5: android.app.ApplicationPackageManager.checkSignatures -> 强制返回 0 (SIGNATURE_MATCH)
        try {
            val pmClass = XposedHelpers.findClass("android.app.ApplicationPackageManager", classLoader)
            XposedHelpers.findAndHookMethod(
                pmClass,
                "checkSignatures",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                XC_MethodReplacement.returnConstant(0)
            )
            XposedBridge.log("$TAG: ✓ [5/5] ApplicationPackageManager.checkSignatures(uid, uid) -> 强制返回 0 (MATCH)")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: ⚠️ [5/5] Hook checkSignatures 失败: ${t.message}")
        }

        XposedBridge.log("$TAG: === [第 1 顺位] 系统签名校验与防崩全部挂钩就绪 ===")
    }

    /**
     * 现代 libxposed API 挂钩实现（第 1 顺位同步执行）
     */
    fun applyModern(module: XposedModule, classLoader: ClassLoader) {
        if (!isBypassEnabled()) {
            Log.i(TAG, "签名校验绕过开关已关闭，跳过处理")
            return
        }

        Log.i(TAG, "=== [第 1 顺位] 现代 libxposed: 启动执行系统签名校验绕过与防崩挂钩 ===")

        // 防御 1: AccountUtils.isSystemSign
        try {
            val utilsClass = Class.forName("com.bbk.account.base.utils.AccountUtils", false, classLoader)
            val method = utilsClass.getDeclaredMethod("isSystemSign", Context::class.java, String::class.java)
            module.hook(method).intercept(Hooker { true })
            Log.i(TAG, "✓ [1/5] 现代 libxposed: AccountUtils.isSystemSign -> 强制返回 true")
        } catch (t: Throwable) {
            Log.w(TAG, "⚠️ [1/5] 现代 Hook isSystemSign 失败: ${t.message}")
        }

        // 防御 2: AccountAppPackageInfo.appIsSystemApp
        try {
            val pkgInfoClass = Class.forName("com.bbk.account.base.data.AccountAppPackageInfo", false, classLoader)
            val method = pkgInfoClass.getDeclaredMethod("appIsSystemApp")
            module.hook(method).intercept(Hooker { true })
            Log.i(TAG, "✓ [2/5] 现代 libxposed: AccountAppPackageInfo.appIsSystemApp -> 强制返回 true")
        } catch (t: Throwable) {
            Log.w(TAG, "⚠️ [2/5] 现代 Hook appIsSystemApp 失败: ${t.message}")
        }

        // 防御 3: AccountProviderLoginPresenter.registerDbListener
        try {
            val presenterClass = Class.forName("com.bbk.account.base.presenter.AccountProviderLoginPresenter", false, classLoader)
            val method = presenterClass.getDeclaredMethod("registerDbListener")
            module.hook(method).intercept(Hooker { null })
            Log.i(TAG, "✓ [3/5] 现代 libxposed: AccountProviderLoginPresenter.registerDbListener -> 拦截置空")
        } catch (t: Throwable) {
            Log.w(TAG, "⚠️ [3/5] 现代 Hook registerDbListener 失败: ${t.message}")
        }

        // 防御 4: AccountUtils.isVivoPhone
        try {
            val utilsClass = Class.forName("com.bbk.account.base.utils.AccountUtils", false, classLoader)
            val method = utilsClass.getDeclaredMethod("isVivoPhone")
            module.hook(method).intercept(Hooker { true })
            Log.i(TAG, "✓ [4/5] 现代 libxposed: AccountUtils.isVivoPhone -> 强制返回 true")
        } catch (t: Throwable) {
            Log.w(TAG, "⚠️ [4/5] 现代 Hook isVivoPhone 失败: ${t.message}")
        }

        Log.i(TAG, "=== [第 1 顺位] 现代 libxposed: 系统签名校验与防崩全部挂钩就绪 ===")
    }
}
