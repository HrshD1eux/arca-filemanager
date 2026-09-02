package com.Hrshd1eux.filemanager.core.operations.util

import java.io.File

/**
 * Resolves destination filename collisions by generating unique indexed filenames.
 */
object ConflictHelper {

    fun getNonConflictingDestination(destinationDir: File, originalName: String): File {
        val target = File(destinationDir, originalName)
        if (!target.exists()) return target

        val isDirectory = target.isDirectory
        val baseName: String
        val extensionSuffix: String

        if (isDirectory || !originalName.contains('.')) {
            baseName = originalName
            extensionSuffix = ""
        } else {
            baseName = originalName.substringBeforeLast('.')
            extensionSuffix = "." + originalName.substringAfterLast('.')
        }

        var counter = 1
        while (true) {
            val candidateName = "$baseName ($counter)$extensionSuffix"
            val candidateFile = File(destinationDir, candidateName)
            if (!candidateFile.exists()) {
                return candidateFile
            }
            counter++
        }
    }
}
