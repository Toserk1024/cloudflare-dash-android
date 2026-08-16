package io.github.toserk1024.cfdash.ui.security

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.toserk1024.cfdash.data.model.CountryMapping
import io.github.toserk1024.cfdash.data.model.SecurityFilter
import io.github.toserk1024.cfdash.data.model.SecurityFilterAttr
import io.github.toserk1024.cfdash.data.model.SecurityFilterOp
import io.github.toserk1024.cfdash.data.model.SecurityGroupBy
import io.github.toserk1024.cfdash.data.model.SecurityLogColumn
import io.github.toserk1024.cfdash.data.model.SecurityLogEntry
import io.github.toserk1024.cfdash.data.model.SecurityTimeRange
import io.github.toserk1024.cfdash.ui.security.SecurityViewModel.SecuritySection

/** 安全 Tab：子项切换「总览 | 日志」，总览/日志筛选器隔离，日志列自选 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityTab(
    state: SecurityViewModel.SecurityUiState,
    onSection: (SecuritySection) -> Unit,
    onGroupBy: (SecurityGroupBy) -> Unit,
    onTimeRange: (SecurityTimeRange) -> Unit,
    onLogTimeRange: (SecurityTimeRange) -> Unit,
    onAddOverviewFilter: (SecurityFilter) -> Unit,
    onRemoveOverviewFilter: (Int) -> Unit,
    onClearOverviewFilters: () -> Unit,
    onAddLogFilter: (SecurityFilter) -> Unit,
    onRemoveLogFilter: (Int) -> Unit,
    onClearLogFilters: () -> Unit,
    onToggleLogColumn: (SecurityLogColumn) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        // 子项切换
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SecuritySection.entries.forEachIndexed { index, s ->
                SegmentedButton(
                    selected = state.section == s,
                    onClick = { onSection(s) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = SecuritySection.entries.size)
                ) { Text(if (s == SecuritySection.OVERVIEW) "总览" else "日志", maxLines = 1) }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        when (state.section) {
            SecuritySection.OVERVIEW -> OverviewContent(
                state = state,
                onGroupBy = onGroupBy,
                onTimeRange = onTimeRange,
                onAddFilter = onAddOverviewFilter,
                onRemoveFilter = onRemoveOverviewFilter,
                onClearFilters = onClearOverviewFilters,
                onRefresh = onRefresh,
                onRetry = onRetry
            )
            SecuritySection.LOG -> LogContent(
                state = state,
                onTimeRange = onLogTimeRange,
                onAddFilter = onAddLogFilter,
                onRemoveFilter = onRemoveLogFilter,
                onClearFilters = onClearLogFilters,
                onToggleLogColumn = onToggleLogColumn
            )
        }
    }
}

// ===================== 总览子页 =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverviewContent(
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
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            // 控制卡：分组视图 + 时间范围
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    GroupBySelector(current = state.groupBy, onSelect = onGroupBy)
                    Spacer(modifier = Modifier.height(12.dp))
                    TimeRangeSelector(current = state.timeRange, onChange = onTimeRange)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            FilterPanel(filters = state.overviewFilters, onAddFilter = onAddFilter, onRemoveFilter = onRemoveFilter, onClearFilters = onClearFilters)
            Spacer(modifier = Modifier.height(12.dp))

            when {
                state.overviewLoading -> CenterProgress()
                state.overviewError != null -> Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("⚠ ${state.overviewError}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onRetry) { Text("重试") }
                }
                else -> {
                    ChartCard(if (state.groupBy == SecurityGroupBy.ALL) "安全概况" else "${state.groupBy.label}占比") {
                        HorizontalStackBar(segments = state.overview, groupByAll = state.groupBy == SecurityGroupBy.ALL)
                    }
                    if (!state.partError.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("⚠ 部分数据加载失败：${state.partError}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
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
                }
            }
        }
    }
}

// ===================== 日志子页 =====================

@Composable
private fun LogContent(
    state: SecurityViewModel.SecurityUiState,
    onTimeRange: (SecurityTimeRange) -> Unit,
    onAddFilter: (SecurityFilter) -> Unit,
    onRemoveFilter: (Int) -> Unit,
    onClearFilters: () -> Unit,
    onToggleLogColumn: (SecurityLogColumn) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                TimeRangeSelector(current = state.logTimeRange, onChange = onTimeRange)
                Spacer(modifier = Modifier.height(12.dp))
                ColumnSelector(selected = state.selectedLogColumns, onToggle = onToggleLogColumn)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        FilterPanel(filters = state.logFilters, onAddFilter = onAddFilter, onRemoveFilter = onRemoveFilter, onClearFilters = onClearFilters)
        Spacer(modifier = Modifier.height(12.dp))

        when {
            state.logLoading -> CenterProgress()
            state.logError != null -> Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("⚠ ${state.logError}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            state.allLogs.isEmpty() -> Text("暂无日志", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
            else -> LogTable(logs = state.allLogs, columns = state.selectedLogColumns)
        }
    }
}

// ===================== 通用组件 =====================

@Composable
private fun CenterProgress() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun TimeRangeSelector(current: SecurityTimeRange, onChange: (SecurityTimeRange) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SecurityTimeRange.entries.forEachIndexed { index, r ->
            SegmentedButton(
                selected = current == r,
                onClick = { onChange(r) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = SecurityTimeRange.entries.size)
            ) { Text(r.label, maxLines = 1) }
        }
    }
}

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
                DropdownMenuItem(text = { Text(gb.label) }, onClick = { onSelect(gb); expanded = false })
            }
        }
    }
}

/** 日志列选择（Dropdown 多选 + 候选框） */
@Composable
private fun ColumnSelector(selected: Set<SecurityLogColumn>, onToggle: (SecurityLogColumn) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { expanded = true }) {
            Text("显示列（${selected.size}/${SecurityLogColumn.entries.size}）", maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        Spacer(modifier = Modifier.width(8.dp))
        // 已选列预览
        Text(
            text = selected.joinToString(" · ") { it.label },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    Box {
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SecurityLogColumn.entries.forEach { col ->
                DropdownMenuItem(
                    text = { Text("${if (col in selected) "✓ " else ""}${col.label}", maxLines = 1) },
                    onClick = { onToggle(col) }
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
                if (filters.isNotEmpty()) TextButton(onClick = onClearFilters) { Text("清除全部") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            FilterEditor(onAddFilter = onAddFilter)
            if (filters.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                filters.forEachIndexed { index, f ->
                    FilterChip(
                        selected = true,
                        onClick = { onRemoveFilter(index) },
                        label = { Text("${f.attr.label} ${f.op.label} ${f.values.joinToString("、")}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "移除", modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/** 新建筛选器编辑器：属性/条件 Dropdown + 人性化值输入（国家搜索、多选） */
@Composable
private fun FilterEditor(onAddFilter: (SecurityFilter) -> Unit) {
    var attr by remember { mutableStateOf(SecurityFilterAttr.IP) }
    var op by remember { mutableStateOf(SecurityFilterOp.EQ) }
    var values by remember { mutableStateOf(listOf<String>()) } // 多值（国家/包含）
    var singleValue by remember { mutableStateOf("") } // 单值文本
    var countryQuery by remember { mutableStateOf("") }

    Row(verticalAlignment = Alignment.CenterVertically) {
        SelectorDropdown(current = attr.label, options = SecurityFilterAttr.entries.map { it.label }) { sel ->
            SecurityFilterAttr.entries.firstOrNull { it.label == sel }?.let { attr = it; values = emptyList(); singleValue = ""; countryQuery = "" }
        }
        Spacer(modifier = Modifier.width(8.dp))
        SelectorDropdown(current = op.label, options = SecurityFilterOp.entries.map { it.label }) { sel ->
            SecurityFilterOp.entries.firstOrNull { it.label == sel }?.let { op = it }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))

    if (attr == SecurityFilterAttr.COUNTRY) {
        // 国家：边输入边搜索 + 候选多选
        CountryValueSelector(query = countryQuery, onQueryChange = { countryQuery = it }, selected = values, onSelect = { code -> values = (values + code).distinct() }, onRemove = { code -> values = values - code })
    } else {
        // 其他属性：文本输入；"包含"支持多值
        OutlinedTextField(
            value = singleValue,
            onValueChange = { singleValue = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("输入值，如 192.168.0.1") },
            singleLine = true,
            trailingIcon = if (op == SecurityFilterOp.CONTAINS) {
                { IconButtonCompat(onClick = {
                    if (singleValue.isNotBlank()) { values = (values + singleValue).distinct(); singleValue = "" }
                }) { Icon(Icons.Default.Add, contentDescription = "添加") } }
            } else null
        )
    }
    // 已选多值 chips（国家/包含）
    if (values.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Column {
            values.forEach { v ->
                FilterChip(
                    selected = true,
                    onClick = { values = values - v },
                    label = { Text(if (attr == SecurityFilterAttr.COUNTRY) CountryMapping.codeToName(v) else v, maxLines = 1) },
                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "移除", modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    FilledTonalButton(
        onClick = {
            val finalValues = when {
                attr == SecurityFilterAttr.COUNTRY -> values
                op == SecurityFilterOp.CONTAINS -> values
                singleValue.isNotBlank() -> listOf(singleValue)
                else -> emptyList()
            }
            if (finalValues.isNotEmpty()) {
                onAddFilter(SecurityFilter(attr, op, finalValues))
                values = emptyList(); singleValue = ""; countryQuery = ""
            }
        },
        enabled = (attr == SecurityFilterAttr.COUNTRY && values.isNotEmpty()) ||
            (attr != SecurityFilterAttr.COUNTRY && (if (op == SecurityFilterOp.CONTAINS) values.isNotEmpty() else singleValue.isNotBlank())),
        modifier = Modifier.fillMaxWidth()
    ) { Text("添加筛选器") }
}

@Composable
private fun IconButtonCompat(onClick: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick) { content() }
}

/** 国家搜索选择器：边输入边搜索 + 候选列表 */
@Composable
private fun CountryValueSelector(
    query: String,
    onQueryChange: (String) -> Unit,
    selected: List<String>,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val candidates = remember(query) { CountryMapping.search(query) }
    Box {
        OutlinedTextField(
            value = query,
            onValueChange = { onQueryChange(it); expanded = true },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("输入国家名或代码搜索") },
            singleLine = true
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            candidates.forEach { (code, name) ->
                val checked = code in selected
                DropdownMenuItem(
                    text = { Text("${if (checked) "✓ " else ""}$name ($code)", maxLines = 1) },
                    onClick = { onSelect(code); onQueryChange("") }
                )
            }
        }
    }
}

/** 通用下拉（属性/条件/列） */
@Composable
private fun SelectorDropdown(current: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(current, maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { onSelect(opt); expanded = false })
            }
        }
    }
}

/** 日志表格（自选列，横向滚动） */
@Composable
private fun LogTable(logs: List<SecurityLogEntry>, columns: Set<SecurityLogColumn>) {
    val cols = columns.toList()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            val scroll = rememberScrollState()
            Row(modifier = Modifier.horizontalScroll(scroll).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TableCell("时间", bold = true)
                cols.forEach { TableCell(it.label, bold = true) }
            }
            logs.forEach { log ->
                Row(modifier = Modifier.horizontalScroll(scroll).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TableCell(log.datetime)
                    cols.forEach { TableCell(logValue(log, it)) }
                }
            }
        }
    }
}

@Composable
private fun TableCell(text: String, bold: Boolean = false) {
    Text(
        text = text.ifBlank { "-" },
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (bold) FontWeight.SemiBold else null,
        color = if (bold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp).width(110.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** 按列提取日志字段值 */
private fun logValue(log: SecurityLogEntry, col: SecurityLogColumn): String = when (col) {
    SecurityLogColumn.ACTION -> log.action
    SecurityLogColumn.ASN -> log.clientASN.orEmpty()
    SecurityLogColumn.COUNTRY -> log.clientCountry?.let { CountryMapping.codeToName(it) }.orEmpty()
    SecurityLogColumn.IP -> log.clientIP.orEmpty()
    SecurityLogColumn.HOST -> log.host.orEmpty()
    SecurityLogColumn.METHOD -> log.method.orEmpty()
    SecurityLogColumn.HTTP_VERSION -> log.httpVersion.orEmpty()
    SecurityLogColumn.PATH -> log.path.orEmpty()
    SecurityLogColumn.QUERY -> log.query.orEmpty()
    SecurityLogColumn.RAY_ID -> log.rayId.orEmpty()
    SecurityLogColumn.RULE_ID -> log.ruleId.orEmpty()
    SecurityLogColumn.SERVICE -> log.source.orEmpty()
    SecurityLogColumn.USER_AGENT -> log.userAgent.orEmpty()
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