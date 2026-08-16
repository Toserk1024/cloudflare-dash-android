package io.github.toserk1024.cfdash.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.toserk1024.cfdash.AppContainer
import io.github.toserk1024.cfdash.data.model.DnsRecord
import io.github.toserk1024.cfdash.ui.cache.CacheContent
import io.github.toserk1024.cfdash.ui.cache.CacheViewModel
import io.github.toserk1024.cfdash.ui.dns.DnsRecordsContent
import io.github.toserk1024.cfdash.ui.dns.DnsViewModel
import io.github.toserk1024.cfdash.ui.network.NetworkTab
import io.github.toserk1024.cfdash.ui.network.NetworkViewModel
import io.github.toserk1024.cfdash.ui.profile.ProfileScreen
import io.github.toserk1024.cfdash.ui.security.SecurityTab
import io.github.toserk1024.cfdash.ui.security.SecurityViewModel
import io.github.toserk1024.cfdash.ui.speed.SpeedTab
import io.github.toserk1024.cfdash.ui.speed.SpeedViewModel
import io.github.toserk1024.cfdash.ui.stats.StatsContent
import io.github.toserk1024.cfdash.ui.stats.StatsMode
import io.github.toserk1024.cfdash.ui.stats.StatsViewModel
import io.github.toserk1024.cfdash.ui.theme.CloudflareOrange
import io.github.toserk1024.cfdash.ui.zones.ZoneDetailTab
import io.github.toserk1024.cfdash.ui.zones.ZonePicker
import io.github.toserk1024.cfdash.ui.zones.ZoneViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 主界面：自绘侧边栏 + 八个常驻 Tab（域名/DNS/统计/缓存/速度/网络/安全/我的）。
 * 全局域名选择器（ZoneViewModel）统一驱动 域名/DNS/统计/缓存/速度/网络/安全 七 Tab；
 * 横栏右侧域名按钮始终显示，统计切换按钮（账户/域名级）仅统计 Tab 显示（带动画）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onDnsEdit: (String, String?, String?) -> Unit,
    onLogout: () -> Unit,
    homeKey: Int = 0,
    initialTab: Int = 0,
    onTabChange: (Int) -> Unit = {},
    onNewUser: () -> Unit = {},
    onUserSwitched: () -> Unit = {},
    homeViewModel: HomeViewModel = viewModel(),
    zoneViewModel: ZoneViewModel = viewModel(),
    dnsViewModel: DnsViewModel = viewModel(),
    statsViewModel: StatsViewModel = viewModel(),
    cacheViewModel: CacheViewModel = viewModel(),
    speedViewModel: SpeedViewModel = viewModel(),
    networkViewModel: NetworkViewModel = viewModel(),
    securityViewModel: SecurityViewModel = viewModel()
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) }
    val homeState by homeViewModel.uiState.collectAsState()
    val zoneState by zoneViewModel.uiState.collectAsState()
    val statsState by statsViewModel.uiState.collectAsState()

    // 全局选中域名变化 → 同步 DNS / 缓存 / 统计（域名级）/ 速度 / 网络
    val selectedZone = zoneState.selectedZone
    LaunchedEffect(selectedZone?.id) {
        dnsViewModel.setZone(selectedZone)
        cacheViewModel.setZone(selectedZone)
        statsViewModel.setZone(selectedZone?.id)
        speedViewModel.setZone(selectedZone)
        networkViewModel.setZone(selectedZone)
        securityViewModel.setZone(selectedZone)
    }

    // 多用户：切换/退出后 homeKey 变化 → 重新加载当前激活用户数据
    LaunchedEffect(homeKey) {
        homeViewModel.loadUser()
    }

    // 已访问过的 Tab 位掩码：懒加载，首次访问后才组合并常驻
    var visitedMask by rememberSaveable { mutableIntStateOf(1) }
    LaunchedEffect(selectedTab) {
        visitedMask = visitedMask or (1 shl selectedTab)
        onTabChange(selectedTab)
    }

    val configuration = LocalConfiguration.current
    val screenWidthPx = with(LocalDensity.current) { configuration.screenWidthDp.dp.toPx() }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val tabTitles = listOf("域名", "DNS", "统计", "缓存", "速度", "网络", "安全", "我的")

    // 域名选择器覆盖层开关
    var showZonePicker by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // ===== 主内容 =====
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(tabTitles[selectedTab], maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "菜单")
                        }
                    },
                    actions = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.animateContentSize()
                        ) {
                            // 统计切换按钮（仅统计 Tab，在外/最右，带动画）
                            AnimatedVisibility(
                                visible = selectedTab == 2,
                                enter = fadeIn(tween(200)) + expandHorizontally(tween(200)),
                                exit = fadeOut(tween(150)) + shrinkHorizontally(tween(150))
                            ) {
                                TextButton(
                                    onClick = {
                                        statsViewModel.setMode(
                                            if (statsState.mode == StatsMode.ACCOUNT) StatsMode.ZONE else StatsMode.ACCOUNT
                                        )
                                    }
                                ) {
                                    Text(if (statsState.mode == StatsMode.ACCOUNT) "账户级" else "域名级")
                                }
                            }
                            // 域名选择按钮（始终，在切换按钮内侧）
                            IconButton(onClick = { showZonePicker = true }) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = "选择域名")
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                // 域名 Tab（常驻）：当前选中域名详情
                if ((visitedMask and 0b001) != 0) {
                    SlidingTab(selected = selectedTab == 0, targetOffset = slideOffsetFor(selectedTab, 0, screenWidthPx)) {
                        ZoneDetailTab(
                            state = zoneState,
                            onSetDevMode = zoneViewModel::setDevelopmentMode,
                            onSetUnderAttack = zoneViewModel::setUnderAttack,
                            onSetIpv6 = zoneViewModel::setIpv6,
                            onRetrySettings = zoneViewModel::refreshSettings
                        )
                    }
                }
                // DNS Tab（常驻）
                if ((visitedMask and 0b010) != 0) {
                    SlidingTab(selected = selectedTab == 1, targetOffset = slideOffsetFor(selectedTab, 1, screenWidthPx)) {
                        val dnsState by dnsViewModel.uiState.collectAsState()
                        DnsRecordsContent(
                            onEditRecord = { record: DnsRecord ->
                                onDnsEdit(dnsState.selectedZone?.id.orEmpty(), record.id, dnsState.selectedZone?.name)
                            },
                            onAddRecord = {
                                onDnsEdit(dnsState.selectedZone?.id.orEmpty(), null, dnsState.selectedZone?.name)
                            },
                            onOpenZonePicker = { showZonePicker = true },
                            viewModel = dnsViewModel
                        )
                    }
                }
                // 统计 Tab（常驻）
                if ((visitedMask and 0b100) != 0) {
                    SlidingTab(selected = selectedTab == 2, targetOffset = slideOffsetFor(selectedTab, 2, screenWidthPx)) {
                        StatsContent(
                            data = statsState.data,
                            loading = statsState.loading,
                            error = statsState.error,
                            range = statsState.range,
                            showZoneBreakdown = statsState.mode == StatsMode.ACCOUNT,
                            refreshing = statsState.refreshing,
                            enablePullRefresh = true,
                            partError = statsState.partError,
                            onRangeChange = statsViewModel::setRange,
                            onRefresh = statsViewModel::refresh,
                            onRetry = statsViewModel::load
                        )
                    }
                }
                // 缓存 Tab（常驻）
                if ((visitedMask and 0b1000) != 0) {
                    SlidingTab(selected = selectedTab == 3, targetOffset = slideOffsetFor(selectedTab, 3, screenWidthPx)) {
                        CacheContent(viewModel = cacheViewModel, onOpenZonePicker = { showZonePicker = true })
                    }
                }
                // 速度 Tab（常驻）
                if ((visitedMask and 0b10000) != 0) {
                    SlidingTab(selected = selectedTab == 4, targetOffset = slideOffsetFor(selectedTab, 4, screenWidthPx)) {
                        val speedState by speedViewModel.uiState.collectAsState()
                        SpeedTab(
                            state = speedState,
                            planName = zoneState.selectedZone?.plan?.name,
                            onSetSetting = speedViewModel::setSetting,
                            onRetry = speedViewModel::refreshSettings
                        )
                    }
                }
                // 网络 Tab（常驻）
                if ((visitedMask and 0b100000) != 0) {
                    SlidingTab(selected = selectedTab == 5, targetOffset = slideOffsetFor(selectedTab, 5, screenWidthPx)) {
                        val networkState by networkViewModel.uiState.collectAsState()
                        NetworkTab(
                            state = networkState,
                            ipv6 = zoneState.ipv6,
                            ipv6Busy = "ipv6" in zoneState.settingsBusy,
                            onSetIpv6 = zoneViewModel::setIpv6,
                            onSetSetting = networkViewModel::setSetting,
                            onRetry = networkViewModel::refreshSettings
                        )
                    }
                }
                // 安全 Tab（常驻）
                if ((visitedMask and 0b1000000) != 0) {
                    SlidingTab(selected = selectedTab == 6, targetOffset = slideOffsetFor(selectedTab, 6, screenWidthPx)) {
                        val securityState by securityViewModel.uiState.collectAsState()
                        SecurityTab(
                            state = securityState,
                            onGroupBy = securityViewModel::setGroupBy,
                            onTimeRange = securityViewModel::setTimeRange,
                            onAddFilter = securityViewModel::addFilter,
                            onRemoveFilter = securityViewModel::removeFilter,
                            onClearFilters = securityViewModel::clearFilters,
                            onRefresh = securityViewModel::refresh,
                            onRetry = securityViewModel::load
                        )
                    }
                }
                // 我的 Tab（常驻）
                if ((visitedMask and 0b10000000) != 0) {
                    SlidingTab(selected = selectedTab == 7, targetOffset = slideOffsetFor(selectedTab, 7, screenWidthPx)) {
                        ProfileScreen(
                            uiState = homeState,
                            onRetry = homeViewModel::loadUser
                        )
                    }
                }
            }
        }

        // ===== 自绘侧边栏 =====
        AnimatedVisibility(
            visible = drawerState.isOpen,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it },
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { scope.launch { drawerState.close() } }
                )
                ModalDrawerSheet(modifier = Modifier.align(Alignment.CenterStart).width(300.dp)) {
                    val user = homeState.user
                    val activeUser = remember(homeKey) { AppContainer.tokenStore.getActiveUser() }
                    val allUsers = remember(homeKey) { AppContainer.tokenStore.getUsers() }
                    var switchMenu by remember { mutableStateOf(false) }
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)) {
                        Surface(shape = CircleShape, color = CloudflareOrange) {
                            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = user?.username?.firstOrNull()?.uppercase()
                                        ?: activeUser?.label?.firstOrNull()?.uppercase() ?: "C",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = user?.username?.ifBlank { "Cloudflare 用户" } ?: activeUser?.label ?: "Cloudflare 用户",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = user?.email ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                            TextButton(onClick = { switchMenu = true }) { Text("切换用户") }
                            TextButton(onClick = onNewUser) { Text("新建用户") }
                        }
                        DropdownMenu(expanded = switchMenu, onDismissRequest = { switchMenu = false }) {
                            allUsers.forEach { u ->
                                DropdownMenuItem(
                                    text = { Text(u.label) },
                                    onClick = {
                                        AppContainer.tokenStore.setActiveUser(u.id)
                                        switchMenu = false
                                        onUserSwitched()
                                    }
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("域名") },
                        selected = selectedTab == 0,
                        onClick = { scope.launch { drawerState.close() }; selectedTab = 0 }
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        label = { Text("DNS") },
                        selected = selectedTab == 1,
                        onClick = { scope.launch { drawerState.close() }; selectedTab = 1 }
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                        label = { Text("统计") },
                        selected = selectedTab == 2,
                        onClick = { scope.launch { drawerState.close() }; selectedTab = 2 }
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.AutoDelete, contentDescription = null) },
                        label = { Text("缓存") },
                        selected = selectedTab == 3,
                        onClick = { scope.launch { drawerState.close() }; selectedTab = 3 }
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Speed, contentDescription = null) },
                        label = { Text("速度") },
                        selected = selectedTab == 4,
                        onClick = { scope.launch { drawerState.close() }; selectedTab = 4 }
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Wifi, contentDescription = null) },
                        label = { Text("网络") },
                        selected = selectedTab == 5,
                        onClick = { scope.launch { drawerState.close() }; selectedTab = 5 }
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Security, contentDescription = null) },
                        label = { Text("安全") },
                        selected = selectedTab == 6,
                        onClick = { scope.launch { drawerState.close() }; selectedTab = 6 }
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Person, contentDescription = null) },
                        label = { Text("我的") },
                        selected = selectedTab == 7,
                        onClick = { scope.launch { drawerState.close() }; selectedTab = 7 }
                    )

                    Spacer(modifier = Modifier.weight(1f))
                    HorizontalDivider()
                    NavigationDrawerItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        label = { Text("退出登录", color = MaterialTheme.colorScheme.error) },
                        selected = false,
                        onClick = { scope.launch { drawerState.close() }; onLogout() }
                    )
                }
            }
        }

        // ===== 域名选择器覆盖层（全屏独立页面，覆盖整个含横栏，带动画）=====
        AnimatedVisibility(
            visible = showZonePicker,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.fillMaxSize()
        ) {
            ZonePicker(
                zones = zoneState.zones,
                loading = zoneState.loading,
                error = zoneState.error,
                selectedZone = zoneState.selectedZone,
                onSelect = { zone ->
                    zoneViewModel.selectZone(zone)
                    showZonePicker = false
                },
                onDismiss = { showZonePicker = false }
            )
        }
    }
}

@Composable
private fun SlidingTab(
    selected: Boolean,
    targetOffset: Float,
    content: @Composable () -> Unit
) {
    val offsetX by animateFloatAsState(
        targetValue = targetOffset,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "tabSlide"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (selected) 1f else 0f)
            .offset { IntOffset(offsetX.roundToInt(), 0) }
    ) {
        content()
    }
}

private fun slideOffsetFor(selectedTab: Int, tabIndex: Int, width: Float): Float = when {
    tabIndex < selectedTab -> -width
    tabIndex > selectedTab -> width
    else -> 0f
}