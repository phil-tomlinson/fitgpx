/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx

import android.content.Context
import io.github.philtomlinson.fitgpx.data.QueueRepository
import io.github.philtomlinson.fitgpx.data.SettingsRepository
import io.github.philtomlinson.fitgpx.una.UnaDevices
import io.github.philtomlinson.fitgpx.una.UnaSync

/** Manual dependency injection: the app is small enough not to need a DI framework. */
class AppContainer(context: Context) {
    val settings = SettingsRepository(context)
    val queue = QueueRepository(context, settings)
    val unaSync = UnaSync(context, queue)
    val unaDevices = UnaDevices(context)
}
