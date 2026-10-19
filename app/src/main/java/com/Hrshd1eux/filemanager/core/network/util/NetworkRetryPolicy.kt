package com.Hrshd1eux.filemanager.core.network.util

import com.Hrshd1eux.filemanager.core.network.model.NetworkException
import kotlinx.coroutines.delay
import java.io.IOException

/**
 * Declares the safety categorization of a network operation.
 */
enum class NetworkOperationKind {
    /**
     * Read-only, idempotent operations (listing directories, querying metadata, downloading files).
     * Safe to retry upon transient network or socket failures.
     */
    READ_IDEMPOTENT,

    /**
     * Mutating, potentially destructive operations (deleting files/directories, moving, renaming).
     * STRICT SECURITY MANDATE: NEVER automatically retry after a connection failure.
     * The server may have already processed or partially processed the command.
     */
    DESTRUCTIVE,

    /**
     * Stream write transfers (uploading files).
     * Requires partial-file cleanup on failure before any retry can be considered.
     */
    WRITE_TRANSFER
}

/**
 * Reliability engine managing network operation retries, backoff, and destructive operation guards.
 *
 * Enforces the core security mandate:
 * "Don't retry potentially destructive operations blindly after a connection failure."
 */
object NetworkRetryPolicy {

    /**
     * Executes a network operation with strict safety and reliability policies:
     * - [NetworkOperationKind.DESTRUCTIVE]: Exactly ONE attempt. If a connection drop, timeout,
     *   or network error occurs, it is wrapped in [NetworkException.NonRetriableOperationException]
     *   and thrown immediately. Automatic blind retries are strictly forbidden.
     * - [NetworkOperationKind.READ_IDEMPOTENT]: Retries up to [maxRetries] on transient connection drops
     *   using exponential backoff.
     * - [NetworkOperationKind.WRITE_TRANSFER]: Exactly ONE attempt. On failure, triggers [onFailureCleanup]
     *   to remove partial remote artifacts before propagating the error.
     */
    suspend fun <T> executeSafely(
        kind: NetworkOperationKind,
        operationName: String,
        targetPath: String = "",
        maxRetries: Int = 2,
        initialDelayMs: Long = 500L,
        onFailureCleanup: (suspend () -> Unit)? = null,
        block: suspend () -> T
    ): T {
        if (kind == NetworkOperationKind.DESTRUCTIVE) {
            return try {
                block()
            } catch (t: Throwable) {
                if (isTransientNetworkError(t)) {
                    throw NetworkException.NonRetriableOperationException(
                        operation = operationName,
                        targetPath = targetPath,
                        message = "Network connection failed during destructive operation: ${t.localizedMessage}",
                        cause = t
                    )
                }
                throw t
            }
        }

        if (kind == NetworkOperationKind.WRITE_TRANSFER) {
            return try {
                block()
            } catch (t: Throwable) {
                try {
                    onFailureCleanup?.invoke()
                } catch (_: Exception) {
                    // Suppress cleanup failure to surface original root cause
                }
                throw t
            }
        }

        // READ_IDEMPOTENT: Safe retry loop
        var attemptsLeft = maxRetries.coerceAtLeast(0)
        var currentDelay = initialDelayMs

        while (true) {
            try {
                return block()
            } catch (t: Throwable) {
                val canRetry = attemptsLeft > 0 && isTransientNetworkError(t)
                if (!canRetry) {
                    throw t
                }
                attemptsLeft--
                delay(currentDelay)
                currentDelay *= 2
            }
        }
    }

    /**
     * Determines whether an exception represents a transient network/connection error.
     */
    fun isTransientNetworkError(throwable: Throwable): Boolean {
        if (throwable is NetworkException.NetworkConnectionException ||
            throwable is NetworkException.NetworkTimeoutException ||
            throwable is NetworkException.HostUnreachableException) {
            return true
        }
        val className = throwable.javaClass.simpleName
        if (className.contains("Socket") || className.contains("Timeout")) {
            return true
        }
        if (throwable is IOException) {
            val msg = throwable.message?.lowercase() ?: ""
            return msg.contains("timeout") ||
                msg.contains("connection reset") ||
                msg.contains("broken pipe") ||
                msg.contains("connection refused") ||
                msg.contains("unexpected end of stream")
        }
        return false
    }
}
