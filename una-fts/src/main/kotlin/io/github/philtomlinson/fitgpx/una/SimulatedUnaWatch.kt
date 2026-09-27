/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.una

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32

/**
 * An in-memory UNA Watch speaking the File Transfer Service protocol, including its failure modes
 * (lost notifications, short bursts). Used by the tests and by the app's demo mode so the whole sync
 * flow can be exercised without hardware.
 */
class SimulatedUnaWatch(
    val protocolVersion: Int = FtsProtocol.FAST_TRANSFER_VERSION,
    mtu: Int = 247,
    /** Drop every Nth READ_DATA notification (0 = never). */
    private val dropEveryNth: Int = 0,
    /** Cap each v5 burst below the requested window, like a watch with a smaller buffer (0 = no cap). */
    private val burstCap: Int = 0,
) : FtsTransport {

    override val maxNotificationSize: Int = mtu - 3

    private val files = LinkedHashMap<String, ByteArray>()
    private val queue = LinkedBlockingQueue<ByteArray>()
    private var readPath: String? = null
    private var readDataCount = 0

    /** Number of requests the client has sent (for asserting efficiency in tests). */
    var requests = 0
        private set

    fun putFile(path: String, data: ByteArray) {
        files[path] = data
    }

    override fun send(request: ByteArray) {
        requests++
        val buf = ByteBuffer.wrap(request).order(ByteOrder.LITTLE_ENDIAN)
        when (request[0]) {
            FtsProtocol.LISTDIR -> listDir(path(request, buf.getShort(2).toInt(), 4))
            FtsProtocol.READ -> {
                val p = path(request, buf.getShort(2).toInt(), 12)
                readPath = p
                emitRead(p, buf.getInt(4), buf.getInt(8))
            }
            FtsProtocol.READ_PACING -> readPath?.let { emitRead(it, buf.getInt(4), buf.getInt(8)) }
            FtsProtocol.DIGEST -> digest(path(request, buf.getShort(2).toInt(), 4))
            else -> queue += byteArrayOf((request[0] + 1).toByte(), FtsProtocol.STATUS_PROTOCOL.toByte())
        }
    }

    override fun receive(timeoutMs: Long): ByteArray? = queue.poll(timeoutMs, TimeUnit.MILLISECONDS)

    private fun path(b: ByteArray, len: Int, at: Int) = String(b, at, len, Charsets.UTF_8)

    private fun le(size: Int) = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

    private fun listDir(dir: String) {
        val prefix = if (dir.endsWith("/")) dir else "$dir/"
        val children = LinkedHashMap<String, Pair<Boolean, Long>>()
        for ((p, data) in files) {
            if (!p.startsWith(prefix)) continue
            val rest = p.removePrefix(prefix)
            val name = rest.substringBefore('/')
            children[name] = if ('/' in rest) (true to 0L) else (false to data.size.toLong())
        }
        if (children.isEmpty() && dir != "/") {
            queue += entry(FtsProtocol.STATUS_NO_FILE, 0, 0, false, 0, "")
            return
        }
        var i = 0
        for ((name, v) in children) queue += entry(FtsProtocol.STATUS_OK, i++, children.size, v.first, v.second, name)
        queue += entry(FtsProtocol.STATUS_OK, children.size, children.size, false, 0, "")
    }

    private fun entry(status: Int, n: Int, total: Int, dir: Boolean, size: Long, name: String): ByteArray {
        val nb = name.toByteArray(Charsets.UTF_8)
        return le(FtsProtocol.LISTDIR_HEADER + nb.size).put(FtsProtocol.LISTDIR_ENTRY).put(status.toByte())
            .putShort(nb.size.toShort()).putInt(n).putInt(total).putInt(if (dir) 1 else 0)
            .putLong(1_758_960_000_000_000_000L).putInt(size.toInt()).put(nb).array()
    }

    private fun emitRead(p: String, offset: Int, chunkSize: Int) {
        val data = files[p]
        if (data == null) {
            queue += readData(FtsProtocol.STATUS_NO_FILE, 0, 0, ByteArray(0))
            return
        }
        val payload = maxNotificationSize - FtsProtocol.READ_DATA_HEADER
        var budget = if (protocolVersion >= FtsProtocol.FAST_TRANSFER_VERSION) {
            if (burstCap > 0) minOf(chunkSize, burstCap) else chunkSize
        } else {
            minOf(chunkSize, payload) // classic: exactly one packet per request
        }
        var pos = offset
        if (pos >= data.size) {
            queue += readData(FtsProtocol.STATUS_OK, data.size, data.size, ByteArray(0))
            return
        }
        while (budget > 0 && pos < data.size) {
            val n = minOf(payload, budget, data.size - pos)
            val packet = readData(FtsProtocol.STATUS_OK, pos, data.size, data.copyOfRange(pos, pos + n))
            readDataCount++
            if (dropEveryNth == 0 || readDataCount % dropEveryNth != 0) queue += packet
            pos += n
            budget -= n
        }
    }

    private fun readData(status: Int, offset: Int, total: Int, data: ByteArray): ByteArray =
        le(FtsProtocol.READ_DATA_HEADER + data.size).put(FtsProtocol.READ_DATA).put(status.toByte()).putShort(0)
            .putInt(offset).putInt(total).putInt(data.size).put(data).array()

    private fun digest(p: String) {
        val data = files[p]
        if (protocolVersion < FtsProtocol.FAST_TRANSFER_VERSION || data == null) {
            val status = if (data == null) FtsProtocol.STATUS_NO_FILE else FtsProtocol.STATUS_PROTOCOL
            queue += le(12).put(FtsProtocol.DIGEST_STATUS).put(status.toByte()).putShort(0).putInt(0).putInt(0).array()
            return
        }
        val crc = CRC32().apply { update(data) }.value
        queue += le(12).put(FtsProtocol.DIGEST_STATUS).put(FtsProtocol.STATUS_OK.toByte()).putShort(0)
            .putInt(data.size).putInt(crc.toInt()).array()
    }
}
