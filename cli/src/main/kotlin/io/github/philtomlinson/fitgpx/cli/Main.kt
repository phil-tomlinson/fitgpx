/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.cli

import io.github.philtomlinson.fitgpx.core.FitToGpx
import io.github.philtomlinson.fitgpx.core.gpx.GpxOptions
import io.github.philtomlinson.fitgpx.core.io.InputUnpacker
import io.github.philtomlinson.fitgpx.core.track.EditSpec
import io.github.philtomlinson.fitgpx.core.track.PrivacyZone
import java.io.File
import kotlin.system.exitProcess

private const val USAGE = """Usage: fitgpx [options] <input>...

Converts FIT activity files (.fit, .fit.gz, .zip exports, or folders of them) to GPX.

Options:
  -o, --output DIR        Output directory (default: next to each input)
  -n, --name TEMPLATE     Output name template (default: {name}). Tokens: {name} {date} {time} {sport} {device}
      --hide-start M      Drop the first M metres of every track
      --hide-end M        Drop the last M metres of every track
      --privacy LAT,LON,R Remove points within R metres of LAT,LON (repeatable)
      --simplify M        Simplify tracks with an M metre tolerance
      --split-pauses      New track segment at every pause
      --max-speed MPS     Drop GPS spikes faster than MPS metres/second
      --no-ele --no-time --no-hr --no-cad --no-power --no-temp
                          Leave the given data out of the GPX
      --laps              Add lap starts as waypoints
      --overwrite         Replace existing files instead of adding " (2)"
  -q, --quiet             Only print errors
  -h, --help              Show this help
"""

fun main(args: Array<String>) {
    if (args.isEmpty() || args.any { it == "-h" || it == "--help" }) {
        println(USAGE)
        exitProcess(if (args.isEmpty()) 2 else 0)
    }
    var edit = EditSpec()
    var gpx = GpxOptions(creator = "FitGPX CLI")
    var outDir: File? = null
    var template = FitToGpx.DEFAULT_NAME_TEMPLATE
    var quiet = false
    var overwrite = false
    val inputs = mutableListOf<File>()
    val zones = mutableListOf<PrivacyZone>()

    var i = 0
    fun value(): String = args.getOrNull(++i) ?: fail("Missing value for ${args[i - 1]}")
    fun number(): Double = value().toDoubleOrNull() ?: fail("Expected a number after ${args[i - 1]}")
    while (i < args.size) {
        when (val a = args[i]) {
            "-o", "--output" -> outDir = File(value())
            "-n", "--name" -> template = value()
            "--hide-start" -> edit = edit.copy(hideStartMeters = number())
            "--hide-end" -> edit = edit.copy(hideEndMeters = number())
            "--simplify" -> edit = edit.copy(simplifyToleranceMeters = number())
            "--max-speed" -> edit = edit.copy(maxSpeedMps = number())
            "--split-pauses" -> edit = edit.copy(splitAtPauses = true)
            "--privacy" -> {
                val parts = value().split(',').map { it.trim().toDoubleOrNull() }
                if (parts.size != 3 || parts.any { it == null }) fail("--privacy expects LAT,LON,RADIUS")
                zones += PrivacyZone(parts[0]!!, parts[1]!!, parts[2]!!)
            }
            "--no-ele" -> gpx = gpx.copy(includeElevation = false)
            "--no-time" -> gpx = gpx.copy(includeTime = false)
            "--no-hr" -> gpx = gpx.copy(includeHeartRate = false)
            "--no-cad" -> gpx = gpx.copy(includeCadence = false)
            "--no-power" -> gpx = gpx.copy(includePower = false)
            "--no-temp" -> gpx = gpx.copy(includeTemperature = false)
            "--laps" -> gpx = gpx.copy(includeLaps = true)
            "--overwrite" -> overwrite = true
            "-q", "--quiet" -> quiet = true
            else -> if (a.startsWith("-")) fail("Unknown option $a") else inputs += File(a)
        }
        i++
    }
    edit = edit.copy(privacyZones = zones)

    val files = inputs.flatMap { f ->
        when {
            f.isDirectory -> f.walkTopDown().filter { it.isFile && it.name.lowercase().let { n -> n.endsWith(".fit") || n.endsWith(".gz") || n.endsWith(".zip") } }.sorted().toList()
            f.isFile -> listOf(f)
            else -> fail("No such file: $f")
        }
    }
    outDir?.mkdirs()

    var converted = 0
    var skipped = 0
    var failed = 0
    val written = mutableSetOf<String>()
    for (file in files) {
        try {
            file.inputStream().use { input ->
                InputUnpacker.stream(file.name, input) { source ->
                    val activity = FitToGpx.read(source.bytes)
                    if (!activity.hasGps) {
                        skipped++
                        if (!quiet) println("skip  ${source.name}: no GPS data")
                        return@stream
                    }
                    val dir = outDir ?: file.absoluteFile.parentFile
                    // Never overwrite a file written in this run; overwrite older files only if asked.
                    val writtenHere = written.map { File(it) }.filter { it.parentFile == dir }.map { it.name }.toSet()
                    val taken = if (overwrite) writtenHere else writtenHere + (dir.list()?.toSet() ?: emptySet())
                    val name = FitToGpx.uniqueName(FitToGpx.fileName(template, source.name, activity), taken)
                    val out = File(dir, name)
                    val title = FitToGpx.defaultTitle(activity)
                    val result = out.bufferedWriter().use { w -> FitToGpx.convert(activity, w, edit, gpx.copy(trackName = title)) }
                    written += out.path
                    converted++
                    if (!quiet) {
                        val km = result.stats.distanceMeters / 1000
                        println("ok    ${source.name} -> ${out.path} (${result.pointCount} points, ${"%.2f".format(km)} km)")
                    }
                    activity.warnings.forEach { if (!quiet) println("      warning: ${it.message}") }
                }
            }
        } catch (e: Exception) {
            failed++
            System.err.println("error ${file.name}: ${e.message}")
        }
    }
    if (!quiet) println("\n$converted converted, $skipped without GPS, $failed failed")
    exitProcess(if (failed > 0) 1 else 0)
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    System.err.println("Run with --help for usage.")
    exitProcess(2)
}
