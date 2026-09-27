/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.core.track

import io.github.philtomlinson.fitgpx.core.model.TrackPoint

/** Summary statistics of a track (or a trimmed part of one). */
data class TrackStats(
    val pointCount: Int,
    val distanceMeters: Double,
    val elapsedMillis: Long?,
    val movingMillis: Long?,
    val elevationGain: Double?,
    val elevationLoss: Double?,
    val minElevation: Double?,
    val maxElevation: Double?,
    val maxSpeed: Double?,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val avgPower: Int?,
    val avgCadence: Int?,
    val bounds: Bounds?,
) {
    /** Average moving speed in m/s. */
    val avgMovingSpeed: Double?
        get() = movingMillis?.takeIf { it > 0 }?.let { distanceMeters / (it / 1000.0) }

    data class Bounds(val minLat: Double, val minLon: Double, val maxLat: Double, val maxLon: Double)

    companion object {
        /** Speed below which the athlete is considered stopped (m/s). */
        const val MOVING_THRESHOLD_MPS = 0.5

        /** Gaps longer than this are treated as the device being paused. */
        const val MAX_MOVING_GAP_MS = 5 * 60 * 1000L

        /** Elevation noise filter: changes smaller than this are ignored (metres). */
        const val ELEVATION_HYSTERESIS_M = 2.0

        val EMPTY = TrackStats(0, 0.0, null, null, null, null, null, null, null, null, null, null, null, null)

        fun of(points: List<TrackPoint>): TrackStats = ofSegments(listOf(points))

        /** Stats over several segments: distance and time are not counted across gaps between segments. */
        fun ofSegments(segments: List<List<TrackPoint>>): TrackStats {
            var count = 0
            var distance = 0.0
            var moving = 0L
            var hasTime = false
            var gain = 0.0
            var loss = 0.0
            var hasEle = false
            var minEle = Double.POSITIVE_INFINITY
            var maxEle = Double.NEGATIVE_INFINITY
            var maxSpeed: Double? = null
            var hrSum = 0L
            var hrN = 0
            var hrMax = 0
            var pwSum = 0L
            var pwN = 0
            var cadSum = 0L
            var cadN = 0
            var minLat = 90.0
            var maxLat = -90.0
            var minLon = 180.0
            var maxLon = -180.0
            var firstTime = Long.MAX_VALUE
            var lastTime = Long.MIN_VALUE

            for (seg in segments) {
                var anchorEle: Double? = null
                for (i in seg.indices) {
                    val p = seg[i]
                    count++
                    if (p.latitude < minLat) minLat = p.latitude
                    if (p.latitude > maxLat) maxLat = p.latitude
                    if (p.longitude < minLon) minLon = p.longitude
                    if (p.longitude > maxLon) maxLon = p.longitude
                    val t = p.time
                    if (t != null) {
                        if (t < firstTime) firstTime = t
                        if (t > lastTime) lastTime = t
                    }
                    p.heartRate?.let { hrSum += it; hrN++; if (it > hrMax) hrMax = it }
                    p.power?.let { pwSum += it; pwN++ }
                    p.cadence?.takeIf { it > 0 }?.let { cadSum += it; cadN++ }
                    val v = p.speed
                    if (v != null && v > (maxSpeed ?: Double.NEGATIVE_INFINITY)) maxSpeed = v
                    p.elevation?.let { e ->
                        hasEle = true
                        if (e < minEle) minEle = e
                        if (e > maxEle) maxEle = e
                        val a = anchorEle
                        if (a == null) {
                            anchorEle = e
                        } else if (e - a >= ELEVATION_HYSTERESIS_M) {
                            gain += e - a
                            anchorEle = e
                        } else if (a - e >= ELEVATION_HYSTERESIS_M) {
                            loss += a - e
                            anchorEle = e
                        }
                    }
                    if (i > 0) {
                        val prev = seg[i - 1]
                        val d = Geo.distance(prev, p)
                        distance += d
                        val t0 = prev.time
                        val t1 = p.time
                        if (t0 != null && t1 != null) {
                            hasTime = true
                            val dt = t1 - t0
                            if (dt in 1..MAX_MOVING_GAP_MS && d / (dt / 1000.0) >= MOVING_THRESHOLD_MPS) moving += dt
                        }
                    }
                }
            }
            if (count == 0) return EMPTY
            return TrackStats(
                pointCount = count,
                distanceMeters = distance,
                elapsedMillis = if (firstTime <= lastTime) lastTime - firstTime else null,
                movingMillis = if (hasTime) moving else null,
                elevationGain = if (hasEle) gain else null,
                elevationLoss = if (hasEle) loss else null,
                minElevation = if (hasEle) minEle else null,
                maxElevation = if (hasEle) maxEle else null,
                maxSpeed = maxSpeed,
                avgHeartRate = if (hrN > 0) (hrSum / hrN).toInt() else null,
                maxHeartRate = if (hrN > 0) hrMax else null,
                avgPower = if (pwN > 0) (pwSum / pwN).toInt() else null,
                avgCadence = if (cadN > 0) (cadSum / cadN).toInt() else null,
                bounds = Bounds(minLat, minLon, maxLat, maxLon),
            )
        }
    }
}
