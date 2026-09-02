package com.Hrshd1eux.filemanager.core.operations.model

/**
 * Outcome of an executed file operation.
 */
sealed interface OperationResult {
    val filesProcessed: Int
    val bytesProcessed: Long

    data class Success(
        override val filesProcessed: Int,
        override val bytesProcessed: Long
    ) : OperationResult

    data class Failure(
        val error: Throwable,
        override val filesProcessed: Int,
        override val bytesProcessed: Long
    ) : OperationResult

    data class Cancelled(
        override val filesProcessed: Int,
        override val bytesProcessed: Long
    ) : OperationResult
}
