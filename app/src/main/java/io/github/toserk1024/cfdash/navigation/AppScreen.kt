package io.github.toserk1024.cfdash.navigation

/**
 * 应用页面（自建页面栈，不再依赖 Navigation Compose 的转场）。
 *
 * 背景：Navigation Compose 的 enter/popEnter 转场在本项目多次调参均无效果
 * （返回时旧页被不透明的新页遮住），因此改为用 [AnimatedContent] 自行渲染两页并控制层级。
 */
sealed interface AppScreen {
    /** contentKey：同一目的地（不同参数）不触发转场 */
    val routeKey: String

    data object Disclaimer : AppScreen {
        override val routeKey: String get() = "disclaimer"
    }

    data object Onboarding : AppScreen {
        override val routeKey: String get() = "onboarding"
    }

    data object Home : AppScreen {
        override val routeKey: String get() = "home"
    }

    data class DnsEdit(
        val zoneId: String,
        val recordId: String? = null,
        val zoneName: String? = null
    ) : AppScreen {
        override val routeKey: String get() = "dns_edit"
    }

    /** 是否可以"返回"到 Home */
    val isChildPage: Boolean get() = this is DnsEdit
}