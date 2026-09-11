package com.yc.iqoolike.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yc.iqoolike.data.TokenModel
import com.yc.iqoolike.ui.theme.LightSuccess
import com.yc.iqoolike.ui.theme.LightWarning
import com.yc.iqoolike.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val historyList by viewModel.historyList.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }
    var selectedItemForDetail by remember { mutableStateOf<TokenModel?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("历史凭证记录", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    if (historyList.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "清空历史")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (historyList.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "暂无历史抓取记录",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(historyList, key = { it.id }) { item ->
                        HistoryCard(
                            item = item,
                            context = context,
                            onItemClick = { selectedItemForDetail = item },
                            onDelete = { viewModel.removeHistoryItem(item) }
                        )
                    }
                }
            }
        }

        // 清空确认弹窗
        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text("清空历史记录") },
                text = { Text("确定要删除本地保存的所有历史 Token 凭证吗？") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.clearHistory()
                        showClearConfirm = false
                    }) {
                        Text("确定清空", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) {
                        Text("取消")
                    }
                }
            )
        }

        // 详情弹窗
        selectedItemForDetail?.let { item ->
            AlertDialog(
                onDismissRequest = { selectedItemForDetail = null },
                title = { Text("凭证详情 (UID: ${item.userId})") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("捕获时间: ${item.formattedTime}", style = MaterialTheme.typography.bodySmall)
                        Text("昵称: ${item.nickname}", style = MaterialTheme.typography.bodySmall)
                        Text("手机号: ${item.mobile}", style = MaterialTheme.typography.bodySmall)
                        Text("剩余时间: ${item.remainingDays} 天 (${item.remainingSeconds}s)", style = MaterialTheme.typography.bodySmall)
                        Text("accessToken:", style = MaterialTheme.typography.labelMedium)
                        Text(
                            item.accessToken,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        copyToClipboard(context, "全量 JSON", item.toJson())
                        selectedItemForDetail = null
                    }) {
                        Text("复制 JSON")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedItemForDetail = null }) {
                        Text("关闭")
                    }
                }
            )
        }
    }
}

@Composable
fun HistoryCard(
    item: TokenModel,
    context: Context,
    onItemClick: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = when {
        item.isExpired -> MaterialTheme.colorScheme.error
        item.isExpiringSoon -> LightWarning
        else -> LightSuccess
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onItemClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "UID: ${item.userId}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            item.isExpired -> "已过期"
                            item.isExpiringSoon -> "剩 ${item.remainingDays} 天"
                            else -> "剩 ${item.remainingDays} 天"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = item.shortTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.accessToken,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = { copyToClipboard(context, "accessToken", item.accessToken) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "复制", modifier = Modifier.size(18.dp))
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
