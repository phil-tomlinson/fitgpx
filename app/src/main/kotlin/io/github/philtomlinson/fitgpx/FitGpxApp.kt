/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx

import android.app.Application
import org.osmdroid.config.Configuration
import java.io.File

class FitGpxApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Configuration.getInstance().apply {
            // OpenStreetMap's tile policy requires an identifying user agent.
            userAgentValue = "FitGPX/${BuildConfig.VERSION_NAME} (+https://github.com/phil-tomlinson/fitgpx)"
            // Keep the tile cache in app-private storage: no storage permission needed.
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
            tileFileSystemCacheMaxBytes = 100L * 1024 * 1024
            tileFileSystemCacheTrimBytes = 80L * 1024 * 1024
        }
    }
}
