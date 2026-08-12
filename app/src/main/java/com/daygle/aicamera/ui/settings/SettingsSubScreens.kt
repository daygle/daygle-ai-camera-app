package com.daygle.aicamera.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daygle.aicamera.ui.HomeTab
import com.daygle.aicamera.ui.components.SettingsDivider
import com.daygle.aicamera.ui.components.SettingsRow
import com.daygle.aicamera.ui.components.SettingsSection
import com.daygle.aicamera.ui.components.SettingsSwitchRow

/**
 * The settings landing page routes into these focused, single-purpose screens
 * rather than showing every control on one long scroll. Each screen owns just
 * its own section's card and dialogs, and shares the same [SettingsViewModel]
 * (backed by DataStore, so separate instances stay in sync).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSubScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

// ─── General ────────────────────────────────────────────────────────────────

@Composable
fun GeneralSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showRefreshDialog by remember { mutableStateOf(false) }

    if (showThemeDialog) {
        SelectionDialog(
            title = "Appearance",
            options = listOf(
                "system" to "System Default",
                "dark" to "Dark Mode",
                "light" to "Light Mode",
            ),
            selectedKey = state.themeMode,
            onSelect = viewModel::setTheme,
            onDismiss = { showThemeDialog = false },
        )
    }

    if (showRefreshDialog) {
        SelectionDialog(
            title = "Live View Refresh",
            options = listOf(
                500L to "Fast (500 ms)",
                1000L to "Balanced (1 s)",
                2000L to "Smooth (2 s)",
            ),
            selectedKey = state.refreshIntervalMs,
            onSelect = viewModel::setRefreshInterval,
            onDismiss = { showRefreshDialog = false },
        )
    }

    SettingsSubScaffold(title = "General", onBack = onBack, modifier = modifier) {
        SettingsSection(title = "General", icon = Icons.Filled.Tune) {
            SettingsRow(
                title = "Theme",
                value = state.themeLabel,
                onClick = { showThemeDialog = true },
            )
            SettingsDivider()
            SettingsSwitchRow(
                title = "24-Hour Format",
                checked = state.use24Hour,
                onCheckedChange = { viewModel.setUse24Hour(it) },
            )
            SettingsDivider()
            SettingsRow(
                title = "Live Refresh Rate",
                value = state.refreshLabel,
                onClick = { showRefreshDialog = true },
            )
        }
    }
}

// ─── Navigation ───────────────────────────────────────────────────────────────

@Composable
fun NavigationSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SettingsSubScaffold(title = "Navigation", onBack = onBack, modifier = modifier) {
        SettingsSection(title = "Tabs", icon = Icons.Filled.Dashboard) {
            HomeTab.entries.forEachIndexed { index, tab ->
                val isActive = state.navItems.contains(tab)
                val currentIndex = state.navItems.indexOf(tab)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        tab.icon,
                        null,
                        tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (isActive) {
                        IconButton(
                            onClick = { viewModel.moveNavItem(currentIndex, true) },
                            enabled = currentIndex > 0,
                        ) {
                            Icon(Icons.Filled.ArrowUpward, "Move Up", modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { viewModel.moveNavItem(currentIndex, false) },
                            enabled = currentIndex < state.navItems.size - 1,
                        ) {
                            Icon(Icons.Filled.ArrowDownward, "Move Down", modifier = Modifier.size(20.dp))
                        }
                    }
                    Switch(
                        checked = isActive,
                        onCheckedChange = { viewModel.toggleNavItem(tab) },
                        enabled = !isActive || state.navItems.size > 1,
                        modifier = Modifier.scale(0.8f),
                    )
                }
                if (index < HomeTab.entries.size - 1) SettingsDivider()
            }
        }
    }
}

// ─── About ────────────────────────────────────────────────────────────────────

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSubScaffold(title = "About", onBack = onBack, modifier = modifier) {
        SettingsSection(title = "About", icon = Icons.Filled.Info) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text("Daygle AI Camera", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Version 1.0.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** A single-choice dialog with radio options, shared by the settings sub-screens. */
@Composable
internal fun <T> SelectionDialog(
    title: String,
    options: List<Pair<T, String>>,
    selectedKey: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(key); onDismiss() }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = (key == selectedKey), onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
