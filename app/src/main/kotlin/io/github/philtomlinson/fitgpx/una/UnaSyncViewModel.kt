/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.una

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.philtomlinson.fitgpx.data.AppSettings
import io.github.philtomlinson.fitgpx.data.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class UnaSyncViewModel(
    private val sync: UnaSync,
    private val devicesSource: UnaDevices,
    private val settingsRepository: SettingsRepository,
    private val demoConnector: WatchConnector?,
) : ViewModel() {

    val state: StateFlow<SyncState> = sync.state
    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _devices = MutableStateFlow<List<WatchDevice>>(emptyList())
    val devices: StateFlow<List<WatchDevice>> = _devices.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    val demoAvailable: Boolean get() = demoConnector != null

    private var scanJob: Job? = null
    private var syncJob: Job? = null

    init {
        sync.reset()
    }

    /** Called once Bluetooth permissions are granted and the adapter is on. */
    fun refresh() {
        _devices.value = devicesSource.paired(settings.value.unaAddress)
        startScan()
    }

    private fun startScan() {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            _scanning.value = true
            withTimeoutOrNull(SCAN_MS) {
                devicesSource.scan().collect { found ->
                    _devices.update { list -> if (list.any { it.address == found.address }) list else list + found }
                }
            }
            _scanning.value = false
        }
    }

    fun syncWith(device: WatchDevice) {
        scanJob?.cancel()
        _scanning.value = false
        viewModelScope.launch { settingsRepository.update { it.copy(unaAddress = device.address, unaName = device.name) } }
        run(devicesSource.connector(device.address))
    }

    fun syncDemo() {
        demoConnector?.let(::run)
    }

    private fun run(connector: WatchConnector) {
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch { sync.sync(connector) }
    }

    fun cancel() {
        syncJob?.cancel()
    }

    fun addAllSynced() {
        viewModelScope.launch { sync.importAllSynced() }
    }

    fun reset() = sync.reset()

    override fun onCleared() {
        scanJob?.cancel()
    }

    private companion object {
        const val SCAN_MS = 20_000L
    }
}
