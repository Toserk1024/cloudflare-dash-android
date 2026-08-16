package io.github.toserk1024.cfdash.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import io.github.toserk1024.cfdash.data.model.SecurityOverview
import io.github.toserk1024.cfdash.data.model.SecurityTrendPoint
import io.github.toserk1024.cfdash.ui.stats.formatCount
import io.github.toserk1024.cfdash.ui.theme.CloudflareOrange
import kotlin.math.roundToInt

/** x 轴分类标签（通过 extras 与模型同步，供 bottomAxis valueFormatter 读取） */
private val labelListKey = com.patrykandpatrick.vico.compose.common.data.ExtraStore.Key<List<String>>()

// 三段颜色：回源(蓝) / 命中(绿) / 缓解(红)
private val originColor = Color(0xFF2D9CDB)
private val cachedColor = Color(0xFF27AE60)
private val mitigatedColor = Color(0xFFEB5757)

/**
 * 100% 水平堆叠条形图（自绘 Canvas/Row 方案，Vico 3.x 不支持横向条形图）。
 * 回源 / 命中 / 缓解 三色块按比例横向排布，下方图例展示名称/数量/占比。
 */
@Composable
fun HorizontalStackBar(
    overview: SecurityOverview,
    modifier: Modifier = Modifier
) {
    val total = overview.total
    if (total <= 0) {
        Box(
            modifier = modifier.fillMaxWidth().height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("暂无数据", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().height(32.dp)) {
            StackSegment(overview.pct(overview.origin), originColor)
            StackSegment(overview.pct(overview.cached), cachedColor)
            StackSegment(overview.pct(overview.mitigated), mitigatedColor)
        }
        Spacer(modifier = Modifier.height(10.dp))
        LegendSegment(originColor, "回源", overview.origin, total)
        LegendSegment(cachedColor, "命中", overview.cached, total)
        LegendSegment(mitigatedColor, "缓解", overview.mitigated, total)
    }
}

@Composable
private fun StackSegment(pct: Float, color: Color) {
    Box(
        modifier = Modifier
            .weight(pct)
            .fillMaxHeight()
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        if (pct > 0.06f) {
            Text(
                text = "${(pct * 100).roundToInt()}%",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun LegendSegment(color: Color, name: String, value: Long, total: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(text = formatCount(value), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (total > 0) "${(value * 100.0 / total).roundToInt()}%" else "0%",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 24h 安全趋势折线图：总请求(橙) + 缓解(红) 双序列 */
@Composable
fun SecurityTrendChart(
    points: List<SecurityTrendPoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("暂无数据", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points) {
        modelProducer.runTransaction {
            lineModel {
                series(y = points.map { it.requests.toFloat() })
                series(y = points.map { it.mitigated.toFloat() })
            }
            extras { it[labelListKey] = points.map { p -> p.label } }
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.Line(LineCartesianLayer.LineFill.single(Fill(CloudflareOrange))),
                    LineCartesianLayer.Line(LineCartesianLayer.LineFill.single(Fill(mitigatedColor)))
                )
            ),
            startAxis = VerticalAxis.rememberStart(
                valueFormatter = CartesianValueFormatter { _, y, _ -> formatCount(y.toLong()) }
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = CartesianValueFormatter { context, x, _ ->
                    context.model.extraStore[labelListKey].getOrNull(x.toInt()) ?: ""
                }
            )
        ),
        modelProducer = modelProducer,
        modifier = modifier.fillMaxWidth().height(200.dp)
    )
}

/** 折线图图例说明 */
@Composable
fun TrendLegend(modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendDot(CloudflareOrange, "总请求")
        Spacer(modifier = Modifier.width(12.dp))
        LegendDot(mitigatedColor, "缓解")
    }
}

@Composable
private fun LegendDot(color: Color, name: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = name, style = TextStyle(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}