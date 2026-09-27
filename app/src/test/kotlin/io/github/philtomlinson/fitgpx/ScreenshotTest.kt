/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.philtomlinson.fitgpx.core.track.PrivacyZone
import io.github.philtomlinson.fitgpx.core.track.TrackProcessor
import io.github.philtomlinson.fitgpx.data.AppSettings
import io.github.philtomlinson.fitgpx.data.Progress
import io.github.philtomlinson.fitgpx.data.ThemeMode
import io.github.philtomlinson.fitgpx.ui.editor.EditorContent
import io.github.philtomlinson.fitgpx.ui.editor.EditorState
import io.github.philtomlinson.fitgpx.ui.editor.LoadedTrack
import io.github.philtomlinson.fitgpx.ui.editor.SelectionStats
import io.github.philtomlinson.fitgpx.ui.home.HomeContent
import io.github.philtomlinson.fitgpx.ui.settings.SettingsContent
import io.github.philtomlinson.fitgpx.ui.theme.FitGpxTheme
import io.github.philtomlinson.fitgpx.una.BluetoothReadiness
import io.github.philtomlinson.fitgpx.una.SyncState
import io.github.philtomlinson.fitgpx.una.UnaSyncContent
import io.github.philtomlinson.fitgpx.una.WatchDevice
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every main screen on the JVM. Run `./gradlew :app:recordRoborazziDebug` to (re)generate
 * the images in `app/src/test/screenshots`; CI uploads them so UI changes can be reviewed in PRs.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreenshotTest {

    private val settings = AppSettings(showMap = false, outputFolderUri = "content://x", outputFolderName = "GPX")

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) =
        captureRoboImage("src/test/screenshots/$name.png") {
            FitGpxTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { content() }
        }

    @Composable
    private fun Home(items: List<io.github.philtomlinson.fitgpx.data.QueueItem>, progress: Progress = Progress.Idle) = HomeContent(
        items = items, progress = progress, settings = settings, snackbar = SnackbarHostState(),
        onSelectFiles = {}, onSelectFolder = {}, onOpenItem = {}, onRemoveItem = {}, onClear = {},
        onClearConverted = {}, onConvert = {}, onCancel = {}, onOpenSettings = {}, onOpenAbout = {},
    )

    @Test
    fun homeEmpty() = shot("01_home_empty") { Home(emptyList()) }

    @Test
    fun homeList() = shot("02_home_list") { Home(DemoData.queue()) }

    @Test
    fun homeConverting() = shot("03_home_converting") { Home(DemoData.queue(), Progress.Converting(2, 4, "Evening Run")) }

    @Test
    fun homeListDark() = shot("04_home_list_dark", dark = true) { Home(DemoData.queue()) }

    private fun editorState(): Pair<EditorState, SelectionStats> {
        val activity = DemoData.ride()
        val s = settings.copy(privacyZones = listOf(PrivacyZone(51.0447, -114.0719, 200.0)))
        val state = EditorState(
            loading = false, title = "Morning Ride", track = LoadedTrack(activity),
            start = 300, end = 4300, hideStartMeters = 200, hideEndMeters = 0, settings = s,
        )
        val processed = TrackProcessor.process(activity, s.editSpec(null, "cycling").copy(trimStart = 300, trimEnd = 4300, hideStartMeters = 200.0))
        return state to SelectionStats(processed.stats, processed.removedPoints, processed.pointCount)
    }

    @Composable
    private fun Editor(state: EditorState, selection: SelectionStats) = EditorContent(
        state = state, selection = selection, onBack = {}, onReset = {}, onRangeChange = { _, _ -> },
        onNudgeStart = {}, onNudgeEnd = {}, onAutoTrim = {}, onHideStart = {}, onHideEnd = {}, onDone = {}, onExport = {},
    )

    @Test
    fun editor() {
        val (state, selection) = editorState()
        shot("05_editor") { Editor(state, selection) }
    }

    @Test
    fun editorDark() {
        val (state, selection) = editorState()
        shot("06_editor_dark", dark = true) { Editor(state, selection) }
    }

    @Test
    fun settingsScreen() = shot("07_settings") {
        SettingsContent(
            s = settings.copy(privacyZones = listOf(PrivacyZone(51.0, -114.0, 200.0, "Home"))),
            update = {}, onBack = {}, onPickFolder = {}, onClearFolder = {}, onOpenZones = {}, onOpenAbout = {},
        )
    }

    @Composable
    private fun Una(state: SyncState, readiness: BluetoothReadiness = BluetoothReadiness.READY) = UnaSyncContent(
        state = state,
        readiness = readiness,
        devices = listOf(WatchDevice("AA:BB", "UNA Watch 7F21", paired = true), WatchDevice("CC:DD", "UNA Watch 03B9", paired = false)),
        scanning = true,
        remembered = WatchDevice("AA:BB", "UNA Watch 7F21", paired = true),
        demoAvailable = false,
        onBack = {}, onAllowBluetooth = {}, onEnableBluetooth = {}, onSync = {}, onDemo = {},
        onCancel = {}, onAddAll = {}, onDone = {}, onAgain = {},
    )

    @Test
    fun unaPick() = shot("08_una_pick") { Una(SyncState.Idle) }

    @Test
    fun unaDownloading() = shot("09_una_downloading") { Una(SyncState.Downloading(2, 5, "activity_20260927T081502.fit", 18_000, 36_327)) }

    @Test
    fun unaDone() = shot("10_una_done") { Una(SyncState.Done(added = 3, alreadySynced = 12, failed = 0)) }

    @Test
    fun unaPermission() = shot("11_una_permission") { Una(SyncState.Idle, BluetoothReadiness.NEEDS_PERMISSION) }
}
