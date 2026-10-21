package com.Hrshd1eux.filemanager.feature.viewer.image

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Hrshd1eux.filemanager.core.display.model.SortDirection
import com.Hrshd1eux.filemanager.core.display.model.SortField
import com.Hrshd1eux.filemanager.core.display.model.SortOrder
import com.Hrshd1eux.filemanager.core.operations.engine.FileOperationEngine
import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import com.Hrshd1eux.filemanager.core.search.model.CategoryFilter
import com.Hrshd1eux.filemanager.core.storage.model.MimeTypeResolver
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.util.FileTypeResolver
import com.Hrshd1eux.filemanager.feature.category.CategoryRepository
import com.Hrshd1eux.filemanager.feature.viewer.image.decoder.AndroidImageBitmapDecoder
import com.Hrshd1eux.filemanager.feature.viewer.image.decoder.ImageBitmapDecoder
import com.Hrshd1eux.filemanager.feature.viewer.image.model.ImageDimensions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ImageBitmapState {
    data object Loading : ImageBitmapState
    data class Success(val bitmap: Bitmap, val dimensions: ImageDimensions) : ImageBitmapState
    data class Error(val message: String, val dimensions: ImageDimensions? = null) : ImageBitmapState
}

sealed interface ImageViewerListState {
    data object Loading : ImageViewerListState
    data class Ready(val images: List<StorageNode>, val initialIndex: Int) : ImageViewerListState
    data object Empty : ImageViewerListState
}

class ImageViewerViewModel @JvmOverloads constructor(
    private val categoryRepository: CategoryRepository = CategoryRepository(),
    private val bitmapDecoder: ImageBitmapDecoder = AndroidImageBitmapDecoder(),
    private val operationEngine: FileOperationEngine = FileOperationEngine(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _listState = MutableStateFlow<ImageViewerListState>(ImageViewerListState.Loading)
    val listState: StateFlow<ImageViewerListState> = _listState.asStateFlow()

    private val _images = MutableStateFlow<List<StorageNode>>(emptyList())
    val images: StateFlow<List<StorageNode>> = _images.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _currentBitmapState = MutableStateFlow<ImageBitmapState>(ImageBitmapState.Loading)
    val currentBitmapState: StateFlow<ImageBitmapState> = _currentBitmapState.asStateFlow()

    private val _currentDimensions = MutableStateFlow<ImageDimensions?>(null)
    val currentDimensions: StateFlow<ImageDimensions?> = _currentDimensions.asStateFlow()

    // In-flight background file operation states (Copy / Move)
    private val _operationProgress = MutableStateFlow<OperationProgress?>(null)
    val operationProgress: StateFlow<OperationProgress?> = _operationProgress.asStateFlow()

    private val _isOperating = MutableStateFlow(false)
    val isOperating: StateFlow<Boolean> = _isOperating.asStateFlow()

    // Bounded in-memory bitmap cache for smooth adjacent paging (max 4 images)
    private val bitmapCache = object : LinkedHashMap<String, Pair<Bitmap, ImageDimensions>>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Bitmap, ImageDimensions>>?): Boolean {
            return size > 4
        }
    }

    private var loadBitmapJob: Job? = null
    private var isInitialized = false

    val currentImage: StorageNode?
        get() = _images.value.getOrNull(_currentIndex.value)

    val hasPrevious: Boolean
        get() = _currentIndex.value > 0

    val hasNext: Boolean
        get() = _currentIndex.value < (_images.value.size - 1)

    fun initialize(
        initialPath: String,
        categoryName: String? = null,
        directoryPath: String? = null
    ) {
        if (isInitialized) return
        isInitialized = true

        viewModelScope.launch(ioDispatcher) {
            _listState.value = ImageViewerListState.Loading

            val initialFile = File(initialPath)
            val foundImages = mutableListOf<StorageNode>()

            if (!categoryName.isNullOrBlank()) {
                val cat = CategoryFilter.entries.firstOrNull {
                    it.name.equals(categoryName, ignoreCase = true) || it.displayName.equals(categoryName, ignoreCase = true)
                } ?: CategoryFilter.IMAGES

                runCatching {
                    val catFiles = categoryRepository.getFilesForCategory(cat, rootPath = null)
                        .filter { FileTypeResolver.isImage(it.name) }
                    val sorted = categoryRepository.sortItems(
                        catFiles,
                        SortOrder(field = SortField.DATE, direction = SortDirection.DESCENDING, foldersFirst = false)
                    )
                    foundImages.addAll(sorted)
                }
            }

            if (foundImages.isEmpty()) {
                // If not in a category or category query was empty, look in parent folder of initial file
                val folder = directoryPath?.let { File(it) } ?: initialFile.parentFile
                if (folder != null && folder.exists() && folder.canRead()) {
                    val folderImages = folder.listFiles()
                        ?.filter { it.isFile && !it.name.startsWith(".") && FileTypeResolver.isImage(it.name) }
                        ?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                        ?.map { it.toStorageNode() }
                        ?: emptyList()
                    foundImages.addAll(folderImages)
                }
            }

            // Ensure initialPath is in the list
            val existingIndex = foundImages.indexOfFirst { it.path == initialFile.absolutePath }
            val targetIndex: Int

            if (existingIndex >= 0) {
                targetIndex = existingIndex
            } else {
                val initialNode = initialFile.toStorageNode()
                foundImages.add(0, initialNode)
                targetIndex = 0
            }

            if (foundImages.isEmpty()) {
                _listState.value = ImageViewerListState.Empty
                return@launch
            }

            _images.value = foundImages
            _currentIndex.value = targetIndex
            _listState.value = ImageViewerListState.Ready(images = foundImages, initialIndex = targetIndex)

            loadCurrentImageBitmap()
        }
    }

    fun selectIndex(newIndex: Int) {
        val imageList = _images.value
        if (newIndex !in imageList.indices || newIndex == _currentIndex.value) {
            return
        }
        _currentIndex.value = newIndex
        loadCurrentImageBitmap()
    }

    fun goToPrevious() {
        if (hasPrevious) {
            selectIndex(_currentIndex.value - 1)
        }
    }

    fun goToNext() {
        if (hasNext) {
            selectIndex(_currentIndex.value + 1)
        }
    }

    fun retryCurrent() {
        val node = currentImage ?: return
        bitmapCache.remove(node.path)
        loadCurrentImageBitmap()
    }

    private fun loadCurrentImageBitmap() {
        loadBitmapJob?.cancel()
        val node = currentImage ?: return

        // Fast path: cached in memory
        val cached = bitmapCache[node.path]
        if (cached != null) {
            _currentDimensions.value = cached.second
            _currentBitmapState.value = ImageBitmapState.Success(cached.first, cached.second)
            return
        }

        loadBitmapJob = viewModelScope.launch(ioDispatcher) {
            _currentBitmapState.value = ImageBitmapState.Loading
            _currentDimensions.value = null

            // Inspect dimensions first
            val dimensions = bitmapDecoder.inspectDimensions(node.path)
            _currentDimensions.value = dimensions

            if (dimensions == null) {
                _currentBitmapState.value = ImageBitmapState.Error(
                    message = "Corrupted or unsupported image file.",
                    dimensions = null
                )
                return@launch
            }

            val decodeResult = bitmapDecoder.decodeBitmap(node.path, maxDimension = 2048)
            decodeResult.fold(
                onSuccess = { bitmap ->
                    bitmapCache[node.path] = Pair(bitmap, dimensions)
                    _currentBitmapState.value = ImageBitmapState.Success(bitmap, dimensions)
                },
                onFailure = { error ->
                    _currentBitmapState.value = ImageBitmapState.Error(
                        message = error.localizedMessage ?: "Failed to decode image.",
                        dimensions = dimensions
                    )
                }
            )
        }
    }

    fun deleteCurrentImage(onAllDeleted: () -> Unit) {
        val node = currentImage ?: return
        viewModelScope.launch(ioDispatcher) {
            val result = categoryRepository.deleteFile(node)
            if (result.isSuccess) {
                bitmapCache.remove(node.path)
                val currentList = _images.value.toMutableList()
                val deletedIndex = _currentIndex.value
                currentList.removeAt(deletedIndex)

                if (currentList.isEmpty()) {
                    _images.value = emptyList()
                    _listState.value = ImageViewerListState.Empty
                    withContext(Dispatchers.Main) {
                        onAllDeleted()
                    }
                } else {
                    _images.value = currentList
                    val nextIndex = deletedIndex.coerceAtMost(currentList.size - 1)
                    _currentIndex.value = nextIndex
                    loadCurrentImageBitmap()
                }
            }
        }
    }

    fun copyCurrentImage(destinationDir: StorageNode, verifyIntegrity: Boolean) {
        val node = currentImage ?: return
        viewModelScope.launch(ioDispatcher) {
            _isOperating.value = true
            operationEngine.copy(
                sources = listOf(node),
                destinationDir = destinationDir,
                conflictResolution = ConflictResolution.RENAME_AUTO,
                verifyIntegrity = verifyIntegrity,
                onProgress = { _operationProgress.value = it }
            )
            _isOperating.value = false
            _operationProgress.value = null
        }
    }

    fun moveCurrentImage(
        destinationDir: StorageNode,
        verifyIntegrity: Boolean,
        onAllDeleted: () -> Unit
    ) {
        val node = currentImage ?: return
        viewModelScope.launch(ioDispatcher) {
            _isOperating.value = true
            val result = operationEngine.move(
                sources = listOf(node),
                destinationDir = destinationDir,
                conflictResolution = ConflictResolution.RENAME_AUTO,
                onProgress = { _operationProgress.value = it }
            )
            _isOperating.value = false
            _operationProgress.value = null

            if (result.isSuccess) {
                bitmapCache.remove(node.path)
                val currentList = _images.value.toMutableList()
                val movedIndex = _currentIndex.value
                currentList.removeAt(movedIndex)

                if (currentList.isEmpty()) {
                    _images.value = emptyList()
                    _listState.value = ImageViewerListState.Empty
                    withContext(Dispatchers.Main) {
                        onAllDeleted()
                    }
                } else {
                    _images.value = currentList
                    val nextIndex = movedIndex.coerceAtMost(currentList.size - 1)
                    _currentIndex.value = nextIndex
                    loadCurrentImageBitmap()
                }
            }
        }
    }

    private fun File.toStorageNode(): StorageNode {
        val mime = MimeTypeResolver.getMimeType(extension) ?: FileTypeResolver.resolveMimeType(name)
        return StorageNode(
            id = absolutePath,
            name = name.ifEmpty { absolutePath },
            path = absolutePath,
            isDirectory = isDirectory,
            size = length(),
            lastModified = lastModified(),
            mimeType = mime
        )
    }
}
