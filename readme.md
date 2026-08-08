# Cloudflare 客户端（Android）

> [!CAUTION]
> 本项目由 **Vibe Coding**（AI 辅助快速迭代）开发，功能以实际使用为准，可能留有瑕疵，恳请见谅，欢迎反馈改进。

基于 Jetpack Compose + Material 3 的 Cloudflare 第三方客户端（包名 `io.github.toserk1024.cfdash`），支持域名管理与 DNS 记录管理。

- 📖 **二次开发指南** → [`agent.md`](agent.md)
- 📝 **更新记录** → [`changelog.md`](changelog.md)

## ✨ 功能

- **认证登录**：支持 **Global API Key**（默认优先）与 **API Token** 双方式，验证通过后进入主界面；Token 模式展示所需权限清单
- **侧边栏导航**：域名 / DNS / 统计数据 / 我的 四个入口，未来可扩展更多功能项
- **域名管理**：列表（全量缓存 + 本地搜索 + 下拉刷新）/ 详情（NS、状态、套餐）/ 删除域名
- **DNS 记录管理**：按域名查看（全量缓存 + 类型筛选 + 本地搜索 + 下拉刷新）/ 新建 / 编辑 / 删除，支持 A、AAAA、CNAME、MX、TXT、NS、SRV、CAA 等类型
- **统计数据**：账号级（侧边栏入口）与域名级（域名详情）双维度，请求数 / 威胁数 / 带宽 / 缓存命中率 / 独立访客；**Vico 图表化展示**：请求数与带宽时间趋势折线图（24小时/7天/30天）、国家地区 / HTTP 状态码 / 缓存状态分布饼图、账号级域名流量拆分柱状图
- **我的**：用户信息展示、版本号、开源地址、退出登录
- **安全**：凭据使用 EncryptedSharedPreferences（Android Keystore）加密存储，仅保存在本机
- **性能**：API 数据首次/下拉刷新时缓存到内存，搜索筛选纯本地完成；四个 Tab 常驻组合，切换仅水平平移过渡，流畅无重建

## 🔒 需要的 API Token 权限

在 https://dash.cloudflare.com/profile/api-tokens 创建 Token 时，建议勾选：

| 权限 | 说明 |
|---|---|
| Zone → Zone → Read / Edit | 域名列表、详情、删除 |
| Zone → DNS → Read / Edit | DNS 记录查看与增删改 |
| Zone → Zone Settings → Read / Edit | 高级设置（开发模式 / 五秒盾 / IPv6） |
| Zone → Analytics → Read | 统计数据（请求/威胁/带宽/缓存命中率） |
| User → User Details → Read | 验证与用户信息 |

> 也支持 **Global API Key**（邮箱 + API Key，`X-Auth-Email` / `X-Auth-Key`）登录，拥有账号全部权限，请谨慎保管。

## 🛠️ 技术栈

- Kotlin + Jetpack Compose + Material 3（Cloudflare 橙主题）
- MVVM：ViewModel + StateFlow + Repository
- OkHttp（Bearer 认证）+ kotlinx.serialization
- Navigation Compose（单 Activity 多路由）
- Vico（Compose 原生图表库）
- EncryptedSharedPreferences（安全存储）
- Release 构建开启 **R8 压缩**；versionName 采用 `日期_自增序号`、versionCode 采用时间戳前 9 位

## 📁 代码结构

```
app/src/main/java/io/github/toserk1024/cfdash/
├── MainActivity.kt            # 入口：登录态路由（Onboarding / Home）
├── AppContainer.kt            # 全局依赖容器
├── navigation/
│   ├── Routes.kt              # 路由定义
│   └── AppNavHost.kt          # 导航图
├── data/
│   ├── model/                 # @Serializable 数据模型
│   ├── api/                   # OkHttp 客户端封装 + 端点常量
│   ├── repository/            # 业务仓储层
│   └── storage/               # Token 加密存储
└── ui/
    ├── onboarding/            # 初始化（Token 验证）
    ├── home/                  # 主界面（底部导航，Tab 常驻组合）
    ├── zones/                 # 域名管理
    ├── dns/                   # DNS 记录管理
    └── profile/               # 我的
```

## 🚀 构建

构建由 **GitHub Actions** 自动完成（`.github/workflows/build.yml`，**push 到 main 自动触发**，也可手动触发；release + R8）：

1. `git push origin main` → 自动开始构建（Actions → Build APK）
2. 构建完成，在 Run 页底部 **Artifacts** 下载 `app-release-apk`
3. 解压得到 `app-release.apk`，安装到设备：`adb install app-release.apk`

> 本地不保留 Android SDK / Gradle 构建环境，全部走 CI 构建。

## ⚠️ 说明

- 删除域名/DNS 记录为破坏性操作，均有二次确认对话框
- 数据采用"首次/下拉刷新拉全量 + 内存缓存 + 本地搜索"策略，避免频繁请求 API
- 高级功能（Workers、Zero Trust 等）暂未开发，后续版本规划中
- 二次开发详见 [`agent.md`](agent.md)，更新记录详见 [`changelog.md`](changelog.md)
