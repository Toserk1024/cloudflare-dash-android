package io.github.toserk1024.cfdash.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.CardDefaults
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
import io.github.toserk1024.cfdash.ui.charts.rememberChartPalette
import kotlin.math.roundToInt

/**
 * 统计页图表（MPAndroidChart 4.x 官方 Compose 模块）。
 *
 * 约定：
 * - 手势：只允许 X 轴缩放（Y 轴锁定），拖拽 / 双击缩放 / 点击高亮保留；
 * - X 轴标签数量按「屏幕可容纳量」动态计算，缩放平移时由库按当前可视范围重算，永不折叠；
 * - 色板：图例顺序 = chart-1..10（跨图表固定）；折线用主题 primary，环图/柱状用 [rememberChartPalette]；
 * - 环图：无圆角扇区 + 2dp 细缝 + 小扇区保底 2°；分类超过 6 类合并「其他」；
 * - 图例统一由 Compose 侧渲染（[LegendRow]），图表内部图例关闭。
 */

/** X 轴短标签：24h 的 "MM-dd HH时" → "HH时"（省空间），其余保持 "MM-dd" */
private fun shortAxisLabel(label: String): String {
    val space = label.lastIndexOf(' ')
    return if (space > 0 && label.endsWith("时")) label.substring(space + 1) else label
}

/** 折线趋势图（请求数/威胁数/带宽等单指标时间序列） */
@Composable
fun TrendLineChart(
    points: List<AnalyticsSeriesPoint>,
    valueSelector: (AnalyticsSeriesPoint) -> Long,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    lineColor: Color? = null,
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
    val shortLabels = remember(labels) { labels.map { shortAxisLabel(it) } }
    val values = points.map { selector(it).toFloat() }
    val stroke = lineColor ?: MaterialTheme.colorScheme.primary
    val color = stroke.mp()
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
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // 严格容量估算：中文≈11dp/字、数字字母≈6.8dp/字，扣掉 Y 轴与左右留白 48dp，
        // 再留 10% 安全余量，宁可少显示几个也不要标签贴在一起
        val labelWidthDp = shortLabels.maxOfOrNull { label ->
            label.sumOf { ch -> if (ch.code > 0x2E80) 11.0 else 6.8 }
        } ?: 24.0
        val usableWidthDp = (maxWidth.value - 48f).coerceAtLeast(60f)
        val capacity = ((usableWidthDp / (labelWidthDp + 24.0)) * 0.9).toInt().coerceAtLeast(2)
        val maxLabels = capacity.coerceIn(2, labels.size.coerceAtLeast(2))
        LineChart(
            data = data,
            modifier = Modifier.fillMaxWidth().height(height),
            animateChanges = true,
            marker = { entry, _ ->
                // 浮层保留完整时间（X 轴标签已缩短）
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
                xAxis.valueFormatter = IAxisValueFormatter { value, _ -> shortLabels.getOrNull(value.toInt()).orEmpty() }
                xAxis.labelCount = maxLabels
                xAxis.axisMinimum = -0.5f
                xAxis.axisMaximum = (labels.size - 1).toFloat() + 0.5f
                notifyDataSetChanged()
            }
        )
    }
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
    val palette = rememberChartPalette()
    val sliceColors = remember(palette) { palette.map { it.toArgb() } }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.mp()
    val centerColor = MaterialTheme.colorScheme.onSurface.mp()
    // 孔色直接取 Card 的默认容器色，保证与卡片底色完全一致（中间「总计」不再显透）
    val holeColor = CardDefaults.cardColors().containerColor.mp()
    val data = remember(chartItems, sliceColors) {
        PieData(
            PieDataSet(
                chartItems.mapIndexed { index, item -> PieEntry(item.value.toFloat(), item.name) },
                ""
            ).apply { applySlices(sliceColors) }
        )
    }
    Column(modifier = modifier) {
        PieChart(
            data = data,
            modifier = Modifier.fillMaxWidth().height(200.dp),
            animateChanges = true,
            marker = { entry, _ ->
                val name = (entry as? PieEntry<*>)?.label.orEmpty()
                val percent = if (total > 0) {
                    "${(entry.y * 100.0 / total).roundToInt()}%"
                } else {
                    "0%"
                }
                ChartTooltip(
                    title = name,
                    value = formatCount(entry.y.toLong()),
                    percent = percent
                )
            },
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
                color = palette[index % palette.size],
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
    val palette = rememberChartPalette()
    val barColors = remember(palette) { palette.map { it.toArgb() } }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.mp()
    val gridColor = MaterialTheme.colorScheme.outlineVariant.mp()
    val data = remember(chartItems, barColors) {
        BarData(
            BarDataSet(
                chartItems.mapIndexed { index, item ->
                    BarEntry(index.toFloat(), item.sum.requests.toFloat())
                },
                ""
            ).apply {
                applyBars(
                    chartItems.indices.map { index ->
                        val color = barColors[index % barColors.size]
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
                xAxis.isDrawLabelsEnabled = false
                xAxis.axisMinimum = -0.5f
                xAxis.axisMaximum = (chartItems.size - 1).toFloat() + 0.5f
                notifyDataSetChanged()
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        // 下方图例列表：逐行列出每个 host 的名称/请求量/占比
        chartItems.forEachIndexed { index, item ->
            LegendRow(
                color = palette[index % palette.size],
                name = item.zoneName,
                value = item.sum.requests,
                total = total
            )
        }
    }
}

/**
 * 点击浮层。
 * - 折线 / 柱状：上下两行（名称 + 数值）
 * - 饼图（[percent] 非空）：名称在左（大号粗体），右侧两行「百分比在上、数值在下」，整体垂直居中
 */
@Composable
private fun ChartTooltip(title: String, value: String, percent: String? = null) {
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 2.dp
    ) {
        if (percent == null) {
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
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = percent, fontSize = 13.sp)
                    Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
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