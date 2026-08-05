# Cloudflare 客户端（Android）

基于 Jetpack Compose + Material 3 的 Cloudflare 第三方客户端，支持域名管理与 DNS 记录管理。

## ✨ 功能

- **初始化向导**：输入 API Token → 调用 `GET /user/tokens/verify` 验证 → 成功进入主界面
- **域名管理**：列表（全量缓存 + 本地搜索 + 下拉刷新）/ 详情（NS、状态、套餐）/ 删除域名
- **DNS 记录管理**：按域名查看（全量缓存 + 类型筛选 + 本地搜索 + 下拉刷新）/ 新建 / 编辑 / 删除，支持 A、AAAA、CNAME、MX、TXT、NS、SRV、CAA 等类型
- **我的**：用户信息展示、退出登录（清除 Token）
- **安全**：API Token 使用 EncryptedSharedPreferences（Android Keystore）加密存储，仅保存在本机
- **性能**：API 数据首次/下拉刷新时缓存到内存，搜索筛选纯本地完成，Tab/页面切换带过渡动画

## 🔒 需要的 API Token 权限

在 https://dash.cloudflare.com/profile/api-tokens 创建 Token 时，建议勾选：

| 权限 | 说明 |
|---|---|
| Zone → Zone → Read / Edit | 域名列表、详情、删除 |
| Zone → DNS → Read / Edit | DNS 记录查看与增删改 |
| User → User Details → Read | 验证 Token 与用户信息 |

## 🛠️ 技术栈

- Kotlin + Jetpack Compose + Material 3（Cloudflare 橙主题）
- MVVM：ViewModel + StateFlow + Repository
- OkHttp（Bearer 认证）+ kotlinx.serialization
- Navigation Compose（单 Activity 多路由）
- EncryptedSharedPreferences（安全存储）

## 📁 代码结构

```
app/src/main/java/com/java/myapplication/
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
    ├── home/                  # 主界面（底部导航）
    ├── zones/                 # 域名管理
    ├── dns/                   # DNS 记录管理
    └── profile/               # 我的
```

## 🚀 构建

构建由 **GitHub Actions** 完成（`.github/workflows/build.yml`，手动触发）：

1. 仓库 → **Actions** → **Build APK** → **Run workflow**
2. 等待构建完成，在 Run 页底部 **Artifacts** 下载 `app-debug-apk`
3. 解压得到 `app-debug.apk`，安装到设备：`adb install app-debug.apk`

> 本地不保留 Android SDK / Gradle 构建环境，全部走 CI 构建。

## ⚠️ 说明

- 删除域名/DNS 记录为破坏性操作，均有二次确认对话框
- 数据采用"首次/下拉刷新拉全量 + 内存缓存 + 本地搜索"策略，避免频繁请求 API
- 高级功能（Workers、Zero Trust 等）暂未开发，后续版本规划中
- 二次开发详见 `agent.md`
