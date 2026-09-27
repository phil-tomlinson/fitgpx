/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx

import io.github.philtomlinson.fitgpx.core.track.PrivacyZone
import io.github.philtomlinson.fitgpx.data.ActivitySummary
import io.github.philtomlinson.fitgpx.data.AppSettings
import io.github.philtomlinson.fitgpx.data.ItemEdit
import io.github.philtomlinson.fitgpx.data.SettingsRepository
import io.github.philtomlinson.fitgpx.data.Storage
import io.github.philtomlinson.fitgpx.data.Units
import io.github.philtomlinson.fitgpx.ui.Format
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Plain JVM tests of app logic that doesn't need Android. */
class AppLogicTest {

    @Test
    fun privacyZonesRoundTrip() {
        val zones = listOf(PrivacyZone(51.044712, -114.071912, 200.0, "Home; sweet, home"), PrivacyZone(-33.8688, 151.2093, 1500.0))
        val decoded = SettingsRepository.decodeZones(SettingsRepository.encodeZones(zones))
        assertEquals(zones.map { it.label }, decoded.map { it.label })
        decoded.zip(zones).forEach { (a, b) ->
            assertEquals(b.latitude, a.latitude, 1e-6)
            assertEquals(b.longitude, a.longitude, 1e-6)
            assertEquals(b.radiusMeters, a.radiusMeters, 0.5)
        }
        assertEquals(emptyList(), SettingsRepository.decodeZones(""))
        assertEquals(emptyList(), SettingsRepository.decodeZones("garbage;1,2"))
    }

    @Test
    fun editSpecCombinesGlobalAndItemSettings() {
        val s = AppSettings(hideStartMeters = 300, simplifyMeters = 3)
        val spec = s.editSpec(ItemEdit(trimStart = 5, trimEnd = 10, hideEndMeters = 100), "cycling")
        assertEquals(5, spec.trimStart)
        assertEquals(10, spec.trimEnd)
        assertEquals(300.0, spec.hideStartMeters)
        assertEquals(100.0, spec.hideEndMeters)
        assertEquals(3.0, spec.simplifyToleranceMeters)
        assertEquals(AppSettings.SPIKE_SPEED_MPS, spec.maxSpeedMps)
        assertNull(s.editSpec(null, "flying").maxSpeedMps, "no spike filter for aircraft")
    }

    @Test
    fun supportedNames() {
        listOf("a.fit", "A.FIT", "123.fit.gz", "export.zip").forEach { assertTrue(Storage.isSupportedName(it), it) }
        listOf("a.gpx", "photo.jpg", "fit").forEach { assertTrue(!Storage.isSupportedName(it), it) }
    }

    @Test
    fun formatting() {
        val default = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("850 m", Format.distance(850.0, Units.METRIC))
            assertEquals("42.20 km", Format.distance(42_195.0, Units.METRIC))
            assertEquals("26.22 mi", Format.distance(42_195.0, Units.IMPERIAL))
            assertEquals("1:02:03", Format.duration(3_723_000))
            assertEquals("2:03", Format.duration(123_000))
            assertEquals("45 s", Format.duration(45_000))
            assertEquals("3,281 ft", Format.elevation(1000.0, Units.IMPERIAL))
            assertEquals("36.0 km/h", Format.speed(10.0, Units.METRIC))
        } finally {
            Locale.setDefault(default)
        }
    }

    @Test
    fun summaryPreviewIsCompact() {
        val s = ActivitySummary.of(DemoData.ride())
        assertTrue(s.preview.size in 10..151, "preview size ${s.preview.size}")
        assertTrue(s.title.endsWith("Ride"), s.title)
        assertTrue(s.distanceMeters > 30_000)
    }
}
