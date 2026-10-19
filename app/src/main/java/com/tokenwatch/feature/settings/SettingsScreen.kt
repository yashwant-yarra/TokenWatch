package com.tokenwatch.feature.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tokenwatch.BuildConfig
import com.tokenwatch.core.ui.components.Arrive
import com.tokenwatch.core.ui.components.BenchDialog
import com.tokenwatch.core.ui.components.GlassToggle
import com.tokenwatch.core.ui.components.InfoNote
import com.tokenwatch.core.ui.components.LoadingState
import com.tokenwatch.core.ui.components.LocalBottomBarPadding
import com.tokenwatch.core.ui.components.PlateButton
import com.tokenwatch.core.ui.components.PlateStyle
import com.tokenwatch.core.ui.components.SandGlass
import com.tokenwatch.core.ui.components.ScaleSelector
import com.tokenwatch.core.ui.components.ScaleSlider
import com.tokenwatch.core.ui.components.Section
import com.tokenwatch.core.ui.components.TopLevelBar
import com.tokenwatch.core.ui.components.rememberHaptics
import com.tokenwatch.domain.model.AppSettings
import com.tokenwatch.feature.tile.UsageTile
import com.tokenwatch.domain.model.ThemeMode

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
        // Messages arrive as an ink slip, not a platform snackbar.
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Text(
                    data.visuals.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(3.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
        },
    ) { padding ->
        val current = settings
        if (current == null) {
            LoadingState(Modifier.padding(padding))
        } else {
            SettingsBody(current, viewModel, Modifier.padding(padding))
        }
    }
}

@Composable
private fun SettingsBody(settings: AppSettings, vm: SettingsViewModel, modifier: Modifier) {
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setUsageAlerts(granted)
    }
    val resetPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setResetAlerts(granted)
    }
    val needsPermission = { enabled: Boolean ->
        enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    }
    val enableAlerts = { enabled: Boolean ->
        if (needsPermission(enabled)) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.setUsageAlerts(enabled)
    }
    val enableResetAlerts = { enabled: Boolean ->
        if (needsPermission(enabled)) resetPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.setResetAlerts(enabled)
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp + LocalBottomBarPadding.current),
    ) {
        item {
            Arrive(0) {
                Section(title = "Theme") {
                    ScaleSelector(ThemeMode.entries, settings.themeMode, { it.label }, vm::setTheme)
                }
            }
        }

        item {
            Arrive(1) {
                Section(title = "Background refresh") {
                    val enabled = settings.refreshIntervalMinutes > 0
                    ToggleRow(
                        "Refresh in the background",
                        if (enabled) "Every ${minutesLabel(settings.refreshIntervalMinutes)}" else "Off",
                        enabled,
                    ) { on -> vm.setRefreshInterval(if (on) DEFAULT_REFRESH_MINUTES else 0) }
                    if (enabled) RefreshIntervalScale(settings.refreshIntervalMinutes, vm::setRefreshInterval)
                    InfoNote(
                        "Re-reads each connected provider's usage page using the sign-in saved in the app, " +
                            "even when the app is closed. Android may run it later than set to save battery, " +
                            "especially at short intervals.",
                    )
                    if (enabled) BatteryOptimizationHint()
                }
            }
        }

        item {
            Arrive(2) {
                Section(title = "Usage alerts") {
                    ToggleRow(
                        "Notify near usage limits",
                        "When a session or weekly window gets down to the amount left below",
                        settings.usageAlertsEnabled,
                        enableAlerts,
                    )
                    if (settings.usageAlertsEnabled) {
                        AlertLinePreview(settings.sessionAlertPercent, settings.weeklyAlertPercent)
                        LabeledGroup("Session: alert at % left") {
                            ScaleSelector(ALERT_OPTIONS, settings.sessionAlertPercent, { "${100 - it}%" }, vm::setSessionAlert)
                        }
                        LabeledGroup("Weekly: alert at % left") {
                            ScaleSelector(ALERT_OPTIONS, settings.weeklyAlertPercent, { "${100 - it}%" }, vm::setWeeklyAlert)
                        }
                        if (settings.refreshIntervalMinutes == 0) {
                            InfoNote("Alerts are checked whenever usage is read. Turn on background refresh to get them while the app is closed.")
                        }
                    }
                    ToggleRow(
                        "Notify when a limit resets",
                        "Re-reads the provider at each reset time and tells you once the page shows usage is back",
                        settings.resetAlertsEnabled,
                        enableResetAlerts,
                    )
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            item {
                Arrive(3) {
                    Section(title = "Quick Settings") {
                        Text(
                            "A tile with the limit closest to running out. Tap it to re-read.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PlateButton("Add tile", { UsageTile.requestAdd(context) }, Modifier.fillMaxWidth(), style = PlateStyle.OUTLINE)
                    }
                }
            }
        }

        item {
            Arrive(4) {
                Section(title = "Your data") {
                    PlateButton("Delete cached usage", { confirm = "history" }, Modifier.fillMaxWidth(), style = PlateStyle.OUTLINE)
                }
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
        "history" -> BenchDialog(
            title = "Delete cached usage?",
            text = "Usage stored on this device and all web data in the app, including provider sign-ins, " +
                "are removed. You'll need to sign in to each provider again.",
            confirmLabel = "Delete",
            onConfirm = { confirm = null; vm.deleteLocalHistory() },
            onDismiss = { confirm = null },
        )
    }
}

/** Two sample glasses showing where each alert line sits; the lines move as the limits change. */
@Composable
private fun AlertLinePreview(sessionUsed: Int, weeklyUsed: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("Session" to sessionUsed, "Weekly" to weeklyUsed).forEach { (label, used) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SandGlass(
                    leftPercent = 60.0,
                    sand = MaterialTheme.colorScheme.onSurfaceVariant,
                    alertUsedPercent = used,
                    width = 26.dp,
                    height = 48.dp,
                )
                Column {
                    Text(label, style = MaterialTheme.typography.labelLarge)
                    Text(
                        "line at ${100 - used}% left",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
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
    PlateButton(
        "Open battery settings",
        {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
        Modifier.fillMaxWidth(),
        style = PlateStyle.OUTLINE,
    )
}

/** A small label with its control directly under it, so the pair reads as one setting. */
@Composable
private fun LabeledGroup(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

/** The whole row is the toggle (48dp+), with the drawn lever at its end. */
@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptics = rememberHaptics()
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch) { haptics.tick(); onChange(it) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        GlassToggle(checked)
    }
}

private const val DEFAULT_REFRESH_MINUTES = 15
private const val MAX_REFRESH_MINUTES = 30
private val ALERT_OPTIONS = listOf(50, 75, 80, 90, 95)

private fun minutesLabel(minutes: Int) = if (minutes == 1) "1 minute" else "$minutes minutes"

/** 1 to 30 minutes on a ruled scale. Saves when the drag ends, so each step doesn't reschedule. */
@Composable
private fun RefreshIntervalScale(minutes: Int, onChange: (Int) -> Unit) {
    var value by remember(minutes) { mutableIntStateOf(minutes) }
    Column {
        ScaleSlider(
            value = value,
            range = 1..MAX_REFRESH_MINUTES,
            onValueChange = { value = it },
            onValueChangeFinished = { if (value != minutes) onChange(value) },
            stateLabel = { "Every ${minutesLabel(it)}" },
        )
        Row {
            Text("1 min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("$MAX_REFRESH_MINUTES min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
