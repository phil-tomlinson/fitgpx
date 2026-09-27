/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.philtomlinson.fitgpx.R
import io.github.philtomlinson.fitgpx.data.LatLon
import io.github.philtomlinson.fitgpx.data.ThemeMode
import io.github.philtomlinson.fitgpx.data.Units
import io.github.philtomlinson.fitgpx.ui.Format
import io.github.philtomlinson.fitgpx.ui.components.Stat
import io.github.philtomlinson.fitgpx.ui.components.TrackPreview
import io.github.philtomlinson.fitgpx.ui.theme.FitGpxTheme
import io.github.philtomlinson.fitgpx.ui.theme.NumberStyle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onClose: () -> Unit,
    onExport: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val applyThen: (() -> Unit) -> Unit = { next -> scope.launch { viewModel.apply(); next() } }
    BackHandler { applyThen(onClose) }
    EditorContent(
        state = state,
        selection = selection,
        onBack = { applyThen(onClose) },
        onReset = viewModel::reset,
        onRangeChange = viewModel::setRange,
        onNudgeStart = viewModel::nudgeStart,
        onNudgeEnd = viewModel::nudgeEnd,
        onAutoTrim = viewModel::autoTrimIdle,
        onHideStart = viewModel::setHideStart,
        onHideEnd = viewModel::setHideEnd,
        onDone = { applyThen(onClose) },
        onExport = { applyThen(onExport) },
    )
}

@Composable
fun EditorContent(
    state: EditorState,
    selection: SelectionStats?,
    onBack: () -> Unit,
    onReset: () -> Unit,
    onRangeChange: (Int, Int) -> Unit,
    onNudgeStart: (Int) -> Unit,
    onNudgeEnd: (Int) -> Unit,
    onAutoTrim: () -> Unit,
    onHideStart: (Int) -> Unit,
    onHideEnd: (Int) -> Unit,
    onDone: () -> Unit,
    onExport: () -> Unit,
) {
    val units = state.settings.units
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        state.track?.activity?.startTime?.let {
                            Text(
                                Format.dateTime(it, state.track.activity.utcOffsetSeconds),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
                actions = {
                    if (state.track != null) {
                        TextButton(onClick = onReset, enabled = state.isTrimmed || state.hideStartMeters != state.settings.hideStartMeters || state.hideEndMeters != state.settings.hideEndMeters) {
                            Icon(Icons.Filled.RestartAlt, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.editor_reset))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (state.track != null) {
                Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f).height(52.dp)) {
                            Text(stringResource(R.string.editor_done))
                        }
                        Button(onClick = onExport, modifier = Modifier.weight(1f).height(52.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.editor_save_gpx))
                        }
                    }
                }
            }
        },
    ) { padding ->
        val track = state.track
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.editor_loading))
                }
            }
            track == null -> Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.editor_error, state.error ?: ""), color = MaterialTheme.colorScheme.error)
            }
            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                val visible = state.visibleRange
                // The map stays outside the scrolling area so panning it never scrolls the page.
                Box(
                    Modifier.fillMaxWidth().height(280.dp).padding(horizontal = 12.dp).clip(RoundedCornerShape(24.dp)),
                ) {
                    val dark = when (state.settings.themeMode) {
                        ThemeMode.SYSTEM -> isSystemInDarkTheme()
                        ThemeMode.DARK -> true
                        ThemeMode.LIGHT -> false
                    }
                    if (state.settings.showMap && !LocalInspectionMode.current) {
                        TrackMap(track, visible, state.settings.privacyZones, FitGpxTheme.colors, dark, Modifier.fillMaxSize())
                    } else {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxSize()) {
                            val preview = remember(track) { track.displayIndices.map { LatLon(track.points[it].latitude, track.points[it].longitude) } }
                            val hl = remember(track, visible) {
                                val idx = track.displayIndices
                                val a = idx.indexOfFirst { it >= visible.first }.coerceAtLeast(0)
                                val b = idx.indexOfLast { it <= visible.last }.coerceAtLeast(a)
                                a..b
                            }
                            TrackPreview(preview, Modifier.fillMaxSize().padding(12.dp), strokeWidth = 3.dp, highlight = hl)
                        }
                    }
                }
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatsCard(selection, units)
                    TrimCard(state, visible, units, onRangeChange, onNudgeStart, onNudgeEnd, onAutoTrim)
                    PrivacyCard(state, selection, units, onHideStart, onHideEnd)
                }
            }
        }
    }
}

@Composable
private fun StatsCard(selection: SelectionStats?, units: Units) {
    val s = selection?.stats
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(stringResource(R.string.stat_distance), s?.let { Format.distance(it.distanceMeters, units) } ?: "–", Modifier.weight(1f), Icons.Filled.Straighten)
            Stat(stringResource(R.string.stat_moving), s?.movingMillis?.let { Format.duration(it) } ?: s?.elapsedMillis?.let { Format.duration(it) } ?: "–", Modifier.weight(1f), Icons.Filled.Timer)
            Stat(stringResource(R.string.stat_elevation), s?.elevationGain?.let { Format.elevation(it, units) } ?: "–", Modifier.weight(1f), Icons.Filled.Landscape)
        }
    }
}

@Composable
private fun TrimCard(
    state: EditorState,
    visible: IntRange,
    units: Units,
    onRangeChange: (Int, Int) -> Unit,
    onNudgeStart: (Int) -> Unit,
    onNudgeEnd: (Int) -> Unit,
    onAutoTrim: () -> Unit,
) {
    val track = state.track ?: return
    val series = remember(track) { availableSeries(track) }
    var chosen by rememberSaveable { mutableStateOf(series.firstOrNull()) }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ContentCut, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.editor_trim), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onAutoTrim) {
                    Icon(Icons.Filled.AutoFixHigh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.editor_auto_trim))
                }
            }
            Text(
                stringResource(R.string.editor_trim_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (series.size > 1) {
                Spacer(Modifier.height(12.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    series.forEachIndexed { i, s ->
                        SegmentedButton(
                            selected = chosen == s,
                            onClick = { chosen = s },
                            shape = SegmentedButtonDefaults.itemShape(i, series.size),
                            icon = { Icon(seriesIcon(s), null, Modifier.size(16.dp)) },
                        ) { Text(stringResource(seriesLabel(s)), maxLines = 1) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            val current = chosen
            if (current != null) {
                ProfileChart(
                    track = track,
                    series = current,
                    start = state.start,
                    end = state.end,
                    visible = visible,
                    units = units,
                    onRangeChange = onRangeChange,
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    description = stringResource(R.string.editor_chart_description),
                )
            }
            RangeSlider(
                value = state.start.toFloat()..state.end.toFloat(),
                onValueChange = { onRangeChange(it.start.roundToInt(), it.endInclusive.roundToInt()) },
                valueRange = 0f..state.lastIndex.coerceAtLeast(1).toFloat(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HandleReadout(stringResource(R.string.editor_start), state, state.start, units, Modifier.weight(1f)) { onNudgeStart(it) }
                HandleReadout(stringResource(R.string.editor_end), state, state.end, units, Modifier.weight(1f)) { onNudgeEnd(it) }
            }
        }
    }
}

@Composable
private fun HandleReadout(label: String, state: EditorState, index: Int, units: Units, modifier: Modifier, onNudge: (Int) -> Unit) {
    val track = state.track ?: return
    val p = track.points[index]
    val startTime = track.points.first().time
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onNudge(-1) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.ChevronLeft, stringResource(R.string.editor_nudge_back, label))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Format.distanceShort(track.cumulative[index], units), style = NumberStyle, maxLines = 1)
                val t = p.time
                if (t != null && startTime != null) {
                    Text(
                        "${Format.duration(t - startTime)} · ${Format.clock(t, track.activity.utcOffsetSeconds)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            IconButton(onClick = { onNudge(1) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.ChevronRight, stringResource(R.string.editor_nudge_forward, label))
            }
        }
    }
}

@Composable
private fun PrivacyCard(state: EditorState, selection: SelectionStats?, units: Units, onHideStart: (Int) -> Unit, onHideEnd: (Int) -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Shield, null, tint = FitGpxTheme.colors.privacyZone, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.editor_privacy), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                stringResource(R.string.editor_privacy_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            HideSlider(stringResource(R.string.editor_hide_start), state.hideStartMeters, units, onHideStart)
            HideSlider(stringResource(R.string.editor_hide_end), state.hideEndMeters, units, onHideEnd)
            val zones = state.settings.privacyZones.size
            if (zones > 0) {
                Text(
                    pluralStringResource(R.plurals.editor_zones_applied, zones, zones),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val removed = selection?.removedPoints ?: 0
            if (removed > 0) {
                Text(
                    pluralStringResource(R.plurals.editor_points_hidden, removed, removed),
                    style = MaterialTheme.typography.bodySmall,
                    color = FitGpxTheme.colors.privacyZone,
                )
            }
        }
    }
}

@Composable
private fun HideSlider(label: String, meters: Int, units: Units, onChange: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                if (meters == 0) stringResource(R.string.editor_hide_off) else Format.distance(meters.toDouble(), units),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = meters.toFloat(),
            onValueChange = { onChange((it / 50).roundToInt() * 50) },
            valueRange = 0f..2000f,
            steps = 39,
        )
    }
}

private fun seriesLabel(s: ChartSeries): Int = when (s) {
    ChartSeries.ELEVATION -> R.string.chart_elevation
    ChartSeries.SPEED -> R.string.chart_speed
    ChartSeries.HEART_RATE -> R.string.chart_heart_rate
}

private fun seriesIcon(s: ChartSeries): ImageVector = when (s) {
    ChartSeries.ELEVATION -> Icons.Filled.Landscape
    ChartSeries.SPEED -> Icons.Filled.Speed
    ChartSeries.HEART_RATE -> Icons.Filled.Favorite
}
