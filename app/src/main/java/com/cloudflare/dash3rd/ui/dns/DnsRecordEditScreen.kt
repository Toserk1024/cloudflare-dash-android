package com.cloudflare.dash3rd.ui.dns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloudflare.dash3rd.data.model.DnsRecordTypes

/** DNS 记录新建/编辑表单 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsRecordEditScreen(
    zoneId: String,
    recordId: String?,
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // 类型选择
            Text("记录类型", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            var typeMenu by remember { mutableStateOf(false) }
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

            Spacer(modifier = Modifier.height(16.dp))

            // 名称
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text("名称") },
                placeholder = { Text("例如 www，根域用 @") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 内容
            OutlinedTextField(
                value = state.content,
                onValueChange = viewModel::setContent,
                label = { Text("内容") },
                placeholder = { Text(contentHint(state.recordType)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            // 优先级（MX/URI）
            if (DnsRecordTypes.HAS_PRIORITY.contains(state.recordType)) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.priority,
                    onValueChange = viewModel::setPriority,
                    label = { Text("优先级 (1-65535)") },
                    placeholder = { Text("10") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // TTL
            Text("TTL", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            var ttlMenu by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = { ttlMenu = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (state.ttl == 1L) "自动" else "${state.ttl} 秒",
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = ttlMenu, onDismissRequest = { ttlMenu = false }) {
                TTL_OPTIONS.forEach { ttl ->
                    DropdownMenuItem(
                        text = { Text(if (ttl == 1L) "自动" else "$ttl 秒") },
                        onClick = {
                            viewModel.setTtl(ttl)
                            ttlMenu = false
                        }
                    )
                }
            }

            // 代理开关（仅可代理类型）
            if (DnsRecordTypes.PROXIABLE.contains(state.recordType)) {
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

            Spacer(modifier = Modifier.height(12.dp))

            // 备注
            OutlinedTextField(
                value = state.comment,
                onValueChange = viewModel::setComment,
                label = { Text("备注（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 错误
            state.error?.let { err ->
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "⚠ $err",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

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

private fun contentHint(type: String): String = when (type) {
    "A" -> "IPv4 地址，例如 1.2.3.4"
    "AAAA" -> "IPv6 地址，例如 2001:db8::1"
    "CNAME" -> "目标域名，例如 example.com"
    "MX" -> "邮件服务器地址，例如 mail.example.com"
    "TXT" -> "文本内容，例如 v=spf1 include:_spf.example.com ~all"
    "NS" -> "名称服务器，例如 ns1.example.com"
    "SRV" -> "格式：优先级 权重 端口 目标，例如 0 5 5060 sip.example.com"
    "CAA" -> "格式：flags tag value，例如 0 issue \"letsencrypt.org\""
    else -> "记录内容"
}

private val TTL_OPTIONS = listOf(
    1L, 300L, 600L, 900L, 1800L, 3600L, 7200L, 18000L, 36000L, 43200L, 86400L, 604800L
)