/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.core

import io.github.philtomlinson.fitgpx.core.fit.ActivityReader
import io.github.philtomlinson.fitgpx.core.fit.FitWarning.Type
import io.github.philtomlinson.fitgpx.core.model.Activity
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Real files from many devices (Garmin, Wahoo, Coros, Strava app), including damaged ones.
 * Expected values were cross-checked against two independent decoders (fitdecode and the
 * Garmin FIT SDK).
 */
class CorpusTest {

    private data class Expect(
        val points: Int,
        val withoutPosition: Int,
        val sport: String?,
        val sessions: Int,
        val warnings: List<Type> = emptyList(),
        val pauses: Int? = null,
    )

    private val expected = mapOf(
        "Activity.fit" to Expect(14, 0, "running", 1),
        "activity-unexpected-eof.fit" to Expect(14, 0, "running", 1, listOf(Type.TRUNCATED)),
        "activity-settings-nodata.fit" to Expect(14, 0, "running", 1, listOf(Type.TRUNCATED)),
        "activity-settings-corruptheader.fit" to Expect(14, 0, "running", 1, listOf(Type.TRAILING_BYTES)),
        "activity-filecrc.fit" to Expect(14, 0, "running", 1, listOf(Type.FILE_CRC_MISMATCH)),
        "activity-activity-filecrc.fit" to Expect(28, 0, "running", 2, listOf(Type.FILE_CRC_MISMATCH)),
        "garmin-fenix-5-bike.fit" to Expect(19, 0, "cycling", 1),
        "garmin-fenix-5-run.fit" to Expect(21, 0, "running", 1),
        "elemnt-bolt-no-application-id-inside-developer-data-id.fit" to Expect(131, 1, "cycling", 1),
        "2015-10-13-08-43-15.fit" to Expect(221, 0, "cycling", 1, pauses = 5),
        "compressed-speed-distance.fit" to Expect(0, 755, "running", 1),
        "sample-activity-indoor-trainer.fit" to Expect(0, 2263, "cycling", 1),
        // The last record of this crashed recording has a timestamp in 2085 and is dropped.
        "strava-android-app-201.10-b1218918.fit" to Expect(236, 237, "running", 1, listOf(Type.TRUNCATED)),
        "sample_mulitple_header.fit" to Expect(1462, 311, "multisport", 5),
        "coros-pace-2-cycling-misaligned-fields.fit" to Expect(10305, 967, "cycling", 1, pauses = 5),
        "developer-types-sample.fit" to Expect(3424, 0, "running", 1),
        "Settings.fit" to Expect(0, 0, null, 0),
    )

    private fun load(name: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/fit/$name")) { "missing fixture $name" }.use { it.readBytes() }

    @Test
    fun everyFixtureDecodesAsExpected() {
        for ((name, e) in expected) {
            val a = ActivityReader().read(load(name))
            assertEquals(e.points, a.points.size, "$name points")
            assertEquals(e.withoutPosition, a.recordsWithoutPosition, "$name records without position")
            assertEquals(e.sport, a.sport, "$name sport")
            assertEquals(e.sessions, a.sessions.size, "$name sessions")
            assertEquals(e.warnings, a.warnings.map { it.type }, "$name warnings")
            e.pauses?.let { assertEquals(it, a.pauses.size, "$name pauses") }
        }
    }

    /** Point-by-point comparison with an independent decoder's output. */
    @Test
    fun pointsMatchIndependentDecoder() {
        val files = listOf(
            "garmin-fenix-5-bike.fit",
            "2015-10-13-08-43-15.fit",
            "elemnt-bolt-no-application-id-inside-developer-data-id.fit",
            "strava-android-app-201.10-b1218918.fit",
        )
        for (name in files) {
            val golden = requireNotNull(javaClass.getResourceAsStream("/golden/$name.points.csv")).bufferedReader().readLines()
            val actual = csv(ActivityReader().read(load(name)))
            assertEquals(golden.size, actual.size, "$name line count")
            golden.zip(actual).forEachIndexed { i, (g, a) -> assertEquals(g, a, "$name line ${i + 1}") }
        }
    }

    @Test
    fun conversionOfEveryFixtureWithGpsProducesValidGpx() {
        for (name in expected.keys) {
            val a = ActivityReader().read(load(name))
            if (!a.hasGps) continue
            val gpx = java.io.StringWriter().also { FitToGpx.convert(a, it) }.toString()
            GpxSchema.assertValid(gpx, name)
            assertTrue(gpx.contains("<trkpt"), name)
        }
    }

    private fun csv(a: Activity): List<String> = a.points.map { p ->
        fun f3(d: Double?) = d?.let { String.format(Locale.ROOT, "%.3f", it) } ?: ""
        listOf(
            p.time?.div(1000)?.toString() ?: "",
            String.format(Locale.ROOT, "%.7f", p.latitude),
            String.format(Locale.ROOT, "%.7f", p.longitude),
            f3(p.elevation), p.heartRate?.toString() ?: "", p.cadence?.toString() ?: "",
            p.power?.toString() ?: "", f3(p.speed), p.temperature?.toString() ?: "",
        ).joinToString(",")
    }
}
