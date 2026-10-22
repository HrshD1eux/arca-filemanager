package com.Hrshd1eux.filemanager.feature.viewer.image

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.provider.LocalFileSystemProvider
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import com.Hrshd1eux.filemanager.core.storage.util.FileIntentDispatcher
import com.Hrshd1eux.filemanager.feature.browser.components.DeleteConfirmationDialog
import com.Hrshd1eux.filemanager.feature.browser.components.DestinationPickerDialog
import com.Hrshd1eux.filemanager.feature.browser.components.OperationProgressDialog
import com.Hrshd1eux.filemanager.feature.properties.FilePropertiesSheet
import kotlinx.coroutines.launch

@Composable
fun ImageViewerScreen(
    initialPath: String,
    categoryName: String? = null,
    directoryPath: String? = null,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImageViewerViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val listState by viewModel.listState.collectAsStateWithLifecycle()
    val images by viewModel.images.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()
    val bitmapState by viewModel.currentBitmapState.collectAsStateWithLifecycle()
    val dimensions by viewModel.currentDimensions.collectAsStateWithLifecycle()
    val operationProgress by viewModel.operationProgress.collectAsStateWithLifecycle()
    val isOperating by viewModel.isOperating.collectAsStateWithLifecycle()

    var showControls by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPropertiesSheet by remember { mutableStateOf(false) }
    var showCopyPicker by remember { mutableStateOf(false) }
    var showMovePicker by remember { mutableStateOf(false) }

    LaunchedEffect(initialPath, categoryName, directoryPath) {
        viewModel.initialize(initialPath, categoryName, directoryPath)
    }

    val currentImage = viewModel.currentImage

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (val state = listState) {
            is ImageViewerListState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            is ImageViewerListState.Empty -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BrokenImage,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "No images found",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Button(
                            onClick = onNavigateBack,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Go Back")
                        }
                    }
                }
            }
            is ImageViewerListState.Ready -> {
                val pagerState = rememberPagerState(
                    initialPage = state.initialIndex,
                    pageCount = { images.size }
                )

                // Sync pager swipes with ViewModel selection
                LaunchedEffect(pagerState.currentPage) {
                    if (pagerState.currentPage != currentIndex) {
                        viewModel.selectIndex(pagerState.currentPage)
                    }
                }

                // Sync ViewModel previous/next button triggers with Pager
                LaunchedEffect(currentIndex) {
                    if (pagerState.currentPage != currentIndex && currentIndex in 0 until images.size) {
                        pagerState.animateScrollToPage(currentIndex)
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = true, // individual page handles disabling user scroll when zoomed in
                    key = { page -> images.getOrNull(page)?.path ?: page.toString() }
                ) { page ->
                    val pageNode = images.getOrNull(page)
                    if (pageNode != null) {
                        val isCurrentPage = (page == currentIndex)
                        ZoomableImagePage(
                            node = pageNode,
                            isCurrentPage = isCurrentPage,
                            bitmapState = if (isCurrentPage) bitmapState else ImageBitmapState.Loading,
                            onToggleControls = { showControls = !showControls },
                            onRetry = { viewModel.retryCurrent() },
                            onOpenWith = { FileIntentDispatcher.openWith(context, pageNode) }
                        )
                    }
                }

                // Top Controls Overlay
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    TopOverlayBar(
                        node = currentImage,
                        currentIndex = currentIndex,
                        totalCount = images.size,
                        dimensionsFormatted = dimensions?.formatted,
                        onBack = onNavigateBack,
                        onProperties = { showPropertiesSheet = true }
                    )
                }

                // Bottom Controls Overlay
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    BottomOverlayBar(
                        hasPrevious = viewModel.hasPrevious,
                        hasNext = viewModel.hasNext,
                        onPrevious = { viewModel.goToPrevious() },
                        onNext = { viewModel.goToNext() },
                        onShare = {
                            currentImage?.let { FileIntentDispatcher.share(context, it) }
                        },
                        onCopy = { showCopyPicker = true },
                        onMove = { showMovePicker = true },
                        onDelete = { showDeleteDialog = true }
                    )
                }
            }
        }
    }

    // Single Delete Dialog
    if (showDeleteDialog && currentImage != null) {
        DeleteConfirmationDialog(
            node = currentImage,
            onConfirmDelete = { permanent ->
                showDeleteDialog = false
                viewModel.deleteCurrentImage(permanent = permanent, onAllDeleted = onNavigateBack)
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    // Properties Sheet
    if (showPropertiesSheet && currentImage != null) {
        FilePropertiesSheet(
            node = currentImage,
            onDismiss = { showPropertiesSheet = false }
        )
    }

    // Copy Destination Picker
    if (showCopyPicker && currentImage != null) {
        DestinationPickerDialog(
            storageProvider = remember { LocalFileSystemProvider() },
            title = "Copy image to",
            confirmButtonText = "Copy Here",
            onDestinationSelected = { dest, verifyIntegrity ->
                showCopyPicker = false
                viewModel.copyCurrentImage(dest, verifyIntegrity)
            },
            onDismiss = { showCopyPicker = false }
        )
    }

    // Move Destination Picker
    if (showMovePicker && currentImage != null) {
        DestinationPickerDialog(
            storageProvider = remember { LocalFileSystemProvider() },
            title = "Move image to",
            confirmButtonText = "Move Here",
            onDestinationSelected = { dest, verifyIntegrity ->
                showMovePicker = false
                viewModel.moveCurrentImage(dest, verifyIntegrity, onAllDeleted = onNavigateBack)
            },
            onDismiss = { showMovePicker = false }
        )
    }

    // In-flight Operation Progress Dialog
    if (isOperating && operationProgress != null) {
        OperationProgressDialog(
            progress = operationProgress!!,
            title = "Processing Image…",
            onCancel = { /* background completes */ }
        )
    }
}

@Composable
private fun ZoomableImagePage(
    node: StorageNode,
    isCurrentPage: Boolean,
    bitmapState: ImageBitmapState,
    onToggleControls: () -> Unit,
    onRetry: () -> Unit,
    onOpenWith: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Reset zoom when navigating away from this page
    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(node.path) {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = {
                        if (scale > 1.2f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            }
            .pointerInput(node.path) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = newScale
                    if (newScale > 1f) {
                        val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                        val maxOffsetY = (size.height * (newScale - 1f)) / 2f
                        offset = Offset(
                            x = (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                            y = (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                        )
                    } else {
                        offset = Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when (bitmapState) {
            is ImageBitmapState.Loading -> {
                CircularProgressIndicator(
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(44.dp)
                )
            }
            is ImageBitmapState.Success -> {
                Image(
                    bitmap = bitmapState.bitmap.asImageBitmap(),
                    contentDescription = node.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                )
            }
            is ImageBitmapState.Error -> {
                CorruptedImageCard(
                    node = node,
                    message = bitmapState.message,
                    onRetry = onRetry,
                    onOpenWith = onOpenWith
                )
            }
        }
    }
}

@Composable
private fun CorruptedImageCard(
    node: StorageNode,
    message: String,
    onRetry: () -> Unit,
    onOpenWith: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(32.dp)
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E24)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.BrokenImage,
                contentDescription = null,
                tint = Color(0xFFEF5350),
                modifier = Modifier.size(56.dp)
            )
            Text(
                text = "Unable to load image",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray,
                textAlign = TextAlign.Center
            )
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry")
                }
                Button(
                    onClick = onOpenWith,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open with")
                }
            }
        }
    }
}

@Composable
private fun TopOverlayBar(
    node: StorageNode?,
    currentIndex: Int,
    totalCount: Int,
    dimensionsFormatted: String?,
    onBack: () -> Unit,
    onProperties: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Black.copy(alpha = 0.72f)
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Navigate back",
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = node?.name ?: "Image",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val details = buildString {
                    if (!dimensionsFormatted.isNullOrBlank()) {
                        append(dimensionsFormatted)
                    }
                    if (node != null && node.size > 0L) {
                        if (isNotEmpty()) append(" • ")
                        append(FileFormatter.formatFileSize(node.size))
                    }
                }

                if (details.isNotEmpty()) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (totalCount > 1) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "${currentIndex + 1} / $totalCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            IconButton(
                onClick = onProperties,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Image properties",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun BottomOverlayBar(
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Black.copy(alpha = 0.72f)
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = hasPrevious,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                    contentDescription = "Previous image",
                    tint = if (hasPrevious) Color.White else Color.Gray.copy(alpha = 0.4f),
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(
                onClick = onShare,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share image",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy image",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = onMove,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                    contentDescription = "Move image",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete image",
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = onNext,
                enabled = hasNext,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                    contentDescription = "Next image",
                    tint = if (hasNext) Color.White else Color.Gray.copy(alpha = 0.4f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
