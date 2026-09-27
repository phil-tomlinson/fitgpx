/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.philtomlinson.fitgpx.DeviceTestSupport.asset
import io.github.philtomlinson.fitgpx.DeviceTestSupport.target
import io.github.philtomlinson.fitgpx.data.Destination
import io.github.philtomlinson.fitgpx.data.ItemEdit
import io.github.philtomlinson.fitgpx.data.ItemStatus
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** End-to-end conversion on a real Android runtime: SAF-style URIs, FileProvider, ZIP output. */
@RunWith(AndroidJUnit4::class)
class ConversionDeviceTest {

    private val queue get() = (target.applicationContext as FitGpxApp).container.queue

    @Before
    fun setUp() = queue.clear()

    private fun trackPoints(uri: Uri): Int =
        target.contentResolver.openInputStream(uri)!!.bufferedReader().use { r -> Regex("<trkpt ").findAll(r.readText()).count() }

    @Test
    fun importConvertShareAndZip() = runBlocking {
        val added = queue.import(listOf(asset("ride.fit"), asset("multisport.fit"), asset("indoor.fit")))
        assertEquals(3, added)
        val items = queue.items.value
        assertEquals(ItemStatus.NoGps, items.single { it.source.name == "indoor.fit" }.status)
        assertTrue(items.filter { it.source.name != "indoor.fit" }.all { it.status == ItemStatus.Ready })

        val shared = queue.convert(Destination.Share)
        assertEquals(2, shared.converted)
        assertEquals(0, shared.failed)
        assertEquals(2, shared.shareUris.size)
        assertEquals(221, trackPoints(shared.shareUris[0]))
        assertTrue(queue.items.value.count { it.status is ItemStatus.Done } == 2)

        val zipFile = File(target.cacheDir, "out.zip").apply { delete() }
        val zipped = queue.convert(Destination.Zip(Uri.fromFile(zipFile)))
        assertEquals(2, zipped.converted)
        ZipFile(zipFile).use { z -> assertEquals(listOf("ride.gpx", "multisport.gpx"), z.entries().toList().map { it.name }) }
    }

    @Test
    fun trimIsApplied() = runBlocking {
        queue.import(listOf(asset("ride.fit")))
        val item = queue.items.value.single()
        queue.updateEdit(item.id, ItemEdit(trimStart = 10, trimEnd = 50), null)
        val result = queue.convert(Destination.Share)
        assertEquals(41, trackPoints(result.shareUris.single()))
    }
}
