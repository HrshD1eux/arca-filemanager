package com.Hrshd1eux.filemanager.core.intent

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.Hrshd1eux.filemanager.core.navigation.Screen
import com.Hrshd1eux.filemanager.core.storage.util.FileTypeResolver
import java.io.File

/**
 * Robust resolver for incoming [android.content.Intent.ACTION_VIEW] URIs.
 *
 * Translates:
 * 1. file:// URIs into direct filesystem paths.
 * 2. content:// URIs pointing to shared storage (MediaStore, Downloads, DocumentProvider) into canonical paths.
 * 3. External content:// streams (e.g., WhatsApp, Telegram, Gmail, Chrome downloads) into isolated cache files,
 *    allowing ARCA's universal viewer and archive engines to inspect and render them directly.
 */
object ViewIntentResolver {

    private const val CACHE_SUBDIR = "external_views"

    /**
     * Resolves an incoming ACTION_VIEW [Uri] to a local on-disk file path.
     *
     * @param context Application context.
     * @param uri The incoming URI from the intent.
     * @return Absolute file path on local storage, or null if unresolvable.
     */
    fun resolveFilePath(context: Context, uri: Uri): String? {
        // 1. Direct file URI (e.g. file:///storage/emulated/0/Download/cat.jpg)
        if (uri.scheme.equals("file", ignoreCase = true)) {
            val path = uri.path
            if (!path.isNullOrBlank() && File(path).exists()) {
                return path
            }
        }

        // 2. Content URI (e.g. content://media/external/... or content://org.telegram...)
        if (uri.scheme.equals("content", ignoreCase = true)) {
            // Check if the URI path itself contains an absolute storage path
            val decodedPath = runCatching { Uri.decode(uri.toString()) }.getOrDefault(uri.toString())
            val storageIndex = decodedPath.indexOf("/storage/")
            if (storageIndex != -1) {
                val candidatePath = decodedPath.substring(storageIndex)
                    .substringBefore('?')
                    .substringBefore('#')
                    .substringBefore('&')
                if (File(candidatePath).exists()) {
                    return candidatePath
                }
            }

            // Check document ID patterns (e.g. raw:/storage/... or primary:...)
            val docPath = resolveDocumentIdPath(uri)
            if (docPath != null && File(docPath).exists()) {
                return docPath
            }

            // Query MediaStore DATA column if available
            val mediaPath = resolveMediaStoreData(context, uri)
            if (mediaPath != null && File(mediaPath).exists()) {
                return mediaPath
            }

            // Stream to cache file if directly inaccessible
            return copyUriToCache(context, uri)
        }

        return null
    }

    private fun resolveDocumentIdPath(uri: Uri): String? {
        val uriStr = uri.toString()
        if (uriStr.contains("%2Fstorage%2F") || uriStr.contains("/storage/")) {
            val decoded = runCatching { Uri.decode(uriStr) }.getOrNull() ?: uriStr
            val start = decoded.indexOf("/storage/")
            if (start != -1) {
                val path = decoded.substring(start)
                    .substringBefore(':')
                    .substringBefore('?')
                    .substringBefore('&')
                if (File(path).exists()) return path
            }
        }
        return null
    }

    private fun resolveMediaStoreData(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DATA),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (idx != -1) {
                        cursor.getString(idx)
                    } else null
                } else null
            }
        }.getOrNull()
    }

    private fun copyUriToCache(context: Context, uri: Uri): String? {
        return runCatching {
            val contentResolver = context.contentResolver
            var displayName: String? = null

            // Query DISPLAY_NAME from OpenableColumns
            runCatching {
                contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx != -1) {
                            displayName = cursor.getString(idx)
                        }
                    }
                }
            }

            if (displayName.isNullOrBlank()) {
                val mime = contentResolver.getType(uri)
                val ext = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "bin"
                displayName = "viewed_${System.currentTimeMillis()}.$ext"
            }

            // Sanitize display name against path traversal
            val safeName = displayName!!.replace(Regex("[/\\\\:*?\"<>|]"), "_")
            val cacheDir = File(context.cacheDir, CACHE_SUBDIR).apply { mkdirs() }
            pruneCache(cacheDir)

            val targetFile = File(cacheDir, safeName)
            contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (targetFile.exists() && targetFile.length() > 0) {
                targetFile.absolutePath
            } else null
        }.getOrNull()
    }

    private fun pruneCache(cacheDir: File) {
        runCatching {
            val files = cacheDir.listFiles() ?: return
            val oneDayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
            for (f in files) {
                if (f.lastModified() < oneDayAgo) {
                    f.delete()
                }
            }
        }
    }

    /**
     * Determines the optimal destination [Screen] within ARCA for the resolved file path.
     */
    fun resolveDestination(filePath: String): Screen {
        val file = File(filePath)
        val name = file.name
        return when {
            FileTypeResolver.canPreviewArchive(name) -> {
                Screen.ArchiveViewer(archivePath = file.absolutePath, title = name)
            }
            else -> {
                Screen.Preview(
                    initialPath = file.absolutePath,
                    categoryName = null,
                    directoryPath = file.parent
                )
            }
        }
    }
}
