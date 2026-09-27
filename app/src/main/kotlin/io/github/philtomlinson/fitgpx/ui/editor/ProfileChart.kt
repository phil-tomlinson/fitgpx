/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.philtomlinson.fitgpx.data.Units
import io.github.philtomlinson.fitgpx.ui.Format
import io.github.philtomlinson.fitgpx.ui.theme.FitGpxTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class ChartSeries { ELEVATION, SPEED, HEART_RATE }

/** Per-point values of a series, NaN where the device recorded nothing. */
internal fun seriesValues(track: LoadedTrack, series: ChartSeries): FloatArray {
    val pts = track.points
    return when (series) {
        ChartSeries.ELEVATION -> FloatArray(pts.size) { pts[it].elevation?.toFloat() ?: Float.NaN }
        ChartSeries.HEART_RATE -> FloatArray(pts.size) { pts[it].heartRate?.toFloat() ?: Float.NaN }
        ChartSeries.SPEED -> {
            val recorded = pts.any { it.speed != null }
            if (recorded) {
                FloatArray(pts.size) { pts[it].speed?.toFloat() ?: Float.NaN }
            } else {
                // Derive from positions over a 5-point window to smooth GPS noise.
                FloatArray(pts.size) { i ->
                    val a = max(0, i - 2)
                    val b = min(pts.lastIndex, i + 2)
                    val t0 = pts[a].time
                    val t1 = pts[b].time
                    if (t0 == null || t1 == null || t1 <= t0) Float.NaN
                    else ((track.cumulative[b] - track.cumulative[a]) / ((t1 - t0) / 1000.0)).toFloat()
                }
            }
        }
    }
}

internal fun availableSeries(track: LoadedTrack): List<ChartSeries> = buildList {
    if (track.activity.hasElevation) add(ChartSeries.ELEVATION)
    if (track.points.any { it.time != null }) add(ChartSeries.SPEED)
    if (track.activity.hasHeartRate) add(ChartSeries.HEART_RATE)
}

/**
 * An area chart of [series] against distance with draggable trim handles. Dragging near a
 * handle moves it; tapping moves the nearest handle to the tapped position.
 */
@Composable
fun ProfileChart(
    track: LoadedTrack,
    series: ChartSeries,
    start: Int,
    end: Int,
    visible: IntRange,
    units: Units,
    onRangeChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    description: String = "",
) {
    val colors = FitGpxTheme.colors
    val seriesColor = when (series) {
        ChartSeries.ELEVATION -> colors.elevation
        ChartSeries.SPEED -> colors.speed
        ChartSeries.HEART_RATE -> colors.heartRate
    }
    val muted = MaterialTheme.colorScheme.outlineVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val handleColor = MaterialTheme.colorScheme.primary
    val startColor = colors.start
    val finishColor = colors.finish
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = onSurfaceVariant)

    val values = remember(track, series) { seriesValues(track, series) }
    val cum = track.cumulative
    val total = cum.lastOrNull() ?: 0.0
    // Down-sample to a few hundred columns: enough for any screen, cheap to redraw while dragging.
    val sample = remember(track) {
        val n = track.points.size
        val step = max(1, n / 600)
        (0 until n step step).toMutableList().also { if (it.last() != n - 1) it += n - 1 }.toIntArray()
    }
    val range = remember(values) {
        var lo = Float.POSITIVE_INFINITY
        var hi = Float.NEGATIVE_INFINITY
        for (v in values) if (!v.isNaN()) { lo = min(lo, v); hi = max(hi, v) }
        if (lo > hi) 0f to 1f else if (hi - lo < 1f) (lo - 0.5f) to (hi + 0.5f) else lo to hi
    }

    val currentStart by rememberUpdatedState(start)
    val currentEnd by rememberUpdatedState(end)
    val onChange by rememberUpdatedState(onRangeChange)

    fun indexAt(x: Float, width: Float): Int {
        if (total <= 0.0) return ((x / width) * (cum.size - 1)).toInt().coerceIn(0, cum.size - 1)
        val d = (x / width).coerceIn(0f, 1f) * total
        var lo = 0
        var hi = cum.lastIndex
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (cum[mid] < d) lo = mid + 1 else hi = mid
        }
        return lo
    }
    fun xOf(i: Int, width: Float): Float = if (total <= 0.0) width * i / max(1, cum.size - 1) else (cum[i] / total * width).toFloat()

    fun moveNearest(x: Float, width: Float, forceHandle: Int? = null): Int {
        val idx = indexAt(x, width)
        val which = forceHandle ?: if (abs(x - xOf(currentStart, width)) <= abs(x - xOf(currentEnd, width))) 0 else 1
        if (which == 0) onChange(min(idx, currentEnd), currentEnd) else onChange(currentStart, max(idx, currentStart))
        return which
    }

    Canvas(
        modifier
            .semantics { contentDescription = description }
            .pointerInput(track) {
                detectTapGestures { moveNearest(it.x, size.width.toFloat()) }
            }
            .pointerInput(track) {
                var handle = 0
                detectDragGestures(
                    onDragStart = { handle = moveNearest(it.x, size.width.toFloat()) },
                    onDrag = { change, _ ->
                        change.consume()
                        moveNearest(change.position.x, size.width.toFloat(), handle)
                    },
                )
            },
    ) {
        val topPad = 18.dp.toPx()
        val bottomPad = 20.dp.toPx()
        val w = size.width
        val h = size.height - topPad - bottomPad
        val (lo, hi) = range
        fun y(v: Float) = topPad + h - (v - lo) / (hi - lo) * h

        fun area(from: Int, to: Int): Pair<Path, Path>? {
            val fill = Path()
            val line = Path()
            var started = false
            var lastX = 0f
            var firstX = 0f
            for (i in sample) {
                if (i < from || i > to) continue
                val v = values[i]
                if (v.isNaN()) continue
                val x = xOf(i, w)
                if (!started) {
                    fill.moveTo(x, topPad + h); fill.lineTo(x, y(v)); line.moveTo(x, y(v)); firstX = x; started = true
                } else {
                    fill.lineTo(x, y(v)); line.lineTo(x, y(v))
                }
                lastX = x
            }
            if (!started) return null
            fill.lineTo(lastX, topPad + h); fill.lineTo(firstX, topPad + h); fill.close()
            return fill to line
        }

        // Baseline
        drawLine(muted, Offset(0f, topPad + h), Offset(w, topPad + h), strokeWidth = 1.dp.toPx())

        area(0, values.lastIndex)?.let { (fill, line) ->
            drawPath(fill, muted.copy(alpha = 0.35f))
            drawPath(line, muted, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        val vis = visible
        area(vis.first, vis.last)?.let { (fill, line) ->
            val x0 = xOf(vis.first, w)
            val x1 = xOf(vis.last, w)
            clipRect(left = x0, right = max(x1, x0 + 1f)) {
                drawPath(fill, seriesColor.copy(alpha = 0.28f))
                drawPath(line, seriesColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }

        // Handles
        drawHandle(xOf(start, w), topPad, h, startColor, handleColor)
        drawHandle(xOf(end, w), topPad, h, finishColor, handleColor)

        // Labels: value range on the left, distance range along the bottom.
        val fmt: (Float) -> String = when (series) {
            ChartSeries.ELEVATION -> { v -> Format.elevation(v.toDouble(), units) }
            ChartSeries.SPEED -> { v -> Format.speed(v.toDouble(), units) }
            ChartSeries.HEART_RATE -> { v -> "${v.toInt()} bpm" }
        }
        drawText(measurer, fmt(hi), Offset(2.dp.toPx(), 0f), labelStyle)
        val loLabel = measurer.measure(fmt(lo), labelStyle)
        drawText(loLabel, topLeft = Offset(2.dp.toPx(), topPad + h - loLabel.size.height - 2.dp.toPx()))
        drawText(measurer, Format.distanceShort(0.0, units), Offset(0f, topPad + h + 3.dp.toPx()), labelStyle)
        val totalLabel = measurer.measure(Format.distanceShort(total, units), labelStyle)
        drawText(totalLabel, topLeft = Offset(w - totalLabel.size.width, topPad + h + 3.dp.toPx()))
    }
}

private fun DrawScope.drawHandle(x: Float, top: Float, h: Float, dot: Color, line: Color) {
    drawLine(line, Offset(x, top - 4.dp.toPx()), Offset(x, top + h), strokeWidth = 2.dp.toPx())
    val knobW = 10.dp.toPx()
    val knobH = 18.dp.toPx()
    drawRoundRect(
        color = line,
        topLeft = Offset(x - knobW / 2, top + h / 2 - knobH / 2),
        size = Size(knobW, knobH),
        cornerRadius = CornerRadius(knobW / 2),
    )
    drawCircle(Color.White, 5.dp.toPx(), Offset(x, top - 4.dp.toPx()))
    drawCircle(dot, 3.5.dp.toPx(), Offset(x, top - 4.dp.toPx()))
}
