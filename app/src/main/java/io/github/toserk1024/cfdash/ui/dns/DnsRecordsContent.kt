package io.github.toserk1024.cfdash.ui.dns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.toserk1024.cfdash.data.model.DnsRecord
import io.github.toserk1024.cfdash.data.model.DnsRecordTypes

/**
 * DNS 记录列表内容（在 Home Tab 与独立页面中复用）
 * 支持本地搜索、类型筛选、下拉刷新，FAB 新建记录。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsRecordsContent(
    onEditRecord: (DnsRecord) -> Unit,
    onAddRecord: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DnsViewModel
) {
    val state by viewModel.uiState.collectAsState()

    // 从编辑/新建页返回（重新进入组合）时，同步本地缓存中的新增/修改记录
    LaunchedEffect(Unit) {
        viewModel.syncPendingChanges()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 域名选择器 + 候选框启用图标（顶栏右侧）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = viewModel::showZonePicker,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = state.selectedZone?.name ?: "选择域名",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                // check-all 图标：启用/关闭候选框（批量操作）
                IconButton(onClick = { viewModel.setSelectionMode(!state.selectionMode) }) {
                    Icon(
                        Icons.Default.Checklist,
                        contentDescription = "批量操作",
                        tint = if (state.selectionMode) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            // 关键字搜索（本地过滤）
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("搜索记录名称") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // 类型筛选
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(listOf("全部") + DnsRecordTypes.ALL) { type ->
                    FilterChip(
                        selected = state.filterType == type || (type == "全部" && state.filterType.isEmpty()),
                        onClick = {
                            viewModel.setFilterType(if (type == "全部") "" else type)
                        },
                        label = { Text(type) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 批量操作栏（候选框启用时显示）
            if (state.selectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已选 ${state.selectedIds.size} 条",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = viewModel::requestBulkDelete, enabled = !state.bulkBusy) { Text("删除") }
                    // 批量代理仅对 A/AAAA/CNAME 生效（其余类型选中会被忽略）
                    OutlinedButton(onClick = { viewModel.requestBulkProxy(true) }, enabled = !state.bulkBusy) { Text("开代理") }
                    OutlinedButton(onClick = { viewModel.requestBulkProxy(false) }, enabled = !state.bulkBusy) { Text("关代理") }
                }
            }

            // 记录列表（下拉刷新）
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    state.loadingRecords && state.records.isEmpty() -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    state.error != null && state.records.isEmpty() -> Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("⚠ ${state.error}", textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = viewModel::refresh) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("重试")
                        }
                    }

                    state.records.isEmpty() -> Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (state.query.isNotBlank() || state.filterType.isNotBlank()) "未找到匹配的记录" else "暂无 DNS 记录",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (state.query.isNotBlank() || state.filterType.isNotBlank()) "可清除筛选条件" else "点击右下角 + 新建记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.records, key = { it.id }) { record ->
                            DnsRecordCard(
                                record = record,
                                deleting = state.deletingId == record.id,
                                selectionMode = state.selectionMode,
                                selected = record.id in state.selectedIds,
                                onSelectChange = { viewModel.toggleSelect(record.id) },
                                onEdit = { onEditRecord(record) },
                                onDelete = { viewModel.requestDelete(record) }
                            )
                        }
                    }
                }
            }
        }

        // 新建记录 FAB
        FloatingActionButton(
            onClick = onAddRecord,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "新建记录")
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
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(modifier = Modifier.size(28.dp)) }
                } else if (state.zones.isEmpty()) {
                    Text("暂无域名，请先在“域名”页添加。")
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        state.zones.forEach { zone ->
                            TextButton(
                                onClick = { viewModel.selectZone(zone) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = zone.name,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Start
                                )
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

    // 删除确认
    state.showDeleteDialog?.let { record ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("删除记录") },
            text = { Text("确定要删除 ${record.type} 记录 “${record.name}” 吗？\n\n此操作不可恢复！") },
            confirmButton = {
                TextButton(onClick = viewModel::deleteRecord) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) { Text("取消") }
            }
        )
    }

    // 批量删除确认
    if (state.showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissBulkDelete,
            title = { Text("批量删除记录") },
            text = { Text("确定要删除选中的 ${state.selectedIds.size} 条 DNS 记录吗？\n\n此操作不可恢复！") },
            confirmButton = {
                TextButton(onClick = viewModel::bulkDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissBulkDelete) { Text("取消") }
            }
        )
    }

    // 批量代理确认（仅 A/AAAA/CNAME 生效）
    state.bulkProxyTarget?.let { target ->
        AlertDialog(
            onDismissRequest = viewModel::dismissBulkProxy,
            title = { Text(if (target) "开启代理" else "关闭代理") },
            text = { Text("确定要${if (target) "开启" else "关闭"}选中记录（仅 A/AAAA/CNAME 类型生效）的 Cloudflare 代理吗？") },
            confirmButton = {
                TextButton(onClick = { viewModel.bulkSetProxy(target) }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissBulkProxy) { Text("取消") }
            }
        )
    }
}

/** DNS 记录卡片（含候选框，可勾选参与批量操作） */
@Composable
private fun DnsRecordCard(
    record: DnsRecord,
    deleting: Boolean,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelectChange: (Boolean) -> Unit = {},
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onEdit,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 候选框仅在启用批量模式时显示，避免常显拥挤
                if (selectionMode) {
                    Checkbox(checked = selected, onCheckedChange = onSelectChange)
                }
                TypeBadge(record.type)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = record.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete, enabled = !deleting) {
                    if (deleting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = record.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "TTL: ${if (record.ttl == 1L) "自动" else formatTtl(record.ttl)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                record.priority?.let {
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "优先级: $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (record.proxied) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "已代理",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (!record.proxiable) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "DNS only",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "编辑",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            record.comment?.takeIf { it.isNotBlank() }?.let { comment ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "备注: $comment",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 记录类型徽章 */
@Composable
private fun TypeBadge(type: String) {
    val color = when (type) {
        "A", "AAAA" -> Color(0xFF0051C3)
        "CNAME" -> Color(0xFFF6821F)
        "MX" -> Color(0xFF7B1FA2)
        "TXT" -> Color(0xFF00897B)
        "NS" -> Color(0xFF546E7A)
        "SRV" -> Color(0xFFE65100)
        "CAA" -> Color(0xFF6A1B9A)
        else -> Color(0xFF455A64)
    }
    Surface(
        color = color.copy(alpha = 0.13f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = type,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** TTL（秒）格式化为可读时分秒（如 300→5分钟、3600→1小时、86400→1天）；调用处负责 TTL=1（自动）特判 */
fun formatTtl(seconds: Long): String = when {
    seconds < 60L -> "${seconds}秒"
    seconds < 3600L -> "${seconds / 60}分钟"
    seconds < 86400L -> {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        if (m > 0) "${h}小时${m}分钟" else "${h}小时"
    }
    else -> {
        val d = seconds / 86400
        val h = (seconds % 86400) / 3600
        if (h > 0) "${d}天${h}小时" else "${d}天"
    }
}