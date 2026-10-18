package io.github.toserk1024.cfdash.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.toserk1024.cfdash.AppContainer
import io.github.toserk1024.cfdash.ui.disclaimer.DisclaimerScreen
import io.github.toserk1024.cfdash.ui.dns.DnsEditViewModel
import io.github.toserk1024.cfdash.ui.dns.DnsRecordEditScreen
import io.github.toserk1024.cfdash.ui.home.HomeScreen
import io.github.toserk1024.cfdash.ui.onboarding.OnboardingScreen

/** 页面转场时长 */
private const val PAGE_ANIM_MS = 320

/**
 * 自建页面导航（完全绕开 Navigation Compose 的转场实现）。
 *
 * 关键设计：
 * 1. 用 [AnimatedContent] 同时渲染「旧页 + 新页」，两页都在动；
 * 2. **正在退出的页面 zIndex = 1（置于最上层）**：
 *    返回时旧页在上层向右滑出，绝不会被不透明的新页遮挡 —— 这正是此前"返回无动画"的根因；
 * 3. 页面只有一层子页（DNS 编辑），因此页面栈用单个 state 表达，返回即回 Home；
 * 4. 系统返回键走 [BackHandler]，与页面内返回按钮共用同一路径。
 */
@Composable
fun AnimatedAppNav(
    startScreen: AppScreen,
    homeKey: Int,
    initialTab: Int,
    onTabChange: (Int) -> Unit,
    onLoggedIn: () -> Unit,
    onUserSwitched: () -> Unit,
    onLoggedOut: () -> Unit
) {
    // homeKey 变化（切换账号）时重建页面栈
    var screen by remember(homeKey) { mutableStateOf(startScreen) }

    fun goHome() {
        screen = AppScreen.Home
    }

    fun openDnsEdit(zoneId: String, recordId: String?, zoneName: String?) {
        if (zoneId.isBlank()) return
        if (screen is AppScreen.DnsEdit) return
        screen = AppScreen.DnsEdit(zoneId = zoneId, recordId = recordId, zoneName = zoneName)
    }

    // 系统返回：子页面 → Home；Home 上不拦截（交给系统退出应用）
    BackHandler(enabled = screen.isChildPage) {
        if (screen.isChildPage) goHome()
    }

    AnimatedContent(
        targetState = screen,
        contentKey = { it.routeKey },
        transitionSpec = {
            val forward = targetState is AppScreen.DnsEdit
            val enter = slideInHorizontally(
                animationSpec = tween(PAGE_ANIM_MS, easing = FastOutSlowInEasing)
            ) { fullWidth -> if (forward) fullWidth else -fullWidth / 4 }
            val exit = slideOutHorizontally(
                animationSpec = tween(PAGE_ANIM_MS, easing = FastOutSlowInEasing)
            ) { fullWidth -> if (forward) -fullWidth / 4 else fullWidth }
            (fadeIn(tween(PAGE_ANIM_MS, easing = FastOutSlowInEasing)) + enter) togetherWith
                (fadeOut(tween(PAGE_ANIM_MS, easing = FastOutSlowInEasing)) + exit)
        },
        label = "appPage"
    ) { target ->
        // 正在退出的页面 → 上层；新页面 → 下层。保证返回时旧页滑出可见
        val isOutgoing = target.routeKey != screen.routeKey
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (isOutgoing) 1f else 0f)
        ) {
            when (target) {
                AppScreen.Disclaimer -> DisclaimerScreen(
                    onAgree = {
                        AppContainer.tokenStore.setDisclaimerAccepted(true)
                        screen = if (AppContainer.tokenStore.hasCredential()) {
                            AppScreen.Home
                        } else {
                            AppScreen.Onboarding
                        }
                    }
                )

                AppScreen.Onboarding -> OnboardingScreen(
                    onSuccess = {
                        onLoggedIn()
                        goHome()
                    }
                )

                AppScreen.Home -> HomeScreen(
                    homeKey = homeKey,
                    initialTab = initialTab,
                    onTabChange = onTabChange,
                    onDnsEdit = { zoneId, recordId, zoneName ->
                        openDnsEdit(zoneId, recordId, zoneName)
                    },
                    onLogout = onLoggedOut,
                    onNewUser = { screen = AppScreen.Onboarding },
                    onUserSwitched = onUserSwitched
                )

                is AppScreen.DnsEdit -> {
                    val editViewModel: DnsEditViewModel = viewModel(
                        key = "dns_edit_${target.zoneId}_${target.recordId.orEmpty()}",
                        factory = viewModelFactory {
                            initializer {
                                DnsEditViewModel(
                                    repository = AppContainer.repository,
                                    zoneId = target.zoneId,
                                    recordId = target.recordId
                                )
                            }
                        }
                    )
                    DnsRecordEditScreen(
                        zoneId = target.zoneId,
                        recordId = target.recordId,
                        zoneName = target.zoneName,
                        onBack = { goHome() },
                        onSaved = { goHome() },
                        viewModel = editViewModel
                    )
                }
            }
        }
    }
}