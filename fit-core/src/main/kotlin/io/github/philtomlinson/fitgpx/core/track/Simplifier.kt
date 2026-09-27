/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.core.track

import io.github.philtomlinson.fitgpx.core.model.TrackPoint
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Ramer–Douglas–Peucker line simplification.
 *
 * Removes points that deviate less than a tolerance (metres) from the line through their
 * neighbours, which shrinks files dramatically while keeping the shape of the route. Distances
 * are measured on a local equirectangular projection, which is accurate to well under a
 * percent over the extent of any single activity.
 */
object Simplifier {

    fun simplify(points: List<TrackPoint>, toleranceMeters: Double): List<TrackPoint> {
        if (points.size < 3 || toleranceMeters <= 0.0) return points
        val keep = keepMask(points, toleranceMeters)
        return points.filterIndexed { i, _ -> keep[i] }
    }

    /** Indices of the points [simplify] would keep, in order. */
    fun simplifyIndices(points: List<TrackPoint>, toleranceMeters: Double): IntArray {
        if (points.size < 3 || toleranceMeters <= 0.0) return IntArray(points.size) { it }
        val keep = keepMask(points, toleranceMeters)
        return keep.indices.filter { keep[it] }.toIntArray()
    }

    private fun keepMask(points: List<TrackPoint>, toleranceMeters: Double): BooleanArray {
        val refLat = Math.toRadians(points.sumOf { it.latitude } / points.size)
        val mPerDegLat = Math.PI / 180 * Geo.EARTH_RADIUS_M
        val mPerDegLon = mPerDegLat * cos(refLat)
        val xs = DoubleArray(points.size) { points[it].longitude * mPerDegLon }
        val ys = DoubleArray(points.size) { points[it].latitude * mPerDegLat }

        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true
        // Explicit stack instead of recursion: long activities would overflow the call stack.
        val stack = ArrayDeque<IntArray>()
        stack.addLast(intArrayOf(0, points.lastIndex))
        while (stack.isNotEmpty()) {
            val (first, last) = stack.removeLast().let { it[0] to it[1] }
            if (last - first < 2) continue
            var maxDist = -1.0
            var index = -1
            for (i in first + 1 until last) {
                val d = perpendicular(xs[i], ys[i], xs[first], ys[first], xs[last], ys[last])
                if (d > maxDist) {
                    maxDist = d
                    index = i
                }
            }
            if (maxDist > toleranceMeters) {
                keep[index] = true
                stack.addLast(intArrayOf(first, index))
                stack.addLast(intArrayOf(index, last))
            }
        }
        return keep
    }

    private fun perpendicular(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 == 0.0) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }
}
