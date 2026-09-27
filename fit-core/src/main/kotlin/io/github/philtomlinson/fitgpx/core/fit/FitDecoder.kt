/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.core.fit

/**
 * A clean-room, dependency-free decoder for the binary FIT (Flexible and Interoperable Data
 * Transfer) format used by Garmin, Wahoo, Coros, Hammerhead, Zwift and most other fitness devices.
 *
 * Supported:
 *  - 12 and 14 byte file headers, header and file CRC validation
 *  - little and big endian definition messages
 *  - compressed timestamp record headers
 *  - developer data fields (skipped safely using their declared size)
 *  - chained FIT files (several FIT files concatenated in one stream)
 *  - lenient recovery of truncated files and files whose header declares no data size,
 *    which is how a device leaves a file after a crash or battery failure mid-activity
 *
 * The decoder is a pure function of its input bytes and is safe to use from any thread.
 */
class FitDecoder(private val options: Options = Options()) {

    data class Options(
        /** Recover as much data as possible from damaged files instead of throwing. */
        val lenient: Boolean = true,
    )

    fun interface MessageHandler {
        fun onMessage(message: FitMessage)
    }

    /**
     * Decodes [data], delivering every data message to [handler] in file order.
     *
     * @throws FitException.NotAFitFile if the data does not start with a FIT header.
     * @throws FitException.Corrupt if the file is damaged and [Options.lenient] is false.
     */
    fun decode(data: ByteArray, handler: MessageHandler): DecodeReport {
        val warnings = mutableListOf<FitWarning>()
        var pos = 0
        var files = 0
        var protocolVersion = 0
        var profileVersion = 0
        var messages = 0

        while (pos < data.size) {
            val remaining = data.size - pos
            if (remaining < MIN_HEADER_SIZE || !hasFitSignature(data, pos)) {
                if (files == 0) throw FitException.NotAFitFile()
                // Some tools pad files with zero bytes; anything else is worth reporting.
                if ((pos until data.size).any { data[it].toInt() != 0 }) {
                    warnings += FitWarning(FitWarning.Type.TRAILING_BYTES, pos, "$remaining unexpected trailing bytes ignored")
                }
                break
            }
            val headerSize = u8(data, pos)
            if (headerSize < MIN_HEADER_SIZE || headerSize > remaining) {
                if (files == 0) throw FitException.NotAFitFile()
                warnings += FitWarning(FitWarning.Type.TRAILING_BYTES, pos, "Invalid chained header ignored")
                break
            }
            protocolVersion = u8(data, pos + 1)
            profileVersion = u16(data, pos + 2, bigEndian = false)
            val dataSize = u32(data, pos + 4, bigEndian = false)

            if (headerSize >= 14) {
                val stored = u16(data, pos + 12, bigEndian = false)
                if (stored != 0 && stored != Crc16.compute(data, pos, 12)) {
                    warnings += FitWarning(FitWarning.Type.HEADER_CRC_MISMATCH, pos, "Header CRC mismatch")
                    if (!options.lenient) throw FitException.Corrupt("Header CRC mismatch")
                }
            }

            val dataStart = pos + headerSize
            var dataEnd = dataStart + dataSize
            var complete = true
            if (dataSize <= 0L || dataEnd > data.size) {
                complete = false
                val type = if (dataSize <= 0L) FitWarning.Type.UNKNOWN_DATA_SIZE else FitWarning.Type.TRUNCATED
                val msg = if (dataSize <= 0L) {
                    "Header declares no data size (file was not closed properly)"
                } else {
                    "File is truncated: header declares $dataSize bytes, only ${data.size - dataStart} present"
                }
                warnings += FitWarning(type, pos, msg)
                if (!options.lenient) throw FitException.Corrupt(msg)
                dataEnd = data.size.toLong()
            }

            val result = decodeRecords(data, dataStart, dataEnd.toInt(), complete, warnings, handler)
            messages += result.messages
            files++

            if (!complete || result.stoppedEarly) break
            val crcPos = dataEnd.toInt()
            if (crcPos + 2 <= data.size) {
                val stored = u16(data, crcPos, bigEndian = false)
                val computed = Crc16.compute(data, pos, crcPos - pos)
                if (stored != computed) {
                    warnings += FitWarning(FitWarning.Type.FILE_CRC_MISMATCH, crcPos, "File CRC mismatch")
                    if (!options.lenient) throw FitException.Corrupt("File CRC mismatch")
                }
                pos = crcPos + 2
            } else {
                warnings += FitWarning(FitWarning.Type.TRUNCATED, crcPos, "File CRC missing")
                if (!options.lenient) throw FitException.Corrupt("File CRC missing")
                pos = data.size
            }
        }
        return DecodeReport(files, messages, protocolVersion, profileVersion, warnings)
    }

    private class RecordResult(val messages: Int, val stoppedEarly: Boolean)

    private fun decodeRecords(
        data: ByteArray,
        start: Int,
        end: Int,
        sizeKnown: Boolean,
        warnings: MutableList<FitWarning>,
        handler: MessageHandler,
    ): RecordResult {
        val definitions = arrayOfNulls<Definition>(16)
        var lastTimestamp: Long? = null
        var p = start
        var count = 0
        val limit = end

        fun fail(type: FitWarning.Type, message: String): RecordResult {
            if (!options.lenient) throw FitException.Corrupt(message)
            // A file already flagged as truncated or unsized legitimately ends with a partial
            // record (or a trailing CRC): that is expected, not a new problem to report twice.
            if (sizeKnown) warnings += FitWarning(type, p, message)
            return RecordResult(count, stoppedEarly = true)
        }

        while (p < limit) {
            val header = u8(data, p)
            if ((header and 0x80) != 0) {
                // Compressed timestamp header: always a data message.
                val local = (header shr 5) and 0x03
                val offset = header and 0x1F
                val def = definitions[local]
                    ?: return fail(FitWarning.Type.UNDEFINED_LOCAL_MESSAGE, "Compressed record for undefined local message $local")
                if (p + 1 + def.dataSize > limit) {
                    return fail(FitWarning.Type.TRUNCATED, "Record truncated at offset $p")
                }
                val ts = lastTimestamp?.let { last ->
                    val base = last and 0x1FL.inv()
                    if (offset >= (last and 0x1F).toInt()) base + offset else base + offset + 0x20
                }
                val msg = readData(data, p + 1, def, injectedTimestamp = ts)
                msg.long(FitProfile.FIELD_TIMESTAMP)?.let { lastTimestamp = it }
                p += 1 + def.dataSize
                handler.onMessage(msg)
                count++
            } else if ((header and 0x40) != 0) {
                val local = header and 0x0F
                val hasDev = (header and 0x20) != 0
                val parsed = parseDefinition(data, p + 1, limit, hasDev)
                    ?: return fail(FitWarning.Type.TRUNCATED, "Definition truncated at offset $p")
                definitions[local] = parsed.first
                p = parsed.second
            } else {
                val local = header and 0x0F
                val def = definitions[local]
                    ?: return fail(FitWarning.Type.UNDEFINED_LOCAL_MESSAGE, "Data record for undefined local message $local at offset $p")
                if (p + 1 + def.dataSize > limit) {
                    return fail(FitWarning.Type.TRUNCATED, "Record truncated at offset $p")
                }
                val msg = readData(data, p + 1, def, injectedTimestamp = null)
                msg.long(FitProfile.FIELD_TIMESTAMP)?.let { lastTimestamp = it }
                p += 1 + def.dataSize
                handler.onMessage(msg)
                count++
            }
        }
        return RecordResult(count, stoppedEarly = false)
    }

    private class Definition(
        val globalNumber: Int,
        val bigEndian: Boolean,
        val fieldNumbers: IntArray,
        val fieldSizes: IntArray,
        val fieldTypes: Array<BaseType>,
        /** Total bytes of the data message payload, including developer fields. */
        val dataSize: Int,
    )

    /** Returns the definition and the offset just past it, or null if it runs past [limit]. */
    private fun parseDefinition(data: ByteArray, start: Int, limit: Int, hasDev: Boolean): Pair<Definition, Int>? {
        var p = start
        if (p + 5 > limit) return null
        val bigEndian = u8(data, p + 1) == 1
        val global = u16(data, p + 2, bigEndian)
        val numFields = u8(data, p + 4)
        p += 5
        if (p + numFields * 3 > limit) return null
        val nums = IntArray(numFields)
        val sizes = IntArray(numFields)
        val types = Array(numFields) { BaseType.BYTE }
        var total = 0
        for (i in 0 until numFields) {
            nums[i] = u8(data, p)
            sizes[i] = u8(data, p + 1)
            types[i] = BaseType.of(u8(data, p + 2))
            total += sizes[i]
            p += 3
        }
        if (hasDev) {
            if (p + 1 > limit) return null
            val numDev = u8(data, p)
            p += 1
            if (p + numDev * 3 > limit) return null
            for (i in 0 until numDev) {
                total += u8(data, p + 1)
                p += 3
            }
        }
        return Definition(global, bigEndian, nums, sizes, types, total) to p
    }

    private fun readData(data: ByteArray, start: Int, def: Definition, injectedTimestamp: Long?): FitMessage {
        val n = def.fieldNumbers.size
        val extra = if (injectedTimestamp != null && FitProfile.FIELD_TIMESTAMP !in def.fieldNumbers) 1 else 0
        val nums = IntArray(n + extra)
        val values = arrayOfNulls<Any>(n + extra)
        var p = start
        for (i in 0 until n) {
            val size = def.fieldSizes[i]
            nums[i] = def.fieldNumbers[i]
            values[i] = readField(data, p, size, def.fieldTypes[i], def.bigEndian)
            p += size
        }
        if (extra == 1) {
            nums[n] = FitProfile.FIELD_TIMESTAMP
            values[n] = injectedTimestamp
        } else if (injectedTimestamp != null) {
            // Explicit timestamp wins; fill it only if the device left it invalid.
            val idx = nums.indexOf(FitProfile.FIELD_TIMESTAMP)
            if (values[idx] == null) values[idx] = injectedTimestamp
        }
        return FitMessage(def.globalNumber, nums, values)
    }

    private fun readField(data: ByteArray, p: Int, size: Int, type: BaseType, bigEndian: Boolean): Any? {
        if (size == 0) return null
        if (type.isString) {
            var len = 0
            while (len < size && data[p + len].toInt() != 0) len++
            if (len == 0) return null
            return String(data, p, len, Charsets.UTF_8)
        }
        if (size % type.size != 0) return null // misaligned field: unusable, skip
        val count = size / type.size
        if (count == 1) return readScalar(data, p, type, bigEndian)
        val list = ArrayList<Any?>(count)
        for (i in 0 until count) list += readScalar(data, p + i * type.size, type, bigEndian)
        return if (list.all { it == null }) null else list
    }

    private fun readScalar(data: ByteArray, p: Int, type: BaseType, bigEndian: Boolean): Any? {
        var raw = 0L
        val s = type.size
        if (bigEndian) {
            for (i in 0 until s) raw = (raw shl 8) or (data[p + i].toLong() and 0xFF)
        } else {
            for (i in s - 1 downTo 0) raw = (raw shl 8) or (data[p + i].toLong() and 0xFF)
        }
        if (raw == type.invalid) return null
        return when {
            type == BaseType.FLOAT32 -> Float.fromBits(raw.toInt()).takeUnless { it.isNaN() }?.toDouble()
            type == BaseType.FLOAT64 -> Double.fromBits(raw).takeUnless { it.isNaN() }
            type.signed && s < 8 -> {
                val shift = 64 - s * 8
                (raw shl shift) shr shift
            }
            else -> raw
        }
    }

    private companion object {
        const val MIN_HEADER_SIZE = 12

        fun hasFitSignature(d: ByteArray, pos: Int): Boolean =
            pos + 12 <= d.size &&
                d[pos + 8] == '.'.code.toByte() && d[pos + 9] == 'F'.code.toByte() &&
                d[pos + 10] == 'I'.code.toByte() && d[pos + 11] == 'T'.code.toByte()

        fun u8(d: ByteArray, p: Int): Int = d[p].toInt() and 0xFF

        fun u16(d: ByteArray, p: Int, bigEndian: Boolean): Int =
            if (bigEndian) (u8(d, p) shl 8) or u8(d, p + 1) else u8(d, p) or (u8(d, p + 1) shl 8)

        fun u32(d: ByteArray, p: Int, bigEndian: Boolean): Long {
            var v = 0L
            if (bigEndian) {
                for (i in 0 until 4) v = (v shl 8) or (d[p + i].toLong() and 0xFF)
            } else {
                for (i in 3 downTo 0) v = (v shl 8) or (d[p + i].toLong() and 0xFF)
            }
            return v
        }
    }
}

/** Summary of a decode pass. */
data class DecodeReport(
    val fileCount: Int,
    val messageCount: Int,
    val protocolVersion: Int,
    val profileVersion: Int,
    val warnings: List<FitWarning>,
)

/** A recoverable problem found while decoding. */
data class FitWarning(val type: Type, val offset: Int, val message: String) {
    enum class Type {
        HEADER_CRC_MISMATCH,
        FILE_CRC_MISMATCH,
        TRUNCATED,
        UNKNOWN_DATA_SIZE,
        TRAILING_BYTES,
        UNDEFINED_LOCAL_MESSAGE,
    }
}

sealed class FitException(message: String) : Exception(message) {
    class NotAFitFile : FitException("Not a FIT file")
    class Corrupt(message: String) : FitException(message)
}

/** The CRC-16 used by the FIT protocol. */
internal object Crc16 {
    private val TABLE = intArrayOf(
        0x0000, 0xCC01, 0xD801, 0x1400, 0xF001, 0x3C00, 0x2800, 0xE401,
        0xA001, 0x6C00, 0x7800, 0xB401, 0x5000, 0x9C01, 0x8801, 0x4400,
    )

    fun compute(data: ByteArray, offset: Int, length: Int): Int {
        var crc = 0
        for (i in offset until offset + length) {
            val b = data[i].toInt() and 0xFF
            var tmp = TABLE[crc and 0xF]
            crc = (crc shr 4) and 0x0FFF
            crc = crc xor tmp xor TABLE[b and 0xF]
            tmp = TABLE[crc and 0xF]
            crc = (crc shr 4) and 0x0FFF
            crc = crc xor tmp xor TABLE[(b shr 4) and 0xF]
        }
        return crc
    }
}
