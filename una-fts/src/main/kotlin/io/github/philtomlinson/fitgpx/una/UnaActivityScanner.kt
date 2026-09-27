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
 * UNA activity apps (Cycling, Running, Hiking, Workout…) and third-party apps built on the SDK's
 * `ActivityWriter` store recordings as `/Apps/<App>/Activity/<YYYYMM>/activity_<timestamp>.fit`.
 * The scan lists `/Apps`, then each app's `Activity` directory and its month folders, so it
 * costs a handful of round trips rather than walking the whole file system.
 */
class UnaActivityScanner(private val client: FtsClient) {

    fun scan(root: String = "/Apps", onApp: (String) -> Unit = {}): List<RemoteActivity> {
        val found = mutableListOf<RemoteActivity>()
        val apps = client.listDir(root).filter { it.isDirectory }
        for (app in apps) {
            onApp(app.name)
            val activityDir = "$root/${app.name}/Activity"
            val entries = try {
                client.listDir(activityDir)
            } catch (_: FtsException.NoSuchFile) {
                continue // not an activity app (a watch face, a glance…)
            } catch (e: FtsException.Status) {
                continue
            }
            collect(activityDir, entries, app.name, found, depth = 0)
        }
        return found.sortedByDescending { it.name }
    }

    private fun collect(dir: String, entries: List<FtsEntry>, app: String, out: MutableList<RemoteActivity>, depth: Int) {
        for (e in entries) {
            val path = "$dir/${e.name}"
            if (e.isDirectory) {
                if (depth < 2) {
                    val sub = try {
                        client.listDir(path)
                    } catch (_: FtsException.NoSuchFile) {
                        continue
                    }
                    collect(path, sub, app, out, depth + 1)
                }
            } else if (e.name.endsWith(".fit", ignoreCase = true) && e.size > 0) {
                out += RemoteActivity(path, e.size, e.modifiedNanos, app)
            }
        }
    }
}
