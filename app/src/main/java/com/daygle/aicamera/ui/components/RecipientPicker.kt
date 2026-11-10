package com.daygle.aicamera.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.daygle.aicamera.data.RecipientChannel
import com.daygle.aicamera.data.model.AppUser
import com.daygle.aicamera.data.recipientOptions

/**
 * The user recipient picker, as on the server's web pages: active users who
 * can receive on [channel] (with their address or push topic), then anything
 * in [selected] that reaches nobody, flagged so it can be unticked. Used by
 * the Alerts screen and the Notifications screen's test push.
 *
 * [offNote] shows under the heading when the channel is switched off for
 * this alert; [emptyNote] when no user can receive on it; [footer] last.
 * With [enabled] false the rows are shown but can't be ticked, e.g. while the
 * user list couldn't be loaded offline.
 */
@Composable
internal fun RecipientPicker(
    legend: String,
    selected: List<String>,
    users: List<AppUser>,
    channel: RecipientChannel,
    onToggle: (RecipientChannel, String, Boolean) -> Unit,
    emptyNote: String,
    offNote: String? = null,
    footer: String? = null,
    enabled: Boolean = true,
) {
    val options = recipientOptions(selected, users, channel)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(legend, style = MaterialTheme.typography.titleSmall)
        offNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (options.isEmpty()) Text(emptyNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        options.forEach { option ->
            val checked = option.value in selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Checkbox,
                        onValueChange = { onToggle(channel, option.value, it) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
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
