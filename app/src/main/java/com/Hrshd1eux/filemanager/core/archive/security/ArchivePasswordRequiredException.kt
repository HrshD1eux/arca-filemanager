package com.Hrshd1eux.filemanager.core.archive.security

import java.io.IOException

/**
 * Exception thrown when an archive requires a password to read its encrypted headers
 * (encrypted filenames) or to decrypt entry content.
 */
class ArchivePasswordRequiredException(
    message: String = "Password required to access this encrypted archive",
    cause: Throwable? = null
) : IOException(message, cause)
