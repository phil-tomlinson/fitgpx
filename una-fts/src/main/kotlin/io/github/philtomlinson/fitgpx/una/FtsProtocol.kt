/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.una

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Wire format of the UNA Watch BLE File Transfer Service (FTS), as documented in the UNA SDK
 * (`Docs/BLE-File-Transfer-Service.md`). It is Adafruit's CircuitPython BLE File Transfer protocol
 * plus UNA's version-5 fast-transfer extensions. All integers are little-endian.
 */
object FtsProtocol {
    const val SERVICE_UUID = "0000febb-0000-1000-8000-00805f9b34fb"
    const val VERSION_UUID = "adaf0001-4669-6c65-5472-616e73666572"
    const val RAW_TRANSFER_UUID = "adaf0002-4669-6c65-5472-616e73666572"
    const val CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb"

    const val READ: Byte = 0x10
    const val READ_DATA: Byte = 0x11
    const val READ_PACING: Byte = 0x12
    const val LISTDIR: Byte = 0x50
    const val LISTDIR_ENTRY: Byte = 0x51
    const val DIGEST: Byte = 0x70
    const val DIGEST_STATUS: Byte = 0x71

    const val STATUS_OK = 0x01
    const val STATUS_ERROR = 0x02
    const val STATUS_NO_FILE = 0x03
    const val STATUS_PROTOCOL = 0x04
    const val STATUS_READ_ONLY = 0x05

    /** Header bytes before the data in a READ_DATA notification. */
    const val READ_DATA_HEADER = 16
    const val LISTDIR_HEADER = 28

    /** Version 5 adds windowed transfers and DIGEST. */
    const val FAST_TRANSFER_VERSION = 5

    /** Recommended read window for version-5 watches. */
    const val READ_WINDOW = 4096

    private fun le(size: Int): ByteBuffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

    fun read(path: String, offset: Int, chunkSize: Int): ByteArray {
        val p = path.toByteArray(Charsets.UTF_8)
        return le(12 + p.size).put(READ).put(0).putShort(p.size.toShort()).putInt(offset).putInt(chunkSize).put(p).array()
    }

    fun readPacing(offset: Int, chunkSize: Int): ByteArray =
        le(12).put(READ_PACING).put(STATUS_OK.toByte()).putShort(0).putInt(offset).putInt(chunkSize).array()

    fun listDir(path: String): ByteArray {
        val p = path.toByteArray(Charsets.UTF_8)
        return le(4 + p.size).put(LISTDIR).put(0).putShort(p.size.toShort()).put(p).array()
    }

    fun digest(path: String): ByteArray {
        val p = path.toByteArray(Charsets.UTF_8)
        return le(4 + p.size).put(DIGEST).put(0).putShort(p.size.toShort()).put(p).array()
    }

    class ReadData(val status: Int, val offset: Int, val totalLength: Int, val data: ByteArray)

    /** Parses READ_DATA; the data length is clamped to what the notification actually carried. */
    fun parseReadData(b: ByteArray): ReadData? {
        if (b.size < READ_DATA_HEADER || b[0] != READ_DATA) return null
        val buf = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        val status = b[1].toInt() and 0xFF
        val offset = buf.getInt(4)
        val total = buf.getInt(8)
        val declared = buf.getInt(12)
        val len = declared.coerceIn(0, b.size - READ_DATA_HEADER)
        return ReadData(status, offset, total, b.copyOfRange(READ_DATA_HEADER, READ_DATA_HEADER + len))
    }

    class ListEntry(
        val status: Int,
        val entryNumber: Int,
        val totalEntries: Int,
        val isDirectory: Boolean,
        val modifiedNanos: Long,
        val size: Long,
        val name: String,
    ) {
        val isTerminator: Boolean get() = entryNumber == totalEntries && name.isEmpty()
    }

    fun parseListEntry(b: ByteArray): ListEntry? {
        if (b.size < 2 || b[0] != LISTDIR_ENTRY) return null
        val status = b[1].toInt() and 0xFF
        if (b.size < LISTDIR_HEADER) return ListEntry(status, 0, 0, false, 0, 0, "")
        val buf = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        val nameLen = (buf.getShort(2).toInt() and 0xFFFF).coerceAtMost(b.size - LISTDIR_HEADER)
        return ListEntry(
            status = status,
            entryNumber = buf.getInt(4),
            totalEntries = buf.getInt(8),
            isDirectory = (buf.getInt(12) and 1) != 0,
            modifiedNanos = buf.getLong(16),
            size = buf.getInt(24).toLong() and 0xFFFFFFFFL,
            name = String(b, LISTDIR_HEADER, nameLen, Charsets.UTF_8),
        )
    }

    class DigestStatus(val status: Int, val fileSize: Long, val crc32: Long)

    fun parseDigest(b: ByteArray): DigestStatus? {
        if (b.size < 2 || b[0] != DIGEST_STATUS) return null
        if (b.size < 12) return DigestStatus(b[1].toInt() and 0xFF, 0, 0)
        val buf = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        return DigestStatus(b[1].toInt() and 0xFF, buf.getInt(4).toLong() and 0xFFFFFFFFL, buf.getInt(8).toLong() and 0xFFFFFFFFL)
    }
}
