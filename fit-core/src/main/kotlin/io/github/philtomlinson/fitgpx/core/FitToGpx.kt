/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.core

import io.github.philtomlinson.fitgpx.core.fit.ActivityReader
import io.github.philtomlinson.fitgpx.core.gpx.GpxOptions
import io.github.philtomlinson.fitgpx.core.gpx.GpxWriter
import io.github.philtomlinson.fitgpx.core.model.Activity
import io.github.philtomlinson.fitgpx.core.track.EditSpec
import io.github.philtomlinson.fitgpx.core.track.ProcessedActivity
import io.github.philtomlinson.fitgpx.core.track.TrackProcessor
import java.io.Writer
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** High-level entry point: FIT bytes in, GPX out. */
object FitToGpx {

    class NoGpsDataException(val activity: Activity) :
        Exception(if (activity.recordCount > 0) "This activity has no GPS data (indoor or GPS off)" else "This file contains no activity records")

    fun read(bytes: ByteArray): Activity = ActivityReader().read(bytes)

    /**
     * Converts [activity] to GPX on [out].
     * @throws NoGpsDataException when nothing is left to export.
     */
    fun convert(activity: Activity, out: Writer, edit: EditSpec = EditSpec(), options: GpxOptions = GpxOptions()): ProcessedActivity {
        if (!activity.hasGps) throw NoGpsDataException(activity)
        val processed = TrackProcessor.process(activity, edit)
        GpxWriter(options).write(processed, out)
        return processed
    }

    const val DEFAULT_NAME_TEMPLATE = "{name}"

    /**
     * A human title for the activity, e.g. "Morning Cycling" — the file's own name (course
     * name) when it has one.
     */
    fun defaultTitle(activity: Activity, fallbackZone: ZoneId = ZoneId.systemDefault()): String {
        activity.name?.takeIf { it.isNotBlank() && activity.isCourse }?.let { return it }
        val sport = when (activity.sport) {
            "cycling" -> "Ride"
            "e_biking" -> "E-Bike Ride"
            "running" -> "Run"
            "walking" -> "Walk"
            "hiking" -> "Hike"
            "swimming" -> "Swim"
            "cross_country_skiing", "alpine_skiing", "snowboarding" -> "Ski"
            "rowing" -> "Row"
            "paddling", "kayaking", "canoeing", "stand_up_paddleboarding" -> "Paddle"
            null -> "Activity"
            else -> activity.sport.replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
        val start = activity.startTime ?: return sport
        val zone: ZoneId = activity.utcOffsetSeconds?.let { ZoneOffset.ofTotalSeconds(it) } ?: fallbackZone
        val hour = Instant.ofEpochMilli(start).atZone(zone).hour
        val part = when (hour) {
            in 5..11 -> "Morning"
            in 12..16 -> "Afternoon"
            in 17..20 -> "Evening"
            else -> "Night"
        }
        return "$part $sport"
    }

    /** Tokens supported in output file name templates. */
    val TEMPLATE_TOKENS = listOf("{name}", "{date}", "{time}", "{sport}", "{device}")

    /**
     * Builds an output file name from [template]. Tokens: `{name}` source file name without
     * extension, `{date}` yyyy-MM-dd and `{time}` HHmm (local time of the activity), `{sport}`,
     * `{device}`. The result is sanitised for every common file system and ends in `.gpx`.
     */
    fun fileName(template: String, sourceName: String, activity: Activity?, fallbackZone: ZoneId = ZoneId.systemDefault()): String {
        val base = sourceName.substringAfterLast('/').replace(Regex("""(\.fit)?(\.gz)?$""", RegexOption.IGNORE_CASE), "")
        val start = activity?.startTime
        val zone: ZoneId = activity?.utcOffsetSeconds?.let { ZoneOffset.ofTotalSeconds(it) } ?: fallbackZone
        val local = start?.let { Instant.ofEpochMilli(it).atZone(zone) }
        val raw = template
            .replace("{name}", base)
            .replace("{date}", local?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) ?: "")
            .replace("{time}", local?.format(DateTimeFormatter.ofPattern("HHmm")) ?: "")
            .replace("{sport}", activity?.sport?.replace('_', '-') ?: "")
            .replace("{device}", listOfNotNull(activity?.manufacturer, activity?.product).joinToString("-"))
        val clean = raw
            .replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_")
            .replace(Regex("""[_\- ]{2,}""")) { m -> m.value.first().toString() }
            .trim(' ', '.', '_', '-')
            .take(120)
            .ifEmpty { base.ifEmpty { "activity" } }
        return "$clean.gpx"
    }

    /** Makes [name] unique among [taken] by appending " (2)", " (3)", … before the extension. */
    fun uniqueName(name: String, taken: Set<String>): String {
        if (name !in taken) return name
        val stem = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var i = 2
        while (true) {
            val candidate = if (ext.isEmpty()) "$stem ($i)" else "$stem ($i).$ext"
            if (candidate !in taken) return candidate
            i++
        }
    }
}
