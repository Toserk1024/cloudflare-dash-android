package io.github.toserk1024.cfdash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
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

    AppNavHost(
        startDestination = if (loggedIn) Routes.HOME else Routes.ONBOARDING,
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
        navController = navController
    )
}