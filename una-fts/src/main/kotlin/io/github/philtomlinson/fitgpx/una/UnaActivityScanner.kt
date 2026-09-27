/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.una

/** A FIT activity file stored on the watch. */
data class RemoteActivity(
    /** Absolute path on the watch, e.g. `/Apps/Cycling/Activity/202609/activity_20260927T081502.fit`. */
    val path: String,
    val size: Long,
    val modifiedNanos: Long,
    /** The watch app that recorded it (the directory under `/Apps`), e.g. "Cycling". */
    val app: String,
) {
    val name: String get() = path.substringAfterLast('/')
}

/**
 * Finds recorded activities on a UNA Watch.
 *
 * SDK activity apps store recordings as `/Apps/<App>/Activity/<YYYYMM>/activity_<timestamp>.fit`,
 * but firmware and third-party apps vary, so the scanner walks the tree and picks up `.fit` files
 * wherever they are. It only ever lists directories the watch itself reported. Some firmware
 * stays silent (rather than answering "not found") for paths that don't exist. The walk is
 * bounded in depth and number of directories, and a directory that doesn't answer is skipped
 * rather than failing the whole sync.
 */
class UnaActivityScanner(
    private val client: FtsClient,
    private val maxDepth: Int = 5,
    private val maxDirectories: Int = 400,
    private val log: (String) -> Unit = {},
) {
    private var visited = 0
    private var consecutiveTimeouts = 0

    fun scan(onDirectory: (String) -> Unit = {}): List<RemoteActivity> {
        visited = 0
        consecutiveTimeouts = 0
        val found = mutableListOf<RemoteActivity>()
        walk("/Apps", 0, found, onDirectory)
        if (found.isEmpty()) {
            // Nothing in the usual place: look at the rest of the volume too.
            log("No .fit files under /Apps; checking the rest of the watch")
            val root = list("/") ?: emptyList()
            for (e in root) if (e.isDirectory && !e.name.equals("Apps", ignoreCase = true)) walk("/${e.name}", 1, found, onDirectory)
            for (e in root) if (!e.isDirectory && isFit(e)) found += RemoteActivity("/${e.name}", e.size, e.modifiedNanos, "")
        }
        log("Found ${found.size} .fit file(s) in $visited folder(s)")
        return found.sortedByDescending { it.name }
    }

    private fun isFit(e: FtsEntry) = e.name.endsWith(".fit", ignoreCase = true) && e.size > 0

    private fun list(dir: String): List<FtsEntry>? {
        if (visited >= maxDirectories) return null
        visited++
        return try {
            client.listDir(dir).also { consecutiveTimeouts = 0 }
        } catch (_: FtsException.Timeout) {
            // Several silent directories in a row means the link is gone, not just a missing path.
            if (++consecutiveTimeouts >= 3) throw FtsException.Timeout("listing $dir")
            null
        } catch (e: FtsException.Cancelled) {
            throw e
        } catch (_: FtsException) {
            null
        }
    }

    private fun walk(dir: String, depth: Int, out: MutableList<RemoteActivity>, onDirectory: (String) -> Unit) {
        onDirectory(appOf(dir) ?: dir)
        val entries = list(dir) ?: return
        for (e in entries) {
            val path = if (dir == "/") "/${e.name}" else "$dir/${e.name}"
            if (e.isDirectory) {
                if (depth < maxDepth) walk(path, depth + 1, out, onDirectory)
            } else if (isFit(e)) {
                out += RemoteActivity(path, e.size, e.modifiedNanos, appOf(path) ?: "")
            }
        }
    }

    /** "/Apps/Cycling/Activity/…" → "Cycling". */
    private fun appOf(path: String): String? {
        val parts = path.trim('/').split('/')
        return if (parts.size >= 2 && parts[0].equals("Apps", ignoreCase = true)) parts[1] else null
    }
}
