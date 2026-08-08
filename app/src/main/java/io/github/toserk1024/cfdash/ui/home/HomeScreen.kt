package io.github.toserk1024.cfdash.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.toserk1024.cfdash.data.model.DnsRecord
import io.github.toserk1024.cfdash.data.model.Zone
import io.github.toserk1024.cfdash.ui.dns.DnsRecordsContent
import io.github.toserk1024.cfdash.ui.dns.DnsViewModel
import io.github.toserk1024.cfdash.ui.profile.ProfileScreen
import io.github.toserk1024.cfdash.ui.zones.ZonesScreen
import kotlin.math.roundToInt

/**
 * 主界面：底部导航（域名 / DNS / 我的）。
 *
 * 卡顿优化：三个 Tab 首次访问后常驻组合，切换时仅做水平平移过渡（offset 位移，GPU 合成，开销极小），
 * 避免 AnimatedContent 每次切换都销毁/重建整页（LazyColumn 全量重建 + 滚动位置丢失）造成的掉帧。
 */
@Composable
fun HomeScreen(
    onZoneClick: (Zone) -> Unit,
    onDnsEdit: (String, String?) -> Unit,
    onLogout: () -> Unit,
    homeViewModel: HomeViewModel = viewModel(),
    dnsViewModel: DnsViewModel = viewModel()
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val homeState by homeViewModel.uiState.collectAsState()

    // 已访问过的 Tab 位掩码（bit0=域名, bit1=DNS, bit2=我的）：懒加载，首次访问后才组合并常驻
    var visitedMask by rememberSaveable { mutableIntStateOf(1) }
    LaunchedEffect(selectedTab) {
        visitedMask = visitedMask or (1 shl selectedTab)
    }

    // 水平平移动画所需的屏幕宽度（px），用于计算 Tab 移出屏幕的偏移量
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(LocalDensity.current) { configuration.screenWidthDp.dp.toPx() }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("域名") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text("DNS") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text("我的") }
                )
            }
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
                    // 仅 DNS Tab 显示时才收集 DNS 状态，减少无关重组
                    val dnsState by dnsViewModel.uiState.collectAsState()
                    DnsRecordsContent(
                        onEditRecord = { record: DnsRecord ->
                            onDnsEdit(dnsState.selectedZone?.id.orEmpty(), record.id)
                        },
                        onAddRecord = { onDnsEdit(dnsState.selectedZone?.id.orEmpty(), null) },
                        viewModel = dnsViewModel
                    )
                }
            }
            // 我的 Tab（常驻）
            if ((visitedMask and 0b100) != 0) {
                SlidingTab(
                    selected = selectedTab == 2,
                    targetOffset = slideOffsetFor(selectedTab, 2, screenWidthPx)
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
