package io.github.toserk1024.cfdash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import io.github.toserk1024.cfdash.navigation.AppNavHost
import io.github.toserk1024.cfdash.navigation.Routes
import io.github.toserk1024.cfdash.data.state.ZoneSelectionStore
import io.github.toserk1024.cfdash.data.state.ZoneSettingsCache
import io.github.toserk1024.cfdash.data.storage.ThemePreferences
import io.github.toserk1024.cfdash.ui.common.AppReadySignal
import io.github.toserk1024.cfdash.ui.dns.DnsRecordsSync
import io.github.toserk1024.cfdash.ui.theme.CloudflareOrange
import io.github.toserk1024.cfdash.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

/** 切换账号 Loading 遮罩：最少显示时长（防闪烁） */
private const val MIN_LOADING_MS = 400L
/** 切换账号 Loading 遮罩：最长等待（兜底，避免网络异常时永远遮着） */
private const val MAX_LOADING_MS = 3000L
/** 就绪信号轮询间隔 */
private const val READY_POLL_MS = 50L

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
                customPrimary = if (themeConfig.dynamicColor) {
                    null
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
    // 登录态：存在任意已保存用户直接进主界面；否则进初始化界面
    var loggedIn by remember { mutableStateOf(AppContainer.tokenStore.hasCredential()) }

    // 导航栈重建键：切换账号 / 退出到剩余账号时 +1 → 整体重建 NavController + NavHost，
    // 使所有 ViewModel 以新激活账号重新加载。
    var navResetKey by remember { mutableIntStateOf(0) }

    // 账号切换中的全屏 Loading 遮罩
    var switching by remember { mutableStateOf(false) }

    // 当前所在 Home Tab（切换账号/重建导航栈后保持所在页面不变）
    var currentTab by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        key(navResetKey) {
            val navController = rememberNavController()

            // 退出到无剩余用户时（loggedIn=false）导航回初始化页
            var firstLaunch by remember { mutableStateOf(true) }
            LaunchedEffect(loggedIn) {
                if (firstLaunch) {
                    firstLaunch = false
                    return@LaunchedEffect
                }
                if (!loggedIn) {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }

            AppNavHost(
                // 启动逻辑：未同意免责声明→免责声明页；已同意→按登录态进主界面或登录页
                startDestination = when {
                    !AppContainer.tokenStore.isDisclaimerAccepted() -> Routes.DISCLAIMER
                    loggedIn -> Routes.HOME
                    else -> Routes.ONBOARDING
                },
                homeKey = navResetKey,
                initialTab = currentTab,
                onTabChange = { currentTab = it },
                onLoggedOut = {
                    // 退出登录：删除当前激活用户；若有剩余用户自动切换并重建导航栈刷新，否则回初始化
                    AppContainer.tokenStore.getActiveUser()?.id?.let { AppContainer.tokenStore.deleteUser(it) }
                    // 账户变化，清除持久化的选中域名与会话级内存缓存（两种分支都需要）
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
                    // 切换账号：清除持久化域名选择 + 清空会话内存缓存，重建导航栈重新加载
                    AppContainer.tokenStore.saveSelectedZoneId(null)
                    clearSessionCaches()
                    AppReadySignal.reset()
                    switching = true
                    navResetKey++
                },
                // 登录成功后同步 loggedIn=true（否则退出登录时 loggedIn 无变化导致不跳转）
                onLoggedIn = { loggedIn = true },
                navController = navController
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