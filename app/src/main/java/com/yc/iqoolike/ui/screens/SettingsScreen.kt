package com.yc.iqoolike.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yc.iqoolike.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isModuleActive by viewModel.isModuleActive.collectAsState()
    val isTargetRunning by viewModel.isTargetRunning.collectAsState()
    val isBypassSignatureEnabled by viewModel.isBypassSignatureEnabled.collectAsState()
    var autoSaveEnabled by remember { mutableStateOf(true) }
    var showRiskDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置与诊断", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 分组 0: 防崩与兼容配置（第 1 顺位执行）
            Text("防崩与兼容配置 (首顺位生效)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("绕过启动系统签名校验") },
                        supportingContent = { Text("首顺位强制模拟 vivo 系统签名并阻断 Observer 异常，解决 NPatch 重签 / 虚拟机启动闪退") },
                        trailingContent = {
                            Switch(
                                checked = isBypassSignatureEnabled,
                                onCheckedChange = { viewModel.setBypassSignatureEnabled(it) }
                            )
                        }
                    )
                }
            }

            // 分组 1: 抓取与存储配置
            Text("抓取配置", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("宿主本地快照持久化") },
                        supportingContent = { Text("拦截成功后在宿主 filesDir 写入 JSON 容灾副本") },
                        trailingContent = {
                            Switch(
                                checked = autoSaveEnabled,
                                onCheckedChange = { autoSaveEnabled = it }
                            )
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = { Text("广播超时时间") },
                        supportingContent = { Text("等待 iQOO 社区进程回传凭证的最长时间") },
                        trailingContent = { Text("15 秒", style = MaterialTheme.typography.labelLarge) }
                    )
                }
            }

            // 分组 2: 诊断与状态
            Text("环境与诊断", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("Hook 框架支持") },
                        supportingContent = { Text("现代 libxposed (API 100+) + 经典 API 82 双栈") },
                        trailingContent = {
                            Text(
                                text = if (isModuleActive) "✓ 已加载" else "未加载",
                                color = if (isModuleActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = { Text("目标应用 (com.iqoo.bbs)") },
                        supportingContent = { Text(if (isTargetRunning) "宿主进程存活中" else "宿主进程尚未运行") },
                        trailingContent = {
                            IconButton(onClick = { viewModel.launchTargetApp(context) }) {
                                Icon(Icons.Default.Launch, contentDescription = "拉起应用")
                            }
                        }
                    )
                }
            }

            // 分组 3: 关于
            Text("关于模块", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("版本号") },
                        trailingContent = { Text("1.0.0 (Build 20260911)") }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = { Text("安全与隐私说明") },
                        supportingContent = { Text("所有 Token 数据仅保存在设备本地，无远程回传") },
                        trailingContent = {
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        },
                        modifier = Modifier.clickable { showRiskDialog = true }
                    )
                }
            }
        }

        if (showRiskDialog) {
            AlertDialog(
                onDismissRequest = { showRiskDialog = false },
                icon = { Icon(Icons.Default.Security, contentDescription = null) },
                title = { Text("安全与机制说明") },
                text = {
                    Text(
                        "1. 本模块通过在宿主 com.iqoo.bbs 进程中拦截底层系统鉴权调用实现静默换票，无需用户在界面输入账号密码。\n\n" +
                        "2. 提取的所有凭证均通过显式广播直接在本地传递并存储于内部缓存，绝不包含任何第三方上报。\n\n" +
                        "3. 请妥善保管好提取出来的 accessToken 与 vivotoken，切勿泄漏给他人。"
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showRiskDialog = false }) {
                        Text("了解并关闭")
                    }
                }
            )
        }
    }
}
