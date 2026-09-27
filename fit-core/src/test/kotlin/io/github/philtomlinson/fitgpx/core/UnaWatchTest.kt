/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.core

import io.github.philtomlinson.fitgpx.core.fit.ActivityReader
import io.github.philtomlinson.fitgpx.core.fit.FitProfile
import org.junit.Test
import java.io.StringWriter
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `una-watch-mtb.fit` was encoded by the UNA Watch SDK's own `SDK::Fit::FitWriter` (see
 * `una-watch-mtb.gen.cpp`): Una manufacturer ID, a developer field, a timer pause and the
 * mountain-bike sub-sport, exactly as the watch writes them.
 */
class UnaWatchTest {

    private val activity = ActivityReader().read(
        requireNotNull(javaClass.getResourceAsStream("/fit/una-watch-mtb.fit")).use { it.readBytes() },
    )

    @Test
    fun decodesUnaFile() {
        assertTrue(activity.warnings.isEmpty(), activity.warnings.toString())
        assertEquals(1500, activity.points.size)
        assertEquals("Una", activity.manufacturer)
        assertEquals("cycling", activity.sport)
        assertEquals(FitProfile.SubSport.MOUNTAIN, activity.subSport)
        assertEquals(1, activity.pauses.size)
        assertEquals(-6 * 3600, activity.utcOffsetSeconds)
        val p = activity.points.first()
        assertEquals(51.0890, p.latitude, 1e-4)
        assertEquals(1360.0, p.elevation!!, 0.5)
        assertEquals(135, p.heartRate)
        assertEquals(78, p.cadence)
    }

    @Test
    fun namedAsMountainBikeRide() {
        assertTrue(FitToGpx.defaultTitle(activity).endsWith("Mountain Bike Ride"), FitToGpx.defaultTitle(activity))
    }

    @Test
    fun convertsToValidGpx() {
        val gpx = StringWriter().also { FitToGpx.convert(activity, it) }.toString()
        GpxSchema.assertValid(gpx, "una")
        assertEquals(1500, Regex("<trkpt ").findAll(gpx).count())
    }
}
