/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.core

import io.github.philtomlinson.fitgpx.core.FitTestEncoder.Companion.simpleActivity
import io.github.philtomlinson.fitgpx.core.io.InputUnpacker
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.ZoneOffset
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InputAndNamingTest {

    private val fit = simpleActivity(n = 3).build()

    private fun gzip(b: ByteArray) = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(b) } }.toByteArray()

    private fun zip(vararg entries: Pair<String, ByteArray>) = ByteArrayOutputStream().also { o ->
        ZipOutputStream(o).use { z ->
            for ((name, data) in entries) {
                z.putNextEntry(ZipEntry(name)); z.write(data); z.closeEntry()
            }
        }
    }.toByteArray()

    @Test
    fun plainFit() {
        val out = InputUnpacker.unpack("ride.fit", fit)
        assertEquals(listOf("ride.fit"), out.map { it.name })
    }

    @Test
    fun gzipFromStravaExport() {
        val out = InputUnpacker.unpack("123.fit.gz", gzip(fit))
        assertEquals("123.fit", out.single().name)
        assertTrue(out.single().bytes.contentEquals(fit))
    }

    @Test
    fun zipWithMixedContent() {
        val archive = zip(
            "activities/1.fit" to fit,
            "activities/2.fit.gz" to gzip(fit),
            "activities/photo.jpg" to ByteArray(100),
            "__MACOSX/activities/._1.fit" to ByteArray(10),
            "nested.zip" to zip("3.FIT" to fit),
        )
        val out = InputUnpacker.unpack("export.zip", archive)
        assertEquals(listOf("1.fit", "2.fit", "3.FIT"), out.map { it.name })
        assertEquals(listOf(0, 1, 2), out.map { it.index })
        val third = InputUnpacker.find("export.zip", archive.inputStream(), 2)
        assertEquals("3.FIT", third?.name)
        assertEquals(null, InputUnpacker.find("export.zip", archive.inputStream(), 3))
    }

    @Test
    fun rejectsUnknownAndOversizedInput() {
        assertFailsWith<InputUnpacker.UnsupportedInputException> { InputUnpacker.unpack("x.gpx", "<gpx/>".toByteArray()) }
        val limits = InputUnpacker.Limits(maxFileBytes = 1024, maxTotalBytes = 4096)
        val bomb = gzip(ByteArray(1_000_000).also { it[8] = '.'.code.toByte(); it[9] = 'F'.code.toByte(); it[10] = 'I'.code.toByte(); it[11] = 'T'.code.toByte() })
        assertFailsWith<InputUnpacker.UnsupportedInputException> { InputUnpacker.unpack("bomb.fit.gz", bomb, limits) }
        val many = zip(*Array(10) { "$it.fit" to fit })
        assertFailsWith<InputUnpacker.UnsupportedInputException> {
            InputUnpacker.unpack("many.zip", many, InputUnpacker.Limits(maxEntries = 5))
        }
    }

    @Test
    fun fileNameTemplates() {
        val a = FitToGpx.read(fit)
        val utc = ZoneOffset.UTC
        assertEquals("ride.gpx", FitToGpx.fileName("{name}", "ride.fit", a, utc))
        assertEquals("ride.gpx", FitToGpx.fileName("{name}", "ride.FIT.gz", a, utc))
        assertEquals("2023-11-14_2213_cycling.gpx", FitToGpx.fileName("{date}_{time}_{sport}", "x.fit", a, utc))
        assertEquals("Garmin.gpx", FitToGpx.fileName("{device}", "x.fit", a, utc))
        assertEquals("a_b_c.gpx", FitToGpx.fileName("a/b:c", "x.fit", a, utc))
        assertEquals("x.gpx", FitToGpx.fileName("{sport}", "x.fit", null, utc))
        assertEquals("activity.gpx", FitToGpx.fileName("///", "", null, utc))
    }

    @Test
    fun uniqueNames() {
        assertEquals("a.gpx", FitToGpx.uniqueName("a.gpx", setOf("b.gpx")))
        assertEquals("a (2).gpx", FitToGpx.uniqueName("a.gpx", setOf("a.gpx")))
        assertEquals("a (3).gpx", FitToGpx.uniqueName("a.gpx", setOf("a.gpx", "a (2).gpx")))
    }

    @Test
    fun titlesAndNoGps() {
        val a = FitToGpx.read(fit)
        assertEquals("Night Ride", FitToGpx.defaultTitle(a, ZoneOffset.UTC)) // 22:13 UTC
        assertEquals("Afternoon Ride", FitToGpx.defaultTitle(a, ZoneOffset.ofHours(-7)))
        val indoor = a.copy(points = emptyList())
        assertFailsWith<FitToGpx.NoGpsDataException> { FitToGpx.convert(indoor, java.io.StringWriter()) }
    }
}
