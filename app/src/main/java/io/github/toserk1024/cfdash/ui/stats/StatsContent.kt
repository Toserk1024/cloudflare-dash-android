package io.github.toserk1024.cfdash.ui.stats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.toserk1024.cfdash.data.model.AnalyticsRange
import io.github.toserk1024.cfdash.data.model.AnalyticsSum

/**
 * 统计数据组件（账号级 / 域名级复用）
 * 指标：请求数、威胁数、带宽、缓存命中率；支持 24h/7d/30d 切换。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsContent(
    summary: AnalyticsSum?,
    loading: Boolean,
    error: String?,
    range: AnalyticsRange,
    enabled: Boolean = true,
    onRangeChange: (AnalyticsRange) -> Unit,
    onRetry: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        // 时间范围切换
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            AnalyticsRange.entries.forEachIndexed { index, r ->
                SegmentedButton(
                    selected = range == r,
                    onClick = { onRangeChange(r) },
                    enabled = enabled && !loading,
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = AnalyticsRange.entries.size)
                ) { Text(r.label, maxLines = 1) }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            loading -> Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            error != null && summary == null -> Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "⚠ $error",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onRetry) { Text("重试") }
            }

            summary != null -> {
                Row(modifier = Modifier.fillMaxWidth()) {
                    MetricCard(title = "请求数", value = formatCount(summary.requests), modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.size(8.dp))
                    MetricCard(title = "威胁数", value = formatCount(summary.threats), modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    MetricCard(title = "带宽", value = formatBytes(summary.bytes), modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.size(8.dp))
                    MetricCard(title = "缓存命中率", value = formatPercent(summary.cacheHitRatio), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 大数格式化：万 / 亿 */
fun formatCount(n: Long): String = when {
    n >= 100_000_000 -> String.format("%.1f亿", n / 100_000_000.0)
    n >= 10_000 -> String.format("%.1f万", n / 10_000.0)
    else -> n.toString()
}

/** 字节格式化：KB / MB / GB */
fun formatBytes(n: Long): String = when {
    n >= 1024L * 1024 * 1024 -> String.format("%.2fGB", n / (1024.0 * 1024 * 1024))
    n >= 1024L * 1024 -> String.format("%.1fMB", n / (1024.0 * 1024))
    n >= 1024L -> String.format("%.1fKB", n / 1024.0)
    else -> "${n}B"
}

/** 比率格式化：百分比 */
fun formatPercent(ratio: Float): String = String.format("%.1f%%", ratio * 100)