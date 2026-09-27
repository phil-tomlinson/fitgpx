/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.ui.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.philtomlinson.fitgpx.R
import io.github.philtomlinson.fitgpx.core.track.PrivacyZone
import io.github.philtomlinson.fitgpx.data.ThemeMode
import io.github.philtomlinson.fitgpx.data.Units
import io.github.philtomlinson.fitgpx.ui.Format
import io.github.philtomlinson.fitgpx.ui.theme.FitGpxTheme
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.TilesOverlay
import kotlin.math.roundToInt

private const val DEFAULT_RADIUS_M = 200.0

@Composable
fun PrivacyZonesScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    var addManually by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_zones)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { addManually = true },
                icon = { Icon(Icons.Filled.EditLocationAlt, null) },
                text = { Text(stringResource(R.string.zones_add_coordinates)) },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.zones_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (s.showMap && !LocalInspectionMode.current) {
                item {
                    val dark = when (s.themeMode) {
                        ThemeMode.SYSTEM -> isSystemInDarkTheme()
                        ThemeMode.DARK -> true
                        ThemeMode.LIGHT -> false
                    }
                    ZoneMap(
                        zones = s.privacyZones,
                        center = viewModel.suggestedCenter()?.let { GeoPoint(it.latitude, it.longitude) },
                        dark = dark,
                        onLongPress = { viewModel.addZone(PrivacyZone(it.latitude, it.longitude, DEFAULT_RADIUS_M)) },
                        modifier = Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(24.dp)),
                    )
                    Text(
                        stringResource(R.string.zones_map_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            if (s.privacyZones.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Shield, null, tint = FitGpxTheme.colors.privacyZone, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.zones_empty), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            itemsIndexed(s.privacyZones) { index, zone ->
                ZoneCard(
                    index = index,
                    zone = zone,
                    units = s.units,
                    onChange = { viewModel.updateZone(index, it) },
                    onDelete = { viewModel.removeZone(index) },
                )
            }
        }
    }
    if (addManually) {
        CoordinateDialog(onDismiss = { addManually = false }) { lat, lon ->
            viewModel.addZone(PrivacyZone(lat, lon, DEFAULT_RADIUS_M))
            addManually = false
        }
    }
}

@Composable
private fun ZoneCard(index: Int, zone: PrivacyZone, units: Units, onChange: (PrivacyZone) -> Unit, onDelete: () -> Unit) {
    var label by remember(zone.label) { mutableStateOf(zone.label) }
    var radius by remember(zone.radiusMeters) { mutableStateOf(zone.radiusMeters.toFloat()) }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Shield, null, tint = FitGpxTheme.colors.privacyZone)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        zone.label.ifBlank { stringResource(R.string.zones_default_name, index + 1) },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "${Format.coordinate(zone.latitude)}, ${Format.coordinate(zone.longitude)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, stringResource(R.string.action_remove)) }
            }
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.zones_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                keyboardOptions = KeyboardOptions.Default,
            )
            if (label != zone.label) {
                TextButton(onClick = { onChange(zone.copy(label = label)) }, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.action_save))
                }
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.zones_radius), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(Format.distance(radius.toDouble(), units), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = radius,
                onValueChange = { radius = ((it / 25).roundToInt() * 25).toFloat() },
                onValueChangeFinished = { onChange(zone.copy(radiusMeters = radius.toDouble())) },
                valueRange = 50f..2000f,
            )
        }
    }
}

@Composable
private fun CoordinateDialog(onDismiss: () -> Unit, onAdd: (Double, Double) -> Unit) {
    var text by remember { mutableStateOf("") }
    val parsed = remember(text) {
        val parts = text.split(',', ' ', ';').filter { it.isNotBlank() }.mapNotNull { it.trim().toDoubleOrNull() }
        if (parts.size == 2 && parts[0] in -90.0..90.0 && parts[1] in -180.0..180.0) parts[0] to parts[1] else null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.zones_add_coordinates)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("51.0447, -114.0719") },
                    singleLine = true,
                    isError = text.isNotBlank() && parsed == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.zones_coordinates_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let { onAdd(it.first, it.second) } }, enabled = parsed != null) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ZoneMap(
    zones: List<PrivacyZone>,
    center: GeoPoint?,
    dark: Boolean,
    onLongPress: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val zoneColor = FitGpxTheme.colors.privacyZone
    val map = remember(dark) {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            isVerticalMapRepetitionEnabled = false
            if (dark) overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
            overlays += CopyrightOverlay(context).apply { setAlignRight(true) }
            controller.setZoom(if (center != null) 13.0 else 3.0)
            controller.setCenter(center ?: GeoPoint(30.0, 0.0))
        }
    }
    val onLongPressState = rememberUpdatedState(onLongPress)
    DisposableEffect(map) {
        map.overlays.add(
            0,
            MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
                override fun longPressHelper(p: GeoPoint?): Boolean {
                    p?.let { onLongPressState.value(it) }
                    return p != null
                }
            }),
        )
        if (zones.isNotEmpty()) {
            map.addOnFirstLayoutListener { _, _, _, _, _ ->
                val pts = zones.flatMap { Polygon.pointsAsCircle(GeoPoint(it.latitude, it.longitude), it.radiusMeters) }
                map.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(pts).increaseByScale(1.6f), false, (24 * density).toInt())
            }
        }
        map.onResume()
        onDispose {
            map.onPause()
            map.onDetach()
        }
    }
    AndroidView(
        factory = { map },
        modifier = modifier,
        update = { m ->
            m.overlays.removeAll { it is Polygon }
            for (z in zones) {
                m.overlays += Polygon(m).apply {
                    points = Polygon.pointsAsCircle(GeoPoint(z.latitude, z.longitude), z.radiusMeters)
                    fillPaint.color = zoneColor.copy(alpha = 0.2f).toArgb()
                    outlinePaint.color = zoneColor.toArgb()
                    outlinePaint.strokeWidth = 2f * density
                    infoWindow = null
                }
            }
            m.invalidate()
        },
    )
}
