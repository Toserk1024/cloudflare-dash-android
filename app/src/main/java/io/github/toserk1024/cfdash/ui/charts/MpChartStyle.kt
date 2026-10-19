package io.github.toserk1024.cfdash.ui.charts

import android.graphics.drawable.GradientDrawable
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.github.mikephil.charting.charts.BarLineChartBase
import com.github.mikephil.charting.charts.Chart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.utils.Fill

/**
 * MPAndroidChart 4.x 样式映射层。
 *
 * 只做「把 Material 3 色板 + Cloudflare 面板观感」翻译成 MP 的属性，不含任何图表逻辑；
 * 图表能力（缩放、拖拽、双击缩放、点击高亮、动画）全部使用库的默认行为，**不关闭**。
 */

/** Compose [Color] → MP 使用的 `android.graphics` 颜色 */
internal fun Color.mp(): Int = toArgb()

/** 颜色加透明度（0~255） */
internal fun alphaOf(color: Int, alpha: Int): Int =
    (color and 0x00FFFFFF) or ((alpha and 0xFF) shl 24)

/** 通用基调：去掉描述与图例（图例由 Compose 侧渲染）、中文空数据文案 */
internal fun Chart<*>.applyChartBase(labelColor: Int) {
    description.isEnabled = false
    legend.isEnabled = false
    noDataText = "暂无数据"
    noDataTextColor = labelColor
}

/** 坐标轴：无轴线、无竖网格、淡横向网格、10sp 标签 */
internal fun BarLineChartBase<*>.applyAxes(labelColor: Int, gridLineColor: Int) {
    // 手势策略：双指缩放只作用 X 轴（Y 轴缩放对趋势/柱状无意义），拖拽与双击缩放保留
    isScaleEnabled = true
    isScaleXEnabled = true
    isScaleYEnabled = false
    isDragEnabled = true
    isDoubleTapToZoomEnabled = true
    // 标签只落在整数序号上，避免缩放后出现非数据点的错位时间
    setExtraOffsets(2f, 8f, 8f, 4f)
    xAxis.position = XAxis.XAxisPosition.BOTTOM
    xAxis.apply {
        isDrawAxisLineEnabled = false
        isDrawGridLinesEnabled = false
        textColor = labelColor
        textSize = 10f
        yOffset = 8f
        granularity = 1f
        isAvoidFirstLastClippingEnabled = true
    }
    axisLeft.apply {
        isDrawAxisLineEnabled = false
        isDrawGridLinesEnabled = true
        // 注意：参数名不能与属性 gridColor 同名，否则 apply 内左侧会解析成参数（val 不可赋值）
        gridColor = gridLineColor
        gridLineWidth = 1f
        textColor = labelColor
        textSize = 10f
        xOffset = 10f
    }
    axisRight.isEnabled = false
}

/** 折线：平滑曲线 + 渐变面积填充 + 高亮点圆环（手势保持库默认：缩放/拖拽/双击/点击全开） */
internal fun LineDataSet<*>.applyLine(lineColor: Int) {
    mode = LineDataSet.Mode.CUBIC_BEZIER
    cubicIntensity = 0.18f
    color = lineColor
    lineWidth = 2.5f
    isDrawCirclesEnabled = false
    isDrawValuesEnabled = false
    isDrawFilledEnabled = true
    fillDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(alphaOf(lineColor, 110), alphaOf(lineColor, 0))
    )
    highlightColor = lineColor
    highlightLineWidth = 1f
    enableDashedHighlightLine(8f, 8f, 0f)
    isHorizontalHighlightIndicatorEnabled = false
    isDrawHighlightCircleEnabled = true
}

/** 环形饼图：无圆角扇区 + 2dp 细缝 + 小扇区保底 2°（图例由 Compose 侧渲染） */
internal fun PieChart.applyDonut(holeColor: Int, centerColor: Int, centerTextSizeSp: Float) {
    isDrawEntryLabelsEnabled = false
    holeRadius = 66f
    transparentCircleRadius = 0f
    this.holeColor = holeColor
    // 圆角会让每个扇区两端起弧（观感像「(=」），且把占比极小扇区压到看不见，故关闭
    isDrawRoundedSlicesEnabled = false
    // 小占比扇区保底 2°，避免几乎不可见、也无法点击
    minAngleForSlices = 2f
    centerTextColor = centerColor
    centerTextSize = centerTextSizeSp
    setExtraOffsets(0f, 0f, 0f, 0f)
}

/** 扇区配色与间隔 */
internal fun PieDataSet<*>.applySlices(sliceColors: List<Int>) {
    sliceSpace = 2f
    colors = sliceColors.toMutableList()
    isDrawValuesEnabled = false
}

/** 圆角柱 + 顶部实色到底部渐隐的填充 */
internal fun BarDataSet<*>.applyBars(fills: List<Fill>) {
    barCornerRadius = 7f
    isDrawValuesEnabled = false
    highlightAlpha = 0
    this.fills = fills.toMutableList()
}

/**
 * 图表彩色板（维度分布用）：固定的 10 色统计色序（chart-1 ~ chart-10）。
 *
 * 这组浅色系专为统计数据设计：亮度接近、色相互相区分，深浅背景都可用，
 * 不再与主题色混合（混合会发灰、降低区分度）。折线图不使用它（走主题 primary）。
 */
@Composable
internal fun rememberChartPalette(): List<Color> = baseChartColors

/** chart-1 ~ chart-10（统计色板，用户指定；Light / Dark 共用同一套） */
private val baseChartColors = listOf(
    Color(0xFFF27A72), // chart-1 浅红
    Color(0xFFF2994A), // chart-2 浅橙
    Color(0xFFFFD166), // chart-3 黄
    Color(0xFFC8E06E), // chart-4 青柠
    Color(0xFF6FCF97), // chart-5 浅绿
    Color(0xFF5FD3C8), // chart-6 青绿
    Color(0xFF56CCF2), // chart-7 浅蓝
    Color(0xFF7C8CE8), // chart-8 靛蓝
    Color(0xFFBB6BD9), // chart-9 浅紫
    Color(0xFFF08CB4)  // chart-10 浅粉
)