/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.data

import io.github.philtomlinson.fitgpx.BuildConfig
import io.github.philtomlinson.fitgpx.core.FitToGpx
import io.github.philtomlinson.fitgpx.core.gpx.GpxOptions
import io.github.philtomlinson.fitgpx.core.track.EditSpec
import io.github.philtomlinson.fitgpx.core.track.PrivacyZone

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class Units { METRIC, IMPERIAL }

enum class ConflictPolicy { KEEP_BOTH, OVERWRITE }

/** Everything the user can configure. Defaults produce a faithful, full-fidelity GPX. */
data class AppSettings(
    val outputFolderUri: String? = null,
    val outputFolderName: String? = null,
    val nameTemplate: String = FitToGpx.DEFAULT_NAME_TEMPLATE,
    val conflictPolicy: ConflictPolicy = ConflictPolicy.KEEP_BOTH,

    val includeElevation: Boolean = true,
    val includeTime: Boolean = true,
    val includeHeartRate: Boolean = true,
    val includeCadence: Boolean = true,
    val includePower: Boolean = true,
    val includeTemperature: Boolean = true,
    val includeLaps: Boolean = false,
    val includeCoursePoints: Boolean = true,
    val coordinateDecimals: Int = 7,

    val splitAtPauses: Boolean = false,
    val splitSessions: Boolean = true,
    val removeSpikes: Boolean = true,
    val simplifyMeters: Int = 0,

    val privacyZones: List<PrivacyZone> = emptyList(),
    val hideStartMeters: Int = 0,
    val hideEndMeters: Int = 0,

    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val units: Units = Units.METRIC,
    val showMap: Boolean = true,
) {
    fun gpxOptions(trackName: String?): GpxOptions = GpxOptions(
        includeElevation = includeElevation,
        includeTime = includeTime,
        includeHeartRate = includeHeartRate,
        includeCadence = includeCadence,
        includePower = includePower,
        includeTemperature = includeTemperature,
        includeCoursePoints = includeCoursePoints,
        includeLaps = includeLaps,
        coordinateDecimals = coordinateDecimals,
        creator = "FitGPX ${BuildConfig.VERSION_NAME} - https://github.com/phil-tomlinson/fitgpx",
        trackName = trackName,
    )

    /** Combines the global settings with a per-activity [edit]. */
    fun editSpec(edit: ItemEdit?, sport: String?): EditSpec = EditSpec(
        trimStart = edit?.trimStart ?: 0,
        trimEnd = edit?.trimEnd,
        hideStartMeters = (edit?.hideStartMeters ?: hideStartMeters).toDouble(),
        hideEndMeters = (edit?.hideEndMeters ?: hideEndMeters).toDouble(),
        privacyZones = privacyZones,
        simplifyToleranceMeters = simplifyMeters.toDouble(),
        splitAtPauses = splitAtPauses,
        splitSessions = splitSessions,
        maxSpeedMps = if (removeSpikes && sport !in FAST_SPORTS) SPIKE_SPEED_MPS else null,
    )

    companion object {
        /** 250 km/h: faster than any human-powered or road sport, slower than a GPS glitch. */
        const val SPIKE_SPEED_MPS = 70.0
        private val FAST_SPORTS = setOf("flying", "sky_diving", "hang_gliding", "motor_sports", "jumpmaster")
    }
}
