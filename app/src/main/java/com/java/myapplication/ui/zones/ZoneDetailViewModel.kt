package com.java.myapplication.ui.zones

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.java.myapplication.AppContainer
import com.java.myapplication.data.model.Zone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ZoneDetailViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {

    private val zoneId: String = checkNotNull(savedStateHandle["zoneId"])

    data class ZoneDetailUiState(
        val zone: Zone? = null,
        val loading: Boolean = true,
        val error: String? = null,
        val deleting: Boolean = false,
        val deleted: Boolean = false
    )

    private val _uiState = MutableStateFlow(ZoneDetailUiState())
    val uiState: StateFlow<ZoneDetailUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val zone = AppContainer.repository.getZone(zoneId)
                _uiState.update { it.copy(zone = zone, loading = false, error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun deleteZone() {
        viewModelScope.launch {
            _uiState.update { it.copy(deleting = true) }
            try {
                AppContainer.repository.deleteZone(zoneId)
                _uiState.update { it.copy(deleting = false, deleted = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(deleting = false, error = e.message) }
            }
        }
    }
}