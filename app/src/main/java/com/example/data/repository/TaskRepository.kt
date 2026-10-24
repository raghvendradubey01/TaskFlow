package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.SubtaskEntry
import com.example.data.model.TaskAlertMode
import com.example.data.model.TaskItem
import com.example.data.model.TaskPriority
import com.example.data.model.WorkspaceItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class TaskRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        FirebaseAuth.getInstance()
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeTasks(userId: String = auth.currentUser?.uid ?: "unauthenticated"): Flow<List<TaskItem>> = callbackFlow {
        val targetUid = userId.ifBlank { "unauthenticated" }
        val path = "users/$targetUid/tasks"
        val registration = db.collection("users").document(targetUid).collection("tasks")
            .whereEqualTo("userId", targetUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, path, auth)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(
                        snapshot.toObjects(TaskItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    )
                }
            }
        awaitClose { registration.remove() }
    }

    fun observeWorkspaces(userId: String = auth.currentUser?.uid ?: "unauthenticated"): Flow<List<WorkspaceItem>> = callbackFlow {
        val targetUid = userId.ifBlank { "unauthenticated" }
        val path = "users/$targetUid/workspaces"
        val registration = db.collection("users").document(targetUid).collection("workspaces")
            .whereEqualTo("userId", targetUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, path, auth)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(
                        snapshot.toObjects(WorkspaceItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    )
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun getUserTasks(userId: String = auth.currentUser?.uid ?: "unauthenticated"): Result<List<TaskItem>> {
        val targetUid = userId.ifBlank { "unauthenticated" }
        val path = "users/$targetUid/tasks"
        return try {
            val snapshot = db.collection("users").document(targetUid).collection("tasks")
                .whereEqualTo("userId", targetUid)
                .get()
                .await()
            val items = snapshot.toObjects(TaskItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            Result.success(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path, auth)
            Result.failure(e)
        }
    }

    suspend fun getTaskById(ownerUserId: String, taskId: String): Result<TaskItem?> {
        val docRef = db.collection("users").document(ownerUserId).collection("tasks").document(taskId)
        return try {
            val snapshot = docRef.get().await()
            Result.success(
                snapshot.toObject(TaskItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun createTask(
        title: String,
        notes: String = "",
        priority: TaskPriority = TaskPriority.MEDIUM,
        workspaceName: String = "Inbox",
        dueDateMillis: Long = 0L,
        reminderTimeMillis: Long = 0L,
        alertMode: TaskAlertMode = TaskAlertMode.NONE,
        subtasks: List<SubtaskEntry> = emptyList(),
        tags: List<String> = emptyList(),
        pinned: Boolean = false,
        deviceName: String = TaskItem.defaultDeviceName()
    ): Result<String> {
        val uid = requireUserId()
        val tasksRef = db.collection("users").document(uid).collection("tasks")
        val docRef = tasksRef.document()
        val encodedSubtasks = subtasks
            .filter { it.title.isNotBlank() }
            .take(20)
            .map { it.encode() }
        val cleanTags = tags
            .map { it.trim().take(40) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(10)

        val effectiveAlertMode = if (reminderTimeMillis > 0L && alertMode == TaskAlertMode.NONE) {
            TaskAlertMode.NOTIFICATION
        } else if (reminderTimeMillis <= 0L) {
            TaskAlertMode.NONE
        } else {
            alertMode
        }

        val payload = mapOf(
            "title" to title.trim().take(200),
            "notes" to notes.trim().take(2000),
            "completed" to false,
            "priority" to priority.name,
            "workspaceName" to workspaceName.trim().ifBlank { "Inbox" }.take(50),
            "dueDateMillis" to dueDateMillis.coerceAtLeast(0L),
            "reminderTimeMillis" to reminderTimeMillis.coerceAtLeast(0L),
            "alertMode" to effectiveAlertMode.name,
            "subtasks" to encodedSubtasks,
            "tags" to cleanTags,
            "pinned" to pinned,
            "deviceName" to deviceName.trim().ifBlank { "Android Device" }.take(100),
            "userId" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun updateTask(
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
        deviceName: String = TaskItem.defaultDeviceName()
    ): Result<Unit> {
        val uid = requireUserId()
        require(taskId.isNotBlank()) { "Task ID must not be blank for update" }
        val docRef = db.collection("users").document(uid).collection("tasks").document(taskId)
        val encodedSubtasks = subtasks
            .filter { it.title.isNotBlank() }
            .take(20)
            .map { it.encode() }
        val cleanTags = tags
            .map { it.trim().take(40) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(10)

        val effectiveAlertMode = if (reminderTimeMillis > 0L && alertMode == TaskAlertMode.NONE) {
            TaskAlertMode.NOTIFICATION
        } else if (reminderTimeMillis <= 0L) {
            TaskAlertMode.NONE
        } else {
            alertMode
        }

        val updates = mapOf(
            "title" to title.trim().take(200),
            "notes" to notes.trim().take(2000),
            "completed" to completed,
            "priority" to priority.name,
            "workspaceName" to workspaceName.trim().ifBlank { "Inbox" }.take(50),
            "dueDateMillis" to dueDateMillis.coerceAtLeast(0L),
            "reminderTimeMillis" to reminderTimeMillis.coerceAtLeast(0L),
            "alertMode" to effectiveAlertMode.name,
            "subtasks" to encodedSubtasks,
            "tags" to cleanTags,
            "pinned" to pinned,
            "deviceName" to deviceName.trim().ifBlank { "Android Device" }.take(100),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun toggleTaskStatus(
        task: TaskItem,
        completed: Boolean = !task.completed,
        pinned: Boolean = task.pinned,
        reminderTimeMillis: Long = task.reminderTimeMillis,
        alertMode: String = task.alertMode,
        deviceName: String = TaskItem.defaultDeviceName()
    ): Result<Unit> {
        val uid = requireUserId()
        require(task.id.isNotBlank()) { "Task ID must not be blank" }
        val docRef = db.collection("users").document(uid).collection("tasks").document(task.id)
        val updates = mapOf(
            "completed" to completed,
            "pinned" to pinned,
            "reminderTimeMillis" to reminderTimeMillis.coerceAtLeast(0L),
            "alertMode" to TaskAlertMode.fromString(alertMode).name,
            "deviceName" to deviceName.trim().ifBlank { "Android Device" }.take(100),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun deleteTask(taskId: String): Result<Unit> {
        val uid = requireUserId()
        require(taskId.isNotBlank()) { "Task ID must not be blank" }
        val docRef = db.collection("users").document(uid).collection("tasks").document(taskId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun createWorkspace(
        name: String,
        colorHex: String = "#6366F1",
        iconName: String = "folder"
    ): Result<String> {
        val uid = requireUserId()
        val wsRef = db.collection("users").document(uid).collection("workspaces").document()
        val validHex = if (colorHex.matches(Regex("^#[0-9A-Fa-f]{6}$"))) colorHex else "#6366F1"
        val payload = mapOf(
            "name" to name.trim().take(50),
            "colorHex" to validHex,
            "iconName" to iconName.trim().ifBlank { "folder" }.take(40),
            "userId" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            wsRef.set(payload).await()
            Result.success(wsRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, wsRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun deleteWorkspace(workspaceId: String): Result<Unit> {
        val uid = requireUserId()
        require(workspaceId.isNotBlank()) { "Workspace ID must not be blank" }
        val docRef = db.collection("users").document(uid).collection("workspaces").document(workspaceId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            Result.failure(e)
        }
    }
}
