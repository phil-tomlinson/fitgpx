/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.philtomlinson.fitgpx.BuildConfig
import io.github.philtomlinson.fitgpx.R
import io.github.philtomlinson.fitgpx.ui.components.SectionHeader

const val REPO_URL = "https://github.com/phil-tomlinson/fitgpx"

private data class Library(val name: String, val license: String, val url: String)

private val LIBRARIES = listOf(
    Library("Kotlin & kotlinx.coroutines", "Apache-2.0", "https://kotlinlang.org"),
    Library("Jetpack Compose & AndroidX", "Apache-2.0", "https://developer.android.com/jetpack"),
    Library("Material Design icons", "Apache-2.0", "https://fonts.google.com/icons"),
    Library("osmdroid", "Apache-2.0", "https://github.com/osmdroid/osmdroid"),
)

/** Specifications and projects FitGPX builds on (see CREDITS.md for the full list). */
private val ACKNOWLEDGEMENTS = listOf(
    Library("FIT protocol (Garmin), clean-room decoder", "Public protocol description", "https://developer.garmin.com/fit/"),
    Library("GPX 1.1 (TopoGrafix)", "Open format", "https://www.topografix.com/gpx.asp"),
    Library("UNA Watch SDK: File Transfer Service and FIT layout", "MIT", "https://github.com/UNAWatch/una-sdk"),
    Library("Adafruit BLE File Transfer protocol", "MIT", "https://github.com/adafruit/Adafruit_CircuitPython_BLE_File_Transfer"),
    Library("python-fitparse test files, fitdecode", "MIT", "https://github.com/dtcooper/python-fitparse"),
)

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val uri = LocalUriHandler.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFF006A63))) {
                    Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.about_description), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            }
            AboutRow(Icons.Filled.Lock, stringResource(R.string.about_privacy), stringResource(R.string.about_privacy_body)) {}
            AboutRow(Icons.Filled.Code, stringResource(R.string.about_source), REPO_URL) { uri.openUri(REPO_URL) }
            AboutRow(Icons.Filled.BugReport, stringResource(R.string.about_issue), stringResource(R.string.about_issue_body)) { uri.openUri("$REPO_URL/issues/new/choose") }
            AboutRow(Icons.Filled.Gavel, stringResource(R.string.about_license), "GNU GPL v3.0 or later") { uri.openUri("$REPO_URL/blob/main/LICENSE") }
            AboutRow(Icons.Filled.Map, stringResource(R.string.about_map_data), "© OpenStreetMap contributors (ODbL)") { uri.openUri("https://www.openstreetmap.org/copyright") }
            SectionHeader(stringResource(R.string.about_libraries))
            LIBRARIES.forEach { lib ->
                ListItem(
                    headlineContent = { Text(lib.name) },
                    supportingContent = { Text(lib.license) },
                    modifier = Modifier.clickable { uri.openUri(lib.url) },
                )
            }
            SectionHeader(stringResource(R.string.about_acknowledgements))
            ACKNOWLEDGEMENTS.forEach { lib ->
                ListItem(
                    headlineContent = { Text(lib.name) },
                    supportingContent = { Text(lib.license) },
                    modifier = Modifier.clickable { uri.openUri(lib.url) },
                )
            }
            AboutRow(Icons.Filled.Info, stringResource(R.string.about_all_credits), "CREDITS.md") { uri.openUri("$REPO_URL/blob/main/CREDITS.md") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AboutRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
