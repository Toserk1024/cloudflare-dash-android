package io.github.toserk1024.cfdash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

    // 登录态：已有认证凭据（Token / Global Key）直接进主界面；否则进初始化界面
    var loggedIn by remember { mutableStateOf(AppContainer.tokenStore.hasCredential()) }
    val startDestination = if (loggedIn) Routes.HOME else Routes.ONBOARDING

    AppNavHost(
        startDestination = startDestination,
        onLoggedOut = { loggedIn = false },
        navController = navController
    )
}