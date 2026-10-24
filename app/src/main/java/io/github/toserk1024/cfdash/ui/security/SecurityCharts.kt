package io.github.toserk1024.cfdash.ui.security

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.mikephil.charting.compose.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IAxisValueFormatter
import io.github.toserk1024.cfdash.data.model.SecuritySegment
import io.github.toserk1024.cfdash.data.model.SecurityTrendSeries
import io.github.toserk1024.cfdash.ui.charts.applyAxes
import io.github.toserk1024.cfdash.ui.charts.applyChartBase
import io.github.toserk1024.cfdash.ui.charts.applyLine
import io.github.toserk1024.cfdash.ui.charts.mp
import io.github.toserk1024.cfdash.ui.charts.rememberChartPalette
import kotlin.math.roundToInt

/**
 * 安全页图表。
 *
 * - 概况：[SecurityOverviewBar]（Canvas 手绘 100% 堆叠条，圆角 + 2dp 间隙 + 点按气泡 + 300ms 入场动画，观感对齐 MP）
 * - 趋势：[SecurityTrendChart]（MP `LineChart` 多序列，样式复用统计页 MpChartStyle）
 */

/** 三段固定配色（对齐官网：已缓解=粉红、由 CF 提供=黄、由源站=蓝）；非分段时按色板顺序 */
private fun segmentColor(palette: List<Color>, index: Int, segmented: Boolean): Color {
    if (segmented) {
        return when (index) {
            0 -> palette[0]   // chart-1 浅红/粉
            1 -> palette[2]   // chart-3 黄
            else -> palette[5] // chart-6 浅蓝
        }
    }
    return palette[index % palette.size]
}

/** 100% 堆叠条 + 图例（点按段或图例显示该段明细） */
@Composable
fun SecurityOverviewBar(
    segments: List<SecuritySegment>,
    modifier: Modifier = Modifier,
    showLegend: Boolean = true,
    segmented: Boolean = true
) {
    val total = segments.sumOf { it.count }
    if (total <= 0) {
        EmptySecurityChart("暂无数据", modifier)
        return
    }
    val palette = rememberChartPalette()
    var selected by remember(segments) { mutableStateOf<Int?>(null) }
    val anim = remember { Animatable(0f) }
    // 数据变化时整条从左向右伸展（300ms），与 MP 的数据动画观感一致
    LaunchedEffect(segments) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
    }
    val currentSegments by rememberUpdatedState(segments)
    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .pointerInput(segments) {
                    detectTapGestures { pos ->
                        val gap = 2.dp.toPx()
                        val usable = size.width - gap * (currentSegments.size - 1).coerceAtLeast(0)
                        var acc = 0f
                        var hit: Int? = null
                        currentSegments.forEachIndexed { i, s ->
                            val w = usable * (s.count / total.toFloat())
                            if (pos.x >= acc && pos.x <= acc + w + gap / 2f) hit = i
                            acc += w + gap
                        }
                        selected = if (selected == hit) null else hit
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(30.dp)) {
                val gap = 2.dp.toPx()
                val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                val usable = size.width - gap * (segments.size - 1).coerceAtLeast(0)
                var x = 0f
                segments.forEachIndexed { index, seg ->
                    val w = usable * (seg.count / total.toFloat()) * anim.value
                    if (w > 0f) {
                        drawRoundRect(
                            color = segmentColor(palette, index, segmented),
                            topLeft = Offset(x, 0f),
                            size = Size(w.coerceAtLeast(if (anim.value > 0.02f) 2.dp.toPx() else 0f), size.height),
                            cornerRadius = radius
                        )
                        x += w + gap
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        if (showLegend) {
            val shown = selected?.let { listOf(it to segments[it]) } ?: segments.mapIndexed { i, s -> i to s }
            shown.forEach { (index, seg) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { selected = if (selected == index) null else index }
                        .background(
                            if (selected == index) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            else Color.Transparent
                        )
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(segmentColor(palette, index, segmented))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = seg.name,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatSecurityCount(seg.count),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "${seg.percent.roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/** 趋势折线（MP）：segmented=true 时用三段固定色，否则用彩色板顺序 */
@Composable
fun SecurityTrendChart(
    series: List<SecurityTrendSeries>,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    segmented: Boolean = false
) {
    val valid = series.filter { it.points.isNotEmpty() }
    if (valid.isEmpty()) {
        EmptySecurityChart("暂无数据", modifier)
        return
    }
    val palette = rememberChartPalette()
    val labels = remember(valid) {
        valid.flatMap { s -> s.points.map { it.label } }.distinct().sorted()
    }
    val colors = valid.indices.map { i -> segmentColor(palette, i, segmented) }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.mp()
    val gridColor = MaterialTheme.colorScheme.outlineVariant.mp()
    val data = remember(valid, labels, colors) {
        LineData(
            *valid.mapIndexed { index, s ->
                LineDataSet(
                    labels.mapIndexed { x, label ->
                        val v = s.points.firstOrNull { it.label == label }?.count ?: 0L
                        Entry(x.toFloat(), v.toFloat())
                    },
                    s.name
                ).apply { applyLine(colors[index].toArgb()) }
            }.toTypedArray()
        )
    }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val capacity = (maxWidth.value / 62f).toInt().coerceIn(2, labels.size.coerceAtLeast(2))
        LineChart(
            data = data,
            modifier = Modifier.fillMaxWidth().height(height),
            animateChanges = true,
            marker = { entry, _ ->
                val idx = entry.x.toInt()
                SecurityTooltip(
                    title = labels.getOrNull(idx).orEmpty(),
                    rows = valid.mapIndexed { i, s ->
                        s.name to formatSecurityCount(
                            s.points.firstOrNull { it.label == labels.getOrNull(idx) }?.count ?: 0L
                        )
                    }
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
                axisLeft.valueFormatter = IAxisValueFormatter { v, _ -> formatSecurityCount(v.toLong()) }
                xAxis.valueFormatter = IAxisValueFormatter { v, _ -> labels.getOrNull(v.toInt()).orEmpty() }
                xAxis.labelCount = capacity
                xAxis.axisMinimum = -0.5f
                xAxis.axisMaximum = (labels.size - 1).toFloat() + 0.5f
                notifyDataSetChanged()
            }
        )
    }
    Spacer(modifier = Modifier.height(6.dp))
    // 图例
    valid.forEachIndexed { index, s ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(colors[index])
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = s.name,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 折线浮层：时间 + 各序列数值 */
@Composable
private fun SecurityTooltip(title: String, rows: List<Pair<String, String>>) {
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            if (title.isNotBlank()) {
                Text(text = title, fontSize = 11.sp, maxLines = 1)
            }
            rows.forEach { (name, value) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = name, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** 空态占位 */
@Composable
fun EmptySecurityChart(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().height(140.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Top 统计卡：标题 + Top3 横条（占比） */
@Composable
fun SecurityTopStatCard(
    title: String,
    items: List<SecuritySegment>,
    modifier: Modifier = Modifier
) {
    val palette = rememberChartPalette()
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            if (items.isEmpty()) {
                Text(
                    "暂无数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                items.take(3).forEachIndexed { index, seg ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = seg.name.ifBlank { "（未知）" },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${seg.percent.roundToInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Canvas(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                            val ratio = (seg.percent / 100f).coerceIn(0f, 1f)
                            drawRoundRect(
                                color = palette[index % palette.size].copy(alpha = 0.9f),
                                size = Size(size.width * ratio, size.height),
                                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 安全页数字格式（与统计页 formatCount 一致：万 / 亿） */
internal fun formatSecurityCount(n: Long): String = when {
    n >= 100_000_000 -> String.format("%.1f亿", n / 100_000_000.0)
    n >= 10_000 -> String.format("%.1f万", n / 10_000.0)
    else -> n.toString()
}