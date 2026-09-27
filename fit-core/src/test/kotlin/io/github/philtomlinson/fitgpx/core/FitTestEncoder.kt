/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.core

import io.github.philtomlinson.fitgpx.core.fit.Crc16
import io.github.philtomlinson.fitgpx.core.fit.FitProfile
import java.io.ByteArrayOutputStream
import kotlin.math.roundToLong

/**
 * A tiny FIT *encoder* used only by tests to build files that exercise specific corners of
 * the protocol (big endian, compressed timestamps, developer fields, damaged files…).
 */
class FitTestEncoder(private val bigEndian: Boolean = false) {

    class Field(val number: Int, val size: Int, val baseType: Int)

    private class Def(val fields: List<Field>, val devSizes: List<Int>)

    private val body = ByteArrayOutputStream()
    private val defs = HashMap<Int, Def>()

    fun define(local: Int, global: Int, fields: List<Field>, devFields: List<Int> = emptyList()): FitTestEncoder {
        val hasDev = devFields.isNotEmpty()
        body.write(0x40 or (if (hasDev) 0x20 else 0) or local)
        body.write(0) // reserved
        body.write(if (bigEndian) 1 else 0)
        writeInt(global.toLong(), 2)
        body.write(fields.size)
        for (f in fields) {
            body.write(f.number); body.write(f.size); body.write(f.baseType)
        }
        if (hasDev) {
            body.write(devFields.size)
            devFields.forEachIndexed { i, size -> body.write(i); body.write(size); body.write(0) }
        }
        defs[local] = Def(fields, devFields)
        return this
    }

    /** Values are Long (integers), String, or null (writes the invalid sentinel). */
    fun data(local: Int, vararg values: Any?): FitTestEncoder {
        body.write(local)
        writeValues(local, values)
        return this
    }

    fun compressed(local: Int, timeOffset: Int, vararg values: Any?): FitTestEncoder {
        body.write(0x80 or (local shl 5) or (timeOffset and 0x1F))
        writeValues(local, values)
        return this
    }

    fun raw(vararg bytes: Int): FitTestEncoder {
        bytes.forEach { body.write(it) }
        return this
    }

    private fun writeValues(local: Int, values: Array<out Any?>) {
        val def = defs.getValue(local)
        require(values.size == def.fields.size) { "expected ${def.fields.size} values" }
        def.fields.forEachIndexed { i, f ->
            when (val v = values[i]) {
                null -> writeInvalid(f)
                is String -> {
                    val b = v.toByteArray(Charsets.UTF_8)
                    for (j in 0 until f.size) body.write(if (j < b.size) b[j].toInt() else 0)
                }
                is Number -> writeInt(v.toLong(), f.size)
                else -> error("unsupported $v")
            }
        }
        def.devSizes.forEach { repeat(it) { body.write(0xAB) } }
    }

    private fun writeInvalid(f: Field) {
        val invalid = when (f.baseType) {
            0x01 -> 0x7FL
            0x83 -> 0x7FFFL
            0x85 -> 0x7FFFFFFFL
            0x0A, 0x8B, 0x8C, 0x07 -> 0L
            else -> -1L
        }
        writeInt(invalid, f.size)
    }

    private fun writeInt(v: Long, size: Int) {
        if (bigEndian) {
            for (i in size - 1 downTo 0) body.write(((v shr (8 * i)) and 0xFF).toInt())
        } else {
            for (i in 0 until size) body.write(((v shr (8 * i)) and 0xFF).toInt())
        }
    }

    /**
     * @param headerSize 12 or 14
     * @param declaredDataSize overrides the data size written in the header (to fake damage)
     * @param corruptCrc writes a wrong file CRC
     * @param includeCrc omit the trailing CRC entirely
     */
    fun build(
        headerSize: Int = 14,
        declaredDataSize: Long? = null,
        corruptCrc: Boolean = false,
        includeCrc: Boolean = true,
    ): ByteArray {
        val data = body.toByteArray()
        val out = ByteArrayOutputStream()
        val size = declaredDataSize ?: data.size.toLong()
        val header = ByteArray(headerSize)
        header[0] = headerSize.toByte()
        header[1] = 0x20
        header[2] = (2195 and 0xFF).toByte(); header[3] = (2195 shr 8).toByte()
        for (i in 0 until 4) header[4 + i] = ((size shr (8 * i)) and 0xFF).toByte()
        ".FIT".forEachIndexed { i, c -> header[8 + i] = c.code.toByte() }
        if (headerSize == 14) {
            val crc = Crc16.compute(header, 0, 12)
            header[12] = (crc and 0xFF).toByte(); header[13] = (crc shr 8).toByte()
        }
        out.write(header)
        out.write(data)
        if (includeCrc) {
            val all = out.toByteArray()
            var crc = Crc16.compute(all, 0, all.size)
            if (corruptCrc) crc = crc xor 0x5A5A
            out.write(crc and 0xFF); out.write(crc shr 8)
        }
        return out.toByteArray()
    }

    companion object {
        fun f(number: Int, size: Int, baseType: Int) = Field(number, size, baseType)

        const val ENUM = 0x00
        const val SINT8 = 0x01
        const val UINT8 = 0x02
        const val UINT16 = 0x84
        const val SINT32 = 0x85
        const val UINT32 = 0x86
        const val STRING = 0x07
        const val UINT32Z = 0x8C

        /** Degrees → semicircles. */
        fun sc(deg: Double): Long = (deg * 2147483648.0 / 180.0).roundToLong()

        /** Unix epoch seconds → FIT timestamp. */
        fun ts(unixSeconds: Long): Long = unixSeconds - FitProfile.FIT_EPOCH_OFFSET_S

        /** Altitude in metres → FIT raw (scale 5, offset 500). */
        fun alt(m: Double): Long = ((m + 500) * 5).roundToLong()

        val RECORD_FIELDS = listOf(
            f(253, 4, UINT32), f(0, 4, SINT32), f(1, 4, SINT32), f(78, 4, UINT32),
            f(3, 1, UINT8), f(4, 1, UINT8), f(7, 2, UINT16), f(13, 1, SINT8),
        )

        /**
         * A simple, valid activity: [n] records one second apart heading north from
         * (lat0, lon0) by [stepDeg] each, climbing 1 m per point.
         */
        fun simpleActivity(
            n: Int = 10,
            start: Long = 1_700_000_000L,
            lat0: Double = 51.0,
            lon0: Double = -114.0,
            stepDeg: Double = 0.0001,
            sport: Int = 2,
        ): FitTestEncoder {
            val e = FitTestEncoder()
            e.define(0, 0, listOf(f(0, 1, ENUM), f(1, 2, UINT16), f(4, 4, UINT32)))
            e.data(0, 4L, 1L, ts(start))
            e.define(1, 20, RECORD_FIELDS)
            for (i in 0 until n) {
                e.data(1, ts(start + i), sc(lat0 + i * stepDeg), sc(lon0), alt(1000.0 + i), 120L + i, 80L, 200L, 15L)
            }
            e.define(2, 18, listOf(f(253, 4, UINT32), f(2, 4, UINT32), f(5, 1, ENUM), f(7, 4, UINT32)))
            e.data(2, ts(start + n - 1), ts(start), sport.toLong(), (n - 1) * 1000L)
            return e
        }
    }
}
