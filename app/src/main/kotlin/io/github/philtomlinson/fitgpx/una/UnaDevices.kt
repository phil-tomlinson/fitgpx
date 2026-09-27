/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.una

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** A watch the user can pick. */
data class WatchDevice(val address: String, val name: String, val paired: Boolean)

/** Bluetooth discovery of UNA Watches. Permissions are checked by the caller. */
@SuppressLint("MissingPermission")
class UnaDevices(private val context: Context) {
    private val adapter: BluetoothAdapter? get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    val isSupported: Boolean get() = adapter != null
    val isEnabled: Boolean get() = adapter?.isEnabled == true

    private val fts = ParcelUuid.fromString(FtsProtocol.SERVICE_UUID)

    private fun looksLikeUna(name: String?): Boolean = name != null && name.contains("una", ignoreCase = true)

    /** Paired watches (paired in system settings, the UNA app, or FitGPX). */
    fun paired(remembered: String?): List<WatchDevice> = runCatching {
        adapter?.bondedDevices.orEmpty()
            .filter { it.type != BluetoothDevice.DEVICE_TYPE_CLASSIC && (looksLikeUna(it.name) || it.address == remembered) }
            .map { WatchDevice(it.address, it.name ?: it.address, paired = true) }
    }.getOrDefault(emptyList())

    /** Nearby watches: devices advertising the File Transfer Service or named like a UNA Watch. */
    fun scan(): Flow<WatchDevice> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            close()
            return@callbackFlow
        }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull()
                val advertisesFts = result.scanRecord?.serviceUuids?.contains(fts) == true
                if (advertisesFts || looksLikeUna(name)) {
                    trySend(WatchDevice(result.device.address, name ?: "UNA Watch", result.device.bondState == BluetoothDevice.BOND_BONDED))
                }
            }

            override fun onScanFailed(errorCode: Int) {
                close()
            }
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(null, settings, callback)
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }

    fun connector(address: String): WatchConnector = WatchConnector { onStage ->
        val device = adapter?.getRemoteDevice(address) ?: throw java.io.IOException("Bluetooth is not available")
        val c = BleFtsTransport.connect(context, device, onStage)
        WatchLink(c.transport, c.protocolVersion) { c.transport.close() }
    }
}

/**
 * A simulated watch pre-loaded with sample recordings, so the sync flow can be demonstrated and
 * tested without hardware (debug builds only).
 */
class DemoWatchConnector(private val context: Context) : WatchConnector {
    override fun connect(onStage: (ConnectStage) -> Unit): WatchLink {
        onStage(ConnectStage.CONNECTING)
        Thread.sleep(400)
        val watch = SimulatedUnaWatch()
        val samples = context.assets.list("demo").orEmpty().filter { it.endsWith(".fit") }.sorted()
        samples.forEachIndexed { i, name ->
            val bytes = context.assets.open("demo/$name").use { it.readBytes() }
            val app = if (i % 2 == 0) "Cycling" else "Hiking"
            watch.putFile("/Apps/$app/Activity/202609/$name", bytes)
        }
        watch.putFile("/Apps/ClockfaceRetro/settings.json", ByteArray(12))
        onStage(ConnectStage.PREPARING)
        return WatchLink(watch, watch.protocolVersion) {}
    }
}
