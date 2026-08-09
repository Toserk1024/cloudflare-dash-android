package io.github.toserk1024.cfdash.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/** 登录页：选择认证方式（Global API Key / API Token）→ 验证 → 进入主界面（免责声明页为独立启动页） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onSuccess: () -> Unit,
    viewModel: OnboardingViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val authMode by viewModel.authMode.collectAsState()
    val showPassword by viewModel.showPassword.collectAsState()
    var email by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        if (state is OnboardingState.Success) onSuccess()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "CF Dash",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "域名与 DNS 管理",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 认证方式切换（Global API Key 优先，API Token 其次）
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = authMode == AuthMode.GLOBAL,
                    onClick = { viewModel.setAuthMode(AuthMode.GLOBAL) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Global API Key", maxLines = 1) }
                SegmentedButton(
                    selected = authMode == AuthMode.TOKEN,
                    onClick = { viewModel.setAuthMode(AuthMode.TOKEN) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("API Token", maxLines = 1) }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (authMode == AuthMode.GLOBAL) {
                // ===== Global API Key 登录表单 =====
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("邮箱") },
                    placeholder = { Text("Cloudflare 账号邮箱") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Global API Key") },
                    placeholder = { Text("粘贴你的 Global API Key") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        TextButton(onClick = { viewModel.togglePasswordVisibility() }) {
                            Text(if (showPassword) "隐藏" else "显示")
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Global API Key 拥有账号全部权限，请谨慎保管。可在 dash.cloudflare.com → 我的资料 → API 令牌页面底部查看。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.verifyGlobalKey(email, apiKey) },
                    enabled = state !is OnboardingState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (state is OnboardingState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("验证中...")
                    } else {
                        Text("验证并进入", fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                // ===== API Token 登录表单 =====
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("API Token") },
                    placeholder = { Text("粘贴你的 Cloudflare API Token") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        TextButton(onClick = { viewModel.togglePasswordVisibility() }) {
                            Text(if (showPassword) "隐藏" else "显示")
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Token 所需权限列表
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "该方式需要以下权限：",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        listOf(
                            "Zone → Zone → Read / Edit（域名管理）",
                            "Zone → DNS → Read / Edit（DNS 记录）",
                            "Zone → Zone Settings → Read / Edit（高级设置）",
                            "Zone → Analytics → Read（统计数据）",
                            "User → User Details → Read（验证与用户信息）"
                        ).forEach { perm ->
                            Text(
                                text = "• $perm",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Token 仅保存在本机加密存储中，用于调用 Cloudflare API。可在 dash.cloudflare.com/profile/api-tokens 创建。注意：Token 只能包含英文字母、数字与符号。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.verifyToken(token) },
                    enabled = state !is OnboardingState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (state is OnboardingState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("验证中...")
                    } else {
                        Text("验证并进入", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // 错误提示
            (state as? OnboardingState.Error)?.let { err ->
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "⚠ ${err.message}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}