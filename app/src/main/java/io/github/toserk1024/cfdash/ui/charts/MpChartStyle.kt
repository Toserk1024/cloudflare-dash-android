package io.github.toserk1024.cfdash.ui.charts

import android.graphics.drawable.GradientDrawable
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

/** 坐标轴：无轴线、无竖网格、淡横向网格、10sp 标签（Cloudflare 面板风格） */
internal fun BarLineChartBase<*>.applyAxes(labelColor: Int, gridColor: Int) {
    setExtraOffsets(2f, 8f, 8f, 4f)
    xAxis.position = XAxis.XAxisPosition.BOTTOM
    xAxis.apply {
        isDrawAxisLineEnabled = false
        isDrawGridLinesEnabled = false
        textColor = labelColor
        textSize = 10f
        yOffset = 8f
        isAvoidFirstLastClippingEnabled = true
    }
    axisLeft.apply {
        isDrawAxisLineEnabled = false
        isDrawGridLinesEnabled = true
        gridColor = gridColor
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

/** 环形饼图：圆角扇区、隐藏扇区标签与数值（图例由 Compose 侧渲染） */
internal fun PieChart.applyDonut(holeColor: Int, centerColor: Int, centerTextSizeSp: Float) {
    isDrawEntryLabelsEnabled = false
    holeRadius = 68f
    transparentCircleRadius = 0f
    this.holeColor = holeColor
    isDrawRoundedSlicesEnabled = true
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
