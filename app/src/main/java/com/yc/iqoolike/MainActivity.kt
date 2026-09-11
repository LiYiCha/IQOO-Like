package com.yc.iqoolike

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.yc.iqoolike.hook.HookInterceptors
import com.yc.iqoolike.ui.screens.MainScreen
import com.yc.iqoolike.ui.theme.IQOOLikeTheme
import com.yc.iqoolike.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /**
     * 模块自检测方法（由 Hook 框架拦截替换为 true）
     */
    fun isModuleActive(): Boolean {
        return false
    }

    private val resultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == HookInterceptors.ACTION_RESULT) {
                val json = intent.getStringExtra("json")
                val sig = intent.getStringExtra("sig")
                val nonce = intent.getStringExtra("nonce")
                viewModel.onResultReceived(json, sig, nonce)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 注册回传结果接收器
        val filter = IntentFilter(HookInterceptors.ACTION_RESULT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(resultReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(resultReceiver, filter)
        }

        setContent {
            IQOOLikeTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setModuleActive(isModuleActive() || viewModel.isModuleActiveNative())
        viewModel.refreshEnvironmentStatus(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(resultReceiver)
        } catch (e: Exception) {
            // Ignore
        }
    }
}
