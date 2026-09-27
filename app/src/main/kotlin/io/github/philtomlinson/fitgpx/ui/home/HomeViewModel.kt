/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.philtomlinson.fitgpx.data.AppSettings
import io.github.philtomlinson.fitgpx.data.BatchResult
import io.github.philtomlinson.fitgpx.data.Destination
import io.github.philtomlinson.fitgpx.data.Progress
import io.github.philtomlinson.fitgpx.data.QueueItem
import io.github.philtomlinson.fitgpx.data.QueueRepository
import io.github.philtomlinson.fitgpx.data.SettingsRepository
import io.github.philtomlinson.fitgpx.data.Storage
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HomeEvent {
    data class Converted(val result: BatchResult) : HomeEvent
    data object NothingFound : HomeEvent
    data object FolderUnavailable : HomeEvent
}

class HomeViewModel(
    private val app: Application,
    private val queue: QueueRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val items: StateFlow<List<QueueItem>> = queue.items
    val progress: StateFlow<Progress> = queue.progress
    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    private var work: Job? = null

    /** Set by the editor's "Save GPX" to open the save options for a single activity. */
    private val _exportRequest = MutableStateFlow<Long?>(null)
    val exportRequest: StateFlow<Long?> = _exportRequest.asStateFlow()

    fun requestExport(id: Long) {
        _exportRequest.value = id
    }

    fun consumeExportRequest() {
        _exportRequest.value = null
    }

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        work = viewModelScope.launch {
            if (queue.import(uris) == 0) _events.send(HomeEvent.NothingFound)
        }
    }

    fun importFolder(treeUri: Uri) {
        work = viewModelScope.launch {
            if (queue.importTree(treeUri) == 0) _events.send(HomeEvent.NothingFound)
        }
    }

    /** Remembers [treeUri] as the default output folder and converts into it. */
    fun chooseFolderAndConvert(treeUri: Uri, ids: Collection<Long>? = null) {
        viewModelScope.launch {
            runCatching { Storage.persist(app, treeUri) }
            val name = runCatching { Storage.treeName(app.contentResolver, treeUri) }.getOrNull()
            settingsRepository.update { it.copy(outputFolderUri = treeUri.toString(), outputFolderName = name) }
            convert(Destination.Folder(treeUri), ids)
        }
    }

    fun convertToSavedFolder(ids: Collection<Long>? = null) {
        val uri = settings.value.outputFolderUri?.let(Uri::parse)
        if (uri == null || !Storage.hasPersistedWrite(app, uri)) {
            _events.trySend(HomeEvent.FolderUnavailable)
            return
        }
        convert(Destination.Folder(uri), ids)
    }

    fun convert(destination: Destination, ids: Collection<Long>? = null) {
        if (work?.isActive == true && progress.value is Progress.Converting) return
        work = viewModelScope.launch {
            val result = queue.convert(destination, ids)
            _events.send(HomeEvent.Converted(result))
        }
    }

    fun cancel() {
        work?.cancel()
    }

    fun remove(id: Long) = queue.remove(id)
    fun clear() = queue.clear()
    fun clearConverted() = queue.clearConverted()
}
