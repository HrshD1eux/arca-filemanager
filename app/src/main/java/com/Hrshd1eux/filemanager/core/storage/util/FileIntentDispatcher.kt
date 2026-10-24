package com.Hrshd1eux.filemanager.core.storage.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import java.io.File

/**
 * Dispatches Android system intents for file viewing, 'Open with', sharing, and 'Send to...'.
 * Leverages secure FileProvider content URIs with read permissions.
 */
object FileIntentDispatcher {

    /**
     * Directly views the file with the default registered handler application.
     */
    fun view(context: Context, node: StorageNode) {
        if (node.isDirectory) return
        val file = File(node.path)
        if (!file.exists()) {
            Toast.makeText(context, "File does not exist", Toast.LENGTH_SHORT).show()
            return
        }

        com.Hrshd1eux.filemanager.core.recents.repository.RecentsRepository.getInstance(context).recordAccess(node)

        val isApk = node.name.endsWith(".apk", ignoreCase = true) ||
            node.mimeType == com.Hrshd1eux.filemanager.core.storage.apk.ApkInstaller.MIME_TYPE_APK ||
            FileTypeResolver.resolveMimeType(node.name) == com.Hrshd1eux.filemanager.core.storage.apk.ApkInstaller.MIME_TYPE_APK

        if (isApk) {
            com.Hrshd1eux.filemanager.core.storage.apk.ApkInstaller.install(context, file).onFailure { e ->
                Toast.makeText(context, "Cannot launch installer: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
            return
        }

        try {
            val uri = getUriForNode(context, file)
            val mimeType = node.mimeType ?: FileTypeResolver.resolveMimeType(node.name)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // If no default handler, fallback to open with chooser
            openWith(context, node)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Opens the file by showing an explicit application chooser ("Open with").
     */
    fun openWith(context: Context, node: StorageNode) {
        if (node.isDirectory) return
        val file = File(node.path)
        if (!file.exists()) {
            Toast.makeText(context, "File does not exist", Toast.LENGTH_SHORT).show()
            return
        }

        com.Hrshd1eux.filemanager.core.recents.repository.RecentsRepository.getInstance(context).recordAccess(node)

        try {
            val uri = getUriForNode(context, file)
            val mimeType = node.mimeType ?: FileTypeResolver.resolveMimeType(node.name)

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(viewIntent, "Open with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app available to open this file", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Dispatches a single file share intent via Android system share sheet.
     */
    fun share(context: Context, node: StorageNode) {
        if (node.isDirectory) return
        val file = File(node.path)
        if (!file.exists()) {
            Toast.makeText(context, "File does not exist", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = getUriForNode(context, file)
            val mimeType = node.mimeType ?: FileTypeResolver.resolveMimeType(node.name)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uri)
                type = mimeType
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to share: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Dispatches a "Send to..." intent for the target file.
     */
    fun sendTo(context: Context, node: StorageNode) {
        if (node.isDirectory) return
        val file = File(node.path)
        if (!file.exists()) {
            Toast.makeText(context, "File does not exist", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = getUriForNode(context, file)
            val mimeType = node.mimeType ?: FileTypeResolver.resolveMimeType(node.name)

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uri)
                type = mimeType
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, "Send to…").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to send: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Dispatches multiple files sharing.
     */
    fun shareMultiple(context: Context, nodes: List<StorageNode>) {
        FileShareHelper.shareNodes(context, nodes)
    }

    private fun getUriForNode(context: Context, file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }
}
