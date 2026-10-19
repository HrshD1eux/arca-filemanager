package com.Hrshd1eux.filemanager.core.storage.util

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Usb
import androidx.compose.ui.graphics.vector.ImageVector
import com.Hrshd1eux.filemanager.core.favorites.repository.FavoritesRepository
import com.Hrshd1eux.filemanager.core.recents.repository.RecentsRepository
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import java.io.File

enum class DestinationCategory(val title: String) {
    RECENT("Recent"),
    FAVORITE("Favorites"),
    STORAGE("Storage")
}

data class SmartDestinationItem(
    val id: String,
    val name: String,
    val path: String,
    val category: DestinationCategory,
    val icon: ImageVector,
    val subtitle: String? = null,
    val isAvailable: Boolean = true
) {
    fun toStorageNode(): StorageNode {
        val file = File(path)
        return StorageNode(
            id = path,
            name = name.removePrefix("★ ").trim(),
            path = path,
            size = 0L,
            lastModified = if (file.exists()) file.lastModified() else System.currentTimeMillis(),
            isDirectory = true
        )
    }
}

object SmartDestinationResolver {

    /**
     * Resolves standard and recent folders (Download, Documents, Pictures, etc.).
     */
    fun getRecentDestinations(
        context: Context? = null,
        recentsRepository: RecentsRepository? = null,
        destinationHistoryRepository: com.Hrshd1eux.filemanager.core.storage.preferences.DestinationHistoryRepository? = null
    ): List<SmartDestinationItem> {
        val items = mutableListOf<SmartDestinationItem>()
        val seenPaths = mutableSetOf<String>()

        // 1. Explicitly recorded copy/move destination history has highest priority
        destinationHistoryRepository?.recentDestinations?.value?.forEach { dest ->
            if (!seenPaths.contains(dest.path)) {
                seenPaths.add(dest.path)
                val file = File(dest.path)
                items.add(
                    SmartDestinationItem(
                        id = dest.path,
                        name = dest.name,
                        path = dest.path,
                        category = DestinationCategory.RECENT,
                        icon = Icons.Default.Folder,
                        subtitle = file.parent ?: dest.path
                    )
                )
            }
        }

        fun addStandard(name: String, dirType: String?, defaultPath: String, icon: ImageVector, subtitle: String) {
            val resolvedPath = if (dirType != null) {
                runCatching {
                    Environment.getExternalStoragePublicDirectory(dirType)?.absolutePath
                }.getOrNull() ?: defaultPath
            } else {
                defaultPath
            }

            if (!seenPaths.contains(resolvedPath)) {
                seenPaths.add(resolvedPath)
                items.add(
                    SmartDestinationItem(
                        id = resolvedPath,
                        name = name,
                        path = resolvedPath,
                        category = DestinationCategory.RECENT,
                        icon = icon,
                        subtitle = subtitle
                    )
                )
            }
        }

        val downloadsType = runCatching { Environment.DIRECTORY_DOWNLOADS }.getOrNull() ?: "Download"
        val documentsType = runCatching { Environment.DIRECTORY_DOCUMENTS }.getOrNull() ?: "Documents"
        val picturesType = runCatching { Environment.DIRECTORY_PICTURES }.getOrNull() ?: "Pictures"
        val moviesType = runCatching { Environment.DIRECTORY_MOVIES }.getOrNull() ?: "Movies"

        // Standard user destinations
        addStandard("Download", downloadsType, "/storage/emulated/0/Download", Icons.Default.Download, "Recent downloads")
        addStandard("Documents", documentsType, "/storage/emulated/0/Documents", Icons.Default.Description, "Documents")
        addStandard("Pictures", picturesType, "/storage/emulated/0/Pictures", Icons.Default.Image, "Photos & Images")
        addStandard("Movies", moviesType, "/storage/emulated/0/Movies", Icons.Default.Movie, "Videos & Movies")

        // Merge recently accessed directory locations from history
        recentsRepository?.recents?.value?.forEach { recent ->
            if (recent.isDirectory && !seenPaths.contains(recent.path)) {
                seenPaths.add(recent.path)
                val file = File(recent.path)
                items.add(
                    SmartDestinationItem(
                        id = recent.path,
                        name = recent.displayName,
                        path = recent.path,
                        category = DestinationCategory.RECENT,
                        icon = Icons.Default.Folder,
                        subtitle = file.parent ?: recent.path
                    )
                )
            }
        }

        return items
    }

    /**
     * Resolves user-starred favorite folders (e.g. ★ Projects, ★ Backup).
     */
    fun getFavoriteDestinations(
        favoritesRepository: FavoritesRepository? = null
    ): List<SmartDestinationItem> {
        val list = favoritesRepository?.favorites?.value ?: emptyList()
        return list
            .filter { it.isDirectory }
            .map { fav ->
                SmartDestinationItem(
                    id = fav.path,
                    name = if (fav.displayName.startsWith("★")) fav.displayName else "★ ${fav.displayName}",
                    path = fav.path,
                    category = DestinationCategory.FAVORITE,
                    icon = Icons.Default.Star,
                    subtitle = fav.path
                )
            }
    }

    /**
     * Resolves storage device roots (Internal, SD Card, USB).
     */
    fun getStorageDestinations(
        context: Context? = null
    ): List<SmartDestinationItem> {
        val items = mutableListOf<SmartDestinationItem>()

        // 1. Primary Internal Storage
        val internalPath = runCatching {
            Environment.getExternalStorageDirectory()?.absolutePath
        }.getOrNull() ?: "/storage/emulated/0"

        items.add(
            SmartDestinationItem(
                id = internalPath,
                name = "Internal",
                path = internalPath,
                category = DestinationCategory.STORAGE,
                icon = Icons.Default.PhoneAndroid,
                subtitle = "Primary internal storage",
                isAvailable = true
            )
        )

        // Detect secondary volumes (SD card and USB)
        var sdCardItem: SmartDestinationItem? = null
        var usbItem: SmartDestinationItem? = null

        if (context != null) {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            if (sm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching {
                    for (volume in sm.storageVolumes) {
                        if (volume.isRemovable) {
                            val dirPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                volume.directory?.absolutePath
                            } else null

                            val desc = volume.getDescription(context) ?: ""
                            val isMounted = volume.state == Environment.MEDIA_MOUNTED
                            val path = dirPath ?: "/storage/${volume.uuid ?: "ext_sd"}"

                            if (desc.contains("usb", ignoreCase = true) || path.contains("usb", ignoreCase = true)) {
                                if (usbItem == null) {
                                    usbItem = SmartDestinationItem(
                                        id = path,
                                        name = "USB",
                                        path = path,
                                        category = DestinationCategory.STORAGE,
                                        icon = Icons.Default.Usb,
                                        subtitle = if (isMounted) desc.ifEmpty { "USB OTG drive" } else "Not connected",
                                        isAvailable = isMounted
                                    )
                                }
                            } else {
                                if (sdCardItem == null) {
                                    sdCardItem = SmartDestinationItem(
                                        id = path,
                                        name = "SD Card",
                                        path = path,
                                        category = DestinationCategory.STORAGE,
                                        icon = Icons.Default.SdCard,
                                        subtitle = if (isMounted) desc.ifEmpty { "MicroSD storage" } else "Not mounted",
                                        isAvailable = isMounted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Direct filesystem detection fallback for /storage mounts
        runCatching {
            val storageDir = File("/storage")
            if (storageDir.exists() && storageDir.isDirectory) {
                storageDir.listFiles()?.forEach { file ->
                    val name = file.name.lowercase()
                    if (name != "emulated" && name != "self" && file.canRead()) {
                        if (name.contains("usb") || name.contains("otg")) {
                            if (usbItem == null) {
                                usbItem = SmartDestinationItem(
                                    id = file.absolutePath,
                                    name = "USB",
                                    path = file.absolutePath,
                                    category = DestinationCategory.STORAGE,
                                    icon = Icons.Default.Usb,
                                    subtitle = "Removable USB • ${file.name}",
                                    isAvailable = true
                                )
                            }
                        } else {
                            if (sdCardItem == null) {
                                sdCardItem = SmartDestinationItem(
                                    id = file.absolutePath,
                                    name = "SD Card",
                                    path = file.absolutePath,
                                    category = DestinationCategory.STORAGE,
                                    icon = Icons.Default.SdCard,
                                    subtitle = "SD Card • ${file.name}",
                                    isAvailable = true
                                )
                            }
                        }
                    }
                }
            }
        }

        // Add SD Card (either detected or placeholder indicating device support)
        items.add(
            sdCardItem ?: SmartDestinationItem(
                id = "/storage/sdcard",
                name = "SD Card",
                path = "/storage/sdcard",
                category = DestinationCategory.STORAGE,
                icon = Icons.Default.SdCard,
                subtitle = "Not mounted",
                isAvailable = false
            )
        )

        // Add USB (either detected or placeholder)
        items.add(
            usbItem ?: SmartDestinationItem(
                id = "/storage/usb",
                name = "USB",
                path = "/storage/usb",
                category = DestinationCategory.STORAGE,
                icon = Icons.Default.Usb,
                subtitle = "Not connected",
                isAvailable = false
            )
        )

        return items
    }
}
