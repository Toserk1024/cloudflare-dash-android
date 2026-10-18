package io.github.toserk1024.cfdash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import io.github.toserk1024.cfdash.data.state.ZoneSelectionStore
import io.github.toserk1024.cfdash.data.state.ZoneSettingsCache
import io.github.toserk1024.cfdash.data.storage.ThemePreferences
import io.github.toserk1024.cfdash.navigation.AppNavHost
import io.github.toserk1024.cfdash.navigation.Routes
import io.github.toserk1024.cfdash.ui.common.AppReadySignal
import io.github.toserk1024.cfdash.ui.dns.DnsRecordsSync
import io.github.toserk1024.cfdash.ui.home.HomeScreen
import io.github.toserk1024.cfdash.ui.theme.CloudflareOrange
import io.github.toserk1024.cfdash.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

/** 切换账号 Loading 遮罩：最少显示时长（防闪烁） */
private const val MIN_LOADING_MS = 400L
/** 切换账号 Loading 遮罩：最长等待（兜底） */
private const val MAX_LOADING_MS = 3000L
/** 就绪信号轮询间隔 */
private const val READY_POLL_MS = 50L
/** 导航防重入窗口（毫秒） */
private const val NAV_DEBOUNCE_MS = 600L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Application 已初始化，此处为幂等兜底
        AppContainer.init(this)
        enableEdgeToEdge()
        setContent {
            val themeConfig by ThemePreferences.state.collectAsState()
            MyApplicationTheme(
                darkTheme = isSystemInDarkTheme(),
                dynamicColor = themeConfig.dynamicColor,
                seedColor = if (themeConfig.dynamicColor) {
                    CloudflareOrange
                } else {
                    Color(ThemePreferences.toArgb(themeConfig.colorHex) ?: 0xFFF6821FL)
                }
            ) {
                MainScreen()
            }
        }
    }
}

@Composable
private fun MainScreen() {
    var accepted by remember { mutableStateOf(AppContainer.tokenStore.isDisclaimerAccepted()) }
    var loggedIn by remember { mutableStateOf(AppContainer.tokenStore.hasCredential()) }
    var navResetKey by remember { mutableIntStateOf(0) }
    var switching by remember { mutableStateOf(false) }
    var currentTab by remember { mutableIntStateOf(0) }
    var lastNavAt by remember { mutableLongStateOf(0L) }

    // 退出到无剩余用户：重建一次，让 startDestination 落到登录页
    LaunchedEffect(loggedIn) {
        if (!loggedIn) navResetKey++
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        key(navResetKey) {
            // NavController 建在 key 内：切账号重建时同时清空导航栈，避免停在上个账号的子页
            val navController = rememberNavController()

            // ===== 底层：Home 常驻，不进导航栈、不随子页销毁 =====
            if (accepted && loggedIn) {
                HomeScreen(
                    homeKey = navResetKey,
                    initialTab = currentTab,
                    onTabChange = { currentTab = it },
                    onDnsEdit = { zoneId, recordId, zoneName ->
                        val now = System.currentTimeMillis()
                        if (zoneId.isNotBlank() && now - lastNavAt > NAV_DEBOUNCE_MS) {
                            lastNavAt = now
                            navController.navigate(Routes.dnsEdit(zoneId, recordId, zoneName)) {
                                launchSingleTop = true
                            }
                        }
                    },
                    onLogout = {
                        AppContainer.tokenStore.getActiveUser()?.id
                            ?.let { AppContainer.tokenStore.deleteUser(it) }
                        AppContainer.tokenStore.saveSelectedZoneId(null)
                        clearSessionCaches()
                        if (AppContainer.tokenStore.hasCredential()) {
                            AppReadySignal.reset()
                            switching = true
                            navResetKey++
                        } else {
                            loggedIn = false
                        }
                    },
                    onNewUser = {
                        navController.navigate(Routes.ONBOARDING)
                    },
                    onUserSwitched = {
                        AppContainer.tokenStore.saveSelectedZoneId(null)
                        clearSessionCaches()
                        AppReadySignal.reset()
                        switching = true
                        navResetKey++
                    }
                )
            }

            // ===== 上层：覆盖层导航（Home 永远在下方存活，转场只动覆盖层）=====
            AppNavHost(
                navController = navController,
                startDestination = when {
                    !accepted -> Routes.DISCLAIMER
                    loggedIn -> Routes.EMPTY
                    else -> Routes.ONBOARDING
                },
                onAccepted = { accepted = true },
                onLoggedIn = { loggedIn = true }
            )
        }

        // 全屏 Loading 遮罩：等待首屏数据就绪（最少 400ms 防闪烁，最多 3s 兜底）
        if (switching) {
            LaunchedEffect(navResetKey) {
                val start = System.currentTimeMillis()
                val deadline = start + MAX_LOADING_MS
                while (System.currentTimeMillis() < deadline && !AppReadySignal.isReady) {
                    delay(READY_POLL_MS)
                }
                val elapsed = System.currentTimeMillis() - start
                if (elapsed < MIN_LOADING_MS) delay(MIN_LOADING_MS - elapsed)
                switching = false
            }
        }
        AnimatedVisibility(
            visible = switching,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200))
        ) {
            SwitchingLoadingOverlay()
        }
    }
}

/** 切换账号 / 退出登录时清空会话级内存缓存，避免旧账号数据串到新账号 */
private fun clearSessionCaches() {
    ZoneSelectionStore.clear()
    ZoneSettingsCache.clear()
    DnsRecordsSync.clear()
}

/** 切换账号时的全屏加载画面：进度圈 + "Loading." 循环动画文字 */
@Composable
private fun SwitchingLoadingOverlay() {
    var dots by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(350)
            dots = dots % 3 + 1
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = CloudflareOrange)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading" + ".".repeat(dots),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}