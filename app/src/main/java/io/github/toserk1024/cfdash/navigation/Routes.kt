package io.github.toserk1024.cfdash.navigation

/** 导航路由定义 */
object Routes {
    const val DISCLAIMER = "disclaimer"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"

    const val DNS_EDIT = "dns_edit/{zoneId}?recordId={recordId}&zoneName={zoneName}"

    fun dnsEdit(zoneId: String, recordId: String? = null, zoneName: String? = null) =
        "dns_edit/$zoneId?recordId=${recordId.orEmpty()}&zoneName=${zoneName.orEmpty()}"
}