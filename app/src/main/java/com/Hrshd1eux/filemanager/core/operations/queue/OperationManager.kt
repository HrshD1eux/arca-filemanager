package com.Hrshd1eux.filemanager.core.operations.queue

import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OperationManager(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher)
) {

    private val mutex = Mutex()
    private var activeJob: Job? = null

    private val _activeOperation = MutableStateFlow<QueuedOperation?>(null)
    val activeOperation: StateFlow<QueuedOperation?> = _activeOperation.asStateFlow()

    private val _waitingQueue = MutableStateFlow<List<QueuedOperation>>(emptyList())
    val waitingQueue: StateFlow<List<QueuedOperation>> = _waitingQueue.asStateFlow()

    private val _completedHistory = MutableStateFlow<List<QueuedOperation>>(emptyList())
    val completedHistory: StateFlow<List<QueuedOperation>> = _completedHistory.asStateFlow()

    private val _operationCompletedEvents = MutableSharedFlow<QueuedOperation>()
    val operationCompletedEvents: SharedFlow<QueuedOperation> = _operationCompletedEvents.asSharedFlow()

    /**
     * Enqueues a new background operation in the FIFO queue.
     * Returns the unique ID of the queued task.
     */
    fun enqueue(
        title: String,
        type: OperationType,
        runBlock: suspend (onProgress: (OperationProgress) -> Unit) -> Unit
    ): String {
        val operation = QueuedOperation(
            title = title,
            type = type,
            status = OperationStatus.WAITING,
            runBlock = runBlock
        )

        scope.launch {
            mutex.withLock {
                _waitingQueue.value = _waitingQueue.value + operation
            }
            processNext()
        }
        return operation.id
    }

    /**
     * Cancels a specific operation by its ID (active or queued).
     */
    fun cancel(operationId: String) {
        scope.launch {
            mutex.withLock {
                val currentActive = _activeOperation.value
                if (currentActive?.id == operationId) {
                    activeJob?.cancel()
                    return@launch
                }

                val currentQueue = _waitingQueue.value
                val targetIndex = currentQueue.indexOfFirst { it.id == operationId }
                if (targetIndex != -1) {
                    val removed = currentQueue[targetIndex]
                    _waitingQueue.value = currentQueue.toMutableList().apply { removeAt(targetIndex) }
                    recordHistory(removed.copy(status = OperationStatus.CANCELLED))
                }
            }
        }
    }

    /**
     * Cancels the currently executing operation and immediately starts the next waiting task.
     */
    fun cancelActive() {
        activeJob?.cancel()
    }

    /**
     * Cancels the active task and clears all waiting tasks in the queue.
     */
    fun cancelAll() {
        scope.launch {
            mutex.withLock {
                val queued = _waitingQueue.value
                _waitingQueue.value = emptyList()
                queued.forEach {
                    recordHistory(it.copy(status = OperationStatus.CANCELLED))
                }
            }
            activeJob?.cancel()
        }
    }

    /**
     * Clears finished and failed items from the completed history list.
     */
    fun clearCompleted() {
        _completedHistory.value = emptyList()
    }

    private suspend fun processNext() {
        mutex.withLock {
            if (_activeOperation.value != null) {
                // A task is already running
                return
            }

            val queue = _waitingQueue.value
            if (queue.isEmpty()) {
                return
            }

            val nextOp = queue.first()
            _waitingQueue.value = queue.drop(1)
            _activeOperation.value = nextOp.copy(status = OperationStatus.RUNNING)

            activeJob = scope.launch {
                executeOperation(nextOp)
            }
        }
    }

    private suspend fun executeOperation(operation: QueuedOperation) {
        try {
            operation.runBlock?.invoke { progress ->
                _activeOperation.value = operation.copy(
                    status = OperationStatus.RUNNING,
                    progress = progress
                )
            }

            val completedOp = operation.copy(
                status = OperationStatus.COMPLETED,
                progress = _activeOperation.value?.progress ?: OperationProgress()
            )
            recordHistory(completedOp)
            _operationCompletedEvents.emit(completedOp)
        } catch (e: CancellationException) {
            val cancelledOp = operation.copy(status = OperationStatus.CANCELLED)
            recordHistory(cancelledOp)
            _operationCompletedEvents.emit(cancelledOp)
        } catch (e: Throwable) {
            val failedOp = operation.copy(
                status = OperationStatus.FAILED,
                errorMessage = e.localizedMessage ?: "Operation failed"
            )
            recordHistory(failedOp)
            _operationCompletedEvents.emit(failedOp)
        } finally {
            _activeOperation.value = null
            activeJob = null
            processNext()
        }
    }

    private fun recordHistory(operation: QueuedOperation) {
        val history = _completedHistory.value.toMutableList()
        history.add(0, operation) // LIFO for history
        if (history.size > MAX_HISTORY_SIZE) {
            _completedHistory.value = history.take(MAX_HISTORY_SIZE)
        } else {
            _completedHistory.value = history
        }
    }

    companion object {
        private const val MAX_HISTORY_SIZE = 25

        @Volatile
        private var INSTANCE: OperationManager? = null

        fun getInstance(ioDispatcher: CoroutineDispatcher = Dispatchers.IO): OperationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OperationManager(ioDispatcher).also { INSTANCE = it }
            }
        }
    }
}
