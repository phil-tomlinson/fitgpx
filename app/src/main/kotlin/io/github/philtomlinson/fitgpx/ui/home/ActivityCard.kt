/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.philtomlinson.fitgpx.R
import io.github.philtomlinson.fitgpx.data.ItemStatus
import io.github.philtomlinson.fitgpx.data.QueueItem
import io.github.philtomlinson.fitgpx.data.Units
import io.github.philtomlinson.fitgpx.ui.Format
import io.github.philtomlinson.fitgpx.ui.components.TrackPreview
import io.github.philtomlinson.fitgpx.ui.components.sportIcon
import io.github.philtomlinson.fitgpx.ui.theme.FitGpxTheme

@Composable
fun ActivityCard(
    item: QueueItem,
    units: Units,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = item.summary
    val enabled = item.canConvert
    var menu by remember { mutableStateOf(false) }
    ElevatedCard(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            // Thumbnail: route shape, or sport icon when there is no route.
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                val preview = summary?.preview
                if (!preview.isNullOrEmpty()) {
                    TrackPreview(preview, Modifier.size(64.dp), strokeWidth = 2.dp, showEndpoints = false)
                } else {
                    Icon(
                        if (item.status is ItemStatus.NoGps) Icons.Filled.GpsOff else sportIcon(summary?.sport),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                }
                if (summary != null && summary.preview.isNotEmpty()) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(4.dp).size(22.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(sportIcon(summary.sport), null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    summary?.title ?: item.source.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = listOfNotNull(
                    summary?.startTime?.let { Format.dateTime(it, summary.utcOffsetSeconds) },
                    summary?.device,
                ).joinToString(" · ").ifEmpty { item.source.containerName }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (summary != null && enabled) {
                    Spacer(Modifier.size(6.dp))
                    val metrics = listOfNotNull(
                        Format.distance(summary.distanceMeters, units),
                        (summary.movingMillis?.takeIf { it > 0 } ?: summary.elapsedMillis)?.let { Format.duration(it) },
                        summary.elevationGain?.takeIf { it >= 1 }?.let { "↑ " + Format.elevation(it, units) },
                    )
                    Text(metrics.joinToString("  ·  "), style = MaterialTheme.typography.labelLarge)
                }
                Status(item)
                val badges = buildList {
                    if (item.edit?.isTrimmed == true) add(Badge(stringResource(R.string.badge_trimmed), Icons.Filled.ContentCut, MaterialTheme.colorScheme.tertiary))
                    if (summary?.recovered == true) add(Badge(stringResource(R.string.badge_recovered), Icons.Filled.Healing, FitGpxTheme.colors.warning))
                    if (summary?.isCourse == true) add(Badge(stringResource(R.string.badge_course), null, MaterialTheme.colorScheme.secondary))
                }
                if (badges.isNotEmpty()) {
                    Spacer(Modifier.size(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        badges.forEach { BadgeChip(it) }
                    }
                }
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more), Modifier.size(20.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (enabled) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_edit)) },
                            leadingIcon = { Icon(Icons.Filled.ContentCut, null) },
                            onClick = { menu = false; onClick() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_remove)) },
                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        onClick = { menu = false; onRemove() },
                    )
                }
            }
        }
    }
}

private data class Badge(val text: String, val icon: ImageVector?, val color: Color)

@Composable
private fun BadgeChip(b: Badge) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(b.color.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (b.icon != null) {
            Icon(b.icon, null, tint = b.color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(b.text, style = MaterialTheme.typography.labelSmall, color = b.color)
    }
}

@Composable
private fun Status(item: QueueItem) {
    val (icon, text, color) = when (val s = item.status) {
        ItemStatus.Reading, ItemStatus.Ready -> return
        ItemStatus.Converting -> {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.status_converting), style = MaterialTheme.typography.bodySmall)
            }
            return
        }
        ItemStatus.NoGps -> Triple(Icons.Filled.GpsOff, stringResource(R.string.status_no_gps), MaterialTheme.colorScheme.onSurfaceVariant)
        is ItemStatus.Failed -> Triple(Icons.Filled.ErrorOutline, stringResource(R.string.status_failed, s.message), MaterialTheme.colorScheme.error)
        is ItemStatus.ConvertFailed -> Triple(Icons.Filled.ErrorOutline, stringResource(R.string.status_convert_failed, s.message), MaterialTheme.colorScheme.error)
        is ItemStatus.Done -> Triple(Icons.Filled.CheckCircle, stringResource(R.string.status_converted, s.fileName), FitGpxTheme.colors.success)
    }
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
