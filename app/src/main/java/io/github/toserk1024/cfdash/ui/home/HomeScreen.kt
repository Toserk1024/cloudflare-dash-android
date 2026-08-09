package io.github.toserk1024.cfdash.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
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
import io.github.toserk1024.cfdash.data.model.Zone
import io.github.toserk1024.cfdash.ui.dns.DnsRecordsContent
import io.github.toserk1024.cfdash.ui.dns.DnsViewModel
import io.github.toserk1024.cfdash.ui.profile.ProfileScreen
import io.github.toserk1024.cfdash.ui.stats.StatsContent
import io.github.toserk1024.cfdash.ui.stats.StatsViewModel
import io.github.toserk1024.cfdash.ui.theme.CloudflareOrange
import io.github.toserk1024.cfdash.ui.zones.ZonesScreen
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 主界面：自绘侧边栏（无右滑手势，点击空白 scrim 关闭）+ 四个常驻 Tab。
 * 卡顿优化：Tab 首次访问后常驻组合，切换仅水平平移过渡（offset 位移，GPU 合成）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onZoneClick: (Zone) -> Unit,
    onDnsEdit: (String, String?, String?) -> Unit,
    onLogout: () -> Unit,
    homeKey: Int = 0,
    onNewUser: () -> Unit = {},
    onUserSwitched: () -> Unit = {},
    homeViewModel: HomeViewModel = viewModel(),
    dnsViewModel: DnsViewModel = viewModel(),
    statsViewModel: StatsViewModel = viewModel()
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val homeState by homeViewModel.uiState.collectAsState()
    val statsState by statsViewModel.uiState.collectAsState()

    // 多用户：切换/退出后 homeKey 变化 → 重新加载当前激活用户数据
    LaunchedEffect(homeKey) {
        homeViewModel.loadUser()
    }

    // 已访问过的 Tab 位掩码：懒加载，首次访问后才组合并常驻
    var visitedMask by rememberSaveable { mutableIntStateOf(1) }
    LaunchedEffect(selectedTab) {
        visitedMask = visitedMask or (1 shl selectedTab)
    }

    // 水平平移动画所需的屏幕宽度（px）
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(LocalDensity.current) { configuration.screenWidthDp.dp.toPx() }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val tabTitles = listOf("域名", "DNS", "统计数据", "我的")

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
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                // 域名 Tab（常驻）
                if ((visitedMask and 0b001) != 0) {
                    SlidingTab(
                        selected = selectedTab == 0,
                        targetOffset = slideOffsetFor(selectedTab, 0, screenWidthPx)
                    ) {
                        ZonesScreen(onZoneClick = onZoneClick)
                    }
                }
                // DNS Tab（常驻）
                if ((visitedMask and 0b010) != 0) {
                    SlidingTab(
                        selected = selectedTab == 1,
                        targetOffset = slideOffsetFor(selectedTab, 1, screenWidthPx)
                    ) {
                        val dnsState by dnsViewModel.uiState.collectAsState()
                        DnsRecordsContent(
                            onEditRecord = { record: DnsRecord ->
                                onDnsEdit(dnsState.selectedZone?.id.orEmpty(), record.id, dnsState.selectedZone?.name)
                            },
                            onAddRecord = {
                                onDnsEdit(dnsState.selectedZone?.id.orEmpty(), null, dnsState.selectedZone?.name)
                            },
                            viewModel = dnsViewModel
                        )
                    }
                }
                // 统计 Tab（常驻）
                if ((visitedMask and 0b100) != 0) {
                    SlidingTab(
                        selected = selectedTab == 2,
                        targetOffset = slideOffsetFor(selectedTab, 2, screenWidthPx)
                    ) {
                        StatsContent(
                            data = statsState.data,
                            loading = statsState.loading,
                            error = statsState.error,
                            range = statsState.range,
                            showZoneBreakdown = true,
                            refreshing = statsState.refreshing,
                            enablePullRefresh = true,
                            partError = statsState.partError,
                            onRangeChange = statsViewModel::setRange,
                            onRefresh = statsViewModel::refresh,
                            onRetry = statsViewModel::load
                        )
                    }
                }
                // 我的 Tab（常驻）
                if ((visitedMask and 0b1000) != 0) {
                    SlidingTab(
                        selected = selectedTab == 3,
                        targetOffset = slideOffsetFor(selectedTab, 3, screenWidthPx)
                    ) {
                        ProfileScreen(
                            uiState = homeState,
                            onRetry = homeViewModel::loadUser,
                            onLogout = onLogout
                        )
                    }
                }
            }
        }

        // ===== 自绘侧边栏：无右滑手势，点击空白（scrim）关闭 =====
        AnimatedVisibility(
            visible = drawerState.isOpen,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it },
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // scrim：点击空白关闭侧边栏
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { scope.launch { drawerState.close() } }
                )
                ModalDrawerSheet(
                    modifier = Modifier.align(Alignment.CenterStart).width(300.dp)
                ) {
                    // 用户信息（多用户：显示激活用户 + 切换/新建入口）
                    val user = homeState.user
                    val activeUser = remember(homeKey) { AppContainer.tokenStore.getActiveUser() }
                    val allUsers = remember(homeKey) { AppContainer.tokenStore.getUsers() }
                    var switchMenu by remember { mutableStateOf(false) }
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)) {
                        Surface(shape = CircleShape, color = CloudflareOrange) {
                            Box(
                                modifier = Modifier.size(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
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
                            text = user?.username?.ifBlank { "Cloudflare 用户" }
                                ?: activeUser?.label ?: "Cloudflare 用户",
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
                        onClick = {
                            scope.launch { drawerState.close() }
                            selectedTab = 0
                        }
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        label = { Text("DNS") },
                        selected = selectedTab == 1,
                        onClick = {
                            scope.launch { drawerState.close() }
                            selectedTab = 1
                        }
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                        label = { Text("统计数据") },
                        selected = selectedTab == 2,
                        onClick = {
                            scope.launch { drawerState.close() }
                            selectedTab = 2
                        }
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Person, contentDescription = null) },
                        label = { Text("我的") },
                        selected = selectedTab == 3,
                        onClick = {
                            scope.launch { drawerState.close() }
                            selectedTab = 3
                        }
                    )

                    Spacer(modifier = Modifier.weight(1f))
                    HorizontalDivider()
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        label = { Text("退出登录", color = MaterialTheme.colorScheme.error) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onLogout()
                        }
                    )
                }
            }
        }
    }
}

/**
 * 常驻 Tab 容器：页面保持组合不销毁，切换时仅做水平平移过渡（250ms，FastOutSlowIn，GPU 合成）。
 * 未选中 Tab 平移到屏幕外（左侧/右侧），选中 Tab 停在原位；选中 Tab 置于顶层（zIndex=1）并填满区域，天然拦截触摸。
 */
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

/** 计算 Tab 的目标水平偏移（px）：位于选中 Tab 左侧的移出左屏，右侧的移出右屏，选中的归位 */
private fun slideOffsetFor(selectedTab: Int, tabIndex: Int, width: Float): Float = when {
    tabIndex < selectedTab -> -width
    tabIndex > selectedTab -> width
    else -> 0f
}