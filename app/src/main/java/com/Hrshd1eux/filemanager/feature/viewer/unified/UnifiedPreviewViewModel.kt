package com.Hrshd1eux.filemanager.feature.viewer.unified

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Hrshd1eux.filemanager.core.display.model.SortDirection
import com.Hrshd1eux.filemanager.core.display.model.SortField
import com.Hrshd1eux.filemanager.core.display.model.SortOrder
import com.Hrshd1eux.filemanager.core.operations.engine.FileOperationEngine
import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import com.Hrshd1eux.filemanager.feature.category.CategoryRepository
import com.Hrshd1eux.filemanager.core.search.model.CategoryFilter
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import com.Hrshd1eux.filemanager.core.storage.util.FileTypeResolver
import com.Hrshd1eux.filemanager.feature.viewer.audio.util.AudioMetadataExtractor
import com.Hrshd1eux.filemanager.feature.viewer.image.decoder.AndroidImageBitmapDecoder
import com.Hrshd1eux.filemanager.feature.viewer.image.decoder.ImageBitmapDecoder
import com.Hrshd1eux.filemanager.feature.viewer.unified.loader.PdfPreviewRenderer
import com.Hrshd1eux.filemanager.feature.viewer.unified.loader.TextPreviewLoader
import com.Hrshd1eux.filemanager.feature.viewer.unified.model.PreviewState
import com.Hrshd1eux.filemanager.feature.viewer.unified.model.PreviewType
import com.Hrshd1eux.filemanager.feature.viewer.unified.model.PreviewTypeResolver
import com.Hrshd1eux.filemanager.feature.viewer.video.util.VideoMetadataExtractor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Single, unified ViewModel driving preview screens across images, video, audio, text, and documents.
 */
class UnifiedPreviewViewModel(
    private val categoryRepository: CategoryRepository = CategoryRepository(),
    private val operationEngine: FileOperationEngine = FileOperationEngine(),
    private val bitmapDecoder: ImageBitmapDecoder = AndroidImageBitmapDecoder(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _files = MutableStateFlow<List<StorageNode>>(emptyList())
    val files: StateFlow<List<StorageNode>> = _files.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _previewType = MutableStateFlow(PreviewType.UNSUPPORTED)
    val previewType: StateFlow<PreviewType> = _previewType.asStateFlow()

    private val _previewState = MutableStateFlow<PreviewState>(PreviewState.Loading(File("")))
    val previewState: StateFlow<PreviewState> = _previewState.asStateFlow()

    // Text Viewer settings
    private val _showLineNumbers = MutableStateFlow(true)
    val showLineNumbers: StateFlow<Boolean> = _showLineNumbers.asStateFlow()

    private val _wordWrap = MutableStateFlow(true)
    val wordWrap: StateFlow<Boolean> = _wordWrap.asStateFlow()

    private val _isMonospace = MutableStateFlow(true)
    val isMonospace: StateFlow<Boolean> = _isMonospace.asStateFlow()

    // PDF active renderer
    private var activePdfRenderer: PdfPreviewRenderer? = null
    private val _pdfCurrentPage = MutableStateFlow(0)
    val pdfCurrentPage: StateFlow<Int> = _pdfCurrentPage.asStateFlow()

    private val _pdfTotalPages = MutableStateFlow(0)
    val pdfTotalPages: StateFlow<Int> = _pdfTotalPages.asStateFlow()

    // In-flight Operation State
    private val _operationProgress = MutableStateFlow<OperationProgress?>(null)
    val operationProgress: StateFlow<OperationProgress?> = _operationProgress.asStateFlow()

    private val _isOperating = MutableStateFlow(false)
    val isOperating: StateFlow<Boolean> = _isOperating.asStateFlow()

    private var isInitialized = false

    val currentFile: StorageNode?
        get() = _files.value.getOrNull(_currentIndex.value)

    val hasPrevious: Boolean
        get() = _currentIndex.value > 0

    val hasNext: Boolean
        get() = _currentIndex.value < (_files.value.size - 1)

    fun initialize(
        initialPath: String,
        categoryName: String? = null,
        directoryPath: String? = null
    ) {
        if (isInitialized) return
        isInitialized = true

        viewModelScope.launch(ioDispatcher) {
            val initialFile = File(initialPath)
            _previewState.value = PreviewState.Loading(initialFile)

            val discoveredFiles = mutableListOf<StorageNode>()

            if (!categoryName.isNullOrBlank()) {
                val cat = CategoryFilter.entries.firstOrNull {
                    it.name.equals(categoryName, ignoreCase = true) || it.displayName.equals(categoryName, ignoreCase = true)
                }
                if (cat != null) {
                    runCatching {
                        val catFiles = categoryRepository.getFilesForCategory(cat, rootPath = null)
                        val sorted = categoryRepository.sortItems(
                            catFiles,
                            SortOrder(field = SortField.NAME, direction = SortDirection.ASCENDING, foldersFirst = false)
                        )
                        discoveredFiles.addAll(sorted)
                    }
                }
            }

            if (discoveredFiles.isEmpty()) {
                val folder = directoryPath?.let { File(it) } ?: initialFile.parentFile
                if (folder != null && folder.exists() && folder.canRead()) {
                    val siblings = folder.listFiles()
                        ?.filter { it.isFile && !it.name.startsWith(".") }
                        ?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                        ?.map { it.toStorageNode() }
                    if (!siblings.isNullOrEmpty()) {
                        discoveredFiles.addAll(siblings)
                    }
                }
            }

            if (discoveredFiles.none { it.path == initialPath } && initialFile.exists()) {
                discoveredFiles.add(0, initialFile.toStorageNode())
            }

            _files.value = discoveredFiles
            val matchedIndex = discoveredFiles.indexOfFirst { it.path == initialPath }.coerceAtLeast(0)
            _currentIndex.value = matchedIndex

            loadPreviewForCurrentIndex()
        }
    }

    fun navigateNext() {
        if (hasNext) {
            _currentIndex.value += 1
            loadPreviewForCurrentIndex()
        }
    }

    fun navigatePrevious() {
        if (hasPrevious) {
            _currentIndex.value -= 1
            loadPreviewForCurrentIndex()
        }
    }

    fun navigateToIndex(index: Int) {
        if (index in _files.value.indices && index != _currentIndex.value) {
            _currentIndex.value = index
            loadPreviewForCurrentIndex()
        }
    }

    fun toggleLineNumbers() {
        _showLineNumbers.value = !_showLineNumbers.value
    }

    fun toggleWordWrap() {
        _wordWrap.value = !_wordWrap.value
    }

    fun toggleMonospace() {
        _isMonospace.value = !_isMonospace.value
    }

    fun goToPdfPage(pageIndex: Int) {
        val renderer = activePdfRenderer ?: return
        val current = _previewState.value as? PreviewState.PdfReady ?: return
        if (pageIndex in 0 until current.pageCount) {
            viewModelScope.launch(ioDispatcher) {
                renderer.renderPage(pageIndex).onSuccess { bitmap ->
                    _pdfCurrentPage.value = pageIndex
                    _previewState.value = current.copy(
                        currentPageIndex = pageIndex,
                        currentPageBitmap = bitmap
                    )
                }
            }
        }
    }

    fun nextPdfPage() {
        goToPdfPage(_pdfCurrentPage.value + 1)
    }

    fun previousPdfPage() {
        goToPdfPage(_pdfCurrentPage.value - 1)
    }

    fun retry() {
        loadPreviewForCurrentIndex()
    }

    fun deleteCurrent(onDismissAfterDelete: () -> Unit) {
        val node = currentFile ?: return
        viewModelScope.launch(ioDispatcher) {
            val file = File(node.path)
            runCatching { file.delete() }

            val remaining = _files.value.toMutableList().apply { removeAt(_currentIndex.value) }
            _files.value = remaining

            if (remaining.isEmpty()) {
                onDismissAfterDelete()
            } else {
                if (_currentIndex.value >= remaining.size) {
                    _currentIndex.value = remaining.size - 1
                }
                loadPreviewForCurrentIndex()
            }
        }
    }

    fun onFileMoved(destinationPath: String, onDismissAfterMove: () -> Unit) {
        val remaining = _files.value.toMutableList().apply { removeAt(_currentIndex.value) }
        _files.value = remaining

        if (remaining.isEmpty()) {
            onDismissAfterMove()
        } else {
            if (_currentIndex.value >= remaining.size) {
                _currentIndex.value = remaining.size - 1
            }
            loadPreviewForCurrentIndex()
        }
    }

    private fun loadPreviewForCurrentIndex() {
        viewModelScope.launch(ioDispatcher) {
            // Clean up existing PDF renderer
            activePdfRenderer?.close()
            activePdfRenderer = null

            val node = currentFile
            if (node == null) {
                _previewState.value = PreviewState.Error(File(""), "File not found")
                return@launch
            }

            val file = File(node.path)
            _previewState.value = PreviewState.Loading(file)

            if (!file.exists() || !file.canRead()) {
                _previewState.value = PreviewState.Error(file, "File does not exist or cannot be read: ${file.name}")
                return@launch
            }

            val type = PreviewTypeResolver.resolve(node.name, node.mimeType)
            _previewType.value = type

            when (type) {
                PreviewType.IMAGE -> {
                    val dimensions = bitmapDecoder.inspectDimensions(file.path)
                    val dimStr = if (dimensions != null) "${dimensions.width} × ${dimensions.height}" else ""
                    _previewState.value = PreviewState.ImageReady(file, dimStr, file.length())
                }

                PreviewType.VIDEO -> {
                    val meta = VideoMetadataExtractor.extractMetadata(file.path, ioDispatcher)
                    _previewState.value = PreviewState.VideoReady(file, meta)
                }

                PreviewType.AUDIO -> {
                    val extraction = AudioMetadataExtractor.extract(file.path)
                    _previewState.value = PreviewState.AudioReady(file, extraction.metadata)
                }

                PreviewType.TEXT -> {
                    val result = TextPreviewLoader.load(file, ioDispatcher)
                    result.onSuccess { textRes ->
                        // Automatically set monospace according to extension
                        val isCode = node.name.substringAfterLast('.', "").lowercase() in setOf(
                            "kt", "java", "py", "c", "cpp", "h", "cs", "js", "ts", "json", "xml", "yaml", "yml", "sql", "sh", "bat", "log"
                        )
                        _isMonospace.value = isCode
                        _previewState.value = PreviewState.TextReady(
                            file = file,
                            lines = textRes.lines,
                            isTruncated = textRes.isTruncated,
                            totalLines = textRes.totalLinesCount,
                            totalBytes = textRes.totalSizeBytes
                        )
                    }.onFailure { err ->
                        _previewState.value = PreviewState.Error(file, "Failed to read text: ${err.localizedMessage ?: "Unknown error"}")
                    }
                }

                PreviewType.PDF -> {
                    val pdfRenderer = PdfPreviewRenderer(file)
                    activePdfRenderer = pdfRenderer
                    val openResult = pdfRenderer.open()
                    openResult.onSuccess { count ->
                        _pdfTotalPages.value = count
                        _pdfCurrentPage.value = 0
                        pdfRenderer.renderPage(0).onSuccess { bitmap ->
                            _previewState.value = PreviewState.PdfReady(
                                file = file,
                                pageCount = count,
                                currentPageIndex = 0,
                                currentPageBitmap = bitmap
                            )
                        }.onFailure { err ->
                            _previewState.value = PreviewState.Error(file, "Failed to render PDF page: ${err.localizedMessage ?: "Unknown error"}")
                        }
                    }.onFailure { err ->
                        _previewState.value = PreviewState.Unsupported(file, "application/pdf", "Unable to open PDF: ${err.localizedMessage ?: "Invalid or damaged document"}")
                    }
                }

                PreviewType.UNSUPPORTED -> {
                    val mime = node.mimeType ?: FileTypeResolver.resolveMimeType(file.name)
                    _previewState.value = PreviewState.Unsupported(
                        file = file,
                        mimeType = mime,
                        reason = "No in-app preview available for .${file.extension.uppercase()} files."
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        activePdfRenderer?.close()
        activePdfRenderer = null
    }

    private fun File.toStorageNode(): StorageNode {
        return StorageNode(
            id = absolutePath,
            name = name,
            path = absolutePath,
            size = length(),
            lastModified = lastModified(),
            isDirectory = isDirectory,
            mimeType = FileTypeResolver.resolveMimeType(name)
        )
    }
}
