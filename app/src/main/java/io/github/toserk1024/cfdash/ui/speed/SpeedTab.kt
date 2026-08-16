package io.github.toserk1024.cfdash.ui.speed

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.toserk1024.cfdash.ui.zones.AdvancedSwitchRow

/** 速度页两个板块（分段按钮） */
enum class SpeedSection(val label: String) {
    PROTOCOL("协议优化"),
    CONTENT("内容优化")
}

/**
 * 速度优化 Tab：分段按钮在「协议优化 / 内容优化」间切换，开关沿用域名页高级设置样式。
 * 当前域名由全局域名选择器驱动（selectedZone 经 HomeScreen 同步到 SpeedViewModel）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedTab(
    state: SpeedViewModel.SpeedUiState,
    planName: String?,
    onSetSetting: (String, Boolean) -> Unit,
    onRetry: () -> Unit
) {
    var section by rememberSaveable { mutableStateOf(SpeedSection.PROTOCOL) }

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
        // Tab 选项卡：协议优化 / 内容优化
        PrimaryTabRow(selectedTabIndex = if (section == SpeedSection.PROTOCOL) 0 else 1) {
            Tab(selected = section == SpeedSection.PROTOCOL, onClick = { section = SpeedSection.PROTOCOL }, text = { Text("协议优化") })
            Tab(selected = section == SpeedSection.CONTENT, onClick = { section = SpeedSection.CONTENT }, text = { Text("内容优化") })
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = section.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                when (section) {
                    SpeedSection.PROTOCOL -> ProtocolContent(state, planName, onSetSetting)
                    SpeedSection.CONTENT -> ContentContent(state, onSetSetting)
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

@Composable
private fun ProtocolContent(state: SpeedViewModel.SpeedUiState, planName: String?, onSetSetting: (String, Boolean) -> Unit) {
    val isFree = planName?.equals("Free", ignoreCase = true) == true
    // HTTP/2：Free 计划始终开启不可关闭 → 副标题标注括号换行 + Free 下开关禁用
    AdvancedSwitchRow(
        title = "HTTP/2",
        subtitle = "多路复用、低延迟传输\n(Free 计划始终开启，不可关闭)",
        checked = state.value(SpeedViewModel.HTTP2) == true,
        enabled = !isFree && SpeedViewModel.HTTP2 !in state.settingsBusy && state.value(SpeedViewModel.HTTP2) != null,
        busy = SpeedViewModel.HTTP2 in state.settingsBusy,
        onCheckedChange = { onSetSetting(SpeedViewModel.HTTP2, it) }
    )
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

    val items = listOf(
        Triple(SpeedViewModel.HTTP2_ORIGIN, "HTTP/2 到源服务器", "源服务器连接也使用 HTTP/2，提升回源性能"),
        Triple(SpeedViewModel.HTTP3, "HTTP/3（使用 QUIC）", "基于 UDP 的更快、更稳的传输协议"),
        Triple(SpeedViewModel.ZERO_RTT, "0-RTT 连接恢复", "对已连接访客减少往返延迟（TLS 更快恢复）")
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
}

@Composable
private fun ContentContent(state: SpeedViewModel.SpeedUiState, onSetSetting: (String, Boolean) -> Unit) {
    val items = listOf(
        Triple(SpeedViewModel.SPEED_BRAIN, "Speed Brain", "通过预加载提升页面加载速度"),
        Triple(SpeedViewModel.FONTS, "Cloudflare Fonts", "在 Cloudflare 边缘优化网页字体加载"),
        Triple(SpeedViewModel.EARLY_HINTS, "Early Hints", "提前向浏览器发送资源加载提示，加速首屏"),
        Triple(SpeedViewModel.ROCKET_LOADER, "Rocket Loader™", "异步加载 JavaScript，提升渲染速度")
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
}