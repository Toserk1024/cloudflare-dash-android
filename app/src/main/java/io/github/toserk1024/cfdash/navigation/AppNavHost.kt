package io.github.toserk1024.cfdash.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.toserk1024.cfdash.AppContainer
import io.github.toserk1024.cfdash.ui.disclaimer.DisclaimerScreen
import io.github.toserk1024.cfdash.ui.dns.DnsRecordEditScreen
import io.github.toserk1024.cfdash.ui.home.HomeScreen
import io.github.toserk1024.cfdash.ui.onboarding.OnboardingScreen

/** 应用导航图 */
@Composable
fun AppNavHost(
    startDestination: String,
    homeKey: Int = 0,
    initialTab: Int = 0,
    onTabChange: (Int) -> Unit = {},
    onLoggedOut: () -> Unit,
    onNewUser: () -> Unit = {},
    onUserSwitched: () -> Unit = {},
    onLoggedIn: () -> Unit = {},
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(tween(220)) + slideInHorizontally { it / 4 } },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(180)) },
        // 返回时源页（如域名详情）向右滑出，与进入从右滑入对称，退场动画可见
        popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally { -it / 4 } }
    ) {

        // 免责声明启动页
        composable(Routes.DISCLAIMER) {
            DisclaimerScreen(
                onAgree = {
                    AppContainer.tokenStore.setDisclaimerAccepted(true)
                    val loggedIn = AppContainer.tokenStore.hasCredential()
                    navController.navigate(if (loggedIn) Routes.HOME else Routes.ONBOARDING) {
                        popUpTo(Routes.DISCLAIMER) { inclusive = true }
                    }
                }
            )
        }

        // 初始化（Token 验证 / 新增用户）
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onSuccess = {
                    // 同步登录态（MainActivity loggedIn=true），确保后续退出登录能正确跳转
                    onLoggedIn()
                    // 从 Home 进入（新增用户）：验证成功返回 Home；否则（首次登录）导航 Home
                    if (navController.previousBackStackEntry?.destination?.route == Routes.HOME) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                }
            )
        }

        // 主界面
        composable(Routes.HOME) {
            HomeScreen(
                homeKey = homeKey,
                initialTab = initialTab,
                onTabChange = onTabChange,
                onDnsEdit = { zoneId, recordId, zoneName ->
                    if (zoneId.isNotBlank()) {
                        navController.navigate(Routes.dnsEdit(zoneId, recordId, zoneName))
                    }
                },
                onNewUser = onNewUser,
                onUserSwitched = onUserSwitched,
                onLogout = onLoggedOut
            )
        }

        // DNS 记录新建/编辑
        composable(
            route = Routes.DNS_EDIT,
            arguments = listOf(
                navArgument("zoneId") { type = NavType.StringType },
                navArgument("recordId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("zoneName") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entry ->
            val zoneId = entry.arguments?.getString("zoneId").orEmpty()
            val recordId = entry.arguments?.getString("recordId")?.takeIf { it.isNotBlank() }
            val zoneName = entry.arguments?.getString("zoneName")?.takeIf { it.isNotBlank() }
            DnsRecordEditScreen(
                zoneId = zoneId,
                recordId = recordId,
                zoneName = zoneName,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

    }
}