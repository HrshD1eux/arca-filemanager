package com.Hrshd1eux.filemanager.core.update.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

import android.app.DownloadManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.core.storage.apk.ApkInstaller
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import com.Hrshd1eux.filemanager.core.update.AppUpdateManager
import com.Hrshd1eux.filemanager.core.update.model.DownloadProgress
import com.Hrshd1eux.filemanager.core.update.model.ReleaseInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

private enum class UpdateDialogStep {
    CHECKING,
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    DOWNLOADING,
    DOWNLOAD_COMPLETED,
    ERROR
}

@Composable
fun AppUpdateDialog(
    currentVersion: String,
    onDismiss: () -> Unit,
    initialRelease: ReleaseInfo? = null,
    onUserInteracted: (versionName: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    var step by remember {
        mutableStateOf(
            if (initialRelease != null) {
                if (initialRelease.isUpdateAvailable) UpdateDialogStep.UPDATE_AVAILABLE else UpdateDialogStep.UP_TO_DATE
            } else {
                UpdateDialogStep.CHECKING
            }
        )
    }
    var releaseInfo by remember { mutableStateOf(initialRelease) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var downloadId by remember { mutableLongStateOf(-1L) }
    var downloadProgress by remember { mutableStateOf<DownloadProgress?>(null) }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    val appIconBitmap = remember(context) {
        runCatching {
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            val sizePx = (32 * context.resources.displayMetrics.density).toInt().coerceAtLeast(64)
            drawable.toBitmap(width = sizePx, height = sizePx, config = Bitmap.Config.ARGB_8888).asImageBitmap()
        }.getOrNull()
    }

    fun runUpdateCheck() {
        step = UpdateDialogStep.CHECKING
        errorMessage = null
        downloadedApkFile = null
        scope.launch {
            AppUpdateManager.checkForUpdates(currentVersion)
                .onSuccess { release ->
                    if (release == null || !release.isUpdateAvailable) {
                        step = UpdateDialogStep.UP_TO_DATE
                        // If an older/matching downloaded APK lingered from previous runs, clean it up
                        if (release != null) {
                            AppUpdateManager.deleteDownloadedApkFile(context, release)
                        }
                    } else {
                        releaseInfo = release
                        // Only when a strictly newer update is available, check if already downloaded
                        val existingFile = AppUpdateManager.getDownloadedApkFile(context, release)
                        val isFullyDownloaded = existingFile != null &&
                            existingFile.exists() &&
                            (release.apkSize <= 0L || existingFile.length() == release.apkSize)

                        if (isFullyDownloaded) {
                            downloadedApkFile = existingFile
                            step = UpdateDialogStep.DOWNLOAD_COMPLETED
                        } else {
                            step = UpdateDialogStep.UPDATE_AVAILABLE
                        }
                    }
                }
                .onFailure { t ->
                    errorMessage = t.localizedMessage ?: "Failed to check for updates."
                    step = UpdateDialogStep.ERROR
                }
        }
    }

    LaunchedEffect(Unit) {
        if (initialRelease != null && initialRelease.isUpdateAvailable) {
            val existingFile = AppUpdateManager.getDownloadedApkFile(context, initialRelease)
            val isFullyDownloaded = existingFile != null &&
                existingFile.exists() &&
                (initialRelease.apkSize <= 0L || existingFile.length() == initialRelease.apkSize)

            if (isFullyDownloaded) {
                downloadedApkFile = existingFile
                step = UpdateDialogStep.DOWNLOAD_COMPLETED
            } else {
                step = UpdateDialogStep.UPDATE_AVAILABLE
            }
        } else if (initialRelease == null) {
            runUpdateCheck()
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (step != UpdateDialogStep.DOWNLOADING) {
                releaseInfo?.versionName?.let(onUserInteracted)
                onDismiss()
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (appIconBitmap != null) {
                    Image(
                        bitmap = appIconBitmap,
                        contentDescription = "ARCA Logo",
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                val icon = when (step) {
                    UpdateDialogStep.CHECKING -> Icons.Default.SystemUpdate
                    UpdateDialogStep.UP_TO_DATE -> Icons.Default.CheckCircle
                    UpdateDialogStep.UPDATE_AVAILABLE -> Icons.Default.NewReleases
                    UpdateDialogStep.DOWNLOADING -> Icons.Default.CloudDownload
                    UpdateDialogStep.DOWNLOAD_COMPLETED -> Icons.Default.CheckCircle
                    UpdateDialogStep.ERROR -> Icons.Default.Error
                }
                val iconTint = when (step) {
                    UpdateDialogStep.UP_TO_DATE, UpdateDialogStep.DOWNLOAD_COMPLETED -> Color(0xFF4CAF50)
                    UpdateDialogStep.UPDATE_AVAILABLE -> MaterialTheme.colorScheme.primary
                    UpdateDialogStep.ERROR -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                }

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = when (step) {
                        UpdateDialogStep.CHECKING -> "Checking for Updates"
                        UpdateDialogStep.UP_TO_DATE -> "App is Up to Date"
                        UpdateDialogStep.UPDATE_AVAILABLE -> "Update Available"
                        UpdateDialogStep.DOWNLOADING -> "Downloading Update"
                        UpdateDialogStep.DOWNLOAD_COMPLETED -> "Ready to Install"
                        UpdateDialogStep.ERROR -> "Update Check Failed"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when (step) {
                    UpdateDialogStep.CHECKING -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Querying GitHub Releases...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    UpdateDialogStep.UP_TO_DATE -> {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF4CAF50).copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "You are running the latest version of ARCA.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Current version: v$currentVersion",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Release builds are directly compiled and published to the GitHub repository.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    UpdateDialogStep.UPDATE_AVAILABLE -> {
                        val release = releaseInfo
                        if (release != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "v${release.versionName}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                if (release.apkSize > 0) {
                                    Text(
                                        text = FileFormatter.formatFileSize(release.apkSize),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = release.releaseName.ifBlank { "ARCA v${release.versionName}" },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )

                            if (release.releaseNotes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 160.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(10.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        Text(
                                            text = release.releaseNotes,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Downloads directly in the background with automatic resume support.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    UpdateDialogStep.DOWNLOADING -> {
                        val prog = downloadProgress
                        val ratio = prog?.progressRatio ?: 0f
                        val percent = prog?.progressPercent ?: 0

                        LinearProgressIndicator(
                            progress = { ratio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (prog != null && prog.totalBytes > 0) {
                                Text(
                                    text = "${FileFormatter.formatFileSize(prog.bytesDownloaded)} / ${FileFormatter.formatFileSize(prog.totalBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = "Connecting to download server...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "$percent%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Downloading update APK directly from GitHub Releases...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    UpdateDialogStep.DOWNLOAD_COMPLETED -> {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF4CAF50).copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Update downloaded successfully!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val fileName = downloadedApkFile?.name ?: releaseInfo?.apkFileName ?: "ARCA-update.apk"
                                Text(
                                    text = fileName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!ApkInstaller.canInstallPackages(context)) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Permission required: Android requires 'Install unknown apps' permission to install the APK.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    UpdateDialogStep.ERROR -> {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Unable to check updates",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = errorMessage ?: "Unknown network error.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                UpdateDialogStep.CHECKING -> {
                    // No action while checking
                }

                UpdateDialogStep.UP_TO_DATE -> {
                    Button(onClick = onDismiss) {
                        Text("Close")
                    }
                }

                UpdateDialogStep.UPDATE_AVAILABLE -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 46.dp),
                            onClick = {
                                val release = releaseInfo ?: return@Button
                                if (release.apkUrl.isBlank()) {
                                    Toast.makeText(context, "No APK asset found for this release.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                onUserInteracted(release.versionName)
                                step = UpdateDialogStep.DOWNLOADING
                                errorMessage = null
                                downloadProgress = DownloadProgress(
                                    status = DownloadManager.STATUS_RUNNING,
                                    bytesDownloaded = 0L,
                                    totalBytes = release.apkSize
                                )
                                downloadJob?.cancel()
                                downloadJob = scope.launch {
                                    AppUpdateManager.downloadApkDirect(context, release).collectLatest { progress ->
                                        downloadProgress = progress
                                        if (progress.isSuccess) {
                                            val apk = AppUpdateManager.getDownloadedApkFile(context, release)
                                            downloadedApkFile = apk
                                            step = UpdateDialogStep.DOWNLOAD_COMPLETED
                                            if (apk != null && ApkInstaller.canInstallPackages(context)) {
                                                AppUpdateManager.installUpdate(context, apk)
                                            }
                                        } else if (progress.isFailed) {
                                            errorMessage = progress.errorMessage
                                                ?: "Download failed. Please check network and retry."
                                            step = UpdateDialogStep.ERROR
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(imageVector = Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Update Now", maxLines = 1, softWrap = false, fontWeight = FontWeight.SemiBold)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 42.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                onClick = {
                                    val release = releaseInfo ?: return@OutlinedButton
                                    if (release.apkUrl.isBlank()) {
                                        Toast.makeText(context, "No APK asset found for this release.", Toast.LENGTH_SHORT).show()
                                        return@OutlinedButton
                                    }
                                    runCatching {
                                        AppUpdateManager.startDownload(context, release)
                                    }.onSuccess {
                                        Toast.makeText(context, "Downloading update in background...", Toast.LENGTH_SHORT).show()
                                        onUserInteracted(release.versionName)
                                        onDismiss()
                                    }.onFailure { err ->
                                        errorMessage = err.localizedMessage ?: "Failed to start background download."
                                        step = UpdateDialogStep.ERROR
                                    }
                                }
                            ) {
                                Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Background", maxLines = 1, softWrap = false)
                            }

                            TextButton(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 42.dp),
                                onClick = {
                                    releaseInfo?.versionName?.let(onUserInteracted)
                                    onDismiss()
                                }
                            ) {
                                Text("Later", maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }

                UpdateDialogStep.DOWNLOADING -> {
                    OutlinedButton(
                        onClick = {
                            downloadJob?.cancel()
                            downloadJob = null
                            if (downloadId != -1L) {
                                val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as DownloadManager
                                dm.remove(downloadId)
                                downloadId = -1L
                            }
                            step = UpdateDialogStep.UPDATE_AVAILABLE
                        }
                    ) {
                        Text("Cancel")
                    }
                }

                UpdateDialogStep.DOWNLOAD_COMPLETED -> {
                    val apk = downloadedApkFile
                    if (!ApkInstaller.canInstallPackages(context)) {
                        Button(
                            onClick = {
                                context.startActivity(ApkInstaller.createManageUnknownAppSourcesIntent(context))
                            }
                        ) {
                            Text("Grant Permission")
                        }
                    } else if (apk != null && apk.exists()) {
                        Button(
                            onClick = {
                                AppUpdateManager.installUpdate(context, apk)
                                    .onFailure { t ->
                                        Toast.makeText(context, "Install failed: ${t.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                            }
                        ) {
                            Icon(imageVector = Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Install Now")
                        }
                    } else {
                        Button(onClick = { runUpdateCheck() }) {
                            Text("Re-download")
                        }
                    }
                }

                UpdateDialogStep.ERROR -> {
                    Button(onClick = { runUpdateCheck() }) {
                        Text("Retry")
                    }
                }
            }
        },
        dismissButton = if (step == UpdateDialogStep.UPDATE_AVAILABLE) {
            null
        } else {
            {
                when (step) {
                    UpdateDialogStep.UP_TO_DATE -> {
                        TextButton(
                            onClick = {
                                runCatching {
                                    uriHandler.openUri("https://github.com/HrshD1eux/arca-filemanager/releases")
                                }
                            }
                        ) {
                            Text("Releases Webpage")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    }
                    UpdateDialogStep.DOWNLOAD_COMPLETED -> {
                        OutlinedButton(onClick = onDismiss) {
                            Text("Close")
                        }
                    }
                    UpdateDialogStep.ERROR -> {
                        OutlinedButton(onClick = onDismiss) {
                            Text("Close")
                        }
                    }
                    else -> {}
                }
            }
        },
        modifier = modifier
    )
}
