package com.Hrshd1eux.filemanager.core.network.model

/**
 * Declares the filesystem and transfer capabilities of a specific network provider.
 */
data class NetworkCapabilities(
    /** Indicates whether the protocol supports resuming interrupted file downloads/uploads. */
    val supportsResume: Boolean,

    /** Indicates whether remote files support random byte access (seeking) without downloading entire stream. */
    val supportsRandomAccess: Boolean,

    /** Indicates whether server-side file duplication is supported without round-trip data transfer. */
    val supportsServerSideCopy: Boolean,

    /** Indicates whether server-side move/rename is supported atomically. */
    val supportsServerSideMove: Boolean,

    /** Indicates whether the protocol transport is cryptographically encrypted (TLS/SSH). */
    val isEncryptedTransport: Boolean,

    /** Indicates whether the protocol requires a share/export name at the root (e.g. SMB `//host/share`). */
    val requiresShareRoot: Boolean = false
) {
    companion object {
        val FTP = NetworkCapabilities(
            supportsResume = true,
            supportsRandomAccess = false,
            supportsServerSideCopy = false,
            supportsServerSideMove = true,
            isEncryptedTransport = false
        )

        val FTPS = NetworkCapabilities(
            supportsResume = true,
            supportsRandomAccess = false,
            supportsServerSideCopy = false,
            supportsServerSideMove = true,
            isEncryptedTransport = true
        )

        val SFTP = NetworkCapabilities(
            supportsResume = true,
            supportsRandomAccess = true,
            supportsServerSideCopy = false,
            supportsServerSideMove = true,
            isEncryptedTransport = true
        )

        val SMB = NetworkCapabilities(
            supportsResume = true,
            supportsRandomAccess = true,
            supportsServerSideCopy = true,
            supportsServerSideMove = true,
            isEncryptedTransport = false, // Configurable via SMB3 encryption
            requiresShareRoot = true
        )

        val WEBDAV = NetworkCapabilities(
            supportsResume = true,
            supportsRandomAccess = false,
            supportsServerSideCopy = true,
            supportsServerSideMove = true,
            isEncryptedTransport = false // Configurable via HTTPS
        )
    }
}
