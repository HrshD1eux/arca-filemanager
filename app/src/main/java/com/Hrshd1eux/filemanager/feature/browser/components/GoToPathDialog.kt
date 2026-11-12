package com.Hrshd1eux.filemanager.feature.browser.components

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.core.designsystem.FolderYellow
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import com.Hrshd1eux.filemanager.core.storage.util.PathJumpResolver
import com.Hrshd1eux.filemanager.core.storage.util.PathJumpTarget
import com.Hrshd1eux.filemanager.core.storage.util.PathResolutionResult

@Composable
fun GoToPathDialog(
    currentPath: String,
    onNavigateToDirectory: (path: String) -> Unit,
    onOpenFile: (PathJumpTarget.DocumentFile) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = "",
                selection = TextRange(0)
            )
        )
    }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    val rawInput = textFieldValue.text
    val resolution = remember(rawInput) {
        if (rawInput.isBlank()) null else PathJumpResolver.resolve(rawInput)
    }
    val validTarget = (resolution as? PathResolutionResult.Valid)?.target
    val errorMessage = (resolution as? PathResolutionResult.Invalid)?.reason

    val pasteFromClipboard: () -> Unit = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipText = clipboard?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            ?.trim()

        if (clipText.isNullOrEmpty()) {
            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
        } else {
            textFieldValue = TextFieldValue(
                text = clipText,
                selection = TextRange(clipText.length)
            )
        }
    }

    val submitDirectoryNavigation: () -> Unit = {
        when (validTarget) {
            is PathJumpTarget.Directory -> onNavigateToDirectory(validTarget.path)
            is PathJumpTarget.DocumentFile -> onNavigateToDirectory(validTarget.parentPath)
            null -> Unit
        }
    }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Go to Path",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    label = { Text("Folder or File Path") },
                    placeholder = {
                        Text(
                            text = currentPath,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = {
                        if (errorMessage != null) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (rawInput.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        textFieldValue = TextFieldValue(text = "", selection = TextRange(0))
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear path"
                                    )
                                }
                            }
                            IconButton(onClick = pasteFromClipboard) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste path",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            if (validTarget != null) {
                                submitDirectoryNavigation()
                            }
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    shape = RoundedCornerShape(8.dp)
                )

                // Detected Target Preview
                if (validTarget != null) {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = when (validTarget) {
                                        is PathJumpTarget.Directory -> Icons.Default.Folder
                                        is PathJumpTarget.DocumentFile -> Icons.AutoMirrored.Filled.InsertDriveFile
                                    },
                                    contentDescription = null,
                                    tint = when (validTarget) {
                                        is PathJumpTarget.Directory -> FolderYellow
                                        is PathJumpTarget.DocumentFile -> MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = when (validTarget) {
                                            is PathJumpTarget.Directory -> validTarget.displayName
                                            is PathJumpTarget.DocumentFile -> validTarget.fileName
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = when (validTarget) {
                                            is PathJumpTarget.Directory -> "Folder • ${validTarget.path}"
                                            is PathJumpTarget.DocumentFile ->
                                                "File (${FileFormatter.formatFileSize(validTarget.sizeBytes)}) • In ${validTarget.parentPath}"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (validTarget is PathJumpTarget.DocumentFile) {
                                OutlinedButton(
                                    onClick = { onOpenFile(validTarget) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Open File Directly")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = submitDirectoryNavigation,
                enabled = validTarget != null,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    when (validTarget) {
                        is PathJumpTarget.DocumentFile -> "Go to Folder"
                        else -> "Go"
                    }
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = pasteFromClipboard,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Paste")
                }
                TextButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancel")
                }
            }
        }
    )
}
