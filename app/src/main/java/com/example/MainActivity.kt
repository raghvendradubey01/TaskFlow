package com.example

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.auth.configureAppCheck
import com.example.auth.signOutUser
import com.example.data.model.TaskAlertMode
import com.example.data.model.TaskItem
import com.example.data.repository.TaskRepository
import com.example.notifications.IncomingAlertController
import com.example.notifications.TaskReminderManager
import com.example.ui.auth.AuthScreen
import com.example.ui.screens.TaskDashboardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.TaskViewModel
import com.example.voice.TaskFlowVoiceService
import com.example.voice.VoiceAssistantController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        configureLockScreenAndWakeFlags()
        enableEdgeToEdge()
        handleIncomingIntent(intent)
        setContent {
            MyApplicationTheme {
                AppNavigation()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun configureLockScreenAndWakeFlags() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (_: Exception) {
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        val taskId = intent.getStringExtra(TaskReminderManager.EXTRA_TASK_ID)
        if (!taskId.isNullOrBlank()) {
            val title = intent.getStringExtra(TaskReminderManager.EXTRA_TASK_TITLE) ?: "Scheduled Task"
            val notes = intent.getStringExtra(TaskReminderManager.EXTRA_TASK_NOTES).orEmpty()
            val workspace = intent.getStringExtra(TaskReminderManager.EXTRA_TASK_WORKSPACE) ?: "Inbox"
            val alertMode = intent.getStringExtra(TaskReminderManager.EXTRA_ALERT_MODE) ?: TaskAlertMode.CALL.name
            val reminderMillis = intent.getLongExtra(TaskReminderManager.EXTRA_REMINDER_MILLIS, System.currentTimeMillis())
            val subtasks = intent.getStringArrayListExtra(TaskReminderManager.EXTRA_SUBTASKS_RAW)?.toList().orEmpty()
            val callAction = intent.getStringExtra(TaskReminderManager.EXTRA_CALL_ACTION)
                ?: TaskReminderManager.CALL_ACTION_SHOW

            val task = TaskItem(
                id = taskId,
                title = title,
                notes = notes,
                workspaceName = workspace,
                alertMode = alertMode,
                reminderTimeMillis = reminderMillis,
                dueDateMillis = reminderMillis,
                subtasks = subtasks
            )

            when (TaskAlertMode.fromString(alertMode)) {
                TaskAlertMode.CALL -> {
                    IncomingAlertController.triggerIncomingCall(
                        context = this,
                        task = task,
                        autoAnswer = callAction == TaskReminderManager.CALL_ACTION_ANSWER
                    )
                }
                TaskAlertMode.POPUP, TaskAlertMode.NOTIFICATION, TaskAlertMode.NONE -> {
                    IncomingAlertController.triggerIncomingPopup(
                        context = this,
                        task = task
                    )
                }
            }
            intent.removeExtra(TaskReminderManager.EXTRA_TASK_ID)
        }

        if (intent.getBooleanExtra(TaskFlowVoiceService.EXTRA_VOICE_WAKE_TRIGGERED, false)) {
            intent.removeExtra(TaskFlowVoiceService.EXTRA_VOICE_WAKE_TRIGGERED)
        }
    }
}

@Composable
fun AppNavigation(
    auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(
            onAuthSuccess = { /* AuthStateListener automatically updates currentUser */ }
        )
    } else {
        AuthenticatedAppContent(
            currentUserId = user.uid,
            userEmail = user.email,
            userDisplayName = user.displayName
        )
    }
}

@Composable
private fun AuthenticatedAppContent(
    currentUserId: String,
    userEmail: String?,
    userDisplayName: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val taskViewModel: TaskViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                TaskViewModel(
                    repository = TaskRepository(db, FirebaseAuth.getInstance()),
                    currentUserId = currentUserId
                )
            }
        }
    )

    TaskDashboardScreen(
        viewModel = taskViewModel,
        userEmail = userEmail,
        userDisplayName = userDisplayName,
        onSignOut = {
            VoiceAssistantController.stopBackgroundAssistant(context)
            signOutUser(
                context = context,
                credentialManager = credentialManager,
                onSignOutComplete = {},
                scope = scope
            )
        }
    )
}
