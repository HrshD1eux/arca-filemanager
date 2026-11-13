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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.R

@Composable
fun CreateFolderDialog(
    onConfirmCreate: (folderName: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val defaultName = stringResource(R.string.create_folder_default_name)
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = defaultName,
                selection = TextRange(0, defaultName.length)
            )
        )
    }

    val forbiddenChars = listOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
    val trimmed = textFieldValue.text.trim()
    val hasForbiddenChars = forbiddenChars.any { textFieldValue.text.contains(it) }

    val emptyError = stringResource(R.string.create_folder_error_empty)
    val forbiddenError = stringResource(R.string.rename_error_forbidden)

    val errorMessage = when {
        trimmed.isEmpty() -> emptyError
        hasForbiddenChars -> forbiddenError
        else -> null
    }

    val isValid = trimmed.isNotEmpty() && !hasForbiddenChars

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.create_folder_dialog_title),
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
                    label = { Text(stringResource(R.string.folder_dialog_name_label)) },
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
                onClick = { onConfirmCreate(trimmed) },
                enabled = isValid,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.compress_button_create))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        modifier = modifier
    )
}
