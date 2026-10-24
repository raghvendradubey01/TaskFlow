package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.SubtaskEntry
import com.example.data.model.TaskAlertMode
import com.example.data.model.TaskPriority
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TaskRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createTask_validPayload_createsDocumentAndReturnsId() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = TaskRepository(firestore, auth)
        val expectedReminderTime = 1893456000000L

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createTask(
                title = TASK_TITLE,
                notes = "Sync across phone and tablet",
                priority = TaskPriority.HIGH,
                workspaceName = "Work",
                reminderTimeMillis = expectedReminderTime,
                alertMode = TaskAlertMode.CALL,
                subtasks = listOf(SubtaskEntry(false, "Verify cloud sync")),
                tags = listOf("sync", "mobile")
            )
        }
        assertTrue("Expected task creation to succeed", createResult.isSuccess)
        val taskId = createResult.getOrThrow()
        assertTrue(taskId.isNotEmpty())

        val fetched = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getTaskById(aliceUid, taskId).getOrThrow()
        }
        assertNotNull(fetched)
        assertEquals(TASK_TITLE, fetched?.title)
        assertEquals(aliceUid, fetched?.userId)
        assertEquals(expectedReminderTime, fetched?.reminderTimeMillis)
        assertEquals(TaskAlertMode.CALL.name, fetched?.alertMode)
    }

    @Test
    fun getUserTasks_authenticatedOwner_returnsMatchingTasks() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = TaskRepository(firestore, auth)
        val taskId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createTask(title = TASK_TITLE).getOrThrow()
        }

        val tasksResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getUserTasks(aliceUid)
        }
        assertTrue(tasksResult.isSuccess)
        assertTrue(tasksResult.getOrThrow().any { it.id == taskId })
    }

    @Test
    fun observeTasks_authenticatedOwner_emitsRealtimeUpdates() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = TaskRepository(firestore, auth)
        val taskId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createTask(title = TASK_TITLE).getOrThrow()
        }

        val emittedTasks = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeTasks(aliceUid).first { list -> list.any { it.id == taskId } }
        }
        assertTrue(emittedTasks.any { it.id == taskId })
    }

    @Test
    fun getTaskById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = TaskRepository(firestore, auth)
        val taskId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createTask(title = TASK_TITLE).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = TaskRepository(firestore, auth)
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getTaskById(aliceUid, taskId)
        }
        assertTrue("Expected cross-user getTaskById to fail", result.isFailure)
        val exception = result.exceptionOrNull() as? FirebaseFirestoreException
        assertNotNull("Expected FirebaseFirestoreException", exception)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, exception?.code)
    }

    @Test
    fun observeTasks_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = TaskRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeTasks().first()
            }
            fail("Expected FirebaseFirestoreException for unauthenticated user")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }

    @Test
    fun workspaceCrud_and_crossUserIsolation_enforced() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = TaskRepository(firestore, auth)

        val wsId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createWorkspace("Product Launch", "#10B981", "rocket").getOrThrow()
        }
        val workspaces = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeWorkspaces(aliceUid).first { list -> list.any { it.id == wsId } }
        }
        assertTrue(workspaces.any { it.id == wsId && it.name == "Product Launch" })

        val deleteResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.deleteWorkspace(wsId)
        }
        assertTrue(deleteResult.isSuccess)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@test.com"
        const val BOB_EMAIL = "bob@test.com"
        const val TASK_TITLE = "Sample Cloud Task"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
