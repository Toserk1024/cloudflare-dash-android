package io.github.toserk1024.cfdash.ui.stats

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.mikephil.charting.compose.BarChart
import com.github.mikephil.charting.compose.LineChart
import com.github.mikephil.charting.compose.PieChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IAxisValueFormatter
import com.github.mikephil.charting.utils.Fill
import io.github.toserk1024.cfdash.data.model.AnalyticsBreakdown
import io.github.toserk1024.cfdash.data.model.AnalyticsSeriesPoint
import io.github.toserk1024.cfdash.data.model.AnalyticsSum
import io.github.toserk1024.cfdash.data.model.ZoneAnalyticsItem
import io.github.toserk1024.cfdash.ui.charts.alphaOf
import io.github.toserk1024.cfdash.ui.charts.applyAxes
import io.github.toserk1024.cfdash.ui.charts.applyBars
import io.github.toserk1024.cfdash.ui.charts.applyChartBase
import io.github.toserk1024.cfdash.ui.charts.applyDonut
import io.github.toserk1024.cfdash.ui.charts.applyLine
import io.github.toserk1024.cfdash.ui.charts.applySlices
import io.github.toserk1024.cfdash.ui.charts.mp
import io.github.toserk1024.cfdash.ui.theme.CloudflareOrange
import kotlin.math.roundToInt

/**
 * 统计页图表（MPAndroidChart 4.x 官方 Compose 模块）。
 *
 * 约定：
 * - 手势全部保留（缩放 / 拖拽 / 双击缩放 / 点击高亮），不做任何关闭；
 * - 图例统一由 Compose 侧渲染（[LegendRow]），图表内部图例关闭；
 * - 样式集中在 `ui/charts/MpChartStyle.kt`，颜色随 Material 3 色板与主题切换刷新。
 */

/** 维度分布色板（与 Cloudflare 橙主题协调的多色系） */
private val pieColors = listOf(
    Color(0xFFF6821F), // Cloudflare 橙
    Color(0xFF2D9CDB), // 蓝
    Color(0xFF27AE60), // 绿
    Color(0xFFEB5757), // 红
    Color(0xFF9B51E0), // 紫
    Color(0xFFF2994A), // 橙黄
    Color(0xFF56CCF2), // 浅蓝
    Color(0xFF6FCF97), // 浅绿
    Color(0xFFBB6BD9), // 浅紫
    Color(0xFFFFD166)  // 黄
)

/** MP 需要的 android 颜色（一次性转换） */
private val mpColors = pieColors.map { it.toArgb() }

/** 折线趋势图（请求数/威胁数/带宽/独立访客等单指标时间序列） */
@Composable
fun TrendLineChart(
    points: List<AnalyticsSeriesPoint>,
    valueSelector: (AnalyticsSeriesPoint) -> Long,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    lineColor: Color = CloudflareOrange,
    valueFormatter: (Long) -> String = ::formatCount
) {
    if (points.isEmpty()) {
        EmptyChartBox(modifier)
        return
    }
    // lambda 每次重组都是新实例，用 rememberUpdatedState 保证图表内部读到的永远是最新的
    val selector by rememberUpdatedState(valueSelector)
    val formatter by rememberUpdatedState(valueFormatter)
    val labels = remember(points) { points.map { it.label } }
    val values = points.map { selector(it).toFloat() }
    val color = lineColor.mp()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.mp()
    val gridColor = MaterialTheme.colorScheme.outlineVariant.mp()
    val data = remember(labels, values, color) {
        LineData(
            LineDataSet(
                values.mapIndexed { index, value -> Entry(index.toFloat(), value) },
                ""
            ).apply { applyLine(color) }
        )
    }
    LineChart(
        data = data,
        modifier = modifier.fillMaxWidth().height(height),
        animateChanges = true,
        marker = { entry, _ ->
            ChartTooltip(
                title = labels.getOrNull(entry.x.toInt()).orEmpty(),
                value = formatter(entry.y.toLong())
            )
        },
        setup = {
            applyChartBase(labelColor)
            applyAxes(labelColor, gridColor)
            axisLeft.axisMinimum = 0f
        },
        update = {
            applyChartBase(labelColor)
            applyAxes(labelColor, gridColor)
            axisLeft.axisMinimum = 0f
            axisLeft.valueFormatter = IAxisValueFormatter { value, _ -> formatter(value.toLong()) }
            xAxis.valueFormatter = IAxisValueFormatter { value, _ -> labels.getOrNull(value.toInt()).orEmpty() }
            xAxis.axisMinimum = -0.5f
            xAxis.axisMaximum = (labels.size - 1).toFloat() + 0.5f
            xAxis.granularity = 1f
            notifyDataSetChanged()
        }
    )
}

/** 维度分布环图（Top N + 其他归并，下方图例展示名称/值/占比） */
@Composable
fun BreakdownPieChart(
    items: List<AnalyticsBreakdown>,
    modifier: Modifier = Modifier,
    maxSlices: Int = 6
) {
    val chartItems = remember(items, maxSlices) {
        items.filter { it.value > 0 }.let { aggregateTop(it, maxSlices) }
    }
    if (chartItems.isEmpty()) {
        EmptyChartBox(modifier)
        return
    }
    val total = chartItems.sumOf { it.value }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.mp()
    val centerColor = MaterialTheme.colorScheme.onSurface.mp()
    val holeColor = MaterialTheme.colorScheme.surface.mp()
    val data = remember(chartItems) {
        PieData(
            PieDataSet(
                chartItems.mapIndexed { index, item -> PieEntry(item.value.toFloat(), item.name) },
                ""
            ).apply { applySlices(mpColors) }
        )
    }
    Column(modifier = modifier) {
        PieChart(
            data = data,
            modifier = Modifier.fillMaxWidth().height(200.dp),
            animateChanges = true,
            setup = {
                applyChartBase(labelColor)
                applyDonut(holeColor, centerColor, 12f)
            },
            update = {
                applyChartBase(labelColor)
                applyDonut(holeColor, centerColor, 12f)
                centerText = "总计\n${formatCount(total)}"
                notifyDataSetChanged()
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        chartItems.forEachIndexed { index, item ->
            LegendRow(
                color = pieColors[index % pieColors.size],
                name = item.name,
                value = item.value,
                total = total
            )
        }
    }
}

/** 域名请求量柱状图（账号级域名拆分，Top N + 其他归并） */
@Composable
fun ZoneBarChart(
    items: List<ZoneAnalyticsItem>,
    modifier: Modifier = Modifier,
    maxBars: Int = 8
) {
    val chartItems = remember(items, maxBars) {
        items.filter { it.sum.requests > 0 }.let { filtered ->
            val top = filtered.take(maxBars)
            if (filtered.size > maxBars) {
                top + ZoneAnalyticsItem(
                    zoneId = "",
                    zoneName = "其他",
                    sum = AnalyticsSum(requests = filtered.drop(maxBars).sumOf { it.sum.requests })
                )
            } else {
                top
            }
        }
    }
    if (chartItems.isEmpty()) {
        EmptyChartBox(modifier)
        return
    }
    val total = chartItems.sumOf { it.sum.requests }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.mp()
    val gridColor = MaterialTheme.colorScheme.outlineVariant.mp()
    val data = remember(chartItems) {
        BarData(
            BarDataSet(
                chartItems.mapIndexed { index, item ->
                    BarEntry(index.toFloat(), item.sum.requests.toFloat())
                },
                ""
            ).apply {
                applyBars(
                    chartItems.indices.map { index ->
                        val color = mpColors[index % mpColors.size]
                        Fill(color, alphaOf(color, 90))
                    }
                )
            }
        ).apply { barWidth = 0.55f }
    }
    Column(modifier = modifier) {
        BarChart(
            data = data,
            modifier = Modifier.fillMaxWidth().height(200.dp),
            animateChanges = true,
            marker = { entry, _ ->
                ChartTooltip(
                    title = chartItems.getOrNull(entry.x.toInt())?.zoneName.orEmpty(),
                    value = formatCount(entry.y.toLong())
                )
            },
            setup = {
                applyChartBase(labelColor)
                applyAxes(labelColor, gridColor)
                axisLeft.axisMinimum = 0f
            },
            update = {
                applyChartBase(labelColor)
                applyAxes(labelColor, gridColor)
                axisLeft.axisMinimum = 0f
                axisLeft.valueFormatter = IAxisValueFormatter { value, _ -> formatCount(value.toLong()) }
                // 横轴标签由下方图例列表承担，避免与图例重复
                xAxis.valueFormatter = IAxisValueFormatter { _, _ -> "" }
                xAxis.axisMinimum = -0.5f
                xAxis.axisMaximum = (chartItems.size - 1).toFloat() + 0.5f
                xAxis.granularity = 1f
                notifyDataSetChanged()
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        // 下方图例列表：像饼图图例一样逐行列出每个 host 的名称/请求量/占比
        chartItems.forEachIndexed { index, item ->
            LegendRow(
                color = pieColors[index % pieColors.size],
                name = item.zoneName,
                value = item.sum.requests,
                total = total
            )
        }
    }
}

/** 点击高亮时的浮层提示（Compose 侧渲染，由 MP 的 marker 槽位定位） */
@Composable
private fun ChartTooltip(title: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** 图例行：色块 + 名称 + 数值 + 占比 */
@Composable
private fun LegendRow(
    color: Color,
    name: String,
    value: Long,
    total: Long,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = formatCount(value),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (total > 0) "${(value * 100.0 / total).roundToInt()}%" else "0%",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 取 Top (max-1) + "其他"（剩余合并）；不超过 max 时原样返回 */
private fun aggregateTop(items: List<AnalyticsBreakdown>, max: Int): List<AnalyticsBreakdown> {
    if (items.size <= max) return items
    val top = items.take(max - 1)
    return top + AnalyticsBreakdown("其他", items.drop(max - 1).sumOf { it.value })
}

/** 无数据占位 */
@Composable
private fun EmptyChartBox(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().height(160.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "暂无数据",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}