/*
 * SPDX-FileCopyrightText: 2026 FitGPX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.philtomlinson.fitgpx.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.philtomlinson.fitgpx.R
import io.github.philtomlinson.fitgpx.core.FitToGpx
import io.github.philtomlinson.fitgpx.data.AppSettings
import io.github.philtomlinson.fitgpx.data.ConflictPolicy
import io.github.philtomlinson.fitgpx.data.ThemeMode
import io.github.philtomlinson.fitgpx.data.Units
import io.github.philtomlinson.fitgpx.ui.Format
import io.github.philtomlinson.fitgpx.ui.components.SectionHeader
import java.time.ZoneOffset
import kotlin.math.roundToInt

private enum class Dialog { NAME, CONFLICT, SIMPLIFY, PRECISION, HIDE_START, HIDE_END, THEME, UNITS }

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenZones: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val una by viewModel.unaStorage.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshUnaStorage() }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(viewModel::setOutputFolder) }
    SettingsContent(
        s = s,
        update = viewModel::update,
        onBack = onBack,
        onPickFolder = { pickFolder.launch(null) },
        onClearFolder = viewModel::clearOutputFolder,
        onOpenZones = onOpenZones,
        onOpenAbout = onOpenAbout,
        unaFiles = una.first,
        unaBytes = una.second,
        onForgetUna = viewModel::forgetUna,
        onClearUna = viewModel::clearUnaCopies,
    )
}

@Composable
fun SettingsContent(
    s: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
    onPickFolder: () -> Unit,
    onClearFolder: () -> Unit,
    onOpenZones: () -> Unit,
    onOpenAbout: () -> Unit,
    unaFiles: Int = 0,
    unaBytes: Long = 0,
    onForgetUna: () -> Unit = {},
    onClearUna: () -> Unit = {},
) {
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.action_settings)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            SectionHeader(stringResource(R.string.settings_output))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_folder)) },
                supportingContent = { Text(s.outputFolderName ?: stringResource(R.string.settings_folder_none)) },
                trailingContent = if (s.outputFolderUri != null) {
                    { IconButton(onClick = onClearFolder) { Icon(Icons.Filled.Clear, stringResource(R.string.settings_folder_clear)) } }
                } else {
                    null
                },
                modifier = Modifier.clickable(onClick = onPickFolder),
            )
            ClickRow(stringResource(R.string.settings_file_names), s.nameTemplate) { dialog = Dialog.NAME }
            ClickRow(
                stringResource(R.string.settings_conflict),
                stringResource(if (s.conflictPolicy == ConflictPolicy.KEEP_BOTH) R.string.settings_conflict_keep else R.string.settings_conflict_replace),
            ) { dialog = Dialog.CONFLICT }

            SectionHeader(stringResource(R.string.settings_content))
            SwitchRow(stringResource(R.string.settings_elevation), null, s.includeElevation) { v -> update { it.copy(includeElevation = v) } }
            SwitchRow(stringResource(R.string.settings_time), stringResource(R.string.settings_time_hint), s.includeTime) { v -> update { it.copy(includeTime = v) } }
            SwitchRow(stringResource(R.string.settings_hr), null, s.includeHeartRate) { v -> update { it.copy(includeHeartRate = v) } }
            SwitchRow(stringResource(R.string.settings_cadence), null, s.includeCadence) { v -> update { it.copy(includeCadence = v) } }
            SwitchRow(stringResource(R.string.settings_power), null, s.includePower) { v -> update { it.copy(includePower = v) } }
            SwitchRow(stringResource(R.string.settings_temperature), null, s.includeTemperature) { v -> update { it.copy(includeTemperature = v) } }
            SwitchRow(stringResource(R.string.settings_course_points), stringResource(R.string.settings_course_points_hint), s.includeCoursePoints) { v -> update { it.copy(includeCoursePoints = v) } }
            SwitchRow(stringResource(R.string.settings_laps), stringResource(R.string.settings_laps_hint), s.includeLaps) { v -> update { it.copy(includeLaps = v) } }

            SectionHeader(stringResource(R.string.settings_processing))
            SwitchRow(stringResource(R.string.settings_spikes), stringResource(R.string.settings_spikes_hint), s.removeSpikes) { v -> update { it.copy(removeSpikes = v) } }
            SwitchRow(stringResource(R.string.settings_split_pauses), stringResource(R.string.settings_split_pauses_hint), s.splitAtPauses) { v -> update { it.copy(splitAtPauses = v) } }
            SwitchRow(stringResource(R.string.settings_split_sessions), stringResource(R.string.settings_split_sessions_hint), s.splitSessions) { v -> update { it.copy(splitSessions = v) } }
            ClickRow(
                stringResource(R.string.settings_simplify),
                if (s.simplifyMeters == 0) stringResource(R.string.settings_simplify_off) else stringResource(R.string.settings_simplify_value, s.simplifyMeters),
            ) { dialog = Dialog.SIMPLIFY }
            ClickRow(stringResource(R.string.settings_precision), precisionLabel(s.coordinateDecimals)) { dialog = Dialog.PRECISION }

            SectionHeader(stringResource(R.string.settings_privacy))
            ClickRow(
                stringResource(R.string.settings_zones),
                if (s.privacyZones.isEmpty()) stringResource(R.string.settings_zones_none) else pluralStringResource(R.plurals.settings_zones_count, s.privacyZones.size, s.privacyZones.size),
                onClick = onOpenZones,
            )
            ClickRow(stringResource(R.string.settings_hide_start), hideLabel(s.hideStartMeters, s.units)) { dialog = Dialog.HIDE_START }
            ClickRow(stringResource(R.string.settings_hide_end), hideLabel(s.hideEndMeters, s.units)) { dialog = Dialog.HIDE_END }

            SectionHeader(stringResource(R.string.settings_una))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_una_watch)) },
                supportingContent = { Text(s.unaName ?: stringResource(R.string.settings_una_none)) },
                trailingContent = if (s.unaAddress != null) {
                    { IconButton(onClick = onForgetUna) { Icon(Icons.Filled.Clear, stringResource(R.string.settings_una_forget)) } }
                } else {
                    null
                },
            )
            if (unaFiles > 0) {
                val size = android.text.format.Formatter.formatShortFileSize(LocalContext.current, unaBytes)
                ClickRow(
                    stringResource(R.string.settings_una_storage),
                    pluralStringResource(R.plurals.settings_una_storage_value, unaFiles, unaFiles, size),
                    onClick = onClearUna,
                )
            }

            SectionHeader(stringResource(R.string.settings_appearance))
            ClickRow(stringResource(R.string.settings_theme), stringResource(themeLabel(s.themeMode))) { dialog = Dialog.THEME }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchRow(stringResource(R.string.settings_dynamic), stringResource(R.string.settings_dynamic_hint), s.dynamicColor) { v -> update { it.copy(dynamicColor = v) } }
            }
            ClickRow(stringResource(R.string.settings_units), stringResource(if (s.units == Units.METRIC) R.string.settings_units_metric else R.string.settings_units_imperial)) { dialog = Dialog.UNITS }
            SwitchRow(stringResource(R.string.settings_map), stringResource(R.string.settings_map_hint), s.showMap) { v -> update { it.copy(showMap = v) } }

            SectionHeader(stringResource(R.string.action_about))
            ClickRow(stringResource(R.string.about_title), stringResource(R.string.about_subtitle), onClick = onOpenAbout)
            Spacer(Modifier.height(24.dp))
        }
    }

    when (dialog) {
        Dialog.NAME -> NameTemplateDialog(s.nameTemplate, onDismiss = { dialog = null }) { t -> update { it.copy(nameTemplate = t) }; dialog = null }
        Dialog.CONFLICT -> ChoiceDialog(
            stringResource(R.string.settings_conflict),
            listOf(ConflictPolicy.KEEP_BOTH to stringResource(R.string.settings_conflict_keep), ConflictPolicy.OVERWRITE to stringResource(R.string.settings_conflict_replace)),
            s.conflictPolicy, { dialog = null },
        ) { v -> update { it.copy(conflictPolicy = v) } }
        Dialog.SIMPLIFY -> ChoiceDialog(
            stringResource(R.string.settings_simplify),
            listOf(0, 1, 3, 5, 10).map { m -> m to if (m == 0) stringResource(R.string.settings_simplify_off) else stringResource(R.string.settings_simplify_value, m) },
            s.simplifyMeters, { dialog = null },
            footer = stringResource(R.string.settings_simplify_hint),
        ) { v -> update { it.copy(simplifyMeters = v) } }
        Dialog.PRECISION -> ChoiceDialog(
            stringResource(R.string.settings_precision),
            listOf(7, 6, 5).map { it to precisionLabel(it) },
            s.coordinateDecimals, { dialog = null },
        ) { v -> update { it.copy(coordinateDecimals = v) } }
        Dialog.HIDE_START -> DistanceDialog(stringResource(R.string.settings_hide_start), s.hideStartMeters, s.units, { dialog = null }) { v -> update { it.copy(hideStartMeters = v) } }
        Dialog.HIDE_END -> DistanceDialog(stringResource(R.string.settings_hide_end), s.hideEndMeters, s.units, { dialog = null }) { v -> update { it.copy(hideEndMeters = v) } }
        Dialog.THEME -> ChoiceDialog(
            stringResource(R.string.settings_theme),
            ThemeMode.entries.map { it to stringResource(themeLabel(it)) },
            s.themeMode, { dialog = null },
        ) { v -> update { it.copy(themeMode = v) } }
        Dialog.UNITS -> ChoiceDialog(
            stringResource(R.string.settings_units),
            listOf(Units.METRIC to stringResource(R.string.settings_units_metric), Units.IMPERIAL to stringResource(R.string.settings_units_imperial)),
            s.units, { dialog = null },
        ) { v -> update { it.copy(units = v) } }
        null -> Unit
    }
}

private fun themeLabel(t: ThemeMode) = when (t) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

@Composable
private fun precisionLabel(decimals: Int): String = when (decimals) {
    7 -> stringResource(R.string.settings_precision_7)
    6 -> stringResource(R.string.settings_precision_6)
    else -> stringResource(R.string.settings_precision_5)
}

@Composable
private fun hideLabel(meters: Int, units: Units): String =
    if (meters == 0) stringResource(R.string.editor_hide_off) else Format.distance(meters.toDouble(), units)

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}

@Composable
fun ClickRow(title: String, value: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = value?.let { { Text(it) } },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onDismiss: () -> Unit,
    footer: String? = null,
    onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(
                            selected = value == selected,
                            role = Role.RadioButton,
                            onClick = { onSelect(value); onDismiss() },
                        ).padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Spacer(Modifier.padding(start = 12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (footer != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(footer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun DistanceDialog(title: String, meters: Int, units: Units, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var value by remember { mutableIntStateOf(meters) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    if (value == 0) stringResource(R.string.editor_hide_off) else Format.distance(value.toDouble(), units),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Slider(value = value.toFloat(), onValueChange = { value = (it / 50).roundToInt() * 50 }, valueRange = 0f..2000f, steps = 39)
                Text(stringResource(R.string.settings_hide_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value); onDismiss() }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun NameTemplateDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    // Preview with a fixed example so the user sees what each token produces.
    val example = remember(text) {
        FitToGpx.fileName(text.ifBlank { FitToGpx.DEFAULT_NAME_TEMPLATE }, "2025-09-27-08-15-02.fit", ExampleActivity, ZoneOffset.UTC)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_file_names)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FitToGpx.TEMPLATE_TOKENS.forEach { token ->
                        AssistChip(onClick = { text += token }, label = { Text(token) })
                    }
                }
                Text(stringResource(R.string.settings_file_names_preview, example), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.settings_file_names_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text.ifBlank { FitToGpx.DEFAULT_NAME_TEMPLATE }) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
