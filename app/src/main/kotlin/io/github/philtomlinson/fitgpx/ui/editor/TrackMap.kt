/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.ui.editor

import android.content.Context
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.philtomlinson.fitgpx.core.model.TrackPoint
import io.github.philtomlinson.fitgpx.core.track.PrivacyZone
import io.github.philtomlinson.fitgpx.ui.theme.FitGpxColors
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay

/** Holds the map and the overlays we update as the user edits. */
private class MapHolder(context: Context, colors: FitGpxColors, private val density: Float, dark: Boolean) {
    val map = MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        isTilesScaledToDpi = true
        minZoomLevel = 3.0
        maxZoomLevel = 19.0
        isVerticalMapRepetitionEnabled = false
        if (dark) overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
    }

    private fun line(color: Color, widthDp: Float) = Polyline(map).apply {
        outlinePaint.color = color.toArgb()
        outlinePaint.strokeWidth = widthDp * density
        outlinePaint.strokeCap = Paint.Cap.ROUND
        outlinePaint.strokeJoin = Paint.Join.ROUND
        outlinePaint.isAntiAlias = true
        setOnClickListener { _, _, _ -> false }
        infoWindow = null
    }

    private fun dot(fill: Color): Marker = Marker(map).apply {
        icon = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill.toArgb())
            setStroke((2.5f * density).toInt(), android.graphics.Color.WHITE)
            setSize((16 * density).toInt(), (16 * density).toInt())
        }
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        infoWindow = null
        setOnMarkerClickListener { _, _ -> true }
    }

    val outside = line(colors.trackMuted.copy(alpha = 0.8f), 3.5f)
    val keptCasing = line(colors.trackOutline, 7.5f)
    val kept = line(colors.track, 4.5f)
    val start = dot(colors.start)
    val finish = dot(colors.finish)
    val zones = mutableListOf<Polygon>()
    private val zoneColor = colors.privacyZone

    init {
        map.overlays += outside
        map.overlays += keptCasing
        map.overlays += kept
        map.overlays += start
        map.overlays += finish
        map.overlays += CopyrightOverlay(context).apply { setAlignRight(true) }
    }

    fun setZones(list: List<PrivacyZone>) {
        map.overlays.removeAll(zones)
        zones.clear()
        for (z in list) {
            zones += Polygon(map).apply {
                points = Polygon.pointsAsCircle(GeoPoint(z.latitude, z.longitude), z.radiusMeters)
                fillPaint.color = zoneColor.copy(alpha = 0.18f).toArgb()
                outlinePaint.color = zoneColor.toArgb()
                outlinePaint.strokeWidth = 1.5f * density
                infoWindow = null
                setOnClickListener { _, _, _ -> false }
            }
        }
        map.overlays.addAll(0, zones)
    }
}

@Composable
fun TrackMap(
    track: LoadedTrack,
    visible: IntRange,
    zones: List<PrivacyZone>,
    colors: FitGpxColors,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val holder = remember(dark) { MapHolder(context, colors, density, dark) }
    val allGeo = remember(track) { track.displayIndices.map { track.points[it].geo() } }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, holder) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> holder.map.onResume()
                Lifecycle.Event.ON_PAUSE -> holder.map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        holder.map.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            holder.map.onPause()
            holder.map.onDetach()
        }
    }

    AndroidView(
        factory = {
            holder.outside.setPoints(allGeo)
            val box = BoundingBox.fromGeoPointsSafe(allGeo)
            holder.map.addOnFirstLayoutListener { _, _, _, _, _ ->
                holder.map.zoomToBoundingBox(box.increaseByScale(1.15f), false, (24 * density).toInt())
            }
            holder.map
        },
        modifier = modifier,
        update = {
            val idx = track.displayIndices
            val pts = track.points
            val kept = ArrayList<GeoPoint>()
            kept += pts[visible.first].geo()
            for (i in idx) if (i > visible.first && i < visible.last) kept += pts[i].geo()
            if (visible.last > visible.first) kept += pts[visible.last].geo()
            holder.keptCasing.setPoints(kept)
            holder.kept.setPoints(kept)
            holder.start.position = pts[visible.first].geo()
            holder.finish.position = pts[visible.last].geo()
            if (holder.zones.size != zones.size) holder.setZones(zones)
            holder.map.invalidate()
        },
    )
}

private fun TrackPoint.geo() = GeoPoint(latitude, longitude)
