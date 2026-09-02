package com.Hrshd1eux.filemanager.core.storage.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import java.io.File

/**
 * Utility to dispatch system sharing intents using secure FileProvider content URIs.
 */
object FileShareHelper {

    fun shareNodes(context: Context, nodes: List<StorageNode>) {
        if (nodes.isEmpty()) return

        val filesToShare = nodes.filter { !it.isDirectory && File(it.path).exists() }
        if (filesToShare.isEmpty()) {
            Toast.makeText(context, "No shareable files selected", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val authority = "${context.packageName}.fileprovider"
            val uris = ArrayList<Uri>()
            for (node in filesToShare) {
                val file = File(node.path)
                val uri = FileProvider.getUriForFile(context, authority, file)
                uris.add(uri)
            }

            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_STREAM, uris[0])
                    type = filesToShare[0].mimeType ?: "*/*"
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    type = "*/*"
                }
            }

            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent, "Share via"))
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to share: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
