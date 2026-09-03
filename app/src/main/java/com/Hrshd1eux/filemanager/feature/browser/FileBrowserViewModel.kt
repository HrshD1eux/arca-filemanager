package com.Hrshd1eux.filemanager.feature.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Hrshd1eux.filemanager.core.archive.engine.ZipEngine
import com.Hrshd1eux.filemanager.core.display.model.SortOrder
import com.Hrshd1eux.filemanager.core.display.model.ViewMode
import com.Hrshd1eux.filemanager.core.display.preferences.DisplayPreferencesRepository
import com.Hrshd1eux.filemanager.core.display.util.NodeSorter
import com.Hrshd1eux.filemanager.core.favorites.model.FavoriteItem
import com.Hrshd1eux.filemanager.core.favorites.repository.FavoritesRepository
import com.Hrshd1eux.filemanager.core.operations.engine.FileOperationEngine
import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import com.Hrshd1eux.filemanager.core.operations.model.OperationResult
import com.Hrshd1eux.filemanager.core.recents.repository.RecentsRepository
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.provider.LocalFileSystemProvider
import com.Hrshd1eux.filemanager.core.storage.provider.StorageProvider
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import com.Hrshd1eux.filemanager.feature.browser.model.BreadcrumbItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel managing directory traversal, breadcrumb hierarchy, and browser state.
 */
class FileBrowserViewModel(
    val storageProvider: StorageProvider = LocalFileSystemProvider(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val operationEngine: FileOperationEngine = FileOperationEngine(ioDispatcher),
    private val zipEngine: ZipEngine = ZipEngine(ioDispatcher),
    private val displayPreferencesRepository: DisplayPreferencesRepository = DisplayPreferencesRepository(),
    private val favoritesRepository: FavoritesRepository = FavoritesRepository(),
    private val recentsRepository: RecentsRepository = RecentsRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<BrowserUiState>(BrowserUiState.Loading)
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val _activeProgress = MutableStateFlow<OperationProgress?>(null)
    val activeProgress: StateFlow<OperationProgress?> = _activeProgress.asStateFlow()

    private val _selectedNodeIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedNodeIds: StateFlow<Set<String>> = _selectedNodeIds.asStateFlow()

    val sortOrder: StateFlow<SortOrder> = displayPreferencesRepository.sortOrder
    val viewMode: StateFlow<ViewMode> = displayPreferencesRepository.viewMode
    val showHiddenFiles: StateFlow<Boolean> = displayPreferencesRepository.showHiddenFiles
    val favorites: StateFlow<List<FavoriteItem>> = favoritesRepository.favorites

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    private var activeJob: Job? = null
    private val rootNode: StorageNode = storageProvider.getRootNode()

    fun toggleShowHiddenFiles(hasPermission: Boolean) {
        val current = displayPreferencesRepository.showHiddenFiles.value
        displayPreferencesRepository.updateShowHiddenFiles(!current)
        refresh(hasPermission)
    }

    fun isFavorite(path: String): Boolean {
        return favoritesRepository.isFavorite(path)
    }

    fun toggleFavorite(node: StorageNode) {
        val wasAdded = favoritesRepository.toggleFavorite(node)
        viewModelScope.launch {
            if (wasAdded) {
                _userMessage.emit("Added to Favorites")
            } else {
                _userMessage.emit("Removed from Favorites")
            }
        }
    }

    fun toggleCurrentFolderFavorite() {
        val currentState = _uiState.value
        if (currentState is BrowserUiState.Success) {
            val currentNode = StorageNode(
                id = currentState.currentPath,
                name = currentState.currentTitle,
                path = currentState.currentPath,
                isDirectory = true,
                size = 0L,
                lastModified = System.currentTimeMillis()
            )
            toggleFavorite(currentNode)
        }
    }

    fun setSortOrder(newOrder: SortOrder, hasPermission: Boolean) {
        displayPreferencesRepository.updateSortOrder(newOrder)
        val currentState = _uiState.value
        if (currentState is BrowserUiState.Success) {
            val sorted = NodeSorter.sort(currentState.items, newOrder)
            _uiState.value = currentState.copy(items = sorted)
        } else {
            refresh(hasPermission)
        }
    }

    fun setViewMode(newMode: ViewMode) {
        displayPreferencesRepository.updateViewMode(newMode)
    }

    fun cycleViewMode() {
        val nextMode = when (viewMode.value) {
            ViewMode.LIST -> ViewMode.COMPACT
            ViewMode.COMPACT -> ViewMode.GRID
            ViewMode.GRID -> ViewMode.LIST
        }
        setViewMode(nextMode)
    }

    fun toggleSelection(nodeId: String) {
        val current = _selectedNodeIds.value
        _selectedNodeIds.value = if (current.contains(nodeId)) {
            current - nodeId
        } else {
            current + nodeId
        }
    }

    fun selectAll(items: List<StorageNode>) {
        val allIds = items.map { it.id }.toSet()
        _selectedNodeIds.value = if (_selectedNodeIds.value.size == allIds.size) {
            emptySet()
        } else {
            allIds
        }
    }

    fun clearSelection() {
        _selectedNodeIds.value = emptySet()
    }

    fun initialize(initialPath: String?, hasPermission: Boolean) {
        val targetPath = initialPath ?: rootNode.path
        loadDirectory(targetPath, hasPermission)
    }

    fun startCopy(
        sources: List<StorageNode>,
        destination: StorageNode,
        conflictResolution: ConflictResolution = ConflictResolution.RENAME_AUTO,
        hasPermission: Boolean
    ) {
        if (!hasPermission) {
            _uiState.value = BrowserUiState.PermissionRequired()
            return
        }

        clearSelection()
        activeJob = viewModelScope.launch {
            _activeProgress.value = OperationProgress(totalFiles = sources.size)

            val result = operationEngine.copy(
                sources = sources,
                destinationDir = destination,
                conflictResolution = conflictResolution,
                onProgress = { progress ->
                    _activeProgress.value = progress
                }
            )

            _activeProgress.value = null

            result.onSuccess { opResult ->
                when (opResult) {
                    is OperationResult.Success -> {
                        _userMessage.emit("Copied ${opResult.filesProcessed} items (${FileFormatter.formatFileSize(opResult.bytesProcessed)})")
                        refresh(hasPermission)
                    }
                    is OperationResult.Cancelled -> {
                        _userMessage.emit("Copy cancelled.")
                        refresh(hasPermission)
                    }
                    is OperationResult.Failure -> {
                        _userMessage.emit("Copy failed: ${opResult.error.message}")
                    }
                }
            }.onFailure { error ->
                _userMessage.emit("Copy error: ${error.localizedMessage}")
            }
        }
    }

    fun startMove(
        sources: List<StorageNode>,
        destination: StorageNode,
        conflictResolution: ConflictResolution = ConflictResolution.RENAME_AUTO,
        hasPermission: Boolean
    ) {
        if (!hasPermission) {
            _uiState.value = BrowserUiState.PermissionRequired()
            return
        }

        clearSelection()
        activeJob = viewModelScope.launch {
            _activeProgress.value = OperationProgress(totalFiles = sources.size)

            val result = operationEngine.move(
                sources = sources,
                destinationDir = destination,
                conflictResolution = conflictResolution,
                onProgress = { progress ->
                    _activeProgress.value = progress
                }
            )

            _activeProgress.value = null

            result.onSuccess { opResult ->
                when (opResult) {
                    is OperationResult.Success -> {
                        _userMessage.emit("Moved ${opResult.filesProcessed} items (${FileFormatter.formatFileSize(opResult.bytesProcessed)})")
                        refresh(hasPermission)
                    }
                    is OperationResult.Cancelled -> {
                        _userMessage.emit("Move cancelled.")
                        refresh(hasPermission)
                    }
                    is OperationResult.Failure -> {
                        _userMessage.emit("Move failed: ${opResult.error.message}")
                    }
                }
            }.onFailure { error ->
                _userMessage.emit("Move error: ${error.localizedMessage}")
            }
        }
    }

    fun startDelete(
        targets: List<StorageNode>,
        hasPermission: Boolean
    ) {
        if (!hasPermission) {
            _uiState.value = BrowserUiState.PermissionRequired()
            return
        }

        clearSelection()
        activeJob = viewModelScope.launch {
            _activeProgress.value = OperationProgress(totalFiles = targets.size)

            val result = operationEngine.delete(
                targets = targets,
                onProgress = { progress ->
                    _activeProgress.value = progress
                }
            )

            _activeProgress.value = null

            result.onSuccess { opResult ->
                when (opResult) {
                    is OperationResult.Success -> {
                        _userMessage.emit("Deleted ${opResult.filesProcessed} items.")
                        refresh(hasPermission)
                    }
                    is OperationResult.Cancelled -> {
                        _userMessage.emit("Delete cancelled.")
                        refresh(hasPermission)
                    }
                    is OperationResult.Failure -> {
                        _userMessage.emit("Delete failed: ${opResult.error.message}")
                    }
                }
            }.onFailure { error ->
                _userMessage.emit("Delete error: ${error.localizedMessage}")
            }
        }
    }

    fun rename(
        target: StorageNode,
        newName: String,
        hasPermission: Boolean
    ) {
        if (!hasPermission) {
            _uiState.value = BrowserUiState.PermissionRequired()
            return
        }

        clearSelection()
        viewModelScope.launch {
            val result = operationEngine.rename(target = target, newName = newName)
            result.onSuccess { renamedNode ->
                _userMessage.emit("Renamed to '${renamedNode.name}'")
                refresh(hasPermission)
            }.onFailure { error ->
                _userMessage.emit("Rename failed: ${error.localizedMessage}")
            }
        }
    }

    fun createDirectory(
        parentPath: String,
        dirName: String,
        hasPermission: Boolean
    ) {
        if (!hasPermission) {
            _uiState.value = BrowserUiState.PermissionRequired()
            return
        }

        clearSelection()
        viewModelScope.launch {
            val parentNode = StorageNode(
                id = parentPath,
                name = File(parentPath).name,
                path = parentPath,
                isDirectory = true
            )
            val result = operationEngine.createDirectory(parentDir = parentNode, dirName = dirName)
            result.onSuccess { newDir ->
                _userMessage.emit("Created folder '${newDir.name}'")
                refresh(hasPermission)
            }.onFailure { error ->
                _userMessage.emit("Failed to create folder: ${error.localizedMessage}")
            }
        }
    }

    fun compressFiles(
        sources: List<StorageNode>,
        archiveName: String,
        hasPermission: Boolean
    ) {
        val currentState = _uiState.value as? BrowserUiState.Success ?: return
        val destDir = File(currentState.currentPath)
        val destZip = File(destDir, archiveName)

        clearSelection()
        activeJob = viewModelScope.launch {
            _activeProgress.value = OperationProgress(totalFiles = sources.size)
            val sourceFiles = sources.map { File(it.path) }
            val result = zipEngine.createZip(
                sources = sourceFiles,
                destinationZip = destZip,
                onProgress = { _activeProgress.value = it }
            )
            _activeProgress.value = null
            activeJob = null
            result.onSuccess { size ->
                _userMessage.emit("Created $archiveName (${FileFormatter.formatFileSize(size)})")
                refresh(hasPermission)
            }.onFailure { error ->
                if (error !is kotlinx.coroutines.CancellationException) {
                    _userMessage.emit("Compression failed: ${error.localizedMessage}")
                }
            }
        }
    }

    fun extractArchive(
        archiveNode: StorageNode,
        destinationDir: File,
        conflictResolution: ConflictResolution = ConflictResolution.RENAME_AUTO,
        hasPermission: Boolean
    ) {
        if (!hasPermission) {
            _uiState.value = BrowserUiState.PermissionRequired()
            return
        }

        clearSelection()
        activeJob = viewModelScope.launch {
            val archiveFile = File(archiveNode.path)
            _activeProgress.value = OperationProgress(totalFiles = 1)
            val result = zipEngine.extractAll(
                zipFile = archiveFile,
                destinationDir = destinationDir,
                conflictResolution = conflictResolution,
                onProgress = { _activeProgress.value = it }
            )
            _activeProgress.value = null
            activeJob = null
            result.onSuccess { count ->
                _userMessage.emit("Extracted $count items to ${destinationDir.name}")
                refresh(hasPermission)
            }.onFailure { error ->
                if (error !is kotlinx.coroutines.CancellationException) {
                    _userMessage.emit("Extraction failed: ${error.localizedMessage}")
                }
            }
        }
    }

    fun cancelActiveOperation() {
        activeJob?.cancel()
        activeJob = null
        _activeProgress.value = null
    }

    fun loadDirectory(path: String, hasPermission: Boolean) {
        if (!hasPermission) {
            _uiState.value = BrowserUiState.PermissionRequired()
            return
        }

        clearSelection()
        viewModelScope.launch {
            _uiState.value = BrowserUiState.Loading

            val result = storageProvider.listChildren(path)
            result.onSuccess { children ->
                val filtered = if (displayPreferencesRepository.showHiddenFiles.value) {
                    children
                } else {
                    children.filter { !it.name.startsWith(".") }
                }
                val sorted = NodeSorter.sort(filtered, displayPreferencesRepository.sortOrder.value)

                val breadcrumbs = buildBreadcrumbs(
                    currentPath = path,
                    rootPath = rootNode.path,
                    rootDisplayName = storageProvider.displayName
                )

                val stats = storageProvider.getStorageStats().getOrNull()
                val currentTitle = if (path == rootNode.path) {
                    storageProvider.displayName
                } else {
                    File(path).name.ifEmpty { path }
                }

                _uiState.value = BrowserUiState.Success(
                    currentPath = path,
                    currentTitle = currentTitle,
                    breadcrumbs = breadcrumbs,
                    items = sorted,
                    stats = stats
                )

                recentsRepository.recordAccess(
                    StorageNode(
                        id = path,
                        name = currentTitle,
                        path = path,
                        isDirectory = true,
                        size = 0L,
                        lastModified = System.currentTimeMillis()
                    )
                )
            }.onFailure { error ->
                _uiState.value = BrowserUiState.Error(
                    message = error.localizedMessage ?: "Failed to read directory."
                )
            }
        }
    }

    fun recordFileAccess(node: StorageNode) {
        recentsRepository.recordAccess(node)
    }

    fun navigateInto(node: StorageNode, hasPermission: Boolean) {
        if (node.isDirectory) {
            loadDirectory(node.path, hasPermission)
        }
    }

    fun navigateUp(hasPermission: Boolean): Boolean {
        val currentState = _uiState.value
        if (currentState is BrowserUiState.Success) {
            if (currentState.currentPath == rootNode.path) {
                return false // At root, allow back navigation to exit browser
            }
            val parentFile = File(currentState.currentPath).parentFile
            if (parentFile != null && parentFile.exists()) {
                loadDirectory(parentFile.absolutePath, hasPermission)
                return true
            }
        }
        return false
    }

    fun navigateToBreadcrumb(item: BreadcrumbItem, hasPermission: Boolean) {
        loadDirectory(item.path, hasPermission)
    }

    fun refresh(hasPermission: Boolean) {
        val currentState = _uiState.value
        val path = if (currentState is BrowserUiState.Success) {
            currentState.currentPath
        } else {
            rootNode.path
        }
        loadDirectory(path, hasPermission)
    }

    companion object {
        fun buildBreadcrumbs(
            currentPath: String,
            rootPath: String,
            rootDisplayName: String
        ): List<BreadcrumbItem> {
            val normalizedCurrent = File(currentPath).absolutePath
            val normalizedRoot = File(rootPath).absolutePath

            val items = mutableListOf<BreadcrumbItem>()

            // Always add Root breadcrumb
            items.add(
                BreadcrumbItem(
                    name = rootDisplayName,
                    path = normalizedRoot,
                    isCurrent = normalizedCurrent == normalizedRoot
                )
            )

            if (normalizedCurrent != normalizedRoot && normalizedCurrent.startsWith(normalizedRoot)) {
                val relative = normalizedCurrent
                    .removePrefix(normalizedRoot)
                    .trimStart(File.separatorChar, '/', '\\')

                if (relative.isNotEmpty()) {
                    val segments = relative.split(File.separatorChar, '/', '\\').filter { it.isNotEmpty() }
                    var accumulatedPath = normalizedRoot

                    segments.forEachIndexed { index, segment ->
                        accumulatedPath = if (accumulatedPath.endsWith(File.separator) || accumulatedPath.endsWith("/")) {
                            accumulatedPath + segment
                        } else {
                            accumulatedPath + File.separator + segment
                        }

                        val isCurrent = (index == segments.lastIndex)
                        items.add(
                            BreadcrumbItem(
                                name = segment,
                                path = accumulatedPath,
                                isCurrent = isCurrent
                            )
                        )
                    }
                }
            }

            return items
        }
    }
}
