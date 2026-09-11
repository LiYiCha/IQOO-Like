package com.yc.iqoolike.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yc.iqoolike.ui.viewmodel.MainViewModel

enum class NavigationTab(val title: String) {
    FETCH("获取"),
    HISTORY("历史"),
    SETTINGS("设置")
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    var currentTab by remember { mutableStateOf(NavigationTab.FETCH) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier.height(80.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = currentTab == NavigationTab.FETCH,
                    onClick = { currentTab = NavigationTab.FETCH },
                    icon = { Icon(Icons.Default.Download, contentDescription = "获取") },
                    label = { Text("获取") }
                )
                NavigationBarItem(
                    selected = currentTab == NavigationTab.HISTORY,
                    onClick = { currentTab = NavigationTab.HISTORY },
                    icon = { Icon(Icons.Default.History, contentDescription = "历史") },
                    label = { Text("历史") }
                )
                NavigationBarItem(
                    selected = currentTab == NavigationTab.SETTINGS,
                    onClick = { currentTab = NavigationTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "设置") },
                    label = { Text("设置") }
                )
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.surface
        ) {
            when (currentTab) {
                NavigationTab.FETCH -> TokenFetchScreen(viewModel = viewModel)
                NavigationTab.HISTORY -> HistoryScreen(viewModel = viewModel)
                NavigationTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
