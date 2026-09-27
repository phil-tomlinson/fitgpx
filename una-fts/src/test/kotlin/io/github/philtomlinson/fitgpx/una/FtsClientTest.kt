/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.una

import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FtsClientTest {

    private fun data(n: Int, seed: Int = 1) = Random(seed).nextBytes(n)

    private fun client(watch: SimulatedUnaWatch, cancelled: () -> Boolean = { false }) =
        FtsClient(watch, watch.protocolVersion, timeoutMs = 300, burstIdleMs = 30, isCancelled = cancelled)

    @Test
    fun requestLayoutsMatchTheSpec() {
        val r = ByteBuffer.wrap(FtsProtocol.read("/a.fit", 16, 4096)).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x10, r.get(0).toInt())
        assertEquals(6, r.getShort(2).toInt()) // pathLength
        assertEquals(16, r.getInt(4)) // chunkOffset
        assertEquals(4096, r.getInt(8)) // chunkSize
        assertEquals("/a.fit", String(r.array(), 12, 6))
        val p = ByteBuffer.wrap(FtsProtocol.readPacing(200, 4096)).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x12, p.get(0).toInt()); assertEquals(1, p.get(1).toInt()); assertEquals(200, p.getInt(4))
        val l = FtsProtocol.listDir("/Apps")
        assertEquals(listOf(0x50, 0, 5, 0), l.take(4).map { it.toInt() })
        assertEquals(0x70, FtsProtocol.digest("/x")[0].toInt())
    }

    @Test
    fun windowedReadIsFastAndExact() {
        val watch = SimulatedUnaWatch(protocolVersion = 5)
        val bytes = data(100_000)
        watch.putFile("/Apps/Cycling/Activity/202609/a.fit", bytes)
        val got = client(watch).readFile("/Apps/Cycling/Activity/202609/a.fit")
        assertContentEquals(bytes, got)
        // One request per 4 KB window (+ the digest), not one per notification.
        assertTrue(watch.requests <= 100_000 / 4096 + 3, "requests=${watch.requests}")
    }

    @Test
    fun classicReadWorksOnVersion4Watch() {
        val watch = SimulatedUnaWatch(protocolVersion = 4, mtu = 185)
        val bytes = data(20_000, 2)
        watch.putFile("/f.fit", bytes)
        assertContentEquals(bytes, client(watch).readFile("/f.fit"))
    }

    @Test
    fun recoversFromLostNotifications() {
        for (version in listOf(4, 5)) {
            val watch = SimulatedUnaWatch(protocolVersion = version, dropEveryNth = 7)
            val bytes = data(30_000, version)
            watch.putFile("/f.fit", bytes)
            assertContentEquals(bytes, client(watch).readFile("/f.fit"), "v$version")
        }
    }

    @Test
    fun handlesShortBursts() {
        val watch = SimulatedUnaWatch(protocolVersion = 5, burstCap = 1000)
        val bytes = data(12_345, 3)
        watch.putFile("/f.fit", bytes)
        assertContentEquals(bytes, client(watch).readFile("/f.fit"))
    }

    @Test
    fun emptyAndMissingFiles() {
        val watch = SimulatedUnaWatch()
        watch.putFile("/empty.fit", ByteArray(0))
        assertEquals(0, client(watch).readFile("/empty.fit").size)
        assertFailsWith<FtsException.NoSuchFile> { client(watch).readFile("/nope.fit") }
        assertFailsWith<FtsException.NoSuchFile> { client(watch).listDir("/Nope") }
    }

    @Test
    fun corruptionIsDetectedByDigest() {
        val watch = SimulatedUnaWatch()
        val bytes = data(5000, 4)
        watch.putFile("/f.fit", bytes)
        // Flip one byte in the first transfer only: the client must re-read and succeed.
        var flipsLeft = 1
        val flaky = object : FtsTransport by watch {
            override fun receive(timeoutMs: Long): ByteArray? = watch.receive(timeoutMs)?.also {
                if (flipsLeft > 0 && it[0] == FtsProtocol.READ_DATA && it.size > 40) {
                    it[40] = (it[40] + 1).toByte(); flipsLeft--
                }
            }
        }
        assertContentEquals(bytes, FtsClient(flaky, 5, 300, 30, isCancelled = { false }).readFile("/f.fit"))

        val alwaysBad = object : FtsTransport by watch {
            override fun receive(timeoutMs: Long): ByteArray? = watch.receive(timeoutMs)?.also {
                if (it[0] == FtsProtocol.READ_DATA && it.size > 40) it[40] = (it[40] + 1).toByte()
            }
        }
        assertFailsWith<FtsException.Corrupt> { FtsClient(alwaysBad, 5, 300, 30, isCancelled = { false }).readFile("/f.fit") }
    }

    @Test
    fun cancellationStopsTransfers() {
        val watch = SimulatedUnaWatch()
        watch.putFile("/f.fit", data(50_000))
        var calls = 0
        assertFailsWith<FtsException.Cancelled> {
            client(watch) { ++calls > 5 }.readFile("/f.fit")
        }
    }

    @Test
    fun scannerFindsActivitiesAcrossApps() {
        val watch = SimulatedUnaWatch()
        watch.putFile("/Apps/Cycling/Activity/202609/activity_20260927T081502.fit", data(900))
        watch.putFile("/Apps/Cycling/Activity/202608/activity_20260830T170000.fit", data(800))
        watch.putFile("/Apps/Cycling/Activity/summary.json", data(50))
        watch.putFile("/Apps/Hiking/Activity/202609/activity_20260920T100000.fit", data(700))
        watch.putFile("/Apps/ClockfaceRetro/settings.json", data(10))
        watch.putFile("/Apps/Running/Activity/202609/activity_20260901T060000.FIT", ByteArray(0)) // still being written
        val found = UnaActivityScanner(client(watch)).scan()
        assertEquals(
            listOf("activity_20260927T081502.fit", "activity_20260920T100000.fit", "activity_20260830T170000.fit"),
            found.map { it.name },
        )
        assertEquals(listOf("Cycling", "Hiking", "Cycling"), found.map { it.app })
        assertEquals(900L, found.first().size)
    }

    /** Reproduces a real watch: it never answers a listing of a path that doesn't exist. */
    @Test
    fun scannerOnlyListsExistingDirectoriesAndSurvivesSilence() {
        val watch = SimulatedUnaWatch(silentForMissing = true)
        watch.putFile("/Apps/Alarm/alarms.json", data(40))
        watch.putFile("/Apps/Cycling/Activity/202609/activity_20260927T081502.fit", data(900))
        val lines = mutableListOf<String>()
        val c = FtsClient(watch, 5, timeoutMs = 300, burstIdleMs = 30, listTimeoutMs = 100, isCancelled = { false }, trace = { lines += it })
        val found = UnaActivityScanner(c).scan()
        assertEquals(listOf("activity_20260927T081502.fit"), found.map { it.name })
        assertTrue(lines.none { "no answer" in it }, lines.joinToString("\n"))
        assertTrue(lines.any { it.startsWith("LIST /Apps → Alarm/, Cycling/") }, lines.joinToString("\n"))
    }

    @Test
    fun scannerLooksOutsideAppsWhenNothingIsThere() {
        val watch = SimulatedUnaWatch()
        watch.putFile("/Apps/Alarm/alarms.json", data(40))
        watch.putFile("/Activities/2026-09-27-081502.fit", data(900))
        val found = UnaActivityScanner(client(watch)).scan()
        assertEquals(listOf("/Activities/2026-09-27-081502.fit"), found.map { it.path })
    }
}
