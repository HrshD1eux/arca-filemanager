package com.Hrshd1eux.filemanager.feature.network.browser

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Hrshd1eux.filemanager.core.display.model.SortOrder
import com.Hrshd1eux.filemanager.core.display.model.ViewMode
import com.Hrshd1eux.filemanager.core.display.preferences.DisplayPreferencesRepository
import com.Hrshd1eux.filemanager.core.display.util.NodeSorter
import com.Hrshd1eux.filemanager.core.network.bridge.NetworkStorageBridge
import com.Hrshd1eux.filemanager.core.network.model.NetworkConnectionState
import com.Hrshd1eux.filemanager.core.network.model.NetworkServerConfig
import com.Hrshd1eux.filemanager.core.network.provider.NetworkProvider
import com.Hrshd1eux.filemanager.core.network.registry.NetworkProviderRegistry
import com.Hrshd1eux.filemanager.core.network.repository.NetworkConnectionRepository
import com.Hrshd1eux.filemanager.core.network.repository.NetworkConnectionRepositoryImpl
import com.Hrshd1eux.filemanager.core.operations.clipboard.OperationClipboard
import com.Hrshd1eux.filemanager.core.operations.clipboard.StagedTransfer
import com.Hrshd1eux.filemanager.core.operations.clipboard.StagedTransferType
import com.Hrshd1eux.filemanager.core.operations.engine.FileOperationEngine
import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import com.Hrshd1eux.filemanager.core.operations.queue.OperationManager
import com.Hrshd1eux.filemanager.core.operations.queue.OperationType
import com.Hrshd1eux.filemanager.core.operations.queue.QueuedOperation
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import com.Hrshd1eux.filemanager.feature.browser.model.BreadcrumbItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ViewModel driving the Remote Network File Browser.
 * Interacts with remote servers via NetworkProvider and NetworkStorageBridge.
 * Provides identical state and functionality as the local FileBrowserViewModel:
 * directory listing, breadcrumbs, view modes, sorting, selection, CRUD, and downloads.
 * Powered by the universal FileOperationEngine for cross-boundary transfers (Phone ↔ NAS ↔ Server).
 */
class NetworkFileBrowserViewModel(
    private val connectionId: String,
    private val repository: NetworkConnectionRepository,
    private val displayPreferencesRepository: DisplayPreferencesRepository = DisplayPreferencesRepository(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val operationEngine: FileOperationEngine = FileOperationEngine(
        ioDispatcher = ioDispatcher,
        remoteOperator = com.Hrshd1eux.filemanager.core.network.bridge.NetworkStorageOperator()
    ),
    private val operationManager: OperationManager = OperationManager(ioDispatcher)
) : ViewModel() {

    private val _uiState = MutableStateFlow<NetworkBrowserUiState>(NetworkBrowserUiState.Loading("Initializing connection..."))
    val uiState: StateFlow<NetworkBrowserUiState> = _uiState.asStateFlow()

    private val _selectedNodeIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedNodeIds: StateFlow<Set<String>> = _selectedNodeIds.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    val sortOrder: StateFlow<SortOrder> = displayPreferencesRepository.sortOrder
    val viewMode: StateFlow<ViewMode> = displayPreferencesRepository.viewMode
    val showHiddenFiles: StateFlow<Boolean> = displayPreferencesRepository.showHiddenFiles

    val stagedTransfer: StateFlow<StagedTransfer?> = OperationClipboard.stagedTransfer

    val activeOperation: StateFlow<QueuedOperation?> = operationManager.activeOperation
    val waitingQueue: StateFlow<List<QueuedOperation>> = operationManager.waitingQueue
    val completedHistory: StateFlow<List<QueuedOperation>> = operationManager.completedHistory

    val activeProgress: StateFlow<OperationProgress?> = operationManager.activeOperation
        .map { it?.progress }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var activeProvider: NetworkProvider? = null
    private var activeBridge: NetworkStorageBridge? = null
    private var serverConfig: NetworkServerConfig? = null
    private var rawDirectoryItems: List<StorageNode> = emptyList()
    private var currentRemotePath: String = "/"

    init {
        initialize()
        observeOperations()
    }

    private fun observeOperations() {
        viewModelScope.launch {
            operationManager.operationCompletedEvents.collect {
                if (_uiState.value is NetworkBrowserUiState.Success) {
                    refresh()
                }
            }
        }
    }

    fun initialize(initialPath: String? = null) {
        viewModelScope.launch {
            _uiState.value = NetworkBrowserUiState.Loading("Locating server configuration...")
            try {
                val savedConfig = repository.getConnection(connectionId)
                if (savedConfig == null) {
                    _uiState.value = NetworkBrowserUiState.Error(
                        serverName = "Server",
                        message = "Your local files are still available.",
                        detail = "Server configuration not found.",
                        canRetry = false
                    )
                    return@launch
                }

                _uiState.value = NetworkBrowserUiState.Loading("Connecting to ${savedConfig.name} (${savedConfig.host})...")
                val fullConfig = repository.populateWithSecrets(savedConfig)
                serverConfig = fullConfig

                val provider = NetworkProviderRegistry.get(fullConfig.id) ?: NetworkProviderRegistry.createProvider(fullConfig, ioDispatcher).also {
                    NetworkProviderRegistry.register(it)
                }
                activeProvider = provider
                activeBridge = NetworkStorageBridge(provider)

                val connectResult = provider.connect()
                if (connectResult.isFailure) {
                    val err = connectResult.exceptionOrNull()?.localizedMessage ?: "Connection dropped or refused"
                    _uiState.value = NetworkBrowserUiState.Error(
                        serverName = fullConfig.name,
                        message = "Your local files are still available.",
                        detail = err,
                        canRetry = true
                    )
                    return@launch
                }

                val startPath = initialPath ?: fullConfig.basePath.ifEmpty { "/" }
                loadDirectory(startPath)
            } catch (e: Exception) {
                _uiState.value = NetworkBrowserUiState.Error(
                    serverName = serverConfig?.name,
                    message = "Your local files are still available.",
                    detail = "Connection error: ${e.localizedMessage}",
                    canRetry = true
                )
            }
        }
    }

    fun loadDirectory(path: String) {
        val bridge = activeBridge
        val provider = activeProvider
        val config = serverConfig

        if (bridge == null || provider == null || config == null) {
            initialize(path)
            return
        }

        clearSelection()
        currentRemotePath = normalizePath(path)

        viewModelScope.launch {
            _uiState.value = NetworkBrowserUiState.Loading("Loading remote directory: $currentRemotePath")
            withContext(ioDispatcher) {
                val listResult = bridge.listChildren(currentRemotePath)
                listResult.onSuccess { items ->
                    rawDirectoryItems = items
                    updateSuccessState()
                }.onFailure { ex ->
                    _uiState.value = NetworkBrowserUiState.Error(
                        message = "Failed to list directory '$currentRemotePath': ${ex.localizedMessage}",
                        canRetry = true
                    )
                }
            }
        }
    }

    private fun updateSuccessState() {
        val config = serverConfig ?: return
        val provider = activeProvider ?: return

        val filteredItems = if (showHiddenFiles.value) {
            rawDirectoryItems
        } else {
            rawDirectoryItems.filterNot { it.name.startsWith(".") }
        }

        val searchedItems = if (_searchQuery.value.isBlank()) {
            filteredItems
        } else {
            val query = _searchQuery.value.trim().lowercase()
            filteredItems.filter { it.name.lowercase().contains(query) }
        }

        val sortedItems = NodeSorter.sort(searchedItems, sortOrder.value)
        val breadcrumbs = generateBreadcrumbs(currentRemotePath, config)
        val title = if (currentRemotePath == "/" || currentRemotePath == normalizePath(config.basePath)) {
            config.name
        } else {
            currentRemotePath.substringAfterLast('/').ifEmpty { config.name }
        }

        _uiState.value = NetworkBrowserUiState.Success(
            currentPath = currentRemotePath,
            currentTitle = title,
            breadcrumbs = breadcrumbs,
            items = sortedItems,
            totalItemCount = sortedItems.size,
            serverConfig = config,
            isEncryptedTransport = provider.capabilities.isEncryptedTransport,
            searchQuery = _searchQuery.value
        )
    }

    fun getCurrentFolderNode(): StorageNode {
        val config = serverConfig
        val provider = activeProvider
        val scheme = provider?.protocol?.scheme ?: "sftp"
        val configId = config?.id ?: connectionId
        val title = (uiState.value as? NetworkBrowserUiState.Success)?.currentTitle ?: "Remote"
        return StorageNode(
            id = "$scheme://$configId$currentRemotePath",
            name = title,
            path = currentRemotePath,
            uriString = "$scheme://$configId$currentRemotePath",
            isDirectory = true
        )
    }

    fun navigateInto(node: StorageNode) {
        if (!node.isDirectory) return
        loadDirectory(node.path)
    }

    fun navigateUp(): Boolean {
        val config = serverConfig ?: return false
        val rootPath = normalizePath(config.basePath)

        if (currentRemotePath == "/" || currentRemotePath == rootPath) {
            return false
        }

        val parent = currentRemotePath.substringBeforeLast('/').ifEmpty { "/" }
        loadDirectory(parent)
        return true
    }

    fun refresh() {
        loadDirectory(currentRemotePath)
    }

    fun toggleSearch() {
        _isSearching.value = !_isSearching.value
        if (!_isSearching.value) {
            _searchQuery.value = ""
            updateSuccessState()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        updateSuccessState()
    }

    fun setSortOrder(newOrder: SortOrder) {
        displayPreferencesRepository.updateSortOrder(newOrder)
        updateSuccessState()
    }

    fun setViewMode(newMode: ViewMode) {
        displayPreferencesRepository.updateViewMode(newMode)
    }

    fun cycleViewMode() {
        val nextMode = when (displayPreferencesRepository.viewMode.value) {
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

    fun selectAll() {
        val currentItems = (uiState.value as? NetworkBrowserUiState.Success)?.items ?: return
        val allIds = currentItems.map { it.id }.toSet()
        _selectedNodeIds.value = if (_selectedNodeIds.value == allIds) emptySet() else allIds
    }

    fun clearSelection() {
        _selectedNodeIds.value = emptySet()
    }

    // --- Universal Staged Transfer Actions ---

    fun stageNodesForMove(nodes: List<StorageNode>) {
        if (nodes.isEmpty()) return
        clearSelection()
        val locationName = serverConfig?.name ?: "Network"
        OperationClipboard.stage(nodes, StagedTransferType.MOVE, locationName)
        viewModelScope.launch {
            val countText = if (nodes.size == 1) "1 item" else "${nodes.size} items"
            _userMessage.emit("Staged $countText to move from $locationName. Navigate to destination folder and tap 'Move here'.")
        }
    }

    fun stageNodesForCopy(nodes: List<StorageNode>) {
        if (nodes.isEmpty()) return
        clearSelection()
        val locationName = serverConfig?.name ?: "Network"
        OperationClipboard.stage(nodes, StagedTransferType.COPY, locationName)
        viewModelScope.launch {
            val countText = if (nodes.size == 1) "1 item" else "${nodes.size} items"
            _userMessage.emit("Staged $countText to copy from $locationName. Navigate to destination folder and tap 'Copy here'.")
        }
    }

    fun clearStagedTransfer() {
        OperationClipboard.clear()
    }

    fun commitStagedTransfer(destination: StorageNode = getCurrentFolderNode()) {
        val transfer = OperationClipboard.stagedTransfer.value ?: return
        OperationClipboard.clear()
        if (transfer.type == StagedTransferType.MOVE) {
            startMove(transfer.sources, destination)
        } else {
            startCopy(transfer.sources, destination)
        }
    }

    fun startCopy(
        sources: List<StorageNode>,
        destination: StorageNode,
        conflictResolution: ConflictResolution = ConflictResolution.RENAME_AUTO
    ) {
        clearSelection()
        val title = if (sources.size == 1) "Copying ${sources.first().name}" else "Copying ${sources.size} items"
        operationManager.enqueue(title = title, type = OperationType.COPY) { onProgress ->
            val result = operationEngine.copy(
                sources = sources,
                destinationDir = destination,
                conflictResolution = conflictResolution,
                onProgress = onProgress
            )
            val opResult = result.getOrThrow()
            _userMessage.emit("Copied ${opResult.filesProcessed} items (${FileFormatter.formatFileSize(opResult.bytesProcessed)})")
            refresh()
        }
    }

    fun startMove(
        sources: List<StorageNode>,
        destination: StorageNode,
        conflictResolution: ConflictResolution = ConflictResolution.RENAME_AUTO
    ) {
        clearSelection()
        val title = if (sources.size == 1) "Moving ${sources.first().name}" else "Moving ${sources.size} items"
        operationManager.enqueue(title = title, type = OperationType.MOVE) { onProgress ->
            val result = operationEngine.move(
                sources = sources,
                destinationDir = destination,
                conflictResolution = conflictResolution,
                onProgress = onProgress
            )
            val opResult = result.getOrThrow()
            _userMessage.emit("Moved ${opResult.filesProcessed} items (${FileFormatter.formatFileSize(opResult.bytesProcessed)})")
            refresh()
        }
    }

    fun createDirectory(name: String) {
        val provider = activeProvider ?: return
        clearSelection()
        viewModelScope.launch {
            val targetPath = if (currentRemotePath.endsWith("/")) "$currentRemotePath$name" else "$currentRemotePath/$name"
            val result = provider.createDirectory(targetPath)
            result.onSuccess {
                _userMessage.emit("Created remote folder '$name'")
                refresh()
            }.onFailure { ex ->
                _userMessage.emit("Failed to create folder: ${ex.localizedMessage}")
            }
        }
    }

    fun rename(node: StorageNode, newName: String) {
        val provider = activeProvider ?: return
        clearSelection()
        viewModelScope.launch {
            val result = provider.rename(node.path, newName)
            result.onSuccess {
                _userMessage.emit("Renamed to '$newName'")
                refresh()
            }.onFailure { ex ->
                _userMessage.emit("Failed to rename: ${ex.localizedMessage}")
            }
        }
    }

    fun delete(nodes: List<StorageNode>) {
        val provider = activeProvider ?: return
        clearSelection()
        val title = if (nodes.size == 1) "Deleting ${nodes.first().name}" else "Deleting ${nodes.size} items"
        operationManager.enqueue(title = title, type = OperationType.DELETE) { onProgress ->
            val result = operationEngine.delete(nodes, onProgress)
            val opResult = result.getOrThrow()
            _userMessage.emit("Deleted ${opResult.filesProcessed} items.")
            refresh()
        }
    }

    fun downloadFile(node: StorageNode, localDir: File) {
        downloadSelected(listOf(node), localDir)
    }

    fun downloadSelected(nodes: List<StorageNode>, localDir: File) {
        val destNode = StorageNode(
            id = localDir.absolutePath,
            name = localDir.name,
            path = localDir.absolutePath,
            isDirectory = true
        )
        startCopy(sources = nodes, destination = destNode)
    }

    fun cancelActiveOperation() {
        operationManager.cancelActive()
    }

    fun disconnect() {
        viewModelScope.launch {
            activeProvider?.disconnect()
            activeProvider = null
            activeBridge = null
            _uiState.value = NetworkBrowserUiState.Error("Disconnected from server", canRetry = true)
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            activeProvider?.disconnect()
        }
    }

    private fun normalizePath(raw: String): String {
        val trimmed = raw.trim()
        val withLeading = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        return if (withLeading.length > 1 && withLeading.endsWith("/")) withLeading.dropLast(1) else withLeading
    }

    private fun generateBreadcrumbs(currentPath: String, config: NetworkServerConfig): List<BreadcrumbItem> {
        val rootPath = normalizePath(config.basePath)
        val rootDisplayName = config.name

        if (currentPath == "/" || currentPath == rootPath) {
            return listOf(BreadcrumbItem(name = rootDisplayName, path = currentPath, isCurrent = true))
        }

        val breadcrumbs = mutableListOf<BreadcrumbItem>()
        var pathCursor = currentPath

        while (pathCursor.isNotEmpty() && pathCursor != "/") {
            val name = pathCursor.substringAfterLast('/')
            val isCurrent = pathCursor == currentPath
            breadcrumbs.add(0, BreadcrumbItem(name = name, path = pathCursor, isCurrent = isCurrent))
            pathCursor = pathCursor.substringBeforeLast('/')
        }

        breadcrumbs.add(0, BreadcrumbItem(name = rootDisplayName, path = rootPath, isCurrent = false))
        return breadcrumbs
    }
}
