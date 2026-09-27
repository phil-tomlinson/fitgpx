/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.philtomlinson.fitgpx.core.track.PrivacyZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    private object K {
        val folderUri = stringPreferencesKey("output_folder_uri")
        val folderName = stringPreferencesKey("output_folder_name")
        val template = stringPreferencesKey("name_template")
        val conflict = stringPreferencesKey("conflict_policy")
        val ele = booleanPreferencesKey("include_elevation")
        val time = booleanPreferencesKey("include_time")
        val hr = booleanPreferencesKey("include_hr")
        val cad = booleanPreferencesKey("include_cadence")
        val power = booleanPreferencesKey("include_power")
        val temp = booleanPreferencesKey("include_temperature")
        val laps = booleanPreferencesKey("include_laps")
        val coursePoints = booleanPreferencesKey("include_course_points")
        val decimals = intPreferencesKey("coordinate_decimals")
        val splitPauses = booleanPreferencesKey("split_pauses")
        val splitSessions = booleanPreferencesKey("split_sessions")
        val spikes = booleanPreferencesKey("remove_spikes")
        val simplify = intPreferencesKey("simplify_m")
        val zones = stringPreferencesKey("privacy_zones")
        val hideStart = intPreferencesKey("hide_start_m")
        val hideEnd = intPreferencesKey("hide_end_m")
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val units = stringPreferencesKey("units")
        val map = booleanPreferencesKey("show_map")
        val unaAddress = stringPreferencesKey("una_address")
        val unaName = stringPreferencesKey("una_name")
    }

    val settings: Flow<AppSettings> = store.data.map { read(it) }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { p ->
            val s = transform(read(p))
            fun <T> put(key: Preferences.Key<T>, value: T?) {
                if (value == null) p.remove(key) else p[key] = value
            }
            put(K.folderUri, s.outputFolderUri)
            put(K.folderName, s.outputFolderName)
            put(K.template, s.nameTemplate)
            put(K.conflict, s.conflictPolicy.name)
            put(K.ele, s.includeElevation)
            put(K.time, s.includeTime)
            put(K.hr, s.includeHeartRate)
            put(K.cad, s.includeCadence)
            put(K.power, s.includePower)
            put(K.temp, s.includeTemperature)
            put(K.laps, s.includeLaps)
            put(K.coursePoints, s.includeCoursePoints)
            put(K.decimals, s.coordinateDecimals)
            put(K.splitPauses, s.splitAtPauses)
            put(K.splitSessions, s.splitSessions)
            put(K.spikes, s.removeSpikes)
            put(K.simplify, s.simplifyMeters)
            put(K.zones, encodeZones(s.privacyZones))
            put(K.hideStart, s.hideStartMeters)
            put(K.hideEnd, s.hideEndMeters)
            put(K.theme, s.themeMode.name)
            put(K.dynamic, s.dynamicColor)
            put(K.units, s.units.name)
            put(K.map, s.showMap)
            put(K.unaAddress, s.unaAddress)
            put(K.unaName, s.unaName)
        }
    }

    private fun read(p: Preferences, d: AppSettings = AppSettings()) = AppSettings(
        outputFolderUri = p[K.folderUri],
        outputFolderName = p[K.folderName],
        nameTemplate = p[K.template] ?: d.nameTemplate,
        conflictPolicy = enumOr(p[K.conflict], d.conflictPolicy),
        includeElevation = p[K.ele] ?: d.includeElevation,
        includeTime = p[K.time] ?: d.includeTime,
        includeHeartRate = p[K.hr] ?: d.includeHeartRate,
        includeCadence = p[K.cad] ?: d.includeCadence,
        includePower = p[K.power] ?: d.includePower,
        includeTemperature = p[K.temp] ?: d.includeTemperature,
        includeLaps = p[K.laps] ?: d.includeLaps,
        includeCoursePoints = p[K.coursePoints] ?: d.includeCoursePoints,
        coordinateDecimals = p[K.decimals] ?: d.coordinateDecimals,
        splitAtPauses = p[K.splitPauses] ?: d.splitAtPauses,
        splitSessions = p[K.splitSessions] ?: d.splitSessions,
        removeSpikes = p[K.spikes] ?: d.removeSpikes,
        simplifyMeters = p[K.simplify] ?: d.simplifyMeters,
        privacyZones = p[K.zones]?.let(::decodeZones) ?: d.privacyZones,
        hideStartMeters = p[K.hideStart] ?: d.hideStartMeters,
        hideEndMeters = p[K.hideEnd] ?: d.hideEndMeters,
        themeMode = enumOr(p[K.theme], d.themeMode),
        dynamicColor = p[K.dynamic] ?: d.dynamicColor,
        units = enumOr(p[K.units], d.units),
        showMap = p[K.map] ?: d.showMap,
        unaAddress = p[K.unaAddress],
        unaName = p[K.unaName],
    )

    companion object {
        private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
            name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

        /** `lat,lon,radius,label;…` with the label URL-encoded. */
        fun encodeZones(zones: List<PrivacyZone>): String = zones.joinToString(";") {
            String.format(Locale.ROOT, "%.6f,%.6f,%.0f,%s", it.latitude, it.longitude, it.radiusMeters, URLEncoder.encode(it.label, "UTF-8"))
        }

        fun decodeZones(s: String): List<PrivacyZone> = s.split(';').mapNotNull { part ->
            val f = part.split(',', limit = 4)
            if (f.size < 3) return@mapNotNull null
            val lat = f[0].toDoubleOrNull() ?: return@mapNotNull null
            val lon = f[1].toDoubleOrNull() ?: return@mapNotNull null
            val r = f[2].toDoubleOrNull() ?: return@mapNotNull null
            PrivacyZone(lat, lon, r, f.getOrNull(3)?.let { URLDecoder.decode(it, "UTF-8") } ?: "")
        }
    }
}
