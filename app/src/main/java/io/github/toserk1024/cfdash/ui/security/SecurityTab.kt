package io.github.toserk1024.cfdash.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.toserk1024.cfdash.data.model.CountryMapping
import io.github.toserk1024.cfdash.data.model.FilterValueKind
import io.github.toserk1024.cfdash.data.model.SecurityFilter
import io.github.toserk1024.cfdash.data.model.SecurityFilterAttr
import io.github.toserk1024.cfdash.data.model.SecurityFilterOp
import io.github.toserk1024.cfdash.data.model.SecurityGroupBy
import io.github.toserk1024.cfdash.data.model.SecurityLogColumn
import io.github.toserk1024.cfdash.data.model.SecurityLogEntry
import io.github.toserk1024.cfdash.data.model.SecurityTimeRange
import io.github.toserk1024.cfdash.ui.security.SecurityViewModel.SecuritySection
import io.github.toserk1024.cfdash.ui.security.SecurityViewModel.SecurityUiState

/**
 * 安全页（重构版）：流量 / 事件 双子页。
 *
 * 流量页 = 概况（三段或分组占比）+ 趋势 + Top 统计；事件页 = 筛选器 + 日志表（每次 10 条，滚动自动续加载）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityTab(
    state: SecurityUiState,
    onSection: (SecuritySection) -> Unit,
    onGroupBy: (SecurityGroupBy) -> Unit,
    onTimeRange: (SecurityTimeRange) -> Unit,
    onEventTimeRange: (SecurityTimeRange) -> Unit,
    onAddTrafficFilter: (SecurityFilter) -> Unit,
    onRemoveTrafficFilter: (Int) -> Unit,
    onClearTrafficFilters: () -> Unit,
    onAddEventFilter: (SecurityFilter) -> Unit,
    onRemoveEventFilter: (Int) -> Unit,
    onClearEventFilters: () -> Unit,
    onToggleLogColumn: (SecurityLogColumn) -> Unit,
    onLoadMoreLogs: () -> Unit,
    onRefresh: () -> Unit
) {
    var showFilterSheet by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // 子页切换
        TabRow(selectedTabIndex = if (state.section == SecuritySection.TRAFFIC) 0 else 1) {
            Tab(
                selected = state.section == SecuritySection.TRAFFIC,
                onClick = { onSection(SecuritySection.TRAFFIC) },
                text = { Text("流量") }
            )
            Tab(
                selected = state.section == SecuritySection.EVENTS,
                onClick = { onSection(SecuritySection.EVENTS) },
                text = { Text("事件") }
            )
        }

        // 工具条：筛选器 / 时间 / 列 / 刷新
        val filters = if (state.section == SecuritySection.TRAFFIC) state.trafficFilters else state.eventFilters
        val timeRange = if (state.section == SecuritySection.TRAFFIC) state.timeRange else state.eventTimeRange
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { showFilterSheet = true }) {
                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (filters.isEmpty()) "筛选器" else "筛选器 ${filters.size}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TimeRangeMenu(current = timeRange) {
                if (state.section == SecuritySection.TRAFFIC) onTimeRange(it) else onEventTimeRange(it)
            }
            Spacer(modifier = Modifier.weight(1f))
            if (state.section == SecuritySection.EVENTS) {
                LogColumnMenu(selected = state.selectedLogColumns, onToggle = onToggleLogColumn)
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新", modifier = Modifier.size(18.dp))
            }
        }
        HorizontalDivider()

        when (state.section) {
            SecuritySection.TRAFFIC -> TrafficPage(
                state = state,
                onGroupBy = onGroupBy,
                onRemoveFilter = onRemoveTrafficFilter,
                onClearFilters = onClearTrafficFilters
            )
            SecuritySection.EVENTS -> EventsPage(
                state = state,
                onRemoveFilter = onRemoveEventFilter,
                onClearFilters = onClearEventFilters,
                onLoadMoreLogs = onLoadMoreLogs
            )
        }
    }

    if (showFilterSheet) {
        val attrs = if (state.section == SecuritySection.TRAFFIC) SecurityFilterAttr.TRAFFIC else SecurityFilterAttr.EVENT
        FilterSheet(
            attrs = attrs,
            onDismiss = { showFilterSheet = false },
            onAdd = { filter ->
                if (state.section == SecuritySection.TRAFFIC) onAddTrafficFilter(filter) else onAddEventFilter(filter)
                showFilterSheet = false
            }
        )
    }
}

// ===== 流量页 =====

@Composable
private fun TrafficPage(
    state: SecurityUiState,
    onGroupBy: (SecurityGroupBy) -> Unit,
    onRemoveFilter: (Int) -> Unit,
    onClearFilters: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FilterChipsRow(state.trafficFilters, onRemoveFilter, onClearFilters)

        state.trafficError?.let { err ->
            ErrorCard(err)
            return@Column
        }
        if (state.trafficLoading && state.overview.total == 0L) {
            LoadingBlock()
            return@Column
        }

        // 概况
        val segments = if (state.groupBy == SecurityGroupBy.ALL) state.overview.segments else state.groupSegments
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = if (state.groupBy == SecurityGroupBy.ALL) "安全概况" else "按${state.groupBy.label}占比",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "共 ${formatSecurityCount(state.overview.total)} 个请求 · ${state.timeRange.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                SecurityOverviewBar(segments = segments, segmented = state.groupBy == SecurityGroupBy.ALL)
            }
        }

        // 趋势
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "请求趋势",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    GroupByMenu(current = state.groupBy, onSelect = onGroupBy)
                }
                Spacer(modifier = Modifier.height(6.dp))
                SecurityTrendChart(
                    series = state.trend,
                    segmented = state.groupBy == SecurityGroupBy.ALL
                )
            }
        }

        // Top 统计
        val top = state.topStats
        if (top.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                top.getOrNull(0)?.let { SecurityTopStatCard(it.title, it.items, Modifier.weight(1f)) }
                top.getOrNull(1)?.let { SecurityTopStatCard(it.title, it.items, Modifier.weight(1f)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                top.getOrNull(2)?.let { SecurityTopStatCard(it.title, it.items, Modifier.weight(1f)) }
                top.getOrNull(3)?.let { SecurityTopStatCard(it.title, it.items, Modifier.weight(1f)) }
            }
        }

        state.partError?.let {
            Text(
                text = "⚠ $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ===== 事件页 =====

@Composable
private fun EventsPage(
    state: SecurityUiState,
    onRemoveFilter: (Int) -> Unit,
    onClearFilters: () -> Unit,
    onLoadMoreLogs: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            FilterChipsRow(state.eventFilters, onRemoveFilter, onClearFilters)
        }
        val err = state.eventsError
        when {
            err != null -> ErrorCard(err)
            state.eventsLoading && state.logs.isEmpty() -> LoadingBlock()
            state.logs.isEmpty() -> EmptySecurityChart("暂无事件日志")
            else -> {
                val visible = state.logs.take(state.logVisibleCount)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(visible) { _, log ->
                        LogRow(log, state.selectedLogColumns)
                    }
                    if (state.loadingMoreLogs) {
                        item { LoadingBlock() }
                    } else if (state.logHasMore) {
                        item {
                            LaunchedEffect(state.logVisibleCount) { onLoadMoreLogs() }
                            Box(modifier = Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                                Text("加载更多…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(log: SecurityLogEntry, columns: Set<SecurityLogColumn>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = log.datetime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = log.action,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                if (SecurityLogColumn.SERVICE in columns && !log.source.isNullOrBlank()) {
                    Text(
                        text = log.source.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (SecurityLogColumn.RULE_ID in columns && !log.ruleId.isNullOrBlank()) {
                Text(
                    text = "规则：${log.ruleId}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (SecurityLogColumn.IP in columns && !log.clientIP.isNullOrBlank()) {
                    Text(log.clientIP.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (SecurityLogColumn.COUNTRY in columns && !log.clientCountry.isNullOrBlank()) {
                    Text(
                        text = CountryMapping.codeToName(log.clientCountry.orEmpty()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (SecurityLogColumn.HOST in columns && !log.host.isNullOrBlank()) {
                    Text(
                        text = log.host.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (SecurityLogColumn.PATH in columns && !log.path.isNullOrBlank()) {
                Text(
                    text = (log.path ?: "") + (log.query?.takeIf { it.isNotBlank() }?.let { "?$it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ===== 筛选器 =====

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    attrs: List<SecurityFilterAttr>,
    onDismiss: () -> Unit,
    onAdd: (SecurityFilter) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var attr by remember { mutableStateOf(attrs.first()) }
    var op by remember { mutableStateOf(SecurityFilterOp.EQ) }
    var selected by remember(attr) { mutableStateOf<Set<String>>(emptySet()) }
    var text by remember(attr) { mutableStateOf("") }
    var countryQuery by remember { mutableStateOf("") }
    var attrMenu by remember { mutableStateOf(false) }

    val ops = remember(attr) {
        if (attr.allowContains) SecurityFilterOp.entries.toList()
        else listOf(SecurityFilterOp.EQ, SecurityFilterOp.NEQ)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("添加筛选器", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            // 属性选择
            Box {
                OutlinedTextField(
                    value = attr.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("属性") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = { attrMenu = true }) { Text("切换") }
                    }
                )
                DropdownMenu(expanded = attrMenu, onDismissRequest = { attrMenu = false }) {
                    attrs.forEach { a ->
                        DropdownMenuItem(
                            text = { Text(a.label) },
                            onClick = { attr = a; attrMenu = false }
                        )
                    }
                }
            }

            // 操作符
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ops.forEach { o ->
                    FilterChipLike(text = o.label, selected = op == o) { op = o }
                }
            }

            // 值控件
            when (attr.valueKind) {
                FilterValueKind.CHOICE -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    attr.candidates.forEach { c ->
                        FilterChipLike(text = c, selected = c in selected) {
                            selected = if (c in selected) selected - c else selected + c
                        }
                    }
                }
                FilterValueKind.COUNTRY -> {
                    OutlinedTextField(
                        value = countryQuery,
                        onValueChange = { countryQuery = it },
                        label = { Text("搜索国家") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CountryMapping.search(countryQuery, 12).forEach { (code, name) ->
                            FilterChipLike(text = "$name ($code)", selected = code in selected) {
                                selected = if (code in selected) selected - code else selected + code
                            }
                        }
                    }
                }
                FilterValueKind.TEXT -> OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("值") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        val values = when (attr.valueKind) {
                            FilterValueKind.TEXT -> listOf(text.trim())
                            else -> selected.toList()
                        }.filter { it.isNotBlank() }
                        if (values.isNotEmpty()) onAdd(SecurityFilter(attr, op, values))
                    },
                    enabled = when (attr.valueKind) {
                        FilterValueKind.TEXT -> text.isNotBlank()
                        else -> selected.isNotEmpty()
                    }
                ) { Text("添加") }
            }
        }
    }
}

@Composable
private fun FilterChipLike(text: String, selected: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(text, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
        } else null
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChipsRow(
    filters: List<SecurityFilter>,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit
) {
    if (filters.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            filters.forEachIndexed { index, f ->
                AssistChip(
                    onClick = { onRemove(index) },
                    label = {
                        Text(
                            text = "${f.attr.label} ${f.op.label} ${f.values.joinToString("、")}",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    trailingIcon = {
                        Icon(Icons.Default.Close, contentDescription = "移除", modifier = Modifier.size(14.dp))
                    }
                )
            }
        }
        TextButton(onClick = onClear, modifier = Modifier.height(28.dp)) {
            Text("清除全部", style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ===== 菜单类组件 =====

@Composable
private fun TimeRangeMenu(current: SecurityTimeRange, onSelect: (SecurityTimeRange) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(current.label, style = MaterialTheme.typography.bodySmall)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SecurityTimeRange.entries.forEach { r ->
                DropdownMenuItem(
                    text = { Text(r.label) },
                    trailingIcon = if (r == current) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    onClick = { onSelect(r); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun GroupByMenu(current: SecurityGroupBy, onSelect: (SecurityGroupBy) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text("分组：${current.label}", style = MaterialTheme.typography.bodySmall)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SecurityGroupBy.entries.forEach { g ->
                DropdownMenuItem(
                    text = { Text(g.label) },
                    trailingIcon = if (g == current) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    onClick = { onSelect(g); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun LogColumnMenu(selected: Set<SecurityLogColumn>, onToggle: (SecurityLogColumn) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text("列 ${selected.size}/${SecurityLogColumn.entries.size}", style = MaterialTheme.typography.bodySmall)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SecurityLogColumn.entries.forEach { col ->
                DropdownMenuItem(
                    text = { Text(col.label) },
                    leadingIcon = {
                        Checkbox(checked = col in selected, onCheckedChange = { onToggle(col) })
                    },
                    onClick = { onToggle(col) }
                )
            }
        }
    }
}

// ===== 通用小组件 =====

@Composable
private fun LoadingBlock() {
    Box(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "⚠ $message",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}