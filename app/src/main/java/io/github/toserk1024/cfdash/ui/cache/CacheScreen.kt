package io.github.toserk1024.cfdash.ui.cache

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/** 缓存清除页：选择域名 + 清除方式 + 输入内容 + 执行清除 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheScreen(
    onBack: () -> Unit,
    viewModel: CacheViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("缓存清除") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 域名选择
            Text("选择域名", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = viewModel::showZonePicker,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = state.selectedZone?.name ?: "选择域名",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }

            Spacer(Modifier.height(20.dp))

            // 清除方式
            Text("清除方式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            PurgeMode.values().forEach { mode ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = state.mode == mode,
                        onClick = { viewModel.setMode(mode) }
                    )
                    Column(Modifier.weight(1f)) {
                        Text(mode.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            mode.hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 输入内容（"清除所有"无需输入）
            if (state.mode != PurgeMode.EVERYTHING) {
                Spacer(Modifier.height(16.dp))
                Text("要清除的内容", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.input,
                    onValueChange = viewModel::setInput,
                    placeholder = { Text(state.mode.placeholder) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(20.dp))

            // 清除按钮
            Button(
                onClick = viewModel::requestPurge,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.busy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("清除缓存")
                }
            }

            // 错误 / 结果提示
            state.error?.let {
                Spacer(Modifier.height(12.dp))
                Text("⚠ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            state.resultMessage?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it,
                    color = if (state.resultSuccess == true) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    // 域名选择对话框
    if (state.showZonePicker) {
        AlertDialog(
            onDismissRequest = viewModel::dismissZonePicker,
            title = { Text("选择域名") },
            text = {
                if (state.loadingZones) {
                    Box(
                        Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(modifier = Modifier.size(28.dp)) }
                } else if (state.zones.isEmpty()) {
                    Text("暂无域名，请先在“域名”页添加。")
                } else {
                    Column(Modifier.fillMaxWidth()) {
                        state.zones.forEach { zone ->
                            TextButton(
                                onClick = { viewModel.selectZone(zone) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(zone.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = viewModel::dismissZonePicker) { Text("关闭") }
            }
        )
    }

    // 清除确认对话框
    if (state.showConfirm) {
        val zoneName = state.selectedZone?.name ?: ""
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirm,
            title = { Text("确认清除缓存") },
            text = {
                Text(
                    buildString {
                        append("确定要清除域名 “$zoneName”的缓存吗？")
                        if (state.mode == PurgeMode.EVERYTHING) {
                            append("\n\n此操作将清除该域名下的全部缓存！")
                        } else {
                            append("\n\n方式：${state.mode.label}\n内容：\n${state.input.trim()}")
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::purge) {
                    Text("确定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConfirm) { Text("取消") }
            }
        )
    }
}