/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.core

import io.github.philtomlinson.fitgpx.core.FitTestEncoder.Companion.simpleActivity
import io.github.philtomlinson.fitgpx.core.gpx.GpxOptions
import io.github.philtomlinson.fitgpx.core.gpx.GpxWriter
import io.github.philtomlinson.fitgpx.core.model.CoursePoint
import io.github.philtomlinson.fitgpx.core.model.TrackPoint
import io.github.philtomlinson.fitgpx.core.track.EditSpec
import io.github.philtomlinson.fitgpx.core.track.TrackProcessor
import org.junit.Test
import org.w3c.dom.Element
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GpxWriterTest {

    private val activity = FitToGpx.read(simpleActivity(n = 5).build())

    private fun gpx(options: GpxOptions = GpxOptions(), edit: EditSpec = EditSpec(), a: io.github.philtomlinson.fitgpx.core.model.Activity = activity): String =
        StringWriter().also { FitToGpx.convert(a, it, edit, options) }.toString()

    private fun parse(xml: String) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(xml.byteInputStream())

    @Test
    fun defaultOutputIsSchemaValidAndComplete() {
        val xml = gpx(GpxOptions(trackName = "Morning Ride"))
        GpxSchema.assertValid(xml)
        val doc = parse(xml)
        val pts = doc.getElementsByTagNameNS("http://www.topografix.com/GPX/1/1", "trkpt")
        assertEquals(5, pts.length)
        val first = pts.item(0) as Element
        assertEquals("51", first.getAttribute("lat"))
        assertEquals("-114", first.getAttribute("lon"))
        assertTrue(xml.contains("<ele>1000</ele>"))
        assertTrue(xml.contains("<time>2023-11-14T22:13:20Z</time>"))
        assertTrue(xml.contains("<gpxtpx:hr>120</gpxtpx:hr>"))
        assertTrue(xml.contains("<gpxtpx:cad>80</gpxtpx:cad>"))
        assertTrue(xml.contains("<gpxtpx:atemp>15</gpxtpx:atemp>"))
        assertTrue(xml.contains("<gpxpx:PowerInWatts>200</gpxpx:PowerInWatts>"))
        assertTrue(xml.contains("<type>cycling</type>"))
        assertEquals(2, Regex("<name>Morning Ride</name>").findAll(xml).count()) // metadata + trk
    }

    @Test
    fun optionsStripData() {
        val xml = gpx(
            GpxOptions(
                includeElevation = false, includeTime = false, includeHeartRate = false,
                includeCadence = false, includePower = false, includeTemperature = false,
            ),
        )
        GpxSchema.assertValid(xml)
        listOf("<ele>", "<time>", "extensions", "gpxtpx", "gpxpx").forEach { assertFalse(xml.contains(it), it) }
    }

    @Test
    fun onlyPowerStillValid() {
        val xml = gpx(GpxOptions(includeHeartRate = false, includeCadence = false, includeTemperature = false))
        GpxSchema.assertValid(xml)
        assertFalse(xml.contains("gpxtpx"))
        assertTrue(xml.contains("gpxpx:PowerExtension"))
    }

    @Test
    fun compactOutputIsValid() {
        val xml = gpx(GpxOptions(prettyPrint = false, coordinateDecimals = 5))
        GpxSchema.assertValid(xml)
        assertFalse(xml.trimEnd().contains('\n'))
    }

    @Test
    fun waypointsAreEscapedAndValid() {
        val course = activity.copy(
            fileType = 6,
            coursePoints = listOf(CoursePoint(null, 51.0002, -114.0, "Café \"Summit\" <&> 'view'\u0001", "summit")),
        )
        val xml = gpx(a = course)
        GpxSchema.assertValid(xml)
        assertTrue(xml.contains("<name>Café &quot;Summit&quot; &lt;&amp;&gt; &apos;view&apos;</name>"))
        assertTrue(xml.contains("<sym>Summit</sym>"))
        val noWpt = gpx(GpxOptions(includeCoursePoints = false), a = course)
        assertFalse(noWpt.contains("<wpt"))
    }

    @Test
    fun metadataTimeFollowsTrim() {
        val xml = gpx(edit = EditSpec(trimStart = 2))
        assertTrue(xml.contains("<metadata>\n    <time>2023-11-14T22:13:22Z</time>"), xml.take(800))
    }

    @Test
    fun numberFormattingIsLocaleIndependent() {
        val default = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            val p = TrackPoint(null, -0.00000004, 12.3456789012, elevation = -3.25)
            val out = GpxWriter().writeToString(TrackProcessor.process(activity.copy(points = listOf(p))))
            assertTrue(out.contains("lat=\"0\" lon=\"12.3456789\""), out)
            assertTrue(out.contains("<ele>-3.3</ele>") || out.contains("<ele>-3.2</ele>"), out)
        } finally {
            java.util.Locale.setDefault(default)
        }
    }

    @Test
    fun fixedFormatting() {
        assertEquals("1.5", GpxWriter.fixed(1.5, 7))
        assertEquals("-0.0000001", GpxWriter.fixed(-0.0000001, 7))
        assertEquals("0", GpxWriter.fixed(-0.00000001, 7))
        assertEquals("100", GpxWriter.fixed(99.99999999, 7))
        assertEquals("179.9999999", GpxWriter.fixed(179.9999999, 7))
    }
}
