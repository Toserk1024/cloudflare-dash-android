# TODO

> 工作规范：**新增/修改功能先写在这里攒批，等用户手动触发后**再按 计划 → 确认 → 实施 → 审查 → changelog → 推送 流程执行。
> 不要直接改代码提交。

## 待办需求（攒批中，未实施）

- [x] **统计图表点击显示详情（Marker）**：**折线趋势图点击拐点 → 显示该点时间与对应数据量**（如点击请求数趋势拐点显示"08-07 14时 · 1.2万请求"、带宽显示"… · 3.2GB"）；**域名柱状图点击柱子 → 显示该 host 与请求量**（Vico 3.x CartesianMarker，数据经 extras 标签 + valueFormatter 格式化）；**饼图点击查看该项具体数据（量）**——点击扇区/图例项弹出或高亮显示该标签对应的具体数量与占比（如点击"美国"→显示"美国：1.2万 · 12%"；Vico 3.x 饼图 slice 点击交互，若不支持则用图例项点击 + 选中高亮/外部详情区实现）— 2026-08-08（批次1已完成：折线/柱状 CartesianMarker、饼图图例点击+高亮+详情行）
- [x] **饼图具体项显示个数**：维度分布饼图（国家/状态码/缓存）每个具体项显示**数量**——当前扇区标签仅百分比、下方图例已显示数量但不够直观；增强：扇区标签改为"百分比 + 数量"（如 `12% · 1.2万`）或图例数量更突出（BreakdownPieChart 的 PieValueFormatter/图例行调整）— 2026-08-08（批次1已完成：图例点击显示"名称：数量 · 占比"详情行）
- [x] **侧边栏宽度收窄**：打开时占用水平空间小一点（当前 ModalDrawerSheet 默认宽度偏大，实施时调小宽度如 280~320dp）— 2026-08-08（批次1已完成：300dp）
- [x] **点击页面空白处关闭侧边栏**：打开侧边栏后点击 scrim/空白区域应关闭（当前 gesturesEnabled=false，需确认 scrim 点击关闭是否生效，若被禁用则恢复 tap-to-close）— 2026-08-08（批次1已完成：ModalNavigationDrawer 内置 scrim tap-to-close，与 gesturesEnabled 无关）
- [x] **域名详情页三个高级设置开关单独禁用**：当前 `settingsBusy` 非 null 时三个开关（开发模式/五秒盾/IPv6）一起禁用；改为仅正在切换的那个开关禁用/转圈，其余保持可用（ZoneDetailScreen `AdvancedSwitchRow` 的 enabled/busy 逻辑调整）— 2026-08-08（批次1已完成）
- [x] **多用户功能**：支持多个 Cloudflare 账号并存；新建用户与切换用户入口均在**侧边栏上部**（用户信息卡区域）——涉及 TokenStore 多凭据存储（按用户隔离）、Onboarding 新建流程、侧边栏用户卡 + 新建/切换入口，实施时需详细设计— 2026-08-08（批次4已完成：TokenStore多用户/Onboarding saveUser/侧边栏切换+新建/退出删除激活用户）
- [x] **TTL 显示可读化（时分秒）**：DNS 记录 TTL（秒）展示为友好格式（如 300→5分钟、3600→1小时、86400→1天；复用现有 formatRemaining 类逻辑，实施时定位 DnsRecord 相关展示处统一格式化）— 2026-08-08（批次1已完成：新增 formatTtl，列表/编辑页/TTL 下拉均应用）
- [x] **DNS 记录列表批量操作（候选框）**：每条记录前加**复选框**，支持批量：
  - **批量删除**（二次确认，逐条 DELETE 并本地同步移除）
  - **批量开关代理**：**仅对 A/AAAA/CNAME（PROXIABLE）类型生效**（其余类型复选框禁用或忽略），逐条 PATCH proxied 并本地同步
  - 全选/取消全选；批量操作栏（选中数 + 删除/代理开关按钮）；操作中防抖与失败提示
  - 涉及：DnsRecordsContent（行复选框 + 批量操作栏）、DnsViewModel（selected 集合/全选/批量删除/批量代理）、DnsRecord（proxied 更新）— 2026-08-08（批次2已完成：复选框+批量栏+二次确认+仅可代理类型生效）
- [x] **DNS 记录完整表单（全部类型按字段拆分重写，仿 Cloudflare 控制台）**：当前所有类型基本只让用户填 name/content 自由文本（SRV/DNSKEY 等的 data 字段未用），填写全靠猜；**重写 DNS 记录编辑页**，每种记录类型渲染**完整字段表单**并序列化为 `DnsRecordRequest.data`/content：
  - **A/AAAA**：名称 + IPv4/IPv6 地址（目标）+ TTL + 代理开关 + 说明"[名称] 是 [IPv4 地址] 的别名"
  - **CNAME**：名称 + 目标（别名指向）+ 说明"[名称] 是 [目标] 的别名"
  - **MX**：名称 + 优先级 + 邮件服务器（目标）+ TTL + 说明"[名称] 的邮件由 [目标] 处理"
  - **TXT/NS**：名称 + 内容/目标服务器 + TTL
  - **SRV**：服务名/协议/名称 + 优先级/权重/端口/目标（主机名）+ TTL
  - **DNSKEY**：算法/协议/标志/公钥
  - **CAA**：标志/标签（issue/issuewild/iodef）/值（证书颁发机构）
  - **SVCB/HTTPS**：优先级/目标/参数（alpn、port、ipv4hint 等）
  - **SSHFP**：算法/指纹类型/指纹
  - **TLSA**：用法/选择器/匹配类型/证书关联数据
  - **NAPTR**：优先级/权重/顺序/标志/服务/正则表达式/替换
  - **URI**：优先级/权重/目标 URI
  - 每种类型**顶部直观说明文案**（如 "[名称] 是 [目标] 的别名。"），字段带 placeholder/校验
  - 涉及：DnsRecordEditScreen（按类型动态渲染字段模板）、DnsEditViewModel（分类型状态+校验+data/content 序列化）、DnsRecordTypes（字段模板/说明文案表）、DnsRecord/DnsRecordRequest（data 解析回填）— 2026-08-08

## Pending 队列（用户反馈待修复/优化，先不实施）

- [ ] **点击空白关闭侧边栏未实现**：批次1 的 #4 声称"ModalNavigationDrawer 内置 scrim tap-to-close 已支持"，但实测点击空白不关闭——需排查是否为 `gesturesEnabled=false` 连带禁用了 scrim 点击，或 SlidingTab/内容层拦截了点击事件（修复方向：改用带 scrim 点击处理的方案，或恢复 `gesturesEnabled` 的 tap 部分）— 2026-08-08 用户反馈
- [ ] **批量代理确认对话框按钮文案不当**：批量开/关代理的 AlertDialog 确认按钮当前显示"开启"/"关闭"，应统一为 **"确定" / "取消"**（DnsRecordsContent 批量代理对话框 confirmButton/dismissButton 文案）— 2026-08-08 用户反馈
- [ ] **DNS 候选框常显拥挤，改为图标按钮启用**：候选框/复选框一直显示显得拥挤——改为**顶栏右侧加 check-all 图标按钮**点击后启用候选功能；批量操作栏的**文字按钮加边框**（当前无边框太虚，用 OutlinedButton 或 border 修饰）— 2026-08-08 用户反馈
- [ ] **DNS 编辑页说明"[标签]"动态替换（新建随输入、编辑按已有内容回填）**：编辑页顶部说明文案中的 `[名称]`、`[目标]` 等占位符需替换为实际值——**新建**：随输入实时替换（name/target 等字段）；**编辑已有记录**（从 DNS 记录页点进编辑页时）：用**回填的实际记录内容**（name/content/data）替换占位符，而非一直显示"[名称]""[目标]"字面占位符（DnsRecordEditScreen 说明卡 + DnsEditViewModel 回填后生成说明）— 2026-08-08 用户反馈
- [ ] **饼图点击预览功能不成功**：批次1 实现的"图例点击显示详情行"（BreakdownPieChart 选中高亮 + 详情）未生效/体验不佳——需排查图例点击事件是否被卡片/容器拦截，或改用扇区点击 + 弹出详情（实施时验证交互）— 2026-08-08 用户反馈
- [ ] **折线统计图 marker 展示框过小**：点击拐点显示的数值只能显示约三个字符（marker label 默认组件过窄，大数值被截断）——建议用 **Card/clip 等有底容器**承接 marker label 内容，设置合适宽度/内边距（TrendLineChart 的 rememberDefaultCartesianMarker label 组件改造）— 2026-08-08 用户反馈

## 已实施（历史，仅供参考）

（功能实现记录见 changelog.md）
