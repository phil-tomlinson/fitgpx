/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.core.track

import io.github.philtomlinson.fitgpx.core.model.Activity
import io.github.philtomlinson.fitgpx.core.model.TrackPoint

/** A circle in which no track points are exported (home, work…). */
data class PrivacyZone(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val label: String = "",
) {
    fun contains(p: TrackPoint): Boolean = Geo.distance(latitude, longitude, p.latitude, p.longitude) <= radiusMeters
}

/**
 * Everything the user can do to a track before export. The default value exports the
 * activity unchanged.
 */
data class EditSpec(
    /** First point to keep (index into [Activity.points]). */
    val trimStart: Int = 0,
    /** Last point to keep, inclusive; null keeps everything to the end. */
    val trimEnd: Int? = null,
    /** Drop the first N metres of the (trimmed) track, hiding where you started. */
    val hideStartMeters: Double = 0.0,
    /** Drop the last N metres of the (trimmed) track, hiding where you finished. */
    val hideEndMeters: Double = 0.0,
    /** Points inside any zone are removed and the track is split around them. */
    val privacyZones: List<PrivacyZone> = emptyList(),
    /** Ramer–Douglas–Peucker tolerance in metres; 0 disables simplification. */
    val simplifyToleranceMeters: Double = 0.0,
    /** Start a new track segment wherever the device timer was paused. */
    val splitAtPauses: Boolean = false,
    /** Export one track per session in multisport activities. */
    val splitSessions: Boolean = true,
    /** Discard GPS fixes that imply moving faster than this (m/s); null disables the filter. */
    val maxSpeedMps: Double? = null,
) {
    val isTrimmed: Boolean get() = trimStart > 0 || trimEnd != null
}

/** One `<trk>` of output. */
data class OutputTrack(val sport: String?, val segments: List<List<TrackPoint>>)

data class Waypoint(
    val latitude: Double,
    val longitude: Double,
    val time: Long?,
    val name: String?,
    val type: String?,
    val elevation: Double? = null,
)

data class ProcessedActivity(
    val source: Activity,
    val tracks: List<OutputTrack>,
    val coursePoints: List<Waypoint>,
    val lapPoints: List<Waypoint>,
    /** Number of points removed by privacy zones, hidden start/end and the spike filter. */
    val removedPoints: Int,
) {
    val pointCount: Int get() = tracks.sumOf { t -> t.segments.sumOf { it.size } }
    val stats: TrackStats by lazy { TrackStats.ofSegments(tracks.flatMap { it.segments }) }
}

object TrackProcessor {

    fun process(activity: Activity, spec: EditSpec = EditSpec()): ProcessedActivity {
        val all = activity.points
        if (all.isEmpty()) return ProcessedActivity(activity, emptyList(), emptyList(), emptyList(), 0)

        val start = spec.trimStart.coerceIn(0, all.lastIndex)
        val end = (spec.trimEnd ?: all.lastIndex).coerceIn(start, all.lastIndex)
        var pts: List<TrackPoint> = all.subList(start, end + 1)
        val trimmedCount = pts.size

        // Hide start / end by distance along the track.
        if (spec.hideStartMeters > 0 || spec.hideEndMeters > 0) {
            val cum = Geo.cumulativeDistance(pts)
            val total = cum.lastOrNull() ?: 0.0
            pts = pts.filterIndexed { i, _ -> cum[i] >= spec.hideStartMeters && cum[i] <= total - spec.hideEndMeters }
        }

        // GPS spike filter: drop fixes that would require an impossible speed from the last kept fix.
        spec.maxSpeedMps?.let { max ->
            val kept = ArrayList<TrackPoint>(pts.size)
            for (p in pts) {
                val prev = kept.lastOrNull()
                val t0 = prev?.time
                val t1 = p.time
                if (prev != null && t0 != null && t1 != null && t1 > t0) {
                    val v = Geo.distance(prev, p) / ((t1 - t0) / 1000.0)
                    if (v > max) continue
                }
                kept += p
            }
            pts = kept
        }

        // Tracks per session.
        val sessions = activity.sessions.filter { it.startTime != null && it.endTime != null }
        val groups: List<Pair<String?, List<TrackPoint>>> =
            if (spec.splitSessions && sessions.size > 1) {
                val ordered = sessions.sortedBy { it.startTime }
                val bySession = ordered.map { it.sport to ArrayList<TrackPoint>() }
                for (p in pts) {
                    val t = p.time
                    // A point belongs to the last session that started at or before it. Points
                    // recorded in the gap between sessions stay with the preceding one.
                    val idx = if (t == null) 0 else ordered.indexOfLast { it.startTime!! <= t }.coerceAtLeast(0)
                    bySession[idx].second += p
                }
                bySession.filter { it.second.isNotEmpty() }
            } else {
                listOf(activity.sport to pts)
            }

        val pauses = if (spec.splitAtPauses) activity.pauses else emptyList()
        // Simplification is a size optimisation, not a removal, so survivors are counted before it.
        var survivors = 0
        val tracks = groups.map { (sport, points) ->
            val segments = mutableListOf<List<TrackPoint>>()
            var current = ArrayList<TrackPoint>()
            var prev: TrackPoint? = null
            for (p in points) {
                if (spec.privacyZones.any { it.contains(p) }) {
                    if (current.isNotEmpty()) segments += current
                    current = ArrayList()
                    prev = null
                    continue
                }
                val t0 = prev?.time
                val t1 = p.time
                if (t0 != null && t1 != null && pauses.any { it.start >= t0 && it.end <= t1 }) {
                    if (current.isNotEmpty()) segments += current
                    current = ArrayList()
                }
                current += p
                survivors++
                prev = p
            }
            if (current.isNotEmpty()) segments += current
            val simplified = segments.map { Simplifier.simplify(it, spec.simplifyToleranceMeters) }
            OutputTrack(sport, simplified)
        }.filter { it.segments.isNotEmpty() }


        // Waypoints: only those within the kept time range and outside privacy zones.
        val firstT = pts.firstOrNull { it.time != null }?.time
        val lastT = pts.lastOrNull { it.time != null }?.time
        fun visible(lat: Double, lon: Double, time: Long?): Boolean {
            val probe = TrackPoint(time, lat, lon)
            if (spec.privacyZones.any { it.contains(probe) }) return false
            if (time != null && firstT != null && lastT != null && (time < firstT || time > lastT)) return false
            return true
        }
        val coursePoints = activity.coursePoints
            .filter { visible(it.latitude, it.longitude, if (spec.isTrimmed) it.time else null) }
            .map { Waypoint(it.latitude, it.longitude, it.time, it.name, it.type) }
        val laps = activity.laps
            .filter { visible(it.latitude, it.longitude, it.startTime) }
            .map { Waypoint(it.latitude, it.longitude, it.startTime, "Lap ${it.index}", "lap") }

        return ProcessedActivity(
            source = activity,
            tracks = tracks,
            coursePoints = coursePoints,
            lapPoints = laps,
            removedPoints = trimmedCount - survivors,
        )
    }
}
