package io.github.toserk1024.cfdash.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.toserk1024.cfdash.data.model.SecurityFilter
import io.github.toserk1024.cfdash.data.model.SecurityFilterAttr
import io.github.toserk1024.cfdash.data.model.SecurityFilterOp
import io.github.toserk1024.cfdash.data.model.SecurityGroupBy
import io.github.toserk1024.cfdash.data.model.SecurityLogEntry
import io.github.toserk1024.cfdash.data.model.SecurityTimeRange

/**
 * 安全分析 Tab：分组视图（Dropdown）+ 时间范围（分段按钮）+ 全局筛选器（新建/删除）+ 概况堆叠条 + 趋势折线 + 日志。
 * 分组视图联动概况与趋势；时间范围作用于概况/趋势/日志/分组；筛选器全局 AND 叠加。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityTab(
    state: SecurityViewModel.SecurityUiState,
    onGroupBy: (SecurityGroupBy) -> Unit,
    onTimeRange: (SecurityTimeRange) -> Unit,
    onAddFilter: (SecurityFilter) -> Unit,
    onRemoveFilter: (Int) -> Unit,
    onClearFilters: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit
) {
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            ControlCard(
                groupBy = state.groupBy,
                timeRange = state.timeRange,
                onGroupBy = onGroupBy,
                onTimeRange = onTimeRange
            )

            Spacer(modifier = Modifier.height(12.dp))

            FilterPanel(
                filters = state.filters,
                onAddFilter = onAddFilter,
                onRemoveFilter = onRemoveFilter,
                onClearFilters = onClearFilters
            )

            Spacer(modifier = Modifier.height(12.dp))

            when {
                state.loading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                state.overview.isEmpty() && state.error != null -> Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("⚠ ${state.error}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onRetry) { Text("重试") }
                }

                else -> {
                    Text(
                        text = "注：安全事件为采样数据，时间范围最大 24 小时",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    ChartCard(if (state.groupBy == SecurityGroupBy.ALL) "安全概况" else "${state.groupBy.label}占比") {
                        HorizontalStackBar(segments = state.overview, groupByAll = state.groupBy == SecurityGroupBy.ALL)
                    }

                    if (!state.partError.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "⚠ 部分数据加载失败：${state.partError}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onRetry) { Text("重试", style = MaterialTheme.typography.bodySmall) }
                        }
                    }

                    if (state.trend.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ChartCard("趋势（${state.timeRange.label}）") {
                            SecurityTrendChart(series = state.trend)
                            TrendLegend(series = state.trend)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    ChartCard("安全事件日志（${state.timeRange.label}）") {
                        if (state.allLogs.isEmpty()) {
                            Text("暂无匹配日志", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            state.allLogs.forEach { log -> LogItem(log) }
                        }
                    }
                }
            }
        }
    }
}

/** 顶部控制卡：分组视图 + 时间范围 */
@Composable
private fun ControlCard(
    groupBy: SecurityGroupBy,
    timeRange: SecurityTimeRange,
    onGroupBy: (SecurityGroupBy) -> Unit,
    onTimeRange: (SecurityTimeRange) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 分组视图下拉
            GroupBySelector(current = groupBy, onSelect = onGroupBy)
            Spacer(modifier = Modifier.height(12.dp))
            // 时间范围分段按钮
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SecurityTimeRange.entries.forEachIndexed { index, r ->
                    SegmentedButton(
                        selected = timeRange == r,
                        onClick = { onTimeRange(r) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = SecurityTimeRange.entries.size)
                    ) { Text(r.label, maxLines = 1) }
                }
            }
        }
    }
}

/** 分组视图选择下拉 */
@Composable
private fun GroupBySelector(current: SecurityGroupBy, onSelect: (SecurityGroupBy) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("分组视图：${current.label}", modifier = Modifier.weight(1f), maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SecurityGroupBy.entries.forEach { gb ->
                DropdownMenuItem(
                    text = { Text(gb.label) },
                    onClick = { onSelect(gb); expanded = false }
                )
            }
        }
    }
}

/** 筛选器面板：新建筛选器 + 已应用筛选器 Chips */
@Composable
private fun FilterPanel(
    filters: List<SecurityFilter>,
    onAddFilter: (SecurityFilter) -> Unit,
    onRemoveFilter: (Int) -> Unit,
    onClearFilters: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "筛选器", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (filters.isNotEmpty()) {
                    TextButton(onClick = onClearFilters) { Text("清除全部") }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // 新建筛选器编辑行：属性 + 条件 + 值 + 添加
            var attr by remember { mutableStateOf(SecurityFilterAttr.IP) }
            var op by remember { mutableStateOf(SecurityFilterOp.EQ) }
            var value by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterDropdown(current = attr.label, options = SecurityFilterAttr.entries.map { it.label }) { sel ->
                    SecurityFilterAttr.entries.firstOrNull { it.label == sel }?.let { attr = it }
                }
                Spacer(modifier = Modifier.width(8.dp))
                FilterDropdown(current = op.label, options = SecurityFilterOp.entries.map { it.label }) { sel ->
                    SecurityFilterOp.entries.firstOrNull { it.label == sel }?.let { op = it }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入值，如 192.168.0.1") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = {
                        onAddFilter(SecurityFilter(attr, op, value))
                        value = ""
                    },
                    enabled = value.isNotBlank()
                ) { Icon(Icons.Default.Add, contentDescription = "添加筛选器") }
            }

            // 已应用筛选器 Chips（带删除叉）
            if (filters.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                filters.forEachIndexed { index, f ->
                    FilterChip(
                        selected = true,
                        onClick = { onRemoveFilter(index) },
                        label = { Text("${f.attr.label} ${f.op.label} ${f.value}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "移除", modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/** 下拉选择器（属性/条件通用） */
@Composable
private fun FilterDropdown(current: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(current, maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { onSelect(opt); expanded = false }
                )
            }
        }
    }
}

/** 单条安全日志 */
@Composable
private fun LogItem(log: SecurityLogEntry) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = log.action.ifBlank { "unknown" },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(actionColor(log.action))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = log.datetime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (!log.source.isNullOrBlank()) {
                    Text(text = log.source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = listOfNotNull(log.host, log.clientIP).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = listOfNotNull(
                    log.clientCountry?.let { "国家 $it" },
                    log.deviceType?.let { "设备 $it" },
                    log.httpVersion?.let { "HTTP $it" },
                    log.cacheStatus?.let { "缓存 $it" }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 操作 → 颜色映射 */
private fun actionColor(action: String): Color = when (action) {
    "block" -> Color(0xFFEB5757)
    "challenge", "jschallenge", "managedchallenge", "connectionclose",
    "managedchallengenoninteractivesolved", "managedchallengeinteractivesolved",
    "precursorinterstitialpageissued" -> Color(0xFFF2994A)
    "allow", "log" -> Color(0xFF27AE60)
    else -> Color(0xFF9E9E9E)
}

/** 卡片容器 */
@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}