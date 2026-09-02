package com.Hrshd1eux.filemanager.feature.browser.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode

@Composable
fun RenameDialog(
    node: StorageNode,
    onConfirmRename: (newName: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initialSelection = if (!node.isDirectory && node.name.contains('.')) {
        val baseLength = node.name.substringBeforeLast('.').length
        TextRange(0, baseLength)
    } else {
        TextRange(0, node.name.length)
    }

    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = node.name, selection = initialSelection))
    }

    val forbiddenChars = listOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
    val trimmed = textFieldValue.text.trim()
    val hasForbiddenChars = forbiddenChars.any { textFieldValue.text.contains(it) }

    val errorMessage = when {
        trimmed.isEmpty() -> "Name cannot be empty"
        hasForbiddenChars -> "Invalid characters: / \\ : * ? \" < > |"
        trimmed == node.name -> null
        else -> null
    }

    val isValid = trimmed.isNotEmpty() && !hasForbiddenChars && trimmed != node.name

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Rename ${if (node.isDirectory) "Folder" else "File"}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = errorMessage != null && trimmed.isNotEmpty(),
                    supportingText = {
                        if (errorMessage != null) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmRename(trimmed) },
                enabled = isValid,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancel")
            }
        },
        modifier = modifier
    )
}
