package com.Hrshd1eux.filemanager.core.operations

import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import com.Hrshd1eux.filemanager.core.operations.queue.OperationManager
import com.Hrshd1eux.filemanager.core.operations.queue.OperationStatus
import com.Hrshd1eux.filemanager.core.operations.queue.OperationType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OperationManagerTest {

    private lateinit var operationManager: OperationManager

    @Before
    fun setUp() {
        operationManager = OperationManager(
            ioDispatcher = Dispatchers.Unconfined,
            scope = CoroutineScope(Dispatchers.Unconfined)
        )
    }

    @Test
    fun enqueue_singleTask_executesAndCompletes() = runTest {
        var taskExecuted = false

        operationManager.enqueue(
            title = "Extracting archive.zip",
            type = OperationType.EXTRACT
        ) { onProgress ->
            onProgress(OperationProgress(bytesProcessed = 50, totalBytes = 100))
            taskExecuted = true
        }

        assertTrue(taskExecuted)
        assertNull(operationManager.activeOperation.value)
        assertTrue(operationManager.waitingQueue.value.isEmpty())
        assertEquals(1, operationManager.completedHistory.value.size)
        assertEquals(OperationStatus.COMPLETED, operationManager.completedHistory.value.first().status)
    }

    @Test
    fun enqueue_multipleTasks_executesInStrictFifoOrder() = runTest {
        val executionOrder = mutableListOf<String>()
        val blocker1 = CompletableDeferred<Unit>()

        operationManager.enqueue("Task 1", OperationType.EXTRACT) {
            blocker1.await()
            executionOrder.add("Task 1")
        }

        operationManager.enqueue("Task 2", OperationType.COMPRESS) {
            executionOrder.add("Task 2")
        }

        // Task 1 is active, Task 2 is waiting
        assertEquals("Task 1", operationManager.activeOperation.value?.title)
        assertEquals(1, operationManager.waitingQueue.value.size)
        assertEquals("Task 2", operationManager.waitingQueue.value.first().title)

        // Release Task 1
        blocker1.complete(Unit)

        assertEquals(listOf("Task 1", "Task 2"), executionOrder)
        assertNull(operationManager.activeOperation.value)
        assertTrue(operationManager.waitingQueue.value.isEmpty())
        assertEquals(2, operationManager.completedHistory.value.size)
    }

    @Test
    fun cancel_waitingTask_removesFromQueueWithoutExecuting() = runTest {
        val blocker1 = CompletableDeferred<Unit>()
        var task2Executed = false

        operationManager.enqueue("Task 1", OperationType.COPY) {
            blocker1.await()
        }

        val task2Id = operationManager.enqueue("Task 2", OperationType.MOVE) {
            task2Executed = true
        }

        assertEquals(1, operationManager.waitingQueue.value.size)

        // Cancel task 2 while task 1 is running
        operationManager.cancel(task2Id)

        assertTrue(operationManager.waitingQueue.value.isEmpty())

        // Complete task 1
        blocker1.complete(Unit)

        assertFalse("Cancelled task must not execute", task2Executed)
        assertTrue(operationManager.completedHistory.value.any { it.id == task2Id && it.status == OperationStatus.CANCELLED })
    }

    @Test
    fun cancelActive_abortsRunningTaskAndExecutesNextWaiting() = runTest {
        val blocker1 = CompletableDeferred<Unit>()
        var task2Executed = false

        operationManager.enqueue("Task 1", OperationType.EXTRACT) {
            blocker1.await()
        }

        operationManager.enqueue("Task 2", OperationType.COMPRESS) {
            task2Executed = true
        }

        assertEquals("Task 1", operationManager.activeOperation.value?.title)

        // Cancel active task 1
        operationManager.cancelActive()

        assertTrue(task2Executed)
        assertNull(operationManager.activeOperation.value)
        assertEquals(2, operationManager.completedHistory.value.size)
        assertTrue(operationManager.completedHistory.value.any { it.title == "Task 1" && it.status == OperationStatus.CANCELLED })
        assertTrue(operationManager.completedHistory.value.any { it.title == "Task 2" && it.status == OperationStatus.COMPLETED })
    }

    @Test
    fun cancelAll_cancelsActiveAndClearsWaitingQueue() = runTest {
        val blocker1 = CompletableDeferred<Unit>()

        operationManager.enqueue("Task 1", OperationType.EXTRACT) { blocker1.await() }
        operationManager.enqueue("Task 2", OperationType.COMPRESS) {}
        operationManager.enqueue("Task 3", OperationType.DELETE) {}

        assertEquals(2, operationManager.waitingQueue.value.size)

        operationManager.cancelAll()

        assertNull(operationManager.activeOperation.value)
        assertTrue(operationManager.waitingQueue.value.isEmpty())
        assertEquals(3, operationManager.completedHistory.value.size)
        assertTrue(operationManager.completedHistory.value.all { it.status == OperationStatus.CANCELLED })
    }
}
