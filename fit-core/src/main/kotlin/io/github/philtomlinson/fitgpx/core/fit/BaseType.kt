/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.core.fit

/**
 * The FIT base types, as defined by the public FIT protocol description.
 *
 * Each type has a fixed size in bytes and a sentinel "invalid" value that a device writes
 * when a field has no data. Decoded invalid values are reported as `null`.
 */
internal enum class BaseType(
    val id: Int,
    val size: Int,
    val signed: Boolean = false,
    val isFloat: Boolean = false,
    val isString: Boolean = false,
    /** Raw (unsigned bit pattern) invalid sentinel. */
    val invalid: Long,
) {
    ENUM(0x00, 1, invalid = 0xFF),
    SINT8(0x01, 1, signed = true, invalid = 0x7F),
    UINT8(0x02, 1, invalid = 0xFF),
    SINT16(0x83, 2, signed = true, invalid = 0x7FFF),
    UINT16(0x84, 2, invalid = 0xFFFF),
    SINT32(0x85, 4, signed = true, invalid = 0x7FFFFFFF),
    UINT32(0x86, 4, invalid = 0xFFFFFFFFL),
    STRING(0x07, 1, isString = true, invalid = 0x00),
    FLOAT32(0x88, 4, isFloat = true, invalid = 0xFFFFFFFFL),
    FLOAT64(0x89, 8, isFloat = true, invalid = -1L), // 0xFFFFFFFFFFFFFFFF
    UINT8Z(0x0A, 1, invalid = 0x00),
    UINT16Z(0x8B, 2, invalid = 0x0000),
    UINT32Z(0x8C, 4, invalid = 0x00000000),
    BYTE(0x0D, 1, invalid = 0xFF),
    SINT64(0x8E, 8, signed = true, invalid = 0x7FFFFFFFFFFFFFFFL),
    UINT64(0x8F, 8, invalid = -1L), // 0xFFFFFFFFFFFFFFFF
    UINT64Z(0x90, 8, invalid = 0L),
    ;

    companion object {
        private val byNumber: Array<BaseType?> = arrayOfNulls<BaseType>(32).also { table ->
            for (t in entries) table[t.id and 0x1F] = t
        }

        /**
         * Resolves a base type byte. Only the low 5 bits identify the type; the high bit is the
         * "endian ability" flag, which we ignore because architecture is declared per message.
         * Unknown types are treated as raw bytes so the decoder can still skip the field.
         */
        fun of(raw: Int): BaseType = byNumber.getOrNull(raw and 0x1F) ?: BYTE
    }
}
