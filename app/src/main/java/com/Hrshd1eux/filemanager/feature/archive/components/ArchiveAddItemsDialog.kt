package com.Hrshd1eux.filemanager.feature.archive.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.core.designsystem.FolderYellow
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.provider.StorageProvider
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import com.Hrshd1eux.filemanager.feature.browser.FileBrowserViewModel
import com.Hrshd1eux.filemanager.feature.browser.model.BreadcrumbItem
import java.io.File

@Composable
fun ArchiveAddItemsDialog(
    storageProvider: StorageProvider,
    onItemsSelected: (List<File>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rootNode = remember { storageProvider.getRootNode() }
    var currentDir by remember { mutableStateOf(rootNode) }
    var childrenList by remember { mutableStateOf<List<StorageNode>>(emptyList()) }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }
    var breadcrumbs by remember { mutableStateOf<List<BreadcrumbItem>>(emptyList()) }

    LaunchedEffect(currentDir.id) {
        isLoading = true
        val result = storageProvider.listChildren(currentDir.id)
        result.onSuccess { children ->
            childrenList = children
                .filter { !it.name.startsWith(".") }
                .sortedWith(compareBy<StorageNode> { !it.isDirectory }.thenBy { it.name.lowercase() })

            breadcrumbs = FileBrowserViewModel.buildBreadcrumbs(
                currentPath = currentDir.path,
                rootPath = rootNode.path,
                rootDisplayName = storageProvider.displayName
            )
        }.onFailure {
            childrenList = emptyList()
        }
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Items to Add",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 320.dp, max = 460.dp)
            ) {
                // Breadcrumbs
                val scrollState = rememberScrollState()
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(scrollState)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        breadcrumbs.forEachIndexed { index, item ->
                            if (index > 0) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (item.isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (item.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable(enabled = !item.isCurrent) {
                                        currentDir = StorageNode(
                                            id = item.path,
                                            name = item.name,
                                            path = item.path,
                                            isDirectory = true
                                        )
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Item List
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                } else if (childrenList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "This folder is empty.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(childrenList, key = { it.id }) { node ->
                            val isSelected = selectedPaths.contains(node.path)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (node.isDirectory) {
                                            currentDir = node
                                        } else {
                                            selectedPaths = if (isSelected) {
                                                selectedPaths - node.path
                                            } else {
                                                selectedPaths + node.path
                                            }
                                        }
                                    },
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = {
                                            selectedPaths = if (it) {
                                                selectedPaths + node.path
                                            } else {
                                                selectedPaths - node.path
                                            }
                                        }
                                    )

                                    Spacer(modifier = Modifier.width(4.dp))

                                    Icon(
                                        imageVector = if (node.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                                        contentDescription = null,
                                        tint = if (node.isDirectory) FolderYellow else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = node.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (node.isDirectory) "Folder" else FileFormatter.formatFileSize(node.size),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }

                                    if (node.isDirectory) {
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = "Open",
                                            tint = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val files = selectedPaths.map { File(it) }
                    onItemsSelected(files)
                },
                enabled = selectedPaths.isNotEmpty(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Add ${if (selectedPaths.isNotEmpty()) "(${selectedPaths.size})" else ""}")
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
