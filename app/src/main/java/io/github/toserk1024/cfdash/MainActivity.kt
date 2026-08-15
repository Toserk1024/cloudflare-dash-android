package io.github.toserk1024.cfdash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.rememberNavController
import io.github.toserk1024.cfdash.navigation.AppNavHost
import io.github.toserk1024.cfdash.navigation.Routes
import io.github.toserk1024.cfdash.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContainer.init(this)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen()
            }
        }
    }
}

@Composable
private fun MainScreen() {
    // 登录态：存在任意已保存用户直接进主界面；否则进初始化界面
    var loggedIn by remember { mutableStateOf(AppContainer.tokenStore.hasCredential()) }

    // 导航栈重建键：切换账号 / 退出到剩余账号时 +1 → 整体重建 NavController + NavHost（重新执行启动流程），
    // 使所有 ViewModel（域名 / DNS / 统计 / 用户）以新激活账号重新加载，彻底解决"切换账号后页面不自动重载"。
    var navResetKey by remember { mutableIntStateOf(0) }

    // 账号切换中的全屏加载遮罩（覆盖整个 MainActivity），避免切换瞬间闪现旧账号内容
    var switching by remember { mutableStateOf(false) }

    // 当前所在 Home Tab（切换账号/重建导航栈后保持所在页面不变）
    var currentTab by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        // 主体：key 变化时整体重建（新 NavController → 新 ViewModelStore → 所有页面从空态重新加载）
        key(navResetKey) {
            val navController = rememberNavController()

            // 退出到无剩余用户时（loggedIn=false）导航回初始化页（NavHost 不监听 startDestination 变化）
            // firstLaunch：首次启动不在此导航（免责声明页作为启动页，同意后由 AppNavHost 按登录态跳转），仅处理"退出登录"后的跳转
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
                    // 账户变化，清除持久化的选中域名
                    AppContainer.tokenStore.saveSelectedZoneId(null)
                    if (AppContainer.tokenStore.hasCredential()) {
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
                    // 切换账号：清除持久化域名选择，重建导航栈重新加载
                    AppContainer.tokenStore.saveSelectedZoneId(null)
                    switching = true
                    navResetKey++
                },
                // 登录成功后同步 loggedIn=true（否则退出登录时 loggedIn 无变化导致不跳转）
                onLoggedIn = { loggedIn = true },
                navController = navController
            )
        }

        // 全屏加载遮罩：覆盖整个 MainActivity，切换/重建完成（新页面初次数据加载）后自动关闭
        if (switching) {
            LaunchedEffect(navResetKey) {
                // 等待新导航栈组合 + 初次数据加载，随后关闭遮罩
                delay(600)
                switching = false
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}