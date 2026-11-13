package com.Hrshd1eux.filemanager.feature.backup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.res.stringResource
import com.Hrshd1eux.filemanager.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Hrshd1eux.filemanager.core.backup.engine.BackupProgress
import com.Hrshd1eux.filemanager.core.backup.engine.BackupReport
import com.Hrshd1eux.filemanager.core.backup.model.BackupMode
import com.Hrshd1eux.filemanager.core.backup.model.BackupPreviewReport
import com.Hrshd1eux.filemanager.core.backup.model.BackupProfile
import com.Hrshd1eux.filemanager.core.backup.model.BackupStatus
import com.Hrshd1eux.filemanager.core.designsystem.VaultEmerald
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = viewModel()
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val isExecuting by viewModel.isExecuting.collectAsStateWithLifecycle()
    val isPreviewing by viewModel.isPreviewing.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val previewReport by viewModel.previewReport.collectAsStateWithLifecycle()
    val lastReport by viewModel.lastReport.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var editingProfile by remember { mutableStateOf<BackupProfile?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var pendingMirrorConfirmationProfile by remember { mutableStateOf<BackupProfile?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.backup_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { isCreatingNew = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.backup_new_profile)
                        )
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Notice banner explaining Safe Copy vs Mirror Sync
            item {
                BackupReliabilityNoticeCard()
            }

            if (profiles.isEmpty()) {
                item {
                    EmptyProfilesCard(onCreateClick = { isCreatingNew = true })
                }
            } else {
                items(items = profiles, key = { it.id }) { profile ->
                    BackupProfileCard(
                        profile = profile,
                        isBusy = isExecuting || isPreviewing,
                        onPreview = { viewModel.generatePreview(profile) },
                        onRun = {
                            if (profile.mode == BackupMode.MIRROR) {
                                // Pre-check preview to verify if mirror deletions will occur
                                pendingMirrorConfirmationProfile = profile
                            } else {
                                viewModel.executeBackup(profile)
                            }
                        },
                        onEdit = { editingProfile = profile },
                        onDelete = { viewModel.deleteProfile(profile.id) }
                    )
                }
            }
        }
    }

    // Edit / Create Profile Dialog
    if (editingProfile != null || isCreatingNew) {
        ProfileEditDialog(
            profile = editingProfile ?: BackupProfile(
                name = "My Backup Profile",
                sourcePaths = listOf("/storage/emulated/0/DCIM"),
                destinationPath = "/storage/emulated/0/Backups/DCIM",
                mode = BackupMode.COPY,
                verifyIntegrity = true
            ),
            onDismiss = {
                editingProfile = null
                isCreatingNew = false
            },
            onSave = { updated ->
                viewModel.saveProfile(updated)
                editingProfile = null
                isCreatingNew = false
            }
        )
    }

    // Pre-Execution Backup Preview Dialog (Milestone 1.4.26)
    if (previewReport != null) {
        BackupPreviewDialog(
            report = previewReport!!,
            onDismiss = { viewModel.dismissPreview() },
            onExecute = {
                val currentProfile = profiles.find { it.id == previewReport!!.profileId }
                if (currentProfile != null) {
                    if (previewReport!!.mode == BackupMode.MIRROR && previewReport!!.filesToDeleteInMirror.isNotEmpty()) {
                        pendingMirrorConfirmationProfile = currentProfile
                    } else {
                        viewModel.dismissPreview()
                        viewModel.executeBackup(currentProfile)
                    }
                }
            }
        )
    }

    // Mirror Mode Confirmation Dialog (Milestone 1.4.28)
    if (pendingMirrorConfirmationProfile != null) {
        val targetProfile = pendingMirrorConfirmationProfile!!
        MirrorModeConfirmationDialog(
            profile = targetProfile,
            onDismiss = { pendingMirrorConfirmationProfile = null },
            onConfirm = {
                viewModel.dismissPreview()
                viewModel.executeBackup(targetProfile, confirmedMirrorDeletion = true)
                pendingMirrorConfirmationProfile = null
            }
        )
    }

    // Executing Progress Dialog (Milestone 1.4.27)
    if (isExecuting) {
        BackupProgressDialog(
            progress = progress ?: BackupProgress(),
            onCancel = { viewModel.cancelExecution() }
        )
    }

    // Completion Report Dialog (Milestone 1.4.29)
    if (lastReport != null) {
        BackupReportDialog(
            report = lastReport!!,
            onDismiss = { viewModel.dismissLastReport() }
        )
    }

    // Error Dialog
    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text(stringResource(R.string.first_run_storage_title)) },
            text = { Text(errorMessage!!) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text(stringResource(R.string.button_ok))
                }
            }
        )
    }
}

@Composable
private fun BackupReliabilityNoticeCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = stringResource(R.string.backup_pipeline_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.backup_pipeline_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BackupProfileCard(
    profile: BackupProfile,
    isBusy: Boolean,
    onPreview: () -> Unit,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    val (modeColor, modeIcon) = when (profile.mode) {
        BackupMode.COPY -> VaultEmerald to Icons.Default.Backup
        BackupMode.INCREMENTAL -> Color(0xFF3B82F6) to Icons.Default.Backup
        BackupMode.MIRROR -> Color(0xFFF59E0B) to Icons.Default.Warning
        BackupMode.FULL -> Color(0xFF8B5CF6) to Icons.Default.Backup
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Name & Mode Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(modeColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = modeIcon,
                            contentDescription = null,
                            tint = modeColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = profile.mode.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = modeColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row {
                    IconButton(onClick = onEdit, enabled = !isBusy) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.backup_edit_profile), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, enabled = !isBusy) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.backup_delete_profile), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Paths
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.backup_source_label, profile.sourcePaths.joinToString()),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.backup_destination_label, profile.destinationPath),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (profile.verifyIntegrity) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = VaultEmerald, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.backup_sha256_enabled), style = MaterialTheme.typography.labelSmall, color = VaultEmerald)
                    }
                }
            }

            // Last Run Status
            if (profile.lastRunTimestamp != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val statusColor = when (profile.lastRunStatus) {
                    BackupStatus.SUCCESS -> VaultEmerald
                    BackupStatus.PARTIAL_SUCCESS -> Color(0xFFF59E0B)
                    BackupStatus.FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.outline
                }
                Text(
                    text = stringResource(R.string.backup_last_run_label, dateFormat.format(Date(profile.lastRunTimestamp)), profile.lastRunStatus?.name ?: "-"),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
                if (!profile.lastRunSummary.isNullOrBlank()) {
                    Text(
                        text = profile.lastRunSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onPreview,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Preview, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.backup_btn_preview))
                }

                Button(
                    onClick = onRun,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.backup_btn_run))
                }
            }
        }
    }
}

@Composable
private fun EmptyProfilesCard(onCreateClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Backup,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.backup_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.backup_empty_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onCreateClick) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.backup_new_profile))
            }
        }
    }
}

@Composable
private fun ProfileEditDialog(
    profile: BackupProfile,
    onDismiss: () -> Unit,
    onSave: (BackupProfile) -> Unit
) {
    var name by remember { mutableStateOf(profile.name) }
    var sourcePath by remember { mutableStateOf(profile.sourcePaths.firstOrNull() ?: "") }
    var destinationPath by remember { mutableStateOf(profile.destinationPath) }
    var mode by remember { mutableStateOf(profile.mode) }
    var verifyIntegrity by remember { mutableStateOf(profile.verifyIntegrity) }
    var exclusions by remember { mutableStateOf(profile.exclusionPatterns.joinToString(", ")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (profile.id.isNotBlank()) stringResource(R.string.backup_configure_profile) else stringResource(R.string.backup_new_profile),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.backup_profile_name)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = sourcePath,
                    onValueChange = { sourcePath = it },
                    label = { Text(stringResource(R.string.backup_source_directory)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = destinationPath,
                    onValueChange = { destinationPath = it },
                    label = { Text(stringResource(R.string.backup_destination_directory)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.backup_mode_policy),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = mode == BackupMode.COPY,
                            onClick = { mode = BackupMode.COPY }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.backup_mode_safe_copy), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.backup_mode_safe_copy_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = mode == BackupMode.INCREMENTAL,
                            onClick = { mode = BackupMode.INCREMENTAL }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.backup_mode_incremental), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.backup_mode_incremental_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = mode == BackupMode.MIRROR,
                            onClick = { mode = BackupMode.MIRROR }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.backup_mode_mirror_sync), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                            Text(stringResource(R.string.backup_mode_mirror_sync_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.backup_verify_integrity), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.backup_verify_integrity_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = verifyIntegrity,
                        onCheckedChange = { verifyIntegrity = it }
                    )
                }

                OutlinedTextField(
                    value = exclusions,
                    onValueChange = { exclusions = it },
                    label = { Text(stringResource(R.string.backup_exclusion_patterns)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val patterns = exclusions.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    onSave(
                        profile.copy(
                            name = name.ifBlank { "Backup Profile" },
                            sourcePaths = listOf(sourcePath),
                            destinationPath = destinationPath,
                            mode = mode,
                            verifyIntegrity = verifyIntegrity,
                            exclusionPatterns = patterns
                        )
                    )
                },
                enabled = sourcePath.isNotBlank() && destinationPath.isNotBlank()
            ) {
                Text(stringResource(R.string.backup_save_profile))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun BackupPreviewDialog(
    report: BackupPreviewReport,
    onDismiss: () -> Unit,
    onExecute: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.backup_preview_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(report.profileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PreviewStatCard(
                    title = stringResource(R.string.backup_files_to_copy),
                    count = report.filesToCopy.size,
                    bytes = report.filesToCopy.sumOf { it.sizeBytes },
                    color = VaultEmerald
                )

                PreviewStatCard(
                    title = stringResource(R.string.backup_files_to_update),
                    count = report.filesToUpdate.size,
                    bytes = report.filesToUpdate.sumOf { it.sizeBytes },
                    color = Color(0xFF3B82F6)
                )

                PreviewStatCard(
                    title = stringResource(R.string.backup_files_already_matching),
                    count = report.filesAlreadyMatching.size,
                    bytes = report.filesAlreadyMatching.sumOf { it.sizeBytes },
                    color = Color.Gray
                )

                if (report.filesToDeleteInMirror.isNotEmpty()) {
                    PreviewStatCard(
                        title = stringResource(R.string.backup_files_to_delete_mirror),
                        count = report.filesToDeleteInMirror.size,
                        bytes = report.totalBytesToDelete,
                        color = MaterialTheme.colorScheme.error,
                        isWarning = true
                    )
                }

                if (report.inaccessibleItems.isNotEmpty()) {
                    PreviewStatCard(
                        title = stringResource(R.string.backup_inaccessible_items),
                        count = report.inaccessibleItems.size,
                        bytes = 0L,
                        color = Color(0xFFF59E0B)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.backup_total_transfer, FileFormatter.formatFileSize(report.totalBytesToCopy)),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        confirmButton = {
            Button(onClick = onExecute) {
                Text(stringResource(R.string.backup_start_button))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
private fun PreviewStatCard(
    title: String,
    count: Int,
    bytes: Long,
    color: Color,
    isWarning: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.1f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                if (isWarning) {
                    Text(
                        text = stringResource(R.string.backup_caution_mirror_deletions),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Text(
                text = "$count (${FileFormatter.formatFileSize(bytes)})",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

@Composable
private fun MirrorModeConfirmationDialog(
    profile: BackupProfile,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
        },
        title = {
            Text(stringResource(R.string.backup_confirm_mirror_title), fontWeight = FontWeight.Bold)
        },
        text = {
            Text(stringResource(R.string.backup_confirm_mirror_desc))
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.backup_btn_delete_and_mirror))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun BackupProgressDialog(
    progress: BackupProgress,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(stringResource(R.string.backup_progress_heading), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinearProgressIndicator(
                    progress = { progress.percent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.backup_progress_files, progress.filesProcessed, progress.totalFiles),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "${progress.percent.toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (progress.currentFileName.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.backup_progress_current, progress.currentFileName),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (progress.speedBytesPerSec > 0) {
                    Text(
                        text = stringResource(R.string.backup_progress_speed, FileFormatter.formatFileSize(progress.speedBytesPerSec)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onCancel) {
                Text(stringResource(R.string.backup_cancel_button))
            }
        }
    )
}

@Composable
private fun BackupReportDialog(
    report: BackupReport,
    onDismiss: () -> Unit
) {
    val (statusColor, statusIcon) = when (report.status) {
        BackupStatus.SUCCESS -> VaultEmerald to Icons.Default.CheckCircle
        BackupStatus.PARTIAL_SUCCESS -> Color(0xFFF59E0B) to Icons.Default.Warning
        BackupStatus.FAILED -> MaterialTheme.colorScheme.error to Icons.Default.Close
        else -> MaterialTheme.colorScheme.primary to Icons.Default.Info
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(imageVector = statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(32.dp))
        },
        title = {
            Text(stringResource(R.string.backup_completed_status, report.status.name), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(report.summary, style = MaterialTheme.typography.bodySmall)

                HorizontalDivider()

                Text(stringResource(R.string.backup_report_transferred, FileFormatter.formatFileSize(report.bytesTransferred)), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.backup_report_copied, report.filesCopied), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.backup_report_skipped, report.filesSkipped), style = MaterialTheme.typography.bodySmall)

                if (report.deletedInMirrorCount > 0) {
                    Text(stringResource(R.string.backup_report_pruned, report.deletedInMirrorCount), style = MaterialTheme.typography.bodySmall, color = Color(0xFFD97706))
                }

                if (report.verifiedCount > 0 || report.failedVerificationCount > 0) {
                    Text(
                        stringResource(R.string.backup_report_integrity, report.verifiedCount, report.failedVerificationCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (report.failedVerificationCount > 0) MaterialTheme.colorScheme.error else VaultEmerald
                    )
                }

                if (report.errors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.backup_report_errors, report.errors.size), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    for (err in report.errors.take(5)) {
                        Text("• ${err.filePath}: ${err.errorMessage}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.action_done))
            }
        }
    )
}
