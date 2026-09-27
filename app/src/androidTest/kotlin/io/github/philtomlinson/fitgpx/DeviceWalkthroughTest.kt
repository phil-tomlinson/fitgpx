/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.philtomlinson.fitgpx.DeviceTestSupport.asset
import io.github.philtomlinson.fitgpx.DeviceTestSupport.screenshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real app through its main flow on an emulator (with live OpenStreetMap tiles) and
 * captures screenshots of each step.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DeviceWalkthroughTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainFlow() {
        (rule.activity.application as FitGpxApp).container.queue.clear()
        rule.waitUntilAtLeastOneExists(hasText("Select files"), 10_000)
        screenshot("01_empty")

        rule.activity.homeViewModel.import(listOf(asset("ride.fit"), asset("multisport.fit"), asset("indoor.fit")))
        rule.waitUntilAtLeastOneExists(hasText("Convert 2 activities"), 20_000)
        rule.waitForIdle()
        screenshot("02_list")

        rule.onNodeWithContentDescription("Settings").performClick()
        rule.waitUntilAtLeastOneExists(hasText("GPX content"), 10_000)
        screenshot("03_settings")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntilAtLeastOneExists(hasText("Convert 2 activities"), 10_000)

        rule.onAllNodesWithText("Ride", substring = true).onFirst().performClick()
        rule.waitUntilAtLeastOneExists(hasText("Trim idle"), 20_000)
        Thread.sleep(8_000) // let map tiles download
        rule.waitForIdle()
        screenshot("04_editor")

        rule.onNodeWithText("Trim idle").performClick()
        rule.waitForIdle()
        Thread.sleep(1_000)
        screenshot("05_editor_trimmed")

        rule.onNodeWithText("Save GPX").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Share…"), 10_000)
        Thread.sleep(800)
        screenshot("06_save_sheet")
    }
}
