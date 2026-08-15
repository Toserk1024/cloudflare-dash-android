package io.github.toserk1024.cfdash.ui.zones

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.toserk1024.cfdash.data.model.ZonePlan

/**
 * 域名 Tab 内容：显示当前选中域名的详情界面（基本信息 / NS / 套餐 / 高级设置）。
 * 不含域名级统计（移入统计 Tab）与底部按钮（删除域名）。
 */
@Composable
fun ZoneDetailTab(
    state: ZoneViewModel.ZoneUiState,
    onSetDevMode: (Boolean) -> Unit,
    onSetUnderAttack: (Boolean) -> Unit,
    onSetIpv6: (Boolean) -> Unit,
    onRetrySettings: () -> Unit
) {
    val zone = state.selectedZone

    when {
        state.loading && state.zones.isEmpty() -> CenteredBox { CircularProgressIndicator() }
        zone == null -> CenteredBox {
            Text(
                text = "请先在右上角选择域名",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        state.detailLoading -> CenteredBox { CircularProgressIndicator() }
        state.detailError != null -> CenteredBox {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⚠ ${state.detailError}", color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = { /* 由 HomeScreen 处理重新选择 */ }) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("重试")
                }
            }
        }
        else -> Column(
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

                    AdvancedSwitchRow(
                        title = "开发模式",
                        subtitle = when {
                            state.devMode == null && state.settingsError == null -> "加载中…"
                            state.devMode == true -> "剩余时间：${formatRemaining(state.devModeRemaining)}"
                            else -> "开启后绕过CDN缓存"
                        },
                        checked = state.devMode == true,
                        enabled = "development_mode" !in state.settingsBusy && state.devMode != null,
                        busy = "development_mode" in state.settingsBusy,
                        onCheckedChange = onSetDevMode
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    AdvancedSwitchRow(
                        title = "五秒盾模式",
                        subtitle = "开启后所有访客需通过 5 秒安全挑战",
                        checked = state.underAttack == true,
                        enabled = "security_level" !in state.settingsBusy && state.underAttack != null,
                        busy = "security_level" in state.settingsBusy,
                        onCheckedChange = onSetUnderAttack
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    AdvancedSwitchRow(
                        title = "IPv6 兼容性",
                        subtitle = "自动处理 IPv6 流量",
                        checked = state.ipv6 == true,
                        enabled = "ipv6" !in state.settingsBusy && state.ipv6 != null,
                        busy = "ipv6" in state.settingsBusy,
                        onCheckedChange = onSetIpv6
                    )

                    state.settingsError?.let {
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

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        content()
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

private fun formatRemaining(seconds: Long): String {
    val totalMin = seconds / 60
    return when {
        totalMin >= 60 -> "${totalMin / 60}小时${totalMin % 60}分钟"
        totalMin > 0 -> "$totalMin 分钟"
        else -> "$seconds 秒"
    }
}