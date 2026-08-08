# Changelog

## 历史版本记录（v1 ~ v1.5，迁移自 agent.md）

- **v1**：基础框架（Onboarding 验证 + 域名列表 + DNS CRUD + 我的）→ 构建通过
- **v1.1**：修复验证流程 Bug（tokenOverride）、Token 格式校验
- **v1.2**：修复"我的"转圈（状态机）、移除添加域名、修复 DNS 编辑导航
- **v1.3**：全量缓存 + 本地搜索 + 下拉刷新（PullToRefreshBox）+ DNS FAB
- **v1.4**：Tab/导航过渡动画（AnimatedContent + NavHost transitions）、修复请求体序列化（reified BodyT）
- **v1.5**：移除本地构建环境（ARM64 AAPT2 hack、setup_android_env.sh、tools/、gradle wrapper 本地 distributionUrl），改用 GitHub Actions CI 构建（手动触发）

## 2026-08-08 · 统计功能图表化升级（Vico 图表库）

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
- **打包清理**：`packaging.resources.excludes` 追加 `**/DebugProbesKt.bin`（kotlinx-coroutines 协程调试探针，仅 IDE 调试用，release 不激活，避免 APK 内出现无用 .bin 文件）
- **文档**：agent.md（统计模块/依赖/注意点/速查表）、readme.md（功能/技术栈）同步更新

## 2026-08-08 · 侧边栏导航 + 统计数据 + 构建优化

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

## 2026-08-08 · 高级设置修复 + Global API Key 登录 + Token 权限提示

- **高级设置加载修复**：域名详情与三个高级设置（开发模式/五秒盾/IPv6）改为**并发请求**（页面 loading 动画期间即开始，单项失败不阻塞其他）；加载失败不再静默吞掉（显示错误原因 + 「重试」按钮，仅重刷设置不打断页面）；开发模式文案调整：默认「开启后绕过CDN缓存」，仅确认开启时显示「剩余时间：X」
- **Global API Key 登录**：认证链路重构为双凭据（`sealed interface AuthCredential`：Token / GlobalKey）；请求头支持 `Authorization: Bearer` 与 `X-Auth-Email` + `X-Auth-Key` 两种；验证按模式区分（Token 走 `/user/tokens/verify`，Global Key 走 `GET /user`）；TokenStore 加密存储扩展（authMode + email/key）；登录页 SegmentedButton 切换，**Global API Key 默认优先，API Token 其次**
- **Token 权限提示**：Token 登录表单展示所需权限列表（Zone Read/Edit、DNS Read/Edit、Zone Settings Read/Edit、User Details Read）
- **readme**：权限表补充 Zone Settings Read/Edit，并注明支持 Global API Key 登录
- **构建修复**：`verify()` 泛型化（`<C : AuthCredential>`），修复 onSave lambda 中无法解析子类属性（email/apiKey/value）导致的 CI 编译失败

## 2026-08-08 · 图标库扩展 + 域名详情高级设置 + Tab 水平平移动画

- **图标**：引入 `material-icons-extended` 完整图标库（release 开启 R8，未引用图标被裁剪，APK 体积几乎无增量）；移除「我的」页自定义 `WebIcon`（ImageVector + PathParser 手写 pathData），开源仓库图标改用标准库 `Icons.Filled.Web`
- **域名详情页新增「高级」设置卡片**（Zone Settings API：`GET/PATCH /zones/{id}/settings/{name}`，需 Token 具备 Zone Settings 权限）：
  - **开发模式**：`development_mode` on/off 开关，副标题动态显示剩余时间（`time_remaining`，3 小时自动关闭）
  - **五秒盾模式**（Under Attack Mode）：`security_level=under_attack` 开启，关闭恢复 `medium`（Cloudflare 默认安全级别）
  - **IPv6 兼容性**：`ipv6` on/off 开关
  - 三个设置并行加载（单项失败不阻塞其他）；切换防连点（settingsBusy 禁用全部开关）、失败保留原值并红色文字提示
- **Tab 切换动画**：透明度过渡（150ms）→ **水平平移过渡**（250ms FastOutSlowInEasing，offset 位移 GPU 合成，方向跟随 Tab 位置：右侧 Tab 从右滑入、左侧 Tab 从左滑入），保持常驻组合不销毁重建（LazyColumn 不重建、滚动位置不丢失）
- **构建修复**：补充 `HomeScreen.kt` 缺失的 `androidx.compose.ui.unit.dp` import（screenWidthDp.dp 计算屏幕宽度），修复 CI release 编译失败

## 2026-08-05 · 本次构建改造

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
