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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** 安全 Tab：TabRow 子项「总览 | 日志」，总览/日志筛选器隔离，日志列自选 */
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
    Column(modifier = Modifier.fillMaxWidth()) {
        PrimaryTabRow(selectedTabIndex = if (state.section == SecuritySection.OVERVIEW) 0 else 1) {
            Tab(selected = state.section == SecuritySection.OVERVIEW, onClick = { onSection(SecuritySection.OVERVIEW) }, text = { Text("总览") })
            Tab(selected = state.section == SecuritySection.LOG, onClick = { onSection(SecuritySection.LOG) }, text = { Text("日志") })
        }
        Spacer(modifier = Modifier.height(12.dp))
        when (state.section) {
            SecuritySection.OVERVIEW -> OverviewContent(state, onGroupBy, onTimeRange, onAddOverviewFilter, onRemoveOverviewFilter, onClearOverviewFilters, onRefresh, onRetry)
            SecuritySection.LOG -> LogContent(state, onLogTimeRange, onAddLogFilter, onRemoveLogFilter, onClearLogFilters, onToggleLogColumn)
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
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    GroupBySelector(current = state.groupBy, onSelect = onGroupBy)
                    Spacer(modifier = Modifier.height(10.dp))
                    TimeRangeSelector(current = state.timeRange, onChange = onTimeRange)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            FilterPanel(state.overviewFilters, onAddFilter, onRemoveFilter, onClearFilters)
            Spacer(modifier = Modifier.height(10.dp))

            when {
                state.overviewLoading -> CenterProgress()
                state.overviewError != null -> Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("⚠ ${state.overviewError}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onRetry) { Text("重试") }
                }
                else -> {
                    ChartCard(if (state.groupBy == SecurityGroupBy.ALL) "安全概况" else "${state.groupBy.label}占比") {
                        HorizontalStackBar(state.overview, state.groupBy == SecurityGroupBy.ALL)
                    }
                    if (!state.partError.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("⚠ 部分数据加载失败：${state.partError}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (state.trend.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        ChartCard("趋势（${state.timeRange.label}）") {
                            SecurityTrendChart(state.trend)
                            TrendLegend(state.trend)
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
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                TimeRangeSelector(state.logTimeRange, onTimeRange)
                Spacer(modifier = Modifier.height(10.dp))
                ColumnSelector(state.selectedLogColumns, onToggleLogColumn)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        FilterPanel(state.logFilters, onAddFilter, onRemoveFilter, onClearFilters)
        Spacer(modifier = Modifier.height(10.dp))

        when {
            state.logLoading -> CenterProgress()
            state.logError != null -> Text("⚠ ${state.logError}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 20.dp))
            state.allLogs.isEmpty() -> Text("暂无日志", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 20.dp))
            else -> LogTable(state.allLogs, state.selectedLogColumns)
        }
    }
}

// ===================== 通用组件 =====================

@Composable
private fun CenterProgress() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun TimeRangeSelector(current: SecurityTimeRange, onChange: (SecurityTimeRange) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SecurityTimeRange.entries.forEachIndexed { index, r ->
            SegmentedButton(
                selected = current == r,
                onClick = { onChange(r) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = SecurityTimeRange.entries.size)
            ) { Text(r.label, maxLines = 1, fontSize = 12.sp) }
        }
    }
}

/** 分组视图：Label 在左侧 + 选择器只显示当前值（Dropdown） */
@Composable
private fun GroupBySelector(current: SecurityGroupBy, onSelect: (SecurityGroupBy) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("分组视图：", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(current.label, maxLines = 1)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                SecurityGroupBy.entries.forEach { gb ->
                    DropdownMenuItem(text = { Text(gb.label) }, onClick = { onSelect(gb); expanded = false })
                }
            }
        }
    }
}

/** 日志列选择（Dropdown 多选） */
@Composable
private fun ColumnSelector(selected: Set<SecurityLogColumn>, onToggle: (SecurityLogColumn) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { expanded = true }) {
            Text("显示列 ${selected.size}/${SecurityLogColumn.entries.size}", maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(selected.joinToString(" · ") { it.label }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Box {
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SecurityLogColumn.entries.forEach { col ->
                DropdownMenuItem(text = { Text("${if (col in selected) "✓ " else ""}${col.label}", maxLines = 1) }, onClick = { onToggle(col) })
            }
        }
    }
}

/** 筛选器面板（tiny） */
@Composable
private fun FilterPanel(
    filters: List<SecurityFilter>,
    onAddFilter: (SecurityFilter) -> Unit,
    onRemoveFilter: (Int) -> Unit,
    onClearFilters: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("筛选器", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (filters.isNotEmpty()) TextButton(onClick = onClearFilters) { Text("清除全部", style = MaterialTheme.typography.bodySmall) }
            }
            Spacer(modifier = Modifier.height(6.dp))
            FilterEditor(onAddFilter)
            if (filters.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                filters.forEachIndexed { index, f ->
                    FilterChip(
                        selected = true,
                        onClick = { onRemoveFilter(index) },
                        label = { Text("${f.attr.label} ${f.op.label} ${f.values.joinToString("、")}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "移除", modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}

/** 新建筛选器：属性/运算符/值 对应关系重构 */
@Composable
private fun FilterEditor(onAddFilter: (SecurityFilter) -> Unit) {
    var attr by remember { mutableStateOf(SecurityFilterAttr.IP) }
    var op by remember { mutableStateOf(SecurityFilterOp.EQ) }
    var values by remember { mutableStateOf(listOf<String>()) }
    var singleValue by remember { mutableStateOf("") }
    var countryQuery by remember { mutableStateOf("") }

    Row(verticalAlignment = Alignment.CenterVertically) {
        LabeledDropdown("属性", attr.label, SecurityFilterAttr.entries.map { it.label }) { sel ->
            SecurityFilterAttr.entries.firstOrNull { it.label == sel }?.let {
                attr = it; values = emptyList(); singleValue = ""; countryQuery = ""
                // 固定候选属性（方法/HTTP版本/操作/来源）仅支持等于/不等于，切到其它运算符时自动重置
                if (attr.valueKind == FilterValueKind.CANDIDATES && op != SecurityFilterOp.EQ && op != SecurityFilterOp.NEQ) {
                    op = SecurityFilterOp.EQ
                }
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        val allowedOps = if (attr.valueKind == FilterValueKind.CANDIDATES)
            listOf(SecurityFilterOp.EQ, SecurityFilterOp.NEQ)
        else SecurityFilterOp.entries
        LabeledDropdown("条件", op.label, allowedOps.map { it.label }) { sel ->
            SecurityFilterOp.entries.firstOrNull { it.label == sel }?.let { op = it }
        }
    }
    Spacer(modifier = Modifier.height(6.dp))

    // 值控件按属性类型（valueKind）动态切换
    when (attr.valueKind) {
        FilterValueKind.COUNTRY -> CountryValueSelector(countryQuery, { countryQuery = it }, values,
            { code -> values = if (code in values) values - code else (values + code).distinct() })
        FilterValueKind.TEXT -> Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = singleValue,
                onValueChange = { singleValue = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入值", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall
            )
            // 包含/不包含时：加号在输入框后（外部），用于追加多值
            if (op == SecurityFilterOp.CONTAINS || op == SecurityFilterOp.NOT_CONTAINS) {
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(onClick = {
                    if (singleValue.isNotBlank()) { values = (values + singleValue).distinct(); singleValue = "" }
                }) { Icon(Icons.Default.Add, contentDescription = "添加多值") }
            }
        }
        FilterValueKind.CANDIDATES -> CandidatesSelector(attr.candidates, values, { values = it })
    }
    // 已选多值 chips
    if (values.isNotEmpty()) {
        Spacer(modifier = Modifier.height(6.dp))
        Column {
            values.forEach { v ->
                FilterChip(
                    selected = true,
                    onClick = { values = values - v },
                    label = { Text(if (attr == SecurityFilterAttr.COUNTRY) CountryMapping.codeToName(v) else v, maxLines = 1, style = MaterialTheme.typography.bodySmall) },
                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "移除", modifier = Modifier.size(14.dp)) },
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
    FilledTonalButton(
        onClick = {
            val finalValues = when (attr.valueKind) {
                FilterValueKind.COUNTRY -> values
                FilterValueKind.TEXT -> if (op == SecurityFilterOp.CONTAINS || op == SecurityFilterOp.NOT_CONTAINS) values
                    else if (singleValue.isNotBlank()) listOf(singleValue) else emptyList()
                FilterValueKind.CANDIDATES -> values
            }
            if (finalValues.isNotEmpty()) {
                onAddFilter(SecurityFilter(attr, op, finalValues))
                values = emptyList(); singleValue = ""; countryQuery = ""
            }
        },
        enabled = finalEnabled(attr, op, values, singleValue),
        modifier = Modifier.fillMaxWidth()
    ) { Text("添加筛选器", style = MaterialTheme.typography.bodySmall) }
}

private fun finalEnabled(attr: SecurityFilterAttr, op: SecurityFilterOp, values: List<String>, singleValue: String): Boolean = when (attr.valueKind) {
    FilterValueKind.COUNTRY -> values.isNotEmpty()
    FilterValueKind.TEXT -> if (op == SecurityFilterOp.CONTAINS || op == SecurityFilterOp.NOT_CONTAINS) values.isNotEmpty() else singleValue.isNotBlank()
    FilterValueKind.CANDIDATES -> values.isNotEmpty()
}

/** 候选值选择框（FilterChip 组：方法/HTTP版本/操作/来源等） */
@Composable
private fun CandidatesSelector(candidates: List<String>, selected: List<String>, onSelect: (List<String>) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        candidates.forEach { c ->
            FilterChip(
                selected = c in selected,
                onClick = { onSelect(if (c in selected) selected - c else selected + c) },
                label = { Text(c, maxLines = 1, style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.padding(vertical = 1.dp)
            )
        }
    }
}

/** 国家搜索：聚焦才展开候选 + 点击 toggle */
@Composable
private fun CountryValueSelector(
    query: String,
    onQueryChange: (String) -> Unit,
    selected: List<String>,
    onToggle: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val candidates = remember(query) { CountryMapping.search(query) }
    Box {
        OutlinedTextField(
            value = query,
            onValueChange = { onQueryChange(it) },
            modifier = Modifier.fillMaxWidth().onFocusChanged { expanded = it.isFocused },
            placeholder = { Text("输入国家名或代码", style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            candidates.forEach { (code, name) ->
                val checked = code in selected
                DropdownMenuItem(
                    text = { Text("${if (checked) "✓ " else ""}$name ($code)", maxLines = 1) },
                    onClick = { onToggle(code); onQueryChange("") }
                )
            }
        }
    }
}

/** 带 Label 的下拉（tiny） */
@Composable
private fun LabeledDropdown(label: String, current: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(current, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(text = { Text(opt, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { onSelect(opt); expanded = false })
                }
            }
        }
    }
}

/** 日志表格（自选列，横向滚动） */
@Composable
private fun LogTable(logs: List<SecurityLogEntry>, columns: Set<SecurityLogColumn>) {
    val cols = columns.toList()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            val scroll = rememberScrollState()
            Row(modifier = Modifier.horizontalScroll(scroll).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                TableCell("时间", bold = true)
                cols.forEach { TableCell(it.label, bold = true) }
            }
            logs.forEach { log ->
                Row(modifier = Modifier.horizontalScroll(scroll).padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
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
        modifier = Modifier.padding(horizontal = 6.dp).width(100.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

private fun logValue(log: SecurityLogEntry, col: SecurityLogColumn): String = when (col) {
    SecurityLogColumn.ACTION -> log.action
    SecurityLogColumn.ASN -> log.clientAsn.orEmpty()
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

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}