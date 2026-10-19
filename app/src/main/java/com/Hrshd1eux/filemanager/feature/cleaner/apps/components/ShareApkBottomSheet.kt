package com.Hrshd1eux.filemanager.feature.cleaner.apps.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Hrshd1eux.filemanager.core.designsystem.VaultEmerald
import com.Hrshd1eux.filemanager.core.storage.apk.ApkShareInspection
import com.Hrshd1eux.filemanager.core.storage.apk.ApkShareManager
import com.Hrshd1eux.filemanager.core.storage.apk.ApkShareMode
import com.Hrshd1eux.filemanager.core.storage.apk.ApkShareResult
import com.Hrshd1eux.filemanager.core.storage.model.InstalledAppInfo
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import kotlinx.coroutines.launch

/**
 * Bottom sheet presenting APK sharing options, split APK limitations disclosure,
 * storage requirements check, and Android standard share interface dispatch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareApkBottomSheet(
    app: InstalledAppInfo,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    apkShareManager: ApkShareManager = remember { ApkShareManager.getInstance() }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var inspection by remember { mutableStateOf<ApkShareInspection?>(null) }
    var isPreparing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var storageWarning by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(app) {
        val insp = apkShareManager.inspectShareableApks(app)
        inspection = insp
        if (insp.isShareable) {
            val hasSpace = apkShareManager.hasSufficientSpace(context.cacheDir, insp.totalSizeBytes)
            if (!hasSpace) {
                val available = apkShareManager.getAvailableCacheBytes(context.cacheDir)
                val needed = insp.totalSizeBytes + ApkShareManager.DEFAULT_SAFETY_BUFFER_BYTES
                storageWarning = "Low cache storage: ${FileFormatter.formatFileSize(available)} available. Preparing may require ${FileFormatter.formatFileSize(needed)}."
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Icon, Name, Version, Format Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (app.icon != null) {
                        Image(
                            bitmap = app.icon.asImageBitmap(),
                            contentDescription = app.appName,
                            modifier = Modifier.size(48.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = app.displayVersion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (app.isSplitApk) {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    } else {
                        VaultEmerald.copy(alpha = 0.12f)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (app.isSplitApk) Icons.Default.Layers else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (app.isSplitApk) MaterialTheme.colorScheme.tertiary else VaultEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (app.isSplitApk) "${app.splitApkCount} Splits" else "Single APK",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (app.isSplitApk) MaterialTheme.colorScheme.tertiary else VaultEmerald
                        )
                    }
                }
            }

            // Inaccessible APK Alert
            if (inspection != null && !inspection!!.isShareable) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "APK Binary Inaccessible",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = inspection?.inaccessibilityReason ?: "The application package files are restricted by Android system security or missing from disk.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Storage Warning Alert
            if (storageWarning != null) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = storageWarning!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Error Message Alert
            if (errorMessage != null) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // APK Files Breakdown Card
            if (inspection != null && inspection!!.isShareable) {
                val insp = inspection!!
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Package Files Breakdown",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Base APK (${insp.baseFile?.name ?: "base.apk"})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = insp.formattedBaseSize,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (insp.isSplitApk && insp.splitFiles.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                text = "Split APKs (${insp.splitFiles.size} components):",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            insp.splitFiles.forEach { sFile ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "• ${sFile.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = FileFormatter.formatFileSize(sFile.length()),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Total App Bundle Size",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = insp.formattedTotalSize,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Split APK Limitations & Universal Installability Explanation
            if (inspection?.isSplitApk == true) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "APK Sharing Limitations",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Text(
                            text = "This application is installed as multiple split APKs (Android App Bundle). Splitting optimizes downloads for this device's specific CPU architecture and screen density.\n\nSharing only the base APK may fail to install or run on another device. Universal installability is not guaranteed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Licensing & Distribution Restrictions Notice
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Licensing & Security Notice",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "ARCA extracts unmodified installed APK packages for local backup and compatible sharing targets (Quick Share, WhatsApp, Drive). Paid, DRM-protected, or hardware-specific applications may require active store verification on the receiving device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action Buttons
            if (isPreparing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(
                            text = "Preparing APK for sharing...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                val isShareable = inspection?.isShareable == true

                if (inspection?.isSplitApk == true) {
                    // Option 1: Share All Split APKs (Recommended)
                    Button(
                        onClick = {
                            dispatchShare(
                                scope = scope,
                                context = context,
                                app = app,
                                mode = ApkShareMode.ALL_SPLITS,
                                apkShareManager = apkShareManager,
                                onStart = { isPreparing = true; errorMessage = null },
                                onComplete = { isPreparing = false },
                                onError = { errorMessage = it },
                                onSuccess = { onDismiss() }
                            )
                        },
                        enabled = isShareable,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share All APKs (Base + ${inspection?.splitFiles?.size ?: 0} Splits)")
                    }

                    // Option 2: Share Base APK Only
                    OutlinedButton(
                        onClick = {
                            dispatchShare(
                                scope = scope,
                                context = context,
                                app = app,
                                mode = ApkShareMode.BASE_ONLY,
                                apkShareManager = apkShareManager,
                                onStart = { isPreparing = true; errorMessage = null },
                                onComplete = { isPreparing = false },
                                onError = { errorMessage = it },
                                onSuccess = { onDismiss() }
                            )
                        },
                        enabled = isShareable,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Share Base APK Only (May Fail on Other Devices)")
                    }
                } else {
                    // Single Standalone APK
                    Button(
                        onClick = {
                            dispatchShare(
                                scope = scope,
                                context = context,
                                app = app,
                                mode = ApkShareMode.BASE_ONLY,
                                apkShareManager = apkShareManager,
                                onStart = { isPreparing = true; errorMessage = null },
                                onComplete = { isPreparing = false },
                                onError = { errorMessage = it },
                                onSuccess = { onDismiss() }
                            )
                        },
                        enabled = isShareable,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VaultEmerald),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share APK File")
                    }
                }
            }
        }
    }
}

private fun dispatchShare(
    scope: kotlinx.coroutines.CoroutineScope,
    context: Context,
    app: InstalledAppInfo,
    mode: ApkShareMode,
    apkShareManager: ApkShareManager,
    onStart: () -> Unit,
    onComplete: () -> Unit,
    onError: (String) -> Unit,
    onSuccess: () -> Unit
) {
    onStart()
    scope.launch {
        val result = apkShareManager.prepareAndCreateShareIntent(
            context = context,
            app = app,
            shareMode = mode
        )
        onComplete()
        when (result) {
            is ApkShareResult.Success -> {
                runCatching {
                    context.startActivity(result.chooserIntent)
                    onSuccess()
                }.onFailure { e ->
                    onError("Failed to launch sharing target: ${e.localizedMessage}")
                }
            }
            is ApkShareResult.InsufficientStorage -> {
                onError("Insufficient temporary cache storage. Required: ${result.formattedRequired}, Available: ${result.formattedAvailable}. Please free up device storage.")
            }
            is ApkShareResult.Inaccessible -> {
                onError(result.reason)
            }
            is ApkShareResult.ExtractionFailed -> {
                onError(result.message)
            }
        }
    }
}
