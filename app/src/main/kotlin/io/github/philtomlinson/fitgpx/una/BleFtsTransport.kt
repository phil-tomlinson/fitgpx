/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.una

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** What the connection is doing, for the progress UI. */
enum class ConnectStage { PAIRING, CONNECTING, PREPARING }

/**
 * [FtsTransport] over a real Bluetooth LE GATT connection to a UNA Watch.
 *
 * Every call blocks; use it from a background thread. Permissions (BLUETOOTH_CONNECT on Android 12+)
 * are checked by the UI before a connection is attempted.
 */
@SuppressLint("MissingPermission")
class BleFtsTransport private constructor(
    private val gatt: BluetoothGatt,
    private val raw: BluetoothGattCharacteristic,
    private val callback: Callback,
    override val maxNotificationSize: Int,
) : FtsTransport, AutoCloseable {

    override fun send(request: ByteArray) {
        if (callback.disconnected) throw IOException("The watch disconnected")
        repeat(20) { attempt ->
            callback.writes.clear()
            val started = if (Build.VERSION.SDK_INT >= 33) {
                gatt.writeCharacteristic(raw, request, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                raw.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @Suppress("DEPRECATION")
                raw.value = request
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(raw)
            }
            if (started) {
                val status = callback.writes.poll(OP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    ?: throw IOException("The watch didn't acknowledge a request")
                if (status == BluetoothGatt.GATT_SUCCESS) return
                throw IOException("Bluetooth write failed ($status)")
            }
            // The stack is busy with a previous operation: back off briefly and retry.
            Thread.sleep(10L * (attempt + 1))
        }
        throw IOException("Bluetooth is busy")
    }

    override fun receive(timeoutMs: Long): ByteArray? {
        val packet = callback.notifications.poll(timeoutMs, TimeUnit.MILLISECONDS)
        if (packet == null && callback.disconnected) throw IOException("The watch disconnected")
        return packet
    }

    override fun close() {
        runCatching { gatt.disconnect() }
        runCatching { gatt.close() }
    }

    private class Callback : BluetoothGattCallback() {
        val notifications = LinkedBlockingQueue<ByteArray>()
        val writes = LinkedBlockingQueue<Int>()
        val events = LinkedBlockingQueue<Pair<String, Any?>>()
        @Volatile var disconnected = false

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                events += "connected" to status
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                disconnected = true
                events += "disconnected" to status
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            events += "mtu" to (if (status == BluetoothGatt.GATT_SUCCESS) mtu else DEFAULT_MTU)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            events += "services" to status
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            events += "read" to (status to value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(gatt: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            @Suppress("DEPRECATION")
            events += "read" to (status to (c.value ?: ByteArray(0)))
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            events += "descriptor" to status
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            writes += status
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            notifications += value
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            notifications += (c.value ?: return).copyOf()
        }

        fun await(name: String, timeoutMs: Long = OP_TIMEOUT_MS): Any? {
            val deadline = System.nanoTime() + timeoutMs * 1_000_000
            while (true) {
                val left = (deadline - System.nanoTime()) / 1_000_000
                if (left <= 0) throw IOException("Timed out waiting for the watch ($name)")
                val (event, value) = events.poll(left, TimeUnit.MILLISECONDS) ?: continue
                if (event == name) return value
                if (event == "disconnected") throw IOException("The watch disconnected")
            }
        }
    }

    /** The connected transport plus the watch's File Transfer protocol version. */
    class Connection(val transport: BleFtsTransport, val protocolVersion: Int)

    companion object {
        private const val OP_TIMEOUT_MS = 15_000L
        private const val DEFAULT_MTU = 23
        private const val DESIRED_MTU = 247
        private val SERVICE = UUID.fromString(FtsProtocol.SERVICE_UUID)
        private val VERSION = UUID.fromString(FtsProtocol.VERSION_UUID)
        private val RAW = UUID.fromString(FtsProtocol.RAW_TRANSFER_UUID)
        private val CCCD = UUID.fromString(FtsProtocol.CCCD_UUID)

        /**
         * Pairs with (if needed) and connects to [device], negotiates a large MTU and subscribes to the
         * File Transfer Service. Blocks until ready or throws [IOException].
         */
        fun connect(context: Context, device: BluetoothDevice, onStage: (ConnectStage) -> Unit = {}): Connection {
            if (device.bondState != BluetoothDevice.BOND_BONDED) {
                onStage(ConnectStage.PAIRING)
                bond(context, device)
            }
            onStage(ConnectStage.CONNECTING)
            val cb = Callback()
            val gatt = device.connectGatt(context, false, cb, BluetoothDevice.TRANSPORT_LE)
                ?: throw IOException("Couldn't start a Bluetooth connection")
            try {
                cb.await("connected", 30_000)
                onStage(ConnectStage.PREPARING)
                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                val mtu = if (gatt.requestMtu(DESIRED_MTU)) cb.await("mtu") as Int else DEFAULT_MTU
                if (!gatt.discoverServices()) throw IOException("Service discovery failed")
                cb.await("services")
                val service = gatt.getService(SERVICE)
                    ?: throw IOException("This device doesn't offer the UNA File Transfer Service")
                val version = service.getCharacteristic(VERSION) ?: throw IOException("File Transfer version missing")
                val raw = service.getCharacteristic(RAW) ?: throw IOException("File Transfer characteristic missing")

                @Suppress("DEPRECATION")
                if (!gatt.readCharacteristic(version)) throw IOException("Couldn't read the protocol version")
                @Suppress("UNCHECKED_CAST")
                val (status, value) = cb.await("read") as Pair<Int, ByteArray>
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    throw IOException("The watch refused access ($status). Pair it with this phone and try again.")
                }
                val protocolVersion = if (value.size >= 4) ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).int else value.firstOrNull()?.toInt() ?: 4

                gatt.setCharacteristicNotification(raw, true)
                val cccd = raw.getDescriptor(CCCD) ?: throw IOException("Notifications not supported")
                val enable = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                val started = if (Build.VERSION.SDK_INT >= 33) {
                    gatt.writeDescriptor(cccd, enable) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    cccd.value = enable
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(cccd)
                }
                if (!started || cb.await("descriptor") != BluetoothGatt.GATT_SUCCESS) {
                    throw IOException("Couldn't subscribe to the watch")
                }
                return Connection(BleFtsTransport(gatt, raw, cb, mtu - 3), protocolVersion)
            } catch (e: Exception) {
                runCatching { gatt.disconnect() }
                runCatching { gatt.close() }
                throw e
            }
        }

        /** Starts pairing and waits for the user to confirm it (on the phone and/or the watch). */
        private fun bond(context: Context, device: BluetoothDevice) {
            val done = CountDownLatch(1)
            var result = BluetoothDevice.BOND_NONE
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    @Suppress("DEPRECATION")
                    val d: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (d?.address != device.address) return
                    val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                    if (state != BluetoothDevice.BOND_BONDING) {
                        result = state
                        done.countDown()
                    }
                }
            }
            ContextCompat.registerReceiver(
                context, receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            try {
                if (!device.createBond()) throw IOException("Couldn't start pairing")
                if (!done.await(120, TimeUnit.SECONDS)) throw IOException("Pairing timed out")
                if (result != BluetoothDevice.BOND_BONDED) throw IOException("Pairing was cancelled or declined")
            } finally {
                runCatching { context.unregisterReceiver(receiver) }
            }
        }
    }
}
