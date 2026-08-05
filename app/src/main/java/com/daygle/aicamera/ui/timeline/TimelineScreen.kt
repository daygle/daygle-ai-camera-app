package com.daygle.aicamera.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daygle.aicamera.ui.components.ErrorState
import com.daygle.aicamera.ui.components.LoadingState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private const val MINUTE_WIDTH = 4f // Pixels per minute
private const val TOTAL_MINUTES = 24 * 60
private val TIMELINE_WIDTH = (TOTAL_MINUTES * MINUTE_WIDTH).dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    onOpenRecording: (Int) -> Unit,
    modifier: Modifier = Modifier,
    refreshTrigger: Int = 0,
    viewModel: TimelineViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) viewModel.load()
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = (state as? TimelineUiState.Ready)?.data?.date
                ?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let {
                        val date = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        viewModel.setDate(date)
                    }
                    showDatePicker = false
                }) { Text("Apply") }
            }
        ) {
            DatePicker(state = dateState)
        }
    }

    when (val s = state) {
        TimelineUiState.Loading -> LoadingState(modifier)
        is TimelineUiState.Error -> ErrorState(s.message, onRetry = viewModel::load, modifier = modifier)
        is TimelineUiState.Ready -> {
            val data = s.data
            val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
            
            Column(modifier.fillMaxSize()) {
                // Header
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Activity Timeline", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                data.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = "Change Date")
                        }
                    }
                }

                PullToRefreshBox(
                    isRefreshing = data.refreshing,
                    onRefresh = viewModel::load,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        TimelineLane(
                            title = "Object Detections",
                            icon = Icons.Filled.Videocam,
                            segments = data.objectSegments,
                            color = MaterialTheme.colorScheme.primary,
                            locale = locale,
                            onSegmentClick = onOpenRecording
                        )

                        TimelineLane(
                            title = "Sound Detections",
                            icon = Icons.Filled.GraphicEq,
                            segments = data.soundSegments,
                            color = MaterialTheme.colorScheme.tertiary,
                            locale = locale,
                            onSegmentClick = onOpenRecording
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineLane(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    segments: List<TimelineSegment>,
    color: Color,
    locale: Locale,
    onSegmentClick: (Int) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = color)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .horizontalScroll(scrollState)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            val textPaint = remember {
                android.graphics.Paint().apply {
                    this.color = android.graphics.Color.GRAY
                    this.textSize = 24f
                    this.textAlign = android.graphics.Paint.Align.CENTER
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(TIMELINE_WIDTH)
                    .pointerInput(segments) {
                        detectTapGestures { offset ->
                            val minute = offset.x / MINUTE_WIDTH
                            val segment = segments.find { 
                                minute >= it.startMinute && minute <= (it.startMinute + it.durationMinutes)
                            }
                            segment?.let { onSegmentClick(it.recordingId) }
                        }
                    }
            ) {
                // Draw hour markers
                for (hour in 0..24) {
                    val x = hour * 60 * MINUTE_WIDTH
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.4f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                    
                    if (hour < 24) {
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText(
                                String.format(locale, "%02d:00", hour),
                                x + (30 * MINUTE_WIDTH),
                                size.height - 10f,
                                textPaint
                            )
                        }
                    }
                }

                // Draw segments
                segments.forEach { segment ->
                    val x = segment.startMinute * MINUTE_WIDTH
                    val width = segment.durationMinutes * MINUTE_WIDTH
                    
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, 20.dp.toPx()),
                        size = Size(width.coerceAtLeast(4f), 80.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }
        }
    }
}
