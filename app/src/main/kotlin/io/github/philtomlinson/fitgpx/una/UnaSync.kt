/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.una

import android.content.Context
import android.net.Uri
import io.github.philtomlinson.fitgpx.data.QueueRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** A live link to a watch (Bluetooth or simulated). */
class WatchLink(val transport: FtsTransport, val protocolVersion: Int, private val onClose: () -> Unit) : AutoCloseable {
    override fun close() = onClose()
}

/** Opens a [WatchLink]; blocking. */
fun interface WatchConnector {
    fun connect(onStage: (ConnectStage) -> Unit): WatchLink
}

sealed interface SyncState {
    data object Idle : SyncState
    data class Connecting(val stage: ConnectStage) : SyncState
    data class Scanning(val app: String?) : SyncState
    data class Downloading(val index: Int, val count: Int, val name: String, val received: Long, val total: Long) : SyncState
    data object Importing : SyncState
    data class Done(val added: Int, val alreadySynced: Int, val failed: Int) : SyncState
    data class Failed(val message: String) : SyncState

    val busy: Boolean get() = this is Connecting || this is Scanning || this is Downloading || this is Importing
}

/**
 * Copies new activities from a UNA Watch into app storage and adds them to the conversion list.
 *
 * Files are kept under `files/una/<App>/` so a sync only transfers what is new (same name and size
 * = already synced). Nothing is ever deleted from the watch.
 */
class UnaSync(context: Context, private val queue: QueueRepository) {
    private val dir = File(context.filesDir, "una")

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    fun reset() {
        if (!_state.value.busy) _state.value = SyncState.Idle
    }

    private fun local(a: RemoteActivity) = File(File(dir, a.app.replace('/', '_')), a.name)

    /** Activities already copied from the watch. */
    fun syncedFiles(): List<File> = dir.walkTopDown().filter { it.isFile && it.name.endsWith(".fit", true) }.sortedByDescending { it.name }.toList()

    fun clearSynced() {
        dir.deleteRecursively()
    }

    suspend fun sync(connector: WatchConnector) {
        val newFiles = mutableListOf<File>()
        var already = 0
        var failed = 0
        try {
            runInterruptible(Dispatchers.IO) {
                connector.connect { _state.value = SyncState.Connecting(it) }.use { link ->
                    val client = FtsClient(link.transport, link.protocolVersion)
                    _state.value = SyncState.Scanning(null)
                    val found = UnaActivityScanner(client).scan { _state.value = SyncState.Scanning(it) }
                    val todo = found.filter { local(it).let { f -> !f.exists() || f.length() != it.size } }
                    already = found.size - todo.size
                    todo.forEachIndexed { i, a ->
                        _state.value = SyncState.Downloading(i + 1, todo.size, a.name, 0, a.size)
                        try {
                            val bytes = client.readFile(a.path) { r, t -> _state.value = SyncState.Downloading(i + 1, todo.size, a.name, r, t) }
                            val target = local(a)
                            target.parentFile?.mkdirs()
                            val tmp = File(target.parentFile, "${target.name}.part")
                            tmp.writeBytes(bytes)
                            if (!tmp.renameTo(target)) throw IOException("Couldn't save ${a.name}")
                            newFiles += target
                        } catch (e: FtsException.Cancelled) {
                            throw e
                        } catch (e: FtsException) {
                            failed++
                        }
                    }
                }
            }
            _state.value = SyncState.Importing
            importOnce(newFiles)
            _state.value = SyncState.Done(newFiles.size, already, failed)
        } catch (e: CancellationException) {
            _state.value = SyncState.Idle
            throw e
        } catch (e: FtsException.Cancelled) {
            _state.value = SyncState.Idle
        } catch (e: Exception) {
            _state.value = SyncState.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            // Whatever was copied before a failure or cancel still goes into the list, otherwise
            // the next sync would consider it already synced and never show it.
            withContext(NonCancellable) { importOnce(newFiles) }
        }
    }

    private val imported = HashSet<String>()

    private suspend fun importOnce(files: List<File>) {
        val fresh = files.filter { imported.add(it.path) }
        if (fresh.isNotEmpty()) queue.import(fresh.map { Uri.fromFile(it) })
    }

    /** Adds every activity already copied from the watch to the list (e.g. after clearing the list). */
    suspend fun importAllSynced(): Int {
        imported.clear()
        return queue.import(syncedFiles().map { Uri.fromFile(it) })
    }
}
