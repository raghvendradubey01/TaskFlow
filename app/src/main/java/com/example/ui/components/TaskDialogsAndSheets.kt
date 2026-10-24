package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.SubtaskEntry
import com.example.data.model.TaskAlertMode
import com.example.data.model.TaskItem
import com.example.data.model.TaskPriority
import com.example.data.model.WorkspaceItem
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.EmeraldSync
import com.example.ui.theme.PriorityHighColor
import com.example.ui.theme.PriorityLowColor
import com.example.ui.theme.PriorityMediumColor
import com.example.ui.theme.PriorityUrgentColor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun priorityColor(priority: TaskPriority): Color = when (priority) {
    TaskPriority.URGENT -> PriorityUrgentColor
    TaskPriority.HIGH -> PriorityHighColor
    TaskPriority.MEDIUM -> PriorityMediumColor
    TaskPriority.LOW -> PriorityLowColor
}

fun parseHexColor(hex: String, fallback: Color = PriorityMediumColor): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorSheet(
    initialTask: TaskItem?,
    availableWorkspaces: List<String>,
    defaultWorkspace: String,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        notes: String,
        completed: Boolean,
        priority: TaskPriority,
        workspaceName: String,
        dueDateMillis: Long,
        reminderTimeMillis: Long,
        alertMode: TaskAlertMode,
        subtasks: List<SubtaskEntry>,
        tags: List<String>,
        pinned: Boolean
    ) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    BackHandler { onDismiss() }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember(initialTask) { mutableStateOf(initialTask?.title.orEmpty()) }
    var notes by remember(initialTask) { mutableStateOf(initialTask?.notes.orEmpty()) }
    var completed by remember(initialTask) { mutableStateOf(initialTask?.completed ?: false) }
    var priority by remember(initialTask) {
        mutableStateOf(initialTask?.priorityEnum() ?: TaskPriority.MEDIUM)
    }
    var workspaceName by remember(initialTask) {
        mutableStateOf(
            initialTask?.workspaceName
                ?: defaultWorkspace.takeIf { it != "All" }
                ?: "Inbox"
        )
    }
    var dueDateMillis by remember(initialTask) { mutableLongStateOf(initialTask?.dueDateMillis ?: 0L) }
    var reminderTimeMillis by remember(initialTask) {
        mutableLongStateOf(initialTask?.reminderTimeMillis ?: 0L)
    }
    var alertMode by remember(initialTask) {
        mutableStateOf(initialTask?.alertModeEnum() ?: TaskAlertMode.NONE)
    }
    var pinned by remember(initialTask) { mutableStateOf(initialTask?.pinned ?: false) }

    var isClockPickerOpen by remember { mutableStateOf(false) }
    var isDueDateCalendarOpen by remember { mutableStateOf(false) }

    val subtasks = remember(initialTask) {
        mutableStateListOf<SubtaskEntry>().apply {
            addAll(initialTask?.decodedSubtasks().orEmpty())
        }
    }
    var newSubtaskText by remember { mutableStateOf("") }

    val tags = remember(initialTask) {
        mutableStateListOf<String>().apply {
            addAll(initialTask?.tags.orEmpty())
        }
    }
    var newTagText by remember { mutableStateOf("") }

    val allWorkspaceChoices = remember(availableWorkspaces) {
        (listOf("Inbox", "Work", "Personal") + availableWorkspaces).distinct()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (initialTask == null) "New Cloud Task" else "Edit Task",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { pinned = !pinned },
                        modifier = Modifier.testTag("pin_task_toggle")
                    ) {
                        Icon(
                            imageVector = if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin task",
                            tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (initialTask != null && onDelete != null) {
                        IconButton(
                            onClick = {
                                onDelete(initialTask.id)
                                onDismiss()
                            },
                            modifier = Modifier.testTag("delete_task_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = stringResource(R.string.delete_task),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 200) title = it },
                label = { Text("Task Title") },
                placeholder = { Text("What needs to get done?") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_title_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = notes,
                onValueChange = { if (it.length <= 2000) notes = it },
                label = { Text("Notes & Context") },
                placeholder = { Text("Add links, acceptance criteria, or sync notes…") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_notes_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Clock Time Scheduling & Alert Mode Section
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when (alertMode) {
                                    TaskAlertMode.CALL -> Icons.Default.Call
                                    TaskAlertMode.POPUP -> Icons.Default.NotificationsActive
                                    TaskAlertMode.NOTIFICATION -> Icons.Default.Notifications
                                    TaskAlertMode.NONE -> Icons.Default.AccessTime
                                },
                                contentDescription = null,
                                tint = if (alertMode == TaskAlertMode.NONE) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    EmeraldSync
                                },
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Clock Time Schedule & Alert Mode",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (reminderTimeMillis > 0L) {
                            TextButton(
                                onClick = {
                                    reminderTimeMillis = 0L
                                    alertMode = TaskAlertMode.NONE
                                }
                            ) {
                                Text("Clear", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // Prominent Interactive Clock Time & Date Picker Button Card
                    Surface(
                        color = if (reminderTimeMillis > 0L && alertMode != TaskAlertMode.NONE) {
                            EmeraldSync.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = if (reminderTimeMillis > 0L && alertMode != TaskAlertMode.NONE) {
                                    EmeraldSync
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                },
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { isClockPickerOpen = true }
                            .testTag("open_clock_time_picker_button")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (reminderTimeMillis > 0L && alertMode != TaskAlertMode.NONE) {
                                                EmeraldSync.copy(alpha = 0.25f)
                                            } else {
                                                MaterialTheme.colorScheme.primaryContainer
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = "Set Clock Time",
                                        tint = if (reminderTimeMillis > 0L && alertMode != TaskAlertMode.NONE) {
                                            EmeraldSync
                                        } else {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        },
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    if (reminderTimeMillis > 0L && alertMode != TaskAlertMode.NONE) {
                                        val clockText = remember(reminderTimeMillis) {
                                            SimpleDateFormat("h:mm a", Locale.getDefault())
                                                .format(Date(reminderTimeMillis))
                                        }
                                        val dateText = remember(reminderTimeMillis) {
                                            SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
                                                .format(Date(reminderTimeMillis))
                                        }
                                        Text(
                                            text = "$clockText • $dateText",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Alert Mode: ${alertMode.label} • Tap to change clock time",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = EmeraldSync
                                        )
                                    } else {
                                        Text(
                                            text = "Set Exact Clock Time & Date",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Pick hour, minute (AM/PM) & date on clock",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = if (reminderTimeMillis > 0L) "Change" else "Set Clock",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Alert Mode Chips (None, Notification, Pop-Up, Incoming Call)
                    Text(
                        text = "Alert Mode",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TaskAlertMode.entries.forEach { mode ->
                            val selected = alertMode == mode
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    alertMode = mode
                                    if (mode != TaskAlertMode.NONE && reminderTimeMillis <= System.currentTimeMillis()) {
                                        val cal = Calendar.getInstance().apply {
                                            add(Calendar.MINUTE, 10)
                                            set(Calendar.SECOND, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }
                                        reminderTimeMillis = cal.timeInMillis
                                    } else if (mode == TaskAlertMode.NONE) {
                                        reminderTimeMillis = 0L
                                    }
                                },
                                label = { Text(mode.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = when (mode) {
                                        TaskAlertMode.CALL -> EmeraldSync.copy(alpha = 0.22f)
                                        TaskAlertMode.POPUP -> ElectricIndigo.copy(alpha = 0.22f)
                                        TaskAlertMode.NOTIFICATION -> CyanAccent.copy(alpha = 0.22f)
                                        TaskAlertMode.NONE -> MaterialTheme.colorScheme.surface
                                    }
                                ),
                                modifier = Modifier.testTag("alert_mode_${mode.name.lowercase()}")
                            )
                        }
                    }

                    // Quick Time Presets
                    Text(
                        text = "Quick Presets",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { isClockPickerOpen = true },
                            label = { Text("Pick Clock Time...") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.AccessTime,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                        AssistChip(
                            onClick = {
                                reminderTimeMillis = System.currentTimeMillis() + 10_000L
                                if (alertMode == TaskAlertMode.NONE) alertMode = TaskAlertMode.CALL
                            },
                            label = { Text("In 10 Secs") },
                            leadingIcon = {
                                Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(15.dp))
                            },
                            modifier = Modifier.testTag("reminder_preset_10s")
                        )
                        AssistChip(
                            onClick = {
                                reminderTimeMillis = System.currentTimeMillis() + 60_000L
                                if (alertMode == TaskAlertMode.NONE) alertMode = TaskAlertMode.CALL
                            },
                            label = { Text("In 1 Min") }
                        )
                        AssistChip(
                            onClick = {
                                reminderTimeMillis = System.currentTimeMillis() + 5 * 60_000L
                                if (alertMode == TaskAlertMode.NONE) alertMode = TaskAlertMode.POPUP
                            },
                            label = { Text("In 5 Mins") }
                        )
                        AssistChip(
                            onClick = {
                                reminderTimeMillis = System.currentTimeMillis() + 15 * 60_000L
                                if (alertMode == TaskAlertMode.NONE) alertMode = TaskAlertMode.NOTIFICATION
                            },
                            label = { Text("In 15 Mins") }
                        )
                        AssistChip(
                            onClick = {
                                reminderTimeMillis = System.currentTimeMillis() + 60 * 60_000L
                                if (alertMode == TaskAlertMode.NONE) alertMode = TaskAlertMode.NOTIFICATION
                            },
                            label = { Text("In 1 Hour") }
                        )
                        AssistChip(
                            onClick = {
                                val cal = Calendar.getInstance().apply {
                                    add(Calendar.DAY_OF_YEAR, 1)
                                    set(Calendar.HOUR_OF_DAY, 9)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                reminderTimeMillis = cal.timeInMillis
                                if (alertMode == TaskAlertMode.NONE) alertMode = TaskAlertMode.CALL
                            },
                            label = { Text("Tomorrow 9 AM") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Priority Selector
            Text(
                text = "Priority Level",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TaskPriority.entries.forEach { item ->
                    val isSelected = priority == item
                    val tone = priorityColor(item)
                    FilterChip(
                        selected = isSelected,
                        onClick = { priority = item },
                        label = { Text(item.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = tone.copy(alpha = 0.2f),
                            selectedLabelColor = tone
                        ),
                        modifier = Modifier.testTag("priority_chip_${item.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Workspace Selector
            Text(
                text = "Workspace",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                allWorkspaceChoices.forEach { ws ->
                    FilterChip(
                        selected = workspaceName.equals(ws, ignoreCase = true),
                        onClick = { workspaceName = ws },
                        label = { Text(ws) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Due Date Section with Calendar Picker
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Due Date",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (dueDateMillis > 0L) {
                    val formatted = remember(dueDateMillis) {
                        SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(Date(dueDateMillis))
                    }
                    Text(
                        text = "Due $formatted",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            val dayMs = 86_400_000L
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { isDueDateCalendarOpen = true },
                    label = { Text("Pick Calendar Date...") },
                    leadingIcon = {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.testTag("open_due_date_calendar_chip")
                )
                AssistChip(
                    onClick = { dueDateMillis = System.currentTimeMillis() + 3_600_000L },
                    label = { Text("Today") },
                    leadingIcon = {
                        Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
                AssistChip(
                    onClick = { dueDateMillis = System.currentTimeMillis() + dayMs },
                    label = { Text("Tomorrow") }
                )
                AssistChip(
                    onClick = { dueDateMillis = System.currentTimeMillis() + 3 * dayMs },
                    label = { Text("In 3 Days") }
                )
                AssistChip(
                    onClick = { dueDateMillis = System.currentTimeMillis() + 7 * dayMs },
                    label = { Text("Next Week") }
                )
                if (dueDateMillis > 0L) {
                    AssistChip(
                        onClick = { dueDateMillis = 0L },
                        label = { Text("Clear") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Subtasks Checklist
            Text(
                text = "Subtasks (${subtasks.size}/20)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            subtasks.forEachIndexed { index, entry ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = entry.completed,
                        onCheckedChange = { checked ->
                            subtasks[index] = entry.copy(completed = checked)
                        }
                    )
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { subtasks.removeAt(index) }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove subtask",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            if (subtasks.size < 20) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = newSubtaskText,
                        onValueChange = { if (it.length <= 180) newSubtaskText = it },
                        placeholder = { Text("Add a step…") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("new_subtask_input")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (newSubtaskText.isNotBlank() && subtasks.size < 20) {
                                subtasks.add(SubtaskEntry(false, newSubtaskText.trim()))
                                newSubtaskText = ""
                            }
                        },
                        modifier = Modifier.testTag("add_subtask_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add subtask")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tags Section
            Text(
                text = "Tags (${tags.size}/10)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tags.forEach { tag ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.clickable { tags.remove(tag) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#$tag",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove tag $tag",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (tags.size < 10) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = newTagText,
                        onValueChange = { if (it.length <= 35) newTagText = it.replace(" ", "") },
                        placeholder = { Text("Add tag (e.g. urgent, q4)…") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val clean = newTagText.trim().removePrefix("#")
                            if (clean.isNotBlank() && clean !in tags && tags.size < 10) {
                                tags.add(clean)
                                newTagText = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add tag")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        if (newSubtaskText.isNotBlank() && subtasks.size < 20) {
                            subtasks.add(SubtaskEntry(false, newSubtaskText.trim()))
                            newSubtaskText = ""
                        }
                        val cleanTag = newTagText.trim().removePrefix("#")
                        if (cleanTag.isNotBlank() && cleanTag !in tags && tags.size < 10) {
                            tags.add(cleanTag)
                            newTagText = ""
                        }
                        onSave(
                            title,
                            notes,
                            completed,
                            priority,
                            workspaceName,
                            dueDateMillis,
                            reminderTimeMillis,
                            alertMode,
                            subtasks.toList(),
                            tags.toList(),
                            pinned
                        )
                    },
                    enabled = title.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("save_task_button")
                ) {
                    Text(stringResource(R.string.save_task), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (isClockPickerOpen) {
        ClockSchedulePickerDialog(
            initialTimeMillis = reminderTimeMillis,
            initialAlertMode = if (alertMode == TaskAlertMode.NONE) TaskAlertMode.CALL else alertMode,
            onDismiss = { isClockPickerOpen = false },
            onConfirmSchedule = { selectedTimeMillis, selectedMode ->
                reminderTimeMillis = selectedTimeMillis
                alertMode = selectedMode
                if (dueDateMillis <= 0L) {
                    dueDateMillis = selectedTimeMillis
                }
                isClockPickerOpen = false
            }
        )
    }

    if (isDueDateCalendarOpen) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = if (dueDateMillis > 0L) dueDateMillis else System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { isDueDateCalendarOpen = false },
            confirmButton = {
                Button(
                    onClick = {
                        val utcMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = utcMillis
                        }
                        val localCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
                            set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
                            set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
                            set(Calendar.HOUR_OF_DAY, 18)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        dueDateMillis = localCal.timeInMillis
                        isDueDateCalendarOpen = false
                    }
                ) {
                    Text("Set Due Date")
                }
            },
            dismissButton = {
                TextButton(onClick = { isDueDateCalendarOpen = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * Full Material 3 Clock Dial / Digital Time & Date Picker Dialog so users can schedule
 * tasks at any exact clock time (HH:MM AM/PM) and calendar date with their preferred Alert Mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockSchedulePickerDialog(
    initialTimeMillis: Long,
    initialAlertMode: TaskAlertMode = TaskAlertMode.CALL,
    taskTitle: String? = null,
    onDismiss: () -> Unit,
    onConfirmSchedule: (reminderTimeMillis: Long, alertMode: TaskAlertMode) -> Unit
) {
    BackHandler { onDismiss() }

    val baseCalendar = remember(initialTimeMillis) {
        Calendar.getInstance().apply {
            if (initialTimeMillis > System.currentTimeMillis()) {
                timeInMillis = initialTimeMillis
            } else {
                add(Calendar.MINUTE, 5)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }
    }

    var selectedYear by remember { mutableIntStateOf(baseCalendar.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(baseCalendar.get(Calendar.MONTH)) }
    var selectedDay by remember { mutableIntStateOf(baseCalendar.get(Calendar.DAY_OF_MONTH)) }
    var selectedAlertMode by remember {
        mutableStateOf(
            if (initialAlertMode == TaskAlertMode.NONE) TaskAlertMode.CALL else initialAlertMode
        )
    }
    var useKeyboardInput by remember { mutableStateOf(false) }
    var showCalendarDialog by remember { mutableStateOf(false) }

    val timePickerState = rememberTimePickerState(
        initialHour = baseCalendar.get(Calendar.HOUR_OF_DAY),
        initialMinute = baseCalendar.get(Calendar.MINUTE),
        is24Hour = false
    )

    val computedScheduleMillis = remember(
        selectedYear,
        selectedMonth,
        selectedDay,
        timePickerState.hour,
        timePickerState.minute
    ) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, selectedYear)
            set(Calendar.MONTH, selectedMonth)
            set(Calendar.DAY_OF_MONTH, selectedDay)
            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
            set(Calendar.MINUTE, timePickerState.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // If user left date on Today and selected a time that already passed today, roll to Tomorrow
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            val today = Calendar.getInstance()
            val isSameDayAsToday = selectedYear == today.get(Calendar.YEAR) &&
                selectedMonth == today.get(Calendar.MONTH) &&
                selectedDay == today.get(Calendar.DAY_OF_MONTH)
            if (isSameDayAsToday) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        cal.timeInMillis
    }

    val previewFormatted = remember(computedScheduleMillis) {
        SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault())
            .format(Date(computedScheduleMillis))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Set Clock Schedule",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (!taskTitle.isNullOrBlank()) {
                        Text(
                            text = taskTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                IconButton(
                    onClick = { useKeyboardInput = !useKeyboardInput },
                    modifier = Modifier.testTag("toggle_clock_input_mode_button")
                ) {
                    Icon(
                        imageVector = if (useKeyboardInput) Icons.Default.AccessTime else Icons.Default.Keyboard,
                        contentDescription = if (useKeyboardInput) "Switch to clock dial" else "Switch to keyboard time input",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Live Scheduled Preview Banner
                Surface(
                    color = EmeraldSync.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            tint = EmeraldSync,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$previewFormatted (${selectedAlertMode.label})",
                            style = MaterialTheme.typography.labelLarge,
                            color = EmeraldSync,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Date Selector Row (Today, Tomorrow, Calendar Date)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val todayCal = Calendar.getInstance()
                    val tomorrowCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }

                    val isToday = selectedYear == todayCal.get(Calendar.YEAR) &&
                        selectedMonth == todayCal.get(Calendar.MONTH) &&
                        selectedDay == todayCal.get(Calendar.DAY_OF_MONTH)
                    val isTomorrow = selectedYear == tomorrowCal.get(Calendar.YEAR) &&
                        selectedMonth == tomorrowCal.get(Calendar.MONTH) &&
                        selectedDay == tomorrowCal.get(Calendar.DAY_OF_MONTH)

                    FilterChip(
                        selected = isToday,
                        onClick = {
                            val now = Calendar.getInstance()
                            selectedYear = now.get(Calendar.YEAR)
                            selectedMonth = now.get(Calendar.MONTH)
                            selectedDay = now.get(Calendar.DAY_OF_MONTH)
                        },
                        label = { Text("Today") }
                    )
                    FilterChip(
                        selected = isTomorrow,
                        onClick = {
                            val tom = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                            selectedYear = tom.get(Calendar.YEAR)
                            selectedMonth = tom.get(Calendar.MONTH)
                            selectedDay = tom.get(Calendar.DAY_OF_MONTH)
                        },
                        label = { Text("Tomorrow") }
                    )
                    AssistChip(
                        onClick = { showCalendarDialog = true },
                        label = {
                            val customCal = Calendar.getInstance().apply {
                                set(selectedYear, selectedMonth, selectedDay)
                            }
                            Text(SimpleDateFormat("MMM d", Locale.getDefault()).format(customCal.time))
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = "Pick date",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }

                // Material 3 Clock Dial OR Digital Keyboard Time Input
                if (useKeyboardInput) {
                    TimeInput(
                        state = timePickerState,
                        modifier = Modifier.testTag("clock_time_keyboard_input")
                    )
                } else {
                    TimePicker(
                        state = timePickerState,
                        modifier = Modifier.testTag("clock_time_dial_picker")
                    )
                }

                // Alert Style Selector Row
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "How to Notify at this Time:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            TaskAlertMode.CALL,
                            TaskAlertMode.POPUP,
                            TaskAlertMode.NOTIFICATION
                        ).forEach { mode ->
                            FilterChip(
                                selected = selectedAlertMode == mode,
                                onClick = { selectedAlertMode = mode },
                                label = { Text(mode.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = when (mode) {
                                        TaskAlertMode.CALL -> EmeraldSync.copy(alpha = 0.22f)
                                        TaskAlertMode.POPUP -> ElectricIndigo.copy(alpha = 0.22f)
                                        else -> CyanAccent.copy(alpha = 0.22f)
                                    }
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmSchedule(computedScheduleMillis, selectedAlertMode)
                },
                modifier = Modifier.testTag("confirm_clock_schedule_button")
            ) {
                Text("Save Schedule", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showCalendarDialog) {
        val initialCal = Calendar.getInstance().apply {
            set(selectedYear, selectedMonth, selectedDay)
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialCal.timeInMillis
        )
        DatePickerDialog(
            onDismissRequest = { showCalendarDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        val utcMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = utcMillis
                        }
                        selectedYear = utcCal.get(Calendar.YEAR)
                        selectedMonth = utcCal.get(Calendar.MONTH)
                        selectedDay = utcCal.get(Calendar.DAY_OF_MONTH)
                        showCalendarDialog = false
                    }
                ) {
                    Text("Select Date")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCalendarDialog = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun CreateWorkspaceDialog(
    existingWorkspaces: List<WorkspaceItem>,
    onDismiss: () -> Unit,
    onCreate: (name: String, colorHex: String, iconName: String) -> Unit,
    onDeleteWorkspace: (WorkspaceItem) -> Unit
) {
    BackHandler { onDismiss() }

    var name by remember { mutableStateOf("") }
    val palette = listOf("#6366F1", "#10B981", "#06B6D4", "#F59E0B", "#EF4444", "#EC4899")
    var selectedHex by remember { mutableStateOf(palette.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Manage Cloud Workspaces",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 45) name = it },
                    label = { Text("New Workspace Name") },
                    placeholder = { Text("e.g., Design System, Fitness") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("workspace_name_input")
                )

                Text(
                    text = "Accent Color",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    palette.forEach { hex ->
                        val color = parseHexColor(hex)
                        val isSelected = selectedHex == hex
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedHex = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected color",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                if (existingWorkspaces.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                    Text(
                        text = "Synced Workspaces",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        existingWorkspaces.forEach { ws ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(parseHexColor(ws.colorHex))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = ws.name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteWorkspace(ws) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete workspace ${ws.name}",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, selectedHex, "folder") },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("confirm_create_workspace")
            ) {
                Text("Add Workspace")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun SyncOverviewDialog(
    userEmail: String?,
    userDisplayName: String?,
    tasks: List<TaskItem>,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    val syncedDevices = remember(tasks) {
        (tasks.map { it.deviceName } + TaskItem.defaultDeviceName())
            .filter { it.isNotBlank() }
            .distinct()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.CloudDone,
                contentDescription = "Cloud Sync Status",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Real-Time Cloud Sync Active",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "All tasks, subtasks, workspaces, and scheduled call/pop-up reminders are synchronized instantly across your signed-in devices.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = userDisplayName?.takeIf { it.isNotBlank() } ?: "Google Account",
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (!userEmail.isNullOrBlank()) {
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "This device: ${TaskItem.defaultDeviceName()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = "Active Devices on Account (${syncedDevices.size})",
                    style = MaterialTheme.typography.labelLarge
                )
                syncedDevices.forEach { device ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = device,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        dismissButton = {
            Button(
                onClick = {
                    onDismiss()
                    onSignOut()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier.testTag("sign_out_button")
            ) {
                Text(stringResource(R.string.sign_out))
            }
        }
    )
}
