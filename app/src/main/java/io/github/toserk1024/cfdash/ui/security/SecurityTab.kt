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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import io.github.toserk1024.cfdash.data.model.SecurityBreakdownItem
import io.github.toserk1024.cfdash.data.model.SecurityGroupBy
import io.github.toserk1024.cfdash.data.model.SecurityLogEntry
import io.github.toserk1024.cfdash.ui.stats.formatCount
import io.github.toserk1024.cfdash.ui.stats.formatPercent

/**
 * 安全分析 Tab：安全概况（回源/命中/缓解 100% 水平堆叠条）+ 24h 趋势折线（总请求/缓解）
 * + 分组视图分布 + 筛选器与安全事件日志。
 * 注：来源浏览器/来源操作系统分组已按用户确认删除（adaptive groups 无现成维度字段）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityTab(
    state: SecurityViewModel.SecurityUiState,
    onGroupBy: (SecurityGroupBy) -> Unit,
    onActionFilter: (String?) -> Unit,
    onSourceFilter: (String?) -> Unit,
    onCountryFilter: (String?) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit
) {
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            // 分组视图方式选择
            GroupBySelector(current = state.groupBy, onSelect = onGroupBy)

            Spacer(modifier = Modifier.height(16.dp))

            when {
                state.loading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                state.overview == null && state.error != null -> Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("⚠ ${state.error}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onRetry) { Text("重试") }
                }

                state.overview != null -> {
                    // 采样提示
                    Text(
                        text = "注：安全事件为采样数据，收窄时间范围可提升完整性",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // 安全概况：回源 / 命中 / 缓解
                    ChartCard("安全概况") {
                        HorizontalStackBar(overview = state.overview)
                    }

                    // 部分加载失败提示
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

                    // 24h 趋势
                    if (state.trend.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ChartCard("24 小时趋势（请求 / 缓解）") {
                            SecurityTrendChart(points = state.trend)
                            TrendLegend()
                        }
                    }

                    // 分组分布（groupBy != ALL）
                    if (state.groupBy != SecurityGroupBy.ALL) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ChartCard("${state.groupBy.label}分布") {
                            if (state.breakdownLoading && state.breakdown.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            } else if (state.breakdown.isEmpty()) {
                                Text("暂无数据", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                BreakdownList(state.breakdown)
                            }
                        }
                    }

                    // 日志筛选器 + 日志列表
                    Spacer(modifier = Modifier.height(12.dp))
                    ChartCard("安全事件日志") {
                        LogFilterRow(
                            actions = state.availableActions,
                            sources = state.availableSources,
                            countries = state.availableCountries,
                            actionFilter = state.actionFilter,
                            sourceFilter = state.sourceFilter,
                            countryFilter = state.countryFilter,
                            onAction = onActionFilter,
                            onSource = onSourceFilter,
                            onCountry = onCountryFilter
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (state.logs.isEmpty()) {
                            Text("暂无匹配日志", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            state.logs.forEach { log -> LogItem(log) }
                        }
                    }
                }
            }
        }
    }
}

/** 分组视图方式选择下拉 */
@Composable
private fun GroupBySelector(current: SecurityGroupBy, onSelect: (SecurityGroupBy) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
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

/** 日志筛选器行：操作 / 来源 / 国家 三个下拉 */
@Composable
private fun LogFilterRow(
    actions: List<String>,
    sources: List<String>,
    countries: List<String>,
    actionFilter: String?,
    sourceFilter: String?,
    countryFilter: String?,
    onAction: (String?) -> Unit,
    onSource: (String?) -> Unit,
    onCountry: (String?) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        FilterDropdown("操作", actionFilter, actions, onAction)
        Spacer(modifier = Modifier.height(8.dp))
        FilterDropdown("来源", sourceFilter, sources, onSource)
        Spacer(modifier = Modifier.height(8.dp))
        FilterDropdown("国家", countryFilter, countries, onCountry)
    }
}

@Composable
private fun FilterDropdown(label: String, current: String?, options: List<String>, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label：${current ?: "全部"}", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("全部") }, onClick = { onSelect(null); expanded = false })
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false })
            }
        }
    }
}

/** 分组分布列表（Top N，含占比） */
@Composable
private fun BreakdownList(items: List<SecurityBreakdownItem>) {
    val total = items.sumOf { it.count }
    Column(modifier = Modifier.fillMaxWidth()) {
        items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(text = formatCount(item.count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = if (total > 0) formatPercent(item.count.toFloat() / total.toFloat()) else "0%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
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

/** 操作 → 颜色映射（block 红 / challenge 橙 / allow 绿 / 其他灰） */
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