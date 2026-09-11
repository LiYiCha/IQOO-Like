package com.yc.iqoolike.hook

import android.app.Activity
import android.content.Context
import android.util.Log
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * 主动静默换票触发器
 * 反射调用 iQOO 社区内部账号换票入口
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
            // 尝试找静态单例字段 (如 INSTANCE, sInstance, etc.)
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
     * 主动触发换票
     */
    fun trigger(classLoader: ClassLoader): Boolean {
        try {
            Log.d(TAG, "开始执行主动静默换票流程...")
            val baMClass = Class.forName("ba.m", false, classLoader)
            val instance = getTargetInstance(baMClass)

            val activity = cachedActivity?.get()
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                // 方案 A: 拥有前台 Activity 实例，调用 ba.m.f(Activity, Callback)
                val methodF = baMClass.declaredMethods.firstOrNull {
                    it.name == "f" && it.parameterTypes.isNotEmpty() && Activity::class.java.isAssignableFrom(it.parameterTypes[0])
                }
                if (methodF != null) {
                    methodF.isAccessible = true
                    Log.d(TAG, "命中 ba.m.f(${methodF.parameterTypes.map { it.simpleName }.joinToString()})")
                    val args = arrayOfNulls<Any>(methodF.parameterTypes.size)
                    args[0] = activity
                    val target = if (Modifier.isStatic(methodF.modifiers)) null else instance
                    methodF.invoke(target, *args)
                    Log.i(TAG, "✓ 成功调用 ba.m.f 触发静默换票")
                    return true
                }
            }

            // 方案 B: 尝试调用 ba.m.b()
            val methodB = baMClass.declaredMethods.firstOrNull { it.name == "b" && it.parameterTypes.isEmpty() }
            if (methodB != null) {
                methodB.isAccessible = true
                val target = if (Modifier.isStatic(methodB.modifiers)) null else instance
                methodB.invoke(target)
                Log.i(TAG, "✓ 成功调用 ba.m.b() 触发换票")
                return true
            }

            // 方案 C: 扫描 ba.m 内其它候选方法
            val candidateMethod = baMClass.declaredMethods.firstOrNull { m ->
                m.parameterTypes.isEmpty() ||
                        (m.parameterTypes.size == 1 && Context::class.java.isAssignableFrom(m.parameterTypes[0]))
            }
            if (candidateMethod != null) {
                candidateMethod.isAccessible = true
                val target = if (Modifier.isStatic(candidateMethod.modifiers)) null else instance
                val ctx = cachedContext?.get() ?: cachedActivity?.get()
                if (candidateMethod.parameterTypes.isEmpty()) {
                    candidateMethod.invoke(target)
                } else if (ctx != null) {
                    candidateMethod.invoke(target, ctx)
                }
                Log.i(TAG, "✓ 成功调用候选方法 ba.m.${candidateMethod.name}")
                return true
            }

            Log.w(TAG, "未找到适用的 ba.m 换票入口方法")
            return false
        } catch (t: Throwable) {
            Log.e(TAG, "主动触发换票异常", t)
            return false
        }
    }
}
