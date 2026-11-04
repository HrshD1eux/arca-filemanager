package com.Hrshd1eux.filemanager.core.thumbnail

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.util.FileTypeResolver

@Composable
fun FileThumbnailView(
    node: StorageNode,
    categoryColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    shape: RoundedCornerShape = RoundedCornerShape(10.dp)
) {
    val context = LocalContext.current
    var thumbnailBitmap by remember(node.path, node.lastModified) { mutableStateOf<Bitmap?>(null) }
    val isSupportedType = remember(node.name) {
        val name = node.name
        FileTypeResolver.isImage(name) ||
                FileTypeResolver.isVideo(name) ||
                FileTypeResolver.isPackage(name) ||
                FileTypeResolver.isAudio(name) ||
                name.endsWith(".pdf", ignoreCase = true)
    }

    LaunchedEffect(node.path, node.lastModified) {
        if (isSupportedType) {
            thumbnailBitmap = ThumbnailLoader.loadThumbnail(context, node.path, targetSize = 160)
        }
    }

    val currentBitmap = thumbnailBitmap
    if (currentBitmap != null && !currentBitmap.isRecycled) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(Color.Black.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = node.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        // Fallback Category Vector Icon
        Box(
            modifier = modifier
                .clip(shape)
                .background(categoryColor.copy(alpha = 0.14f))
                .border(0.5.dp, categoryColor.copy(alpha = 0.28f), shape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = getFileFallbackIcon(node.name),
                contentDescription = null,
                tint = categoryColor,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

private fun getFileFallbackIcon(fileName: String): ImageVector {
    return when {
        FileTypeResolver.isImage(fileName) -> Icons.Default.Image
        FileTypeResolver.isVideo(fileName) -> Icons.Default.VideoLibrary
        FileTypeResolver.isAudio(fileName) -> Icons.Default.Audiotrack
        FileTypeResolver.isDocument(fileName) -> Icons.Default.Description
        FileTypeResolver.isArchive(fileName) -> Icons.Default.FolderZip
        FileTypeResolver.isPackage(fileName) -> Icons.Default.Android
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}
