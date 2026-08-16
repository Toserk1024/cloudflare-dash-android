package io.github.toserk1024.cfdash.ui.network

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.toserk1024.cfdash.ui.zones.AdvancedSwitchRow

/**
 * 网络优化 Tab：开关沿用域名页高级设置样式。
 * 当前域名由全局域名选择器驱动；IPv6 行复用 ZoneViewModel 状态（与域名页高级设置共享，开关天然同步）。
 */
@Composable
fun NetworkTab(
    state: NetworkViewModel.NetworkUiState,
    ipv6: Boolean?,
    ipv6Busy: Boolean,
    onSetIpv6: (Boolean) -> Unit,
    onSetSetting: (String, Boolean) -> Unit,
    onRetry: () -> Unit
) {
    if (state.selectedZone == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Text(
                text = "请先在右上角选择域名",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "网络",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                // IPv6（复用域名页高级设置状态，两处开关保持同步）
                AdvancedSwitchRow(
                    title = "IPv6 兼容性",
                    subtitle = "自动处理 IPv6 流量",
                    checked = ipv6 == true,
                    enabled = !ipv6Busy && ipv6 != null,
                    busy = ipv6Busy,
                    onCheckedChange = onSetIpv6
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                val items = listOf(
                    Triple(NetworkViewModel.GRPC, "gRPC", "支持 gRPC 协议，通过 HTTP/2 传输"),
                    Triple(NetworkViewModel.WEBSOCKETS, "WebSockets", "支持 WebSocket 双向实时通信"),
                    Triple(NetworkViewModel.PSEUDO_IPV4, "Pseudo IPv4", "为仅支持 IPv4 的源服务器添加伪 IPv4 头部"),
                    Triple(NetworkViewModel.IP_GEOLOCATION, "IP 地理位置", "添加国家/地区代码请求头（Cf-IPCountry）"),
                    Triple(NetworkViewModel.NETWORK_ERROR_LOGGING, "网络错误记录", "通过 NEL 报告网络错误，提升可观测性"),
                    Triple(NetworkViewModel.ONION_ROUTING, "洋葱路由", "通过 Tor 网络直接提供服务（机会性洋葱路由）")
                )
                items.forEachIndexed { i, (setting, title, subtitle) ->
                    if (i > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    AdvancedSwitchRow(
                        title = title,
                        subtitle = subtitle,
                        checked = state.value(setting) == true,
                        enabled = setting !in state.settingsBusy && state.value(setting) != null,
                        busy = setting in state.settingsBusy,
                        onCheckedChange = { onSetSetting(setting, it) }
                    )
                }

                state.settingsError?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onRetry) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.height(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("重试", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}