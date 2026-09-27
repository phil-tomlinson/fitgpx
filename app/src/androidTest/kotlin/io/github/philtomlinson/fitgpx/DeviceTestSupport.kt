/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

internal object DeviceTestSupport {
    val target get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val testContext get() = InstrumentationRegistry.getInstrumentation().context

    /** Copies a FIT file from the test APK's assets into the app's cache and returns a file URI. */
    fun asset(name: String): Uri {
        val f = File(target.cacheDir, "test-input/$name")
        f.parentFile!!.mkdirs()
        testContext.assets.open(name).use { input -> f.outputStream().use { input.copyTo(it) } }
        return Uri.fromFile(f)
    }

    /**
     * Saves a full-screen screenshot to /data/local/tmp/fitgpx-shots, which survives the app being
     * uninstalled after the test run; CI pulls it from there and publishes the images.
     */
    fun screenshot(name: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(cmd: String) = automation.executeShellCommand(cmd).close()
        shell("mkdir -p $SHOTS_DIR")
        shell("screencap -p $SHOTS_DIR/$name.png")
        Thread.sleep(500) // screencap runs asynchronously in the shell
    }

    private const val SHOTS_DIR = "/data/local/tmp/fitgpx-shots"
}
