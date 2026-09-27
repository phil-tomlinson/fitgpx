/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.core

import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory
import kotlin.test.fail

/** Validates GPX against the official GPX 1.1 + Garmin TrackPointExtension/PowerExtension v1 schemas. */
object GpxSchema {
    private val schema: Schema by lazy {
        val url = requireNotNull(GpxSchema::class.java.getResource("/xsd/master.xsd"))
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(url)
    }

    fun assertValid(gpx: String, label: String = "") {
        try {
            schema.newValidator().validate(StreamSource(StringReader(gpx)))
        } catch (e: org.xml.sax.SAXException) {
            fail("GPX $label is not schema-valid: ${e.message}")
        }
    }
}
