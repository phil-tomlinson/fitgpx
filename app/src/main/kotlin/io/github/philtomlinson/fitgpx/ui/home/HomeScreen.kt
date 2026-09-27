/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.ui.home

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.philtomlinson.fitgpx.R
import io.github.philtomlinson.fitgpx.data.AppSettings
import io.github.philtomlinson.fitgpx.data.Destination
import io.github.philtomlinson.fitgpx.data.ItemStatus
import io.github.philtomlinson.fitgpx.data.LatLon
import io.github.philtomlinson.fitgpx.data.Progress
import io.github.philtomlinson.fitgpx.data.QueueItem
import io.github.philtomlinson.fitgpx.ui.Format
import io.github.philtomlinson.fitgpx.ui.components.TrackPreview
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenItem: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Which activities the save sheet is for: null = sheet hidden, empty = everything convertible.
    var sheetFor by remember { mutableStateOf<Set<Long>?>(null) }
    // Remembered across the system folder/file picker, which may recreate the activity.
    var pendingIds by rememberSaveable { mutableStateOf<List<Long>?>(null) }
    val exportRequest by viewModel.exportRequest.collectAsStateWithLifecycle()
    LaunchedEffect(exportRequest) {
        exportRequest?.let {
            sheetFor = setOf(it)
            viewModel.consumeExportRequest()
        }
    }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { viewModel.import(it) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(viewModel::importFolder) }
    val pickOutputFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        it?.let { uri -> viewModel.chooseFolderAndConvert(uri, pendingIds) }
    }
    val createZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
        it?.let { uri -> viewModel.convert(Destination.Zip(uri), pendingIds) }
    }

    // Some pickers grey out .fit files when filtering by type, so accept everything and detect by content.
    val openFiles = { pickFiles.launch(arrayOf("*/*")) }
    val openFolder = { pickFolder.launch(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.Converted -> {
                    val r = event.result
                    if (r.destination == Destination.Share && r.shareUris.isNotEmpty()) {
                        val intent = Intent(if (r.shareUris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "application/gpx+xml"
                            if (r.shareUris.size == 1) putExtra(Intent.EXTRA_STREAM, r.shareUris[0])
                            else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(r.shareUris))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            context.startActivity(Intent.createChooser(intent, resources.getString(R.string.share_chooser)))
                        } catch (_: ActivityNotFoundException) {
                            snackbar.showSnackbar(resources.getString(R.string.share_no_app))
                        }
                    } else if (r.converted > 0) {
                        val msg = when (r.destination) {
                            is Destination.Folder -> resources.getQuantityString(R.plurals.result_saved_folder, r.converted, r.converted, r.folderName ?: "")
                            is Destination.Zip -> resources.getQuantityString(R.plurals.result_saved_zip, r.converted, r.converted)
                            Destination.Share -> ""
                        }
                        snackbar.showSnackbar(msg)
                    }
                    if (r.failed > 0) snackbar.showSnackbar(resources.getQuantityString(R.plurals.result_failed, r.failed, r.failed))
                }
                HomeEvent.NothingFound -> snackbar.showSnackbar(resources.getString(R.string.result_nothing_found))
                HomeEvent.FolderUnavailable -> pickOutputFolder.launch(null)
            }
        }
    }

    HomeContent(
        items = items,
        progress = progress,
        settings = settings,
        snackbar = snackbar,
        onSelectFiles = openFiles,
        onSelectFolder = openFolder,
        onOpenItem = onOpenItem,
        onRemoveItem = viewModel::remove,
        onClear = viewModel::clear,
        onClearConverted = viewModel::clearConverted,
        onConvert = { sheetFor = emptySet() },
        onCancel = viewModel::cancel,
        onOpenSettings = onOpenSettings,
        onOpenAbout = onOpenAbout,
    )

    sheetFor?.let { target ->
        val ids: List<Long>? = target.toList().ifEmpty { null }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        fun dismissThen(action: () -> Unit) {
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                sheetFor = null
                pendingIds = ids
                action()
            }
        }
        val count = if (ids == null) items.count { it.canConvert } else ids.size
        val single = ids?.singleOrNull()?.let { id -> items.firstOrNull { it.id == id } }
        ModalBottomSheet(onDismissRequest = { sheetFor = null }, sheetState = sheetState) {
            SaveOptions(
                count = count,
                folderName = settings.outputFolderName.takeIf { settings.outputFolderUri != null },
                onSaveToFolder = { dismissThen { viewModel.convertToSavedFolder(ids) } },
                onChooseFolder = { dismissThen { pickOutputFolder.launch(null) } },
                onSaveZip = {
                    val name = single?.let { it.source.name.substringBeforeLast('.') + ".zip" } ?: "fitgpx-${LocalDate.now()}.zip"
                    dismissThen { createZip.launch(name) }
                },
                onShare = { dismissThen { viewModel.convert(Destination.Share, ids) } },
            )
        }
    }
}

@Composable
fun HomeContent(
    items: List<QueueItem>,
    progress: Progress,
    settings: AppSettings,
    snackbar: SnackbarHostState,
    onSelectFiles: () -> Unit,
    onSelectFolder: () -> Unit,
    onOpenItem: (Long) -> Unit,
    onRemoveItem: (Long) -> Unit,
    onClear: () -> Unit,
    onClearConverted: () -> Unit,
    onConvert: () -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var menu by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                scrollBehavior = scrollBehavior,
                actions = {
                    if (items.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { addMenu = true }) { Icon(Icons.Filled.Add, stringResource(R.string.action_add)) }
                            DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_select_files)) },
                                    leadingIcon = { Icon(Icons.Filled.UploadFile, null) },
                                    onClick = { addMenu = false; onSelectFiles() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_select_folder)) },
                                    leadingIcon = { Icon(Icons.Filled.FolderOpen, null) },
                                    onClick = { addMenu = false; onSelectFolder() },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.action_settings)) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (items.any { it.status is ItemStatus.Done }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.action_clear_converted)) }, onClick = { menu = false; onClearConverted() })
                            }
                            if (items.isNotEmpty()) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.action_clear_all)) }, onClick = { menu = false; onClear() })
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_about)) },
                                leadingIcon = { Icon(Icons.Filled.Info, null) },
                                onClick = { menu = false; onOpenAbout() },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (items.isNotEmpty() || progress != Progress.Idle) {
                ActionBar(items, progress, onConvert, onCancel)
            }
        },
    ) { padding ->
        if (items.isEmpty() && progress == Progress.Idle) {
            EmptyState(Modifier.padding(padding), onSelectFiles, onSelectFolder)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "summary") { SummaryHeader(items, settings) }
                items(items, key = { it.id }) { item ->
                    ActivityCard(
                        item = item,
                        units = settings.units,
                        onClick = { onOpenItem(item.id) },
                        onRemove = { onRemoveItem(item.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryHeader(items: List<QueueItem>, settings: AppSettings) {
    val convertible = items.filter { it.canConvert }
    val distance = convertible.sumOf { it.summary?.distanceMeters ?: 0.0 }
    val problems = items.count { it.status is ItemStatus.NoGps || it.status is ItemStatus.Failed || it.status is ItemStatus.ConvertFailed }
    Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
        Text(
            pluralStringResource(R.plurals.home_summary, items.size, items.size, Format.distance(distance, settings.units)),
            style = MaterialTheme.typography.titleMedium,
        )
        if (problems > 0) {
            Text(
                pluralStringResource(R.plurals.home_problems, problems, problems),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionBar(items: List<QueueItem>, progress: Progress, onConvert: () -> Unit, onCancel: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            AnimatedContent(targetState = progress, contentKey = { it::class }, label = "action-bar") { p ->
                when (p) {
                    is Progress.Converting -> Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.home_converting, (p.done + 1).coerceAtMost(p.total), p.total), style = MaterialTheme.typography.titleSmall)
                                if (p.current.isNotEmpty()) {
                                    Text(p.current, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { if (p.total == 0) 0f else p.done.toFloat() / p.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    is Progress.Importing -> Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.home_importing, p.current),
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    Progress.Idle -> {
                        val n = items.count { it.canConvert }
                        Button(onClick = onConvert, enabled = n > 0, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(pluralStringResource(R.plurals.home_convert, n, n))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier, onSelectFiles: () -> Unit, onSelectFolder: () -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(168.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            TrackPreview(SAMPLE_ROUTE, Modifier.size(112.dp), strokeWidth = 4.dp)
        }
        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onSelectFiles, modifier = Modifier.widthIn(min = 240.dp).height(52.dp)) {
            Icon(Icons.Filled.UploadFile, null, Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.action_select_files))
        }
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = onSelectFolder, modifier = Modifier.widthIn(min = 240.dp).height(52.dp)) {
            Icon(Icons.Filled.FolderOpen, null, Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.action_select_folder))
        }
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(R.string.home_empty_supported),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.home_empty_private), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SaveOptions(
    count: Int,
    folderName: String?,
    onSaveToFolder: () -> Unit,
    onChooseFolder: () -> Unit,
    onSaveZip: () -> Unit,
    onShare: () -> Unit,
) {
    Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
        Text(
            pluralStringResource(R.plurals.save_sheet_title, count, count),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        val colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        if (folderName != null) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.save_to_folder, folderName)) },
                supportingContent = { Text(stringResource(R.string.save_to_folder_hint)) },
                leadingContent = { Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickableRow(onSaveToFolder),
                colors = colors,
            )
        }
        ListItem(
            headlineContent = { Text(stringResource(if (folderName == null) R.string.save_choose_folder else R.string.save_other_folder)) },
            supportingContent = { Text(stringResource(R.string.save_choose_folder_hint)) },
            leadingContent = { Icon(Icons.Filled.CreateNewFolder, null) },
            modifier = Modifier.clickableRow(onChooseFolder),
            colors = colors,
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.save_zip)) },
            supportingContent = { Text(stringResource(R.string.save_zip_hint)) },
            leadingContent = { Icon(Icons.Filled.Archive, null) },
            modifier = Modifier.clickableRow(onSaveZip),
            colors = colors,
        )
        HorizontalDivider(Modifier.padding(vertical = 4.dp, horizontal = 16.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.save_share)) },
            supportingContent = { Text(stringResource(R.string.save_share_hint)) },
            leadingContent = { Icon(Icons.AutoMirrored.Filled.Send, null) },
            modifier = Modifier.clickableRow(onShare),
            colors = colors,
        )
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit) = this.clickable(onClick = onClick)

/** A decorative winding route for the empty state. */
internal val SAMPLE_ROUTE: List<LatLon> = run {
    val pts = mutableListOf<LatLon>()
    for (i in 0..120) {
        val t = i / 120.0
        val lat = 51.0 + t * 0.02 + 0.004 * kotlin.math.sin(t * 9.0)
        val lon = -114.0 + t * 0.03 + 0.006 * kotlin.math.cos(t * 6.5) - 0.006
        pts += LatLon(lat, lon)
    }
    pts
}
