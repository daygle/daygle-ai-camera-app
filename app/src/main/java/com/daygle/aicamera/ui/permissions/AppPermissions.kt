package com.daygle.aicamera.ui.permissions

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.daygle.aicamera.push.NtfyService
import com.daygle.aicamera.ui.LifecycleResumeEffect

/**
 * Runtime-permission checks and the intents that let the user grant them, plus
 * a reusable [PermissionsChecklist] composable. The app needs three things to
 * deliver camera alerts reliably:
 *
 *  - **Notifications** (POST_NOTIFICATIONS on Android 13+) so alerts can show.
 *  - **Background activity** (battery-optimization exemption) so the ntfy
 *    listener keeps its persistent connection alive.
 *  - **Auto-restart** after a reboot, which the boot receiver handles with no
 *    user action.
 */
object AppPermissions {

    fun notificationsEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun batteryUnrestricted(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openAppNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure { openAppDetails(context) }
    }

    private fun openAppDetails(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    // The battery-optimization exemption is exactly what a self-hosted alert
    // listener needs to stay connected; the BatteryLife lint is a Play-policy
    // heads-up, not a correctness issue here.
    @SuppressLint("BatteryLife")
    fun requestBatteryException(context: Context) {
        val request = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(request) }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    /** Post a sample alert; returns false when notifications are not permitted. */
    fun sendTestNotification(context: Context): Boolean = NtfyService.postTestNotification(context)
}

/**
 * A live checklist of the permissions the app needs, each with its current
 * status and a button to grant it. Reused on the onboarding screen, in Settings
 * and on the push-setup screen. Statuses refresh whenever the screen resumes
 * (e.g. after the user returns from a system settings page).
 */
@Composable
fun PermissionsChecklist(
    modifier: Modifier = Modifier,
    showTestButton: Boolean = true,
) {
    val context = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(onPause = {}, onResume = { refreshTick++ })

    val notificationsOn = remember(refreshTick) { AppPermissions.notificationsEnabled(context) }
    val batteryOk = remember(refreshTick) { AppPermissions.batteryUnrestricted(context) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        refreshTick++
        // A denied (or permanently-denied) runtime request shows no dialog, so
        // route the user to the notification settings toggle instead.
        if (!granted) AppPermissions.openAppNotificationSettings(context)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PermissionRow(
            icon = Icons.Filled.NotificationsActive,
            title = "Notifications",
            description = "Show camera detection alerts on this device.",
            granted = notificationsOn,
            actionLabel = "Enable",
            onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    AppPermissions.openAppNotificationSettings(context)
                }
            },
        )
        PermissionRow(
            icon = Icons.Filled.Sensors,
            title = "Background activity",
            description = "Let the alert listener keep running so alerts arrive instantly.",
            granted = batteryOk,
            actionLabel = "Allow",
            onAction = { AppPermissions.requestBatteryException(context) },
        )
        PermissionRow(
            icon = Icons.Filled.RestartAlt,
            title = "Auto-restart",
            description = "The listener resumes automatically after a reboot. No action needed.",
            granted = true,
            actionLabel = null,
            onAction = {},
        )
        if (showTestButton) {
            OutlinedButton(
                onClick = {
                    val posted = AppPermissions.sendTestNotification(context)
                    Toast.makeText(
                        context,
                        if (posted) "Test notification sent." else "Enable notifications first, then try again.",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Send test notification")
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (granted) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Enabled",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        } else if (actionLabel != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
