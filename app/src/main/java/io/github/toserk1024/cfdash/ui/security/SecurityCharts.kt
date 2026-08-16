package io.github.toserk1024.cfdash.ui.security

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import io.github.toserk1024.cfdash.data.model.SecuritySegment
import io.github.toserk1024.cfdash.data.model.SecurityTrendSeries
import io.github.toserk1024.cfdash.ui.stats.formatCount
import kotlin.math.roundToInt

/** x 轴分类标签（extras 同步） */
private val labelListKey = ExtraStore.Key<List<String>>()

/** 段色板（与 Cloudflare 橙主题协调） */
private val segmentColors = listOf(
    Color(0xFFF6821F), // Cloudflare 橙
    Color(0xFF2D9CDB), // 蓝
    Color(0xFF27AE60), // 绿
    Color(0xFFEB5757), // 红
    Color(0xFF9B51E0), // 紫
    Color(0xFFF2994A), // 橙黄
    Color(0xFF56CCF2), // 浅蓝
    Color(0xFF6FCF97)  // 浅绿
)

// 分组=全部时三段固定色：回源(蓝) / 命中(绿) / 缓解(红)
private val fixedSegmentColors = listOf(
    Color(0xFF2D9CDB), Color(0xFF27AE60), Color(0xFFEB5757)
)

/** 100% 水平堆叠条形图（Canvas 自绘，天然处理 0 值）。分组=全部用固定三段色；分组=X 用色板多段 */
@Composable
fun HorizontalStackBar(
    segments: List<SecuritySegment>,
    groupByAll: Boolean,
    modifier: Modifier = Modifier
) {
    val total = segments.sumOf { it.count }
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
        Canvas(modifier = Modifier.fillMaxWidth().height(32.dp)) {
            val w = size.width
            val h = size.height
            var x = 0f
            segments.forEachIndexed { index, seg ->
                val pct = seg.count.toFloat() / total
                if (pct > 0f) {
                    val color = if (groupByAll) fixedSegmentColors.getOrElse(index) { segmentColors[index % segmentColors.size] }
                    else segmentColors[index % segmentColors.size]
                    drawRect(color, Offset(x, 0f), Size(w * pct, h))
                    x += w * pct
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        segments.forEachIndexed { index, seg ->
            val color = if (groupByAll) fixedSegmentColors.getOrElse(index) { segmentColors[index % segmentColors.size] }
            else segmentColors[index % segmentColors.size]
            LegendSegment(color, seg.name, seg.count, total)
        }
    }
}

@Composable
private fun LegendSegment(color: Color, name: String, value: Long, total: Long) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
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

/** 安全趋势折线图（支持多序列：分组=全部单线"请求"；分组=X Top5 分组各一线） */
@Composable
fun SecurityTrendChart(
    series: List<SecurityTrendSeries>,
    modifier: Modifier = Modifier
) {
    if (series.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("暂无数据", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    // 统一 x 轴：取全部 label 并集，缺失点补 0（保证多序列长度一致）
    val allLabels = remember(series) {
        series.flatMap { it.points.map { p -> p.label } }.distinct().sorted()
    }
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(series, allLabels) {
        modelProducer.runTransaction {
            lineModel {
                series.forEach { s ->
                    series(
                        y = allLabels.map { label ->
                            s.points.firstOrNull { it.label == label }?.count?.toFloat() ?: 0f
                        }
                    )
                }
            }
            extras { it[labelListKey] = allLabels }
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.Line(LineCartesianLayer.LineFill.single(Fill(segmentColors[0]))),
                    LineCartesianLayer.Line(LineCartesianLayer.LineFill.single(Fill(segmentColors[1]))),
                    LineCartesianLayer.Line(LineCartesianLayer.LineFill.single(Fill(segmentColors[2]))),
                    LineCartesianLayer.Line(LineCartesianLayer.LineFill.single(Fill(segmentColors[3]))),
                    LineCartesianLayer.Line(LineCartesianLayer.LineFill.single(Fill(segmentColors[4])))
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

/** 趋势图例：逐序列色块 + 名称 */
@Composable
fun TrendLegend(series: List<SecurityTrendSeries>, modifier: Modifier = Modifier) {
    if (series.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth().padding(top = 4.dp)) {
        series.forEachIndexed { index, s ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(segmentColors[index % segmentColors.size]))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = s.name,
                    style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}