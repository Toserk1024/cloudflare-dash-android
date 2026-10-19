# TODO

> 工作规范：**新增/修改功能先写在这里攒批，等用户手动触发后**再按 计划 → 确认 → 实施 → 审查 → changelog → 推送 流程执行。
> 不要直接改代码提交。

## 待办需求（攒批中，未实施）

### [待决策] 安全 Tab 数据与 Cloudflare 官网不一致（重做统计口径）

- **来源**：用户反馈（2026-10-18）。此前已多次提出该需求，上代模型声称已实现并写入 changelog（含「修复缓解始终为 0」），todo 相应条目被移除，但**实际数据始终与官网对不上**，需求未真正达成。
- **现象**：安全概况占比、趋势曲线、分组分布、日志顺序与内容均与官网 Security → Overview 不一致。
- **已定位根因（代码审查确认，非调参可解）**：
  1. **量纲混用（致命）**：`firewallEventsAdaptive` 的事件条数（limit 1000/2000，被截断）与 `httpRequests1hGroups` 的请求数放入同一 100% 堆叠条相减 → 缓解恒≈0%、回源虚高（`SecurityAnalytics.kt:317-333`）。
  2. **自创指标**：「回源 / 命中 / 缓解」三段分解在官网并不存在，对照必然对不上。
  3. **统计对象错**：分组视图用 `httpRequestsAdaptiveGroups`（全站 HTTP 流量分布），而非「被缓解事件」分布（`SecurityAnalytics.kt:232-240`）。
  4. **筛选器失效**：`groupBy = ALL` 时 `overviewQuery` / `trendQuery` 完全不拼 filter（`SecurityAnalytics.kt:214-220、248-254`）。
  5. **粒度声明与实现不符**：`trendDimension` 定义了 5min / 15min，但 ALL 分支固定查 1hGroups（`SecurityAnalytics.kt:20-26 vs 250-253`）。
  6. **日志无排序**：`firewallEventsAdaptive(limit:200)` 无 `orderBy`（`SecurityAnalytics.kt:285`）→ 顺序不确定，「最新在前」不成立。
- **待用户决策的方案**：
  - A（推荐）真对齐官网：废弃「回源」概念，改为 总请求 / 已缓解（含占比） / 缓解趋势 / 缓解动作与来源 TopN / Top 国家，缓解改用**聚合**而非明细。
  - B 保留三段但自洽：缓存命中 / 未命中回源 / 被缓解，三者互斥、同量纲、同数据源。
  - C 最小刀：仅去除明细截断，其余不动（仍对不上官网）。
- **前置验证（禁止猜字段）**：① `httpRequestsAdaptiveGroups` 是否支持 `securityAction` / `securitySource` 维度；② `firewallEventsAdaptiveGroups` 聚合版是否真不可访问；③ `firewallEventsAdaptive` 是否支持 `count` 聚合。
- **状态**：等待用户指示，未实施。

### [进行中] 图表库迁移至 MPAndroidChart 4.x（官方 Compose 模块）

- **来源**：用户反馈（2026-10-18）。用户认为 Vico 不足以表达 CDN 场景，提出换库；经代码审查，**主因并非库能力不足，而是上一代只用到了最基础的三个能力**。
- **决策（2026-10-19）**：采用 **MPAndroidChart 4.0.1 官方 Compose 模块**（不自研桥接），目标是观感接近 Cloudflare 面板。
- **审查结论**：
  - 项目实际用到：单序列折线（`lineModel`）、单序列柱状（`columnModel`）、饼图（`pieSeries`）+ 自定义 marker。
  - Vico 3.x 已具备但**未被使用**：堆叠柱/堆叠面积（`rememberStackedCartesianLayer`）、拖拽平移与双指缩放（`rememberVicoScrollState` / `Zoom`）、左右双 Y 轴、区间高亮、多序列联动 marker、负值/对数轴。
  - 安全页的「100% 堆叠条」是 **Compose Canvas 手绘矩形**，完全未走图表库（`SecurityCharts.kt:75-112`）。
- **CDN 场景缺口清单（①~⑩）**：① 多序列同图；② 双 Y 轴；③ 堆叠面积/柱；④ 时间轴缩放+平移+区间选择；⑤ 十字准线+多点联合 tooltip；⑥ 大点数（30d 多维度数千点）流畅度；⑦ 平滑/阶梯曲线；⑧ 对数轴；⑨ 热力图/地理/桑基等高级图型；⑩ 图表联动（选时间窗口联动全页）。
- **候选路线（待用户决策）**：
  - **A（推荐先验证）** Vico 深挖：迁移成本≈0，能力覆盖 ①~⑧。
  - **B** MPAndroidChart：交互最成熟，但官方仓库 2022 年后基本停更 + Compose 集成割裂。
  - **C** ECharts（WebView 桥接）：能力上限最高（含热力图/地理/桑基/dataZoom/brush），代价是 WebView 体积、内存与手势冲突。
  - **D** SKIA/Canvas 自研：无上限但等于自养图表引擎。
- **已定方案与进度（2026-10-19）**：
  - 选型：**B 方案 = MPAndroidChart 4.0.1 官方 Compose 模块**（`MPChartCompose`，仅 JitPack 发布；实测 AAR `minCompileSdk=37`）。底层仍是 `AndroidView` 宿主，但桥接由官方提供，不造轮子。
  - 用户决策：① 工具链走最小升级（B1）；② 图表手势**全部保留**（缩放/拖拽/双击/点击 tooltip）；③ **安全页整体不动**（Vico 折线 + Canvas 堆叠条保持原样）。
  - **批次 1（已完成，等 CI 验证）**：AGP 9.0.0→9.2.1、Gradle wrapper 9.1.0→9.4.1、compileSdk 36→37、Compose BOM 2026.06.01→2026.09.00、新增 JitPack 仓库、CI SDK 组件改 `platforms;android-37.0` / `build-tools;37.0.0`（含 release.yml 的 aapt 路径）。**注意：SDK 37 平台包名带小版本号（android-37.0 / 37.1 / 37.2），不存在 `platforms;android-37`。**
  - **批次 2（待实施）**：统计页 3 个图表（`TrendLineChart` / `BreakdownPieChart` / `ZoneBarChart`）迁移到 MP；新增 `ui/charts/MpChartStyle.kt` 做主题映射；Vico 依赖保留（安全页在用）。
  - **已知副作用（用户已接受）**：图表区域内纵向滑动不滚动页面、该区域下拉刷新不触发。
- **状态**：批次 1 已实施并推送，待用户实机验证零回归；批次 2 等用户指令。

## Pending 队列（用户反馈待修复/优化，先不实施）

（当前无待修复项，新反馈继续在此追加）