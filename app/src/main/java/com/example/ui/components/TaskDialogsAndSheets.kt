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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Event
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

            // Notification, Pop-Up & Incoming Call Alert Section
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
                                    TaskAlertMode.NONE -> Icons.Default.NotificationsOff
                                },
                                contentDescription = null,
                                tint = if (alertMode == TaskAlertMode.NONE) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    EmeraldSync
                                },
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Notify & Alert Style",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }

                        if (reminderTimeMillis > 0L && alertMode != TaskAlertMode.NONE) {
                            val timeFormatted = remember(reminderTimeMillis) {
                                SimpleDateFormat("MMM d, h:mm:ss a", Locale.getDefault())
                                    .format(Date(reminderTimeMillis))
                            }
                            Text(
                                text = timeFormatted,
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldSync
                            )
                        }
                    }

                    // Alert Mode Chips (None, Notification, Pop-Up, Incoming Call)
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
                                        reminderTimeMillis = System.currentTimeMillis() + 60_000L
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

                    // Reminder Time Presets
                    Text(
                        text = "When to Notify / Call",
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
                                }
                                reminderTimeMillis = cal.timeInMillis
                                if (alertMode == TaskAlertMode.NONE) alertMode = TaskAlertMode.CALL
                            },
                            label = { Text("Tomorrow 9 AM") }
                        )
                        if (reminderTimeMillis > 0L) {
                            AssistChip(
                                onClick = {
                                    reminderTimeMillis = 0L
                                    alertMode = TaskAlertMode.NONE
                                },
                                label = { Text("Clear") }
                            )
                        }
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

            // Due Date Quick Presets
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
                        SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(dueDateMillis))
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
