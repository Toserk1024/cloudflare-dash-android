package com.cloudflare.dash3rd.navigation

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
import com.cloudflare.dash3rd.ui.dns.DnsRecordEditScreen
import com.cloudflare.dash3rd.ui.dns.DnsRecordsScreen
import com.cloudflare.dash3rd.ui.home.HomeScreen
import com.cloudflare.dash3rd.ui.onboarding.OnboardingScreen
import com.cloudflare.dash3rd.ui.zones.ZoneDetailScreen

/** 应用导航图 */
@Composable
fun AppNavHost(
    startDestination: String,
    onLoggedOut: () -> Unit,
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(tween(220)) + slideInHorizontally { it / 4 } },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(180)) },
        popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally { it / 4 } }
    ) {

        // 初始化（Token 验证）
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        // 主界面
        composable(Routes.HOME) {
            HomeScreen(
                onZoneClick = { zone ->
                    navController.navigate(Routes.zoneDetail(zone.id, zone.name))
                },
                onDnsEdit = { zoneId, recordId ->
                    if (zoneId.isNotBlank()) {
                        navController.navigate(Routes.dnsEdit(zoneId, recordId))
                    }
                },
                onLogout = {
                    onLoggedOut()
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }

        // 域名详情
        composable(
            route = Routes.ZONE_DETAIL,
            arguments = listOf(
                navArgument("zoneId") { type = NavType.StringType },
                navArgument("zoneName") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entry ->
            val zoneId = entry.arguments?.getString("zoneId").orEmpty()
            val zoneName = entry.arguments?.getString("zoneName")?.takeIf { it.isNotBlank() }
            ZoneDetailScreen(
                zoneId = zoneId,
                zoneName = zoneName,
                onBack = { navController.popBackStack() },
                onManageDns = { zid, zname ->
                    navController.navigate(Routes.dnsRecords(zid, zname))
                },
                onDeleted = { navController.popBackStack() }
            )
        }

        // DNS 记录列表（独立页面）
        composable(
            route = Routes.DNS_RECORDS,
            arguments = listOf(
                navArgument("zoneId") { type = NavType.StringType },
                navArgument("zoneName") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entry ->
            val zoneId = entry.arguments?.getString("zoneId").orEmpty()
            val zoneName = entry.arguments?.getString("zoneName")?.takeIf { it.isNotBlank() }
            DnsRecordsScreen(
                zoneId = zoneId,
                zoneName = zoneName,
                onBack = { navController.popBackStack() },
                onEditRecord = { zid, rid ->
                    navController.navigate(Routes.dnsEdit(zid, rid))
                }
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
                }
            )
        ) { entry ->
            val zoneId = entry.arguments?.getString("zoneId").orEmpty()
            val recordId = entry.arguments?.getString("recordId")?.takeIf { it.isNotBlank() }
            DnsRecordEditScreen(
                zoneId = zoneId,
                recordId = recordId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
    }
}