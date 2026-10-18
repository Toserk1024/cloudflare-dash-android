package io.github.toserk1024.cfdash.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.toserk1024.cfdash.AppContainer
import io.github.toserk1024.cfdash.ui.disclaimer.DisclaimerScreen
import io.github.toserk1024.cfdash.ui.dns.DnsRecordEditScreen
import io.github.toserk1024.cfdash.ui.onboarding.OnboardingScreen

/** 页面转场时长（覆盖层滑入/滑出） */
private const val PAGE_ANIM_MS = 320

/**
 * 覆盖层导航（使用 Navigation Compose 提供的转场）。
 *
 * 架构关键：**Home 不在导航栈里**，而是常驻在 NavHost 之下（见 MainActivity）。
 * 因此：
 * - 打开 DNS 编辑页 = 覆盖层从右滑入，Home 保持存活、不重组；
 * - 返回 = 覆盖层向右滑出，Home 始终在下方稳定显示 → 动画期间没有 Home 的重建成本，不会掉帧；
 * - [Routes.EMPTY] 是透明占位：导航栈停在这里时，画面就是下方常驻的 Home。
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String,
    onAccepted: () -> Unit,
    onLoggedIn: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            fadeIn(tween(PAGE_ANIM_MS, easing = FastOutSlowInEasing)) +
                slideInHorizontally(tween(PAGE_ANIM_MS, easing = FastOutSlowInEasing)) { it }
        },
        // 覆盖层被替换时的旧页表现：淡出即可（旧页通常是透明的 EMPTY 占位）
        exitTransition = {
            fadeOut(tween(PAGE_ANIM_MS, easing = FastOutSlowInEasing))
        },
        popEnterTransition = {
            fadeIn(tween(PAGE_ANIM_MS, easing = FastOutSlowInEasing))
        },
        popExitTransition = {
            fadeOut(tween(PAGE_ANIM_MS, easing = FastOutSlowInEasing)) +
                slideOutHorizontally(tween(PAGE_ANIM_MS, easing = FastOutSlowInEasing)) { it }
        }
    ) {

        // 透明占位：Home 在其下方常驻
        composable(Routes.EMPTY) {
            // 空内容，不绘制任何东西
        }

        // 免责声明
        composable(Routes.DISCLAIMER) {
            DisclaimerScreen(
                onAgree = {
                    AppContainer.tokenStore.setDisclaimerAccepted(true)
                    onAccepted()
                    val loggedIn = AppContainer.tokenStore.hasCredential()
                    if (loggedIn) {
                        navController.navigate(Routes.EMPTY) {
                            popUpTo(0) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Routes.ONBOARDING) {
                            popUpTo(Routes.DISCLAIMER) { inclusive = true }
                        }
                    }
                }
            )
        }

        // 登录 / 新增用户
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onSuccess = {
                    onLoggedIn()
                    // 回到 Home（Home 常驻在 NavHost 之下），并清空导航栈
                    navController.navigate(Routes.EMPTY) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // DNS 记录新建/编辑（覆盖层）
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
                // 显式回到 EMPTY（Home 占位），重复触发也安全，不会把 Home 之外的栈弹空
                onBack = { navController.popBackStack(Routes.EMPTY, inclusive = false) },
                onSaved = { navController.popBackStack(Routes.EMPTY, inclusive = false) }
            )
        }
    }
}