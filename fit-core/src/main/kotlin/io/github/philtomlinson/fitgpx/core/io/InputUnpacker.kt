/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.core.io

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/** One FIT payload found in an input, with a display name and its position in the input. */
class FitSource(val name: String, val bytes: ByteArray, val index: Int)

/**
 * Turns whatever the user picked into FIT payloads:
 *  - a plain `.fit` file
 *  - a gzip-compressed `.fit.gz` (the format of a Strava bulk export)
 *  - a `.zip` archive holding any mix of the above (Garmin Connect "Export Original",
 *    Strava bulk export, or a zip of a whole folder), including nested archives
 *
 * Detection is by content, not file extension. Archives are **streamed**: only one FIT payload
 * is held in memory at a time, so multi-gigabyte bulk exports work on a phone. Sizes are
 * bounded to defend against decompression bombs, see [Limits].
 */
object InputUnpacker {

    data class Limits(
        /** Largest single FIT payload accepted (after decompression). */
        val maxFileBytes: Long = 64L * 1024 * 1024,
        /** Largest total decompressed size of one input. */
        val maxTotalBytes: Long = 8L * 1024 * 1024 * 1024,
        val maxEntries: Int = 100_000,
        val maxDepth: Int = 3,
    )

    class UnsupportedInputException(message: String) : IOException(message)

    enum class Kind { FIT, GZIP, ZIP, UNKNOWN }

    fun detect(head: ByteArray): Kind = when {
        head.size >= 12 && head[8] == '.'.code.toByte() && head[9] == 'F'.code.toByte() &&
            head[10] == 'I'.code.toByte() && head[11] == 'T'.code.toByte() -> Kind.FIT
        head.size >= 2 && head[0] == 0x1F.toByte() && head[1] == 0x8B.toByte() -> Kind.GZIP
        head.size >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() &&
            head[2] == 3.toByte() && head[3] == 4.toByte() -> Kind.ZIP
        else -> Kind.UNKNOWN
    }

    /**
     * Streams every FIT payload in [input] to [visitor], in order. [FitSource.index] numbers the
     * payloads from 0 so a specific one can be found again later with [find].
     *
     * @throws UnsupportedInputException if the input is not FIT, gzip or zip, or exceeds [limits].
     */
    fun stream(name: String, input: InputStream, limits: Limits = Limits(), visitor: (FitSource) -> Unit) {
        val state = State(limits, visitor)
        visit(name, BufferedInputStream(input, BUFFER), 0, state, topLevel = true)
    }

    /** Collects every FIT payload. Prefer [stream] for archives that may be large. */
    fun unpack(name: String, input: InputStream, limits: Limits = Limits()): List<FitSource> {
        val out = mutableListOf<FitSource>()
        stream(name, input, limits) { out += it }
        return out
    }

    fun unpack(name: String, bytes: ByteArray, limits: Limits = Limits()): List<FitSource> =
        unpack(name, ByteArrayInputStream(bytes), limits)

    /** Returns the payload with the given [index], reading no further than necessary. */
    fun find(name: String, input: InputStream, index: Int, limits: Limits = Limits()): FitSource? {
        var found: FitSource? = null
        try {
            stream(name, input, limits) {
                if (it.index == index) {
                    found = it
                    throw Found()
                }
            }
        } catch (_: Found) {
            // Early exit.
        }
        return found
    }

    /** Control-flow signal for [find]; no stack trace is captured. */
    private class Found : RuntimeException(null, null, false, false)

    private class State(val limits: Limits, val visitor: (FitSource) -> Unit) {
        var total = 0L
        var entries = 0
        var index = 0
        fun count(n: Long) {
            total += n
            if (total > limits.maxTotalBytes) {
                throw UnsupportedInputException("Input expands beyond ${limits.maxTotalBytes / (1024 * 1024)} MB")
            }
        }
    }

    private fun visit(name: String, input: BufferedInputStream, depth: Int, state: State, topLevel: Boolean) {
        input.mark(16)
        val head = ByteArray(12)
        var n = 0
        while (n < head.size) {
            val r = input.read(head, n, head.size - n)
            if (r < 0) break
            n += r
        }
        input.reset()
        when (detect(head.copyOf(n))) {
            Kind.FIT -> {
                val bytes = readBounded(input, state)
                state.visitor(FitSource(name, bytes, state.index++))
            }
            Kind.GZIP -> {
                if (depth >= state.limits.maxDepth) return
                val inner = BufferedInputStream(GZIPInputStream(NonClosing(input), BUFFER), BUFFER)
                visit(stripSuffix(name, ".gz"), inner, depth + 1, state, topLevel)
            }
            Kind.ZIP -> {
                if (depth >= state.limits.maxDepth) return
                val zip = ZipInputStream(NonClosing(input))
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    if (++state.entries > state.limits.maxEntries) throw UnsupportedInputException("Archive has too many entries")
                    val entryName = entry.name.substringAfterLast('/')
                    if (entryName.startsWith(".") || entry.name.startsWith("__MACOSX/")) continue
                    val lower = entryName.lowercase()
                    // Only open entries that can plausibly hold FIT data; skip photos, GPX, CSV…
                    if (!(lower.endsWith(".fit") || lower.endsWith(".gz") || lower.endsWith(".zip"))) continue
                    visit(entryName, BufferedInputStream(NonClosing(zip), BUFFER), depth + 1, state, topLevel = false)
                }
            }
            Kind.UNKNOWN -> if (topLevel) throw UnsupportedInputException("Not a FIT, ZIP or GZIP file")
        }
    }

    private fun stripSuffix(name: String, suffix: String) =
        if (name.endsWith(suffix, ignoreCase = true)) name.dropLast(suffix.length) else name

    private fun readBounded(input: InputStream, state: State): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(BUFFER)
        var total = 0L
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            total += n
            if (total > state.limits.maxFileBytes) {
                throw UnsupportedInputException("A file is larger than ${state.limits.maxFileBytes / (1024 * 1024)} MB")
            }
            state.count(n.toLong())
            buffer.write(chunk, 0, n)
        }
        return buffer.toByteArray()
    }

    /** Stops nested readers from closing the zip stream between entries. */
    private class NonClosing(input: InputStream) : FilterInputStream(input) {
        override fun close() = Unit
    }

    private const val BUFFER = 64 * 1024
}
