/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.core

import io.github.philtomlinson.fitgpx.core.FitTestEncoder.Companion.ENUM
import io.github.philtomlinson.fitgpx.core.FitTestEncoder.Companion.SINT32
import io.github.philtomlinson.fitgpx.core.FitTestEncoder.Companion.STRING
import io.github.philtomlinson.fitgpx.core.FitTestEncoder.Companion.UINT16
import io.github.philtomlinson.fitgpx.core.FitTestEncoder.Companion.UINT32
import io.github.philtomlinson.fitgpx.core.FitTestEncoder.Companion.f
import io.github.philtomlinson.fitgpx.core.FitTestEncoder.Companion.sc
import io.github.philtomlinson.fitgpx.core.FitTestEncoder.Companion.simpleActivity
import io.github.philtomlinson.fitgpx.core.FitTestEncoder.Companion.ts
import io.github.philtomlinson.fitgpx.core.fit.ActivityReader
import io.github.philtomlinson.fitgpx.core.model.Pause
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActivityReaderTest {

    private val t0 = 1_700_000_000L

    @Test
    fun timerEventsBecomePauses() {
        val e = simpleActivity(n = 5, start = t0)
        e.define(5, 21, listOf(f(253, 4, UINT32), f(0, 1, ENUM), f(1, 1, ENUM)))
        e.data(5, ts(t0 + 10), 0L, 4L) // timer stop_all
        e.data(5, ts(t0 + 70), 0L, 0L) // timer start
        e.data(5, ts(t0 + 80), 0L, 1L) // stop
        e.data(5, ts(t0 + 81), 0L, 1L) // duplicate stop is ignored
        e.data(5, ts(t0 + 95), 0L, 0L) // start
        val a = ActivityReader().read(e.build())
        assertEquals(
            listOf(Pause((t0 + 10) * 1000, (t0 + 70) * 1000), Pause((t0 + 80) * 1000, (t0 + 95) * 1000)),
            a.pauses,
        )
    }

    @Test
    fun courseFileYieldsNameAndCoursePoints() {
        val e = FitTestEncoder()
        e.define(0, 0, listOf(f(0, 1, ENUM), f(1, 2, UINT16)))
        e.data(0, 6L, 1L) // type = course
        e.define(1, 31, listOf(f(5, 16, STRING), f(4, 1, ENUM)))
        e.data(1, "Banff Loop", 2L)
        e.define(2, 20, listOf(f(253, 4, UINT32), f(0, 4, SINT32), f(1, 4, SINT32)))
        e.data(2, ts(t0), sc(51.17), sc(-115.57))
        e.data(2, ts(t0 + 1), sc(51.18), sc(-115.57))
        e.define(3, 32, listOf(f(1, 4, UINT32), f(2, 4, SINT32), f(3, 4, SINT32), f(5, 1, ENUM), f(6, 16, STRING)))
        e.data(3, ts(t0 + 1), sc(51.18), sc(-115.57), 1L, "Summit <&> view")
        val a = ActivityReader().read(e.build())
        assertTrue(a.isCourse)
        assertEquals("Banff Loop", a.name)
        assertEquals("cycling", a.sport)
        val cp = a.coursePoints.single()
        assertEquals("Summit <&> view", cp.name)
        assertEquals("summit", cp.type)
        assertEquals("Banff Loop", FitToGpx.defaultTitle(a))
    }

    @Test
    fun lapsAndUtcOffset() {
        val e = simpleActivity(n = 3, start = t0)
        e.define(6, 19, listOf(f(253, 4, UINT32), f(2, 4, UINT32), f(3, 4, SINT32), f(4, 4, SINT32), f(9, 4, UINT32)))
        e.data(6, ts(t0 + 2), ts(t0), sc(51.0), sc(-114.0), 2500L)
        e.define(7, 34, listOf(f(253, 4, UINT32), f(5, 4, UINT32)))
        e.data(7, ts(t0 + 2), ts(t0 + 2) - 6 * 3600) // UTC-6 (Mountain daylight time)
        val a = ActivityReader().read(e.build())
        assertEquals(1, a.laps.size)
        assertEquals(25.0, a.laps[0].distance!!, 1e-9)
        assertEquals(-6 * 3600, a.utcOffsetSeconds)
    }

    @Test
    fun implausibleTimeJumpsAreDropped() {
        val e = FitTestEncoder()
        e.define(0, 20, listOf(f(253, 4, UINT32), f(0, 4, SINT32), f(1, 4, SINT32)))
        e.data(0, ts(t0), sc(1.0), sc(1.0))
        e.data(0, ts(t0 + 1), sc(1.0001), sc(1.0))
        e.data(0, ts(t0 + 60L * 365 * 24 * 3600), sc(1.0002), sc(1.0)) // 60 years later
        e.data(0, ts(t0 + 2), sc(1.0003), sc(1.0))
        val a = ActivityReader().read(e.build())
        assertEquals(3, a.points.size)
        assertEquals(1, a.recordsWithoutPosition)
    }
}
