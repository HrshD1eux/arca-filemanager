package com.Hrshd1eux.filemanager.core.storage.analyzer

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.storage.StorageManager
import androidx.collection.LruCache
import com.Hrshd1eux.filemanager.ArcaApplication
import com.Hrshd1eux.filemanager.core.storage.model.AppStorageMeasurement
import com.Hrshd1eux.filemanager.core.storage.model.AppTypeFilter
import com.Hrshd1eux.filemanager.core.storage.model.InstalledAppInfo
import com.Hrshd1eux.filemanager.core.storage.model.InstalledAppSortOrder
import com.Hrshd1eux.filemanager.core.storage.model.PackageExtraInfo
import com.Hrshd1eux.filemanager.core.storage.model.SplitApkInfo
import com.Hrshd1eux.filemanager.core.storage.model.StorageAccuracy
import com.Hrshd1eux.filemanager.core.storage.model.StorageComponentMeasurement
import com.Hrshd1eux.filemanager.core.storage.model.StorageMeasurementSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Summary report of all installed applications analyzed on the device.
 */
data class InstalledAppsReport(
    val apps: List<InstalledAppInfo> = emptyList(),
    val userAppCount: Int = 0,
    val systemAppCount: Int = 0,
    val totalAppCount: Int = 0,
    val totalMeasurableBytes: Long = 0L,
    val inaccessibleCount: Int = 0,
    val uninspectedPackages: List<String> = emptyList(),
    val measuredTimestampMillis: Long = System.currentTimeMillis(),
    val isAnalyzing: Boolean = false,
    val isUsageAccessGranted: Boolean = false
) {
    val freshnessLabel: String
        get() {
            val diff = (System.currentTimeMillis() - measuredTimestampMillis).coerceAtLeast(0L)
            return when {
                diff < 30_000L -> "Measured just now"
                diff < 60_000L -> "Measured less than a min ago"
                diff < 3600_000L -> "Measured ${diff / 60_000L}m ago"
                else -> "Measured recently"
            }
        }
}

/**
 * Repository responsible for inspecting installed user and visible system apps,
 * breaking down storage measurements into code, data, and cache components,
 * tracking measurement accuracy (Exact, Approximate, Stale, Unavailable),
 * extracting rich package metadata, and adhering to Android's platform security boundaries.
 */
class InstalledAppsRepository(
    context: Context? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mockApps: List<InstalledAppInfo>? = null
) {
    private val appContext: Context? = context?.applicationContext
        ?: runCatching { ArcaApplication.instance?.applicationContext }.getOrNull()

    private val iconCache = LruCache<String, Bitmap>(120)

    @Volatile
    private var cachedReport: InstalledAppsReport? = null

    /**
     * Checks if the device user has granted PACKAGE_USAGE_STATS to ARCA.
     */
    fun hasUsageStatsPermission(): Boolean {
        val ctx = appContext ?: return false
        return runCatching {
            val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    ctx.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    ctx.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }

    suspend fun getInstalledApps(forceRefresh: Boolean = false): InstalledAppsReport = withContext(ioDispatcher) {
        if (forceRefresh) {
            cachedReport = null
        }
        if (!forceRefresh && cachedReport != null) {
            val current = cachedReport!!
            // If cached report is older than 30 mins, mark measurements stale
            val isOld = (System.currentTimeMillis() - current.measuredTimestampMillis) > 30 * 60 * 1000L
            return@withContext if (isOld) {
                current.copy(apps = current.apps.map { it.copy(storageMeasurement = it.storageMeasurement.asStale()) })
            } else {
                current
            }
        }

        // Return mock data for unit tests if provided
        if (mockApps != null) {
            val userCount = mockApps.count { it.isUserApp }
            val systemCount = mockApps.size - userCount
            val totalMeasurable = mockApps.sumOf { it.storageMeasurement.totalBytes ?: 0L }
            val inaccessibleCount = mockApps.count { !it.storageMeasurement.isAccessible }
            val report = InstalledAppsReport(
                apps = mockApps,
                userAppCount = userCount,
                systemAppCount = systemCount,
                totalAppCount = mockApps.size,
                totalMeasurableBytes = totalMeasurable,
                inaccessibleCount = inaccessibleCount,
                measuredTimestampMillis = System.currentTimeMillis(),
                isAnalyzing = false,
                isUsageAccessGranted = false
            )
            cachedReport = report
            return@withContext report
        }

        val pm = appContext?.packageManager
        if (pm == null) {
            return@withContext InstalledAppsReport()
        }

        val usageAccessGranted = hasUsageStatsPermission()
        val flags = PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS

        val rawPackages: List<PackageInfo> = runCatching {
            pm.getInstalledPackages(flags)
        }.getOrElse { emptyList() }

        val measuredTime = System.currentTimeMillis()
        val appList = ArrayList<InstalledAppInfo>(rawPackages.size)
        val uninspectedList = ArrayList<String>()
        var userCount = 0
        var systemCount = 0
        var totalBytesSum = 0L
        var inaccessibleCount = 0

        val storageStatsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { appContext.getSystemService(StorageStatsManager::class.java) }.getOrNull()
        } else {
            null
        }

        for (pkg in rawPackages) {
            val appInfo = pkg.applicationInfo ?: continue
            val packageName = pkg.packageName ?: continue
            val isSelf = packageName == appContext.packageName

            val app = runCatching {
                val appName = appInfo.loadLabel(pm).toString()
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

                val vName = pkg.versionName
                val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkg.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pkg.versionCode.toLong()
                }

                // Extract split APKs and package extra info
                val extraInfo = extractPackageExtraInfo(pkg, appInfo)

                // Inspect per-app storage components (Code, Data, Cache, Total)
                val measurement = inspectPackageStorage(
                    appInfo = appInfo,
                    packageName = packageName,
                    isSelf = isSelf,
                    usageAccessGranted = usageAccessGranted,
                    storageStatsManager = storageStatsManager,
                    extraInfo = extraInfo,
                    measuredTime = measuredTime
                )

                if (measurement.totalBytes != null && measurement.isAccessible) {
                    totalBytesSum += measurement.totalBytes ?: 0L
                } else {
                    inaccessibleCount++
                }

                val isUser = !isSystem || isUpdatedSystem
                if (isUser) userCount++ else systemCount++

                val cachedIcon = iconCache.get(packageName)
                val icon = cachedIcon ?: runCatching {
                    val drawable = appInfo.loadIcon(pm)
                    drawableToBitmap(drawable, 96).also {
                        iconCache.put(packageName, it)
                    }
                }.getOrNull()

                InstalledAppInfo(
                    packageName = packageName,
                    appName = appName,
                    versionName = vName,
                    versionCode = vCode,
                    isSystemApp = isSystem,
                    isUpdatedSystemApp = isUpdatedSystem,
                    installTimeMillis = pkg.firstInstallTime,
                    lastUpdateTimeMillis = pkg.lastUpdateTime,
                    sourceDir = appInfo.sourceDir,
                    icon = icon,
                    storageMeasurement = measurement,
                    extraInfo = extraInfo
                )
            }.getOrElse {
                uninspectedList.add(packageName)
                inaccessibleCount++
                null
            }

            if (app != null) {
                appList.add(app)
            }
        }

        val report = InstalledAppsReport(
            apps = appList,
            userAppCount = userCount,
            systemAppCount = systemCount,
            totalAppCount = appList.size,
            totalMeasurableBytes = totalBytesSum,
            inaccessibleCount = inaccessibleCount,
            uninspectedPackages = uninspectedList,
            measuredTimestampMillis = measuredTime,
            isAnalyzing = false,
            isUsageAccessGranted = usageAccessGranted
        )
        cachedReport = report
        report
    }

    /**
     * Inspects package storage adhering strictly to Android platform permissions and boundaries.
     */
    private fun inspectPackageStorage(
        appInfo: ApplicationInfo,
        packageName: String,
        isSelf: Boolean,
        usageAccessGranted: Boolean,
        storageStatsManager: StorageStatsManager?,
        extraInfo: PackageExtraInfo,
        measuredTime: Long
    ): AppStorageMeasurement {
        // Strategy 1: If Usage Access is granted (or inspecting ARCA itself on API 26+), query StorageStatsManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && storageStatsManager != null && (usageAccessGranted || isSelf)) {
            val statsResult = runCatching {
                val uuid = appInfo.storageUuid ?: StorageManager.UUID_DEFAULT
                val userHandle = Process.myUserHandle()
                val stats = storageStatsManager.queryStatsForPackage(uuid, packageName, userHandle)
                val rawApkSize = calculateApkSizeOnDisk(appInfo)

                val extCacheBytes = runCatching {
                    val extCacheDir = File(Environment.getExternalStorageDirectory(), "Android/data/$packageName/cache")
                    if (extCacheDir.exists() && extCacheDir.canRead()) calculateDirSize(extCacheDir) else 0L
                }.getOrDefault(0L)

                val effectiveCacheBytes = maxOf(stats.cacheBytes, extCacheBytes)

                AppStorageMeasurement.fromSystemStats(
                    appBytes = stats.appBytes,
                    dataBytes = stats.dataBytes,
                    cacheBytes = effectiveCacheBytes,
                    rawApkBytes = rawApkSize,
                    splitCount = extraInfo.splitApks.size,
                    timestampMillis = measuredTime,
                    isSelf = isSelf
                )
            }
            if (statsResult.isSuccess) {
                return statsResult.getOrThrow()
            }
        }

        // Strategy 2: If inspecting ARCA itself without usage stats, calculate own storage directly
        if (isSelf && appContext != null) {
            val ctx = appContext
            val selfResult = runCatching {
                val codeBytes = calculateApkSizeOnDisk(appInfo)
                val dataBytes = calculateDirSizeExcluding(ctx.dataDir, setOf(ctx.cacheDir, ctx.codeCacheDir))
                val cacheBytes = calculateDirSize(ctx.cacheDir) + calculateDirSize(ctx.codeCacheDir)
                AppStorageMeasurement.fromSelfInspection(
                    apkBytes = codeBytes,
                    packageBytes = codeBytes,
                    dataBytes = dataBytes,
                    cacheBytes = cacheBytes,
                    timestampMillis = measuredTime
                )
            }
            if (selfResult.isSuccess) {
                return selfResult.getOrThrow()
            }
        }

        // Strategy 3: Inspect APK binaries on disk (Code size only)
        return runCatching {
            val baseFile = appInfo.sourceDir?.let { File(it) }
            if (baseFile != null && baseFile.exists() && baseFile.canRead()) {
                val baseSize = baseFile.length()
                val splitsSize = extraInfo.totalSplitBytes
                val totalCodeSize = baseSize + splitsSize

                if (totalCodeSize > 0L) {
                    AppStorageMeasurement.fromApkFiles(
                        totalSize = totalCodeSize,
                        timestampMillis = measuredTime,
                        splitCount = extraInfo.splitApks.size
                    )
                } else {
                    AppStorageMeasurement.unavailable("Storage measurement not accessible (0 bytes returned)")
                }
            } else {
                AppStorageMeasurement.unavailable("Android platform restricts inspecting package storage on disk")
            }
        }.getOrElse { error ->
            AppStorageMeasurement.unavailable(error.localizedMessage ?: "Permission or platform inspection restriction")
        }
    }

    private fun extractPackageExtraInfo(pkg: PackageInfo, appInfo: ApplicationInfo): PackageExtraInfo {
        val splitList = ArrayList<SplitApkInfo>()
        appInfo.splitSourceDirs?.forEach { splitPath ->
            val splitFile = File(splitPath)
            val exists = runCatching { splitFile.exists() && splitFile.canRead() }.getOrDefault(false)
            val size = if (exists) splitFile.length() else null
            splitList.add(
                SplitApkInfo(
                    name = splitFile.name,
                    path = splitPath,
                    sizeBytes = size,
                    isAccessible = exists && (size != null && size > 0L)
                )
            )
        }

        val primaryCpuAbi = runCatching {
            val field = ApplicationInfo::class.java.getField("primaryCpuAbi")
            field.get(appInfo) as? String
        }.getOrNull() ?: if (Build.SUPPORTED_ABIS.isNotEmpty()) Build.SUPPORTED_ABIS[0] else null

        val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appInfo.minSdkVersion
        } else {
            null
        }

        val storageUuidStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appInfo.storageUuid?.toString() ?: "Internal Storage"
        } else {
            "Internal Storage"
        }

        val isExternal = (appInfo.flags and ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0
        val isHwAccel = (appInfo.flags and ApplicationInfo.FLAG_HARDWARE_ACCELERATED) != 0
        val isDebug = (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val isBackup = (appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0
        val isLargeHeap = (appInfo.flags and ApplicationInfo.FLAG_LARGE_HEAP) != 0
        val isCleartext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (appInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) != 0
        } else {
            false
        }

        val permissions = pkg.requestedPermissions?.toList() ?: emptyList()
        val permissionsCount = permissions.size

        return PackageExtraInfo(
            splitApks = splitList,
            nativeLibraryDir = appInfo.nativeLibraryDir,
            primaryCpuAbi = primaryCpuAbi,
            isExternalStorage = isExternal,
            targetSdkVersion = appInfo.targetSdkVersion,
            minSdkVersion = minSdk,
            requestedPermissionsCount = permissionsCount,
            declaredPermissions = permissions,
            processName = appInfo.processName,
            storageUuid = storageUuidStr,
            isHardwareAccelerated = isHwAccel,
            isDebuggable = isDebug,
            isBackupAllowed = isBackup,
            isLargeHeap = isLargeHeap,
            isCleartextTrafficPermitted = isCleartext
        )
    }

    private fun calculateApkSizeOnDisk(appInfo: ApplicationInfo): Long {
        var size = appInfo.sourceDir?.let { File(it) }?.takeIf { it.exists() }?.length() ?: 0L
        appInfo.splitSourceDirs?.forEach { path ->
            size += File(path).takeIf { it.exists() }?.length() ?: 0L
        }
        return size
    }

    private fun calculateDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var total = 0L
        dir.walkTopDown().maxDepth(10).forEach { file ->
            if (file.isFile) total += file.length()
        }
        return total
    }

    private fun calculateDirSizeExcluding(dir: File?, excludedDirs: Set<File?>): Long {
        if (dir == null || !dir.exists()) return 0L
        val excludedPaths = excludedDirs.filterNotNull().map { it.absolutePath }
        var total = 0L
        dir.walkTopDown().maxDepth(10).forEach { file ->
            val isExcluded = excludedPaths.any { file.absolutePath.startsWith(it) }
            if (!isExcluded && file.isFile) {
                total += file.length()
            }
        }
        return total
    }

    private fun drawableToBitmap(drawable: Drawable, targetSize: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return scaleBitmap(drawable.bitmap, targetSize)
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else targetSize
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else targetSize
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return scaleBitmap(bitmap, targetSize)
    }

    private fun scaleBitmap(bitmap: Bitmap, targetSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxDim = maxOf(width, height)
        if (maxDim <= targetSize) return bitmap
        val scale = targetSize.toFloat() / maxDim
        val newWidth = (width * scale).toInt().coerceAtLeast(1)
        val newHeight = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    fun filterAndSort(
        apps: List<InstalledAppInfo>,
        query: String,
        filter: AppTypeFilter,
        sortOrder: InstalledAppSortOrder
    ): List<InstalledAppInfo> {
        val trimmed = query.trim()

        return apps.asSequence()
            .filter { app ->
                when (filter) {
                    AppTypeFilter.ALL -> true
                    AppTypeFilter.USER -> app.isUserApp
                    AppTypeFilter.SYSTEM -> !app.isUserApp
                }
            }
            .filter { app ->
                if (trimmed.isEmpty()) true
                else app.appName.contains(trimmed, ignoreCase = true) ||
                        app.packageName.contains(trimmed, ignoreCase = true)
            }
            .sortedWith { a, b ->
                when (sortOrder) {
                    InstalledAppSortOrder.NAME_ASC -> a.appName.compareTo(b.appName, ignoreCase = true)
                    InstalledAppSortOrder.NAME_DESC -> b.appName.compareTo(a.appName, ignoreCase = true)
                    InstalledAppSortOrder.SIZE_DESC -> {
                        // Total Size descending; inaccessible items placed at the end
                        val aSize = a.storageMeasurement.totalBytes
                        val bSize = b.storageMeasurement.totalBytes
                        when {
                            aSize != null && bSize != null -> bSize.compareTo(aSize)
                            aSize != null -> -1
                            bSize != null -> 1
                            else -> a.appName.compareTo(b.appName, ignoreCase = true)
                        }
                    }
                    InstalledAppSortOrder.SIZE_ASC -> {
                        // Total Size ascending; inaccessible items placed at the end
                        val aSize = a.storageMeasurement.totalBytes
                        val bSize = b.storageMeasurement.totalBytes
                        when {
                            aSize != null && bSize != null -> aSize.compareTo(bSize)
                            aSize != null -> -1
                            bSize != null -> 1
                            else -> a.appName.compareTo(b.appName, ignoreCase = true)
                        }
                    }
                    InstalledAppSortOrder.CODE_SIZE_DESC -> {
                        // Code Size descending; inaccessible items placed at the end
                        val aCode = a.storageMeasurement.codeBytes
                        val bCode = b.storageMeasurement.codeBytes
                        when {
                            aCode != null && bCode != null -> bCode.compareTo(aCode)
                            aCode != null -> -1
                            bCode != null -> 1
                            else -> a.appName.compareTo(b.appName, ignoreCase = true)
                        }
                    }
                    InstalledAppSortOrder.DATA_SIZE_DESC -> {
                        // Data Size descending; inaccessible items placed at the end
                        val aData = a.storageMeasurement.dataBytes
                        val bData = b.storageMeasurement.dataBytes
                        when {
                            aData != null && bData != null -> bData.compareTo(aData)
                            aData != null -> -1
                            bData != null -> 1
                            else -> a.appName.compareTo(b.appName, ignoreCase = true)
                        }
                    }
                    InstalledAppSortOrder.CACHE_SIZE_DESC -> {
                        // Cache Size descending; inaccessible items placed at the end
                        val aCache = a.storageMeasurement.cacheBytes
                        val bCache = b.storageMeasurement.cacheBytes
                        when {
                            aCache != null && bCache != null -> bCache.compareTo(aCache)
                            aCache != null -> -1
                            bCache != null -> 1
                            else -> a.appName.compareTo(b.appName, ignoreCase = true)
                        }
                    }
                    InstalledAppSortOrder.RECENTLY_UPDATED -> b.lastUpdateTimeMillis.compareTo(a.lastUpdateTimeMillis)
                    InstalledAppSortOrder.INSTALL_DATE -> b.installTimeMillis.compareTo(a.installTimeMillis)
                    InstalledAppSortOrder.PACKAGE_NAME_ASC -> a.packageName.compareTo(b.packageName, ignoreCase = true)
                }
            }
            .toList()
    }

    companion object {
        @Volatile
        private var instance: InstalledAppsRepository? = null

        fun getInstance(context: Context? = null): InstalledAppsRepository {
            return instance ?: synchronized(this) {
                instance ?: InstalledAppsRepository(context).also { instance = it }
            }
        }
    }
}
