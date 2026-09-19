package com.tokenwatch.feature.settings

import androidx.lifecycle.compose.LifecycleResumeEffect
import android.provider.Settings
import android.os.PowerManager
import android.net.Uri
import android.content.Intent
import com.tokenwatch.core.ui.components.LocalBottomBarPadding
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.tokenwatch.core.ui.components.TopLevelBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tokenwatch.BuildConfig
import com.tokenwatch.core.ui.components.InfoNote
import com.tokenwatch.core.ui.components.LoadingState
import com.tokenwatch.core.ui.components.SectionCard
import com.tokenwatch.domain.model.AppSettings
import com.tokenwatch.domain.model.ThemeMode
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopLevelBar("Settings") },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val current = settings
        if (current == null) {
            LoadingState(Modifier.padding(padding))
        } else {
            SettingsBody(current, viewModel, Modifier.padding(padding))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsBody(settings: AppSettings, vm: SettingsViewModel, modifier: Modifier) {
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setUsageAlerts(granted)
    }
    val enableAlerts = { enabled: Boolean ->
        val needsPermission = enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.setUsageAlerts(enabled)
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + LocalBottomBarPadding.current),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard(title = "Theme") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = settings.themeMode == mode,
                            onClick = { vm.setTheme(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                        ) { Text(mode.label) }
                    }
                }
            }
        }

        item {
            SectionCard(title = "Background refresh") {
                val enabled = settings.refreshIntervalMinutes > 0
                SwitchRow(
                    "Refresh in the background",
                    if (enabled) "Every ${minutesLabel(settings.refreshIntervalMinutes)}" else "Off",
                    enabled,
                ) { on -> vm.setRefreshInterval(if (on) DEFAULT_REFRESH_MINUTES else 0) }
                if (enabled) RefreshIntervalSlider(settings.refreshIntervalMinutes, vm::setRefreshInterval)
                InfoNote(
                    "Re-reads each connected provider's usage page using the sign-in saved in the app, " +
                        "even when the app is closed. Android may run it later than set to save battery, " +
                        "especially at short intervals.",
                )
                if (enabled) BatteryOptimizationHint()
            }
        }

        item {
            SectionCard(title = "Usage alerts") {
                SwitchRow("Notify near usage limits", "When a session or weekly quota reaches the limit below", settings.usageAlertsEnabled, enableAlerts)
                if (settings.usageAlertsEnabled) {
                    LabeledGroup("Session limit") {
                        ChoiceChips(ALERT_OPTIONS, settings.sessionAlertPercent, { "$it%" }, vm::setSessionAlert)
                    }
                    LabeledGroup("Weekly limit") {
                        ChoiceChips(ALERT_OPTIONS, settings.weeklyAlertPercent, { "$it%" }, vm::setWeeklyAlert)
                    }
                    if (settings.refreshIntervalMinutes == 0) {
                        InfoNote("Alerts are checked whenever usage is read. Turn on background refresh to get them while the app is closed.")
                    }
                }
            }
        }

        item {
            SectionCard(title = "Your data") {
                OutlinedButton(
                    onClick = { confirm = "history" },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                ) { Text("Delete cached usage") }
            }
        }

        item {
            Text(
                "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }

    when (confirm) {
        "history" -> ConfirmDialog(
            title = "Delete cached usage?",
            text = "Usage stored on this device and all web data in the app, including provider sign-ins, " +
                "are removed. You'll need to sign in to each provider again.",
            confirmLabel = "Delete",
            onConfirm = { confirm = null; vm.deleteLocalHistory() },
            onDismiss = { confirm = null },
        )
    }
}

/**
 * While Android still battery-optimizes the app, background refresh gets delayed or skipped when the phone is idle.
 * Play doesn't allow asking for the exemption directly, so this opens the app's settings, where battery use can be
 * set to Unrestricted. Hidden once that's done; rechecked when returning to the app.
 */
@Composable
private fun BatteryOptimizationHint() {
    val context = LocalContext.current
    val power = remember { context.getSystemService(PowerManager::class.java) }
    var unrestricted by remember { mutableStateOf(power.isIgnoringBatteryOptimizations(context.packageName)) }
    LifecycleResumeEffect(Unit) {
        unrestricted = power.isIgnoringBatteryOptimizations(context.packageName)
        onPauseOrDispose {}
    }
    if (unrestricted) return
    InfoNote("Battery optimization is on for TokenWatch, so refreshes can be late or skipped while the phone is idle. Set battery use to Unrestricted to keep them on time.")
    OutlinedButton(
        onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Open battery settings") }
}

/** A small label with its control directly under it, so the pair reads as one setting. */
@Composable
private fun LabeledGroup(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

/** Same full-width segmented control as Theme; no checkmark, so five options fit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceChips(options: List<Int>, selected: Int, label: (Int) -> String, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = selected == option,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {},
            ) { Text(label(option), maxLines = 1) }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, onValueChange = onChange, role = Role.Switch),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

private const val DEFAULT_REFRESH_MINUTES = 15
private const val MAX_REFRESH_MINUTES = 30
private val ALERT_OPTIONS = listOf(50, 75, 80, 90, 95)

private fun minutesLabel(minutes: Int) = if (minutes == 1) "1 minute" else "$minutes minutes"

/** 1 to 30 minutes in 1-minute steps. Saves when the drag ends, so each step doesn't reschedule. */
@Composable
private fun RefreshIntervalSlider(minutes: Int, onChange: (Int) -> Unit) {
    var value by remember(minutes) { mutableFloatStateOf(minutes.toFloat()) }
    Column {
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChange(value.roundToInt()) },
            valueRange = 1f..MAX_REFRESH_MINUTES.toFloat(),
            steps = MAX_REFRESH_MINUTES - 2,
            // Whole-minute steps without 29 tick dots along the track.
            colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
            modifier = Modifier.semantics { stateDescription = "Every ${minutesLabel(value.roundToInt())}" },
        )
        Row {
            Text("1 min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("$MAX_REFRESH_MINUTES min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
