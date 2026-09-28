package com.Hrshd1eux.filemanager.core.network.model

/**
 * Authentication methods supported across network storage protocols.
 */
enum class NetworkAuthType(val displayName: String) {
    /** No credentials required (e.g. anonymous FTP, guest SMB, public WebDAV). */
    ANONYMOUS("Anonymous / Guest"),

    /** Standard username and password authentication. */
    PASSWORD("Username & Password"),

    /** SSH Key Pair (OpenSSH / PEM RSA/Ed25519) with optional passphrase for SFTP. */
    KEY_PAIR("SSH Key Pair"),

    /** Client X.509 certificate / PKCS#12 for FTPS or WebDAV. */
    CERTIFICATE("Client Certificate"),

    /** Bearer / API Token for WebDAV. */
    TOKEN("Bearer Token"),

    /** Windows Domain / Workgroup NTLMv2 authentication for SMB. */
    DOMAIN_NTLM("Domain / NTLMv2")
}
