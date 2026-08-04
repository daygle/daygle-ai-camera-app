package com.daygle.aicamera.ui.vpn

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daygle.aicamera.vpn.TunnelManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VpnViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.onConsentResult(result.resultCode == Activity.RESULT_OK)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("VPN (WireGuard)", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Prominent, colour-coded live status.
            TunnelStatusCard(status = state.status)

            // What VPN-only mode does.
            InfoCard(
                "Route this app through your own WireGuard tunnel. Only this app's " +
                    "traffic uses the VPN; other apps are unaffected. When VPN-only " +
                    "mode is on, the app refuses to connect unless the tunnel is up.",
            )

            // VPN-only toggle.
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ListItem(
                    headlineContent = { Text("VPN-Only Mode", fontWeight = FontWeight.Bold) },
                    supportingContent = {
                        Text(
                            if (state.vpnOnly) {
                                "On — the app only connects while the tunnel is up."
                            } else {
                                "Off — the app connects over your normal network."
                            },
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = state.vpnOnly,
                            enabled = state.isValid || state.vpnOnly,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    viewModel.enableVpnOnly { intent -> consentLauncher.launch(intent) }
                                } else {
                                    viewModel.disableVpnOnly()
                                }
                            },
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }

            // Config editor.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "WireGuard Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val showInlineError = state.hasConfig && state.validationError != null
                        OutlinedTextField(
                            value = state.configText,
                            onValueChange = viewModel::onConfigChange,
                            label = { Text("Paste your .conf") },
                            placeholder = {
                                Text(
                                    "[Interface]\nPrivateKey = ...\nAddress = ...\n\n[Peer]\n" +
                                        "PublicKey = ...\nEndpoint = host:51820\n" +
                                        "AllowedIPs = 0.0.0.0/0\nPersistentKeepalive = 25",
                                )
                            },
                            isError = showInlineError,
                            minLines = 8,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                            ),
                        )

                        when {
                            showInlineError -> Text(
                                state.validationError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            state.endpoint != null -> Text(
                                "Peer: ${state.endpoint}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        state.message?.let { MessageBanner(it) }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedButton(
                                onClick = viewModel::clearConfig,
                                enabled = state.hasConfig,
                                shape = RoundedCornerShape(16.dp),
                            ) { Text("Clear") }
                            Button(
                                onClick = viewModel::saveConfig,
                                enabled = state.isValid,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                            ) { Text("Save Configuration") }
                        }
                    }
                }
            }

            // Manual connect / disconnect.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                val connecting = state.status == TunnelManager.Status.CONNECTING
                val isUp = state.status == TunnelManager.Status.UP
                OutlinedButton(
                    onClick = { viewModel.connect { intent -> consentLauncher.launch(intent) } },
                    enabled = state.isValid && !isUp && !connecting,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    if (connecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Connect")
                    }
                }
                OutlinedButton(
                    onClick = viewModel::disconnect,
                    enabled = isUp || connecting,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("Disconnect") }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TunnelStatusCard(status: TunnelManager.Status) {
    val container: Color
    val accent: Color
    val onContainer: Color
    val title: String
    val description: String
    val icon: ImageVector?

    when (status) {
        TunnelManager.Status.UP -> {
            container = MaterialTheme.colorScheme.primaryContainer
            accent = MaterialTheme.colorScheme.primary
            onContainer = MaterialTheme.colorScheme.onPrimaryContainer
            title = "Connected"
            description = "This app's traffic is routed through your tunnel."
            icon = Icons.Filled.CheckCircle
        }
        TunnelManager.Status.CONNECTING -> {
            container = MaterialTheme.colorScheme.tertiaryContainer
            accent = MaterialTheme.colorScheme.tertiary
            onContainer = MaterialTheme.colorScheme.onTertiaryContainer
            title = "Connecting…"
            description = "Bringing the WireGuard tunnel up."
            icon = null // show a spinner instead
        }
        TunnelManager.Status.ERROR -> {
            container = MaterialTheme.colorScheme.errorContainer
            accent = MaterialTheme.colorScheme.error
            onContainer = MaterialTheme.colorScheme.onErrorContainer
            title = "Tunnel error"
            description = "Couldn't bring the tunnel up. Check your configuration and try again."
            icon = Icons.Filled.Error
        }
        TunnelManager.Status.DOWN -> {
            container = MaterialTheme.colorScheme.surfaceContainerHigh
            accent = MaterialTheme.colorScheme.outline
            onContainer = MaterialTheme.colorScheme.onSurface
            title = "Disconnected"
            description = "The tunnel is down. Connect below or turn on VPN-only mode."
            icon = Icons.Filled.Lock
        }
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(26.dp),
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.surface,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onContainer,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer,
                )
            }
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessageBanner(message: VpnMessage) {
    val container = if (message.isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val content = if (message.isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    val icon = if (message.isError) Icons.Filled.Error else Icons.Filled.CheckCircle

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Text(message.text, style = MaterialTheme.typography.bodySmall, color = content)
        }
    }
}
