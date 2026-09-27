/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.ui

import io.github.philtomlinson.fitgpx.data.Units
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs

/** Locale- and unit-aware formatting of activity values. */
object Format {
    private const val M_PER_MI = 1609.344
    private const val FT_PER_M = 3.28084

    private fun number(value: Double, decimals: Int): String =
        NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
        }.format(value)

    fun distance(meters: Double, units: Units): String = when (units) {
        Units.METRIC -> if (meters < 1000) "${number(meters, 0)} m" else "${number(meters / 1000, if (meters < 100_000) 2 else 1)} km"
        Units.IMPERIAL -> {
            val mi = meters / M_PER_MI
            if (mi < 0.1) "${number(meters * FT_PER_M, 0)} ft" else "${number(mi, if (mi < 100) 2 else 1)} mi"
        }
    }

    /** Short distance for axis labels / slider readouts. */
    fun distanceShort(meters: Double, units: Units): String = when (units) {
        Units.METRIC -> if (meters < 1000) "${number(meters, 0)} m" else "${number(meters / 1000, 1)} km"
        Units.IMPERIAL -> "${number(meters / M_PER_MI, 1)} mi"
    }

    fun elevation(meters: Double, units: Units): String = when (units) {
        Units.METRIC -> "${number(meters, 0)} m"
        Units.IMPERIAL -> "${number(meters * FT_PER_M, 0)} ft"
    }

    fun speed(mps: Double, units: Units): String = when (units) {
        Units.METRIC -> "${number(mps * 3.6, 1)} km/h"
        Units.IMPERIAL -> "${number(mps * 3600 / M_PER_MI, 1)} mph"
    }

    /** 1:02:03, 12:34 or 45 s. */
    fun duration(millis: Long): String {
        val total = abs(millis) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return when {
            h > 0 -> String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
            m > 0 -> String.format(Locale.ROOT, "%d:%02d", m, s)
            else -> "$s s"
        }
    }

    fun zone(utcOffsetSeconds: Int?): ZoneId = utcOffsetSeconds?.let { ZoneOffset.ofTotalSeconds(it) } ?: ZoneId.systemDefault()

    fun dateTime(epochMillis: Long, utcOffsetSeconds: Int?): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .format(Instant.ofEpochMilli(epochMillis).atZone(zone(utcOffsetSeconds)))

    fun clock(epochMillis: Long, utcOffsetSeconds: Int?): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
            .format(Instant.ofEpochMilli(epochMillis).atZone(zone(utcOffsetSeconds)))

    fun coordinate(value: Double): String = String.format(Locale.ROOT, "%.5f", value)

    /** "cycling" → "Cycling", "stand_up_paddleboarding" → "Stand up paddleboarding". */
    fun sport(sport: String?): String? = sport?.replace('_', ' ')?.replaceFirstChar { it.titlecase(Locale.getDefault()) }
}
