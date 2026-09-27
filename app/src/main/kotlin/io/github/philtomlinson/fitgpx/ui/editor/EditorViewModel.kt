/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.philtomlinson.fitgpx.core.model.Activity
import io.github.philtomlinson.fitgpx.core.track.Geo
import io.github.philtomlinson.fitgpx.core.track.Simplifier
import io.github.philtomlinson.fitgpx.core.track.TrackProcessor
import io.github.philtomlinson.fitgpx.core.track.TrackStats
import io.github.philtomlinson.fitgpx.data.ActivitySummary
import io.github.philtomlinson.fitgpx.data.AppSettings
import io.github.philtomlinson.fitgpx.data.ItemEdit
import io.github.philtomlinson.fitgpx.data.QueueRepository
import io.github.philtomlinson.fitgpx.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The loaded track plus data derived once for fast interactive editing. */
class LoadedTrack(val activity: Activity) {
    val points = activity.points
    val cumulative: DoubleArray = Geo.cumulativeDistance(points)

    /** Indices kept for drawing on the map (~2 m tolerance): exact shape at a fraction of the cost. */
    val displayIndices: IntArray = Simplifier.simplifyIndices(points, 2.0)
}

data class EditorState(
    val loading: Boolean = true,
    val error: String? = null,
    val title: String = "",
    val track: LoadedTrack? = null,
    val start: Int = 0,
    val end: Int = 0,
    val hideStartMeters: Int = 0,
    val hideEndMeters: Int = 0,
    val settings: AppSettings = AppSettings(),
) {
    val lastIndex: Int get() = (track?.points?.size ?: 1) - 1
    val isTrimmed: Boolean get() = start > 0 || end < lastIndex

    /** First and last index that survive the hide-start/hide-end distances. */
    val visibleRange: IntRange
        get() {
            val t = track ?: return 0..0
            val c = t.cumulative
            var a = start
            while (a < end && c[a] - c[start] < hideStartMeters) a++
            var b = end
            while (b > a && c[end] - c[b] < hideEndMeters) b--
            return a..b
        }
}

data class SelectionStats(val stats: TrackStats, val removedPoints: Int, val keptPoints: Int)

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModel(
    private val itemId: Long,
    private val queue: QueueRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    /** Stats of exactly what will be exported, recomputed (off the main thread) as the user edits. */
    val selection: StateFlow<SelectionStats?> = _state
        .mapLatest { s ->
            val t = s.track ?: return@mapLatest null
            val spec = s.settings.editSpec(currentEdit(s), t.activity.sport).copy(simplifyToleranceMeters = 0.0)
            val processed = TrackProcessor.process(t.activity, spec)
            SelectionStats(processed.stats, processed.removedPoints, processed.pointCount)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            val item = queue.item(itemId)
            val settings = settingsRepository.settings.first()
            if (item == null) {
                _state.update { it.copy(loading = false, error = "Activity no longer in the list") }
                return@launch
            }
            try {
                val activity = queue.loadActivity(item.source)
                val track = LoadedTrack(activity)
                val last = track.points.lastIndex.coerceAtLeast(0)
                val edit = item.edit
                _state.update {
                    it.copy(
                        loading = false,
                        title = item.summary?.title ?: item.source.name,
                        track = track,
                        start = (edit?.trimStart ?: 0).coerceIn(0, last),
                        end = (edit?.trimEnd ?: last).coerceIn(0, last),
                        hideStartMeters = edit?.hideStartMeters ?: settings.hideStartMeters,
                        hideEndMeters = edit?.hideEndMeters ?: settings.hideEndMeters,
                        settings = settings,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: e.javaClass.simpleName) }
            }
        }
        // Keep global settings (privacy zones etc.) live while editing.
        viewModelScope.launch {
            settingsRepository.settings.collect { s -> _state.update { it.copy(settings = s) } }
        }
    }

    fun setRange(start: Int, end: Int) = _state.update {
        val last = it.lastIndex
        val a = start.coerceIn(0, last)
        val b = end.coerceIn(0, last)
        if (a <= b) it.copy(start = a, end = b) else it
    }

    fun nudgeStart(delta: Int) = _state.value.let { setRange(it.start + delta, it.end) }
    fun nudgeEnd(delta: Int) = _state.value.let { setRange(it.start, it.end + delta) }

    /** Cuts standing around before the start and after the finish (GPS wander at a café…). */
    fun autoTrimIdle() {
        val s = _state.value
        val t = s.track ?: return
        val pts = t.points
        if (pts.size < 3) return
        val radius = IDLE_RADIUS_M
        var a = 0
        while (a < pts.lastIndex && Geo.distance(pts[0], pts[a]) < radius) a++
        var b = pts.lastIndex
        while (b > a && Geo.distance(pts[pts.lastIndex], pts[b]) < radius) b--
        // Keep one point inside the radius so the track still starts where movement began.
        setRange((a - 1).coerceAtLeast(0), (b + 1).coerceAtMost(pts.lastIndex))
    }

    fun setHideStart(meters: Int) = _state.update { it.copy(hideStartMeters = meters) }
    fun setHideEnd(meters: Int) = _state.update { it.copy(hideEndMeters = meters) }

    fun reset() = _state.update {
        it.copy(start = 0, end = it.lastIndex, hideStartMeters = it.settings.hideStartMeters, hideEndMeters = it.settings.hideEndMeters)
    }

    private fun currentEdit(s: EditorState): ItemEdit? {
        val edit = ItemEdit(
            trimStart = s.start,
            trimEnd = s.end.takeIf { it < s.lastIndex },
            hideStartMeters = s.hideStartMeters.takeIf { it != s.settings.hideStartMeters },
            hideEndMeters = s.hideEndMeters.takeIf { it != s.settings.hideEndMeters },
        )
        return edit.takeUnless { it == ItemEdit() }
    }

    /** Stores the edit on the queue item so conversion uses it. */
    suspend fun apply() {
        val s = _state.value
        val t = s.track ?: return
        val edit = currentEdit(s)
        val summary = withContext(Dispatchers.Default) {
            val processed = TrackProcessor.process(t.activity, s.settings.editSpec(edit, t.activity.sport))
            val kept = processed.tracks.flatMap { it.segments }.flatten()
            ActivitySummary.of(t.activity.copy(points = kept), processed.stats).copy(title = s.title)
        }
        queue.updateEdit(itemId, edit, summary)
    }

    companion object {
        const val IDLE_RADIUS_M = 25.0
    }
}
