# Changelog

## 2026-08-05 · 本次构建改造

- 包名定稿为 `io.github.toserk1024.cfdash`（规避商标风险；此前曾迁移至 com.cloudflare.dash3rd）
- 版本号机制：
  - versionName 采用 `v日期_自增序号`（如 `v2026.08.08_123`，序号每次构建 +1）
  - versionCode 采用构建时间(unix秒)前 9 位截取，规避 32 位 Int 极限
- CI 构建方式由 debug 改为 **release**，并开启 **R8 压缩**（节约打包资源、减小体积）
- 修复 R8 构建：补充 kotlinx.serialization keep 规则与 Tink 编译期注解 `-dontwarn` 规则
- README 调整：更名 `readme.md`，顶部新增 vibe coding 红色提示，并指向 `agent.md` / `changelog.md`

## 会话内累积更新（本次之前的修改）

- **底部导航 Tab 切换卡顿修复**：三个 Tab 首次访问后常驻组合，切换仅做 150ms 透明度过渡（GPU 合成），根治 AnimatedContent 每次切换销毁/重建整页造成的掉帧
- **DNS 记录编辑/新建后列表自动更新**：新增跨 ViewModel 同步队列（`DnsRecordsSync`），保存成功后本地更新缓存（编辑原位替换、新建追加），无需重新请求 API；删除记录保持本地同步
- 首次包名迁移（`com.java.myapplication` → `com.cloudflare.dash3rd` → 本次定稿 `io.github.toserk1024.cfdash`）
