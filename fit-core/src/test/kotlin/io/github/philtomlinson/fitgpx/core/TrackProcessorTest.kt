/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.core

import io.github.philtomlinson.fitgpx.core.model.Activity
import io.github.philtomlinson.fitgpx.core.model.Pause
import io.github.philtomlinson.fitgpx.core.model.Session
import io.github.philtomlinson.fitgpx.core.model.TrackPoint
import io.github.philtomlinson.fitgpx.core.track.EditSpec
import io.github.philtomlinson.fitgpx.core.track.Geo
import io.github.philtomlinson.fitgpx.core.track.PrivacyZone
import io.github.philtomlinson.fitgpx.core.track.Simplifier
import io.github.philtomlinson.fitgpx.core.track.TrackProcessor
import io.github.philtomlinson.fitgpx.core.track.TrackStats
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackProcessorTest {

    /** 101 points 1 s apart going north ~11.1 m per point (0.0001°). */
    private fun line(n: Int = 101, t0: Long = 1_700_000_000_000L): List<TrackPoint> =
        (0 until n).map { i -> TrackPoint(t0 + i * 1000L, 51.0 + i * 0.0001, -114.0, elevation = 1000.0 + i) }

    private fun activity(points: List<TrackPoint>, sessions: List<Session> = emptyList(), pauses: List<Pause> = emptyList()) =
        Activity(
            fileType = 4, manufacturer = null, product = null, timeCreated = null, sport = "running", name = null,
            points = points, sessions = sessions, laps = emptyList(), coursePoints = emptyList(), pauses = pauses,
            utcOffsetSeconds = null, recordsWithoutPosition = 0, recordCount = points.size, warnings = emptyList(),
        )

    @Test
    fun defaultSpecKeepsEverything() {
        val pts = line()
        val out = TrackProcessor.process(activity(pts))
        assertEquals(listOf(pts), out.tracks.single().segments)
        assertEquals(0, out.removedPoints)
    }

    @Test
    fun trimKeepsInclusiveRange() {
        val pts = line()
        val out = TrackProcessor.process(activity(pts), EditSpec(trimStart = 10, trimEnd = 20))
        val seg = out.tracks.single().segments.single()
        assertEquals(pts.subList(10, 21), seg)
        // Trimming is what the user asked for, not "removed" data.
        assertEquals(0, out.removedPoints)
    }

    @Test
    fun trimIndicesAreClamped() {
        val out = TrackProcessor.process(activity(line(5)), EditSpec(trimStart = -3, trimEnd = 99))
        assertEquals(5, out.pointCount)
        val single = TrackProcessor.process(activity(line(5)), EditSpec(trimStart = 4, trimEnd = 1))
        assertEquals(1, single.pointCount)
    }

    @Test
    fun hideStartAndEndByDistance() {
        val pts = line()
        val step = Geo.distance(pts[0], pts[1])
        val out = TrackProcessor.process(activity(pts), EditSpec(hideStartMeters = step * 10 - 0.01, hideEndMeters = step * 5 - 0.01))
        val seg = out.tracks.single().segments.single()
        assertEquals(pts[10], seg.first())
        assertEquals(pts[95], seg.last())
        assertEquals(15, out.removedPoints)
    }

    @Test
    fun privacyZoneRemovesPointsAndSplitsTrack() {
        val pts = line()
        val zone = PrivacyZone(pts[50].latitude, pts[50].longitude, radiusMeters = 30.0)
        val out = TrackProcessor.process(activity(pts), EditSpec(privacyZones = listOf(zone)))
        val segs = out.tracks.single().segments
        assertEquals(2, segs.size)
        assertTrue(segs.flatten().none { zone.contains(it) })
        assertEquals(pts.count { zone.contains(it) }, out.removedPoints)
        assertTrue(out.removedPoints in 5..7)
    }

    @Test
    fun splitAtPauses() {
        val pts = line(10)
        val pause = Pause(pts[4].time!! + 100, pts[5].time!! - 100)
        val split = TrackProcessor.process(activity(pts, pauses = listOf(pause)), EditSpec(splitAtPauses = true))
        assertEquals(listOf(5, 5), split.tracks.single().segments.map { it.size })
        val joined = TrackProcessor.process(activity(pts, pauses = listOf(pause)), EditSpec(splitAtPauses = false))
        assertEquals(1, joined.tracks.single().segments.size)
    }

    @Test
    fun multisportBecomesOneTrackPerSession() {
        val pts = line(30)
        val t = { i: Int -> pts[i].time!! }
        val sessions = listOf(
            Session(t(0), t(9), "swimming", null, null, null),
            Session(t(10), t(19), "cycling", null, null, null),
            Session(t(20), t(29), "running", null, null, null),
        )
        val out = TrackProcessor.process(activity(pts, sessions), EditSpec())
        assertEquals(listOf("swimming", "cycling", "running"), out.tracks.map { it.sport })
        assertEquals(listOf(10, 10, 10), out.tracks.map { it.segments.single().size })
        val merged = TrackProcessor.process(activity(pts, sessions), EditSpec(splitSessions = false))
        assertEquals(1, merged.tracks.size)
    }

    @Test
    fun spikeFilterDropsTeleports() {
        val pts = line(10).toMutableList()
        pts[5] = pts[5].copy(latitude = 52.0) // 100 km away for one second
        val out = TrackProcessor.process(activity(pts), EditSpec(maxSpeedMps = 50.0))
        assertEquals(9, out.pointCount)
        assertEquals(1, out.removedPoints)
    }

    @Test
    fun simplificationKeepsShapeAndEndpoints() {
        val pts = line()
        val simplified = Simplifier.simplify(pts, 1.0)
        assertEquals(listOf(pts.first(), pts.last()), simplified) // a straight line needs two points
        val zigzag = pts.mapIndexed { i, p -> if (i % 2 == 0) p else p.copy(longitude = p.longitude + 0.001) }
        assertEquals(zigzag, Simplifier.simplify(zigzag, 1.0)) // ~70 m deviations are all kept
        assertEquals(pts, Simplifier.simplify(pts, 0.0))
        assertEquals(listOf(0, 100), Simplifier.simplifyIndices(pts, 1.0).toList())
        assertEquals(zigzag.indices.toList(), Simplifier.simplifyIndices(zigzag, 1.0).toList())
    }

    @Test
    fun statsAreSensible() {
        val pts = line()
        val s = TrackStats.of(pts)
        assertEquals(101, s.pointCount)
        assertEquals(1111.95, s.distanceMeters, 0.5) // 0.01° of latitude
        assertEquals(100_000L, s.elapsedMillis)
        assertEquals(100_000L, s.movingMillis)
        assertEquals(100.0, s.elevationGain!!, 1e-9)
        assertEquals(0.0, s.elevationLoss!!, 1e-9)
        assertEquals(11.12, s.avgMovingSpeed!!, 0.01)
    }

    @Test
    fun elevationNoiseIsFiltered() {
        val noisy = line(50).mapIndexed { i, p -> p.copy(elevation = 1000.0 + if (i % 2 == 0) 0.0 else 1.5) }
        val s = TrackStats.of(noisy)
        assertEquals(0.0, s.elevationGain!!, 1e-9)
    }

    @Test
    fun stoppedTimeIsNotMoving() {
        val t0 = 1_700_000_000_000L
        val pts = (0 until 10).map { TrackPoint(t0 + it * 1000L, 51.0, -114.0) }
        assertEquals(0L, TrackStats.of(pts).movingMillis)
    }
}
