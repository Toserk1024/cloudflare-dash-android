package io.github.toserk1024.cfdash.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.toserk1024.cfdash.BuildConfig
import io.github.toserk1024.cfdash.ui.home.HomeViewModel.HomeUiState
import io.github.toserk1024.cfdash.ui.theme.CloudflareOrange

/** 我的（Profile）Tab */
@Composable
fun ProfileScreen(
    uiState: HomeUiState,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        val user = uiState.user
        when {
            // 加载中
            uiState.loading && user == null -> Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            // 加载失败（无用户数据）
            uiState.error != null && user == null -> Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "⚠ ${uiState.error}",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("重新加载")
                    }
                }
            }

            user != null -> {
                // 用户信息卡片
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = CloudflareOrange) {
                                Box(
                                    modifier = Modifier.size(56.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = user.username.firstOrNull()?.uppercase() ?: "C",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = user.username.ifBlank { "Cloudflare 用户" },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = user.email,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))
                        ProfileInfoRow("用户 ID", user.id)
                        ProfileInfoRow("国家/地区", user.country ?: "-")
                        ProfileInfoRow("2FA 认证", if (user.two_factor_authentication_enabled) "已启用" else "未启用")
                        user.created_on?.let { ProfileInfoRow("注册时间", it.replace("T", " ").substringBefore(".")) }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 关于信息
                AboutCard()
            }
        }
    }
}

/** 关于卡片：功能简介 + 版本 + 开源仓库 */
@Composable
private fun AboutCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("关于 CF Dash", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(12.dp))

            FeatureRow("域名管理", "查看域名详情，配置开发模式、五秒盾、IPv6 等高级设置")
            FeatureRow("DNS 管理", "记录的查看、新建、编辑、删除与批量操作")
            FeatureRow("数据统计", "账户级与域名级流量分析，Vico 图表可视化")
            FeatureRow("缓存清除", "按 URL / 主机 / 标签 / 前缀等方式清除 Cloudflare 缓存")

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "数据仅保存在本机，Token 使用系统级加密存储。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "version: ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 开源仓库（Card，点击打开浏览器）
            val context = LocalContext.current
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Toserk1024/cloudflare-dash-android"))
                            )
                        } catch (_: Exception) { }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Web,
                        contentDescription = "开源仓库",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "GitHub 开源仓库",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "github.com/Toserk1024/cloudflare-dash-android",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** 功能简介行 */
@Composable
private fun FeatureRow(title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}