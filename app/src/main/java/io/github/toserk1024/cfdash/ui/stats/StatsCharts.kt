package io.github.toserk1024.cfdash.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.marker.ColumnCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import com.patrykandpatrick.vico.compose.pie.PieChart
import com.patrykandpatrick.vico.compose.pie.PieChartHost
import com.patrykandpatrick.vico.compose.pie.data.PieChartModelProducer
import com.patrykandpatrick.vico.compose.pie.data.PieValueFormatter
import com.patrykandpatrick.vico.compose.pie.data.pieSeries
import com.patrykandpatrick.vico.compose.pie.rememberPieChart
import io.github.toserk1024.cfdash.data.model.AnalyticsBreakdown
import io.github.toserk1024.cfdash.data.model.AnalyticsSeriesPoint
import io.github.toserk1024.cfdash.data.model.AnalyticsSum
import io.github.toserk1024.cfdash.data.model.ZoneAnalyticsItem
import io.github.toserk1024.cfdash.ui.theme.CloudflareOrange
import kotlin.math.roundToInt

/** x 轴分类标签（通过 extras 与模型同步，供 bottomAxis valueFormatter 读取） */
private val labelListKey = ExtraStore.Key<List<String>>()

/** 维度分布饼图色板（与 Cloudflare 橙主题协调的多色系） */
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
    val modelProducer = remember { CartesianChartModelProducer() }
    // valueSelector/valueFormatter 是 lambda（每次重组新实例），用 rememberUpdatedState 保证 LaunchedEffect 仅在数据变化时触发
    val currentSelector by rememberUpdatedState(valueSelector)
    val currentFormatter by rememberUpdatedState(valueFormatter)
    LaunchedEffect(points) {
        modelProducer.runTransaction {
            lineModel { series(y = points.map { currentSelector(it).toFloat() }) }
            extras { it[labelListKey] = points.map { p -> p.label } }
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.Line(
                        LineCartesianLayer.LineFill.single(Fill(lineColor))
                    )
                )
            ),
            startAxis = VerticalAxis.rememberStart(
                valueFormatter = CartesianValueFormatter { _, y, _ -> currentFormatter(y.toLong()) }
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = CartesianValueFormatter { context, x, _ ->
                    context.model.extraStore[labelListKey].getOrNull(x.toInt()) ?: ""
                }
            ),
            // 点击拐点：显示该点时间与对应数据量
            marker = rememberDefaultCartesianMarker(
                label = rememberTextComponent(
                    style = TextStyle(color = Color.White, fontSize = 12.sp, background = Color(0xCC000000))
                ),
                valueFormatter = remember {
                    DefaultCartesianMarker.ValueFormatter { context, targets ->
                        val target = targets.firstOrNull() ?: return@ValueFormatter ""
                        val xLabel = context.model.extraStore[labelListKey].getOrNull(target.x.toInt()) ?: ""
                        val yText = (target as? LineCartesianLayerMarkerTarget)
                            ?.points.firstOrNull()?.entry?.y?.toLong()?.let { currentFormatter(it) } ?: ""
                        if (xLabel.isNotBlank()) "$xLabel\n$yText" else yText
                    }
                }
            )
        ),
        modelProducer = modelProducer,
        modifier = modifier.height(height)
    )
}

/** 维度分布饼图（Top N + 其他归并，下方图例展示名称/值/占比） */
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
    val modelProducer = remember { PieChartModelProducer() }
    LaunchedEffect(chartItems) {
        modelProducer.runTransaction {
            pieSeries { series(*chartItems.map { it.value.toFloat() }.toTypedArray()) }
        }
    }
    val total = chartItems.sumOf { it.value }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    Column(modifier = modifier) {
        PieChartHost(
            chart = rememberPieChart(
                sliceProvider = PieChart.SliceProvider.series(
                    chartItems.indices.map { i ->
                        PieChart.Slice(fill = Fill(pieColors[i % pieColors.size]))
                    }
                ),
                valueFormatter = PieValueFormatter { _, value, _ -> "${value.roundToInt()}%" }
            ),
            modelProducer = modelProducer,
            modifier = Modifier.fillMaxWidth().height(200.dp)
        )
        // 选中项详情：点击图例查看该标签对应的具体数量与占比
        selectedIndex.takeIf { it in chartItems.indices }?.let { i ->
            val item = chartItems[i]
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${item.name}：${formatCount(item.value)} · ${
                    if (total > 0) "${(item.value * 100.0 / total).roundToInt()}%" else "0%"
                }",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = pieColors[i % pieColors.size]
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        chartItems.forEachIndexed { index, item ->
            LegendRow(
                color = pieColors[index % pieColors.size],
                name = item.name,
                value = item.value,
                total = total,
                selected = index == selectedIndex,
                onClick = {
                    selectedIndex = if (selectedIndex == index) -1 else index
                }
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
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(chartItems) {
        modelProducer.runTransaction {
            columnModel { series(y = chartItems.map { it.sum.requests.toFloat() }) }
            extras { it[labelListKey] = chartItems.map { item -> item.zoneName } }
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                ColumnCartesianLayer.ColumnProvider.series(
                    rememberLineComponent(Fill(CloudflareOrange), 16.dp)
                )
            ),
            startAxis = VerticalAxis.rememberStart(
                valueFormatter = CartesianValueFormatter { _, y, _ -> formatCount(y.toLong()) }
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = CartesianValueFormatter { context, x, _ ->
                    context.model.extraStore[labelListKey].getOrNull(x.toInt()) ?: ""
                }
            ),
            // 点击柱子：显示该 host 与请求量
            marker = rememberDefaultCartesianMarker(
                label = rememberTextComponent(
                    style = TextStyle(color = Color.White, fontSize = 12.sp, background = Color(0xCC000000))
                ),
                valueFormatter = remember {
                    DefaultCartesianMarker.ValueFormatter { context, targets ->
                        val target = targets.firstOrNull() ?: return@ValueFormatter ""
                        val xLabel = context.model.extraStore[labelListKey].getOrNull(target.x.toInt()) ?: ""
                        val yText = (target as? ColumnCartesianLayerMarkerTarget)
                            ?.columns.firstOrNull()?.entry?.y?.toLong()?.let { formatCount(it) } ?: ""
                        if (xLabel.isNotBlank()) "$xLabel\n$yText" else yText
                    }
                }
            )
        ),
        modelProducer = modelProducer,
        modifier = modifier.height(200.dp)
    )
}

/** 图例行：色块 + 名称 + 数值 + 占比；可点击选中（高亮背景，配合详情行） */
@Composable
private fun LegendRow(
    color: Color,
    name: String,
    value: Long,
    total: Long,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) color.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick)
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