package com.Hrshd1eux.filemanager.feature.archive.components

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@Composable
fun CreateZipDialog(
    defaultName: String,
    onConfirmCreate: (archiveName: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initialName = if (defaultName.endsWith(".zip", ignoreCase = true)) defaultName else "$defaultName.zip"
    var nameState by remember { mutableStateOf(TextFieldValue(initialName, TextRange(0, initialName.substringBeforeLast(".zip").length))) }
    val focusRequester = remember { FocusRequester() }

    val rawName = nameState.text.trim()
    val isValid = rawName.isNotEmpty() && !rawName.contains('/') && !rawName.contains('\\')

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compress to ZIP") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Enter a name for the new archive:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = nameState,
                    onValueChange = { nameState = it },
                    singleLine = true,
                    isError = !isValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    label = { Text("Archive Name") },
                    supportingText = {
                        if (!isValid) {
                            Text("Name cannot be empty or contain slashes.")
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = if (rawName.endsWith(".zip", ignoreCase = true)) rawName else "$rawName.zip"
                    onConfirmCreate(finalName)
                },
                enabled = isValid,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Compress")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                Text("Cancel")
            }
        },
        modifier = modifier
    )
}
