/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx

import android.net.Uri
import io.github.philtomlinson.fitgpx.core.model.Activity
import io.github.philtomlinson.fitgpx.core.model.Session
import io.github.philtomlinson.fitgpx.core.model.TrackPoint
import io.github.philtomlinson.fitgpx.data.ActivitySummary
import io.github.philtomlinson.fitgpx.data.ItemEdit
import io.github.philtomlinson.fitgpx.data.ItemStatus
import io.github.philtomlinson.fitgpx.data.QueueItem
import io.github.philtomlinson.fitgpx.data.SourceRef
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Synthetic but realistic activities for UI tests and screenshots. */
object DemoData {

    /** A ~40 km loop with rolling hills, heart rate, cadence and power. */
    fun ride(
        start: Long = 1_758_960_902_000L,
        n: Int = 4800,
        lat0: Double = 51.0447,
        lon0: Double = -114.0719,
        sport: String = "cycling",
        seed: Double = 0.0,
    ): Activity {
        val pts = List(n) { i ->
            val t = i.toDouble() / n
            val a = 2 * PI * t
            val lat = lat0 + 0.055 * sin(a + seed) + 0.012 * sin(5 * a)
            val lon = lon0 + 0.09 * cos(a + seed) - 0.09 + 0.018 * cos(3 * a + seed)
            TrackPoint(
                time = start + i * 1000L,
                latitude = lat,
                longitude = lon,
                elevation = 1050 + 60 * sin(3 * a) + 25 * sin(11 * a + 1),
                heartRate = (138 + 18 * sin(3 * a + 0.4) + 6 * sin(17 * a)).toInt(),
                cadence = (86 + 6 * sin(9 * a)).toInt(),
                power = (205 + 70 * sin(3 * a + 0.3)).toInt(),
                speed = 8.4 + 2.5 * cos(3 * a),
                temperature = 17,
            )
        }
        return Activity(
            fileType = 4, manufacturer = "Garmin", product = "Edge 540", timeCreated = start,
            sport = sport, name = null, points = pts,
            sessions = listOf(Session(start, start + n * 1000L, sport, null, null, null)),
            laps = emptyList(), coursePoints = emptyList(), pauses = emptyList(),
            utcOffsetSeconds = -6 * 3600, recordsWithoutPosition = 0, recordCount = n, warnings = emptyList(),
        )
    }

    fun indoor(): Activity = ride().copy(points = emptyList(), product = "Tacx NEO", recordsWithoutPosition = 3600)

    private fun item(id: Long, name: String, activity: Activity, status: ItemStatus = ItemStatus.Ready, edit: ItemEdit? = null) = QueueItem(
        id = id,
        source = SourceRef(Uri.parse("content://demo/$id"), 0, name, name),
        status = status,
        summary = ActivitySummary.of(activity),
        edit = edit,
    )

    fun queue(): List<QueueItem> = listOf(
        item(1, "2025-09-27-08-15-02.fit", ride(), ItemStatus.Done("2025-09-27-08-15-02.gpx")),
        item(2, "Evening_Run.fit", ride(start = 1_758_999_000_000L, n = 2400, lat0 = 49.2827, lon0 = -123.1207, sport = "running", seed = 1.3), edit = ItemEdit(trimStart = 40)),
        item(3, "Hike_Sulphur.fit", ride(start = 1_759_060_000_000L, n = 3600, lat0 = 51.1784, lon0 = -115.5708, sport = "hiking", seed = 2.1).let { a ->
            a.copy(warnings = listOf(io.github.philtomlinson.fitgpx.core.fit.FitWarning(io.github.philtomlinson.fitgpx.core.fit.FitWarning.Type.TRUNCATED, 0, "File is truncated")))
        }),
        item(4, "Zwift_Watopia.fit", indoor(), ItemStatus.NoGps),
        item(5, "Paddle.fit", ride(start = 1_759_100_000_000L, n = 1800, lat0 = 50.9, lon0 = -114.5, sport = "kayaking", seed = 0.7)),
    )
}
