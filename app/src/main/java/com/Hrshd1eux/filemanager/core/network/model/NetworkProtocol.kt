package com.Hrshd1eux.filemanager.core.network.model

/**
 * Supported network storage protocols in ARCA:
 *
 * NetworkProvider
 *        │
 *        ├── FTP
 *        ├── FTPS
 *        ├── SFTP
 *        ├── SMB
 *        └── WebDAV
 */
enum class NetworkProtocol(
    val scheme: String,
    val defaultPort: Int,
    val displayName: String,
    val isEncryptedByDefault: Boolean,
    val supportedAuthTypes: Set<NetworkAuthType>
) {
    /**
     * File Transfer Protocol (RFC 959).
     * Cleartext control and data channels over TCP port 21.
     * Insecure on untrusted networks; suitable only for isolated LANs.
     */
    FTP(
        scheme = "ftp",
        defaultPort = 21,
        displayName = "FTP",
        isEncryptedByDefault = false,
        supportedAuthTypes = setOf(NetworkAuthType.ANONYMOUS, NetworkAuthType.PASSWORD)
    ),

    /**
     * FTP Secure / FTP over TLS (RFC 4217).
     * TLS-encrypted control and data channels (PROT P).
     * Explicit mode (port 21 via AUTH TLS) or Implicit mode (port 990).
     */
    FTPS(
        scheme = "ftps",
        defaultPort = 21,
        displayName = "FTPS (FTP over TLS)",
        isEncryptedByDefault = true,
        supportedAuthTypes = setOf(NetworkAuthType.PASSWORD, NetworkAuthType.CERTIFICATE)
    ),

    /**
     * SSH File Transfer Protocol (SFTP).
     * Encrypted subsystem running over an SSHv2 transport on port 22.
     * Completely distinct from FTP; supports password and SSH Key (RSA/Ed25519) authentication.
     */
    SFTP(
        scheme = "sftp",
        defaultPort = 22,
        displayName = "SFTP (SSH File Transfer)",
        isEncryptedByDefault = true,
        supportedAuthTypes = setOf(NetworkAuthType.PASSWORD, NetworkAuthType.KEY_PAIR)
    ),

    /**
     * Server Message Block (SMB / Samba).
     * Standard protocol for Windows file sharing, NAS devices, and Unix Samba servers.
     * Port 445 (TCP). ARCA strictly mandates SMB2 / SMB3 dialects; SMB1 is rejected.
     */
    SMB(
        scheme = "smb",
        defaultPort = 445,
        displayName = "SMB / Samba (SMB2/SMB3)",
        isEncryptedByDefault = false,
        supportedAuthTypes = setOf(
            NetworkAuthType.ANONYMOUS,
            NetworkAuthType.PASSWORD,
            NetworkAuthType.DOMAIN_NTLM
        )
    ),

    /**
     * Web Distributed Authoring and Versioning (RFC 4918).
     * HTTP / HTTPS extension for remote collaborative file management.
     * Port 80 (HTTP) or Port 443 (HTTPS). Used by Nextcloud, ownCloud, NAS WebDAV.
     */
    WEBDAV(
        scheme = "webdav",
        defaultPort = 80,
        displayName = "WebDAV (HTTP/HTTPS)",
        isEncryptedByDefault = false,
        supportedAuthTypes = setOf(
            NetworkAuthType.ANONYMOUS,
            NetworkAuthType.PASSWORD,
            NetworkAuthType.TOKEN
        )
    )
}
