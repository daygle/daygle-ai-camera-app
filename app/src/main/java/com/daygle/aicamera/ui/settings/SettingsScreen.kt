package com.daygle.aicamera.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenVpn: () -> Unit,
    onSignOut: () -> Unit,
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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsGroup(title = "Appearance", icon = Icons.Filled.Palette) {
                ThemeOption("System Default", state.themeMode == "system") { viewModel.setTheme("system") }
                ThemeOption("Dark Mode", state.themeMode == "dark") { viewModel.setTheme("dark") }
                ThemeOption("Light Mode", state.themeMode == "light") { viewModel.setTheme("light") }
            }

            SettingsGroup(title = "Display Options", icon = Icons.Filled.Schedule) {
                SwitchRow("Use 24-Hour Format", state.use24Hour) { viewModel.setUse24Hour(it) }
            }

            SettingsGroup(title = "Network", icon = Icons.Filled.Lock) {
                ListItem(
                    headlineContent = { Text("VPN (WireGuard)", style = MaterialTheme.typography.bodyLarge) },
                    supportingContent = { Text("Route this app through your own WireGuard tunnel") },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                    modifier = Modifier.clickable { onOpenVpn() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }

            SettingsGroup(title = "Live View Refresh", icon = Icons.Filled.Speed) {
                RefreshOption("Fast (500 ms)", state.refreshLabel == "Fast (500 ms)", 500L, viewModel)
                RefreshOption("Balanced (1 s)", state.refreshLabel == "Balanced (1 s)", 1000L, viewModel)
                RefreshOption("Smooth (2 s)", state.refreshLabel == "Smooth (2 s)", 2000L, viewModel)
            }

            SettingsGroup(title = "Account", icon = Icons.Filled.Dns) {
                ListItem(
                    headlineContent = { Text("Server", style = MaterialTheme.typography.bodyLarge) },
                    supportingContent = {
                        Text(state.serverLabel.ifBlank { "Not connected" })
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                SignOutButton(onClick = { showSignOutDialog = true })
            }

            SettingsGroup(title = "About", icon = Icons.Filled.Info) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Daygle AI Camera", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Version 1.0.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Android client for self-hosted Daygle AI Camera",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SignOutButton(onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(
                "Sign Out",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )
        },
        supportingContent = { Text("Disconnect and forget this server on this device") },
        leadingContent = {
            Icon(
                Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        modifier = Modifier.clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
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

@Composable
private fun SettingsGroup(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier.clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onToggle) },
        modifier = Modifier.clickable { onToggle(!checked) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun RefreshOption(
    label: String,
    selected: Boolean,
    intervalMs: Long,
    viewModel: SettingsViewModel,
) {
    ListItem(
        headlineContent = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier.clickable { viewModel.setRefreshInterval(intervalMs) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
