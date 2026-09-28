package com.daygle.aicamera.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.model.Recording
import com.daygle.aicamera.data.model.TimelineResponse
import com.daygle.aicamera.data.model.TimelineSegmentDto
import com.daygle.aicamera.ui.friendlyMessage
import com.daygle.aicamera.ui.isMotion
import com.daygle.aicamera.ui.isSound
import com.daygle.aicamera.ui.parseTimestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class TimelineSegment(
    val recordingId: Int,
    val startMinute: Float,
    val durationMinutes: Float,
    val title: String,
)

data class TimelineReady(
    val date: LocalDate,
    val startTime: LocalTime = LocalTime.MIN,
    val endTime: LocalTime = LocalTime.MAX,
    val objectSegments: List<TimelineSegment>,
    val soundSegments: List<TimelineSegment>,
    val motionSegments: List<TimelineSegment>,
    val refreshing: Boolean = false,
)

sealed interface TimelineUiState {
    data object Loading : TimelineUiState
    data class Error(val message: String) : TimelineUiState
    data class Ready(val data: TimelineReady) : TimelineUiState
}

/**
 * Loads timeline data via the server's `GET /api/recordings/timeline`, which
 * pre-clamps recordings to the selected day in the requested timezone and
 * precomputes each bar's seconds-from-midnight. Falls back to the local
 * grouping over `GET /api/recordings` when the timeline endpoint is
 * unavailable (older server), so the screen degrades gracefully.
 */
@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val repository: CameraRepository
) : ViewModel() {

    private val _state = MutableStateFlow<TimelineUiState>(TimelineUiState.Loading)
    val state: StateFlow<TimelineUiState> = _state.asStateFlow()

    private var selectedDate: LocalDate = LocalDate.now()
    private var startTime: LocalTime = LocalTime.MIN
    private var endTime: LocalTime = LocalTime.MAX
    private var loadJob: Job? = null

    init {
        load()
    }

    fun setDate(date: LocalDate) {
        selectedDate = date
        load()
    }

    fun setTimeRange(start: LocalTime, end: LocalTime) {
        startTime = start
        endTime = end
        load()
    }

    fun load() {
        _state.update { current ->
            if (current is TimelineUiState.Ready) {
                TimelineUiState.Ready(
                    current.data.copy(
                        refreshing = true,
                        date = selectedDate,
                        startTime = startTime,
                        endTime = endTime,
                    )
                )
            } else {
                TimelineUiState.Loading
            }
        }

        // Switching days quickly must not let an older, slower response
        // overwrite the day the user is now looking at.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val timelineResult = repository.recordingsTimeline(
                day = selectedDate.format(DAY_FORMAT),
                tzOffsetMinutes = tzOffsetMinutes(selectedDate),
            )
            val timeline = timelineResult.getOrNull()
            if (timeline != null) {
                _state.value = TimelineUiState.Ready(buildReady(timeline))
            } else {
                // Older server without the timeline endpoint: group full
                // recordings client-side, preserving the previous behaviour.
                val recordingsResult = repository.recordings()
                if (recordingsResult.isSuccess) {
                    _state.value = TimelineUiState.Ready(
                        buildReadyFromRecordings(recordingsResult.getOrThrow())
                    )
                } else {
                    _state.value = TimelineUiState.Error(
                        recordingsResult.exceptionOrNull()?.friendlyMessage() ?: "Unknown error"
                    )
                }
            }
        }
    }

    private fun buildReady(timeline: TimelineResponse): TimelineReady {
        val objects = mutableListOf<TimelineSegment>()
        val sounds = mutableListOf<TimelineSegment>()
        val motions = mutableListOf<TimelineSegment>()

        timeline.recordings.forEach { dto ->
            val segment = dto.toSegment() ?: return@forEach
            if (!inTimeRange(segment.startMinute * 60f)) return@forEach
            when {
                dto.isSound() -> sounds.add(segment)
                dto.isMotion() -> motions.add(segment)
                else -> objects.add(segment)
            }
        }

        return TimelineReady(
            date = selectedDate,
            startTime = startTime,
            endTime = endTime,
            objectSegments = objects,
            soundSegments = sounds,
            motionSegments = motions,
        )
    }

    private fun buildReadyFromRecordings(recordings: List<Recording>): TimelineReady {
        val zone = ZoneId.systemDefault()
        val objects = mutableListOf<TimelineSegment>()
        val sounds = mutableListOf<TimelineSegment>()
        val motions = mutableListOf<TimelineSegment>()

        recordings.forEach { recording ->
            val startTs = parseTimestamp(recording.startedAt) ?: return@forEach

            // Normalize to the device's local zone so the day boundary, the
            // time-range filter, and the on-screen position all agree, no matter
            // what offset the server timestamp carries.
            val localTs = startTs.atZoneSameInstant(zone)

            if (localTs.toLocalDate() != selectedDate) return@forEach

            val time = localTs.toLocalTime()
            if (time.isBefore(startTime) || time.isAfter(endTime)) return@forEach

            val segment = TimelineSegment(
                recordingId = recording.id,
                startMinute = time.toSecondOfDay() / 60f,
                durationMinutes = (recording.durationSeconds / 60.0).coerceAtLeast(1.0).toFloat(),
                title = recording.triggerLabel ?: recording.triggerType ?: "Event",
            )

            when {
                recording.isSound() -> sounds.add(segment)
                recording.isMotion() -> motions.add(segment)
                else -> objects.add(segment)
            }
        }

        return TimelineReady(
            date = selectedDate,
            startTime = startTime,
            endTime = endTime,
            objectSegments = objects,
            soundSegments = sounds,
            motionSegments = motions,
        )
    }

    /**
     * Client-side complement to the server's day clamp: narrows segments to
     * the selected [startTime]/[endTime] window (no-op for the full day).
     */
    private fun inTimeRange(startSeconds: Float): Boolean {
        if (startTime != LocalTime.MIN && startSeconds < startTime.toSecondOfDay()) return false
        if (endTime != LocalTime.MAX && startSeconds > endTime.toSecondOfDay()) return false
        return true
    }

    private fun TimelineSegmentDto.toSegment(): TimelineSegment? {
        if (timelineDurationSeconds <= 0.0) return null
        return TimelineSegment(
            recordingId = id,
            startMinute = (timelineStartSeconds / 60.0).toFloat(),
            durationMinutes = (timelineDurationSeconds / 60.0).coerceAtLeast(1.0).toFloat(),
            title = triggerLabel ?: triggerType ?: "Event",
        )
    }

    /**
     * Device timezone as minutes east of UTC, negated to match the server's
     * `tz_offset_minutes` convention (JS `getTimezoneOffset()`: positive west
     * of UTC). The server turns it into `timezone(timedelta(minutes=-value))`.
     *
     * The offset is taken for the selected [day], not for "now": across a
     * daylight-saving change the two differ by an hour, which would shift
     * every bar (and the day boundary) when browsing past days. Noon is used
     * because DST transitions happen in the early hours, so it reflects the
     * offset in effect for most of that day.
     */
    private fun tzOffsetMinutes(day: LocalDate): Int =
        -day.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).offset.totalSeconds / 60

    companion object {
        private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
