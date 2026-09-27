/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.core.gpx

import io.github.philtomlinson.fitgpx.core.model.TrackPoint
import io.github.philtomlinson.fitgpx.core.track.ProcessedActivity
import io.github.philtomlinson.fitgpx.core.track.Waypoint
import java.io.StringWriter
import java.io.Writer
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToLong

/** What to put in the GPX file. */
data class GpxOptions(
    val includeElevation: Boolean = true,
    val includeTime: Boolean = true,
    val includeHeartRate: Boolean = true,
    val includeCadence: Boolean = true,
    val includePower: Boolean = true,
    val includeTemperature: Boolean = true,
    /** Export course points (turns, climbs, water…) of course files as waypoints. */
    val includeCoursePoints: Boolean = true,
    /** Export the start of every lap as a waypoint. */
    val includeLaps: Boolean = false,
    /** Decimal places for latitude/longitude. 7 ≈ 1 cm, 6 ≈ 10 cm, 5 ≈ 1 m. */
    val coordinateDecimals: Int = 7,
    val creator: String = "FitGPX",
    val trackName: String? = null,
    val description: String? = null,
    val prettyPrint: Boolean = true,
) {
    val includesSensorData: Boolean get() = includeHeartRate || includeCadence || includePower || includeTemperature
}

/**
 * Streams GPX 1.1 that validates against the GPX 1.1 schema and the Garmin TrackPointExtension v1
 * and PowerExtension v1 schemas — the dialect accepted by Strava, Garmin Connect, Komoot,
 * Ride with GPS, OsmAnd, GoldenCheetah and virtually every other tool.
 *
 * Output is written directly without building a DOM, so memory use is constant regardless of
 * track length.
 */
class GpxWriter(private val options: GpxOptions = GpxOptions()) {

    fun write(activity: ProcessedActivity, out: Writer) {
        val nl = if (options.prettyPrint) "\n" else ""
        fun indent(n: Int) = if (options.prettyPrint) "  ".repeat(n) else ""

        val tpx = options.includeHeartRate || options.includeCadence || options.includeTemperature
        val px = options.includePower
        val source = activity.source

        out.write("""<?xml version="1.0" encoding="UTF-8"?>""")
        out.write(nl)
        out.write("""<gpx version="1.1" creator="${esc(options.creator)}"""")
        out.write(""" xmlns="http://www.topografix.com/GPX/1/1"""")
        out.write(""" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"""")
        if (tpx) out.write(""" xmlns:gpxtpx="$NS_TPX"""")
        if (px) out.write(""" xmlns:gpxpx="$NS_PX"""")
        out.write(""" xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd""")
        if (tpx) out.write(" $NS_TPX http://www.garmin.com/xmlschemas/TrackPointExtensionv1.xsd")
        if (px) out.write(" $NS_PX http://www.garmin.com/xmlschemas/PowerExtensionv1.xsd")
        out.write("\">")
        out.write(nl)

        // <metadata>
        val name = options.trackName ?: source.name
        val start = activity.tracks.asSequence().flatMap { it.segments }.flatten().firstOrNull { it.time != null }?.time
            ?: source.startTime
        val stats = activity.stats
        out.write(indent(1)); out.write("<metadata>"); out.write(nl)
        if (name != null) {
            out.write(indent(2)); out.write("<name>${esc(name)}</name>"); out.write(nl)
        }
        if (options.description != null) {
            out.write(indent(2)); out.write("<desc>${esc(options.description)}</desc>"); out.write(nl)
        }
        if (options.includeTime && start != null) {
            out.write(indent(2)); out.write("<time>${time(start)}</time>"); out.write(nl)
        }
        stats.bounds?.let { b ->
            out.write(indent(2))
            out.write("<bounds minlat=\"${coord(b.minLat)}\" minlon=\"${coord(b.minLon)}\" maxlat=\"${coord(b.maxLat)}\" maxlon=\"${coord(b.maxLon)}\"/>")
            out.write(nl)
        }
        out.write(indent(1)); out.write("</metadata>"); out.write(nl)

        // <wpt>
        val waypoints = buildList {
            if (options.includeCoursePoints) addAll(activity.coursePoints)
            if (options.includeLaps) addAll(activity.lapPoints)
        }
        for (w in waypoints) writeWaypoint(w, out, indent(1), indent(2), nl)

        // <trk>
        for (track in activity.tracks) {
            out.write(indent(1)); out.write("<trk>"); out.write(nl)
            if (name != null) {
                out.write(indent(2)); out.write("<name>${esc(name)}</name>"); out.write(nl)
            }
            track.sport?.let {
                out.write(indent(2)); out.write("<type>${esc(it)}</type>"); out.write(nl)
            }
            for (segment in track.segments) {
                out.write(indent(2)); out.write("<trkseg>"); out.write(nl)
                val sb = StringBuilder(256)
                for (p in segment) {
                    sb.setLength(0)
                    appendPoint(sb, p, indent(3), indent(4), nl, tpx, px)
                    out.write(sb.toString())
                }
                out.write(indent(2)); out.write("</trkseg>"); out.write(nl)
            }
            out.write(indent(1)); out.write("</trk>"); out.write(nl)
        }
        out.write("</gpx>")
        out.write(nl)
        out.flush()
    }

    fun writeToString(activity: ProcessedActivity): String = StringWriter().also { write(activity, it) }.toString()

    private fun writeWaypoint(w: Waypoint, out: Writer, i1: String, i2: String, nl: String) {
        out.write("$i1<wpt lat=\"${coord(w.latitude)}\" lon=\"${coord(w.longitude)}\">$nl")
        if (options.includeElevation && w.elevation != null) out.write("$i2<ele>${fixed(w.elevation, 1)}</ele>$nl")
        if (options.includeTime && w.time != null) out.write("$i2<time>${time(w.time)}</time>$nl")
        w.name?.let { out.write("$i2<name>${esc(it)}</name>$nl") }
        w.type?.let {
            out.write("$i2<sym>${esc(symbol(it))}</sym>$nl")
            out.write("$i2<type>${esc(it)}</type>$nl")
        }
        out.write("$i1</wpt>$nl")
    }

    private fun appendPoint(sb: StringBuilder, p: TrackPoint, i3: String, i4: String, nl: String, tpx: Boolean, px: Boolean) {
        sb.append(i3).append("<trkpt lat=\"").append(coord(p.latitude)).append("\" lon=\"").append(coord(p.longitude)).append("\">").append(nl)
        if (options.includeElevation && p.elevation != null) {
            sb.append(i4).append("<ele>").append(fixed(p.elevation, 1)).append("</ele>").append(nl)
        }
        if (options.includeTime && p.time != null) {
            sb.append(i4).append("<time>").append(time(p.time)).append("</time>").append(nl)
        }
        val hr = p.heartRate.takeIf { options.includeHeartRate }
        val cad = p.cadence.takeIf { options.includeCadence }
        val temp = p.temperature.takeIf { options.includeTemperature }
        val power = p.power.takeIf { options.includePower }
        val hasTpx = tpx && (hr != null || cad != null || temp != null)
        val hasPx = px && power != null
        if (hasTpx || hasPx) {
            val i5 = if (i4.isEmpty()) "" else "$i4  "
            val i6 = if (i4.isEmpty()) "" else "$i5  "
            sb.append(i4).append("<extensions>").append(nl)
            if (hasPx) {
                sb.append(i5).append("<gpxpx:PowerExtension><gpxpx:PowerInWatts>").append(power)
                    .append("</gpxpx:PowerInWatts></gpxpx:PowerExtension>").append(nl)
            }
            if (hasTpx) {
                sb.append(i5).append("<gpxtpx:TrackPointExtension>").append(nl)
                // Element order is fixed by the TrackPointExtension v1 schema: atemp, wtemp, depth, hr, cad.
                if (temp != null) sb.append(i6).append("<gpxtpx:atemp>").append(temp).append("</gpxtpx:atemp>").append(nl)
                if (hr != null) sb.append(i6).append("<gpxtpx:hr>").append(hr.coerceIn(0, 255)).append("</gpxtpx:hr>").append(nl)
                if (cad != null) sb.append(i6).append("<gpxtpx:cad>").append(cad.coerceIn(0, 254)).append("</gpxtpx:cad>").append(nl)
                sb.append(i5).append("</gpxtpx:TrackPointExtension>").append(nl)
            }
            sb.append(i4).append("</extensions>").append(nl)
        }
        sb.append(i3).append("</trkpt>").append(nl)
    }

    private fun coord(v: Double): String = fixed(v, options.coordinateDecimals.coerceIn(1, 9))

    companion object {
        const val NS_TPX = "http://www.garmin.com/xmlschemas/TrackPointExtension/v1"
        const val NS_PX = "http://www.garmin.com/xmlschemas/PowerExtension/v1"

        private val POW10 = LongArray(10).also { a -> a[0] = 1; for (i in 1 until a.size) a[i] = a[i - 1] * 10 }

        /** Locale-independent fixed-point formatting with trailing zeros removed. */
        internal fun fixed(v: Double, decimals: Int): String {
            val scaled = (abs(v) * POW10[decimals]).roundToLong()
            val intPart = scaled / POW10[decimals]
            var frac = scaled % POW10[decimals]
            val sb = StringBuilder(16)
            if (v < 0 && scaled != 0L) sb.append('-')
            sb.append(intPart)
            if (frac != 0L) {
                var digits = decimals
                while (frac % 10 == 0L) {
                    frac /= 10
                    digits--
                }
                val s = frac.toString()
                sb.append('.')
                repeat(digits - s.length) { sb.append('0') }
                sb.append(s)
            }
            return sb.toString()
        }

        internal fun time(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()

        internal fun esc(s: String): String {
            val sb = StringBuilder(s.length + 8)
            for (c in s) {
                when {
                    c == '&' -> sb.append("&amp;")
                    c == '<' -> sb.append("&lt;")
                    c == '>' -> sb.append("&gt;")
                    c == '"' -> sb.append("&quot;")
                    c == '\'' -> sb.append("&apos;")
                    // Characters not allowed in XML 1.0 are dropped.
                    c.code < 0x20 && c != '\t' && c != '\n' && c != '\r' -> {}
                    c == '￾' || c == '￿' -> {}
                    else -> sb.append(c)
                }
            }
            return sb.toString()
        }

        /** Maps a FIT course point type to a common GPX symbol name (Garmin / OsmAnd vocabulary). */
        internal fun symbol(type: String): String = when (type) {
            "summit" -> "Summit"
            "valley" -> "Valley"
            "water", "sports_drink" -> "Drinking Water"
            "food", "energy_gel", "store" -> "Restaurant"
            "danger", "alert", "sharp_curve", "steep_incline", "obstacle" -> "Danger Area"
            "first_aid" -> "First Aid"
            "left", "slight_left", "sharp_left", "left_fork" -> "Left"
            "right", "slight_right", "sharp_right", "right_fork" -> "Right"
            "straight", "middle_fork" -> "Straight"
            "u_turn" -> "U-Turn"
            "campsite", "shelter" -> "Campground"
            "toilet", "shower", "rest_area" -> "Restroom"
            "bridge" -> "Bridge"
            "tunnel" -> "Tunnel"
            "lap" -> "Flag, Blue"
            else -> "Flag"
        }
    }
}
