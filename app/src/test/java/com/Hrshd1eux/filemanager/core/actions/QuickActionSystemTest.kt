package com.Hrshd1eux.filemanager.core.actions

import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickActionSystemTest {

    @Test
    fun folderContext_resolvesExactActionsInOrder() {
        val folderNode = StorageNode(
            id = "/storage/emulated/0/Documents",
            name = "Documents",
            path = "/storage/emulated/0/Documents",
            size = 0L,
            lastModified = System.currentTimeMillis(),
            isDirectory = true
        )

        val context = QuickActionContext.fromNode(folderNode)
        assertEquals(QuickActionContext.FOLDER, context)

        val actions = context.getActions()
        val expectedActions = listOf(
            QuickActionType.OPEN_FOLDER,
            QuickActionType.MOVE,
            QuickActionType.COPY,
            QuickActionType.COMPRESS,
            QuickActionType.ENCRYPT,
            QuickActionType.SHARE
        )
        assertEquals(expectedActions, actions)

        val titles = actions.map { it.title }
        assertEquals(listOf("Open", "Move", "Copy", "Compress", "Encrypt", "Share"), titles)
    }

    @Test
    fun fileContext_resolvesExactActionsInOrder() {
        val fileNode = StorageNode(
            id = "/storage/emulated/0/Documents/report.pdf",
            name = "report.pdf",
            path = "/storage/emulated/0/Documents/report.pdf",
            size = 1024L,
            lastModified = System.currentTimeMillis(),
            isDirectory = false
        )

        val context = QuickActionContext.fromNode(fileNode)
        assertEquals(QuickActionContext.FILE, context)

        val actions = context.getActions()
        val expectedActions = listOf(
            QuickActionType.OPEN,
            QuickActionType.SHARE,
            QuickActionType.COPY,
            QuickActionType.MOVE,
            QuickActionType.COMPRESS,
            QuickActionType.ENCRYPT,
            QuickActionType.PROPERTIES
        )
        assertEquals(expectedActions, actions)

        val titles = actions.map { it.title }
        assertEquals(listOf("Open", "Share", "Copy", "Move", "Compress", "Encrypt", "Properties"), titles)
    }

    @Test
    fun archiveContext_resolvesExactActionsInOrder() {
        val archiveNode = StorageNode(
            id = "/storage/emulated/0/Download/bundle.7z",
            name = "bundle.7z",
            path = "/storage/emulated/0/Download/bundle.7z",
            size = 2048L,
            lastModified = System.currentTimeMillis(),
            isDirectory = false
        )

        val context = QuickActionContext.fromNode(archiveNode)
        assertEquals(QuickActionContext.ARCHIVE, context)

        val actions = context.getActions()
        val expectedActions = listOf(
            QuickActionType.OPEN_ARCHIVE,
            QuickActionType.EXTRACT,
            QuickActionType.TEST,
            QuickActionType.ADD,
            QuickActionType.ENCRYPT,
            QuickActionType.PROPERTIES
        )
        assertEquals(expectedActions, actions)

        val titles = actions.map { it.title }
        assertEquals(listOf("Open", "Extract", "Test", "Add", "Encrypt", "Properties"), titles)
    }

    @Test
    fun archiveContext_rarArchive_omitsAddAction_dueToReadOnlyBoundary() {
        val rarNode = StorageNode(
            id = "/storage/emulated/0/Download/backup.rar",
            name = "backup.rar",
            path = "/storage/emulated/0/Download/backup.rar",
            size = 2048L,
            lastModified = System.currentTimeMillis(),
            isDirectory = false
        )

        val context = QuickActionContext.fromNode(rarNode)
        assertEquals(QuickActionContext.ARCHIVE, context)

        val actions = context.getActions(rarNode)
        val expectedActions = listOf(
            QuickActionType.OPEN_ARCHIVE,
            QuickActionType.EXTRACT,
            QuickActionType.TEST,
            QuickActionType.ENCRYPT,
            QuickActionType.PROPERTIES
        )
        assertEquals(expectedActions, actions)
        org.junit.Assert.assertFalse(actions.contains(QuickActionType.ADD))
    }

    @Test
    fun archiveContext_isoArchive_omitsAddAction_dueToReadOnlyBoundary() {
        val isoNode = StorageNode(
            id = "/storage/emulated/0/Download/image.iso",
            name = "image.iso",
            path = "/storage/emulated/0/Download/image.iso",
            size = 4096L,
            lastModified = System.currentTimeMillis(),
            isDirectory = false
        )

        val context = QuickActionContext.fromNode(isoNode)
        assertEquals(QuickActionContext.ARCHIVE, context)

        val actions = context.getActions(isoNode)
        val expectedActions = listOf(
            QuickActionType.OPEN_ARCHIVE,
            QuickActionType.EXTRACT,
            QuickActionType.TEST,
            QuickActionType.ENCRYPT,
            QuickActionType.PROPERTIES
        )
        assertEquals(expectedActions, actions)
        org.junit.Assert.assertFalse(actions.contains(QuickActionType.ADD))
    }

    @Test
    fun contextDetection_variousArchiveExtensions() {
        val extensions = listOf("zip", "rar", "iso", "7z", "tar", "tar.gz", "tgz", "tar.bz2", "tbz2", "tar.xz", "txz")
        for (ext in extensions) {
            val node = StorageNode(
                id = "/test/file.$ext",
                name = "file.$ext",
                path = "/test/file.$ext",
                size = 100L,
                lastModified = 0L,
                isDirectory = false
            )
            assertEquals("Failed for extension: $ext", QuickActionContext.ARCHIVE, QuickActionContext.fromNode(node))
        }

        // Uppercase extensions
        val uppercaseNode = StorageNode(
            id = "/test/ARCHIVE.ZIP",
            name = "ARCHIVE.ZIP",
            path = "/test/ARCHIVE.ZIP",
            size = 100L,
            lastModified = 0L,
            isDirectory = false
        )
        assertEquals(QuickActionContext.ARCHIVE, QuickActionContext.fromNode(uppercaseNode))
    }

    @Test
    fun contextDetection_edgeCases() {
        // Directory named like an archive must be treated as FOLDER
        val dirWithArchiveName = StorageNode(
            id = "/test/folder.zip",
            name = "folder.zip",
            path = "/test/folder.zip",
            size = 0L,
            lastModified = 0L,
            isDirectory = true
        )
        assertEquals(QuickActionContext.FOLDER, QuickActionContext.fromNode(dirWithArchiveName))

        // Hidden file without extension
        val hiddenFile = StorageNode(
            id = "/test/.gitignore",
            name = ".gitignore",
            path = "/test/.gitignore",
            size = 50L,
            lastModified = 0L,
            isDirectory = false
        )
        assertEquals(QuickActionContext.FILE, QuickActionContext.fromNode(hiddenFile))
    }
}
