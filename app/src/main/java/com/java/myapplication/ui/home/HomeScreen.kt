package com.java.myapplication.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.java.myapplication.data.model.DnsRecord
import com.java.myapplication.data.model.Zone
import com.java.myapplication.ui.dns.DnsRecordsContent
import com.java.myapplication.ui.dns.DnsViewModel
import com.java.myapplication.ui.profile.ProfileScreen
import com.java.myapplication.ui.zones.ZonesScreen

/** 主界面：底部导航（域名 / DNS / 我的），Tab 切换带过渡动画 */
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
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    // 缩短动画时长并减小位移，避免切换时新旧两页同时渲染造成的卡顿
                    if (targetState > initialState) {
                        (slideInHorizontally(tween(200)) { it / 6 } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(200)) { -it / 6 } + fadeOut(tween(200)))
                    } else {
                        (slideInHorizontally(tween(200)) { -it / 6 } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(200)) { it / 6 } + fadeOut(tween(200)))
                    }
                },
                label = "tabContent"
            ) { tab ->
                when (tab) {
                    0 -> ZonesScreen(
                        onZoneClick = onZoneClick
                    )
                    1 -> {
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
                    2 -> ProfileScreen(
                        uiState = homeState,
                        onRetry = homeViewModel::loadUser,
                        onLogout = onLogout
                    )
                }
            }
        }
    }
}