package com.Hrshd1eux.filemanager.feature.properties

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.core.designsystem.FolderYellow
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import com.Hrshd1eux.filemanager.core.storage.util.FolderDetailsCalculator
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePropertiesSheet(
    node: StorageNode,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var folderDetails by remember { mutableStateOf<FolderDetailsCalculator.FolderDetails?>(null) }
    var isCalculatingFolder by remember { mutableStateOf(false) }

    LaunchedEffect(node.id) {
        if (node.isDirectory) {
            isCalculatingFolder = true
            folderDetails = FolderDetailsCalculator.calculateDetails(File(node.path))
            isCalculatingFolder = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            PropertiesHeader(node = node)

            // Section 1: Size & Storage
            PropertiesCard {
                if (node.isDirectory) {
                    val sizeText = if (isCalculatingFolder) {
                        "Calculating size..."
                    } else {
                        val details = folderDetails ?: FolderDetailsCalculator.FolderDetails()
                        "${FileFormatter.formatFileSize(details.totalBytes)} (${FileFormatter.formatExactBytes(details.totalBytes)})"
                    }

                    val itemsCountText = if (isCalculatingFolder) {
                        "Counting contents..."
                    } else {
                        val details = folderDetails ?: FolderDetailsCalculator.FolderDetails()
                        "${details.totalFiles} files, ${details.totalFolders} subfolders"
                    }

                    PropertyRow(
                        icon = Icons.Default.Storage,
                        label = "Total Size",
                        value = sizeText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PropertyRow(
                        icon = Icons.Default.Folder,
                        label = "Contains",
                        value = itemsCountText
                    )
                } else {
                    PropertyRow(
                        icon = Icons.Default.Storage,
                        label = "File Size",
                        value = "${FileFormatter.formatFileSize(node.size)} (${FileFormatter.formatExactBytes(node.size)})"
                    )
                }
            }

            // Section 2: Location
            PropertiesCard {
                Column {
                    PropertyRow(
                        icon = Icons.Default.Info,
                        label = "Location",
                        value = node.path
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("File Path", node.path)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Path copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy Full Path")
                    }
                }
            }

            // Section 3: Time & Type
            PropertiesCard {
                PropertyRow(
                    icon = Icons.Default.Schedule,
                    label = "Last Modified",
                    value = FileFormatter.formatDateTime(node.lastModified)
                )
                Spacer(modifier = Modifier.height(8.dp))
                PropertyRow(
                    icon = Icons.Default.Description,
                    label = "Type",
                    value = FileFormatter.getCategoryDescription(node.mimeType, node.isDirectory)
                )
                if (node.mimeType != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    PropertyRow(
                        icon = Icons.Default.Info,
                        label = "MIME Type",
                        value = node.mimeType
                    )
                }
            }

            // Section 4: Access & Flags
            PropertiesCard {
                PropertyRow(
                    icon = Icons.Default.Security,
                    label = "Permissions",
                    value = if (node.isWritable) "Read / Write" else "Read Only"
                )
                Spacer(modifier = Modifier.height(8.dp))
                PropertyRow(
                    icon = Icons.Default.Visibility,
                    label = "Hidden Attribute",
                    value = if (node.isHidden) "Yes (Hidden file)" else "No"
                )
            }
        }
    }
}

@Composable
private fun PropertiesHeader(
    node: StorageNode,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (node.isDirectory) FolderYellow.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (node.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                contentDescription = null,
                tint = if (node.isDirectory) FolderYellow else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (node.isDirectory) "Directory" else node.extension.uppercase().ifEmpty { "File" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PropertiesCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun PropertyRow(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
