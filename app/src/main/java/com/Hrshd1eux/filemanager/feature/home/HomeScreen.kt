package com.Hrshd1eux.filemanager.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.R
import com.Hrshd1eux.filemanager.core.designsystem.FolderYellow
import com.Hrshd1eux.filemanager.core.designsystem.VaultEmerald
import android.os.Environment
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Hrshd1eux.filemanager.core.favorites.model.FavoriteItem

import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.Hrshd1eux.filemanager.core.recents.model.RecentItem
import com.Hrshd1eux.filemanager.core.search.model.CategoryFilter
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import com.Hrshd1eux.filemanager.feature.home.components.StorageBreakdownCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToBrowser: (initialPath: String?, title: String) -> Unit,
    onNavigateToSearch: (rootPath: String?) -> Unit,
    onNavigateToCategorySearch: (CategoryFilter) -> Unit = {},
    onNavigateToLargeFiles: (rootPath: String?) -> Unit = {},
    onNavigateToDuplicateFinder: (rootPath: String?) -> Unit = {},
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel()
) {
    var showMenu by remember { mutableStateOf(false) }
    var showClearRecentsDialog by remember { mutableStateOf(false) }
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val storageBreakdown by viewModel.storageBreakdown.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.home_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { onNavigateToSearch(null) }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options"
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.settings_title)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Settings, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    onNavigateToSettings()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.about_title)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Info, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    onNavigateToAbout()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                StorageBreakdownCard(
                    breakdown = storageBreakdown,
                    title = "Internal Storage",
                    onCardClick = { onNavigateToBrowser(null, "Internal Storage") },
                    onCategoryClick = onNavigateToCategorySearch
                )
            }

            // Recent Section
            if (recents.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        TextButton(onClick = { showClearRecentsDialog = true }) {
                            Text(
                                text = "Clear",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                items(recents.size, key = { recents[it].path }) { index ->
                    val recent = recents[index]
                    RecentItemRow(
                        item = recent,
                        onClick = { onNavigateToBrowser(recent.path, recent.displayName) },
                        onRemove = { viewModel.removeRecent(recent.path) }
                    )
                }
            }

            // Favorites / Bookmarks Section
            if (favorites.isNotEmpty()) {
                item {
                    Text(
                        text = "Favorites",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                items(favorites.size, key = { favorites[it].path }) { index ->
                    val fav = favorites[index]
                    FavoriteItemRow(
                        item = fav,
                        onClick = { onNavigateToBrowser(fav.path, fav.displayName) },
                        onRemove = { viewModel.removeFavorite(fav.path) }
                    )
                }
            }

            item {
                Text(
                    text = "Quick Access",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                FolderItem(
                    name = "Download",
                    path = "/storage/emulated/0/Download",
                    icon = Icons.Default.Download,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = {
                        val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
                        onNavigateToBrowser(path, "Download")
                    }
                )
            }

            item {
                FolderItem(
                    name = "Documents",
                    path = "/storage/emulated/0/Documents",
                    icon = Icons.Default.Description,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = {
                        val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath
                        onNavigateToBrowser(path, "Documents")
                    }
                )
            }

            item {
                FolderItem(
                    name = "Pictures",
                    path = "/storage/emulated/0/Pictures",
                    icon = Icons.Default.Image,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = {
                        val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath
                        onNavigateToBrowser(path, "Pictures")
                    }
                )
            }

            item {
                FolderItem(
                    name = "Music",
                    path = "/storage/emulated/0/Music",
                    icon = Icons.Default.MusicNote,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = {
                        val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath
                        onNavigateToBrowser(path, "Music")
                    }
                )
            }

            item {
                Text(
                    text = "Storage Tools",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                FolderItem(
                    name = "Large Files",
                    path = "Review & delete files > 100 MB",
                    icon = Icons.Default.CleaningServices,
                    iconTint = MaterialTheme.colorScheme.error,
                    onClick = { onNavigateToLargeFiles(null) }
                )
            }

            item {
                FolderItem(
                    name = "Duplicate Finder",
                    path = "Scan & remove duplicate copies (SHA-256)",
                    icon = Icons.Default.ContentCopy,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = { onNavigateToDuplicateFinder(null) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                StatusBanner()
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showClearRecentsDialog) {
        AlertDialog(
            onDismissRequest = { showClearRecentsDialog = false },
            title = { Text("Clear Recent History?") },
            text = { Text("All items will be removed from your recent access history. Your files will not be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearRecents()
                        showClearRecentsDialog = false
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearRecentsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun FolderItem(
    name: String,
    path: String,
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun StatusBanner(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = VaultEmerald,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "100% Offline Core • Zero Telemetry • No Network Access",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FavoriteItemRow(
    item: FavoriteItem,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (item.isDirectory) FolderYellow.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                    contentDescription = null,
                    tint = if (item.isDirectory) FolderYellow else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Favorite",
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = item.path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BookmarkRemove,
                    contentDescription = "Remove Bookmark",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun RecentItemRow(
    item: RecentItem,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (item.isDirectory) FolderYellow.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                    contentDescription = null,
                    tint = if (item.isDirectory) FolderYellow else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )

                val meta = if (item.isDirectory) {
                    formatRelativeTime(item.accessedAt)
                } else {
                    "${FileFormatter.formatFileSize(item.size)} • ${formatRelativeTime(item.accessedAt)}"
                }

                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Remove Recent Item",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days == 1L -> "Yesterday"
        days < 7 -> "${days}d ago"
        else -> FileFormatter.formatDateTime(timestamp)
    }
}
