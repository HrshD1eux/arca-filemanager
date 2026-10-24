package com.Hrshd1eux.filemanager.feature.apk.components

import android.widget.Toast
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
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Hrshd1eux.filemanager.core.designsystem.VaultEmerald
import com.Hrshd1eux.filemanager.core.storage.apk.ApkInstallStatus
import com.Hrshd1eux.filemanager.core.storage.apk.ApkInstaller
import com.Hrshd1eux.filemanager.core.storage.apk.ApkPackageDetails
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkInstallBottomSheet(
    apkFile: File,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var details by remember { mutableStateOf<ApkPackageDetails?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var parseFailed by remember { mutableStateOf(false) }

    LaunchedEffect(apkFile.absolutePath) {
        isLoading = true
        parseFailed = false
        val result = withContext(Dispatchers.IO) {
            ApkInstaller.parseApk(context, apkFile)
        }
        if (result != null) {
            details = result
        } else {
            parseFailed = true
        }
        isLoading = false
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Text(
                            text = "Analyzing package metadata…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (parseFailed || details == null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Package Inspection Failed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "ARCA could not parse the application metadata for '${apkFile.name}'. The APK file may be incomplete or corrupted.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                ApkInstaller.install(context, apkFile).onFailure { e ->
                                    Toast.makeText(context, "Cannot launch installer: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Try Install Anyway")
                        }
                    }
                }
            } else {
                val pkg = details!!

                // App Header: Icon, Name, Package ID
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (pkg.icon != null) {
                            Image(
                                bitmap = pkg.icon.asImageBitmap(),
                                contentDescription = pkg.appName,
                                modifier = Modifier.size(52.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Android,
                                contentDescription = null,
                                tint = VaultEmerald,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pkg.appName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = pkg.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Status Badge
                InstallStatusBadge(pkg)

                // Package Metadata Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DetailRow(label = "Version", value = "${pkg.versionName} (${pkg.versionCode})")
                        DetailRow(label = "File Size", value = FileFormatter.formatFileSize(pkg.fileSize))
                        if (pkg.minSdkVersion != null || pkg.targetSdkVersion != null) {
                            val compatStr = buildString {
                                if (pkg.minSdkVersion != null) append("Min Android API ${pkg.minSdkVersion}")
                                if (pkg.minSdkVersion != null && pkg.targetSdkVersion != null) append(" • ")
                                if (pkg.targetSdkVersion != null) append("Target API ${pkg.targetSdkVersion}")
                            }
                            DetailRow(label = "Compatibility", value = compatStr)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                        DetailRow(label = "Source Path", value = pkg.filePath, monospace = true)
                    }
                }

                // Install permission explanation if not currently granted
                val canInstall = ApkInstaller.canInstallPackages(context)
                if (!canInstall) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Permission required: Android requires 'Install unknown apps' to be enabled for ARCA before installing APKs.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            if (!ApkInstaller.canInstallPackages(context)) {
                                Toast.makeText(
                                    context,
                                    "Enable 'Allow from this source', then tap Install again",
                                    Toast.LENGTH_LONG
                                ).show()
                                context.startActivity(ApkInstaller.createManageUnknownAppSourcesIntent(context))
                            } else {
                                ApkInstaller.install(context, apkFile).onFailure { e ->
                                    Toast.makeText(context, "Install failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                                onDismiss()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (pkg.installStatus == ApkInstallStatus.DOWNGRADE) {
                                Color(0xFFE65100)
                            } else {
                                VaultEmerald
                            }
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.InstallMobile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val actionLabel = when (pkg.installStatus) {
                            ApkInstallStatus.NEW_APP -> "Install"
                            ApkInstallStatus.UPDATE -> "Update"
                            ApkInstallStatus.SAME_VERSION -> "Reinstall"
                            ApkInstallStatus.DOWNGRADE -> "Install (Downgrade)"
                        }
                        Text(actionLabel, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallStatusBadge(pkg: ApkPackageDetails) {
    val (bgColor, textColor, icon, label) = when (pkg.installStatus) {
        ApkInstallStatus.NEW_APP -> Quadruple(
            VaultEmerald.copy(alpha = 0.15f),
            VaultEmerald,
            Icons.Default.CheckCircle,
            "New Application"
        )
        ApkInstallStatus.UPDATE -> Quadruple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Default.ArrowUpward,
            "Update: ${pkg.installedVersionName ?: "?"} → ${pkg.versionName}"
        )
        ApkInstallStatus.SAME_VERSION -> Quadruple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Default.Info,
            "Already Installed (v${pkg.versionName})"
        )
        ApkInstallStatus.DOWNGRADE -> Quadruple(
            Color(0xFFFFF3E0),
            Color(0xFFE65100),
            Icons.Default.Warning,
            "Downgrade Warning: Installed v${pkg.installedVersionName ?: "?"} is newer than v${pkg.versionName}"
        )
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun DetailRow(
    label: String,
    value: String,
    monospace: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            fontSize = if (monospace) 11.sp else 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
