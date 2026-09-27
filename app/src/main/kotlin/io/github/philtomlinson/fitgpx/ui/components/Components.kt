/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DownhillSkiing
import androidx.compose.material.icons.filled.ElectricBike
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.GolfCourse
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.IceSkating
import androidx.compose.material.icons.filled.Kayaking
import androidx.compose.material.icons.filled.NordicWalking
import androidx.compose.material.icons.filled.Paragliding
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Rowing
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material.icons.filled.Snowboarding
import androidx.compose.material.icons.filled.Snowshoeing
import androidx.compose.material.icons.filled.Surfing
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.philtomlinson.fitgpx.data.LatLon
import io.github.philtomlinson.fitgpx.ui.theme.FitGpxTheme
import io.github.philtomlinson.fitgpx.ui.theme.NumberStyle
import kotlin.math.cos
import kotlin.math.max

fun sportIcon(sport: String?): ImageVector = when (sport) {
    "cycling" -> Icons.AutoMirrored.Filled.DirectionsBike
    "e_biking" -> Icons.Filled.ElectricBike
    "running", "transition" -> Icons.AutoMirrored.Filled.DirectionsRun
    "walking", "wheelchair_push_walk" -> Icons.AutoMirrored.Filled.DirectionsWalk
    "hiking", "mountaineering", "geocaching" -> Icons.Filled.Hiking
    "swimming" -> Icons.Filled.Pool
    "alpine_skiing" -> Icons.Filled.DownhillSkiing
    "cross_country_skiing" -> Icons.Filled.NordicWalking
    "snowboarding" -> Icons.Filled.Snowboarding
    "snowshoeing" -> Icons.Filled.Snowshoeing
    "ice_skating", "inline_skating" -> Icons.Filled.IceSkating
    "rowing" -> Icons.Filled.Rowing
    "paddling", "kayaking", "canoeing", "rafting", "stand_up_paddleboarding" -> Icons.Filled.Kayaking
    "sailing", "windsurfing", "kitesurfing" -> Icons.Filled.Sailing
    "surfing", "wakeboarding", "wakesurfing", "water_skiing" -> Icons.Filled.Surfing
    "boating" -> Icons.Filled.DirectionsBoat
    "driving", "motor_sports" -> Icons.Filled.DirectionsCar
    "motorcycling" -> Icons.Filled.TwoWheeler
    "flying" -> Icons.Filled.Flight
    "hang_gliding", "sky_diving" -> Icons.Filled.Paragliding
    "golf", "disc_golf" -> Icons.Filled.GolfCourse
    "rock_climbing" -> Icons.Filled.Terrain
    "multisport" -> Icons.Filled.EmojiEvents
    else -> Icons.Filled.Route
}

/**
 * Draws a route shape without a map: used for list thumbnails, as the offline fallback when the
 * map is disabled, and in screenshot tests.
 */
@Composable
fun TrackPreview(
    points: List<LatLon>,
    modifier: Modifier = Modifier,
    color: Color = FitGpxTheme.colors.track,
    strokeWidth: Dp = 2.5.dp,
    highlight: IntRange? = null,
    mutedColor: Color = FitGpxTheme.colors.trackMuted,
    showEndpoints: Boolean = true,
) {
    val start = FitGpxTheme.colors.start
    val finish = FitGpxTheme.colors.finish
    val projected = remember(points) { project(points) }
    Canvas(modifier) {
        if (projected.isEmpty()) return@Canvas
        val pad = strokeWidth.toPx() * 2 + 2.dp.toPx()
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        val (minX, minY, spanX, spanY) = projected.bounds
        val scale = if (spanX == 0.0 && spanY == 0.0) 1.0 else minOf(w / max(spanX, 1e-12), h / max(spanY, 1e-12))
        val offX = pad + (w - spanX * scale) / 2
        val offY = pad + (h - spanY * scale) / 2
        fun at(i: Int) = Offset(
            (offX + (projected.xs[i] - minX) * scale).toFloat(),
            (offY + (spanY - (projected.ys[i] - minY)) * scale).toFloat(),
        )
        fun path(range: IntRange): Path = Path().apply {
            for (i in range) {
                val o = at(i)
                if (i == range.first) moveTo(o.x, o.y) else lineTo(o.x, o.y)
            }
        }
        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val all = projected.xs.indices
        if (highlight == null) {
            drawPath(path(all), color, style = stroke)
        } else {
            drawPath(path(all), mutedColor.copy(alpha = 0.6f), style = stroke)
            val r = highlight.first.coerceIn(all)..highlight.last.coerceIn(all)
            if (r.first < r.last) drawPath(path(r), color, style = Stroke(width = strokeWidth.toPx() * 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (showEndpoints) {
            val r = strokeWidth.toPx() * 1.6f
            val first = highlight?.first?.coerceIn(all) ?: 0
            val last = highlight?.last?.coerceIn(all) ?: all.last
            drawCircle(Color.White, r * 1.45f, at(first)); drawCircle(start, r, at(first))
            drawCircle(Color.White, r * 1.45f, at(last)); drawCircle(finish, r, at(last))
        }
    }
}

private class Projected(val xs: DoubleArray, val ys: DoubleArray) {
    fun isEmpty() = xs.isEmpty()
    val bounds: List<Double> by lazy {
        val minX = xs.min(); val minY = ys.min()
        listOf(minX, minY, xs.max() - minX, ys.max() - minY)
    }
}

private fun project(points: List<LatLon>): Projected {
    if (points.isEmpty()) return Projected(DoubleArray(0), DoubleArray(0))
    val k = cos(Math.toRadians(points.sumOf { it.latitude } / points.size))
    return Projected(DoubleArray(points.size) { points[it].longitude * k }, DoubleArray(points.size) { points[it].latitude })
}

/** A labelled value, e.g. "Distance / 42.2 km". */
@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, icon: ImageVector? = null, iconTint: Color = MaterialTheme.colorScheme.primary) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text(value, style = NumberStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}
