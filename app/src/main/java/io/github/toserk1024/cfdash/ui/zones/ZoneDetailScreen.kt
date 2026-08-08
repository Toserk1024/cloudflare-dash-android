package io.github.toserk1024.cfdash.ui.zones

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.toserk1024.cfdash.data.model.Zone
import io.github.toserk1024.cfdash.data.model.ZonePlan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneDetailScreen(
    zoneId: String,
    zoneName: String?,
    onBack: () -> Unit,
    onManageDns: (String, String) -> Unit,
    onDeleted: () -> Unit,
    viewModel: ZoneDetailViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.deleted) {
        if (state.deleted) onDeleted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(zoneName ?: "域名详情", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.zone != null -> ZoneDetailContent(
                    zone = state.zone!!,
                    deleting = state.deleting,
                    devMode = state.devMode,
                    devModeRemaining = state.devModeRemaining,
                    underAttack = state.underAttack,
                    ipv6 = state.ipv6,
                    settingsBusy = state.settingsBusy,
                    settingsError = state.settingsError,
                    onManageDns = { onManageDns(state.zone!!.id, state.zone!!.name) },
                    onDelete = viewModel::deleteZone,
                    onSetDevMode = viewModel::setDevelopmentMode,
                    onSetUnderAttack = viewModel::setUnderAttack,
                    onSetIpv6 = viewModel::setIpv6,
                    onRetrySettings = viewModel::refreshSettings
                )
                else -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("⚠ ${state.error ?: "加载失败"}", textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = viewModel::load) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("重试")
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneDetailContent(
    zone: Zone,
    deleting: Boolean,
    devMode: Boolean?,
    devModeRemaining: Long,
    underAttack: Boolean?,
    ipv6: Boolean?,
    settingsBusy: String?,
    settingsError: String?,
    onManageDns: () -> Unit,
    onDelete: () -> Unit,
    onSetDevMode: (Boolean) -> Unit,
    onSetUnderAttack: (Boolean) -> Unit,
    onSetIpv6: (Boolean) -> Unit,
    onRetrySettings: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 基本信息卡片
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = zone.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    StatusBadge(zone.status)
                }
                Spacer(modifier = Modifier.height(8.dp))
                InfoRow("Zone ID", zone.id)
                InfoRow("类型", zone.type)
                InfoRow("暂停状态", if (zone.paused) "已暂停" else "运行中")
                InfoRow("开发模式", if (zone.development_mode > 0) "已开启" else "关闭")
                zone.original_registrar?.let { InfoRow("注册商", it) }
                zone.created_on?.let { InfoRow("创建时间", it.replace("T", " ").substringBefore(".")) }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // NS 服务器卡片
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Name Servers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                zone.name_servers.forEach { ns ->
                    Text(text = ns, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                }
                if (zone.name_servers.isEmpty()) {
                    Text("暂无 NS 记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "请确保在域名注册商处将 NS 设置为以上服务器以激活 Cloudflare。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 套餐卡片
        zone.plan?.takeIf { it.name.isNotBlank() }?.let { plan ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("套餐", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    PlanRow(plan)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 高级设置卡片
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("高级", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))

                // 开发模式
                AdvancedSwitchRow(
                    title = "开发模式",
                    subtitle = when {
                        devMode == null && settingsError == null -> "加载中…"
                        devMode == true -> "剩余时间：${formatRemaining(devModeRemaining)}"
                        else -> "开启后绕过CDN缓存"
                    },
                    checked = devMode == true,
                    enabled = settingsBusy == null && devMode != null,
                    busy = settingsBusy == "development_mode",
                    onCheckedChange = onSetDevMode
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // 五秒盾模式
                AdvancedSwitchRow(
                    title = "五秒盾模式",
                    subtitle = "开启后所有访客需通过 5 秒安全挑战",
                    checked = underAttack == true,
                    enabled = settingsBusy == null && underAttack != null,
                    busy = settingsBusy == "security_level",
                    onCheckedChange = onSetUnderAttack
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // IPv6 兼容性
                AdvancedSwitchRow(
                    title = "IPv6 兼容性",
                    subtitle = "自动处理 IPv6 流量",
                    checked = ipv6 == true,
                    enabled = settingsBusy == null && ipv6 != null,
                    busy = settingsBusy == "ipv6",
                    onCheckedChange = onSetIpv6
                )

                settingsError?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onRetrySettings) {
                            Text("重试", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 操作按钮
        Button(
            onClick = onManageDns,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("管理 DNS 记录", fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { showDeleteConfirm = true },
            enabled = !deleting,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            if (deleting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("删除域名", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // 删除确认对话框
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除域名") },
            text = { Text("确定要删除域名 ${zone.name} 吗？\n\n此操作不可恢复，域名下的所有 DNS 记录将被删除！") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PlanRow(plan: ZonePlan) {
    InfoRow("名称", plan.name)
    InfoRow("价格", if (plan.price > 0) "${plan.currency} ${plan.price}" else "免费")
    InfoRow("周期", plan.frequency)
    InfoRow("订阅状态", if (plan.is_subscribed) "已订阅" else "未订阅")
}

/** 高级设置开关行（标题 + 副标题 + Switch / 切换中 loading） */
@Composable
private fun AdvancedSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    busy: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}

/** 秒数格式化为"X小时Y分钟/Z分钟" */
private fun formatRemaining(seconds: Long): String {
    val totalMin = seconds / 60
    return when {
        totalMin >= 60 -> "${totalMin / 60}小时${totalMin % 60}分钟"
        totalMin > 0 -> "$totalMin 分钟"
        else -> "$seconds 秒"
    }
}