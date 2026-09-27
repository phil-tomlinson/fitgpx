/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.data

import android.net.Uri
import io.github.philtomlinson.fitgpx.core.FitToGpx
import io.github.philtomlinson.fitgpx.core.model.Activity
import io.github.philtomlinson.fitgpx.core.track.Simplifier
import io.github.philtomlinson.fitgpx.core.track.TrackStats

/** Where an activity came from: a document, and the payload index inside it (for archives). */
data class SourceRef(
    val uri: Uri,
    /** Index of the FIT payload inside the document (0 for a plain .fit file). */
    val index: Int,
    /** Name of the FIT payload, e.g. "12345.fit" inside "export.zip". */
    val name: String,
    /** Display name of the document the user picked. */
    val containerName: String,
)

/** Per-activity edits made in the editor. Null fields fall back to the global settings. */
data class ItemEdit(
    val trimStart: Int = 0,
    val trimEnd: Int? = null,
    val hideStartMeters: Int? = null,
    val hideEndMeters: Int? = null,
) {
    val isTrimmed: Boolean get() = trimStart > 0 || trimEnd != null
}

data class LatLon(val latitude: Double, val longitude: Double)

/** What the list shows for an activity, computed once at import time. */
data class ActivitySummary(
    val title: String,
    val sport: String?,
    val startTime: Long?,
    val utcOffsetSeconds: Int?,
    val device: String?,
    val pointCount: Int,
    val distanceMeters: Double,
    val elapsedMillis: Long?,
    val movingMillis: Long?,
    val elevationGain: Double?,
    val hasHeartRate: Boolean,
    val hasPower: Boolean,
    val hasCadence: Boolean,
    val isCourse: Boolean,
    /** True when the file was damaged and FitGPX recovered what it could. */
    val recovered: Boolean,
    val warnings: List<String>,
    /** A light copy of the route for list thumbnails. */
    val preview: List<LatLon>,
) {
    companion object {
        fun of(activity: Activity, stats: TrackStats = TrackStats.of(activity.points)): ActivitySummary = ActivitySummary(
            title = FitToGpx.defaultTitle(activity),
            sport = activity.sport,
            startTime = activity.startTime,
            utcOffsetSeconds = activity.utcOffsetSeconds,
            device = listOfNotNull(activity.manufacturer, activity.product).joinToString(" ").ifBlank { null },
            pointCount = stats.pointCount,
            distanceMeters = stats.distanceMeters,
            elapsedMillis = stats.elapsedMillis,
            movingMillis = stats.movingMillis,
            elevationGain = stats.elevationGain,
            hasHeartRate = activity.hasHeartRate,
            hasPower = activity.hasPower,
            hasCadence = activity.hasCadence,
            isCourse = activity.isCourse,
            recovered = activity.warnings.isNotEmpty(),
            warnings = activity.warnings.map { it.message },
            preview = preview(activity),
        )

        private fun preview(activity: Activity): List<LatLon> {
            val pts = activity.points
            if (pts.isEmpty()) return emptyList()
            val b = TrackStats.of(pts).bounds ?: return emptyList()
            // Tolerance of ~0.5% of the route's extent keeps the shape at thumbnail size.
            val extentM = maxOf(b.maxLat - b.minLat, b.maxLon - b.minLon) * 111_000
            var simple = Simplifier.simplify(pts, (extentM * 0.005).coerceAtLeast(1.0))
            if (simple.size > MAX_PREVIEW) {
                val step = simple.size / MAX_PREVIEW.toDouble()
                simple = List(MAX_PREVIEW) { simple[(it * step).toInt()] } + simple.last()
            }
            return simple.map { LatLon(it.latitude, it.longitude) }
        }

        private const val MAX_PREVIEW = 150
    }
}

sealed interface ItemStatus {
    data object Reading : ItemStatus
    data object Ready : ItemStatus
    data object NoGps : ItemStatus
    data class Failed(val message: String) : ItemStatus
    data object Converting : ItemStatus
    data class Done(val fileName: String) : ItemStatus
    data class ConvertFailed(val message: String) : ItemStatus
}

data class QueueItem(
    val id: Long,
    val source: SourceRef,
    val status: ItemStatus,
    val summary: ActivitySummary? = null,
    val edit: ItemEdit? = null,
) {
    val canConvert: Boolean
        get() = summary != null && status !is ItemStatus.NoGps && status !is ItemStatus.Failed && status !is ItemStatus.Reading
}
