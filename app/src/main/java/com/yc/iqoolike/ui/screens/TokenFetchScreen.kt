package com.yc.iqoolike.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yc.iqoolike.data.TokenModel
import com.yc.iqoolike.ui.theme.DarkSuccess
import com.yc.iqoolike.ui.theme.DarkWarning
import com.yc.iqoolike.ui.theme.LightSuccess
import com.yc.iqoolike.ui.theme.LightWarning
import com.yc.iqoolike.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenFetchScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val latestToken by viewModel.latestToken.collectAsState()
    val isFetching by viewModel.isFetching.collectAsState()
    val isModuleActive by viewModel.isModuleActive.collectAsState()
    val isTargetRunning by viewModel.isTargetRunning.collectAsState()
    val isTargetInstalled by viewModel.isTargetInstalled.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshEnvironmentStatus(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("iQOO Token", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = {
                        viewModel.refreshEnvironmentStatus(context)
                        viewModel.fetchToken(context)
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多选项")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("复制 JSON") },
                            onClick = {
                                showMenu = false
                                latestToken?.let { copyToClipboard(context, "Token JSON", it.toJson()) }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("复制 KEY=VALUE") },
                            onClick = {
                                showMenu = false
                                latestToken?.let { copyToClipboard(context, "Token Key-Value", it.toKeyValueString()) }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("仅复制 accessToken") },
                            onClick = {
                                showMenu = false
                                latestToken?.let { copyToClipboard(context, "accessToken", it.accessToken) }
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (!isTargetRunning) {
                        viewModel.launchTargetApp(context)
                    }
                    viewModel.fetchToken(context)
                },
                icon = {
                    if (isFetching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                },
                text = { Text(if (isFetching) "正在获取..." else "立即获取") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp),
                expanded = true
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 状态 Chip 行 (横向滑动)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusChip(
                    label = if (isModuleActive) "LSPosed 已激活" else "未激活 / 未生效",
                    isSuccess = isModuleActive
                )
                StatusChip(
                    label = if (isTargetInstalled) "com.iqoo.bbs 已安装" else "未安装目标应用",
                    isSuccess = isTargetInstalled
                )
                StatusChip(
                    label = if (isTargetRunning) "目标进程运行中" else "目标未运行(点击拉起)",
                    isSuccess = isTargetRunning,
                    onClick = { viewModel.launchTargetApp(context) }
                )
            }

            // 未激活提示横幅
            AnimatedVisibility(visible = !isModuleActive) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "⚠ 模块未在 LSPosed / Xposed 中生效",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "请在框架管理中勾选本模块，并将作用域添加到「iQOO 社区」，随后重启应用或软重启系统。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // 抓取状态提示
            AnimatedVisibility(visible = statusMessage.isNotEmpty()) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 主 Token 卡片
            val token = latestToken
            if (token != null && token.accessToken.isNotEmpty()) {
                MainTokenCard(token = token, context = context)

                // 2 列短字段网格
                Text(text = "账号与凭证信息", style = MaterialTheme.typography.titleSmall)
                ShortFieldsGrid(token = token, context = context)

                // 1 列长字段卡片
                Text(text = "系统授权与设备指纹", style = MaterialTheme.typography.titleSmall)
                LongFieldsList(token = token, context = context)

                // 底部快照来源及时间
                Text(
                    text = "快照时间: ${token.formattedTime} · 来源: ${token.source}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // 空状态
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("暂无 Token 凭证数据", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "确保手机已登录 Vivo 账号并打开 iQOO 社区，点击右下角「立即获取」即可主动拉取。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

@Composable
fun StatusChip(label: String, isSuccess: Boolean, onClick: (() -> Unit)? = null) {
    val isDark = isSystemInDarkTheme()
    val dotColor = if (isSuccess) {
        if (isDark) DarkSuccess else LightSuccess
    } else {
        if (isDark) DarkWarning else LightWarning
    }
    val containerColor = if (isSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = containerColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun MainTokenCard(token: TokenModel, context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "accessToken (社区核心凭证)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = { copyToClipboard(context, "accessToken", token.accessToken) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "复制 accessToken",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            var expanded by remember { mutableStateOf(false) }
            Text(
                text = token.accessToken,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
            )

            // 有效期与进度条
            val progress = token.progressRatio
            val isDark = isSystemInDarkTheme()
            val statusColor = when {
                token.isExpired -> MaterialTheme.colorScheme.error
                token.isExpiringSoon -> if (isDark) DarkWarning else LightWarning
                else -> if (isDark) DarkSuccess else LightSuccess
            }

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.primaryContainer
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when {
                        token.isExpired -> "已过期"
                        token.isExpiringSoon -> "即将过期 (剩余 ${token.remainingDays} 天)"
                        else -> "有效 (剩余 ${token.remainingDays} 天)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "总计 ${token.expiresIn / 86400} 天",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ShortFieldsGrid(token: TokenModel, context: Context) {
    val items = listOf(
        "userId" to token.userId.toString(),
        "expiresIn" to "${token.expiresIn}s",
        "nickname" to token.nickname.ifEmpty { "未获取" },
        "mobile" to token.mobile.ifEmpty { "未获取" },
        "versionCode" to token.versionCode.toString()
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in items.indices step 2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FieldItemBox(
                    modifier = Modifier.weight(1f),
                    label = items[i].first,
                    value = items[i].second,
                    onCopy = { copyToClipboard(context, items[i].first, items[i].second) }
                )
                if (i + 1 < items.size) {
                    FieldItemBox(
                        modifier = Modifier.weight(1f),
                        label = items[i + 1].first,
                        value = items[i + 1].second,
                        onCopy = { copyToClipboard(context, items[i + 1].first, items[i + 1].second) }
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun FieldItemBox(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    onCopy: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = value, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ContentCopy, contentDescription = "复制 $label", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun LongFieldsList(token: TokenModel, context: Context) {
    val items = listOf(
        "openid" to token.openid,
        "vivotoken" to token.vivotoken,
        "x-visitor" to token.xVisitor
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((label, value) in items) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            ) {
                var expanded by remember { mutableStateOf(false) }
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(
                            onClick = { copyToClipboard(context, label, value) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "复制 $label", modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(
                        text = value.ifEmpty { "空或尚未拦截" },
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                    )
                }
            }
        }
    }
}

fun copyToClipboard(context: Context, label: String, text: String) {
    if (text.isEmpty()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "已复制 $label", Toast.LENGTH_SHORT).show()
}
