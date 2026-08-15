package com.daygle.aicamera.ui.snapshots

import androidx.lifecycle.ViewModel
import com.daygle.aicamera.data.CameraRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Minimal holder for the standalone event-snapshot screen shown when a push
 * alert is tapped. Builds the authenticated snapshot URL for one event id.
 */
@HiltViewModel
class SnapshotViewModel @Inject constructor(
    private val repository: CameraRepository,
) : ViewModel() {
    fun snapshotUrl(eventId: Int): String? = repository.eventSnapshotUrl(eventId)
}
