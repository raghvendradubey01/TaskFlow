package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.TaskAgentService
import com.example.data.model.SubtaskEntry
import com.example.data.model.TaskAlertMode
import com.example.data.model.TaskItem
import com.example.data.model.TaskPriority
import com.example.data.model.WorkspaceItem
import com.example.data.repository.TaskRepository
import com.example.notifications.IncomingAlertController
import com.google.firebase.Timestamp
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class TaskFilter(val label: String) {
    ALL("All Tasks"),
    ACTIVE("In Progress"),
    SCHEDULED("Scheduled Alerts"),
    COMPLETED("Completed"),
    PINNED("Pinned"),
    HIGH_PRIORITY("High & Urgent")
}

enum class TaskSort(val label: String) {
    RECENT("Recently Synced"),
    REMINDER_TIME("Next Alert Time"),
    PRIORITY("Highest Priority"),
    DUE_DATE("Due Date")
}

data class AgentChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val createdTask: TaskItem? = null
)

class TaskViewModel(
    private val repository: TaskRepository,
    private val currentUserId: String
) : ViewModel() {

    val tasksState: StateFlow<UiState<List<TaskItem>>> = repository.observeTasks(currentUserId)
        .map<List<TaskItem>, UiState<List<TaskItem>>> { list ->
            val sorted = list.sortedWith(
                compareByDescending<TaskItem> { it.pinned }
                    .thenBy { it.completed }
                    .thenByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
            )
            UiState.Success(sorted)
        }
        .catch { error ->
            Log.w(TAG, "Error observing tasks", error)
            emit(UiState.Error(error.localizedMessage ?: "Failed to sync tasks"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UiState.Loading
        )

    val workspacesState: StateFlow<UiState<List<WorkspaceItem>>> = repository.observeWorkspaces(currentUserId)
        .map<List<WorkspaceItem>, UiState<List<WorkspaceItem>>> { list ->
            val sorted = list.sortedBy { it.name.lowercase() }
            UiState.Success(sorted)
        }
        .catch { error ->
            Log.w(TAG, "Error observing workspaces", error)
            emit(UiState.Error(error.localizedMessage ?: "Failed to sync workspaces"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val _selectedWorkspace = MutableStateFlow("All")
    val selectedWorkspace: StateFlow<String> = _selectedWorkspace.asStateFlow()

    private val _selectedFilter = MutableStateFlow(TaskFilter.ALL)
    val selectedFilter: StateFlow<TaskFilter> = _selectedFilter.asStateFlow()

    private val _selectedSort = MutableStateFlow(TaskSort.RECENT)
    val selectedSort: StateFlow<TaskSort> = _selectedSort.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _transientMessage = MutableStateFlow<String?>(null)
    val transientMessage: StateFlow<String?> = _transientMessage.asStateFlow()

    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()

    // Active Incoming Call or Pop-Up Alert state
    private val _activeIncomingCallTask = MutableStateFlow<TaskItem?>(null)
    val activeIncomingCallTask: StateFlow<TaskItem?> = _activeIncomingCallTask.asStateFlow()

    private val _activePopupTask = MutableStateFlow<TaskItem?>(null)
    val activePopupTask: StateFlow<TaskItem?> = _activePopupTask.asStateFlow()

    private val _systemNotificationEvent = MutableStateFlow<TaskItem?>(null)
    val systemNotificationEvent: StateFlow<TaskItem?> = _systemNotificationEvent.asStateFlow()

    // AI Agent conversation state
    private val _agentMessages = MutableStateFlow(
        listOf(
            AgentChatMessage(
                isUser = false,
                text = "Hi! I'm your TaskFlow AI Reminder Agent. Tell me what you want to be notified about and when — I can schedule a Push Notification, a Pop-Up Alert, or call you with an Incoming AI Voice Call!"
            )
        )
    )
    val agentMessages: StateFlow<List<AgentChatMessage>> = _agentMessages.asStateFlow()

    private val _isAgentProcessing = MutableStateFlow(false)
    val isAgentProcessing: StateFlow<Boolean> = _isAgentProcessing.asStateFlow()

    // Tracks taskId -> reminderTimeMillis already fired in this session
    private val firedReminders = mutableMapOf<String, Long>()
    private val sessionStartMillis = System.currentTimeMillis()

    init {
        // Observe process-wide IncomingAlertController so alarms & background service triggers open immediately
        viewModelScope.launch {
            IncomingAlertController.incomingCallTask.collect { incomingTask ->
                if (incomingTask != null) {
                    firedReminders[incomingTask.id] = incomingTask.reminderTimeMillis
                    val fullMatch = (tasksState.value as? UiState.Success)?.data
                        ?.firstOrNull { it.id == incomingTask.id }
                    _activePopupTask.value = null
                    _activeIncomingCallTask.value = fullMatch ?: incomingTask
                } else {
                    _activeIncomingCallTask.value = null
                }
            }
        }

        viewModelScope.launch {
            IncomingAlertController.incomingPopupTask.collect { popupTask ->
                if (popupTask != null && _activeIncomingCallTask.value == null) {
                    firedReminders[popupTask.id] = popupTask.reminderTimeMillis
                    val fullMatch = (tasksState.value as? UiState.Success)?.data
                        ?.firstOrNull { it.id == popupTask.id }
                    _activePopupTask.value = fullMatch ?: popupTask
                }
            }
        }

        // Real-time ticker that checks scheduled tasks every second and fires Pop-Up, Call, or Notification
        viewModelScope.launch {
            while (true) {
                delay(1000L)
                val currentTasks = (tasksState.value as? UiState.Success)?.data.orEmpty()
                val now = System.currentTimeMillis()
                for (task in currentTasks) {
                    if (task.completed || task.reminderTimeMillis <= 0L) continue
                    val mode = task.alertModeEnum()
                    if (mode == TaskAlertMode.NONE) continue

                    // Fire if reminderTimeMillis was reached within the last 2 minutes and hasn't fired yet
                    val alreadyFiredForTimestamp = firedReminders[task.id] == task.reminderTimeMillis
                    val isDueNow = now >= task.reminderTimeMillis &&
                        (task.reminderTimeMillis >= sessionStartMillis - 60_000L)

                    if (isDueNow && !alreadyFiredForTimestamp) {
                        firedReminders[task.id] = task.reminderTimeMillis
                        when (mode) {
                            TaskAlertMode.CALL -> {
                                _activeIncomingCallTask.value = task
                                _systemNotificationEvent.value = task
                            }
                            TaskAlertMode.POPUP -> {
                                _activePopupTask.value = task
                                _systemNotificationEvent.value = task
                            }
                            TaskAlertMode.NOTIFICATION -> {
                                _systemNotificationEvent.value = task
                                _activePopupTask.value = task
                            }
                            TaskAlertMode.NONE -> Unit
                        }
                        break
                    }
                }
            }
        }
    }

    fun selectWorkspace(workspaceName: String) {
        _selectedWorkspace.value = workspaceName
    }

    fun selectFilter(filter: TaskFilter) {
        _selectedFilter.value = filter
    }

    fun selectSort(sort: TaskSort) {
        _selectedSort.value = sort
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearMessage() {
        _transientMessage.value = null
    }

    fun clearSystemNotificationEvent() {
        _systemNotificationEvent.value = null
    }

    fun triggerManualCall(task: TaskItem) {
        _activePopupTask.value = null
        _activeIncomingCallTask.value = task
    }

    fun triggerManualPopup(task: TaskItem) {
        _activeIncomingCallTask.value = null
        _activePopupTask.value = task
    }

    fun dismissIncomingCall() {
        _activeIncomingCallTask.value = null
        IncomingAlertController.clearIncomingCall()
    }

    fun dismissPopupAlert() {
        _activePopupTask.value = null
        IncomingAlertController.clearIncomingPopup()
    }

    fun snoozeTaskReminder(task: TaskItem, minutes: Int) {
        val snoozedTime = System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60_000L
        _activeIncomingCallTask.value = null
        _activePopupTask.value = null
        IncomingAlertController.clearIncomingCall()
        IncomingAlertController.clearIncomingPopup()
        viewModelScope.launch {
            val mode = if (task.alertModeEnum() == TaskAlertMode.NONE) {
                TaskAlertMode.CALL.name
            } else {
                task.alertMode
            }
            val result = repository.toggleTaskStatus(
                task = task,
                completed = false,
                pinned = task.pinned,
                reminderTimeMillis = snoozedTime,
                alertMode = mode
            )
            result.fold(
                onSuccess = {
                    _transientMessage.value = "Snoozed for $minutes minutes"
                },
                onFailure = { error ->
                    _transientMessage.value = error.localizedMessage ?: "Failed to snooze task"
                }
            )
        }
    }

    fun scheduleTaskClockTime(
        task: TaskItem,
        reminderTimeMillis: Long,
        alertMode: TaskAlertMode
    ) {
        viewModelScope.launch {
            val result = repository.toggleTaskStatus(
                task = task,
                completed = false,
                pinned = task.pinned,
                reminderTimeMillis = reminderTimeMillis,
                alertMode = alertMode.name
            )
            result.fold(
                onSuccess = {
                    val formatted = java.text.SimpleDateFormat(
                        "EEE, MMM d • h:mm a",
                        java.util.Locale.getDefault()
                    ).format(java.util.Date(reminderTimeMillis))
                    _transientMessage.value = "Scheduled for $formatted (${alertMode.label})"
                },
                onFailure = { error ->
                    _transientMessage.value = error.localizedMessage ?: "Failed to update schedule"
                }
            )
        }
    }

    fun sendAgentCommand(userPrompt: String, availableWorkspaces: List<String>) {
        if (userPrompt.isBlank()) return
        val userMsg = AgentChatMessage(isUser = true, text = userPrompt.trim())
        _agentMessages.value = _agentMessages.value + userMsg
        _isAgentProcessing.value = true

        viewModelScope.launch {
            val action = TaskAgentService.parseUserReminderRequest(
                userPrompt = userPrompt,
                availableWorkspaces = availableWorkspaces
            )
            val subtaskEntries = action.subtasks.map { SubtaskEntry(false, it) }

            val createResult = repository.createTask(
                title = action.title,
                notes = action.notes,
                priority = action.priority,
                workspaceName = action.workspaceName,
                dueDateMillis = action.reminderTimeMillis,
                reminderTimeMillis = action.reminderTimeMillis,
                alertMode = action.alertMode,
                subtasks = subtaskEntries,
                tags = action.tags,
                pinned = action.priority == TaskPriority.URGENT
            )

            _isAgentProcessing.value = false

            createResult.fold(
                onSuccess = { taskId ->
                    val createdTask = TaskItem(
                        id = taskId,
                        title = action.title,
                        notes = action.notes,
                        completed = false,
                        priority = action.priority.name,
                        workspaceName = action.workspaceName,
                        dueDateMillis = action.reminderTimeMillis,
                        reminderTimeMillis = action.reminderTimeMillis,
                        alertMode = action.alertMode.name,
                        subtasks = subtaskEntries.map { it.encode() },
                        tags = action.tags,
                        pinned = action.priority == TaskPriority.URGENT,
                        userId = currentUserId
                    )
                    val agentReplyMsg = AgentChatMessage(
                        isUser = false,
                        text = action.agentReply,
                        createdTask = createdTask
                    )
                    _agentMessages.value = _agentMessages.value + agentReplyMsg

                    if (action.triggerImmediately) {
                        firedReminders[taskId] = action.reminderTimeMillis
                        if (action.alertMode == TaskAlertMode.CALL) {
                            _activeIncomingCallTask.value = createdTask
                        } else {
                            _activePopupTask.value = createdTask
                        }
                    }
                },
                onFailure = { error ->
                    val errMsg = AgentChatMessage(
                        isUser = false,
                        text = "I couldn't save that task right now: ${error.localizedMessage ?: "Unknown error"}"
                    )
                    _agentMessages.value = _agentMessages.value + errMsg
                }
            )
        }
    }

    fun createTask(
        title: String,
        notes: String,
        priority: TaskPriority,
        workspaceName: String,
        dueDateMillis: Long,
        reminderTimeMillis: Long = 0L,
        alertMode: TaskAlertMode = TaskAlertMode.NONE,
        subtasks: List<SubtaskEntry>,
        tags: List<String>,
        pinned: Boolean,
        onSuccess: () -> Unit = {}
    ) {
        if (title.isBlank()) {
            _transientMessage.value = "Task title cannot be empty"
            return
        }
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.createTask(
                title = title,
                notes = notes,
                priority = priority,
                workspaceName = workspaceName.ifBlank { "Inbox" },
                dueDateMillis = dueDateMillis,
                reminderTimeMillis = reminderTimeMillis,
                alertMode = alertMode,
                subtasks = subtasks,
                tags = tags,
                pinned = pinned
            )
            _isMutating.value = false
            result.fold(
                onSuccess = {
                    val alertSuffix = if (reminderTimeMillis > 0L && alertMode != TaskAlertMode.NONE) {
                        " with ${alertMode.label}"
                    } else ""
                    _transientMessage.value = "Task synced to cloud$alertSuffix"
                    onSuccess()
                },
                onFailure = { error ->
                    _transientMessage.value = error.localizedMessage ?: "Failed to create task"
                }
            )
        }
    }

    fun updateTask(
        taskId: String,
        title: String,
        notes: String,
        completed: Boolean,
        priority: TaskPriority,
        workspaceName: String,
        dueDateMillis: Long,
        reminderTimeMillis: Long = 0L,
        alertMode: TaskAlertMode = TaskAlertMode.NONE,
        subtasks: List<SubtaskEntry>,
        tags: List<String>,
        pinned: Boolean,
        onSuccess: () -> Unit = {}
    ) {
        if (title.isBlank()) {
            _transientMessage.value = "Task title cannot be empty"
            return
        }
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.updateTask(
                taskId = taskId,
                title = title,
                notes = notes,
                completed = completed,
                priority = priority,
                workspaceName = workspaceName.ifBlank { "Inbox" },
                dueDateMillis = dueDateMillis,
                reminderTimeMillis = reminderTimeMillis,
                alertMode = alertMode,
                subtasks = subtasks,
                tags = tags,
                pinned = pinned
            )
            _isMutating.value = false
            result.fold(
                onSuccess = {
                    _transientMessage.value = "Changes synced across devices"
                    onSuccess()
                },
                onFailure = { error ->
                    _transientMessage.value = error.localizedMessage ?: "Failed to update task"
                }
            )
        }
    }

    fun toggleTaskCompletion(task: TaskItem) {
        if (_activeIncomingCallTask.value?.id == task.id) {
            _activeIncomingCallTask.value = null
            IncomingAlertController.clearIncomingCall()
        }
        if (_activePopupTask.value?.id == task.id) {
            _activePopupTask.value = null
            IncomingAlertController.clearIncomingPopup()
        }
        viewModelScope.launch {
            val result = repository.toggleTaskStatus(
                task = task,
                completed = !task.completed,
                pinned = task.pinned
            )
            result.onFailure { error ->
                _transientMessage.value = error.localizedMessage ?: "Failed to update task status"
            }
        }
    }

    fun toggleTaskPin(task: TaskItem) {
        viewModelScope.launch {
            val result = repository.toggleTaskStatus(
                task = task,
                completed = task.completed,
                pinned = !task.pinned
            )
            result.onFailure { error ->
                _transientMessage.value = error.localizedMessage ?: "Failed to update pin status"
            }
        }
    }

    fun toggleSubtask(task: TaskItem, subtaskIndex: Int) {
        val currentSubtasks = task.decodedSubtasks().toMutableList()
        if (subtaskIndex !in currentSubtasks.indices) return
        val target = currentSubtasks[subtaskIndex]
        currentSubtasks[subtaskIndex] = target.copy(completed = !target.completed)
        val allCompleted = currentSubtasks.isNotEmpty() && currentSubtasks.all { it.completed }

        viewModelScope.launch {
            val result = repository.updateTask(
                taskId = task.id,
                title = task.title,
                notes = task.notes,
                completed = if (allCompleted) true else task.completed,
                priority = task.priorityEnum(),
                workspaceName = task.workspaceName,
                dueDateMillis = task.dueDateMillis,
                reminderTimeMillis = task.reminderTimeMillis,
                alertMode = task.alertModeEnum(),
                subtasks = currentSubtasks,
                tags = task.tags,
                pinned = task.pinned
            )
            result.onFailure { error ->
                _transientMessage.value = error.localizedMessage ?: "Failed to sync subtask"
            }
        }
    }

    fun deleteTask(taskId: String) {
        if (_activeIncomingCallTask.value?.id == taskId) {
            _activeIncomingCallTask.value = null
        }
        if (_activePopupTask.value?.id == taskId) {
            _activePopupTask.value = null
        }
        viewModelScope.launch {
            val result = repository.deleteTask(taskId)
            result.fold(
                onSuccess = { _transientMessage.value = "Task removed from all devices" },
                onFailure = { error ->
                    _transientMessage.value = error.localizedMessage ?: "Failed to delete task"
                }
            )
        }
    }

    fun createWorkspace(name: String, colorHex: String, iconName: String, onSuccess: () -> Unit = {}) {
        if (name.isBlank()) {
            _transientMessage.value = "Workspace name cannot be empty"
            return
        }
        viewModelScope.launch {
            val result = repository.createWorkspace(name = name, colorHex = colorHex, iconName = iconName)
            result.fold(
                onSuccess = {
                    _selectedWorkspace.value = name.trim().take(50)
                    _transientMessage.value = "Workspace '${name.trim()}' created"
                    onSuccess()
                },
                onFailure = { error ->
                    _transientMessage.value = error.localizedMessage ?: "Failed to create workspace"
                }
            )
        }
    }

    fun deleteWorkspace(workspaceItem: WorkspaceItem) {
        viewModelScope.launch {
            val result = repository.deleteWorkspace(workspaceItem.id)
            result.fold(
                onSuccess = {
                    if (_selectedWorkspace.value == workspaceItem.name) {
                        _selectedWorkspace.value = "All"
                    }
                    _transientMessage.value = "Workspace deleted"
                },
                onFailure = { error ->
                    _transientMessage.value = error.localizedMessage ?: "Failed to delete workspace"
                }
            )
        }
    }

    fun seedStarterTasks() {
        viewModelScope.launch {
            _isMutating.value = true
            val now = System.currentTimeMillis()
            val dayMs = 86_400_000L

            repository.createWorkspace("Work", "#6366F1", "work")
            repository.createWorkspace("Personal", "#10B981", "person")
            repository.createWorkspace("Launch Q4", "#06B6D4", "rocket")

            repository.createTask(
                title = "Finalize cross-device product architecture",
                notes = "Review real-time Firestore snapshot listener latency across mobile and tablet form factors.",
                priority = TaskPriority.URGENT,
                workspaceName = "Launch Q4",
                dueDateMillis = now + dayMs,
                reminderTimeMillis = now + 120_000L,
                alertMode = TaskAlertMode.CALL,
                subtasks = listOf(
                    SubtaskEntry(true, "Audit zero-trust security rules"),
                    SubtaskEntry(true, "Verify offline-to-online delta sync"),
                    SubtaskEntry(false, "Sign off on tablet two-pane layout")
                ),
                tags = listOf("architecture", "cloud", "q4"),
                pinned = true
            )

            repository.createTask(
                title = "Prepare weekly sprint retrospective deck",
                notes = "Highlight velocity metrics, resolved blockers, and upcoming UI polish milestones.",
                priority = TaskPriority.HIGH,
                workspaceName = "Work",
                dueDateMillis = now + 2 * dayMs,
                reminderTimeMillis = now + 300_000L,
                alertMode = TaskAlertMode.POPUP,
                subtasks = listOf(
                    SubtaskEntry(true, "Collect team metrics"),
                    SubtaskEntry(false, "Draft key takeaways slide")
                ),
                tags = listOf("sprint", "team"),
                pinned = false
            )

            repository.createTask(
                title = "Evening 5K tempo run & mobility session",
                notes = "Keep heart rate in Zone 3 and log recovery hydration.",
                priority = TaskPriority.MEDIUM,
                workspaceName = "Personal",
                dueDateMillis = now + dayMs,
                reminderTimeMillis = now + 900_000L,
                alertMode = TaskAlertMode.NOTIFICATION,
                subtasks = listOf(
                    SubtaskEntry(false, "10-min dynamic warm-up"),
                    SubtaskEntry(false, "5km tempo run"),
                    SubtaskEntry(false, "Foam roll & stretch")
                ),
                tags = listOf("fitness", "habit"),
                pinned = false
            )
            _isMutating.value = false
            _transientMessage.value = "Starter workspaces & tasks synced!"
        }
    }

    private companion object {
        const val TAG = "TaskViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
