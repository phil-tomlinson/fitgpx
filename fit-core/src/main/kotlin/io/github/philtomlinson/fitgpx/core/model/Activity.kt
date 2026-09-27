/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.core.model

import io.github.philtomlinson.fitgpx.core.fit.FitWarning

/** A single GPS fix with the sensor readings recorded alongside it. */
data class TrackPoint(
    /** Unix epoch milliseconds, or null when the file has no absolute time. */
    val time: Long?,
    val latitude: Double,
    val longitude: Double,
    /** Metres above mean sea level. */
    val elevation: Double? = null,
    val heartRate: Int? = null,
    val cadence: Int? = null,
    val power: Int? = null,
    /** Metres per second. */
    val speed: Double? = null,
    /** Degrees Celsius. */
    val temperature: Int? = null,
    /** Cumulative distance reported by the device, in metres. */
    val distance: Double? = null,
)

/** A session is one sport within an activity; multisport files have several. */
data class Session(
    val startTime: Long?,
    val endTime: Long?,
    val sport: String?,
    val subSport: Int?,
    val totalDistance: Double?,
    val totalTimerTime: Double?,
)

/** A named point on a course (turn, climb, water stop…). */
data class CoursePoint(
    val time: Long?,
    val latitude: Double,
    val longitude: Double,
    val name: String?,
    val type: String?,
)

/** A lap marker at the position where a lap started. */
data class LapMarker(
    val index: Int,
    val startTime: Long?,
    val latitude: Double,
    val longitude: Double,
    val distance: Double?,
    val elapsedSeconds: Double?,
)

/** A period where the device timer was stopped (auto-pause or manual pause). */
data class Pause(val start: Long, val end: Long)

/** Everything FitGPX extracts from a FIT file. */
data class Activity(
    val fileType: Int?,
    val manufacturer: String?,
    val product: String?,
    val timeCreated: Long?,
    /** Primary sport (snake_case FIT name), e.g. "cycling". */
    val sport: String?,
    /** A name stored in the file (course name or sport profile name), if any. */
    val name: String?,
    val points: List<TrackPoint>,
    val sessions: List<Session>,
    val laps: List<LapMarker>,
    val coursePoints: List<CoursePoint>,
    val pauses: List<Pause>,
    /** Offset from UTC in seconds at the time of recording, if the device recorded it. */
    val utcOffsetSeconds: Int?,
    /** Number of record messages that had no usable GPS position. */
    val recordsWithoutPosition: Int,
    val recordCount: Int,
    val warnings: List<FitWarning>,
) {
    val startTime: Long? get() = points.firstOrNull { it.time != null }?.time ?: sessions.firstOrNull()?.startTime ?: timeCreated
    val endTime: Long? get() = points.lastOrNull { it.time != null }?.time
    val hasGps: Boolean get() = points.isNotEmpty()
    val isCourse: Boolean get() = fileType == 6
    val hasHeartRate: Boolean get() = points.any { it.heartRate != null }
    val hasCadence: Boolean get() = points.any { it.cadence != null }
    val hasPower: Boolean get() = points.any { it.power != null }
    val hasElevation: Boolean get() = points.any { it.elevation != null }
    val hasTemperature: Boolean get() = points.any { it.temperature != null }
}
