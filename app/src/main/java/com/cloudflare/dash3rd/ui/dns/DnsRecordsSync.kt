package com.cloudflare.dash3rd.ui.dns

import com.cloudflare.dash3rd.data.model.DnsRecord

/**
 * DNS 记录变更的跨 ViewModel 同步队列。
 *
 * 背景：编辑/新建页（DnsEditViewModel）与列表页（DnsViewModel）是不同实例，
 * 保存成功后列表页的内存缓存不会自动更新。
 * 因此：编辑/新建页保存成功后，把 API 返回的完整记录放入本队列；
 * 列表页从编辑页返回（重新进入组合）时消费队列并更新本地缓存，
 * 全程无需重新请求 API，符合"全量缓存 + 本地更新"策略。
 *
 * 所有调用均发生在主线程（viewModelScope 默认 Main dispatcher），无需额外同步。
 */
object DnsRecordsSync {

    private data class Change(val zoneId: String, val record: DnsRecord)

    private val pending = mutableListOf<Change>()

    /** 记录一次编辑/新建变更（编辑页保存成功后调用） */
    fun add(zoneId: String, record: DnsRecord) {
        pending.add(Change(zoneId, record))
    }

    /**
     * 取出指定域名下待同步的记录，并清空对应队列（列表页进入组合时调用）。
     * 仅返回与当前域名匹配的变更，避免串到其它域名。
     */
    fun takeFor(zoneId: String): List<DnsRecord> {
        if (pending.isEmpty()) return emptyList()
        val changes = pending.filter { it.zoneId == zoneId }.map { it.record }
        if (changes.isNotEmpty()) {
            pending.removeAll { it.zoneId == zoneId }
        }
        return changes
    }
}
