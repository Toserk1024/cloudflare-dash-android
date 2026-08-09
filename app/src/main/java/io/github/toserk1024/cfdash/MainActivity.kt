package io.github.toserk1024.cfdash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import io.github.toserk1024.cfdash.navigation.AppNavHost
import io.github.toserk1024.cfdash.navigation.Routes
import io.github.toserk1024.cfdash.ui.theme.MyApplicationTheme

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
    val navController = rememberNavController()

    // 登录态：存在任意已保存用户直接进主界面；否则进初始化界面
    var loggedIn by remember { mutableStateOf(AppContainer.tokenStore.hasCredential()) }
    // Home 刷新键：切换用户/退出后 +1，触发 HomeScreen 重新加载用户数据
    var homeKey by remember { mutableIntStateOf(0) }

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
        homeKey = homeKey,
        onLoggedOut = {
            // 退出登录：删除当前激活用户；若有剩余用户自动切换并刷新 Home，否则回初始化
            AppContainer.tokenStore.getActiveUser()?.id?.let { AppContainer.tokenStore.deleteUser(it) }
            if (AppContainer.tokenStore.hasCredential()) {
                homeKey++
            } else {
                loggedIn = false
            }
        },
        onNewUser = {
            navController.navigate(Routes.ONBOARDING)
        },
        onUserSwitched = {
            homeKey++
        },
        // 登录成功后同步 loggedIn=true（否则退出登录时 loggedIn 无变化导致不跳转）
        onLoggedIn = { loggedIn = true },
        navController = navController
    )
}