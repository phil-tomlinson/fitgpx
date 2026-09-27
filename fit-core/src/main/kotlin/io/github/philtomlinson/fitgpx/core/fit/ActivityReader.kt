/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.core.fit

import io.github.philtomlinson.fitgpx.core.fit.FitProfile.Mesg
import io.github.philtomlinson.fitgpx.core.model.Activity
import io.github.philtomlinson.fitgpx.core.model.CoursePoint
import io.github.philtomlinson.fitgpx.core.model.LapMarker
import io.github.philtomlinson.fitgpx.core.model.Pause
import io.github.philtomlinson.fitgpx.core.model.Session
import io.github.philtomlinson.fitgpx.core.model.TrackPoint
import kotlin.math.abs

/** Builds an [Activity] (GPS track, sessions, laps, course points, pauses) from FIT bytes. */
class ActivityReader(private val decoder: FitDecoder = FitDecoder()) {

    fun read(data: ByteArray): Activity {
        val b = Builder()
        val report = decoder.decode(data) { b.accept(it) }
        return b.build(report.warnings)
    }

    private companion object {
        /** Consecutive records more than a week apart cannot belong to one recording. */
        const val MAX_TIME_JUMP_MS = 7L * 24 * 3600 * 1000
    }

    private class Builder {
        var fileType: Int? = null
        var manufacturer: Int? = null
        var productName: String? = null
        var timeCreatedFit: Long? = null
        var sportName: String? = null
        var sportEnum: Int? = null
        var sportSubSport: Int? = null
        var courseName: String? = null
        var courseSport: Int? = null
        var utcOffset: Int? = null
        var recordsWithoutPosition = 0
        var recordCount = 0
        val points = ArrayList<TrackPoint>(4096)
        val sessions = ArrayList<Session>()
        val laps = ArrayList<LapMarker>()
        val coursePoints = ArrayList<CoursePoint>()
        val pauses = ArrayList<Pause>()
        var timerStoppedAt: Long? = null
        var lastPointTime: Long? = null
        var implausiblePoints = 0

        fun time(fit: Long?): Long? {
            if (fit == null) return null
            if (fit >= FitProfile.MIN_ABSOLUTE_TIMESTAMP) return FitProfile.toEpochMillis(fit)
            // Relative timestamp (seconds since power-on). Anchor on the file creation time if known.
            val created = timeCreatedFit ?: return null
            return FitProfile.toEpochMillis(created + fit)
        }

        fun position(m: FitMessage, latField: Int, lonField: Int): Pair<Double, Double>? {
            val lat = m.long(latField) ?: return null
            val lon = m.long(lonField) ?: return null
            if (lat == 0L && lon == 0L) return null // "null island": a placeholder, never a real fix
            val latDeg = lat * FitProfile.SEMICIRCLE_TO_DEG
            val lonDeg = lon * FitProfile.SEMICIRCLE_TO_DEG
            if (latDeg !in -90.0..90.0 || lonDeg !in -180.0..180.0) return null
            return latDeg to lonDeg
        }

        fun accept(m: FitMessage) {
            when (m.globalNumber) {
                Mesg.FILE_ID -> {
                    if (fileType == null) {
                        fileType = m.int(FitProfile.FileId.TYPE)
                        manufacturer = m.int(FitProfile.FileId.MANUFACTURER)
                        productName = m.string(FitProfile.FileId.PRODUCT_NAME)
                        timeCreatedFit = m.long(FitProfile.FileId.TIME_CREATED)
                    }
                }
                Mesg.RECORD -> record(m)
                Mesg.SESSION -> {
                    val start = time(m.long(FitProfile.Session.START_TIME))
                    val elapsed = m.scaled(FitProfile.Session.TOTAL_ELAPSED_TIME, 1000.0)
                    val end = time(m.long(FitProfile.FIELD_TIMESTAMP))
                        ?: if (start != null && elapsed != null) start + (elapsed * 1000).toLong() else null
                    sessions += Session(
                        startTime = start,
                        endTime = end,
                        sport = m.int(FitProfile.Session.SPORT)?.let { FitProfile.SPORTS[it] },
                        subSport = m.int(FitProfile.Session.SUB_SPORT),
                        totalDistance = m.scaled(FitProfile.Session.TOTAL_DISTANCE, 100.0),
                        totalTimerTime = m.scaled(FitProfile.Session.TOTAL_TIMER_TIME, 1000.0),
                    )
                    if (sportName == null) sportName = m.string(FitProfile.Session.SPORT_PROFILE_NAME)
                }
                Mesg.LAP -> {
                    val pos = position(m, FitProfile.Lap.START_POSITION_LAT, FitProfile.Lap.START_POSITION_LONG)
                    if (pos != null) {
                        laps += LapMarker(
                            index = laps.size + 1,
                            startTime = time(m.long(FitProfile.Lap.START_TIME)),
                            latitude = pos.first,
                            longitude = pos.second,
                            distance = m.scaled(FitProfile.Lap.TOTAL_DISTANCE, 100.0),
                            elapsedSeconds = m.scaled(FitProfile.Lap.TOTAL_ELAPSED_TIME, 1000.0),
                        )
                    }
                }
                Mesg.EVENT -> {
                    if (m.int(FitProfile.Event.EVENT) == FitProfile.Event.EVENT_TIMER) {
                        val t = time(m.long(FitProfile.FIELD_TIMESTAMP)) ?: return
                        when (m.int(FitProfile.Event.EVENT_TYPE)) {
                            FitProfile.Event.TYPE_STOP, FitProfile.Event.TYPE_STOP_ALL,
                            FitProfile.Event.TYPE_STOP_DISABLE, FitProfile.Event.TYPE_STOP_DISABLE_ALL,
                            -> if (timerStoppedAt == null) timerStoppedAt = t
                            FitProfile.Event.TYPE_START -> {
                                val stopped = timerStoppedAt
                                if (stopped != null && t > stopped) pauses += Pause(stopped, t)
                                timerStoppedAt = null
                            }
                        }
                    }
                }
                Mesg.SPORT -> {
                    if (sportEnum == null) sportEnum = m.int(FitProfile.Sport.SPORT)
                    if (sportSubSport == null) sportSubSport = m.int(FitProfile.Sport.SUB_SPORT)
                    if (sportName == null) sportName = m.string(FitProfile.Sport.NAME)
                }
                Mesg.COURSE -> {
                    courseName = m.string(FitProfile.Course.NAME) ?: courseName
                    courseSport = m.int(FitProfile.Course.SPORT) ?: courseSport
                }
                Mesg.COURSE_POINT -> {
                    val pos = position(m, FitProfile.CoursePoint.POSITION_LAT, FitProfile.CoursePoint.POSITION_LONG)
                        ?: return
                    coursePoints += CoursePoint(
                        time = time(m.long(FitProfile.CoursePoint.TIMESTAMP)),
                        latitude = pos.first,
                        longitude = pos.second,
                        name = m.string(FitProfile.CoursePoint.NAME),
                        type = m.int(FitProfile.CoursePoint.TYPE)?.let { FitProfile.COURSE_POINT_TYPES[it] },
                    )
                }
                Mesg.ACTIVITY -> {
                    val ts = m.long(FitProfile.FIELD_TIMESTAMP)
                    val local = m.long(FitProfile.Activity.LOCAL_TIMESTAMP)
                    if (ts != null && local != null) {
                        val offset = local - ts
                        // Real offsets are within ±14h; anything else is a device bug.
                        if (offset in -14 * 3600L..14 * 3600L) utcOffset = offset.toInt()
                    }
                }
            }
        }

        fun record(m: FitMessage) {
            recordCount++
            val pos = position(m, FitProfile.Record.POSITION_LAT, FitProfile.Record.POSITION_LONG)
            if (pos == null) {
                recordsWithoutPosition++
                return
            }
            val elevation = m.scaled(FitProfile.Record.ENHANCED_ALTITUDE, 5.0, 500.0)
                ?: m.scaled(FitProfile.Record.ALTITUDE, 5.0, 500.0)
            val speed = m.scaled(FitProfile.Record.ENHANCED_SPEED, 1000.0)
                ?: m.scaled(FitProfile.Record.SPEED, 1000.0)
            val t = time(m.long(FitProfile.FIELD_TIMESTAMP))
            val prev = lastPointTime
            if (t != null && prev != null && abs(t - prev) > MAX_TIME_JUMP_MS) {
                // Garbage written by a crashing device (e.g. a timestamp decades away).
                implausiblePoints++
                return
            }
            if (t != null) lastPointTime = t
            points += TrackPoint(
                time = t,
                latitude = pos.first,
                longitude = pos.second,
                elevation = elevation?.takeIf { it in -1000.0..10000.0 },
                heartRate = m.int(FitProfile.Record.HEART_RATE)?.takeIf { it > 0 },
                cadence = m.int(FitProfile.Record.CADENCE),
                power = m.int(FitProfile.Record.POWER),
                speed = speed,
                temperature = m.int(FitProfile.Record.TEMPERATURE),
                distance = m.scaled(FitProfile.Record.DISTANCE, 100.0),
            )
        }

        fun build(warnings: List<FitWarning>): Activity {
            val sport = sessions.firstNotNullOfOrNull { it.sport }
                ?: sportEnum?.let { FitProfile.SPORTS[it] }
                ?: courseSport?.let { FitProfile.SPORTS[it] }
            return Activity(
                fileType = fileType,
                manufacturer = manufacturer?.let { FitProfile.MANUFACTURERS[it] },
                product = productName,
                timeCreated = time(timeCreatedFit),
                sport = if (sessions.map { it.sport }.distinct().size > 1) "multisport" else sport,
                name = courseName ?: sportName,
                points = points,
                sessions = sessions,
                laps = laps,
                coursePoints = coursePoints,
                pauses = pauses,
                utcOffsetSeconds = utcOffset,
                recordsWithoutPosition = recordsWithoutPosition + implausiblePoints,
                recordCount = recordCount,
                warnings = warnings,
                subSport = sessions.firstNotNullOfOrNull { it.subSport } ?: sportSubSport,
            )
        }
    }
}
