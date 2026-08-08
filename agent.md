# agent.md — Cloudflare 客户端二次开发指南

> 供 AI Agent / 开发者快速理解项目结构与约定，进行二次开发。

## 1. 项目概览

基于 **Jetpack Compose + Material 3** 的 Cloudflare 第三方 Android 客户端（包名 `io.github.toserk1024.cfdash`）。
当前已实现：**双认证登录（Global API Key / API Token，Global 默认优先）、侧边栏导航（域名/DNS/统计/我的）、域名管理（列表/搜索/详情/删除）、DNS 记录管理（列表/筛选/搜索/新建/编辑/删除）、域名高级设置（开发模式 / 五秒盾 / IPv6）、统计数据（账号级/域名级，24h/7d/30d 切换，Vico 图表化：汇总卡 + 时间趋势折线图 + 国家/状态码/缓存维度饼图 + 账号级域名流量拆分柱状图）、用户信息与退出登录**。

**已移除功能**：添加域名（用户要求删除，相关代码已清理干净，如需恢复参考 §8.3）。
**未开发**：Workers、Zero Trust 等高级功能（用户明确暂不开发）。

## 2. 技术栈与依赖

| 类别 | 方案 | 版本 |
|---|---|---|
| UI | Jetpack Compose + Material 3 | BOM 2026.01.01 |
| 语言 | Kotlin | 2.3.10 |
| 构建 | AGP + Gradle Version Catalog | AGP 9.0.0 / Gradle 9.1.0 |
| 网络 | OkHttp | 4.12.0 |
| JSON | kotlinx.serialization | 1.7.3（需 serialization 插件） |
| 导航 | Navigation Compose | 2.8.5 |
| 存储 | EncryptedSharedPreferences (security-crypto) | 1.1.0-alpha06 |
| 协程 | kotlinx-coroutines-android | 1.9.0 |
| 图标 | material-icons-extended（完整图标库，release R8 裁剪未引用图标，体积几乎无增量） | BOM 管理 |
| ViewModel | lifecycle-viewmodel-compose | 2.8.7 |
| 图表 | Vico（Compose 原生，仅 compose 模块，3.x 无独立 core） | 3.2.3 |

依赖定义位置：`gradle/libs.versions.toml` + `app/build.gradle.kts`。
仓库镜像：阿里云/华为云优先（`settings.gradle.kts`），离线环境可构建。

## 3. 完整代码结构（34 个 Kotlin 文件）

```
app/src/main/java/io/github/toserk1024/cfdash/
├── MainActivity.kt               # 入口：AppContainer.init + 登录态路由（onboarding/home）
├── AppContainer.kt               # 全局单例容器：tokenStore + repository（Service Locator）
├── navigation/
│   ├── Routes.kt                 # 路由字符串常量 + 带参路由构建函数
│   └── AppNavHost.kt             # NavHost：6 个目的地 + 全局转场动画
├── data/
│   ├── api/
│   │   ├── CloudflareApi.kt      # BASE_URL + 端点路径常量（含 /graphql）
│   │   ├── AuthCredential.kt     # 认证凭据：sealed interface（Token / GlobalKey）
│   │   └── CloudflareClient.kt   # ★核心：OkHttp 封装 + inline reified 序列化 + 双认证头 + graphql()
│   ├── model/
│   │   ├── ApiResponse.kt        # 通用包装 success/errors/result/result_info + TokenVerifyResult
│   │   ├── Zone.kt               # Zone + ZonePlan
│   │   ├── ZoneSetting.kt        # Zone 设置项（id/value/editable/time_remaining）+ 更新请求体
│   │   ├── ZoneAnalytics.kt      # 统计：AnalyticsRange/AnalyticsSum + AnalyticsParser（GraphQL 查询/解析）
│   │   ├── DnsRecord.kt          # DnsRecord + DnsRecordRequest + DnsRecordTypes(常量)
│   │   └── User.kt               # 用户信息 + AccountRef（账号级统计用）
│   ├── repository/
│   │   └── CloudflareRepository.kt  # 业务层：verifyCredential/getUser/getAccounts/getZones/getZone/deleteZone/getZoneSetting/updateZoneSetting/getZoneAnalytics/getAccountAnalytics/getDnsRecords/createDnsRecord/updateDnsRecord/deleteDnsRecord
│   └── storage/
│       └── TokenStore.kt         # EncryptedSharedPreferences 双凭据存储（authMode + token/email+key）
└── ui/
    ├── theme/                    # Color/Theme/Type（Cloudflare 橙 #F6821F）
    ├── onboarding/               # OnboardingScreen + OnboardingViewModel（双认证：Global 默认 / Token）
    ├── home/                     # HomeScreen（侧边栏导航 + 水平平移过渡）+ HomeViewModel（用户信息）
    ├── stats/                    # StatsViewModel（账号级统计）+ StatsContent（复用组件）+ StatsData（展示数据聚合）+ StatsCharts（Vico 图表：趋势折线/维度饼图/域名柱状图）
    ├── zones/                    # ZonesScreen + ZonesViewModel（域名列表）+ ZoneDetailScreen + ZoneDetailViewModel（含高级设置 + 域名统计）
    ├── dns/                      # DnsRecordsContent（复用组件）+ DnsRecordsScreen + DnsRecordEditScreen + DnsViewModel + DnsEditViewModel
    └── profile/                  # ProfileScreen（我的）
```

## 4. 架构设计（★重点理解）

### 4.1 数据流
```
UI(Composable) ⇄ ViewModel(StateFlow) ⇄ Repository ⇄ CloudflareClient(OkHttp) ⇄ Cloudflare API
                        ↑
                  AppContainer(全局单例)
```

### 4.2 依赖注入
无 DI 框架，用 `AppContainer` 单例：
- `AppContainer.init(context)` 在 MainActivity.onCreate 调用一次
- ViewModel 直接访问 `AppContainer.repository` / `AppContainer.tokenStore`
- `CloudflareClient` 通过 `tokenStore::getToken` 获取 Token

### 4.3 缓存与本地搜索（重要约定！）
用户明确要求：**请求数据后缓存到内存，搜索/筛选在本地完成，只有下拉刷新才请求 API**。

- **ZonesViewModel**：`allZones`（内存缓存，翻页拉全量直到 total_pages）→ `applyFilter()` 按 `name.contains(query, ignoreCase)` 本地过滤 → 展示 `zones`
- **DnsViewModel**：`allRecords`（当前选中域名的全量记录缓存）→ `applyFilter()` 按类型+名称本地过滤 → 展示 `records`
- **下拉刷新**：Material3 `PullToRefreshBox(isRefreshing, onRefresh)`，onRefresh 重新拉全量
- **删除后**：同步从缓存移除（`filterNot`），无需刷新

### 4.4 序列化方案（★不要改回 Any！）
`CloudflareClient` 的 GET/POST/PATCH/DELETE 全部为 `internal suspend inline fun <reified T>`：
- **响应反序列化**：`decodeApiResponse<T>(raw)`（@PublishedApi internal inline），在 inline 函数体内解码，避免 reified 进入非 inline 函数
- **请求体序列化**：POST/PATCH 用双泛型 `post<RespT, BodyT>(body: BodyT)`，在调用点用**具体类型** `CloudflareJson.encodeToString(body)`——**绝不能**把 body 收窄成 `Any?` 再序列化（会报 "Serializer for class 'Any' is not found"）
- `CloudflareJson` 和 `decodeApiResponse` 标记 `@PublishedApi`（供 public/internal inline 访问）
- Repository 调用需显式双类型参数：`client.post<DnsRecord, DnsRecordRequest>(path, request)`
- Token 校验：发送前检查仅含 ASCII 可打印字符（0x21..0x7E），否则抛友好中文错误（OkHttp 拒绝非 ASCII header）

### 4.5 导航
- 单 Activity，路由见 `Routes.kt`：onboarding / home / zone_detail / dns_records / dns_edit
- `AppNavHost` 配置了全局转场动画（fadeIn + slideInHorizontally 1/4 屏）
- HomeScreen 底部三 Tab（域名/DNS/我的）：首次访问后常驻组合（visitedMask 懒加载），切换仅水平平移过渡（SlidingTab，250ms FastOutSlowIn，offset 位移 GPU 合成，方向跟随 Tab 位置），避免重建卡顿
- 带参路由：`zone_detail/{zoneId}?zoneName={zoneName}`（可选参数有 defaultValue=""）
- ViewModel 从 `SavedStateHandle` 读参数（如 DnsEditViewModel 的 zoneId/recordId、DnsViewModel 的 zoneId）
- **注意**：`SavedStateHandle["key"]` 泛型推断可能失败，用 `savedStateHandle.get<String>("key")` 显式类型

### 4.6 DNS 页面复用
`DnsRecordsContent`（列表+搜索+筛选+下拉刷新+FAB）在**两处复用**：
1. HomeScreen DNS Tab（`DnsViewModel` 由 HomeScreen 顶层 `viewModel()` 创建，常驻内存）
2. 独立页面 DnsRecordsScreen（从域名详情进入，路由参数预选域名）
FAB 已内置在 Content 中，DnsRecordsScreen 不再自带 FAB。

## 5. Cloudflare API 端点

| 用途 | 方法/路径 | 备注 |
|---|---|---|
| 验证 Token | GET `/user/tokens/verify` | 用 tokenOverride 传用户输入（未保存时） |
| 用户信息 | GET `/user` | |
| 域名列表 | GET `/zones?page=&per_page=&name=&status=` | |
| 域名详情 | GET `/zones/{zone_id}` | |
| 删除域名 | DELETE `/zones/{zone_id}` | |
| 账号列表 | GET `/accounts` | 账号级统计取 accountTag（比 /user.accounts 可靠） |
| 获取 Zone 设置 | GET `/zones/{zone_id}/settings/{name}` | development_mode / security_level / ipv6 等 |
| 更新 Zone 设置 | PATCH `/zones/{zone_id}/settings/{name}` | body: {"value": ...}，需 Zone Settings 权限 |
| 统计数据 | POST `/graphql` | GraphQL Analytics（httpRequests1dGroups/1hGroups），需 Analytics Read；详见下方统计查询说明 |
| DNS 记录列表 | GET `/zones/{zone_id}/dns_records?page=&per_page=` | 拉全量翻页 |
| 新建记录 | POST `/zones/{zone_id}/dns_records` | body: DnsRecordRequest |
| 更新记录 | PATCH `/zones/{zone_id}/dns_records/{id}` | body: DnsRecordRequest |
| 删除记录 | DELETE `/zones/{zone_id}/dns_records/{id}` | |

Token 权限要求：Zone Read/Edit、DNS Read/Edit、Zone Settings Read/Edit（高级设置）、Analytics Read（统计数据）、User Details Read（Account Settings Read 仅在添加域名时需要，已无此功能）。

认证方式：**双凭据**（`AuthCredential`）——Global API Key（`X-Auth-Email`/`X-Auth-Key`，`GET /user` 验证）与 API Token（`Authorization: Bearer`，`/user/tokens/verify` 验证），登录页 Global 默认优先；凭据存 EncryptedSharedPreferences（`authMode` 区分 token/global）。

## 6. 构建与部署

### 6.1 构建（GitHub Actions CI，推荐）

本地已移除 Android SDK / Gradle 构建环境，构建统一走 **GitHub Actions**（`.github/workflows/build.yml`，**push 到 main 自动触发**，也可在 Actions 页手动触发）：

1. `git push origin main` 自动开始构建；或仓库 → **Actions** → **Build APK** → **Run workflow**（手动）
2. 等待完成，在 Run 页底部 **Artifacts** 下载 `app-release-apk`（zip）
3. 解压得到 `app-release.apk` 安装到设备

构建环境：`ubuntu-latest`（x86_64）+ JDK 17（temurin）+ platform-35 + build-tools 35.0.0；`gradle-wrapper.properties` 使用官方 distributionUrl。**注意：不要把 distributionUrl 再改成本地路径（如 file:///root/...），CI 无法访问。**

- **ABI 过滤**：`defaultConfig.ndk.abiFilters` 仅打包 `armeabi-v7a` + `arm64-v8a`（排除 x86/x86_64，当前纯 Kotlin 无 .so，属防御性声明）
- **构建提速**：`gradle.properties` 开启 `org.gradle.caching=true`（Gradle Build Cache，复用任务输出）+ `org.gradle.jvmargs=-Xmx4096m`；CI 由 `gradle/actions/setup-gradle@v4` 自动缓存 Gradle 依赖（无需额外 actions/cache）

### 6.2 安装到设备（Shizuku/ADB）
```bash
# 终端（proot）把 APK 复制到 sdcard
cp app/build/outputs/apk/release/app-release.apk /sdcard/Download/cf-app.apk
# shell（Shizuku）复制到 /data/local/tmp 并安装（system_server 读不了 /sdcard，必须经 tmp）
cp /sdcard/Download/cf-app.apk /data/local/tmp/cf-app.apk
pm install -r /data/local/tmp/cf-app.apk
```
启动：`am start -n io.github.toserk1024.cfdash/.MainActivity`

## 7. 已知问题与注意事项

1. **图标库已升级 material-icons-extended**：完整 ~2000 图标可用（release R8 裁剪未引用，体积几乎无增量）。新增图标直接 `import androidx.compose.material.icons.filled.XXX`（如 `Public`/`Web`），无需自定义 ImageVector；已移除旧的 core 限制与自定义 WebIcon。
2. **inline reified 约束**：`decodeApiResponse` 是 @PublishedApi internal inline；`CloudflareJson` 也需 @PublishedApi。新增泛型 API 方法时遵循同样模式，否则 "Public-API inline function cannot access non-public-API"。
3. **allWarningsAsErrors 疑似开启**：deprecated API 调用会被当作编译错误（如 HttpUrl.get、Icons.Default.List），务必用新 API（toHttpUrl、AutoMirrored）。
4. **EncryptedSharedPreferences**：首次创建 MasterKey 可能稍慢，用 lazy 延迟初始化。
5. **验证 Token 流程**：OnboardingViewModel 先 `verifyToken(token)`（tokenOverride 传用户输入）→ 成功才 `saveToken`。**不要**改成先保存再验证。
6. **"我的"页**：HomeViewModel 有 loading/error 状态机，失败显示错误+重试，不会无限转圈。
7. **删除域名/记录**：均有 AlertDialog 二次确认；删除后本地缓存同步移除。
8. **DNS 记录类型**：MX/URI 显示 priority 字段；A/AAAA/CNAME 显示 proxied 开关；SRV/CAA 等用 content 文本（Cloudflare 接受 content 格式）。
9. **ProfileScreen 中 HomeUiState 是 HomeViewModel 嵌套类**，import 需写 `io.github.toserk1024.cfdash.ui.home.HomeViewModel.HomeUiState`。
10. **双认证头**：`CloudflareClient.requestRaw` 按 `AuthCredential` 类型添加请求头（Token → `Authorization: Bearer`；GlobalKey → `X-Auth-Email` + `X-Auth-Key`），均校验 ASCII 可打印字符；验证走 `verifyCredential()`（Token → `/user/tokens/verify`，GlobalKey → `GET /user`）。修改 Zone 设置需 Token 具备 **Zone Settings Read/Edit** 权限，否则 403（高级卡片会显示错误 + 重试按钮）。
11. **GraphQL Analytics**：`client.graphql()` POST `/graphql`，响应为 data/errors 结构（非 ApiResponse 包装），errors 非空抛 CloudflareException；查询构建与解析统一在 `AnalyticsParser`（时间窗口 UTC、数据集映射 24h/7d/30d）；统计需 Token 具备 **Zone Analytics Read**（域名级）/ **Account Analytics Read**（账号级）权限，否则 403。**关键约束（schema 反查确认，踩坑记录）**：① **1d/1h Groups 的 dimensions 仅有 `date`（1d）/`datetime`（1h，"truncated to the hour"）**，无 datetimeHour、无国家/状态码/缓存维度；② **1d/1h Groups 的 orderBy 枚举无 `count_DESC`**（合法值仅 sum_*/uniq_*/avg_*/date/datetime 等聚合字段），故汇总/趋势查询**不使用 orderBy**，趋势序列客户端按时间标签排序；③ **国家/状态码/缓存维度分布与域名拆分必须用 `httpRequestsAdaptiveGroups`**（自适应采样，dimensions 支持 clientCountryName/edgeResponseStatus/cacheStatus/clientRequestHTTPHost，orderBy 支持 count_DESC，时间过滤统一 `datetime_geq/leq` ISO）；④ **`zones` 节点无 `name` 字段**（报 unknown field "name"），域名拆分改为 AdaptiveGroups 按 `clientRequestHTTPHost` 分组（zoneName=host）；⑤ filter 时间字段：1hGroups → `datetime_geq/leq`，1dGroups → `date_geq/leq`（yyyy-MM-dd）；⑥ 账号级 accountTag 用 `GET /accounts`（`/user` 的 `accounts` 字段可能为空）；⑦ 趋势查询 `dimensions { datetime | date }` + `sum` + `uniq { uniques }`（独立访客）。
12. **Vico 图表（3.x，踩坑记录）**：仅依赖 `com.patrykandpatrick.vico:compose`（3.x 无独立 core 模块，minSdk≥23）；核心 API：`CartesianChartHost(rememberCartesianChart(rememberLineCartesianLayer/rememberColumnCartesianLayer, startAxis = VerticalAxis.rememberStart(), bottomAxis = HorizontalAxis.rememberBottom()), modelProducer)`；数据更新用 `CartesianChartModelProducer.runTransaction { lineModel/columnModel { series(y = list) } }`；**x 轴分类标签**：x 用序号（series 自动索引），标签经 `extras { it[key] = labels }` 同步，`HorizontalAxis.rememberBottom(valueFormatter = CartesianValueFormatter { context, x, _ -> context.model.extraStore[key][x.toInt()] })` 读取；饼图用 `PieChartHost(rememberPieChart(sliceProvider = PieChart.SliceProvider.series(slices), valueFormatter = PieValueFormatter {...}), modelProducer)` + `pieSeries { series(*values) }`；柱状图颜色 `rememberLineComponent(Fill(color), 16.dp)`，折线 `LineCartesianLayer.Line(LineCartesianLayer.LineFill.single(Fill(color)))`。modelProducer 应持久化（remember/ViewModel），runTransaction 是挂起函数。

## 8. 二次开发指南

### 8.1 新增一个页面/功能的通用步骤
1. **数据层**：`data/model/` 加 @Serializable 模型（snake_case 字段直接命名，ignoreUnknownKeys 已开）；`CloudflareApi.kt` 加端点常量；`CloudflareRepository.kt` 加方法（用 client.get/post 的 reified 泛型）
2. **ViewModel**：新建 `ui/<feature>/XxxViewModel.kt`，StateFlow + `AppContainer.repository`；从 SavedStateHandle 读路由参数（用 `get<String>()`）
3. **UI**：新建 Screen；`Routes.kt` 加路由 + 构建函数；`AppNavHost.kt` 加 composable（navArgument 定义，可选参数 defaultValue=""）
4. 需要缓存+本地搜索的功能，参考 ZonesViewModel/DnsViewModel 的 allXxx + applyFilter + PullToRefreshBox 模式

### 8.2 常见修改点速查
| 需求 | 修改文件 |
|---|---|
| 改主题色 | ui/theme/Color.kt |
| 加底部 Tab / 改 Tab 动画 | ui/home/HomeScreen.kt（SlidingTab，visitedMask 常驻 + 水平平移） |
| 加路由 | navigation/Routes.kt + AppNavHost.kt |
| 加 API 端点 | data/api/CloudflareApi.kt + Repository |
| 加 Zone 设置开关 | data/model/ZoneSetting.kt + CloudflareApi/Repository + ZoneDetailViewModel/Screen |
| 加统计数据 | data/model/ZoneAnalytics.kt（AnalyticsParser 查询/解析 + 模型）+ Repository.getZoneAnalytics/getAccountAnalytics + ui/stats/ + HomeScreen/ZoneDetailScreen |
| 加统计图表 | ui/stats/StatsCharts.kt（TrendLineChart/BreakdownPieChart/ZoneBarChart，Vico 3.x）+ StatsContent 卡片编排 + StatsData 聚合字段 + AnalyticsParser 对应查询 |
| 改 DNS 记录字段 | data/model/DnsRecord.kt |
| 改认证逻辑 | ui/onboarding/OnboardingViewModel.kt + AuthCredential.kt + TokenStore.kt |

### 8.3 恢复"添加域名"功能（如需）
1. 恢复 data/model/Zone.kt 中的 `CreateZoneRequest`/`AccountRef`，新建 Account.kt
2. Repository 恢复 `createZone(name, accountId)` + `getAccounts()`
3. 新建 AddZoneScreen/AddZoneViewModel；ZonesScreen 加 FAB；Routes/AppNavHost 加 add_zone 路由

## 9. 版本记录
版本演进记录已迁移至 [`changelog.md`](changelog.md)，此处不再维护。
