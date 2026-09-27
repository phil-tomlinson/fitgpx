/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.data

import android.content.Context
import android.net.Uri
import io.github.philtomlinson.fitgpx.core.FitToGpx
import io.github.philtomlinson.fitgpx.core.fit.FitException
import io.github.philtomlinson.fitgpx.core.io.InputUnpacker
import io.github.philtomlinson.fitgpx.core.model.Activity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

sealed interface Progress {
    data object Idle : Progress
    data class Importing(val found: Int, val current: String) : Progress
    data class Converting(val done: Int, val total: Int, val current: String) : Progress
}

data class BatchResult(
    val destination: Destination,
    val converted: Int,
    val failed: Int,
    val shareUris: List<Uri>,
    val folderName: String?,
)

/**
 * The import queue: owns the list of activities, imports documents (streaming archives so even
 * huge bulk exports fit in memory) and converts batches to a [Destination].
 */
class QueueRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val _items = MutableStateFlow<List<QueueItem>>(emptyList())
    val items: StateFlow<List<QueueItem>> = _items.asStateFlow()

    private val _progress = MutableStateFlow<Progress>(Progress.Idle)
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private val ids = AtomicLong(1)
    private val resolver get() = context.contentResolver

    fun item(id: Long): QueueItem? = _items.value.firstOrNull { it.id == id }

    fun remove(id: Long) = _items.update { list -> list.filterNot { it.id == id } }

    fun clear() = _items.update { emptyList() }

    fun clearConverted() = _items.update { list -> list.filterNot { it.status is ItemStatus.Done } }

    fun updateEdit(id: Long, edit: ItemEdit?, summary: ActivitySummary?) = updateItem(id) {
        it.copy(edit = edit, summary = summary ?: it.summary, status = if (it.status is ItemStatus.Done) ItemStatus.Ready else it.status)
    }

    private fun updateItem(id: Long, transform: (QueueItem) -> QueueItem) =
        _items.update { list -> list.map { if (it.id == id) transform(it) else it } }

    /** Imports documents picked by the user or shared from another app. Returns the number of activities added. */
    suspend fun import(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var added = 0
        try {
            for (uri in uris) {
                currentCoroutineContext().ensureActive()
                added += importDocument(uri, Storage.displayName(resolver, uri))
            }
        } finally {
            _progress.value = Progress.Idle
        }
        added
    }

    /** Imports every supported file found in a folder tree. */
    suspend fun importTree(treeUri: Uri): Int = withContext(Dispatchers.IO) {
        _progress.value = Progress.Importing(0, Storage.treeName(resolver, treeUri))
        val files = runCatching { Storage.listTree(resolver, treeUri) }.getOrElse { emptyList() }
        var added = 0
        try {
            for ((uri, name) in files) {
                currentCoroutineContext().ensureActive()
                added += importDocument(uri, name)
            }
        } finally {
            _progress.value = Progress.Idle
        }
        added
    }

    private suspend fun importDocument(uri: Uri, containerName: String): Int {
        val existing = _items.value.map { it.source.uri to it.source.index }.toSet()
        val job = currentCoroutineContext()[Job]
        var added = 0
        try {
            val input = resolver.openInputStream(uri) ?: throw FileNotFoundException(containerName)
            input.use { stream ->
                InputUnpacker.stream(containerName, stream) { source ->
                    job?.ensureActive()
                    if ((uri to source.index) in existing) return@stream
                    val ref = SourceRef(uri, source.index, source.name, containerName)
                    _progress.value = Progress.Importing(_items.value.size + 1, source.name)
                    val item = try {
                        val activity = FitToGpx.read(source.bytes)
                        val summary = ActivitySummary.of(activity)
                        QueueItem(ids.getAndIncrement(), ref, if (activity.hasGps) ItemStatus.Ready else ItemStatus.NoGps, summary)
                    } catch (e: FitException) {
                        QueueItem(ids.getAndIncrement(), ref, ItemStatus.Failed(e.message ?: "Unreadable FIT file"))
                    }
                    _items.update { it + item }
                    added++
                }
            }
        } catch (e: IOException) {
            // The whole document is unusable (unsupported type, unreadable, too large).
            _items.update {
                it + QueueItem(ids.getAndIncrement(), SourceRef(uri, 0, containerName, containerName), ItemStatus.Failed(e.message ?: "Could not read file"))
            }
            added++
        } catch (e: SecurityException) {
            _items.update {
                it + QueueItem(ids.getAndIncrement(), SourceRef(uri, 0, containerName, containerName), ItemStatus.Failed("Permission to read this file was revoked"))
            }
            added++
        }
        return added
    }

    /** Loads the full activity for the editor. */
    suspend fun loadActivity(ref: SourceRef): Activity = withContext(Dispatchers.IO) {
        val input = resolver.openInputStream(ref.uri) ?: throw FileNotFoundException(ref.containerName)
        val source = input.use { InputUnpacker.find(ref.containerName, it, ref.index) }
            ?: throw FileNotFoundException(ref.name)
        FitToGpx.read(source.bytes)
    }

    /** Converts the given items (all convertible ones when [ids] is null) to [destination]. */
    suspend fun convert(destination: Destination, ids: Collection<Long>? = null): BatchResult = withContext(Dispatchers.IO) {
        val settings = settingsRepository.settings.first()
        val targets = _items.value.filter { it.canConvert && (ids == null || it.id in ids) }
        var done = 0
        var failed = 0
        _progress.value = Progress.Converting(0, targets.size, "")
        val job = currentCoroutineContext()[Job]
        val sink = OutputSink.create(context, destination, settings.conflictPolicy)
        try {
            sink.use {
                // One pass over each document, so an archive is decompressed only once.
                for ((uri, group) in targets.groupBy { it.source.uri }) {
                    val byIndex = group.associateBy { it.source.index }
                    val remaining = byIndex.keys.toMutableSet()
                    try {
                        val input = resolver.openInputStream(uri) ?: throw FileNotFoundException(group.first().source.containerName)
                        input.use { stream ->
                            InputUnpacker.stream(group.first().source.containerName, stream) { source ->
                                val item = byIndex[source.index] ?: return@stream
                                remaining -= source.index
                                job?.ensureActive()
                                _progress.value = Progress.Converting(done + failed, targets.size, item.summary?.title ?: source.name)
                                updateItem(item.id) { it.copy(status = ItemStatus.Converting) }
                                val status = try {
                                    val activity = FitToGpx.read(source.bytes)
                                    val desired = FitToGpx.fileName(settings.nameTemplate, source.name, activity)
                                    val (name, out) = sink.open(desired)
                                    out.bufferedWriter(Charsets.UTF_8).use { w ->
                                        FitToGpx.convert(
                                            activity, w,
                                            settings.editSpec(item.edit, activity.sport),
                                            settings.gpxOptions(item.summary?.title),
                                        )
                                    }
                                    done++
                                    ItemStatus.Done(name)
                                } catch (e: Exception) {
                                    failed++
                                    ItemStatus.ConvertFailed(e.message ?: e.javaClass.simpleName)
                                }
                                updateItem(item.id) { it.copy(status = status) }
                                _progress.value = Progress.Converting(done + failed, targets.size, "")
                                if (remaining.isEmpty()) throw StopReading()
                            }
                        }
                    } catch (_: StopReading) {
                        // All wanted payloads of this document were converted.
                    } catch (e: IOException) {
                        for (index in remaining) {
                            failed++
                            byIndex[index]?.let { item -> updateItem(item.id) { it.copy(status = ItemStatus.ConvertFailed(e.message ?: "Could not read file")) } }
                        }
                    }
                    currentCoroutineContext().ensureActive()
                }
            }
        } finally {
            _progress.value = Progress.Idle
            // Anything still marked as converting was interrupted (cancelled).
            _items.update { list -> list.map { if (it.status == ItemStatus.Converting) it.copy(status = ItemStatus.Ready) else it } }
        }
        BatchResult(
            destination = destination,
            converted = done,
            failed = failed,
            shareUris = if (destination == Destination.Share) sink.sharedUris else emptyList(),
            folderName = (destination as? Destination.Folder)?.let { settings.outputFolderName },
        )
    }

    private class StopReading : RuntimeException(null, null, false, false)
}
