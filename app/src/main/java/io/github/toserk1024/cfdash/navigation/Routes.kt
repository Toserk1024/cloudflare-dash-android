package io.github.toserk1024.cfdash.navigation

/** 导航路由定义 */
object Routes {
    /** 透明占位路由：Home 常驻在 NavHost 之下，导航栈停在空页面上时即显示 Home */
    const val EMPTY = "empty"
    const val DISCLAIMER = "disclaimer"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"

    const val DNS_EDIT = "dns_edit/{zoneId}?recordId={recordId}&zoneName={zoneName}"

    fun dnsEdit(zoneId: String, recordId: String? = null, zoneName: String? = null) =
        "dns_edit/$zoneId?recordId=${recordId.orEmpty()}&zoneName=${zoneName.orEmpty()}"
}