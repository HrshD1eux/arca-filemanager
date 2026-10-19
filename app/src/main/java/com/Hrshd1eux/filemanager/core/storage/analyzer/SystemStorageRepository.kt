package com.Hrshd1eux.filemanager.core.storage.analyzer

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.Hrshd1eux.filemanager.ArcaApplication
import com.Hrshd1eux.filemanager.core.storage.model.InaccessibleStorageZone
import com.Hrshd1eux.filemanager.core.storage.model.StorageAccuracy
import com.Hrshd1eux.filemanager.core.storage.model.SystemStorageBreakdown
import com.Hrshd1eux.filemanager.core.storage.model.SystemStoragePartition
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Repository responsible for inspecting operating-system storage partitions,
 * distinguishing Android OS storage from system app packages,
 * measuring accessible temporary and cache storage, and explicitly identifying
 * inaccessible system-private storage zones.
 *
 * Adheres strictly to the architectural constraints:
 * 1. Read-only inspection ONLY.
 * 2. Never offer a "delete all system temp files" button.
 * 3. Never imply ARCA can browse arbitrary system-private directories.
 * 4. Distinguish system app package footprint from Android OS storage.
 */
class SystemStorageRepository(
    context: Context? = null,
    private val installedAppsRepository: InstalledAppsRepository? = null,
    private val localStorageAnalyzer: LocalStorageAnalyzer = LocalStorageAnalyzer(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mockBreakdown: SystemStorageBreakdown? = null
) {
    private val appContext: Context? = context?.applicationContext
        ?: runCatching { ArcaApplication.instance?.applicationContext }.getOrNull()

    private val appsRepo = installedAppsRepository
        ?: InstalledAppsRepository.getInstance(appContext)

    @Volatile
    private var cachedBreakdown: SystemStorageBreakdown? = null

    suspend fun getSystemStorageBreakdown(forceRefresh: Boolean = false): SystemStorageBreakdown = withContext(ioDispatcher) {
        if (!forceRefresh && cachedBreakdown != null) {
            return@withContext cachedBreakdown!!
        }

        if (mockBreakdown != null) {
            cachedBreakdown = mockBreakdown
            return@withContext mockBreakdown
        }

        // 1. Inspect System OS Partitions (/system, /vendor, /product, /system_ext)
        val osPartitions = ArrayList<SystemStoragePartition>()
        var osTotalBytes = 0L

        // /system partition
        runCatching {
            val systemDir = Environment.getRootDirectory()
            if (systemDir.exists() && systemDir.canRead()) {
                val stat = StatFs(systemDir.absolutePath)
                val part = SystemStoragePartition(
                    name = "Android OS System",
                    mountPoint = systemDir.absolutePath,
                    totalBytes = stat.totalBytes,
                    freeBytes = stat.availableBytes,
                    isReadOnly = true,
                    description = "Core Android framework, system binaries, and runtime"
                )
                osPartitions.add(part)
                osTotalBytes += part.usedBytes
            }
        }

        // Additional read-only platform partitions if present on device
        val secondaryPartitionPaths = listOf(
            "/vendor" to "Hardware abstraction layers and device manufacturer drivers",
            "/product" to "OEM product-specific extensions and bundled system configuration",
            "/system_ext" to "Android OS platform extensions"
        )

        for ((path, desc) in secondaryPartitionPaths) {
            runCatching {
                val dir = File(path)
                if (dir.exists() && dir.canRead()) {
                    val stat = StatFs(dir.absolutePath)
                    // If totalBytes equals system partition's totalBytes, it is mounted on the same root filesystem
                    val alreadyAdded = osPartitions.any { it.totalBytes == stat.totalBytes && it.freeBytes == stat.availableBytes }
                    if (!alreadyAdded && stat.totalBytes > 0L) {
                        val part = SystemStoragePartition(
                            name = dir.name.replaceFirstChar { it.uppercase() },
                            mountPoint = dir.absolutePath,
                            totalBytes = stat.totalBytes,
                            freeBytes = stat.availableBytes,
                            isReadOnly = true,
                            description = desc
                        )
                        osPartitions.add(part)
                        osTotalBytes += part.usedBytes
                    }
                }
            }
        }

        // 2. Data partition metrics (/data)
        var dataTotalBytes = 0L
        var dataFreeBytes = 0L
        runCatching {
            val dataDir = Environment.getDataDirectory()
            if (dataDir.exists()) {
                val stat = StatFs(dataDir.absolutePath)
                dataTotalBytes = stat.totalBytes
                dataFreeBytes = stat.availableBytes
            }
        }

        // 3. Query Installed Apps Report to separate System Apps from User Apps
        val appsReport = runCatching {
            appsRepo.getInstalledApps(forceRefresh = forceRefresh)
        }.getOrDefault(com.Hrshd1eux.filemanager.core.storage.analyzer.InstalledAppsReport())

        var systemAppsBytes = 0L
        var systemAppsCount = 0
        var userAppsBytes = 0L
        var userAppsCount = 0
        var totalReportedCache = 0L
        var cacheReportedCount = 0

        for (app in appsReport.apps) {
            if (app.isUserApp) {
                userAppsCount++
                userAppsBytes += app.storageMeasurement.totalBytes ?: 0L
            } else {
                systemAppsCount++
                // System app package footprint is the code size of pre-installed APKs
                systemAppsBytes += app.storageMeasurement.codeBytes ?: (app.storageMeasurement.totalBytes ?: 0L)
            }

            // Accessible cache from app stats
            val cacheBytes = app.storageMeasurement.cacheBytes
            if (cacheBytes != null && cacheBytes > 0L) {
                totalReportedCache += cacheBytes
                cacheReportedCount++
            }
        }

        // 4. Query Shared Media Breakdown
        val storageBreakdown = runCatching {
            localStorageAnalyzer.analyzeStorage()
        }.getOrDefault(com.Hrshd1eux.filemanager.core.storage.model.StorageBreakdown())

        val mediaBytes = storageBreakdown.categories.sumOf { it.byteCount }

        // 5. Accessible cache determination
        val (accessibleCache, cacheAccuracy, cacheSource) = when {
            cacheReportedCount > 0 -> {
                Triple(totalReportedCache, StorageAccuracy.EXACT, "Aggregated from system storage stats across $cacheReportedCount apps")
            }
            appContext != null -> {
                // Inspect ARCA's own accessible cache
                val selfCache = calculateDirSize(appContext.cacheDir) + calculateDirSize(appContext.codeCacheDir)
                if (selfCache > 0L) {
                    Triple(selfCache, StorageAccuracy.APPROXIMATE, "ARCA internal cache (other app caches sandboxed by OS)")
                } else {
                    Triple(null, StorageAccuracy.UNAVAILABLE, "Restricted by Android platform sandbox without Usage Access")
                }
            }
            else -> {
                Triple(null, StorageAccuracy.UNAVAILABLE, "Restricted by Android platform sandbox")
            }
        }

        val breakdown = SystemStorageBreakdown(
            androidOsBytes = osTotalBytes,
            androidOsPartitions = osPartitions,
            systemAppsBytes = systemAppsBytes,
            systemAppsCount = systemAppsCount,
            userAppsBytes = userAppsBytes,
            userAppsCount = userAppsCount,
            sharedMediaBytes = mediaBytes,
            freeStorageBytes = dataFreeBytes,
            dataPartitionTotalBytes = dataTotalBytes,
            accessibleCacheBytes = accessibleCache,
            accessibleCacheAccuracy = cacheAccuracy,
            accessibleCacheSource = cacheSource,
            inaccessibleZones = SystemStorageBreakdown.defaultInaccessibleZones(),
            isAnalyzing = false,
            measuredAtMillis = System.currentTimeMillis()
        )

        cachedBreakdown = breakdown
        breakdown
    }

    private fun calculateDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var total = 0L
        dir.walkTopDown().maxDepth(10).forEach { file ->
            if (file.isFile) total += file.length()
        }
        return total
    }

    companion object {
        @Volatile
        private var instance: SystemStorageRepository? = null

        fun getInstance(context: Context? = null): SystemStorageRepository {
            return instance ?: synchronized(this) {
                instance ?: SystemStorageRepository(context).also { instance = it }
            }
        }
    }
}
