/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.core.fit

/**
 * One decoded FIT data message. Field values are raw (unscaled) and invalid values are `null`.
 *
 * Integer fields decode to [Long], floating point fields to [Double], strings to [String] and
 * array fields to a [List]. The typed accessors return the first element of an array field.
 */
class FitMessage internal constructor(
    val globalNumber: Int,
    private val fieldNumbers: IntArray,
    private val values: Array<Any?>,
) {
    val fields: Set<Int> get() = fieldNumbers.toSet()

    operator fun get(field: Int): Any? {
        for (i in fieldNumbers.indices) if (fieldNumbers[i] == field) return values[i]
        return null
    }

    fun long(field: Int): Long? = when (val v = scalar(get(field))) {
        is Long -> v
        is Double -> v.toLong()
        else -> null
    }

    fun int(field: Int): Int? = long(field)?.toInt()

    fun double(field: Int): Double? = when (val v = scalar(get(field))) {
        is Long -> v.toDouble()
        is Double -> v
        else -> null
    }

    fun string(field: Int): String? = get(field) as? String

    /** Applies the FIT profile scale and offset: `value / scale - offset`. */
    fun scaled(field: Int, scale: Double, offset: Double = 0.0): Double? = double(field)?.let { it / scale - offset }

    private fun scalar(v: Any?): Any? = if (v is List<*>) v.firstOrNull { it != null } else v

    override fun toString(): String = buildString {
        append("FitMessage(").append(globalNumber)
        for (i in fieldNumbers.indices) append(", ").append(fieldNumbers[i]).append('=').append(values[i])
        append(')')
    }
}
