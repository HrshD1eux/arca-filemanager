package com.Hrshd1eux.filemanager.core.operations.queue

import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import java.util.UUID

enum class OperationType {
    EXTRACT,
    COMPRESS,
    COPY,
    MOVE,
    DELETE
}

enum class OperationStatus {
    WAITING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class QueuedOperation(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val type: OperationType,
    val status: OperationStatus = OperationStatus.WAITING,
    val progress: OperationProgress = OperationProgress(),
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    internal val runBlock: (suspend (onProgress: (OperationProgress) -> Unit) -> Unit)? = null
)
