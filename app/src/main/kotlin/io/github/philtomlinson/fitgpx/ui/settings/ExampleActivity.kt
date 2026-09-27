/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.ui.settings

import io.github.philtomlinson.fitgpx.core.model.Activity
import io.github.philtomlinson.fitgpx.core.model.TrackPoint

/** A made-up ride used to preview file name templates. */
internal val ExampleActivity = Activity(
    fileType = 4,
    manufacturer = "Garmin",
    product = "Edge 540",
    timeCreated = null,
    sport = "cycling",
    name = null,
    points = listOf(TrackPoint(time = 1_758_960_902_000L, latitude = 51.0447, longitude = -114.0719)),
    sessions = emptyList(),
    laps = emptyList(),
    coursePoints = emptyList(),
    pauses = emptyList(),
    utcOffsetSeconds = 0,
    recordsWithoutPosition = 0,
    recordCount = 1,
    warnings = emptyList(),
)
