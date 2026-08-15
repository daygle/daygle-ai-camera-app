package com.daygle.aicamera

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.daygle.aicamera.push.NtfyService
import com.daygle.aicamera.ui.DaygleNavHost
import com.daygle.aicamera.ui.theme.DaygleTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Event id from a tapped push alert, consumed by [DaygleNavHost]. */
    private val _snapshotEventId = MutableStateFlow<Int?>(null)
    val snapshotEventId: StateFlow<Int?> = _snapshotEventId.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeSnapshotIntent(intent)
        enableEdgeToEdge()
        setContent {
            DaygleTheme {
                DaygleNavHost(
                    snapshotEventId = snapshotEventId,
                    onSnapshotEventConsumed = { _snapshotEventId.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeSnapshotIntent(intent)
    }

    /** Read (and clear) the event id carried by a push-alert tap. */
    private fun consumeSnapshotIntent(intent: Intent?) {
        val eventId = intent?.getIntExtra(NtfyService.EXTRA_EVENT_ID, -1) ?: -1
        if (eventId > 0) {
            // Clear so activity recreation (rotation/process restore) doesn't
            // re-open the same snapshot.
            intent?.removeExtra(NtfyService.EXTRA_EVENT_ID)
            _snapshotEventId.value = eventId
        }
    }
}
