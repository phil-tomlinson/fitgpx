/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.core.track

import io.github.philtomlinson.fitgpx.core.model.TrackPoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    /** Mean Earth radius in metres (IUGG). */
    const val EARTH_RADIUS_M = 6_371_008.8

    /** Great-circle distance in metres (haversine). */
    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }

    fun distance(a: TrackPoint, b: TrackPoint): Double = distance(a.latitude, a.longitude, b.latitude, b.longitude)

    /** Cumulative distance along [points], in metres; element 0 is 0. */
    fun cumulativeDistance(points: List<TrackPoint>): DoubleArray {
        val out = DoubleArray(points.size)
        for (i in 1 until points.size) out[i] = out[i - 1] + distance(points[i - 1], points[i])
        return out
    }
}
