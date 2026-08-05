package com.java.myapplication.ui.dns

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.java.myapplication.AppContainer
import com.java.myapplication.data.model.DnsRecordRequest
import com.java.myapplication.data.model.DnsRecordTypes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** DNS 记录新建/编辑 ViewModel */
class DnsEditViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {

    private val zoneId: String = checkNotNull(savedStateHandle.get<String>("zoneId"))
    private val recordId: String? = savedStateHandle.get<String>("recordId")?.takeIf { it.isNotBlank() }

    data class DnsEditUiState(
        val recordType: String = "A",
        val name: String = "",
        val content: String = "",
        val ttl: Long = 1,
        val proxied: Boolean = false,
        val priority: String = "",
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
                            content = record.content,
                            ttl = record.ttl,
                            proxied = record.proxied,
                            priority = record.priority?.toString() ?: "",
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

    fun setType(type: String) {
        _uiState.update { it.copy(recordType = type) }
    }

    fun setName(value: String) {
        _uiState.update { it.copy(name = value) }
    }

    fun setContent(value: String) {
        _uiState.update { it.copy(content = value) }
    }

    fun setTtl(value: Long) {
        _uiState.update { it.copy(ttl = value) }
    }

    fun setProxied(value: Boolean) {
        _uiState.update { it.copy(proxied = value) }
    }

    fun setPriority(value: String) {
        _uiState.update { it.copy(priority = value) }
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
        if (s.content.isBlank()) {
            _uiState.update { it.copy(error = "请输入内容") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(submitting = true, error = null) }
            try {
                val request = DnsRecordRequest(
                    type = s.recordType,
                    name = s.name.trim(),
                    content = s.content.trim(),
                    ttl = s.ttl,
                    proxied = if (DnsRecordTypes.PROXIABLE.contains(s.recordType)) s.proxied else false,
                    priority = s.priority.toLongOrNull(),
                    comment = s.comment.trim().ifEmpty { null },
                    tags = emptyList()
                )
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
}