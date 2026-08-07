# Changelog

## 历史版本记录（v1 ~ v1.5，迁移自 agent.md）

- **v1**：基础框架（Onboarding 验证 + 域名列表 + DNS CRUD + 我的）→ 构建通过
- **v1.1**：修复验证流程 Bug（tokenOverride）、Token 格式校验
- **v1.2**：修复"我的"转圈（状态机）、移除添加域名、修复 DNS 编辑导航
- **v1.3**：全量缓存 + 本地搜索 + 下拉刷新（PullToRefreshBox）+ DNS FAB
- **v1.4**：Tab/导航过渡动画（AnimatedContent + NavHost transitions）、修复请求体序列化（reified BodyT）
- **v1.5**：移除本地构建环境（ARM64 AAPT2 hack、setup_android_env.sh、tools/、gradle wrapper 本地 distributionUrl），改用 GitHub Actions CI 构建（手动触发）

## 2026-08-05 · 本次构建改造

- 包名定稿为 `io.github.toserk1024.cfdash`（规避商标风险；此前曾迁移至 com.cloudflare.dash3rd）
- 版本号机制：
  - versionName 采用 `日期_自增序号`（如 `2026.08.08_1`，序号每次构建 +1，应用内展示前缀 v）
  - versionCode 采用构建时间(unix秒)前 9 位截取，规避 32 位 Int 极限
  - 构建日期按 **UTC+8**（Asia/Shanghai）计算
- 主题：曾尝试黑白灰极客风（含 OLED 纯黑），**已回滚为 Cloudflare 橙主题**（沿用默认 dynamic color）
- 关于页：动态显示版本号（前缀 v）+ 独立 **AssistChip** 开源仓库入口（带图标，点击打开浏览器）
- CI 构建方式由 debug 改为 **release**，开启 **R8 压缩**；**push 到 main 自动触发**构建
- 正式签名支持：`signingConfigs.release` 通过环境变量（`BUILD_STORE_FILE/BUILD_STORE_PASSWORD/BUILD_KEY_ALIAS/BUILD_KEY_PASSWORD`）引入，未配置自动回退 debug 签名；**仅 v2+v3**（v1 关闭，v3 由 AGP 8+ 默认开启）；CI 从 GitHub Secrets 解码 keystore（workflow 用 env 注入，`if` 条件不可直接用 secrets；keystore 路径用 `rootProject.file()` 相对仓库根解析，修复 CI 双重 `app/app/` 路径问题）
- 修复 R8 构建：补充 kotlinx.serialization keep 规则与 Tink 编译期注解 `-dontwarn` 规则
- README 调整：更名 `readme.md`，顶部新增 vibe coding 警示提示，并指向 `agent.md` / `changelog.md`

## 会话内累积更新（本次之前的修改）

- **底部导航 Tab 切换卡顿修复**：三个 Tab 首次访问后常驻组合，切换仅做 150ms 透明度过渡（GPU 合成），根治 AnimatedContent 每次切换销毁/重建整页造成的掉帧
- **DNS 记录编辑/新建后列表自动更新**：新增跨 ViewModel 同步队列（`DnsRecordsSync`），保存成功后本地更新缓存（编辑原位替换、新建追加），无需重新请求 API；删除记录保持本地同步
- 首次包名迁移（`com.java.myapplication` → `com.cloudflare.dash3rd` → 本次定稿 `io.github.toserk1024.cfdash`）
