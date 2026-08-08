package io.github.toserk1024.cfdash.ui.dns

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.toserk1024.cfdash.AppContainer
import io.github.toserk1024.cfdash.data.model.DnsRecord
import io.github.toserk1024.cfdash.data.model.DnsRecordRequest
import io.github.toserk1024.cfdash.data.model.DnsRecordTypes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** DNS 记录新建/编辑 ViewModel（按类型渲染完整表单，data 序列化） */
class DnsEditViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {

    private val zoneId: String = checkNotNull(savedStateHandle.get<String>("zoneId"))
    private val recordId: String? = savedStateHandle.get<String>("recordId")?.takeIf { it.isNotBlank() }

    data class DnsEditUiState(
        val recordType: String = "A",
        val name: String = "",
        /** 除 name 外的全部字段（key 见 DnsRecordFieldDefs） */
        val fields: Map<String, String> = emptyMap(),
        val ttl: Long = 1,
        val proxied: Boolean = false,
        val comment: String = "",
        val loading: Boolean = false,
        val submitting: Boolean = false,
        val error: String? = null,
        val saved: Boolean = false,
        val isEdit: Boolean = false
    )

    private val _uiState = MutableStateFlow(DnsEditUiState(isEdit = recordId != null))
    val uiState: StateFlow<DnsEditUiState> = _uiState

    init {
        if (recordId != null) loadRecord()
    }

    private fun loadRecord() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val resp = AppContainer.repository.getDnsRecords(zoneId, page = 1, perPage = 100)
                val record = resp.result?.find { it.id == recordId }
                if (record != null) {
                    _uiState.update {
                        it.copy(
                            recordType = record.type,
                            name = record.name,
                            fields = parseFields(record),
                            ttl = record.ttl,
                            proxied = record.proxied,
                            comment = record.comment ?: "",
                            loading = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(loading = false, error = "未找到该记录") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    /** 编辑回填：从 record.content / record.data 解析各字段 */
    private fun parseFields(record: DnsRecord): Map<String, String> {
        val f = mutableMapOf<String, String>()
        val d = record.data
        fun dataStr(key: String): String? = d?.get(key)?.jsonPrimitive?.content
        when (record.type) {
            "A", "AAAA", "CNAME", "MX", "NS" -> f[DnsRecordFieldDefs.TARGET] = record.content
            "TXT" -> f[DnsRecordFieldDefs.CONTENT] = record.content
            "SRV" -> {
                f[DnsRecordFieldDefs.PRIORITY] = dataStr(DnsRecordFieldDefs.PRIORITY) ?: ""
                f[DnsRecordFieldDefs.WEIGHT] = dataStr(DnsRecordFieldDefs.WEIGHT) ?: ""
                f[DnsRecordFieldDefs.PORT] = dataStr(DnsRecordFieldDefs.PORT) ?: ""
                f[DnsRecordFieldDefs.TARGET] = dataStr(DnsRecordFieldDefs.TARGET) ?: ""
            }
            "DNSKEY" -> {
                f[DnsRecordFieldDefs.FLAGS] = dataStr(DnsRecordFieldDefs.FLAGS) ?: ""
                f[DnsRecordFieldDefs.PROTOCOL] = dataStr(DnsRecordFieldDefs.PROTOCOL) ?: ""
                f[DnsRecordFieldDefs.ALGORITHM] = dataStr(DnsRecordFieldDefs.ALGORITHM) ?: ""
                f[DnsRecordFieldDefs.PUBLIC_KEY] = dataStr(DnsRecordFieldDefs.PUBLIC_KEY) ?: ""
            }
            "CAA" -> {
                f[DnsRecordFieldDefs.FLAGS] = dataStr(DnsRecordFieldDefs.FLAGS) ?: ""
                f[DnsRecordFieldDefs.TAG] = dataStr(DnsRecordFieldDefs.TAG) ?: ""
                f[DnsRecordFieldDefs.VALUE] = dataStr(DnsRecordFieldDefs.VALUE) ?: ""
            }
            "SVCB", "HTTPS" -> {
                f[DnsRecordFieldDefs.PRIORITY] = dataStr(DnsRecordFieldDefs.PRIORITY) ?: ""
                f[DnsRecordFieldDefs.TARGET] = dataStr(DnsRecordFieldDefs.TARGET) ?: ""
                f[DnsRecordFieldDefs.PARAMS] = dataStr(DnsRecordFieldDefs.PARAMS) ?: ""
            }
            "SSHFP" -> {
                f[DnsRecordFieldDefs.ALGORITHM] = dataStr(DnsRecordFieldDefs.ALGORITHM) ?: ""
                f[DnsRecordFieldDefs.FINGERPRINT_TYPE] = dataStr(DnsRecordFieldDefs.FINGERPRINT_TYPE) ?: ""
                f[DnsRecordFieldDefs.FINGERPRINT] = dataStr(DnsRecordFieldDefs.FINGERPRINT) ?: ""
            }
            "TLSA" -> {
                f[DnsRecordFieldDefs.USAGE] = dataStr(DnsRecordFieldDefs.USAGE) ?: ""
                f[DnsRecordFieldDefs.SELECTOR] = dataStr(DnsRecordFieldDefs.SELECTOR) ?: ""
                f[DnsRecordFieldDefs.MATCHING_TYPE] = dataStr(DnsRecordFieldDefs.MATCHING_TYPE) ?: ""
                f[DnsRecordFieldDefs.CERTIFICATE] = dataStr(DnsRecordFieldDefs.CERTIFICATE) ?: ""
            }
            "NAPTR" -> {
                f[DnsRecordFieldDefs.ORDER] = dataStr(DnsRecordFieldDefs.ORDER) ?: ""
                f[DnsRecordFieldDefs.PREFERENCE] = dataStr(DnsRecordFieldDefs.PREFERENCE) ?: ""
                f[DnsRecordFieldDefs.FLAGS] = dataStr(DnsRecordFieldDefs.FLAGS) ?: ""
                f[DnsRecordFieldDefs.SERVICE] = dataStr(DnsRecordFieldDefs.SERVICE) ?: ""
                f[DnsRecordFieldDefs.REGEXP] = dataStr(DnsRecordFieldDefs.REGEXP) ?: ""
                f[DnsRecordFieldDefs.REPLACEMENT] = dataStr(DnsRecordFieldDefs.REPLACEMENT) ?: ""
            }
            "URI" -> {
                f[DnsRecordFieldDefs.PRIORITY] = dataStr(DnsRecordFieldDefs.PRIORITY) ?: ""
                f[DnsRecordFieldDefs.WEIGHT] = dataStr(DnsRecordFieldDefs.WEIGHT) ?: ""
                f[DnsRecordFieldDefs.TARGET] = dataStr(DnsRecordFieldDefs.TARGET) ?: ""
            }
        }
        // MX / URI 的 priority 独立字段
        if (record.type == "MX" && record.priority != null) {
            f[DnsRecordFieldDefs.PRIORITY] = record.priority.toString()
        }
        return f
    }

    fun setType(type: String) {
        // 切换类型：清空字段（保留 name 不适用，全部重置更安全）
        _uiState.update { it.copy(recordType = type, fields = emptyMap(), name = "") }
    }

    fun setName(value: String) {
        _uiState.update { it.copy(name = value) }
    }

    fun setField(key: String, value: String) {
        _uiState.update { it.copy(fields = it.fields + (key to value)) }
    }

    fun setTtl(value: Long) {
        _uiState.update { it.copy(ttl = value) }
    }

    fun setProxied(value: Boolean) {
        _uiState.update { it.copy(proxied = value) }
    }

    fun setComment(value: String) {
        _uiState.update { it.copy(comment = value) }
    }

    fun submit() {
        val s = _uiState.value
        if (s.name.isBlank()) {
            _uiState.update { it.copy(error = "请输入名称") }
            return
        }
        // 校验必填字段
        val missing = DnsRecordFieldDefs.fields(s.recordType)
            .filter { it.required }
            .firstOrNull { s.fields[it.key].isNullOrBlank() }
        if (missing != null) {
            _uiState.update { it.copy(error = "请填写：${missing.label}") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(submitting = true, error = null) }
            try {
                val request = buildRequest(s)
                val savedRecord = if (recordId != null) {
                    AppContainer.repository.updateDnsRecord(zoneId, recordId, request)
                } else {
                    AppContainer.repository.createDnsRecord(zoneId, request)
                }
                // 记录到同步队列：返回列表页时本地更新缓存，无需重新请求 API
                DnsRecordsSync.add(zoneId, savedRecord)
                _uiState.update { it.copy(submitting = false, saved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(submitting = false, error = e.message) }
            }
        }
    }

    /** 按记录类型构建请求：简单类型 content=目标；复杂类型 data=结构化对象 + content=RFC 组合 */
    private fun buildRequest(s: DnsEditUiState): DnsRecordRequest {
        val type = s.recordType
        val f = s.fields
        fun str(key: String): String = f[key]?.trim() ?: ""
        fun num(key: String): Int? = f[key]?.trim()?.toIntOrNull()

        var content = ""
        var priority: Long? = null
        var data: JsonObject? = null

        when (type) {
            "A", "AAAA", "CNAME", "NS" -> content = str(DnsRecordFieldDefs.TARGET)
            "MX" -> {
                content = str(DnsRecordFieldDefs.TARGET)
                priority = num(DnsRecordFieldDefs.PRIORITY)?.toLong()
            }
            "TXT" -> content = str(DnsRecordFieldDefs.CONTENT)
            "SRV" -> {
                val p = num(DnsRecordFieldDefs.PRIORITY)
                val w = num(DnsRecordFieldDefs.WEIGHT)
                val port = num(DnsRecordFieldDefs.PORT)
                val target = str(DnsRecordFieldDefs.TARGET)
                content = listOfNotNull(p, w, port, target).joinToString(" ")
                data = buildJsonObject {
                    p?.let { put(DnsRecordFieldDefs.PRIORITY, it) }
                    w?.let { put(DnsRecordFieldDefs.WEIGHT, it) }
                    port?.let { put(DnsRecordFieldDefs.PORT, it) }
                    if (target.isNotEmpty()) put(DnsRecordFieldDefs.TARGET, target)
                }
            }
            "DNSKEY" -> {
                val flags = num(DnsRecordFieldDefs.FLAGS)
                val protocol = num(DnsRecordFieldDefs.PROTOCOL)
                val algorithm = num(DnsRecordFieldDefs.ALGORITHM)
                val key = str(DnsRecordFieldDefs.PUBLIC_KEY)
                content = listOfNotNull(flags, protocol, algorithm, key).joinToString(" ")
                data = buildJsonObject {
                    flags?.let { put(DnsRecordFieldDefs.FLAGS, it) }
                    protocol?.let { put(DnsRecordFieldDefs.PROTOCOL, it) }
                    algorithm?.let { put(DnsRecordFieldDefs.ALGORITHM, it) }
                    if (key.isNotEmpty()) put(DnsRecordFieldDefs.PUBLIC_KEY, key)
                }
            }
            "CAA" -> {
                val flags = num(DnsRecordFieldDefs.FLAGS)
                val tag = str(DnsRecordFieldDefs.TAG)
                val value = str(DnsRecordFieldDefs.VALUE)
                content = listOfNotNull(flags, tag, value).joinToString(" ")
                data = buildJsonObject {
                    flags?.let { put(DnsRecordFieldDefs.FLAGS, it) }
                    if (tag.isNotEmpty()) put(DnsRecordFieldDefs.TAG, tag)
                    if (value.isNotEmpty()) put(DnsRecordFieldDefs.VALUE, value)
                }
            }
            "SVCB", "HTTPS" -> {
                val p = num(DnsRecordFieldDefs.PRIORITY)
                val target = str(DnsRecordFieldDefs.TARGET)
                val params = str(DnsRecordFieldDefs.PARAMS)
                content = listOfNotNull(p, target, params).joinToString(" ")
                data = buildJsonObject {
                    p?.let { put(DnsRecordFieldDefs.PRIORITY, it) }
                    if (target.isNotEmpty()) put(DnsRecordFieldDefs.TARGET, target)
                    if (params.isNotEmpty()) put(DnsRecordFieldDefs.PARAMS, params)
                }
            }
            "SSHFP" -> {
                val algorithm = num(DnsRecordFieldDefs.ALGORITHM)
                val ft = num(DnsRecordFieldDefs.FINGERPRINT_TYPE)
                val fp = str(DnsRecordFieldDefs.FINGERPRINT)
                content = listOfNotNull(algorithm, ft, fp).joinToString(" ")
                data = buildJsonObject {
                    algorithm?.let { put(DnsRecordFieldDefs.ALGORITHM, it) }
                    ft?.let { put(DnsRecordFieldDefs.FINGERPRINT_TYPE, it) }
                    if (fp.isNotEmpty()) put(DnsRecordFieldDefs.FINGERPRINT, fp)
                }
            }
            "TLSA" -> {
                val usage = num(DnsRecordFieldDefs.USAGE)
                val selector = num(DnsRecordFieldDefs.SELECTOR)
                val mt = num(DnsRecordFieldDefs.MATCHING_TYPE)
                val cert = str(DnsRecordFieldDefs.CERTIFICATE)
                content = listOfNotNull(usage, selector, mt, cert).joinToString(" ")
                data = buildJsonObject {
                    usage?.let { put(DnsRecordFieldDefs.USAGE, it) }
                    selector?.let { put(DnsRecordFieldDefs.SELECTOR, it) }
                    mt?.let { put(DnsRecordFieldDefs.MATCHING_TYPE, it) }
                    if (cert.isNotEmpty()) put(DnsRecordFieldDefs.CERTIFICATE, cert)
                }
            }
            "NAPTR" -> {
                val order = num(DnsRecordFieldDefs.ORDER)
                val pref = num(DnsRecordFieldDefs.PREFERENCE)
                val flags = str(DnsRecordFieldDefs.FLAGS)
                val service = str(DnsRecordFieldDefs.SERVICE)
                val regexp = str(DnsRecordFieldDefs.REGEXP)
                val replacement = str(DnsRecordFieldDefs.REPLACEMENT)
                content = listOfNotNull(order, pref, flags, service, regexp, replacement).joinToString(" ")
                data = buildJsonObject {
                    order?.let { put(DnsRecordFieldDefs.ORDER, it) }
                    pref?.let { put(DnsRecordFieldDefs.PREFERENCE, it) }
                    if (flags.isNotEmpty()) put(DnsRecordFieldDefs.FLAGS, flags)
                    if (service.isNotEmpty()) put(DnsRecordFieldDefs.SERVICE, service)
                    if (regexp.isNotEmpty()) put(DnsRecordFieldDefs.REGEXP, regexp)
                    if (replacement.isNotEmpty()) put(DnsRecordFieldDefs.REPLACEMENT, replacement)
                }
            }
            "URI" -> {
                val p = num(DnsRecordFieldDefs.PRIORITY)
                val w = num(DnsRecordFieldDefs.WEIGHT)
                val target = str(DnsRecordFieldDefs.TARGET)
                content = listOfNotNull(p, w, target).joinToString(" ")
                data = buildJsonObject {
                    p?.let { put(DnsRecordFieldDefs.PRIORITY, it) }
                    w?.let { put(DnsRecordFieldDefs.WEIGHT, it) }
                    if (target.isNotEmpty()) put(DnsRecordFieldDefs.TARGET, target)
                }
            }
            else -> content = str(DnsRecordFieldDefs.TARGET)
        }

        return DnsRecordRequest(
            type = type,
            name = s.name.trim(),
            content = content,
            ttl = s.ttl,
            proxied = if (DnsRecordTypes.PROXIABLE.contains(type)) s.proxied else false,
            priority = priority,
            comment = s.comment.trim().ifEmpty { null },
            tags = emptyList(),
            data = data
        )
    }
}