package com.Hrshd1eux.filemanager.feature.viewer.video

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
import com.Hrshd1eux.filemanager.feature.viewer.video.model.VideoMetadata
import com.Hrshd1eux.filemanager.feature.viewer.video.util.VideoMetadataExtractor
import com.Hrshd1eux.filemanager.feature.viewer.video.util.VideoTimeFormatter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface VideoPlaybackState {
    data object Idle : VideoPlaybackState
    data object Preparing : VideoPlaybackState
    data object Playing : VideoPlaybackState
    data object Paused : VideoPlaybackState
    data object Completed : VideoPlaybackState
    data class Error(val message: String, val isUnsupportedCodec: Boolean = false) : VideoPlaybackState
}

sealed interface VideoViewerListState {
    data object Loading : VideoViewerListState
    data class Ready(val videos: List<StorageNode>, val initialIndex: Int) : VideoViewerListState
    data object Empty : VideoViewerListState
}

class VideoViewerViewModel(
    private val categoryRepository: CategoryRepository = CategoryRepository(),
    private val operationEngine: FileOperationEngine = FileOperationEngine(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _listState = MutableStateFlow<VideoViewerListState>(VideoViewerListState.Loading)
    val listState: StateFlow<VideoViewerListState> = _listState.asStateFlow()

    private val _videos = MutableStateFlow<List<StorageNode>>(emptyList())
    val videos: StateFlow<List<StorageNode>> = _videos.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _playbackState = MutableStateFlow<VideoPlaybackState>(VideoPlaybackState.Idle)
    val playbackState: StateFlow<VideoPlaybackState> = _playbackState.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _metadata = MutableStateFlow<VideoMetadata?>(null)
    val metadata: StateFlow<VideoMetadata?> = _metadata.asStateFlow()

    private val _seekTargetMs = MutableStateFlow<Long?>(null)
    val seekTargetMs: StateFlow<Long?> = _seekTargetMs.asStateFlow()

    // File Operation states (Copy / Move)
    private val _operationProgress = MutableStateFlow<OperationProgress?>(null)
    val operationProgress: StateFlow<OperationProgress?> = _operationProgress.asStateFlow()

    private val _isOperating = MutableStateFlow(false)
    val isOperating: StateFlow<Boolean> = _isOperating.asStateFlow()

    private var isInitialized = false

    val currentVideo: StorageNode?
        get() = _videos.value.getOrNull(_currentIndex.value)

    val hasPrevious: Boolean
        get() = _currentIndex.value > 0

    val hasNext: Boolean
        get() = _currentIndex.value < (_videos.value.size - 1)

    fun initialize(
        initialPath: String,
        categoryName: String? = null,
        directoryPath: String? = null
    ) {
        if (isInitialized) return
        isInitialized = true

        viewModelScope.launch(ioDispatcher) {
            _listState.value = VideoViewerListState.Loading

            val initialFile = File(initialPath)
            val foundVideos = mutableListOf<StorageNode>()

            if (!categoryName.isNullOrBlank()) {
                val cat = CategoryFilter.entries.firstOrNull {
                    it.name.equals(categoryName, ignoreCase = true) || it.displayName.equals(categoryName, ignoreCase = true)
                } ?: CategoryFilter.VIDEOS

                runCatching {
                    val catFiles = categoryRepository.getFilesForCategory(cat, rootPath = null)
                        .filter { FileTypeResolver.isVideo(it.name) }
                    val sorted = categoryRepository.sortItems(
                        catFiles,
                        SortOrder(field = SortField.DATE, direction = SortDirection.DESCENDING, foldersFirst = false)
                    )
                    foundVideos.addAll(sorted)
                }
            }

            if (foundVideos.isEmpty()) {
                val folder = directoryPath?.let { File(it) } ?: initialFile.parentFile
                if (folder != null && folder.exists() && folder.canRead()) {
                    val folderVideos = folder.listFiles()
                        ?.filter { it.isFile && !it.name.startsWith(".") && FileTypeResolver.isVideo(it.name) }
                        ?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                        ?.map { it.toStorageNode() }
                        ?: emptyList()
                    foundVideos.addAll(folderVideos)
                }
            }

            // Ensure initialPath is included
            val existingIndex = foundVideos.indexOfFirst { it.path == initialFile.absolutePath }
            val targetIndex: Int

            if (existingIndex >= 0) {
                targetIndex = existingIndex
            } else {
                foundVideos.add(0, initialFile.toStorageNode())
                targetIndex = 0
            }

            if (foundVideos.isEmpty()) {
                _listState.value = VideoViewerListState.Empty
                return@launch
            }

            _videos.value = foundVideos
            _currentIndex.value = targetIndex
            _listState.value = VideoViewerListState.Ready(videos = foundVideos, initialIndex = targetIndex)

            loadCurrentVideoMetadata()
        }
    }

    fun selectIndex(newIndex: Int) {
        val videoList = _videos.value
        if (newIndex !in videoList.indices || newIndex == _currentIndex.value) {
            return
        }
        stopPlayback()
        _currentIndex.value = newIndex
        loadCurrentVideoMetadata()
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

    fun onPrepared(duration: Long) {
        _durationMs.value = duration
        _playbackState.value = VideoPlaybackState.Playing
    }

    fun onPlay() {
        if (_playbackState.value is VideoPlaybackState.Paused || _playbackState.value is VideoPlaybackState.Completed) {
            _playbackState.value = VideoPlaybackState.Playing
        }
    }

    fun onPause() {
        if (_playbackState.value is VideoPlaybackState.Playing) {
            _playbackState.value = VideoPlaybackState.Paused
        }
    }

    fun togglePlayPause() {
        when (_playbackState.value) {
            is VideoPlaybackState.Playing -> onPause()
            is VideoPlaybackState.Paused, is VideoPlaybackState.Completed -> onPlay()
            else -> Unit
        }
    }

    fun onCompleted() {
        _playbackState.value = VideoPlaybackState.Completed
        _currentPositionMs.value = _durationMs.value
    }

    fun onError(what: Int, extra: Int) {
        val isUnsupported = (extra == -1010 || extra == -1004 || extra == -1007 || what == -1010)
        val message = when (extra) {
            -1010 -> "Unsupported video codec or container format."
            -1007 -> "Malformed bitstream or damaged media file."
            -1004 -> "I/O error reading video data from storage."
            -110 -> "Connection or decoder operation timed out."
            else -> if (what == 100) "Media server terminated." else "Device decoders could not play this video (Error $what, $extra)."
        }
        _playbackState.value = VideoPlaybackState.Error(message = message, isUnsupportedCodec = isUnsupported)
    }

    fun onSeekStart(targetMs: Long) {
        _seekTargetMs.value = targetMs
    }

    fun onSeekEnd(targetMs: Long) {
        val clamped = VideoTimeFormatter.clampPosition(targetMs, _durationMs.value)
        _currentPositionMs.value = clamped
        _seekTargetMs.value = clamped
    }

    fun clearSeekTarget() {
        _seekTargetMs.value = null
    }

    fun seekRelative(deltaMs: Long) {
        val current = _currentPositionMs.value
        val target = VideoTimeFormatter.clampPosition(current + deltaMs, _durationMs.value)
        onSeekEnd(target)
    }

    fun updateCurrentPosition(positionMs: Long) {
        if (_seekTargetMs.value == null) {
            _currentPositionMs.value = positionMs
        }
    }

    fun onLifecyclePause() {
        // Guarantee no background playback or battery drain when screen is backgrounded
        onPause()
    }

    fun onAudioFocusLost() {
        onPause()
    }

    fun retryCurrent() {
        val node = currentVideo ?: return
        stopPlayback()
        loadCurrentVideoMetadata()
    }

    private fun loadCurrentVideoMetadata() {
        val node = currentVideo ?: return
        _playbackState.value = VideoPlaybackState.Preparing
        _currentPositionMs.value = 0L
        _durationMs.value = 0L

        viewModelScope.launch(ioDispatcher) {
            val meta = VideoMetadataExtractor.extractMetadata(node.path)
            _metadata.value = meta
            if (meta != null && meta.durationMs > 0L) {
                _durationMs.value = meta.durationMs
            }
        }
    }

    private fun stopPlayback() {
        _playbackState.value = VideoPlaybackState.Idle
        _currentPositionMs.value = 0L
        _seekTargetMs.value = null
    }

    fun deleteCurrentVideo(onAllDeleted: () -> Unit) {
        val node = currentVideo ?: return
        stopPlayback()
        viewModelScope.launch(ioDispatcher) {
            val result = categoryRepository.deleteFile(node)
            if (result.isSuccess) {
                val currentList = _videos.value.toMutableList()
                val deletedIndex = _currentIndex.value
                currentList.removeAt(deletedIndex)

                if (currentList.isEmpty()) {
                    _videos.value = emptyList()
                    _listState.value = VideoViewerListState.Empty
                    withContext(Dispatchers.Main) {
                        onAllDeleted()
                    }
                } else {
                    _videos.value = currentList
                    val nextIndex = deletedIndex.coerceAtMost(currentList.size - 1)
                    _currentIndex.value = nextIndex
                    loadCurrentVideoMetadata()
                }
            }
        }
    }

    fun copyCurrentVideo(destinationDir: StorageNode, verifyIntegrity: Boolean) {
        val node = currentVideo ?: return
        onPause()
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

    fun moveCurrentVideo(
        destinationDir: StorageNode,
        verifyIntegrity: Boolean,
        onAllDeleted: () -> Unit
    ) {
        val node = currentVideo ?: return
        stopPlayback()
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
                val currentList = _videos.value.toMutableList()
                val movedIndex = _currentIndex.value
                currentList.removeAt(movedIndex)

                if (currentList.isEmpty()) {
                    _videos.value = emptyList()
                    _listState.value = VideoViewerListState.Empty
                    withContext(Dispatchers.Main) {
                        onAllDeleted()
                    }
                } else {
                    _videos.value = currentList
                    val nextIndex = movedIndex.coerceAtMost(currentList.size - 1)
                    _currentIndex.value = nextIndex
                    loadCurrentVideoMetadata()
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

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
    }
}
