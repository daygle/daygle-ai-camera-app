package com.daygle.aicamera.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.daygle.aicamera.DaygleApp
import com.daygle.aicamera.data.CameraRepository

/** Pull the shared [CameraRepository] out of the Application from a ViewModel factory. */
fun CreationExtras.repository(): CameraRepository {
    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DaygleApp
    return app.container.repository
}
