package com.daygle.aicamera.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daygle.aicamera.ui.components.AppLogo
import com.daygle.aicamera.ui.components.SettingsCard
import com.daygle.aicamera.ui.components.SettingsCategoryRow
import com.daygle.aicamera.ui.components.SettingsDivider
import com.daygle.aicamera.ui.components.SettingsRow

/**
 * Settings landing page. Rather than one long scroll of every control, this
 * shows a compact header and a list of category rows that each open a focused
 * sub-screen (see SettingsSubScreens.kt). This keeps the top level short and
 * scannable, mirroring the LocaPeer settings landing page.
 */
@Composable
fun SettingsScreen(
    onSignOut: () -> Unit,
    onOpenGeneral: () -> Unit,
    onOpenNavigation: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenServerDetails: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSignOutDialog by remember { mutableStateOf(false) }

    if (showSignOutDialog) {
        SignOutDialog(
            serverLabel = state.serverLabel,
            onConfirm = {
                showSignOutDialog = false
                onSignOut()
            },
            onDismiss = { showSignOutDialog = false },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Header ─────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppLogo(modifier = Modifier.size(72.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Daygle AI Camera",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    state.serverLabel.ifBlank { "Not connected" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── Category list ──────────────────────────────────────────────────
        SettingsCard {
            SettingsCategoryRow(
                icon = Icons.Filled.Tune,
                title = "General",
                subtitle = "Theme, time format, refresh rate",
                onClick = onOpenGeneral
            )
            SettingsDivider()
            SettingsCategoryRow(
                icon = Icons.Filled.Dashboard,
                title = "Navigation",
                subtitle = "Choose and reorder your tabs",
                onClick = onOpenNavigation
            )
            SettingsDivider()
            SettingsCategoryRow(
                icon = Icons.Filled.Notifications,
                title = "Notifications",
                subtitle = "Push alerts and device permissions",
                onClick = onOpenNotifications
            )
            SettingsDivider()
            SettingsCategoryRow(
                icon = Icons.Filled.Dns,
                title = "Server Details",
                subtitle = state.serverLabel.ifBlank { "Manage your connection" },
                onClick = onOpenServerDetails
            )
            SettingsDivider()
            SettingsCategoryRow(
                icon = Icons.Filled.Info,
                title = "About",
                subtitle = "Version and app information",
                onClick = onOpenAbout
            )
        }

        // ── Sign out ───────────────────────────────────────────────────────
        SettingsCard {
            SettingsRow(
                title = "Sign Out",
                destructive = true,
                onClick = { showSignOutDialog = true },
                action = {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun SignOutDialog(
    serverLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
        title = { Text("Sign out?") },
        text = {
            Text(
                if (serverLabel.isBlank()) {
                    "You'll need to enter your server address and credentials to sign back in."
                } else {
                    "You'll be disconnected from $serverLabel and need your credentials to sign back in."
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Sign Out", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
