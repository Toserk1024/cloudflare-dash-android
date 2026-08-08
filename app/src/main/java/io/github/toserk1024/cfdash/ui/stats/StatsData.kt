package io.github.toserk1024.cfdash.ui.stats

import io.github.toserk1024.cfdash.data.model.AnalyticsBreakdown
import io.github.toserk1024.cfdash.data.model.AnalyticsSeries
import io.github.toserk1024.cfdash.data.model.AnalyticsSum
import io.github.toserk1024.cfdash.data.model.ZoneAnalyticsItem

/**
 * 统计数据展示数据（账号级 / 域名级复用）：
 * summary 汇总 + series 时间趋势 + country/status/cache 维度分布 + zoneBreakdown 域名拆分（仅账号级）。
 * 各字段 null 表示未加载或加载失败（单项失败不阻塞其他）。
 */
data class StatsData(
    val summary: AnalyticsSum? = null,
    val series: AnalyticsSeries? = null,
    val country: List<AnalyticsBreakdown>? = null,
    val status: List<AnalyticsBreakdown>? = null,
    val cache: List<AnalyticsBreakdown>? = null,
    val zoneBreakdown: List<ZoneAnalyticsItem>? = null
)
