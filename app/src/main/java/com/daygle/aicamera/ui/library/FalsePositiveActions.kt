package com.daygle.aicamera.ui.library

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.model.Event
import com.daygle.aicamera.data.model.Recording
import com.daygle.aicamera.ui.friendlyMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * False-positive marking shared by the screens that offer it (the Events
 * list, snapshot viewers and the recording player). Any signed-in account
 * may mark on current servers; [canMark] hides the control until the
 * account is known. Older servers allowed admins only and answer a viewer
 * with 403 (see [falsePositiveErrorMessage]).
 * [message] is a one-line result for a toast, cleared by [consumeMessage].
 */
class FalsePositiveActions(
    private val scope: CoroutineScope,
    private val repository: CameraRepository,
) {
    val canMark: StateFlow<Boolean> = repository.account
        .map { it != null }
        .stateIn(scope, SharingStarted.Eagerly, repository.account.value != null)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        // The role is only known once someone asked the server.
        if (repository.account.value == null) scope.launch { repository.refreshAccount() }
    }

    fun consumeMessage() {
        _message.value = null
    }

    /** Mark an event (or its snapshot); [onUpdated] gets the event as the server now stores it. */
    fun markEvent(eventId: Int, falsePositive: Boolean, onUpdated: (Event) -> Unit) = run(falsePositive) {
        repository.markEventFalsePositive(eventId, falsePositive).map(onUpdated)
    }

    /** Mark every event in a recording; [onUpdated] gets the re-read recording. */
    fun markRecording(recordingId: Int, falsePositive: Boolean, onUpdated: (Recording) -> Unit) = run(falsePositive) {
        repository.markRecordingFalsePositive(recordingId, falsePositive).map(onUpdated)
    }

    private fun run(falsePositive: Boolean, block: suspend () -> Result<Unit>) {
        if (_busy.value) return
        _busy.value = true
        scope.launch {
            block()
                .onSuccess {
                    _message.value = if (falsePositive) "Marked as a false positive." else "Marked as a real detection."
                }
                .onFailure { _message.value = falsePositiveErrorMessage(it) }
            _busy.value = false
        }
    }
}

/**
 * Why marking failed. A 404/405 means a server from before false-positive
 * support; a 403 a server from before viewers could mark; a 409 is a
 * recording with no detections to mark.
 */
fun falsePositiveErrorMessage(error: Throwable): String = when ((error as? HttpException)?.code()) {
    404, 405 -> "This server doesn't support false positives yet. Update the Daygle server."
    403 -> "Only admins can mark false positives on this server. Update the Daygle server to let viewers mark."
    409 -> "This recording has no detections to mark."
    else -> "Couldn't update: ${error.friendlyMessage()}"
}

/** Shows each marking result as a toast. */
@Composable
fun FalsePositiveMessages(actions: FalsePositiveActions) {
    val message by actions.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            actions.consumeMessage()
        }
    }
}
