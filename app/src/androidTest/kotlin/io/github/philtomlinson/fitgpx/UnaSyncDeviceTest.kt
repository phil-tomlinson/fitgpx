/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.philtomlinson.fitgpx.DeviceTestSupport.screenshot
import io.github.philtomlinson.fitgpx.data.ItemStatus
import io.github.philtomlinson.fitgpx.una.DemoWatchConnector
import io.github.philtomlinson.fitgpx.una.SyncState
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Syncs from the simulated UNA Watch (same protocol code path as a real one, minus the radio). */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class UnaSyncDeviceTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val container get() = (rule.activity.application as FitGpxApp).container

    @Test
    fun syncCopiesOnlyNewActivities() = runBlocking {
        container.queue.clear()
        container.unaSync.clearSynced()
        val connector = DemoWatchConnector(rule.activity)
        container.unaSync.sync(connector)
        assertEquals(SyncState.Done(added = 3, alreadySynced = 0, failed = 0), container.unaSync.state.value)
        val items = container.queue.items.value
        assertEquals(3, items.size)
        assertTrue(items.all { it.status == ItemStatus.Ready })
        assertTrue(items.any { it.summary?.device == "Una" && it.summary?.title?.endsWith("Mountain Bike Ride") == true })

        container.unaSync.sync(connector)
        assertEquals(SyncState.Done(added = 0, alreadySynced = 3, failed = 0), container.unaSync.state.value)
        assertEquals(3, container.queue.items.value.size)
    }

    @Test
    fun syncScreenWalkthrough() {
        container.queue.clear()
        container.unaSync.clearSynced()
        rule.waitUntilAtLeastOneExists(hasText("Sync from a UNA Watch"), 10_000)
        rule.onNodeWithText("Sync from a UNA Watch").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Try with a simulated watch"), 10_000)
        rule.waitForIdle()
        screenshot("07_una_sync")
        rule.onNodeWithText("Try with a simulated watch").performClick()
        rule.waitUntilAtLeastOneExists(hasText("View activities"), 30_000)
        rule.waitForIdle()
        screenshot("08_una_done")
        rule.onNodeWithText("View activities").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Convert 3 activities"), 10_000)
        rule.waitForIdle()
        screenshot("09_una_list")
    }
}
