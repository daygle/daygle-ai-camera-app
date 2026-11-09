package com.daygle.aicamera.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daygle.aicamera.data.AlertTarget
import com.daygle.aicamera.data.RecipientChannel
import com.daygle.aicamera.data.model.AppUser
import com.daygle.aicamera.data.recipientOptions
import com.daygle.aicamera.ui.components.SettingsSection

/**
 * Admins only: pick who each alert emails and pushes, with the same user
 * picker as the server's web Alerts page. Whether an alert emails or pushes at
 * all, its schedules and everything else stay on the web Alerts page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(onBack: () -> Unit, viewModel: AlertsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alerts", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            if (state.config != null) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                    Button(
                        onClick = viewModel::save,
                        enabled = state.edited.isNotEmpty() && !state.saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.saving) "Saving…" else "Save Recipients")
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> Centered(padding) { CircularProgressIndicator() }
            state.notAdmin -> Notice(padding, "Only administrators can change alert recipients.")
            state.error != null -> Notice(padding, state.error.orEmpty(), retry = viewModel::load)
            state.targets.isEmpty() -> Notice(padding, "No alerts are set up yet. Add them on the server's Zones, Sounds and Alerts pages.")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.targets, key = { it.key }) { target ->
                    AlertCard(
                        target = target,
                        users = state.config?.users.orEmpty(),
                        onRecipient = { channel, value, checked -> viewModel.onRecipient(target.key, channel, value, checked) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Centered(padding: PaddingValues, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun Notice(padding: PaddingValues, text: String, retry: (() -> Unit)? = null) {
    Centered(padding) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (retry != null) {
            OutlinedButton(onClick = retry, modifier = Modifier.padding(top = 12.dp)) { Text("Try Again") }
        }
    }
}

@Composable
private fun AlertCard(
    target: AlertTarget,
    users: List<AppUser>,
    onRecipient: (RecipientChannel, String, Boolean) -> Unit,
) {
    SettingsSection(title = target.title) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(target.scope, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            RecipientPicker(
                legend = "Email Recipients",
                channelOn = target.emailOn,
                offNote = "Email is off for this alert. Turn it on on the server's Alerts page.",
                emptyNote = "No users have an email address yet. Add one on the server's Users page.",
                selected = target.email,
                users = users,
                channel = RecipientChannel.EMAIL,
                onToggle = onRecipient,
            )
            HorizontalDivider()
            RecipientPicker(
                legend = "Push Recipients",
                channelOn = target.pushOn,
                offNote = "Push is off for this alert. Turn it on on the server's Alerts page.",
                emptyNote = "No users have a push topic yet. Each user sets one as Push Topic on their profile.",
                selected = target.push,
                users = users,
                channel = RecipientChannel.PUSH,
                onToggle = onRecipient,
                footer = "With nobody ticked, pushes go to the shared topic.",
            )
        }
    }
}

@Composable
private fun RecipientPicker(
    legend: String,
    channelOn: Boolean,
    offNote: String,
    emptyNote: String,
    selected: List<String>,
    users: List<AppUser>,
    channel: RecipientChannel,
    onToggle: (RecipientChannel, String, Boolean) -> Unit,
    footer: String? = null,
) {
    val options = recipientOptions(selected, users, channel)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(legend, style = MaterialTheme.typography.titleSmall)
        if (!channelOn) Text(offNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (options.isEmpty()) Text(emptyNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        options.forEach { option ->
            val checked = option.value in selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle(channel, option.value, it) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = null)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(option.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        option.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (option.orphan) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        footer?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
