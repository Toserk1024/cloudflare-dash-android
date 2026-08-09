package io.github.toserk1024.cfdash.navigation

/** 导航路由定义 */
object Routes {
    const val DISCLAIMER = "disclaimer"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"

    const val ZONE_DETAIL = "zone_detail/{zoneId}?zoneName={zoneName}"

    const val DNS_RECORDS = "dns_records/{zoneId}?zoneName={zoneName}"
    const val DNS_EDIT = "dns_edit/{zoneId}?recordId={recordId}&zoneName={zoneName}"

    fun zoneDetail(zoneId: String, zoneName: String? = null) =
        "zone_detail/$zoneId?zoneName=${zoneName.orEmpty()}"

    fun dnsRecords(zoneId: String, zoneName: String? = null) =
        "dns_records/$zoneId?zoneName=${zoneName.orEmpty()}"

    fun dnsEdit(zoneId: String, recordId: String? = null, zoneName: String? = null) =
        "dns_edit/$zoneId?recordId=${recordId.orEmpty()}&zoneName=${zoneName.orEmpty()}"
}