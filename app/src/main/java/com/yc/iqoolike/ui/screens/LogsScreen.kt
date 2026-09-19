package com.yc.iqoolike.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yc.iqoolike.data.LogEntry
import com.yc.iqoolike.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(viewModel: MainViewModel) {
    val logs by viewModel.logs.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()

    var selectedLevel by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var autoScroll by remember { mutableStateOf(true) }

    val filteredLogs = remember(logs, selectedLevel, searchQuery) {
        logs.filter { entry ->
            val matchLevel = if (selectedLevel == "ALL") true else entry.level.equals(selectedLevel, ignoreCase = true)
            val matchQuery = if (searchQuery.isBlank()) true else {
                entry.message.contains(searchQuery, ignoreCase = true) || entry.tag.contains(searchQuery, ignoreCase = true)
            }
            matchLevel && matchQuery
        }
    }

    // 自动滚动至底部
    LaunchedEffect(filteredLogs.size) {
        if (autoScroll && filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "运行与挂钩日志",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${filteredLogs.size}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                },
                actions = {
                    // 自动滚动开关
                    IconButton(onClick = { autoScroll = !autoScroll }) {
                        Icon(
                            imageVector = if (autoScroll) Icons.Default.VerticalAlignBottom else Icons.Default.Pause,
                            contentDescription = if (autoScroll) "自动滚动开" else "自动滚动暂停",
                            tint = if (autoScroll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 复制全部日志
                    IconButton(
                        onClick = {
                            if (filteredLogs.isEmpty()) {
                                Toast.makeText(context, "暂无日志可复制", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            val sb = StringBuilder()
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                            filteredLogs.forEach {
                                sb.append("[${dateFormat.format(Date(it.timestamp))}] [${it.level}] [${it.tag}]: ${it.message}\n")
                            }
                            clipboardManager.setText(AnnotatedString(sb.toString()))
                            Toast.makeText(context, "已复制 ${filteredLogs.size} 条日志至剪贴板", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制全部")
                    }

                    // 清空日志
                    IconButton(onClick = { viewModel.clearLogs() }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "清空日志")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 筛选控制栏
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    // 搜索输入
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("搜索日志内容或标签...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "清除", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 级别筛选 FilterChips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("ALL" to "全部", "INFO" to "信息", "WARN" to "警告", "ERROR" to "错误").forEach { (key, label) ->
                            FilterChip(
                                selected = selectedLevel == key,
                                onClick = { selectedLevel = key },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }

            // 日志列表区域
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "暂无调试日志",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "当宿主 iQOO 社区启动或触发凭证提取时，挂钩事件与异常诊断将通过 IPC 实时显示在此处",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { logEntry ->
                        LogItemCard(
                            log = logEntry,
                            timeStr = dateFormat.format(Date(logEntry.timestamp)),
                            onCopy = {
                                clipboardManager.setText(AnnotatedString("[${logEntry.tag}] ${logEntry.message}"))
                                Toast.makeText(context, "已复制单条日志", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LogItemCard(
    log: LogEntry,
    timeStr: String,
    onCopy: () -> Unit
) {
    val levelColor = when (log.level.uppercase()) {
        "ERROR" -> Color(0xFFE53935)
        "WARN" -> Color(0xFFFB8C00)
        "DEBUG" -> Color(0xFF8E24AA)
        else -> Color(0xFF43A047)
    }

    val containerColor = when (log.level.uppercase()) {
        "ERROR" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCopy() },
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 级别胶囊
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = levelColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = log.level.uppercase(),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = levelColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Tag 标签
                Text(
                    text = log.tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.weight(1f))

                // 时间戳
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            // 消息正文 (等宽字体，高可读性)
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
