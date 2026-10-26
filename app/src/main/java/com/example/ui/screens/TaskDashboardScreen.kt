package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.TaskAlertMode
import com.example.data.model.TaskItem
import com.example.data.model.TaskPriority
import com.example.data.model.WorkspaceItem
import com.example.notifications.TaskReminderManager
import com.example.ui.components.AiTaskAgentSheet
import com.example.ui.components.ClockSchedulePickerDialog
import com.example.ui.components.CreateWorkspaceDialog
import com.example.ui.components.HeyTaskFlowOrbOverlay
import com.example.ui.components.HeyTaskFlowVoiceBanner
import com.example.ui.components.IncomingTaskCallOverlay
import com.example.ui.components.SyncOverviewDialog
import com.example.ui.components.TaskEditorSheet
import com.example.ui.components.TaskPopupAlertDialog
import com.example.ui.components.parseHexColor
import com.example.ui.components.priorityColor
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.EmeraldSync
import com.example.ui.viewmodel.TaskFilter
import com.example.ui.viewmodel.TaskSort
import com.example.ui.viewmodel.TaskViewModel
import com.example.ui.viewmodel.UiState
import com.example.voice.VoiceAssistantController
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TaskDashboardScreen(
    viewModel: TaskViewModel,
    userEmail: String?,
    userDisplayName: String?,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val workspacesState by viewModel.workspacesState.collectAsStateWithLifecycle()
    val selectedWorkspace by viewModel.selectedWorkspace.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val selectedSort by viewModel.selectedSort.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val transientMessage by viewModel.transientMessage.collectAsStateWithLifecycle()

    val activeIncomingCallTask by viewModel.activeIncomingCallTask.collectAsStateWithLifecycle()
    val activePopupTask by viewModel.activePopupTask.collectAsStateWithLifecycle()
    val systemNotificationEvent by viewModel.systemNotificationEvent.collectAsStateWithLifecycle()
    val agentMessages by viewModel.agentMessages.collectAsStateWithLifecycle()
    val isAgentProcessing by viewModel.isAgentProcessing.collectAsStateWithLifecycle()

    val isVoiceServiceEnabled by VoiceAssistantController.isServiceEnabled.collectAsStateWithLifecycle()
    val voiceAssistantState by VoiceAssistantController.assistantState.collectAsStateWithLifecycle()
    val voiceLiveTranscript by VoiceAssistantController.liveTranscript.collectAsStateWithLifecycle()
    val voiceStatusMessage by VoiceAssistantController.statusMessage.collectAsStateWithLifecycle()
    val voiceLastCreatedTask by VoiceAssistantController.lastCreatedTask.collectAsStateWithLifecycle()
    val isVoiceOverlayVisible by VoiceAssistantController.isOverlayVisible.collectAsStateWithLifecycle()
    val voiceMicRmsLevel by VoiceAssistantController.micRmsLevel.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var isEditorOpen by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<TaskItem?>(null) }
    var clockSchedulingTask by remember { mutableStateOf<TaskItem?>(null) }
    var isWorkspaceDialogOpen by remember { mutableStateOf(false) }
    var isSyncDialogOpen by remember { mutableStateOf(false) }
    var isAgentSheetOpen by remember { mutableStateOf(false) }
    var wakeImmediatelyAfterPermission by remember { mutableStateOf(false) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            VoiceAssistantController.startBackgroundAssistant(
                context = context,
                wakeImmediately = wakeImmediatelyAfterPermission
            )
        }
        wakeImmediatelyAfterPermission = false
    }

    val startupPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] == true ||
            VoiceAssistantController.hasRecordAudioPermission(context)
        if (micGranted) {
            VoiceAssistantController.startBackgroundAssistant(context)
        }
    }

    val systemVoiceRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                .orEmpty()
            val spokenText = matches.firstOrNull()?.trim().orEmpty()
            if (spokenText.isNotBlank()) {
                val (matched, remainder) = VoiceAssistantController.extractWakeWordCommand(spokenText)
                val finalCommand = if (matched && remainder.isNotBlank()) remainder else spokenText
                VoiceAssistantController.submitSimulatedOrTypedVoiceCommand(context, finalCommand)
            }
        }
    }

    val launchSystemSpeechDialog = {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(
                    RecognizerIntent.EXTRA_PROMPT,
                    "Say your TaskFlow command (e.g., 'Call me at 5 PM for meeting')"
                )
            }
            systemVoiceRecognizerLauncher.launch(intent)
        } catch (_: Exception) {
            VoiceAssistantController.triggerWakeNow(context)
        }
    }

    LaunchedEffect(Unit) {
        TaskReminderManager.ensureNotificationChannel(context)
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !TaskReminderManager.hasNotificationPermission(context)
        ) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!VoiceAssistantController.hasRecordAudioPermission(context)) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (permissionsToRequest.isNotEmpty()) {
            startupPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
        if (VoiceAssistantController.hasRecordAudioPermission(context)) {
            VoiceAssistantController.startBackgroundAssistant(context)
        }
    }

    LaunchedEffect(systemNotificationEvent) {
        val task = systemNotificationEvent ?: return@LaunchedEffect
        TaskReminderManager.showSystemNotification(
            context = context,
            taskId = task.id,
            title = task.title,
            notes = task.notes,
            workspace = task.workspaceName,
            alertMode = task.alertMode
        )
        viewModel.clearSystemNotificationEvent()
    }

    LaunchedEffect(transientMessage) {
        val msg = transientMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    val allTasks = (tasksState as? UiState.Success)?.data.orEmpty()
    val customWorkspaces = (workspacesState as? UiState.Success)?.data.orEmpty()

    // Sync system alarms whenever tasks list updates
    LaunchedEffect(allTasks) {
        allTasks.forEach { task ->
            TaskReminderManager.scheduleReminderAlarm(context, task)
        }
    }

    val workspaceTabs by remember(allTasks, customWorkspaces) {
        derivedStateOf {
            val fromTasks = allTasks.map { it.workspaceName }.filter { it.isNotBlank() }
            val fromCustom = customWorkspaces.map { it.name }.filter { it.isNotBlank() }
            (listOf("All", "Inbox", "Work", "Personal") + fromCustom + fromTasks).distinct()
        }
    }

    val filteredTasks by remember(allTasks, selectedWorkspace, selectedFilter, selectedSort, searchQuery) {
        derivedStateOf {
            allTasks
                .filter { task ->
                    val matchesWorkspace = selectedWorkspace == "All" ||
                        task.workspaceName.equals(selectedWorkspace, ignoreCase = true)
                    val matchesFilter = when (selectedFilter) {
                        TaskFilter.ALL -> true
                        TaskFilter.ACTIVE -> !task.completed
                        TaskFilter.SCHEDULED -> task.hasActiveReminder()
                        TaskFilter.COMPLETED -> task.completed
                        TaskFilter.PINNED -> task.pinned
                        TaskFilter.HIGH_PRIORITY -> {
                            val p = task.priorityEnum()
                            p == TaskPriority.URGENT || p == TaskPriority.HIGH
                        }
                    }
                    val q = searchQuery.trim().lowercase()
                    val matchesSearch = q.isEmpty() ||
                        task.title.lowercase().contains(q) ||
                        task.notes.lowercase().contains(q) ||
                        task.workspaceName.lowercase().contains(q) ||
                        task.tags.any { it.lowercase().contains(q.removePrefix("#")) }
                    matchesWorkspace && matchesFilter && matchesSearch
                }
                .let { list ->
                    when (selectedSort) {
                        TaskSort.RECENT -> list.sortedWith(
                            compareByDescending<TaskItem> { it.pinned }
                                .thenBy { it.completed }
                                .thenByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                        )
                        TaskSort.REMINDER_TIME -> list.sortedWith(
                            compareBy<TaskItem> { it.completed }
                                .thenBy { if (it.reminderTimeMillis > 0L) it.reminderTimeMillis else Long.MAX_VALUE }
                                .thenByDescending { it.priorityEnum().sortWeight }
                        )
                        TaskSort.PRIORITY -> list.sortedWith(
                            compareBy<TaskItem> { it.completed }
                                .thenByDescending { it.pinned }
                                .thenByDescending { it.priorityEnum().sortWeight }
                        )
                        TaskSort.DUE_DATE -> list.sortedWith(
                            compareBy<TaskItem> { it.completed }
                                .thenBy { if (it.dueDateMillis > 0L) it.dueDateMillis else Long.MAX_VALUE }
                                .thenByDescending { it.priorityEnum().sortWeight }
                        )
                    }
                }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FloatingActionButton(
                        onClick = { isAgentSheetOpen = true },
                        containerColor = CyanAccent,
                        contentColor = Color(0xFF042F2E),
                        modifier = Modifier.testTag("open_ai_agent_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Reminder & Call Agent"
                        )
                    }

                    ExtendedFloatingActionButton(
                        onClick = {
                            editingTask = null
                            isEditorOpen = true
                        },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.new_task_fab), fontWeight = FontWeight.Bold) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("add_task_fab")
                    )
                }
            }
        ) { innerPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val isExpanded = maxWidth >= 720.dp

                if (isExpanded) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .width(300.dp)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            TopSyncHeader(
                                userDisplayName = userDisplayName,
                                onOpenAgentSheet = { isAgentSheetOpen = true },
                                onOpenSyncDialog = { isSyncDialogOpen = true },
                                onOpenWorkspaceDialog = { isWorkspaceDialogOpen = true }
                            )
                            HeyTaskFlowVoiceBanner(
                                isServiceEnabled = isVoiceServiceEnabled,
                                assistantState = voiceAssistantState,
                                statusMessage = voiceStatusMessage,
                                micRmsLevel = voiceMicRmsLevel,
                                onToggleBackgroundService = { enable ->
                                    if (enable) {
                                        if (VoiceAssistantController.hasRecordAudioPermission(context)) {
                                            VoiceAssistantController.startBackgroundAssistant(context)
                                        } else {
                                            wakeImmediatelyAfterPermission = false
                                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    } else {
                                        VoiceAssistantController.stopBackgroundAssistant(context)
                                    }
                                },
                                onSpeakNowClick = {
                                    if (VoiceAssistantController.hasRecordAudioPermission(context)) {
                                        VoiceAssistantController.triggerWakeNow(context)
                                    } else {
                                        wakeImmediatelyAfterPermission = true
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            )
                            SyncProductivitySummaryCard(
                                tasks = allTasks,
                                onOpenAgentSheet = { isAgentSheetOpen = true }
                            )
                            WorkspaceVerticalList(
                                workspaces = workspaceTabs,
                                customWorkspaces = customWorkspaces,
                                selectedWorkspace = selectedWorkspace,
                                tasks = allTasks,
                                onSelectWorkspace = viewModel::selectWorkspace,
                                onAddWorkspace = { isWorkspaceDialogOpen = true }
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            SearchAndFilterHeader(
                                searchQuery = searchQuery,
                                onSearchChange = viewModel::updateSearchQuery,
                                selectedFilter = selectedFilter,
                                onSelectFilter = viewModel::selectFilter,
                                selectedSort = selectedSort,
                                onSelectSort = viewModel::selectSort
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            TaskStreamContent(
                                tasksState = tasksState,
                                filteredTasks = filteredTasks,
                                onToggleComplete = viewModel::toggleTaskCompletion,
                                onTogglePin = viewModel::toggleTaskPin,
                                onToggleSubtask = viewModel::toggleSubtask,
                                onScheduleClockTime = { task -> clockSchedulingTask = task },
                                onTriggerCall = viewModel::triggerManualCall,
                                onTriggerPopup = viewModel::triggerManualPopup,
                                onEditTask = { task ->
                                    editingTask = task
                                    isEditorOpen = true
                                },
                                onDeleteTask = viewModel::deleteTask,
                                onSeedSampleTasks = viewModel::seedStarterTasks
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 600.dp)
                            .align(Alignment.TopCenter)
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 12.dp,
                                bottom = 96.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                TopSyncHeader(
                                    userDisplayName = userDisplayName,
                                    onOpenAgentSheet = { isAgentSheetOpen = true },
                                    onOpenSyncDialog = { isSyncDialogOpen = true },
                                    onOpenWorkspaceDialog = { isWorkspaceDialogOpen = true }
                                )
                            }
                            item {
                                HeyTaskFlowVoiceBanner(
                                    isServiceEnabled = isVoiceServiceEnabled,
                                    assistantState = voiceAssistantState,
                                    statusMessage = voiceStatusMessage,
                                    micRmsLevel = voiceMicRmsLevel,
                                    onToggleBackgroundService = { enable ->
                                        if (enable) {
                                            if (VoiceAssistantController.hasRecordAudioPermission(context)) {
                                                VoiceAssistantController.startBackgroundAssistant(context)
                                            } else {
                                                wakeImmediatelyAfterPermission = false
                                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        } else {
                                            VoiceAssistantController.stopBackgroundAssistant(context)
                                        }
                                    },
                                    onSpeakNowClick = {
                                        if (VoiceAssistantController.hasRecordAudioPermission(context)) {
                                            VoiceAssistantController.triggerWakeNow(context)
                                        } else {
                                            wakeImmediatelyAfterPermission = true
                                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                )
                            }
                            item {
                                SyncProductivitySummaryCard(
                                    tasks = allTasks,
                                    onOpenAgentSheet = { isAgentSheetOpen = true }
                                )
                            }
                            item {
                                WorkspaceHorizontalChips(
                                    workspaces = workspaceTabs,
                                    customWorkspaces = customWorkspaces,
                                    selectedWorkspace = selectedWorkspace,
                                    onSelectWorkspace = viewModel::selectWorkspace,
                                    onAddWorkspace = { isWorkspaceDialogOpen = true }
                                )
                            }
                            item {
                                SearchAndFilterHeader(
                                    searchQuery = searchQuery,
                                    onSearchChange = viewModel::updateSearchQuery,
                                    selectedFilter = selectedFilter,
                                    onSelectFilter = viewModel::selectFilter,
                                    selectedSort = selectedSort,
                                    onSelectSort = viewModel::selectSort
                                )
                            }

                            when (val state = tasksState) {
                                is UiState.Loading -> {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 48.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator()
                                        }
                                    }
                                }
                                is UiState.Error -> {
                                    item {
                                        ErrorBanner(message = state.message)
                                    }
                                }
                                is UiState.Success -> {
                                    if (filteredTasks.isEmpty()) {
                                        item {
                                            EmptyTasksCard(
                                                isFiltered = allTasks.isNotEmpty(),
                                                onSeedSampleTasks = viewModel::seedStarterTasks
                                            )
                                        }
                                    } else {
                                        items(
                                            items = filteredTasks,
                                            key = { it.id }
                                        ) { task ->
                                            TaskItemCard(
                                                task = task,
                                                onToggleComplete = { viewModel.toggleTaskCompletion(task) },
                                                onTogglePin = { viewModel.toggleTaskPin(task) },
                                                onToggleSubtask = { idx -> viewModel.toggleSubtask(task, idx) },
                                                onScheduleClockTime = { clockSchedulingTask = task },
                                                onTriggerCall = { viewModel.triggerManualCall(task) },
                                                onTriggerPopup = { viewModel.triggerManualPopup(task) },
                                                onEdit = {
                                                    editingTask = task
                                                    isEditorOpen = true
                                                },
                                                onDelete = { viewModel.deleteTask(task.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Siri / Gemini Live Style "Hey TaskFlow" Voice Assistant Overlay
        if (isVoiceOverlayVisible && activeIncomingCallTask == null) {
            HeyTaskFlowOrbOverlay(
                assistantState = voiceAssistantState,
                liveTranscript = voiceLiveTranscript,
                statusMessage = voiceStatusMessage,
                micRmsLevel = voiceMicRmsLevel,
                lastCreatedTask = voiceLastCreatedTask,
                onStartListeningNow = {
                    if (VoiceAssistantController.hasRecordAudioPermission(context)) {
                        VoiceAssistantController.triggerWakeNow(context)
                    } else {
                        wakeImmediatelyAfterPermission = true
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onLaunchSystemVoiceRecognizer = launchSystemSpeechDialog,
                onSubmitVoiceCommandText = { cmd ->
                    VoiceAssistantController.submitSimulatedOrTypedVoiceCommand(context, cmd)
                },
                onPreviewCallForTask = { task ->
                    viewModel.triggerManualCall(task)
                },
                onDismiss = {
                    VoiceAssistantController.dismissOverlay()
                }
            )
        }

        // Full-Screen Incoming AI Task Call Overlay
        val callTask = activeIncomingCallTask
        if (callTask != null) {
            IncomingTaskCallOverlay(
                task = callTask,
                onCompleteTask = { viewModel.toggleTaskCompletion(callTask) },
                onSnoozeMinutes = { mins -> viewModel.snoozeTaskReminder(callTask, mins) },
                onDismissCall = viewModel::dismissIncomingCall
            )
        }
    }

    // Pop-Up Reminder Alert Dialog
    val popupTask = activePopupTask
    if (popupTask != null && activeIncomingCallTask == null) {
        TaskPopupAlertDialog(
            task = popupTask,
            onCompleteTask = { viewModel.toggleTaskCompletion(popupTask) },
            onSnoozeMinutes = { mins -> viewModel.snoozeTaskReminder(popupTask, mins) },
            onSwitchToCall = { viewModel.triggerManualCall(popupTask) },
            onDismiss = viewModel::dismissPopupAlert
        )
    }

    // AI Reminder & Call Agent Sheet
    if (isAgentSheetOpen) {
        AiTaskAgentSheet(
            messages = agentMessages,
            isProcessing = isAgentProcessing,
            onSendPrompt = { prompt ->
                viewModel.sendAgentCommand(prompt, workspaceTabs.filter { it != "All" })
            },
            onTriggerCallPreview = { task ->
                isAgentSheetOpen = false
                viewModel.triggerManualCall(task)
            },
            onTriggerPopupPreview = { task ->
                isAgentSheetOpen = false
                viewModel.triggerManualPopup(task)
            },
            onDismiss = { isAgentSheetOpen = false }
        )
    }

    if (isEditorOpen) {
        TaskEditorSheet(
            initialTask = editingTask,
            availableWorkspaces = workspaceTabs.filter { it != "All" },
            defaultWorkspace = selectedWorkspace,
            onDismiss = {
                isEditorOpen = false
                editingTask = null
            },
            onSave = { title, notes, completed, priority, wsName, dueMillis, reminderMillis, alertMode, subtasks, tags, pinned ->
                val currentEditing = editingTask
                if (currentEditing == null) {
                    viewModel.createTask(
                        title = title,
                        notes = notes,
                        priority = priority,
                        workspaceName = wsName,
                        dueDateMillis = dueMillis,
                        reminderTimeMillis = reminderMillis,
                        alertMode = alertMode,
                        subtasks = subtasks,
                        tags = tags,
                        pinned = pinned,
                        onSuccess = { isEditorOpen = false }
                    )
                } else {
                    viewModel.updateTask(
                        taskId = currentEditing.id,
                        title = title,
                        notes = notes,
                        completed = completed,
                        priority = priority,
                        workspaceName = wsName,
                        dueDateMillis = dueMillis,
                        reminderTimeMillis = reminderMillis,
                        alertMode = alertMode,
                        subtasks = subtasks,
                        tags = tags,
                        pinned = pinned,
                        onSuccess = {
                            isEditorOpen = false
                            editingTask = null
                        }
                    )
                }
            },
            onDelete = { taskId -> viewModel.deleteTask(taskId) }
        )
    }

    if (isWorkspaceDialogOpen) {
        CreateWorkspaceDialog(
            existingWorkspaces = customWorkspaces,
            onDismiss = { isWorkspaceDialogOpen = false },
            onCreate = { name, colorHex, iconName ->
                viewModel.createWorkspace(name, colorHex, iconName) {
                    isWorkspaceDialogOpen = false
                }
            },
            onDeleteWorkspace = viewModel::deleteWorkspace
        )
    }

    if (isSyncDialogOpen) {
        SyncOverviewDialog(
            userEmail = userEmail,
            userDisplayName = userDisplayName,
            tasks = allTasks,
            onSignOut = onSignOut,
            onDismiss = { isSyncDialogOpen = false }
        )
    }

    val schedulingTask = clockSchedulingTask
    if (schedulingTask != null) {
        ClockSchedulePickerDialog(
            initialTimeMillis = schedulingTask.reminderTimeMillis,
            initialAlertMode = if (schedulingTask.alertModeEnum() == TaskAlertMode.NONE) {
                TaskAlertMode.CALL
            } else {
                schedulingTask.alertModeEnum()
            },
            taskTitle = schedulingTask.title,
            onDismiss = { clockSchedulingTask = null },
            onConfirmSchedule = { selectedMillis, selectedMode ->
                viewModel.scheduleTaskClockTime(
                    task = schedulingTask,
                    reminderTimeMillis = selectedMillis,
                    alertMode = selectedMode
                )
                clockSchedulingTask = null
            }
        )
    }
}

@Composable
private fun TopSyncHeader(
    userDisplayName: String?,
    onOpenAgentSheet: () -> Unit,
    onOpenSyncDialog: () -> Unit,
    onOpenWorkspaceDialog: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.width(10.dp))
                Surface(
                    color = EmeraldSync.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .clickable { onOpenSyncDialog() }
                        .testTag("sync_status_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(EmeraldSync)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.sync_live_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldSync
                        )
                    }
                }
            }
            Text(
                text = if (!userDisplayName.isNullOrBlank()) {
                    "Synced for $userDisplayName • ${TaskItem.defaultDeviceName()}"
                } else {
                    "Active on ${TaskItem.defaultDeviceName()}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onOpenAgentSheet,
                modifier = Modifier.testTag("open_ai_agent_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Reminder & Call Agent",
                    tint = CyanAccent
                )
            }
            IconButton(
                onClick = onOpenWorkspaceDialog,
                modifier = Modifier.testTag("manage_workspaces_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CreateNewFolder,
                    contentDescription = stringResource(R.string.new_workspace_button),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = onOpenSyncDialog,
                modifier = Modifier.testTag("account_sync_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = "Cloud Sync & Account",
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun SyncProductivitySummaryCard(
    tasks: List<TaskItem>,
    onOpenAgentSheet: () -> Unit
) {
    val totalCount = tasks.size
    val completedCount = tasks.count { it.completed }
    val scheduledAlertsCount = tasks.count { it.hasActiveReminder() }
    val deviceCount = remember(tasks) {
        (tasks.map { it.deviceName } + TaskItem.defaultDeviceName()).filter { it.isNotBlank() }.distinct().size
    }
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount.toFloat() else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            ElectricIndigo,
                            Color(0xFF312E81)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CROSS-DEVICE SYNC & ALERTS",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$completedCount of $totalCount tasks completed",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                    }
                    Surface(
                        color = Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Devices,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$deviceCount ${if (deviceCount == 1) "Device" else "Devices"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50)),
                    color = EmeraldSync,
                    trackColor = Color.White.copy(alpha = 0.2f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$scheduledAlertsCount Scheduled Call/Pop-Up Alerts",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Surface(
                        color = CyanAccent.copy(alpha = 0.24f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .clickable { onOpenAgentSheet() }
                            .testTag("summary_ai_agent_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Ask AI Agent to Notify/Call",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceHorizontalChips(
    workspaces: List<String>,
    customWorkspaces: List<WorkspaceItem>,
    selectedWorkspace: String,
    onSelectWorkspace: (String) -> Unit,
    onAddWorkspace: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        workspaces.forEach { ws ->
            val isSelected = selectedWorkspace.equals(ws, ignoreCase = true)
            val customColor = customWorkspaces.firstOrNull { it.name.equals(ws, ignoreCase = true) }?.colorHex
            FilterChip(
                selected = isSelected,
                onClick = { onSelectWorkspace(ws) },
                label = { Text(ws) },
                leadingIcon = if (customColor != null) {
                    {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(parseHexColor(customColor))
                        )
                    }
                } else null,
                modifier = Modifier.testTag("workspace_tab_${ws.lowercase()}")
            )
        }
        FilterChip(
            selected = false,
            onClick = onAddWorkspace,
            label = { Text("+ Workspace") }
        )
    }
}

@Composable
private fun WorkspaceVerticalList(
    workspaces: List<String>,
    customWorkspaces: List<WorkspaceItem>,
    selectedWorkspace: String,
    tasks: List<TaskItem>,
    onSelectWorkspace: (String) -> Unit,
    onAddWorkspace: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "WORKSPACES",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onAddWorkspace, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Add Workspace", modifier = Modifier.size(18.dp))
                }
            }
            workspaces.forEach { ws ->
                val isSelected = selectedWorkspace.equals(ws, ignoreCase = true)
                val count = if (ws == "All") tasks.size else tasks.count { it.workspaceName.equals(ws, ignoreCase = true) }
                val customHex = customWorkspaces.firstOrNull { it.name.equals(ws, ignoreCase = true) }?.colorHex
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectWorkspace(ws) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (customHex != null) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(parseHexColor(customHex))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = ws,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchAndFilterHeader(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedFilter: TaskFilter,
    onSelectFilter: (TaskFilter) -> Unit,
    selectedSort: TaskSort,
    onSelectSort: (TaskSort) -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text(stringResource(R.string.search_tasks_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            } else null,
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                focusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_input")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TaskFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { onSelectFilter(filter) },
                        label = { Text(filter.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}")
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { sortMenuExpanded = true },
                    modifier = Modifier.testTag("sort_tasks_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Sort tasks"
                    )
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    TaskSort.entries.forEach { sortOption ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = sortOption.label,
                                    fontWeight = if (selectedSort == sortOption) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onSelectSort(sortOption)
                                sortMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskStreamContent(
    tasksState: UiState<List<TaskItem>>,
    filteredTasks: List<TaskItem>,
    onToggleComplete: (TaskItem) -> Unit,
    onTogglePin: (TaskItem) -> Unit,
    onToggleSubtask: (TaskItem, Int) -> Unit,
    onScheduleClockTime: (TaskItem) -> Unit,
    onTriggerCall: (TaskItem) -> Unit,
    onTriggerPopup: (TaskItem) -> Unit,
    onEditTask: (TaskItem) -> Unit,
    onDeleteTask: (String) -> Unit,
    onSeedSampleTasks: () -> Unit
) {
    when (tasksState) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is UiState.Error -> {
            ErrorBanner(message = tasksState.message)
        }
        is UiState.Success -> {
            if (filteredTasks.isEmpty()) {
                EmptyTasksCard(
                    isFiltered = tasksState.data.isNotEmpty(),
                    onSeedSampleTasks = onSeedSampleTasks
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items = filteredTasks, key = { it.id }) { task ->
                        TaskItemCard(
                            task = task,
                            onToggleComplete = { onToggleComplete(task) },
                            onTogglePin = { onTogglePin(task) },
                            onToggleSubtask = { idx -> onToggleSubtask(task, idx) },
                            onScheduleClockTime = { onScheduleClockTime(task) },
                            onTriggerCall = { onTriggerCall(task) },
                            onTriggerPopup = { onTriggerPopup(task) },
                            onEdit = { onEditTask(task) },
                            onDelete = { onDeleteTask(task.id) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskItemCard(
    task: TaskItem,
    onToggleComplete: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleSubtask: (Int) -> Unit,
    onScheduleClockTime: () -> Unit,
    onTriggerCall: () -> Unit,
    onTriggerPopup: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val priorityTone = priorityColor(task.priorityEnum())
    val decodedSubtasks = remember(task.subtasks) { task.decodedSubtasks() }
    var subtasksExpanded by remember { mutableStateOf(true) }

    val cardBg by animateColorAsState(
        targetValue = if (task.completed) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "card_bg"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .testTag("task_item_card_${task.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onToggleComplete,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("toggle_complete_${task.id}")
                ) {
                    Icon(
                        imageVector = if (task.completed) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = if (task.completed) "Mark incomplete" else "Mark complete",
                        tint = if (task.completed) EmeraldSync else priorityTone,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = priorityTone.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = task.priorityEnum().label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = priorityTone,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = task.workspaceName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (task.completed) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
                    )

                    if (task.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = task.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onScheduleClockTime,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("schedule_clock_${task.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = "Set exact clock schedule time",
                            tint = if (task.reminderTimeMillis > 0L) EmeraldSync else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onTriggerCall,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("trigger_call_${task.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneInTalk,
                            contentDescription = "Simulate incoming task call",
                            tint = EmeraldSync,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onTriggerPopup,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("trigger_popup_${task.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Show task pop-up alert",
                            tint = CyanAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onTogglePin,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = if (task.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (task.pinned) "Unpin task" else "Pin task",
                            tint = if (task.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete task",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Subtasks Checklist Preview
            if (decodedSubtasks.isNotEmpty()) {
                val doneSteps = decodedSubtasks.count { it.completed }
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { subtasksExpanded = !subtasksExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Checklist ($doneSteps/${decodedSubtasks.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (subtasksExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle checklist",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(visible = subtasksExpanded) {
                    Column(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        decodedSubtasks.forEachIndexed { idx, step ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleSubtask(idx) }
                                    .padding(vertical = 2.dp)
                            ) {
                                Checkbox(
                                    checked = step.completed,
                                    onCheckedChange = { onToggleSubtask(idx) },
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = step.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (step.completed) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    textDecoration = if (step.completed) TextDecoration.LineThrough else TextDecoration.None
                                )
                            }
                        }
                    }
                }
            }

            // Footer: Scheduled Alert Badge, Due Date, Tags & Synced Device Attribution
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (task.reminderTimeMillis > 0L && task.alertModeEnum() != TaskAlertMode.NONE) {
                    val alertTimeText = remember(task.reminderTimeMillis) {
                        SimpleDateFormat("MMM d • h:mm a", Locale.getDefault()).format(Date(task.reminderTimeMillis))
                    }
                    val modeEnum = task.alertModeEnum()
                    Surface(
                        color = EmeraldSync.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.clickable { onScheduleClockTime() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (modeEnum == TaskAlertMode.CALL) Icons.Default.Call else Icons.Default.AccessTime,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = EmeraldSync
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${modeEnum.label} • $alertTimeText",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldSync
                            )
                        }
                    }
                } else if (!task.completed) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .clickable { onScheduleClockTime() }
                            .testTag("set_clock_chip_${task.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "+ Set Clock Time",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                if (task.dueDateMillis > 0L) {
                    val dateText = remember(task.dueDateMillis) {
                        SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(task.dueDateMillis))
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Event,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = dateText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                task.tags.forEach { tag ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "#$tag",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = task.deviceName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTasksCard(
    isFiltered: Boolean,
    onSeedSampleTasks: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CloudDone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = if (isFiltered) "No matching tasks in this view" else stringResource(R.string.empty_tasks_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (isFiltered) {
                    "Try switching workspace tabs or clearing your active filter."
                } else {
                    stringResource(R.string.empty_tasks_subtitle)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (!isFiltered) {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onSeedSampleTasks,
                    modifier = Modifier.testTag("seed_sample_tasks_button")
                ) {
                    Text(stringResource(R.string.load_sample_tasks))
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp)
        )
    }
}
