package com.Hrshd1eux.filemanager.core.storage.volume

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Discovers and inspects all physical and mounted storage volumes:
 * - Primary internal storage
 * - Removable MicroSD cards
 * - Connected USB OTG drives
 */
object StorageVolumeResolver {

    suspend fun resolveVolumes(
        context: Context,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): List<PhysicalVolume> = withContext(dispatcher) {
        val volumes = mutableListOf<PhysicalVolume>()
        val seenPaths = mutableSetOf<String>()

        // 1. Primary Internal Storage
        val primaryDir = Environment.getExternalStorageDirectory()
        val primaryPath = primaryDir.absolutePath
        seenPaths.add(primaryPath)

        val primaryStats = getVolumeStats(primaryDir)
        volumes.add(
            PhysicalVolume(
                id = "primary_internal",
                name = "Internal Storage",
                path = primaryPath,
                isPrimary = true,
                isRemovable = false,
                isUsb = false,
                isAvailable = primaryDir.exists() && primaryDir.canRead(),
                totalBytes = primaryStats.first,
                freeBytes = primaryStats.second,
                usedBytes = primaryStats.third
            )
        )

        // 2. Discover Secondary / Removable Volumes via StorageManager
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        if (storageManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching {
                for (volume in storageManager.storageVolumes) {
                    if (volume.isPrimary) continue // Already added as primary

                    val isMounted = volume.state == Environment.MEDIA_MOUNTED ||
                            volume.state == Environment.MEDIA_MOUNTED_READ_ONLY

                    val path = resolveVolumePath(context, volume) ?: continue
                    if (seenPaths.contains(path)) continue
                    seenPaths.add(path)

                    val file = File(path)
                    val isAvailable = isMounted && file.exists() && file.canRead()
                    val stats = if (isAvailable) getVolumeStats(file) else Triple(0L, 0L, 0L)

                    val desc = runCatching { volume.getDescription(context) }.getOrNull() ?: ""
                    val isUsb = desc.contains("usb", ignoreCase = true) ||
                            path.contains("usb", ignoreCase = true) ||
                            volume.uuid?.contains("usb", ignoreCase = true) == true

                    val name = if (desc.isNotEmpty()) {
                        desc
                    } else if (isUsb) {
                        "USB Storage"
                    } else {
                        "SD Card"
                    }

                    volumes.add(
                        PhysicalVolume(
                            id = volume.uuid ?: path,
                            name = name,
                            path = path,
                            isPrimary = false,
                            isRemovable = volume.isRemovable,
                            isUsb = isUsb,
                            isAvailable = isAvailable,
                            totalBytes = stats.first,
                            freeBytes = stats.second,
                            usedBytes = stats.third
                        )
                    )
                }
            }
        }

        // 3. Fallback discovery via ContextCompat.getExternalFilesDirs for devices where StorageManager path is restricted
        runCatching {
            val externalDirs = ContextCompat.getExternalFilesDirs(context, null)
            for (dir in externalDirs) {
                if (dir == null) continue
                // Format: /storage/XXXX-XXXX/Android/data/... -> extract root /storage/XXXX-XXXX
                val fullPath = dir.absolutePath
                if (fullPath.contains("/Android/data/")) {
                    val rootPath = fullPath.substringBefore("/Android/data/")
                    if (!seenPaths.contains(rootPath)) {
                        val rootFile = File(rootPath)
                        if (rootFile.exists() && rootFile.canRead()) {
                            seenPaths.add(rootPath)
                            val stats = getVolumeStats(rootFile)
                            val isUsb = rootPath.contains("usb", ignoreCase = true)
                            volumes.add(
                                PhysicalVolume(
                                    id = rootPath,
                                    name = if (isUsb) "USB Storage" else "SD Card",
                                    path = rootPath,
                                    isPrimary = false,
                                    isRemovable = true,
                                    isUsb = isUsb,
                                    isAvailable = true,
                                    totalBytes = stats.first,
                                    freeBytes = stats.second,
                                    usedBytes = stats.third
                                )
                            )
                        }
                    }
                }
            }
        }

        volumes
    }

    private fun resolveVolumePath(context: Context, volume: android.os.storage.StorageVolume): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val dir = volume.directory
            if (dir != null) {
                return dir.absolutePath
            }
        }

        // Reflection fallback for API 24-29
        val pathMethod = runCatching { volume.javaClass.getMethod("getPath") }.getOrNull()
        if (pathMethod != null) {
            val path = runCatching { pathMethod.invoke(volume) as? String }.getOrNull()
            if (!path.isNullOrEmpty()) {
                return path
            }
        }

        // UUID fallback: standard Android path /storage/{UUID}
        val uuid = volume.uuid
        if (!uuid.isNullOrEmpty()) {
            val standardPath = File("/storage", uuid)
            if (standardPath.exists()) {
                return standardPath.absolutePath
            }
        }

        return null
    }

    private fun getVolumeStats(dir: File): Triple<Long, Long, Long> {
        return runCatching {
            val stat = StatFs(dir.absolutePath)
            val total = stat.totalBytes
            val free = stat.availableBytes
            val used = (total - free).coerceAtLeast(0L)
            Triple(total, free, used)
        }.getOrDefault(Triple(0L, 0L, 0L))
    }
}
