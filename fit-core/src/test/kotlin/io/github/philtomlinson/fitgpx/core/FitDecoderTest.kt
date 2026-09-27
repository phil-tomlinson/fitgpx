/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.core

import io.github.philtomlinson.fitgpx.core.FitTestEncoder.Companion.RECORD_FIELDS
import io.github.philtomlinson.fitgpx.core.FitTestEncoder.Companion.SINT32
import io.github.philtomlinson.fitgpx.core.FitTestEncoder.Companion.UINT16
import io.github.philtomlinson.fitgpx.core.FitTestEncoder.Companion.UINT32
import io.github.philtomlinson.fitgpx.core.FitTestEncoder.Companion.UINT8
import io.github.philtomlinson.fitgpx.core.FitTestEncoder.Companion.alt
import io.github.philtomlinson.fitgpx.core.FitTestEncoder.Companion.f
import io.github.philtomlinson.fitgpx.core.FitTestEncoder.Companion.sc
import io.github.philtomlinson.fitgpx.core.FitTestEncoder.Companion.simpleActivity
import io.github.philtomlinson.fitgpx.core.FitTestEncoder.Companion.ts
import io.github.philtomlinson.fitgpx.core.fit.ActivityReader
import io.github.philtomlinson.fitgpx.core.fit.Crc16
import io.github.philtomlinson.fitgpx.core.fit.FitDecoder
import io.github.philtomlinson.fitgpx.core.fit.FitException
import io.github.philtomlinson.fitgpx.core.fit.FitMessage
import io.github.philtomlinson.fitgpx.core.fit.FitWarning
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FitDecoderTest {

    private fun decode(bytes: ByteArray, lenient: Boolean = true): Pair<List<FitMessage>, List<FitWarning>> {
        val msgs = mutableListOf<FitMessage>()
        val report = FitDecoder(FitDecoder.Options(lenient)).decode(bytes) { msgs += it }
        return msgs to report.warnings
    }

    @Test
    fun decodesScaledRecordValues() {
        val a = ActivityReader().read(simpleActivity(n = 3).build())
        assertEquals(3, a.points.size)
        val p = a.points[0]
        assertEquals(1_700_000_000_000L, p.time)
        assertEquals(51.0, p.latitude, 1e-7)
        assertEquals(-114.0, p.longitude, 1e-7)
        assertEquals(1000.0, p.elevation!!, 1e-9)
        assertEquals(120, p.heartRate)
        assertEquals(80, p.cadence)
        assertEquals(200, p.power)
        assertEquals(15, p.temperature)
        assertEquals("cycling", a.sport)
        assertEquals("Garmin", a.manufacturer)
        assertTrue(a.warnings.isEmpty())
    }

    @Test
    fun bigEndianDefinitionsDecodeIdentically() {
        fun build(be: Boolean) = FitTestEncoder(bigEndian = be).apply {
            define(0, 20, RECORD_FIELDS)
            data(0, ts(1_700_000_000), sc(-33.8688), sc(151.2093), alt(58.4), 150L, 90L, 321L, -3L)
        }.build()
        val le = ActivityReader().read(build(false)).points.single()
        val be = ActivityReader().read(build(true)).points.single()
        assertEquals(le, be)
        assertEquals(-33.8688, be.latitude, 1e-7)
        assertEquals(58.4, be.elevation!!, 1e-9)
        assertEquals(-3, be.temperature)
        assertEquals(321, be.power)
    }

    @Test
    fun compressedTimestampsIncludingRollover() {
        val start = 1_700_000_000L // FIT ts low 5 bits decide the rollover
        val fitStart = ts(start)
        val e = FitTestEncoder()
        e.define(0, 20, listOf(f(253, 4, UINT32), f(0, 4, SINT32), f(1, 4, SINT32)))
        e.data(0, fitStart, sc(10.0), sc(10.0))
        e.define(1, 20, listOf(f(0, 4, SINT32), f(1, 4, SINT32)))
        val low = (fitStart and 0x1F).toInt()
        // Same 32 s window, +3 s
        e.compressed(1, (low + 3) and 0x1F, sc(10.001), sc(10.0))
        // An offset smaller than the last low bits means the counter rolled over.
        e.compressed(1, (low + 3 + 30) and 0x1F, sc(10.002), sc(10.0))
        val times = ActivityReader().read(e.build()).points.map { it.time!! / 1000 }
        assertEquals(listOf(start, start + 3, start + 33), times)
    }

    @Test
    fun developerFieldsAreSkipped() {
        val e = FitTestEncoder()
        e.define(0, 20, listOf(f(253, 4, UINT32), f(0, 4, SINT32), f(1, 4, SINT32)), devFields = listOf(4, 1))
        e.data(0, ts(1_700_000_000), sc(1.0), sc(2.0))
        e.data(0, ts(1_700_000_001), sc(1.1), sc(2.0))
        val a = ActivityReader().read(e.build())
        assertEquals(2, a.points.size)
        assertEquals(1.1, a.points[1].latitude, 1e-7)
        assertTrue(a.warnings.isEmpty())
    }

    @Test
    fun invalidValuesBecomeNullAndPlaceholderFixesAreDropped() {
        val e = FitTestEncoder()
        e.define(0, 20, RECORD_FIELDS)
        e.data(0, ts(1_700_000_000), null, null, alt(1.0), 100L, 1L, 1L, 1L) // no fix
        e.data(0, ts(1_700_000_001), 0L, 0L, alt(1.0), 100L, 1L, 1L, 1L) // null island
        e.data(0, ts(1_700_000_002), sc(5.0), sc(5.0), null, null, null, null, null)
        val a = ActivityReader().read(e.build())
        assertEquals(1, a.points.size)
        assertEquals(2, a.recordsWithoutPosition)
        val p = a.points.single()
        assertNull(p.elevation); assertNull(p.heartRate); assertNull(p.cadence); assertNull(p.power); assertNull(p.temperature)
    }

    @Test
    fun twelveByteHeader() {
        val a = ActivityReader().read(simpleActivity(n = 4).build(headerSize = 12))
        assertEquals(4, a.points.size)
        assertTrue(a.warnings.isEmpty())
    }

    @Test
    fun chainedFilesAreConcatenated() {
        val bytes = simpleActivity(n = 5, start = 1_700_000_000).build() +
            simpleActivity(n = 7, start = 1_700_001_000).build()
        val a = ActivityReader().read(bytes)
        assertEquals(12, a.points.size)
        assertEquals(2, a.sessions.size)
    }

    @Test
    fun truncatedFileIsRecoveredLeniently() {
        val full = simpleActivity(n = 20).build()
        val cut = full.copyOf(full.size - 40)
        val a = ActivityReader().read(cut)
        assertTrue(a.points.size in 15..19, "recovered ${a.points.size}")
        assertTrue(a.warnings.any { it.type == FitWarning.Type.TRUNCATED })
        assertFailsWith<FitException.Corrupt> { decode(cut, lenient = false) }
    }

    @Test
    fun unsizedHeaderFromCrashedDeviceIsRecovered() {
        val bytes = simpleActivity(n = 8).build(declaredDataSize = 0, includeCrc = false)
        val a = ActivityReader().read(bytes)
        assertEquals(8, a.points.size)
        assertEquals(listOf(FitWarning.Type.UNKNOWN_DATA_SIZE), a.warnings.map { it.type })
    }

    @Test
    fun crcMismatchIsReportedNotFatal() {
        val bytes = simpleActivity(n = 3).build(corruptCrc = true)
        val (msgs, warnings) = decode(bytes)
        assertTrue(msgs.isNotEmpty())
        assertEquals(listOf(FitWarning.Type.FILE_CRC_MISMATCH), warnings.map { it.type })
        assertFailsWith<FitException.Corrupt> { decode(bytes, lenient = false) }
    }

    @Test
    fun validFileHasNoWarningsInStrictMode() {
        val (msgs, warnings) = decode(simpleActivity(n = 3).build(), lenient = false)
        assertEquals(5, msgs.size) // file_id + 3 records + session
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun rejectsNonFitData() {
        assertFailsWith<FitException.NotAFitFile> { decode("hello world, not a fit file".toByteArray()) }
        assertFailsWith<FitException.NotAFitFile> { decode(ByteArray(3)) }
    }

    @Test
    fun dataForUndefinedLocalMessageStopsGracefully() {
        val e = simpleActivity(n = 3)
        e.raw(0x05, 1, 2, 3) // data message for local 5, never defined
        val a = ActivityReader().read(e.build())
        assertEquals(3, a.points.size)
        assertTrue(a.warnings.any { it.type == FitWarning.Type.UNDEFINED_LOCAL_MESSAGE })
    }

    @Test
    fun misalignedFieldSizesAreIgnored() {
        val e = FitTestEncoder()
        // A 3-byte uint16 field cannot be decoded; the rest of the record must still be.
        e.define(0, 20, listOf(f(253, 4, UINT32), f(0, 4, SINT32), f(1, 4, SINT32), f(7, 3, UINT16), f(3, 1, UINT8)))
        e.data(0, ts(1_700_000_000), sc(1.0), sc(1.0), 0x010203L, 99L)
        val p = ActivityReader().read(e.build()).points.single()
        assertNull(p.power)
        assertEquals(99, p.heartRate)
    }

    @Test
    fun crcMatchesKnownVector() {
        // The CRC of a buffer followed by its own CRC (little endian) is zero.
        val data = "123456789".toByteArray()
        val crc = Crc16.compute(data, 0, data.size)
        val withCrc = data + byteArrayOf((crc and 0xFF).toByte(), (crc shr 8).toByte())
        assertEquals(0, Crc16.compute(withCrc, 0, withCrc.size))
        assertEquals(0xBB3D, crc) // CRC-16/ARC check value
    }
}
