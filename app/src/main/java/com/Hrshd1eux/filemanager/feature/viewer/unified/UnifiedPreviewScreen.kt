package com.Hrshd1eux.filemanager.feature.viewer.unified

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.feature.browser.components.DestinationPickerDialog
import com.Hrshd1eux.filemanager.feature.browser.components.OperationProgressDialog
import com.Hrshd1eux.filemanager.core.storage.provider.LocalFileSystemProvider
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import com.Hrshd1eux.filemanager.core.storage.util.FileIntentDispatcher
import com.Hrshd1eux.filemanager.feature.browser.components.DeleteConfirmationDialog
import com.Hrshd1eux.filemanager.feature.properties.FilePropertiesSheet
import com.Hrshd1eux.filemanager.feature.viewer.audio.util.AudioTimeFormatter
import com.Hrshd1eux.filemanager.feature.viewer.unified.model.PreviewState
import com.Hrshd1eux.filemanager.feature.viewer.unified.model.PreviewType
import com.Hrshd1eux.filemanager.feature.viewer.unified.ui.AudioPreviewContent
import com.Hrshd1eux.filemanager.feature.viewer.unified.ui.ErrorPreviewContent
import com.Hrshd1eux.filemanager.feature.viewer.unified.ui.ImagePreviewContent
import com.Hrshd1eux.filemanager.feature.viewer.unified.ui.LoadingPreviewContent
import com.Hrshd1eux.filemanager.feature.viewer.unified.ui.PdfPreviewContent
import com.Hrshd1eux.filemanager.feature.viewer.unified.ui.TextPreviewContent
import com.Hrshd1eux.filemanager.feature.viewer.unified.ui.UnifiedBottomBar
import com.Hrshd1eux.filemanager.feature.viewer.unified.ui.UnifiedTopBar
import com.Hrshd1eux.filemanager.feature.viewer.unified.ui.UnsupportedPreviewContent
import com.Hrshd1eux.filemanager.feature.viewer.unified.ui.VideoPreviewContent
import com.Hrshd1eux.filemanager.feature.viewer.video.util.VideoTimeFormatter
import java.io.File

/**
 * Universal preview screen for ARCA supporting images, video, audio, text, PDFs, and unsupported formats.
 */
@Composable
fun UnifiedPreviewScreen(
    initialPath: String,
    categoryName: String? = null,
    directoryPath: String? = null,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UnifiedPreviewViewModel = viewModel()
) {
    val context = LocalContext.current
    val previewState by viewModel.previewState.collectAsStateWithLifecycle()
    val previewType by viewModel.previewType.collectAsStateWithLifecycle()
    val isOperating by viewModel.isOperating.collectAsStateWithLifecycle()
    val operationProgress by viewModel.operationProgress.collectAsStateWithLifecycle()
    val currentFile = viewModel.currentFile
    val hasPrevious = viewModel.hasPrevious
    val hasNext = viewModel.hasNext

    val showLineNumbers by viewModel.showLineNumbers.collectAsStateWithLifecycle()
    val wordWrap by viewModel.wordWrap.collectAsStateWithLifecycle()
    val isMonospace by viewModel.isMonospace.collectAsStateWithLifecycle()

    var showControls by remember { mutableStateOf(true) }
    var inspectedNode by remember { mutableStateOf<StorageNode?>(null) }
    var nodeToDelete by remember { mutableStateOf<StorageNode?>(null) }
    var showCopyPicker by remember { mutableStateOf(false) }
    var showMovePicker by remember { mutableStateOf(false) }
    var apkToInstall by remember { mutableStateOf<File?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(initialPath, categoryName, directoryPath) {
        viewModel.initialize(initialPath, categoryName, directoryPath)
    }

    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val isMediaMode = previewType == PreviewType.IMAGE || previewType == PreviewType.VIDEO

    // Dynamic subtitle text
    val subtitle = remember(previewState, currentFile) {
        val node = currentFile
        if (node == null) "" else {
            val sizeStr = FileFormatter.formatFileSize(node.size)
            when (val state = previewState) {
                is PreviewState.ImageReady -> "$sizeStr • ${state.dimensions}"
                is PreviewState.VideoReady -> {
                    val dur = state.metadata?.durationMs ?: 0L
                    val durStr = if (dur > 0L) VideoTimeFormatter.formatDuration(dur) else ""
                    listOf(sizeStr, durStr).filter { it.isNotBlank() }.joinToString(" • ")
                }
                is PreviewState.AudioReady -> {
                    val dur = state.metadata?.durationMs ?: 0L
                    val durStr = if (dur > 0L) AudioTimeFormatter.formatDuration(dur) else ""
                    listOf(sizeStr, durStr).filter { it.isNotBlank() }.joinToString(" • ")
                }
                is PreviewState.TextReady -> "$sizeStr • ${state.totalLines} lines"
                is PreviewState.PdfReady -> "$sizeStr • ${state.pageCount} pages"
                else -> sizeStr
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isMediaMode) Color.Black else MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (isMediaMode) {
                    showControls = !showControls
                }
            }
    ) {
        // Main Preview Content Area
        Box(modifier = Modifier.fillMaxSize()) {
            when (val state = previewState) {
                is PreviewState.Loading -> {
                    LoadingPreviewContent(file = state.file)
                }

                is PreviewState.ImageReady -> {
                    ImagePreviewContent(file = state.file)
                }

                is PreviewState.VideoReady -> {
                    VideoPreviewContent(file = state.file, metadata = state.metadata)
                }

                is PreviewState.AudioReady -> {
                    AudioPreviewContent(file = state.file, metadata = state.metadata)
                }

                is PreviewState.TextReady -> {
                    TextPreviewContent(
                        file = state.file,
                        lines = state.lines,
                        isTruncated = state.isTruncated,
                        totalLines = state.totalLines,
                        totalBytes = state.totalBytes,
                        showLineNumbers = showLineNumbers,
                        wordWrap = wordWrap,
                        isMonospace = isMonospace,
                        modifier = Modifier.padding(top = 64.dp, bottom = 64.dp)
                    )
                }

                is PreviewState.PdfReady -> {
                    PdfPreviewContent(
                        file = state.file,
                        pageCount = state.pageCount,
                        currentPageIndex = state.currentPageIndex,
                        currentPageBitmap = state.currentPageBitmap,
                        onPreviousPage = { viewModel.previousPdfPage() },
                        onNextPage = { viewModel.nextPdfPage() },
                        modifier = Modifier.padding(top = 64.dp, bottom = 64.dp)
                    )
                }

                is PreviewState.Unsupported -> {
                    UnsupportedPreviewContent(
                        file = state.file,
                        mimeType = state.mimeType,
                        reason = state.reason,
                        onOpenWith = {
                            currentFile?.let { FileIntentDispatcher.openWith(context, it) }
                        },
                        onShowProperties = {
                            currentFile?.let { inspectedNode = it }
                        },
                        onInstallApk = {
                            apkToInstall = state.file
                        },
                        modifier = Modifier.padding(top = 64.dp, bottom = 64.dp)
                    )
                }

                is PreviewState.Error -> {
                    ErrorPreviewContent(
                        file = state.file,
                        message = state.message,
                        onRetry = { viewModel.retry() },
                        onOpenWith = {
                            currentFile?.let { FileIntentDispatcher.openWith(context, it) }
                        },
                        modifier = Modifier.padding(top = 64.dp, bottom = 64.dp)
                    )
                }
            }
        }

        // Top Overlay Bar
        AnimatedVisibility(
            visible = showControls || !isMediaMode,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            UnifiedTopBar(
                title = currentFile?.name ?: "Preview",
                subtitle = subtitle,
                onNavigateBack = onNavigateBack,
                onOpenWith = {
                    currentFile?.let { FileIntentDispatcher.openWith(context, it) }
                },
                onShowProperties = {
                    currentFile?.let { inspectedNode = it }
                },
                isDarkOverlay = isMediaMode,
                customActions = {
                    if (previewType == PreviewType.TEXT) {
                        IconButton(onClick = { viewModel.toggleLineNumbers() }) {
                            Icon(
                                imageVector = Icons.Default.FormatListNumbered,
                                contentDescription = "Toggle line numbers",
                                tint = if (showLineNumbers) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.toggleWordWrap() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.WrapText,
                                contentDescription = "Toggle word wrap",
                                tint = if (wordWrap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.toggleMonospace() }) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = "Toggle monospace",
                                tint = if (isMonospace) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        }

        // Bottom Action Bar
        AnimatedVisibility(
            visible = showControls || !isMediaMode,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            UnifiedBottomBar(
                hasPrevious = hasPrevious,
                hasNext = hasNext,
                onNavigatePrevious = { viewModel.navigatePrevious() },
                onShare = {
                    currentFile?.let { FileIntentDispatcher.share(context, it) }
                },
                onCopy = { showCopyPicker = true },
                onMove = { showMovePicker = true },
                onDelete = {
                    currentFile?.let { nodeToDelete = it }
                },
                onNavigateNext = { viewModel.navigateNext() },
                isDarkOverlay = isMediaMode
            )
        }
    }

    // Properties Bottom Sheet
    inspectedNode?.let { node ->
        FilePropertiesSheet(
            node = node,
            onDismiss = { inspectedNode = null }
        )
    }

    // Delete Confirmation Dialog
    nodeToDelete?.let { node ->
        DeleteConfirmationDialog(
            node = node,
            onConfirmDelete = { permanent ->
                nodeToDelete = null
                viewModel.deleteCurrent(permanent = permanent, onDismissAfterDelete = onNavigateBack)
            },
            onDismiss = { nodeToDelete = null }
        )
    }

    // Copy Destination Picker
    if (showCopyPicker && currentFile != null) {
        DestinationPickerDialog(
            storageProvider = remember { LocalFileSystemProvider() },
            title = "Copy ${currentFile.name} to",
            confirmButtonText = "Copy Here",
            onDestinationSelected = { dest, verifyIntegrity ->
                showCopyPicker = false
                viewModel.copyCurrentFile(dest, verifyIntegrity)
            },
            onDismiss = { showCopyPicker = false }
        )
    }

    // Move Destination Picker
    if (showMovePicker && currentFile != null) {
        DestinationPickerDialog(
            storageProvider = remember { LocalFileSystemProvider() },
            title = "Move ${currentFile.name} to",
            confirmButtonText = "Move Here",
            onDestinationSelected = { dest, _ ->
                showMovePicker = false
                viewModel.moveCurrentFile(dest, onDismissAfterMove = onNavigateBack)
            },
            onDismiss = { showMovePicker = false }
        )
    }

    // Operation Progress Dialog
    val currentProgress = operationProgress
    if (isOperating && currentProgress != null) {
        OperationProgressDialog(
            progress = currentProgress,
            title = "Processing...",
            onCancel = {}
        )
    }

    // APK Installation Bottom Sheet
    apkToInstall?.let { file ->
        com.Hrshd1eux.filemanager.feature.apk.components.ApkInstallBottomSheet(
            apkFile = file,
            onDismiss = { apkToInstall = null }
        )
    }

    // Floating Snackbar Notification Host
    Box(modifier = Modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (showControls) 80.dp else 16.dp)
        )
    }
}
