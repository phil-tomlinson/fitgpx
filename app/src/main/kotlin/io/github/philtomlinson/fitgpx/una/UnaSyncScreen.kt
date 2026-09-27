/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.una

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.philtomlinson.fitgpx.R
import io.github.philtomlinson.fitgpx.ui.theme.FitGpxTheme

enum class BluetoothReadiness { UNSUPPORTED, NEEDS_PERMISSION, OFF, READY }

internal fun bluetoothPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

private fun readiness(context: Context, devices: UnaDevices): BluetoothReadiness = when {
    !devices.isSupported -> BluetoothReadiness.UNSUPPORTED
    bluetoothPermissions().any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED } ->
        BluetoothReadiness.NEEDS_PERMISSION
    !devices.isEnabled -> BluetoothReadiness.OFF
    else -> BluetoothReadiness.READY
}

@Composable
fun UnaSyncScreen(viewModel: UnaSyncViewModel, devices: UnaDevices, onBack: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val found by viewModel.devices.collectAsStateWithLifecycle()
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) } // re-evaluate readiness after returning to the screen
    val ready = remember(tick) { readiness(context, devices) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    LaunchedEffect(ready) { if (ready == BluetoothReadiness.READY) viewModel.refresh() }

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { tick++ }

    UnaSyncContent(
        state = state,
        readiness = ready,
        devices = found,
        scanning = scanning,
        remembered = settings.unaAddress?.let { addr -> found.firstOrNull { it.address == addr } ?: WatchDevice(addr, settings.unaName ?: addr, true) },
        demoAvailable = viewModel.demoAvailable,
        onBack = { viewModel.reset(); onBack() },
        onAllowBluetooth = { askPermission.launch(bluetoothPermissions()) },
        onEnableBluetooth = { enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
        onSync = viewModel::syncWith,
        onDemo = viewModel::syncDemo,
        onCancel = viewModel::cancel,
        onAddAll = { viewModel.addAllSynced(); viewModel.reset(); onBack() },
        onDone = { viewModel.reset(); onBack() },
        onAgain = viewModel::reset,
        onShareLog = {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "FitGPX UNA sync log")
                putExtra(Intent.EXTRA_TEXT, viewModel.log)
            }
            context.startActivity(Intent.createChooser(send, null))
        },
    )
}

@Composable
fun UnaSyncContent(
    state: SyncState,
    readiness: BluetoothReadiness,
    devices: List<WatchDevice>,
    scanning: Boolean,
    remembered: WatchDevice?,
    demoAvailable: Boolean,
    onBack: () -> Unit,
    onAllowBluetooth: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onSync: (WatchDevice) -> Unit,
    onDemo: () -> Unit,
    onCancel: () -> Unit,
    onAddAll: () -> Unit,
    onDone: () -> Unit,
    onAgain: () -> Unit,
    onShareLog: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.una_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header()
            when (state) {
                is SyncState.Done -> DoneCard(state, onDone, onAddAll, onAgain, onShareLog)
                is SyncState.Failed -> FailedCard(state.message, remembered, onSync, onAgain, onShareLog)
                SyncState.Idle -> PickCard(readiness, devices, scanning, remembered, onAllowBluetooth, onEnableBluetooth, onSync)
                else -> ProgressCard(state, onCancel)
            }
            if (demoAvailable && !state.busy) {
                TextButton(onClick = onDemo, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Icon(Icons.Filled.Science, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.una_demo))
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Watch, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(stringResource(R.string.una_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PickCard(
    readiness: BluetoothReadiness,
    devices: List<WatchDevice>,
    scanning: Boolean,
    remembered: WatchDevice?,
    onAllowBluetooth: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onSync: (WatchDevice) -> Unit,
) {
    when (readiness) {
        BluetoothReadiness.UNSUPPORTED -> Message(Icons.Filled.BluetoothDisabled, stringResource(R.string.una_unsupported))
        BluetoothReadiness.NEEDS_PERMISSION -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Message(Icons.Filled.Bluetooth, stringResource(R.string.una_permission_why))
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAllowBluetooth) { Text(stringResource(R.string.una_allow_bluetooth)) }
        }
        BluetoothReadiness.OFF -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Message(Icons.Filled.BluetoothDisabled, stringResource(R.string.una_bluetooth_off))
            Spacer(Modifier.height(12.dp))
            Button(onClick = onEnableBluetooth) { Text(stringResource(R.string.una_turn_on_bluetooth)) }
        }
        BluetoothReadiness.READY -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (remembered != null) {
                Button(onClick = { onSync(remembered) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(Icons.Filled.Sync, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.una_sync_with, remembered.name))
                }
            }
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.una_watches), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (scanning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                    val colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    devices.filter { it.address != remembered?.address }.forEach { d ->
                        ListItem(
                            headlineContent = { Text(d.name) },
                            supportingContent = { Text(stringResource(if (d.paired) R.string.una_paired else R.string.una_nearby)) },
                            leadingContent = { Icon(Icons.Filled.Watch, null) },
                            colors = colors,
                            modifier = Modifier.clickable { onSync(d) },
                        )
                    }
                    if (devices.none { it.address != remembered?.address }) {
                        Text(
                            stringResource(if (scanning) R.string.una_searching else R.string.una_none_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
            Text(stringResource(R.string.una_tips), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProgressCard(state: SyncState, onCancel: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val title = when (state) {
                is SyncState.Connecting -> when (state.stage) {
                    ConnectStage.PAIRING -> stringResource(R.string.una_pairing)
                    ConnectStage.CONNECTING -> stringResource(R.string.una_connecting)
                    ConnectStage.PREPARING -> stringResource(R.string.una_preparing)
                }
                is SyncState.Scanning -> stringResource(R.string.una_scanning)
                is SyncState.Downloading -> stringResource(R.string.una_downloading, state.index, state.count)
                SyncState.Importing -> stringResource(R.string.una_importing)
                else -> ""
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
            when (state) {
                is SyncState.Downloading -> {
                    LinearProgressIndicator(
                        progress = { if (state.total > 0) state.received.toFloat() / state.total else 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(state.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is SyncState.Scanning -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.app?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                else -> LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (state is SyncState.Connecting && state.stage == ConnectStage.PAIRING) {
                Text(stringResource(R.string.una_pairing_hint), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

@Composable
private fun DoneCard(state: SyncState.Done, onDone: () -> Unit, onAddAll: () -> Unit, onAgain: () -> Unit, onShareLog: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                if (state.nothingOnWatch) Icons.Filled.Info else Icons.Filled.CheckCircle,
                null,
                tint = if (state.nothingOnWatch) MaterialTheme.colorScheme.onSurfaceVariant else FitGpxTheme.colors.success,
                modifier = Modifier.size(40.dp),
            )
            Text(
                when {
                    state.added > 0 -> pluralStringResource(R.plurals.una_added, state.added, state.added)
                    state.nothingOnWatch -> stringResource(R.string.una_no_recordings)
                    else -> stringResource(R.string.una_nothing_new)
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            if (state.alreadySynced > 0) {
                Text(
                    pluralStringResource(R.plurals.una_already, state.alreadySynced, state.alreadySynced),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.failed > 0) {
                Text(
                    pluralStringResource(R.plurals.una_failed_files, state.failed, state.failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(4.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.una_view_activities)) }
            if (state.added == 0 && state.alreadySynced > 0) {
                OutlinedButton(onClick = onAddAll, modifier = Modifier.fillMaxWidth()) {
                    Text(pluralStringResource(R.plurals.una_add_all, state.alreadySynced, state.alreadySynced))
                }
            }
            if (state.nothingOnWatch) {
                Text(
                    stringResource(R.string.una_no_recordings_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Row {
                TextButton(onClick = onAgain) { Text(stringResource(R.string.una_sync_again)) }
                if (state.nothingOnWatch || state.failed > 0) TextButton(onClick = onShareLog) { Text(stringResource(R.string.una_share_log)) }
            }
        }
    }
}

@Composable
private fun FailedCard(message: String, remembered: WatchDevice?, onSync: (WatchDevice) -> Unit, onAgain: () -> Unit, onShareLog: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.una_failed), style = MaterialTheme.typography.titleMedium)
            }
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.una_tips), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.align(Alignment.End)) {
                TextButton(onClick = onShareLog) { Text(stringResource(R.string.una_share_log)) }
                TextButton(onClick = onAgain) { Text(stringResource(R.string.una_choose_watch)) }
                if (remembered != null) Button(onClick = { onSync(remembered) }) { Text(stringResource(R.string.una_retry)) }
            }
        }
    }
}

@Composable
private fun Message(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}
