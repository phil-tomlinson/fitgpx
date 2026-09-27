/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.philtomlinson.fitgpx.core.track.PrivacyZone
import io.github.philtomlinson.fitgpx.data.AppSettings
import io.github.philtomlinson.fitgpx.data.LatLon
import io.github.philtomlinson.fitgpx.data.QueueRepository
import io.github.philtomlinson.fitgpx.data.SettingsRepository
import io.github.philtomlinson.fitgpx.data.Storage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val app: Application,
    private val repository: SettingsRepository,
    private val queue: QueueRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.update(transform) }
    }

    fun setOutputFolder(treeUri: Uri) {
        viewModelScope.launch {
            runCatching { Storage.persist(app, treeUri) }
            val name = runCatching { Storage.treeName(app.contentResolver, treeUri) }.getOrNull()
            repository.update { it.copy(outputFolderUri = treeUri.toString(), outputFolderName = name) }
        }
    }

    fun clearOutputFolder() {
        val old = settings.value.outputFolderUri?.let(Uri::parse)
        if (old != null) {
            runCatching {
                app.contentResolver.releasePersistableUriPermission(
                    old,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        update { it.copy(outputFolderUri = null, outputFolderName = null) }
    }

    fun addZone(zone: PrivacyZone) = update { it.copy(privacyZones = it.privacyZones + zone) }

    fun updateZone(index: Int, zone: PrivacyZone) = update {
        it.copy(privacyZones = it.privacyZones.mapIndexed { i, z -> if (i == index) zone else z })
    }

    fun removeZone(index: Int) = update { it.copy(privacyZones = it.privacyZones.filterIndexed { i, _ -> i != index }) }

    /** A sensible place to centre the zone map: the start of an imported activity, if any. */
    fun suggestedCenter(): LatLon? = queue.items.value.firstNotNullOfOrNull { it.summary?.preview?.firstOrNull() }
}
