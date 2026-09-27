/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.una

import java.io.IOException
import java.util.zip.CRC32

/**
 * The link to a watch: on Android a GATT connection to the Raw Transfer characteristic, in
 * tests a simulated watch. Calls are blocking; run them off the main thread.
 */
interface FtsTransport {
    /** Largest notification the link can deliver (ATT MTU − 3). */
    val maxNotificationSize: Int

    /** Writes one request to the Raw Transfer characteristic. */
    fun send(request: ByteArray)

    /** Next notification, or null if none arrives within [timeoutMs]. */
    fun receive(timeoutMs: Long): ByteArray?

    /** Discards any notifications that are still queued (stale responses to an earlier command). */
    fun drain() {
        do {
            val stale = receive(0)
        } while (stale != null)
    }
}

sealed class FtsException(message: String) : IOException(message) {
    class NoSuchFile(path: String) : FtsException("Not found on the watch: $path")
    class Status(val status: Int, what: String) : FtsException("Watch returned error $status for $what")
    class Timeout(what: String) : FtsException("The watch stopped responding ($what)")
    class Corrupt(path: String) : FtsException("Transfer of $path failed its integrity check")
    class Cancelled : FtsException("Cancelled")
}

data class FtsEntry(val name: String, val isDirectory: Boolean, val size: Long, val modifiedNanos: Long)

data class FtsDigest(val size: Long, val crc32: Long)

/**
 * A blocking client for the UNA File Transfer Service.
 *
 * @param protocolVersion value of the Version characteristic; ≥ 5 enables windowed reads and DIGEST.
 * @param timeoutMs how long to wait for the watch before giving up on a command.
 * @param burstIdleMs in windowed mode, how long a pause in notifications means a burst ended early
 *   (a dropped notification or a watch that sends less than asked); the client then re-requests
 *   from the first missing byte.
 */
class FtsClient(
    private val transport: FtsTransport,
    val protocolVersion: Int,
    private val timeoutMs: Long = 8_000,
    private val burstIdleMs: Long = 400,
    private val maxRetries: Int = 6,
    private val isCancelled: () -> Boolean = { Thread.currentThread().isInterrupted },
    /** Directory listings wait this long: some firmware stays silent for paths that don't exist. */
    private val listTimeoutMs: Long = 4_000,
    /** Receives a human-readable trace of the conversation, for the shareable sync log. */
    private val trace: (String) -> Unit = {},
) {
    val fastTransfer: Boolean get() = protocolVersion >= FtsProtocol.FAST_TRANSFER_VERSION

    private fun checkCancelled() {
        if (isCancelled()) throw FtsException.Cancelled()
    }

    private fun statusError(status: Int, what: String, path: String): FtsException =
        if (status == FtsProtocol.STATUS_NO_FILE) FtsException.NoSuchFile(path) else FtsException.Status(status, what)

    /** Lists a directory. Throws [FtsException.NoSuchFile] if it doesn't exist. */
    fun listDir(path: String): List<FtsEntry> {
        var attempt = 0
        while (true) {
            try {
                val entries = listDirOnce(path)
                trace("LIST $path → " + if (entries.isEmpty()) "(empty)" else entries.joinToString(", ") { if (it.isDirectory) "${it.name}/" else "${it.name} (${it.size} B)" })
                return entries
            } catch (e: FtsException.Timeout) {
                trace("LIST $path → no answer")
                // A late reply must not be mistaken for the next listing: wait for silence first.
                do {
                    val late = transport.receive(burstIdleMs * 2)
                } while (late != null)
                if (++attempt > 1) throw e
            } catch (e: FtsException) {
                trace("LIST $path → ${e.message}")
                throw e
            }
        }
    }

    private fun listDirOnce(path: String): List<FtsEntry> {
        checkCancelled()
        transport.drain()
        transport.send(FtsProtocol.listDir(path))
        val entries = sortedMapOf<Int, FtsEntry>()
        while (true) {
            checkCancelled()
            val packet = transport.receive(listTimeoutMs) ?: throw FtsException.Timeout("listing $path")
            val e = FtsProtocol.parseListEntry(packet)
            if (e == null) {
                trace("ignored packet ${hex(packet)}") // a stale response to something else
                continue
            }
            if (e.status != FtsProtocol.STATUS_OK) throw statusError(e.status, "listing $path", path)
            if (e.isTerminator) break
            if (e.name != "." && e.name != "..") entries[e.entryNumber] = FtsEntry(e.name, e.isDirectory, e.size, e.modifiedNanos)
            if (e.totalEntries in 1..entries.size && entries.lastKey() == e.totalEntries - 1 && entries.size == e.totalEntries) {
                // All entries seen; the terminator normally follows, but don't depend on it.
                transport.receive(burstIdleMs)
                break
            }
        }
        return entries.values.toList()
    }

    /** CRC-32 of a file computed on the watch, or null on watches without the DIGEST extension. */
    fun digest(path: String): FtsDigest? {
        if (!fastTransfer) return null
        checkCancelled()
        transport.drain()
        transport.send(FtsProtocol.digest(path))
        while (true) {
            val packet = transport.receive(timeoutMs) ?: throw FtsException.Timeout("checking $path")
            val d = FtsProtocol.parseDigest(packet) ?: continue
            if (d.status != FtsProtocol.STATUS_OK) throw statusError(d.status, "checking $path", path)
            return FtsDigest(d.fileSize, d.crc32)
        }
    }

    /**
     * Reads a whole file. Chunks are reassembled by offset; a lost notification is recovered by
     * re-requesting from the first missing byte. On version-5 watches the result is verified with
     * DIGEST and re-read once on mismatch.
     */
    fun readFile(path: String, onProgress: (received: Long, total: Long) -> Unit = { _, _ -> }): ByteArray {
        val first = readOnce(path, onProgress)
        val digest = digest(path) ?: return first
        if (verified(first, digest)) return first
        val second = readOnce(path, onProgress)
        if (verified(second, digest)) return second
        throw FtsException.Corrupt(path)
    }

    private fun hex(b: ByteArray): String = b.take(32).joinToString("") { "%02x".format(it) } + if (b.size > 32) "…(${b.size} B)" else ""

    private fun verified(data: ByteArray, d: FtsDigest): Boolean =
        data.size.toLong() == d.size && CRC32().apply { update(data) }.value == d.crc32

    private fun readOnce(path: String, onProgress: (Long, Long) -> Unit): ByteArray {
        checkCancelled()
        val payload = (transport.maxNotificationSize - FtsProtocol.READ_DATA_HEADER).coerceAtLeast(1)
        // Classic (v4) watches answer one packet per request: stop-and-wait. v5 watches burst a window.
        val window = if (fastTransfer) FtsProtocol.READ_WINDOW else payload
        transport.drain()
        transport.send(FtsProtocol.read(path, 0, window))

        var buffer: ByteArray? = null
        var total = -1
        var contiguous = 0
        val pending = HashMap<Int, ByteArray>() // out-of-order chunks waiting for the gap to fill
        var windowEnd = window
        var stalls = 0

        fun absorb(offset: Int, data: ByteArray) {
            val buf = buffer ?: return
            val end = minOf(offset + data.size, buf.size)
            if (offset <= contiguous && end > contiguous) {
                System.arraycopy(data, contiguous - offset, buf, contiguous, end - contiguous)
                contiguous = end
            } else if (offset > contiguous && offset < buf.size) {
                pending[offset] = data
            }
            // Pull in any stashed chunks the new data has reached.
            var progressed = true
            while (progressed) {
                progressed = false
                val next = pending.keys.filter { it <= contiguous }.minOrNull() ?: break
                val chunk = pending.remove(next)!!
                val e = minOf(next + chunk.size, buf.size)
                if (e > contiguous) {
                    System.arraycopy(chunk, contiguous - next, buf, contiguous, e - contiguous)
                    contiguous = e
                }
                progressed = true
            }
        }

        while (true) {
            checkCancelled()
            val wait = if (total < 0) timeoutMs else if (fastTransfer) burstIdleMs else timeoutMs
            val packet = transport.receive(wait)
            if (packet == null) {
                if (total < 0 || ++stalls > maxRetries) throw FtsException.Timeout("reading $path")
                // Burst ended short or a notification was lost: ask again from the first missing byte.
                windowEnd = contiguous + window
                transport.send(FtsProtocol.readPacing(contiguous, window))
                continue
            }
            val r = FtsProtocol.parseReadData(packet) ?: continue
            if (r.status != FtsProtocol.STATUS_OK) throw statusError(r.status, "reading $path", path)
            if (total < 0) {
                if (r.totalLength < 0) throw FtsException.Status(FtsProtocol.STATUS_PROTOCOL, "reading $path")
                total = r.totalLength
                buffer = ByteArray(total)
            }
            val before = contiguous
            absorb(r.offset, r.data)
            if (contiguous > before) stalls = 0
            onProgress(contiguous.toLong(), total.toLong())
            if (contiguous >= total) break
            if (contiguous >= windowEnd) {
                windowEnd = contiguous + window
                transport.send(FtsProtocol.readPacing(contiguous, window))
            }
        }
        // Leftover notifications from the final burst must not be mistaken for the next reply.
        transport.drain()
        return buffer ?: ByteArray(0)
    }
}
