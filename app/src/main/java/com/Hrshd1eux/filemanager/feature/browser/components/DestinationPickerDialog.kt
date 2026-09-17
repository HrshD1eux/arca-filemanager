package com.Hrshd1eux.filemanager.feature.browser.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Hrshd1eux.filemanager.core.designsystem.FolderYellow
import com.Hrshd1eux.filemanager.core.designsystem.VaultEmerald
import com.Hrshd1eux.filemanager.core.favorites.repository.FavoritesRepository
import com.Hrshd1eux.filemanager.core.recents.repository.RecentsRepository
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.provider.StorageProvider
import com.Hrshd1eux.filemanager.core.storage.util.DestinationCategory
import com.Hrshd1eux.filemanager.core.storage.util.SmartDestinationItem
import com.Hrshd1eux.filemanager.core.storage.util.SmartDestinationResolver
import com.Hrshd1eux.filemanager.feature.browser.FileBrowserViewModel
import com.Hrshd1eux.filemanager.feature.browser.model.BreadcrumbItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Smart Destination Picker (1.2.4).
 *
 * Eliminates tedious manual tree traversal by presenting intelligent shortcuts:
 * - Recent (Download, Documents, Pictures, etc.)
 * - Favorites (★ Projects, ★ Backup, etc.)
 * - Storage (Internal, SD Card, USB)
 *
 * Allows instant 1-tap jumping, directory browsing, and on-the-fly folder creation.
 */
@Composable
fun DestinationPickerDialog(
    storageProvider: StorageProvider,
    title: String = "Select Destination",
    confirmButtonText: String = "Copy Here",
    excludedPaths: Set<String> = emptySet(),
    favoritesRepository: FavoritesRepository? = null,
    recentsRepository: RecentsRepository? = null,
    onDestinationSelected: (destination: StorageNode, verifyIntegrity: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val rootNode = remember { storageProvider.getRootNode() }

    // null means landing page (Smart Shortcuts: Recent, Favorites, Storage)
    var currentDir by remember { mutableStateOf<StorageNode?>(null) }
    var folders by remember { mutableStateOf<List<StorageNode>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var verifyIntegrity by remember { mutableStateOf(false) }
    var breadcrumbs by remember { mutableStateOf<List<BreadcrumbItem>>(emptyList()) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }

    // Resolve Smart Destinations
    val recentDestinations = remember(recentsRepository) {
        SmartDestinationResolver.getRecentDestinations(context, recentsRepository)
            .filter { !excludedPaths.contains(it.path) }
    }
    val favoriteDestinations = remember(favoritesRepository) {
        SmartDestinationResolver.getFavoriteDestinations(favoritesRepository)
            .filter { !excludedPaths.contains(it.path) }
    }
    val storageDestinations = remember(context) {
        SmartDestinationResolver.getStorageDestinations(context)
            .filter { !excludedPaths.contains(it.path) }
    }

    // Load subfolders when currentDir changes
    LaunchedEffect(currentDir?.id) {
        val target = currentDir
        if (target != null) {
            isLoading = true
            val result = storageProvider.listChildren(target.id)
            result.onSuccess { children ->
                folders = children
                    .filter { it.isDirectory && !excludedPaths.contains(it.path) }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

                breadcrumbs = FileBrowserViewModel.buildBreadcrumbs(
                    currentPath = target.path,
                    rootPath = rootNode.path,
                    rootDisplayName = storageProvider.displayName
                )
            }.onFailure {
                folders = emptyList()
            }
            isLoading = false
        } else {
            folders = emptyList()
            breadcrumbs = emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (currentDir != null) {
                    Text(
                        text = currentDir!!.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 340.dp, max = 460.dp)
            ) {
                // Breadcrumb Bar
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
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Root Smart Destinations shortcut
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { currentDir = null }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Quick Destinations",
                                tint = if (currentDir == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Destinations",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (currentDir == null) FontWeight.Bold else FontWeight.Normal,
                                color = if (currentDir == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Child directory breadcrumbs
                        breadcrumbs.forEach { item ->
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(14.dp)
                            )

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

                // Content View
                if (currentDir == null) {
                    // Smart Shortcuts Landing View
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Recent Section
                        item {
                            DestinationSectionHeader(title = "Recent", count = recentDestinations.size)
                        }
                        items(recentDestinations, key = { "recent_${it.path}" }) { destination ->
                            SmartDestinationCard(
                                item = destination,
                                onNavigateInto = {
                                    currentDir = destination.toStorageNode()
                                },
                                onPickImmediately = {
                                    onDestinationSelected(destination.toStorageNode(), verifyIntegrity)
                                }
                            )
                        }

                        // 2. Favorites Section
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            DestinationSectionHeader(title = "Favorites", count = favoriteDestinations.size)
                        }
                        if (favoriteDestinations.isEmpty()) {
                            item {
                                Text(
                                    text = "No favorites starred yet. Tap ★ on any folder to pin here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            items(favoriteDestinations, key = { "fav_${it.path}" }) { destination ->
                                SmartDestinationCard(
                                    item = destination,
                                    onNavigateInto = {
                                        currentDir = destination.toStorageNode()
                                    },
                                    onPickImmediately = {
                                        onDestinationSelected(destination.toStorageNode(), verifyIntegrity)
                                    }
                                )
                            }
                        }

                        // 3. Storage Devices Section
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            DestinationSectionHeader(title = "Storage", count = storageDestinations.size)
                        }
                        items(storageDestinations, key = { "storage_${it.id}" }) { destination ->
                            SmartDestinationCard(
                                item = destination,
                                onNavigateInto = {
                                    if (destination.isAvailable) {
                                        currentDir = destination.toStorageNode()
                                    }
                                },
                                onPickImmediately = {
                                    if (destination.isAvailable) {
                                        onDestinationSelected(destination.toStorageNode(), verifyIntegrity)
                                    }
                                }
                            )
                        }
                    }
                } else {
                    // Subfolder Explorer View
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // Folder toolbar (New Folder button)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Subfolders",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            TextButton(
                                onClick = { showCreateFolderDialog = true },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New Folder", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        if (isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            }
                        } else if (folders.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No subfolders here.\nTap '$confirmButtonText' to select this folder.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(folders, key = { it.id }) { folder ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { currentDir = folder },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint = FolderYellow,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = folder.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Icon(
                                                imageVector = Icons.Default.ChevronRight,
                                                contentDescription = "Open",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Verification Switch (for copy / extract operations)
                if (confirmButtonText.contains("Copy", ignoreCase = true) || confirmButtonText.contains("Extract", ignoreCase = true)) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { verifyIntegrity = !verifyIntegrity }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Verify files after operation",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Computes bit-for-bit SHA-256 checksums",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = verifyIntegrity,
                            onCheckedChange = { verifyIntegrity = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            val isEnabled = currentDir != null && !excludedPaths.contains(currentDir!!.path)
            Button(
                onClick = {
                    currentDir?.let { onDestinationSelected(it, verifyIntegrity) }
                },
                enabled = isEnabled,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    if (currentDir != null) "$confirmButtonText" else "Pick a Folder"
                )
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

    // Inline Create Folder Dialog
    if (showCreateFolderDialog && currentDir != null) {
        val parentPath = currentDir!!.path
        CreateFolderDialog(
            onConfirmCreate = { newFolderName ->
                showCreateFolderDialog = false
                coroutineScope.launch {
                    val newDir = File(parentPath, newFolderName)
                    val created = withContext(Dispatchers.IO) {
                        newDir.mkdirs()
                    }
                    if (created || newDir.exists()) {
                        // Refresh folder listing and enter created directory
                        currentDir = StorageNode(
                            id = newDir.absolutePath,
                            name = newFolderName,
                            path = newDir.absolutePath,
                            isDirectory = true
                        )
                    }
                }
            },
            onDismiss = { showCreateFolderDialog = false }
        )
    }
}

@Composable
private fun DestinationSectionHeader(title: String, count: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (count > 0) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            thickness = 1.dp
        )
    }
}

@Composable
private fun SmartDestinationCard(
    item: SmartDestinationItem,
    onNavigateInto: () -> Unit,
    onPickImmediately: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = item.isAvailable, onClick = onNavigateInto),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isAvailable) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when (item.category) {
                                DestinationCategory.RECENT -> FolderYellow.copy(alpha = 0.15f)
                                DestinationCategory.FAVORITE -> Color(0xFFFFB300).copy(alpha = 0.15f)
                                DestinationCategory.STORAGE -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = when {
                            !item.isAvailable -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            item.category == DestinationCategory.FAVORITE -> Color(0xFFFFB300)
                            item.category == DestinationCategory.RECENT -> FolderYellow
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.isAvailable) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.subtitle != null) {
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (item.isAvailable) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onPickImmediately,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Select", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Browse Subfolders",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
