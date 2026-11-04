package com.Hrshd1eux.filemanager.feature.browser.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode

@Composable
fun BatchRenameDialog(
    nodes: List<StorageNode>,
    onConfirmBatchRename: (List<Pair<StorageNode, String>>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var baseName by remember { mutableStateOf("") }
    val forbiddenChars = listOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
    val hasForbiddenChars = forbiddenChars.any { baseName.contains(it) }

    val errorMessage = when {
        baseName.isBlank() -> "Base name cannot be empty"
        hasForbiddenChars -> "Invalid characters: / \\ : * ? \" < > |"
        else -> null
    }

    val isValid = errorMessage == null

    val previewList = remember(baseName, nodes) {
        nodes.mapIndexed { index, node ->
            val ext = if (node.isDirectory) "" else {
                val dotIndex = node.name.lastIndexOf('.')
                if (dotIndex != -1) node.name.substring(dotIndex) else ""
            }
            val paddedIndex = (index + 1).toString().padStart(if (nodes.size > 99) 3 else 2, '0')
            val newName = if (baseName.isNotBlank()) "${baseName.trim()}_$paddedIndex$ext" else node.name
            node to newName
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Rename ${nodes.size} Items",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enter a base name. Files will be numbered sequentially while keeping their original extensions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = baseName,
                    onValueChange = { baseName = it },
                    label = { Text("Base Name") },
                    placeholder = { Text("e.g. Document, Photo") },
                    singleLine = true,
                    isError = !isValid && baseName.isNotEmpty(),
                    supportingText = {
                        if (errorMessage != null && baseName.isNotEmpty()) {
                            Text(errorMessage, color = MaterialTheme.colorScheme.error)
                        } else if (previewList.isNotEmpty()) {
                            Text(
                                "Preview: ${previewList.first().second}",
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValid) {
                        onConfirmBatchRename(previewList)
                    }
                },
                enabled = isValid
            ) {
                Text("Rename All")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        modifier = modifier
    )
}
