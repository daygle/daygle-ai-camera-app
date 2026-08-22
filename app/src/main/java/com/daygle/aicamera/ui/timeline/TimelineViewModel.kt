package com.daygle.aicamera.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.model.Recording
import com.daygle.aicamera.ui.friendlyMessage
import com.daygle.aicamera.ui.isMotionLabel
import com.daygle.aicamera.ui.isSoundLabel
import com.daygle.aicamera.ui.parseTimestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
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

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val repository: CameraRepository
) : ViewModel() {

    private val _state = MutableStateFlow<TimelineUiState>(TimelineUiState.Loading)
    val state: StateFlow<TimelineUiState> = _state.asStateFlow()

    private var selectedDate: LocalDate = LocalDate.now()
    private var startTime: LocalTime = LocalTime.MIN
    private var endTime: LocalTime = LocalTime.MAX

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
                TimelineUiState.Ready(current.data.copy(refreshing = true, date = selectedDate, startTime = startTime, endTime = endTime))
            } else {
                TimelineUiState.Loading
            }
        }

        viewModelScope.launch {
            val result = repository.recordings()
            if (result.isSuccess) {
                val recordings = result.getOrThrow()
                val (objects, sounds, motions) = processRecordings(recordings, selectedDate, startTime, endTime)
                _state.value = TimelineUiState.Ready(
                    TimelineReady(
                        date = selectedDate,
                        startTime = startTime,
                        endTime = endTime,
                        objectSegments = objects,
                        soundSegments = sounds,
                        motionSegments = motions
                    )
                )
            } else {
                _state.value = TimelineUiState.Error(
                    result.exceptionOrNull()?.friendlyMessage() ?: "Unknown error"
                )
            }
        }
    }

    private fun processRecordings(
        recordings: List<Recording>,
        date: LocalDate,
        startLimit: LocalTime,
        endLimit: LocalTime
    ): Triple<List<TimelineSegment>, List<TimelineSegment>, List<TimelineSegment>> {
        val zone = ZoneId.systemDefault()

        val objectSegments = mutableListOf<TimelineSegment>()
        val soundSegments = mutableListOf<TimelineSegment>()
        val motionSegments = mutableListOf<TimelineSegment>()

        recordings.forEach { recording ->
            val startTs = parseTimestamp(recording.startedAt) ?: return@forEach

            // Normalize to the device's local zone so the day boundary, the
            // time-range filter, and the on-screen position all agree, no matter
            // what offset the server timestamp carries.
            val localTs = startTs.atZoneSameInstant(zone)

            if (localTs.toLocalDate() != date) return@forEach

            val time = localTs.toLocalTime()
            if (time.isBefore(startLimit) || time.isAfter(endLimit)) return@forEach

            val isSound = recording.source?.lowercase() == "sound" ||
                    recording.triggerType?.lowercase() == "sound" ||
                    isSoundLabel(recording.triggerLabel) ||
                    recording.labels.any { isSoundLabel(it) }

            val isMotion = recording.source?.lowercase() == "motion" ||
                    recording.triggerType?.lowercase() == "motion" ||
                    isMotionLabel(recording.triggerLabel) ||
                    recording.labels.any { isMotionLabel(it) }

            val startMinute = (localTs.hour * 60 + localTs.minute + localTs.second / 60f)
            val durationMinutes = (recording.durationSeconds / 60.0).coerceAtLeast(1.0).toFloat()

            val segment = TimelineSegment(
                recordingId = recording.id,
                startMinute = startMinute,
                durationMinutes = durationMinutes,
                title = recording.triggerLabel ?: recording.triggerType ?: "Event"
            )

            when {
                isSound -> soundSegments.add(segment)
                isMotion -> motionSegments.add(segment)
                else -> objectSegments.add(segment)
            }
        }

        return Triple(objectSegments, soundSegments, motionSegments)
    }
}
