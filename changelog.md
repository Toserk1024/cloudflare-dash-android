# Changelog

## 2026.08.15

### 新增缓存清除功能 + 切换账号重载体验优化

- **新增「缓存」清除功能（侧边栏 → 缓存清除页）**：侧边栏新增「缓存」菜单项（AutoDelete 图标），进入独立缓存清除页 `CacheScreen`。支持 Cloudflare `POST /zones/{id}/purge_cache` 的 **5 种清除方式**（单选互斥）：① 清除所有（`purge_everything:true`）；② 按 URL 清除（`files`，精确匹配）；③ 按主机名清除（`hosts`）；④ 按标签清除（`tags`，Cache-Tag）；⑤ 按前缀清除（`prefixes`）。页面含域名选择器（复用域名列表）、方式说明、按行输入内容（"清除所有"无需输入）、二次确认对话框、清除结果/错误提示（`CacheScreen.kt` / `CacheViewModel.kt`）
- **数据层扩展**：`CloudflareApi` 新增 `PURGE_CACHE` 端点；`CloudflareRepository.purgeCache(zoneId, purgeEverything, files, hosts, tags, prefixes)` 用 `buildJsonObject` 动态构造互斥请求体（避免 null 字段序列化），经 `client.requestRaw` POST 调用（`CloudflareRepository.kt` / `CloudflareApi.kt`）
- **切换账号重载遮罩全不透明（todo）**：切换账号/退出到剩余账号时的全屏加载遮罩由半透明黑（alpha 0.5）改为**纯不透明黑**（`Color.Black`），完全遮住旧界面，避免闪现旧账号内容（`MainActivity.kt`）
- **切换账号重载后保持所在页面（todo）**：`MainActivity` 新增 `currentTab` 记录当前 Home Tab，重建导航栈后通过 `initialTab`/`onTabChange` 透传（`AppNavHost` → `HomeScreen`），`HomeScreen` 初始 Tab 用 `initialTab`，切换 Tab 时回调 `onTabChange`；账号切换/重建后停留在原所在 Tab 而非跳回「域名」（`MainActivity.kt` / `AppNavHost.kt` / `HomeScreen.kt`）

### 缓存清除改为侧边栏 Tab + 侧边栏顺序调整

- **缓存清除改为侧边栏内嵌 Tab（todo）**：移除独立缓存清除路由（Routes.CACHE / AppNavHost composable / onOpenCache），改为 HomeScreen 第 4 个常驻 Tab（侧边栏「缓存」菜单项直接切换）；`CacheScreen` 重构为无 Scaffold 的 `CacheContent` 供 Tab 内嵌（域名选择/方式/输入/按钮/对话框逻辑不变）（`CacheScreen.kt` / `HomeScreen.kt` / `AppNavHost.kt` / `Routes.kt`）
- **清除方式整行点击选择（todo）**：缓存清除方式列表由仅点候选框选中改为**整行（Row + clickable）点击即可选择**（`CacheScreen.kt`）
- **侧边栏菜单顺序调整（todo）**：「我的」移到「缓存」下方，顺序为 域名 / DNS / 统计数据 / 缓存 / 我的（索引 0/1/2/3/4）（`HomeScreen.kt`）

### 修复缓存/我的 Tab 空白（位掩码错误）

- **根因**：`HomeScreen` 缓存 Tab（索引 3）判断位误用 `0b10000`（位 4）、我的 Tab（索引 4）判断位误用 `0b100000`（位 5），而 `visitedMask` 实际用 `1 shl selectedTab` 记录（缓存=位 3=`0b1000`、我的=位 4=`0b10000`），位不匹配导致条件恒假 → 缓存/我的两页始终空白
- **修复**：缓存 Tab 判断位改为 `0b1000`、我的 Tab 判断位改为 `0b10000`，与 visitedMask 位对齐（`HomeScreen.kt`）

## 2026.08.10

### 修复切换账号后页面不自动重载 + 移除批量操作取消按钮

- **修复切换账号后页面不自动重载（pending）**：根因是 `MainActivity.onUserSwitched` 仅 `homeKey++` 触发 `HomeViewModel.loadUser()`（只刷新"我的"用户信息），而域名 / DNS / 统计等 ViewModel 作用域在 NavBackStackEntry、常驻组合不重建，仍展示旧账号缓存数据。修复：`MainActivity.MainScreen` 引入 `navResetKey`，用 `key(navResetKey)` 包裹 NavController + NavHost——切换账号 / 退出到剩余账号时 `navResetKey++` **整体重建导航栈（重新执行启动流程）**，所有 ViewModel 以新激活账号从空态重新加载；同时新增**全屏加载遮罩**（`switching` + 半透明黑底 `CircularProgressIndicator` 覆盖整个 MainActivity，切换完成后自动关闭），避免切换瞬间闪现旧账号内容（`MainActivity.kt`）
- **移除 DNS 批量操作栏的"取消"按钮（todo）**：批量栏「已选 X 条」后的「取消」按钮（仅清空选中、保留批量模式）冗余，删除；退出批量模式仍由顶栏 checklist 图标控制（`DnsRecordsContent.kt`）

## 2026.08.09

### 新增 GitHub Release 发布 Action + changelog 规范化 + security-crypto 回滚

- **新增 `.github/workflows/release.yml`（手动触发）**：构建方式与 `build.yml` 一致（release + R8 + 正式签名），Release 标题 = `v` + 日期（去掉 `_序号`）；**Release Notes 自动生成**——先 `gh release list` 获取上一个 release 的 tag，再从 `changelog.md` 提取"上次发布日期之前（更新）"的所有子标题（`###`）作为更新内容（**last release 机制，覆盖跨多日的新增更新，保证多日版本发布**），并附 changelog 链接；首次发布提取全部子标题
- **agent.md**：新增 §6.2 GitHub Release 发布说明（含 Release Notes 自动提取约定）
- **changelog.md 规范化**：按日期分组（`##日期`）+ 子标题（`###`），新在前旧在后，v1~v1.5 最老移至末尾；日期标题统一**点号格式**（`##2026.08.09`，与 versionName 日期一致）保证 awk 可精确解析
- **security-crypto 回滚至 1.1.0-alpha06**：1.1.0 整库被官方弃用（无库内替代 API），回滚到原版本

### 构建修复：android-37 平台未发布，回退 core/lifecycle（保留其余依赖升级）

- 尝试升级 AGP 9.1.0 + compileSdk 37 以适配 core-ktx 1.19 / lifecycle 2.11，但 `platforms;android-37` 在 sdkmanager 中不存在（Google 尚未发布 API 37）→ **回退**：core-ktx **1.18.0**、lifecycle **2.10.0**、AGP **9.0.0**、compileSdk **36**、CI 保持 android-36；**其余升级保留**（compose-bom 2026.06.01、navigation 2.9.8、activity 1.13.0、coroutines 1.11.0、serialization 1.11.0、security-crypto 1.1.0）

### 依赖版本更新 + DNS 编辑页名称域名后缀判断

- **依赖版本更新**（9 项）：`security-crypto` 1.1.0-alpha06→**1.1.0**（脱离 alpha）、`core-ktx` 1.10.1→1.19.0、`lifecycle-runtime-ktx` 2.6.1→2.11.0、`lifecycle-viewmodel-compose` 2.8.7→2.11.0、`activity-compose` 1.8.0→1.13.0、`compose-bom` 2026.01.01→2026.06.01、`navigation-compose` 2.8.5→2.9.8、`kotlinx-serialization-json` 1.7.3→1.11.0、`kotlinx-coroutines-android` 1.9.0→1.11.0（AGP/Kotlin/okhttp/vico 大版本未动）
- **DNS 编辑页名称域名后缀判断**：顶部说明文案的"[名称]"智能展示——`@`→域名本身（根域不加点）；未以当前域名结尾自动补".域名"（如 www→www.a.com）；已以域名结尾直接显示（如 www.a.com）；名称等于域名本身直接显示。为支持此功能，将 `zoneName` 沿调用链传入编辑页（`Routes` / `AppNavHost` / `HomeScreen` / `DnsRecordsScreen` / `DnsRecordEditScreen`）

### 修复退出登录不跳转 bug

- **根因**：登录成功后 `MainActivity.loggedIn` 从未更新为 true（始终为启动时初始值），导致单用户退出时 `loggedIn` 无变化、`LaunchedEffect` 不触发导航（表现为"账户已删但界面没反应、不返回登录页"）
- **修复**：`AppNavHost` 新增 `onLoggedIn` 回调，`OnboardingScreen` 登录成功后调用；`MainActivity` 传 `onLoggedIn = { loggedIn = true }` 同步登录态。修复后：**单用户退出**→删除账户并跳转登录页；**多用户退出当前**→`deleteUser` 自动切到剩余有效账户 + `homeKey++` 重载数据（`MainActivity.kt` / `AppNavHost.kt`）

### 免责声明页优化：状态持久化 + 排版重构 + 启动逻辑

- **免责声明状态持久化**：`TokenStore` 新增 `isDisclaimerAccepted()` / `setDisclaimerAccepted()`（EncryptedSharedPreferences），同意后下次启动不再显示免责声明页
- **排版重构**：免责声明页重写为「顶部标题区（CF Dash 橙色 + 使用须知）+ 中部声明分节滚动（六小节加粗标题 + 正文）+ 底部复选框与退出/同意并继续按钮组」，层次分明、更优雅整洁
- **启动逻辑优化**：`MainActivity` `startDestination` 按 `isDisclaimerAccepted()` + 登录态三态判断；免责声明"同意并继续"后持久化状态并按登录态导航（`AppNavHost`）

### 品牌更名 CF Dash + 免责声明独立启动页 + 折线缩放回滚

- **应用更名 CF Dash**：应用名称与登录页标题由"Cloudflare 客户端"改为"CF Dash"（`strings.xml` `app_name` + `OnboardingScreen`），更低调、规避直呼官方品牌名的招摇感
- **新增免责声明独立启动页**：新增 `ui/disclaimer/DisclaimerScreen.kt`，作为 `startDestination` 启动页；含完整免责声明（第三方声明 / 风险自担 / API 密钥安全 / 责任限制 / 第三方行为 / 接受条款），须勾选"我已知晓并愿意承担相应风险"才能点"继续"（已登录→主界面，未登录→登录页），另有"退出"退出应用；接入 `Routes` / `AppNavHost` / `MainActivity`
- **导航逻辑修复**：`MainActivity` 增加 `firstLaunch` 标志，避免首次启动（未登录）时 `LaunchedEffect` 立即跳登录页、覆盖免责声明页；退出登录后仍正常回登录页
- **折线图缩放回滚**：移除 `TrendLineChart` 的 `zoomState = rememberVicoZoomState(zoomEnabled = false)`，恢复默认缩放（回滚批次7的"取消缩放"改动）
- **登录页重写**：`OnboardingScreen` 移除调试用免责声明弹窗代码，重写为干净登录页（标题 CF Dash）

## 2026.08.08

### todo批次7：域名拆分柱子多彩修复 / 版本号跨天重置 / 30天取消缩放 / 签名v2+v3

- **域名拆分柱子颜色修复（pending）**：根因是 `ColumnCartesianLayer.ColumnProvider.series(columns)` 的 columns 按**系列索引**（`entry.seriesIndex`）取色，单系列时恒为 0 导致所有柱子同色。改为**自定义 `ColumnProvider.getColumn(entry)` 按 x 索引返回 `pieColors` 颜色**，与下方图例一一对应（`StatsCharts.kt` `ZoneBarChart`）
- **版本 name 跨天重置（pending）**：`version.properties` 新增 `lastDate` 字段；`currentBuildSeq()` 判断"上次构建日期 ≠ 今天"时重置为 0，`bumpVersion` 写回 `buildSeq` + `lastDate`，实现跨天序号归零（如 08-08_5 → 08-09_1）（`app/build.gradle.kts`）
- **30 天折线图取消缩放**：`TrendLineChart` 的 `CartesianChartHost` 设置 `zoomState = rememberVicoZoomState(zoomEnabled = false)`，横轴默认压缩全量显示（`StatsCharts.kt`）
- **签名方案 v2 + v3**：显式开启 `enableV3Signing = true`（与 v2 并列，v1 关闭）（`app/build.gradle.kts` signingConfigs.release）

### todo批次6：带宽纵轴单位 / 域名拆分多彩去横轴 / 默认24h / 分割线间距 / CNAME文案 / 详情页返回动画

- **带宽趋势 Y 轴单位（人类可视化）**：带宽趋势折线图 Y 轴改用 `formatBytes` 自动选取 KB/MB/GB，替代数字单位（`StatsContent.kt` 带宽趋势 `TrendLineChart` 传 `valueFormatter = ::formatBytes`）
- **域名流量拆分**：移除横坐标（bottomAxis），柱状图柱子改为**多彩**（每根柱子 `LineComponent` 用 `pieColors`），与下方图例颜色**一一对应**；host 名称改由图例列表展示（`StatsCharts.kt` `ZoneBarChart`）
- **统计默认 24 小时**：账号级 `StatsViewModel` 与域名级 `ZoneDetailViewModel` 默认时间范围由 7 天（D7）改为 24 小时（H24）
- **侧边栏分割线间距平均分**：用户区底部 padding 由 16dp 收窄为 8dp，与分割线下方 8dp 对称，消除"上宽下窄"（`HomeScreen.kt`）
- **CNAME 介绍精简**：移除后半段"访问 [名称] 将跳转到 [目标]"，改为「[名称] 是 [目标] 的别名。」（`DnsRecordFieldDefs.kt`）
- **域名详情页返回动画**：`popExitTransition` 改为向右滑出（`slideOutHorizontally { -it / 4 }`），与进入从右滑入对称，退场动画可见（`navigation/AppNavHost.kt`）

### todo批次5：饼图弹窗移除 + 域名流量拆分列表 + 多修复

- **饼图移除 dialog**：维度分布饼图（国家/状态码/缓存）图例/扇区点击不再弹出 AlertDialog 详情；`LegendRow` 移除选中高亮与点击交互，仅展示色块/名称/数值/占比（`StatsCharts.kt`）
- **域名流量拆分列表**：账号级「域名流量拆分」柱状图下方新增**图例列表**，像饼图图例一样逐行列出每个 host 的名称/请求量/占比（Top8+其他，柱状图保留；`ZoneBarChart` 外包 `Column` + `LegendRow`）
- **A/AAAA 编辑页说明修复**：根因是模板占位符 `[IPv4 地址]`（带空格）与 replace 的 `[IPv4地址]`（无空格）不匹配导致地址未替换；文案更正为「将 [名称] 指向 [IPv4 地址]」/「将 [名称] 指向 [IPv6 地址]」，占位符统一带空格并随输入/回填值动态替换（`DnsRecordFieldDefs.kt` + `DnsRecordEditScreen.kt`）
- **高级设置开关独立防抖**：根因①ViewModel `settingsBusy != null` 全局拦截（任一设置切换时其他设置请求被直接忽略）；②Screen 端 `enabled` 条件写反（非正在操作时反而禁用）。修复：`settingsBusy` 改为 `Set<String>`，仅防**同一设置**重复操作，不同设置可**并行独立切换**；Screen 端 `enabled = "xxx" !in settingsBusy`（仅自己忙时禁用/转圈，其余保持可用）（`ZoneDetailViewModel.kt` + `ZoneDetailScreen.kt`）
- **侧边栏分割线间距**：侧边栏用户区与菜单之间的 `HorizontalDivider` 与菜单项之间增加 8dp 间距（`HomeScreen.kt`）

### 代码清理（移除测试代码与无用文件）

- **移除测试代码**：删除 `app/src/test/`（ExampleUnitTest）与 `app/src/androidTest/`（ExampleInstrumentedTest）整目录（项目无真实测试，纯脚手架示例）
- **移除测试依赖**：`gradle/libs.versions.toml` 删除 junit / junitVersion / espressoCore 版本号及 junit / androidx-junit / androidx-espresso-core / androidx-ui-test-manifest / androidx-ui-test-junit4 库；`app/build.gradle.kts` 删除 `testImplementation` / `androidTestImplementation` / `debugImplementation(ui-test-manifest)` 及 `testInstrumentationRunner`（保留 debugImplementation ui-tooling，开发预览用）
- **todo.md 精简**：按用户要求删除全部已实施条目（git 与 changelog 兜底记录），仅保留 4 条未实施需求（饼图移除 dialog、域名流量拆分列表、A/AAAA 编辑页说明异常、高级设置开关独立防抖）

### 统计功能图表化升级（Vico 图表库）

- **图表库**：接入 **Vico 3.2.3**（Compose 原生图表库，Maven Central 分发；仅依赖 `com.patrykandpatrick.vico:compose`——3.x 已无独立 core 模块；minSdk≥23 满足项目 24）
- **统计数据全面升级**（账号级「统计数据」Tab + 域名详情页同步升级，`StatsContent` 复用组件统一增强）：
  - **汇总指标卡新增独立访客**（GraphQL `uniq { uniques }`）
  - **时间趋势折线图**：请求数趋势、带宽趋势（24h 按小时约 24 点、7d/30d 按天 7/30 点；GraphQL `dimensions{datetimeHour|date}` + `sum` + `uniq`，客户端按时间标签排序，规避 Groups 数据集 orderBy 不支持时间排序的限制）
  - **维度分布饼图**（Top 6 + 其他归并，图例展示名称/数值/占比）：国家/地区（`clientCountryName`）、HTTP 状态码（`edgeResponseStatus`）、缓存状态（`cacheStatus` → 中文映射：命中/未命中/动态/过期/绕过等）
  - **账号级域名流量拆分柱状图**（Top 8 + 其他归并，`zones{zoneTag name + sum}`，按请求量降序）
  - **并行加载 + 单项降级**：汇总必须成功（失败 → 整体错误页）；趋势/维度/域名拆分并行请求，单项失败不阻塞其他（顶部红色提示 + 重试按钮）
- **GraphQL 查询/解析扩展**（`AnalyticsParser`）：新增 `zoneSeriesQuery`/`accountSeriesQuery`/`zoneBreakdownQuery`/`accountBreakdownQuery`/`accountZoneBreakdownQuery` 及对应解析；维度分布用 `count` + `orderBy:[count_DESC]` 取 Top 15
- **Repository 扩展**：新增 `getZoneAnalyticsSeries`/`getAccountAnalyticsSeries`/`getZoneBreakdown`/`getAccountBreakdown`/`getAccountZoneBreakdown`
- **模型扩展**：`AnalyticsSum` 新增 `uniques`；新增 `AnalyticsSeriesPoint`/`AnalyticsSeries`/`AnalyticsBreakdown`/`ZoneAnalyticsItem`/`BreakdownDimension`；新增 UI 聚合 `StatsData`
- **UI**：新增 `StatsCharts.kt`（`TrendLineChart`/`BreakdownPieChart`/`ZoneBarChart`，Vico 3.x API：`CartesianChartHost` + `lineModel`/`columnModel` + extras 分类轴标签 + `PieChartHost` + `pieSeries`）；`StatsContent` 参数重构为 `data: StatsData`
- **构建修复（首次 CI 失败）**：① Vico 3.2.3 的 AAR 元数据要求 **compileSdk ≥ 36** → 升级 `compileSdk` 35→36（targetSdk 保持 35），CI workflow 同步 `platforms;android-36` + `build-tools;36.0.0`；② `AnalyticsParser` 账号级解析（趋势/维度/域名拆分）中 `JsonElement` 不能直接 `[key]` 索引，改为 `zone.jsonObject[...]`；③ 汇总 `async` 缺少 `runCatching` 包装导致 `Deferred<AnalyticsSum>` 无 `getOrElse`，改为 `async { runCatching { ... } }` 后再 `getOrElse { throw it }`
- **GraphQL 字段修正（运行时验证，schema 反查）**：① **1hGroups 时间维度为 `datetime`**（"truncated to the hour"），非 `datetimeHour`——趋势 H24 查询与解析同步修正；② **1d/1h Groups 的 orderBy 枚举无 `count_DESC`**（仅 sum_*/uniq_*/date 等），且其 dimensions **仅有 date/datetime**（无国家/状态码/缓存维度）——**维度分布与域名拆分改用 `httpRequestsAdaptiveGroups`**（自适应采样：支持 `clientCountryName`/`edgeResponseStatus`/`cacheStatus`/`clientRequestHTTPHost`，orderBy 支持 `count_DESC`，时间过滤统一 `datetime_geq/leq`）；③ **`zones` 节点无 `name` 字段**——域名拆分改为 AdaptiveGroups 按 `clientRequestHTTPHost` 分组（zoneName=host）；④ 修复 `StatsContent` 多余 `!!` 编译警告（smart cast）
- **运行时修复（第三次迭代）**：① **独立访客始终为 0** → 汇总查询补查 `uniq { uniques }`，`accumulate` 解析 uniques；② **7d/30d 维度分布/域名拆分报错**（`httpRequestsAdaptiveGroups` 查询范围**限 1 天**，"cannot request a time range wider than 1d"）→ 维度分布改用 **Groups sum 的 `countryMap`/`responseStatusMap`**（支持 7d/30d，一次查询同时返回国家+状态码），**缓存分布**由 `cachedRequests/requests` 计算"命中/未命中"两切片（Groups 无 cacheStatus 维度），**域名拆分仅 24h**（AdaptiveGroups 限 1d，7d/30d 自动隐藏）；③ **带宽趋势 Y 轴**改用 `formatBytes`（KB/MB/GB），`TrendLineChart` 新增 `valueFormatter` 参数并用 `rememberUpdatedState` 稳定 LaunchedEffect（避免 lambda 每次重组触发重复 runTransaction 导致图表异常）
- **体验与版本修复（第四次迭代）**：① **移除右滑调出侧边栏手势**（`ModalNavigationDrawer(gesturesEnabled = false)`，仅顶部菜单按钮打开）；② **带宽趋势**调用改为与请求数趋势完全一致（高度/逻辑统一）；③ **统计全量缓存**：账号级 `StatsViewModel` 与域名级 `ZoneDetailViewModel` 均按时间范围缓存 StatsData，首次/切换加载后走缓存，**下拉刷新**（账号级 `PullToRefreshBox` + `verticalScroll`，域名级因内嵌页面 scroll 不启用下拉避免嵌套冲突）；④ **版本号自增修复**：CI `actions/cache` 的 key 由 `hashFiles('version.properties')` 改为 **`github.run_id`**（每次构建唯一，restore-keys 前缀匹配最近一次保存的 buildSeq）——原 hash 方案因 version.properties 每次 checkout 重置为仓库值、hash 恒等导致缓存永不精确命中、buildSeq 无法持久化，版本号停在第 1 个
- **体验优化与图表交互（todo.md 批次1）**：
  - **侧边栏宽度收窄**：`ModalDrawerSheet` 宽度 300dp；**点击空白处关闭**侧边栏（ModalNavigationDrawer 内置 scrim tap-to-close，与 gesturesEnabled 无关，确认已支持）
  - **域名详情页三个高级设置开关单独禁用**：仅正在切换的那个禁用/转圈，其余保持可用
  - **TTL 可读化**：新增 `formatTtl`（秒→X分钟/X小时/X天），DNS 记录列表与编辑页 TTL 展示、TTL 下拉选项均应用
  - **图表点击显示详情（Vico CartesianMarker）**：请求数/带宽趋势折线图**点击拐点**显示"时间 + 数值"；域名柱状图**点击柱子**显示"host + 请求量"（`rememberDefaultCartesianMarker` + 自定义 ValueFormatter，经 extras 读时间标签）
  - **饼图图例点击**：点击图例项高亮并显示"名称：数量 · 占比"详情行（BreakdownPieChart 图例可交互）
  - **DNS 记录列表批量操作（候选框，todo.md 批次2）**：每条记录前加复选框；选中后显示批量操作栏（已选数/取消/删除/开代理/关代理）——**批量删除**（二次确认，逐条 DELETE 本地同步，部分失败汇总提示）、**批量开关代理仅对 A/AAAA/CNAME 生效**（其余类型选中忽略，逐条 PATCH proxied 本地同步）；DnsViewModel 新增 selectedIds/批量方法，DnsRecordsContent 新增批量栏/行复选框/确认对话框
  - **DNS 记录完整表单（todo.md 批次3，#9）**：按记录类型渲染**完整字段表单**（仿 Cloudflare 控制台），不再让用户填裸 content：
    - 新增 `DnsRecordFieldDefs`（12 种类型字段模板 + 顶部说明文案，如 CNAME"[名称] 是 [目标] 的别名"）
    - 新增 `DnsEditViewModel` 重构：字段 Map 状态、编辑回填（record.data/content 解析）、按类型序列化 `data`（JsonObject：SRV/DNSKEY/CAA/SVCB/HTTPS/SSHFP/TLSA/NAPTR/URI）与 `content`（RFC 组合）、数字键盘与必填校验
    - `DnsRecordEditScreen` 重写：类型选择 + 顶部说明卡 + 名称 + 动态字段表单 + TTL + 代理开关（仅可代理类型）+ 备注
  - **多用户功能（todo.md 批次4，#6）**：支持多个 Cloudflare 账号并存，**新建/切换入口在侧边栏上部**（用户卡区）：
    - `TokenStore` 重构为多用户存储：每用户一组凭据（`cf_user_{id}_mode/token/email/key`）+ 激活用户 id；新增 `UserAccount`、`saveUser/getUsers/getActiveUser/setActiveUser/deleteUser`；GlobalKey 用户 id=邮箱、Token 用户 id=内容 hash
    - `OnboardingViewModel`：验证成功改为 `saveUser`（保存为新用户并激活）
    - `MainActivity`：登录态=存在任意用户；`homeKey` 刷新键驱动 Home 重载用户数据；退出登录=删除激活用户（有剩余自动切换回 Home，无则回初始化）
    - `AppNavHost`：Home→Onboarding 新增用户（成功后 popBackStack 回 Home）、`onNewUser/onUserSwitched/homeKey` 回调
    - `HomeScreen` 侧边栏用户卡：显示激活用户 + **"切换用户"（DropdownMenu 列出全部用户）/ "新建用户"**；切换后重载用户数据
  - **Pending 修复（todo.md Pending 队列 7 项全部完成）**：
    - **点击空白关闭侧边栏**：`HomeScreen` 由 ModalNavigationDrawer 改为**自绘侧边栏**（Box + AnimatedVisibility + scrim 点击关闭，无右滑手势，宽度 300dp）
    - **批量代理确认按钮文案**：统一为"确定 / 取消"
    - **DNS 候选框改为图标启用**：域名行右侧 **check-all 图标**（Checklist）控制候选模式；复选框仅启用时显示；批量操作按钮改 **OutlinedButton（带边框）**
    - **DNS 编辑页说明动态替换**：`[名称]/[目标]/[IPv4/6地址]` 占位符随输入/回填值实时替换
    - **饼图点击预览**：图例点击改为 **AlertDialog 弹窗**显示"名称：数量 · 占比"
    - **折线图 marker 展示框**：label 增加 `lineCount=2` + `overflow=Visible`，完整显示"时间 + 数值"
    - **退出登录按钮失效**：`MainActivity` 用 `LaunchedEffect(loggedIn)` 在无剩余用户时导航回初始化页（NavHost 不监听 startDestination 变化）
- **打包清理**：`packaging.resources.excludes` 追加 `**/DebugProbesKt.bin`（kotlinx-coroutines 协程调试探针，仅 IDE 调试用，release 不激活，避免 APK 内出现无用 .bin 文件）
- **文档**：agent.md（统计模块/依赖/注意点/速查表）、readme.md（功能/技术栈）同步更新

### 侧边栏导航 + 统计数据 + 构建优化

- **侧边栏导航**：底栏 NavigationBar → **ModalNavigationDrawer 侧边栏**（用户信息卡 + 域名/DNS/统计数据/我的 + 退出登录），为未来更多功能预留扩展位；主内容区第 4 个 Tab「统计数据」，沿用 visitedMask 常驻 + SlidingTab 水平平移过渡
- **统计数据（GraphQL Analytics）**：按官方文档实现 `POST /graphql` 查询（`httpRequests1hGroups`/`httpRequests1dGroups`，`sum{requests, threats, bytes, cachedRequests, cachedBytes}`）：
  - **账号级**（侧边栏「统计数据」Tab，遍历账号下所有域名累加）+ **域名级**（域名详情页统计卡片）
  - 时间范围 **24小时 / 7天 / 30天** SegmentedButton 切换（数据集/limit 映射，UTC 时间窗）
  - 指标：请求数、威胁数、带宽、缓存命中率（万/亿、KB/MB/GB 格式化）
  - 可复用 `StatsContent` 组件；`User` 模型新增 `accounts`（账号级统计需要 accountTag）
  - Token 权限要求新增 **Zone → Analytics → Read**（登录页权限列表/readme 同步）
- **构建优化**：
  - `defaultConfig.ndk.abiFilters` 仅打包 `armeabi-v7a` + `arm64-v8a`（排除 x86/x86_64）
  - `gradle.properties`：`org.gradle.caching=true`（Gradle Build Cache 跨构建复用任务输出）+ `-Xmx4096m`
- **文档**：agent.md（34 文件结构/GraphQL 端点/权限/注意点/速查表）、readme.md（功能/权限表）同步
- **构建修复**：移除 `CloudflareClient.kt` 两个不存在的顶层 import（`json.content` 为成员属性、`parseToJsonElement` 为 Json 成员函数，均无需 import），修复 CI release 编译失败
- **统计修复**：账号级统计取账号改用 `GET /accounts`（`Repository.getAccounts()`），替代 `GET /user` 的 `accounts` 字段（Global Key 下该字段可能为空导致「账号信息缺失」）
- **构建修复**：`getAccounts()` 泛型参数修正为 `client.get<List<AccountRef>>`（`get<T>` 的 T 即 result 类型，勿再嵌套 ApiResponse），修复 CI 编译失败
- **统计查询修复**：移除 GraphQL 查询中的 `orderBy`（官方文档：预聚合 Groups 数据集排序仅支持聚合字段，如 `sum_bytes_DESC`，不支持时间维度排序，导致三个时间范围全部报 "cannot order by datetime/date"）；只做总量累加与顺序无关；filter 保持官方验证字段（1hGroups → `datetime_geq/leq`，1dGroups → `date_geq/leq`）；limit 加缓冲（48/15/32）防时间边界漏行

### 高级设置修复 + Global API Key 登录 + Token 权限提示

- **高级设置加载修复**：域名详情与三个高级设置（开发模式/五秒盾/IPv6）改为**并发请求**（页面 loading 动画期间即开始，单项失败不阻塞其他）；加载失败不再静默吞掉（显示错误原因 + 「重试」按钮，仅重刷设置不打断页面）；开发模式文案调整：默认「开启后绕过CDN缓存」，仅确认开启时显示「剩余时间：X」
- **Global API Key 登录**：认证链路重构为双凭据（`sealed interface AuthCredential`：Token / GlobalKey）；请求头支持 `Authorization: Bearer` 与 `X-Auth-Email` + `X-Auth-Key` 两种；验证按模式区分（Token 走 `/user/tokens/verify`，Global Key 走 `GET /user`）；TokenStore 加密存储扩展（authMode + email/key）；登录页 SegmentedButton 切换，**Global API Key 默认优先，API Token 其次**
- **Token 权限提示**：Token 登录表单展示所需权限列表（Zone Read/Edit、DNS Read/Edit、Zone Settings Read/Edit、User Details Read）
- **readme**：权限表补充 Zone Settings Read/Edit，并注明支持 Global API Key 登录
- **构建修复**：`verify()` 泛型化（`<C : AuthCredential>`），修复 onSave lambda 中无法解析子类属性（email/apiKey/value）导致的 CI 编译失败

### 图标库扩展 + 域名详情高级设置 + Tab 水平平移动画

- **图标**：引入 `material-icons-extended` 完整图标库（release 开启 R8，未引用图标被裁剪，APK 体积几乎无增量）；移除「我的」页自定义 `WebIcon`（ImageVector + PathParser 手写 pathData），开源仓库图标改用标准库 `Icons.Filled.Web`
- **域名详情页新增「高级」设置卡片**（Zone Settings API：`GET/PATCH /zones/{id}/settings/{name}`，需 Token 具备 Zone Settings 权限）：
  - **开发模式**：`development_mode` on/off 开关，副标题动态显示剩余时间（`time_remaining`，3 小时自动关闭）
  - **五秒盾模式**（Under Attack Mode）：`security_level=under_attack` 开启，关闭恢复 `medium`（Cloudflare 默认安全级别）
  - **IPv6 兼容性**：`ipv6` on/off 开关
  - 三个设置并行加载（单项失败不阻塞其他）；切换防连点（settingsBusy 禁用全部开关）、失败保留原值并红色文字提示
- **Tab 切换动画**：透明度过渡（150ms）→ **水平平移过渡**（250ms FastOutSlowInEasing，offset 位移 GPU 合成，方向跟随 Tab 位置：右侧 Tab 从右滑入、左侧 Tab 从左滑入），保持常驻组合不销毁重建（LazyColumn 不重建、滚动位置不丢失）
- **构建修复**：补充 `HomeScreen.kt` 缺失的 `androidx.compose.ui.unit.dp` import（screenWidthDp.dp 计算屏幕宽度），修复 CI release 编译失败

## 2026.08.05

### 本次构建改造

- 包名定稿为 `io.github.toserk1024.cfdash`（规避商标风险；此前曾迁移至 com.cloudflare.dash3rd）
- 版本号机制：
  - versionName 采用 `日期_自增序号`（如 `2026.08.08_1`，序号每次构建 +1，应用内展示前缀 v）
  - versionCode 采用构建时间(unix秒)前 9 位截取，规避 32 位 Int 极限
  - 构建日期按 **UTC+8**（Asia/Shanghai）计算
- 主题：Cloudflare 橙主题（沿用 dynamic color）；深色模式背景 **OLED 纯黑**（background/surface=#000000，省电）
- 关于页：动态显示版本号（前缀 v）+ 开源仓库 **Card** 入口（自定义 Web/地球图标，点击打开浏览器）
- 初始化页：移除 Logo 区域（仅保留标题与 Token 输入）
- CI 构建方式由 debug 改为 **release**，开启 **R8 压缩**；**push 到 main 自动触发**构建
- 正式签名支持：`signingConfigs.release` 通过环境变量（`BUILD_STORE_FILE/BUILD_STORE_PASSWORD/BUILD_KEY_ALIAS/BUILD_KEY_PASSWORD`）引入，未配置自动回退 debug 签名；**仅 v2+v3**（v1 关闭，v3 由 AGP 8+ 默认开启）；CI 从 GitHub Secrets 解码 keystore（workflow 用 env 注入，`if` 条件不可直接用 secrets；keystore 路径用 `rootProject.file()` 相对仓库根解析，修复 CI 双重 `app/app/` 路径问题）
- 修复 R8 构建：补充 kotlinx.serialization keep 规则与 Tink 编译期注解 `-dontwarn` 规则
- README 调整：更名 `readme.md`，顶部新增 vibe coding 警示提示，并指向 `agent.md` / `changelog.md`

## 会话内累积更新（本次之前的修改）

- **底部导航 Tab 切换卡顿修复**：三个 Tab 首次访问后常驻组合，切换仅做 150ms 透明度过渡（GPU 合成），根治 AnimatedContent 每次切换销毁/重建整页造成的掉帧
- **DNS 记录编辑/新建后列表自动更新**：新增跨 ViewModel 同步队列（`DnsRecordsSync`），保存成功后本地更新缓存（编辑原位替换、新建追加），无需重新请求 API；删除记录保持本地同步
- 首次包名迁移（`com.java.myapplication` → `com.cloudflare.dash3rd` → 本次定稿 `io.github.toserk1024.cfdash`）

## 历史版本记录（v1 ~ v1.5，迁移自 agent.md）

- **v1**：基础框架（Onboarding 验证 + 域名列表 + DNS CRUD + 我的）→ 构建通过
- **v1.1**：修复验证流程 Bug（tokenOverride）、Token 格式校验
- **v1.2**：修复"我的"转圈（状态机）、移除添加域名、修复 DNS 编辑导航
- **v1.3**：全量缓存 + 本地搜索 + 下拉刷新（PullToRefreshBox）+ DNS FAB
- **v1.4**：Tab/导航过渡动画（AnimatedContent + NavHost transitions）、修复请求体序列化（reified BodyT）
- **v1.5**：移除本地构建环境（ARM64 AAPT2 hack、setup_android_env.sh、tools/、gradle wrapper 本地 distributionUrl），改用 GitHub Actions CI 构建（手动触发）
