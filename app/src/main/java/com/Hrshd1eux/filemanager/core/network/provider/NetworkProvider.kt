package com.Hrshd1eux.filemanager.core.network.provider

import com.Hrshd1eux.filemanager.core.network.model.NetworkCapabilities
import com.Hrshd1eux.filemanager.core.network.model.NetworkConnectionState
import com.Hrshd1eux.filemanager.core.network.model.NetworkConnectionTestResult
import com.Hrshd1eux.filemanager.core.network.model.NetworkNode
import com.Hrshd1eux.filemanager.core.network.model.NetworkProtocol
import com.Hrshd1eux.filemanager.core.network.model.NetworkServerConfig
import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream
import java.io.OutputStream

/**
 * Common contract for all remote network filesystem providers.
 *
 * NetworkProvider
 *        │
 *        ├── FTP
 *        ├── FTPS
 *        ├── SFTP
 *        ├── SMB
 *        └── WebDAV
 *
 * Strictly isolated from offline storage engines (POSIX, SAF, Vault).
 */
interface NetworkProvider {
    val protocol: NetworkProtocol
    val config: NetworkServerConfig
    val connectionState: StateFlow<NetworkConnectionState>
    val capabilities: NetworkCapabilities

    /**
     * Establishes a session with the remote server.
     */
    suspend fun connect(): Result<Unit>

    /**
     * Terminates the active session and releases underlying network resources.
     */
    suspend fun disconnect()

    /**
     * Tests connectivity and authentication without establishing a permanent session.
     */
    suspend fun testConnection(): Result<NetworkConnectionTestResult>

    /**
     * Lists child nodes in the specified remote directory.
     */
    suspend fun listChildren(remotePath: String): Result<List<NetworkNode>>

    /**
     * Retrieves metadata for a specific remote node.
     */
    suspend fun getNode(remotePath: String): Result<NetworkNode>

    /**
     * Opens an InputStream to read remote file content.
     */
    suspend fun openInputStream(remotePath: String): Result<InputStream>

    /**
     * Opens an OutputStream to write data to the remote server.
     */
    suspend fun openOutputStream(remotePath: String, append: Boolean = false): Result<OutputStream>

    /**
     * Creates a new directory at the specified remote path.
     */
    suspend fun createDirectory(remotePath: String): Result<Unit>

    /**
     * Deletes a remote file or empty directory.
     */
    suspend fun delete(remotePath: String): Result<Unit>

    /**
     * Renames or moves a remote file/directory.
     */
    suspend fun rename(remotePath: String, newName: String): Result<Unit>

    /**
     * Checks if a remote path exists.
     */
    suspend fun exists(remotePath: String): Boolean
}
