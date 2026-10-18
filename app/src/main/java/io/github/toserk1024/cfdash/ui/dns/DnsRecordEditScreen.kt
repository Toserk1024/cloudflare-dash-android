package io.github.toserk1024.cfdash.ui.dns

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.toserk1024.cfdash.data.model.DnsRecordTypes

/**
 * DNS 记录新建/编辑表单（按记录类型渲染字段，仿 Cloudflare 控制台）。
 *
 * 性能约定：表单改用 [LazyColumn]，**只组合可见区域的字段**。
 * 之前用 `Column + verticalScroll` 会把十几个 `OutlinedTextField` 一次性组合/测量，
 * 首帧成本高达数百毫秒，导致"第一次进入时转场动画被吞掉"。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsRecordEditScreen(
    zoneId: String,
    recordId: String?,
    zoneName: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: DnsEditViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEdit) "编辑记录" else "新建记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (state.loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        val fieldDefs = remember(state.recordType) { DnsRecordFieldDefs.fields(state.recordType) }
        val displayName = remember(state.name, zoneName) {
            when {
                state.name.isBlank() -> "记录名"
                zoneName.isNullOrBlank() -> state.name
                state.name == "@" -> zoneName // @ 代表根域：不加点，直接显示域名本身
                state.name == zoneName -> state.name
                state.name.endsWith(".$zoneName") -> state.name
                else -> "${state.name}.$zoneName"
            }
        }
        val desc = remember(state.recordType, displayName, state.fields) {
            DnsRecordFieldDefs.description(state.recordType)
                .replace("[名称]", displayName)
                .replace("[目标]", state.fields[DnsRecordFieldDefs.TARGET]?.takeIf { it.isNotBlank() } ?: "目标")
                .replace("[IPv4 地址]", state.fields[DnsRecordFieldDefs.TARGET]?.takeIf { it.isNotBlank() } ?: "IPv4 地址")
                .replace("[IPv6 地址]", state.fields[DnsRecordFieldDefs.TARGET]?.takeIf { it.isNotBlank() } ?: "IPv6 地址")
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp)
        ) {
            // 类型选择
            item(key = "type") {
                Text("记录类型", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                var typeMenu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(
                        onClick = { typeMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.recordType,
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                        DnsRecordTypes.ALL.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    viewModel.setType(type)
                                    typeMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // 说明条
            item(key = "desc") {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 名称
            item(key = "name") {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text("名称") },
                    placeholder = { Text(if (state.recordType == "SRV") "例如 _sip._tcp" else "例如 www，根域用 @") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 按类型渲染字段（懒组合：只有可见的才创建）
            items(fieldDefs, key = { it.key }) { field ->
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.fields[field.key] ?: "",
                    onValueChange = { viewModel.setField(field.key, it) },
                    label = { Text(field.label) },
                    placeholder = {
                        if (field.placeholder.isNotBlank()) Text(field.placeholder)
                    },
                    singleLine = true,
                    keyboardOptions = if (field.numeric) {
                        KeyboardOptions(keyboardType = KeyboardType.Number)
                    } else {
                        KeyboardOptions.Default
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // TTL
            item(key = "ttl") {
                Spacer(modifier = Modifier.height(12.dp))
                Text("TTL", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                var ttlMenu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(
                        onClick = { ttlMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (state.ttl == 1L) "自动" else formatTtl(state.ttl),
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = ttlMenu, onDismissRequest = { ttlMenu = false }) {
                        TTL_OPTIONS.forEach { ttl ->
                            DropdownMenuItem(
                                text = { Text(if (ttl == 1L) "自动" else formatTtl(ttl)) },
                                onClick = {
                                    viewModel.setTtl(ttl)
                                    ttlMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // 代理开关（仅可代理类型）
            if (DnsRecordTypes.PROXIABLE.contains(state.recordType)) {
                item(key = "proxied") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cloudflare 代理", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "开启后流量经过 Cloudflare CDN 并隐藏源站 IP",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.proxied,
                            onCheckedChange = viewModel::setProxied
                        )
                    }
                }
            }

            // 备注
            item(key = "comment") {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.comment,
                    onValueChange = viewModel::setComment,
                    label = { Text("备注（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 错误提示
            state.error?.let { err ->
                item(key = "error") {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "⚠ $err",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // 保存
            item(key = "submit") {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = viewModel::submit,
                    enabled = !state.submitting,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    if (state.submitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("保存中...")
                    } else {
                        Text(if (state.isEdit) "保存修改" else "创建记录", fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

private val TTL_OPTIONS = listOf(
    1L, 300L, 600L, 900L, 1800L, 3600L, 7200L, 18000L, 36000L, 43200L, 86400L, 604800L
)