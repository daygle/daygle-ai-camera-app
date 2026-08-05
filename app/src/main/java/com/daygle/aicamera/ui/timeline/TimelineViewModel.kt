package com.daygle.aicamera.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daygle.aicamera.data.CameraRepository
import com.daygle.aicamera.data.model.Recording
import com.daygle.aicamera.ui.dashboard.friendlyMessage
import com.daygle.aicamera.ui.isSoundLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
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
        val current = _state.value
        _state.update {
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
                val (objects, sounds) = processRecordings(recordings, selectedDate, startTime, endTime)
                _state.value = TimelineUiState.Ready(
                    TimelineReady(
                        date = selectedDate,
                        startTime = startTime,
                        endTime = endTime,
                        objectSegments = objects,
                        soundSegments = sounds
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
    ): Pair<List<TimelineSegment>, List<TimelineSegment>> {
        val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime()
        val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime()

        val objectSegments = mutableListOf<TimelineSegment>()
        val soundSegments = mutableListOf<TimelineSegment>()

        recordings.forEach { recording ->
            val startTs = recording.startedAt?.let { 
                try { OffsetDateTime.parse(it) } catch (_: Exception) { null } 
            } ?: return@forEach

            if (startTs.isBefore(startOfDay) || !startTs.isBefore(endOfDay)) return@forEach
            
            val time = startTs.toLocalTime()
            if (time.isBefore(startLimit) || time.isAfter(endLimit)) return@forEach

            val isSound = recording.source?.lowercase() == "sound" ||
                    recording.triggerType?.lowercase() == "sound" ||
                    isSoundLabel(recording.triggerLabel) ||
                    recording.labels.any { isSoundLabel(it) }

            val startMinute = (startTs.hour * 60 + startTs.minute + startTs.second / 60f)
            val durationMinutes = (recording.durationSeconds / 60.0).coerceAtLeast(1.0).toFloat()

            val segment = TimelineSegment(
                recordingId = recording.id,
                startMinute = startMinute,
                durationMinutes = durationMinutes,
                title = recording.triggerLabel ?: recording.triggerType ?: "Event"
            )

            if (isSound) soundSegments.add(segment) else objectSegments.add(segment)
        }

        return objectSegments to soundSegments
    }
}
