package com.Hrshd1eux.filemanager.feature.browser

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Hrshd1eux.filemanager.core.archive.engine.ExtendedArchiveEngine
import com.Hrshd1eux.filemanager.core.archive.engine.IsoEngine
import com.Hrshd1eux.filemanager.core.archive.engine.RarEngine
import com.Hrshd1eux.filemanager.core.archive.engine.SevenZEngine
import com.Hrshd1eux.filemanager.core.dragdrop.DragDropManager
import com.Hrshd1eux.filemanager.core.dragdrop.DragDropPayload
import com.Hrshd1eux.filemanager.core.archive.engine.TarEngine
import com.Hrshd1eux.filemanager.core.archive.engine.ZipEngine
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveFormatOption
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveIntegrityReport
import com.Hrshd1eux.filemanager.core.archive.model.CompressionMethodOption
import com.Hrshd1eux.filemanager.core.archive.model.CompressionProfile
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import com.Hrshd1eux.filemanager.core.display.model.SortOrder
import com.Hrshd1eux.filemanager.core.display.model.ViewMode
import com.Hrshd1eux.filemanager.core.display.preferences.DisplayPreferencesRepository
import com.Hrshd1eux.filemanager.core.display.util.NodeSorter
import com.Hrshd1eux.filemanager.core.favorites.model.FavoriteItem
import com.Hrshd1eux.filemanager.core.favorites.repository.FavoritesRepository
import com.Hrshd1eux.filemanager.core.operations.engine.FileOperationEngine
import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import com.Hrshd1eux.filemanager.core.operations.queue.OperationManager
import com.Hrshd1eux.filemanager.core.operations.queue.OperationStatus
import com.Hrshd1eux.filemanager.core.operations.queue.OperationType
import com.Hrshd1eux.filemanager.core.operations.queue.QueuedOperation
import com.Hrshd1eux.filemanager.core.recents.repository.RecentsRepository
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.preferences.LastDestinationRepository
import com.Hrshd1eux.filemanager.core.storage.provider.LocalFileSystemProvider
import com.Hrshd1eux.filemanager.core.storage.provider.StorageProvider
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import com.Hrshd1eux.filemanager.core.storage.util.FileTypeResolver
import com.Hrshd1eux.filemanager.core.storage.util.SmartDestinationResolver
import com.Hrshd1eux.filemanager.feature.browser.model.BreadcrumbItem
import com.Hrshd1eux.filemanager.feature.browser.model.BrowserTab
import com.Hrshd1eux.filemanager.feature.browser.model.TabType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import java.io.File

/**
 * ViewModel managing directory traversal, breadcrumb hierarchy, and browser state.
 */
class FileBrowserViewModel(
    val storageProvider: StorageProvider = LocalFileSystemProvider(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val operationEngine: FileOperationEngine = FileOperationEngine(ioDispatcher),
    private val zipEngine: ZipEngine = ZipEngine(ioDispatcher),
    private val sevenZEngine: SevenZEngine = SevenZEngine(ioDispatcher),
    private val tarEngine: TarEngine = TarEngine(ioDispatcher),
    private val rarEngine: RarEngine = RarEngine(ioDispatcher),
    private val isoEngine: IsoEngine = IsoEngine(ioDispatcher),
    private val extendedArchiveEngine: ExtendedArchiveEngine = ExtendedArchiveEngine(ioDispatcher),
    private val operationManager: OperationManager = OperationManager(ioDispatcher),
    private val displayPreferencesRepository: DisplayPreferencesRepository = DisplayPreferencesRepository(),
    val favoritesRepository: FavoritesRepository = FavoritesRepository(),
    val recentsRepository: RecentsRepository = RecentsRepository(),
    val lastDestinationRepository: LastDestinationRepository = LastDestinationRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<BrowserUiState>(BrowserUiState.Loading)
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    val activeOperation: StateFlow<QueuedOperation?> = operationManager.activeOperation
    val waitingQueue: StateFlow<List<QueuedOperation>> = operationManager.waitingQueue
    val completedHistory: StateFlow<List<QueuedOperation>> = operationManager.completedHistory

    val activeProgress: StateFlow<OperationProgress?> = operationManager.activeOperation
        .map { it?.progress }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _testReport = MutableStateFlow<ArchiveIntegrityReport?>(null)
    val testReport: StateFlow<ArchiveIntegrityReport?> = _testReport.asStateFlow()

    private val _isTestingArchive = MutableStateFlow(false)
    val isTestingArchive: StateFlow<Boolean> = _isTestingArchive.asStateFlow()

    private val _testProgress = MutableStateFlow<OperationProgress?>(null)
    val testProgress: StateFlow<OperationProgress?> = _testProgress.asStateFlow()

    private val _testingArchiveName = MutableStateFlow("")
    val testingArchiveName: StateFlow<String> = _testingArchiveName.asStateFlow()

    private var archiveTestJob: Job? = null

    private val _selectedNodeIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedNodeIds: StateFlow<Set<String>> = _selectedNodeIds.asStateFlow()

    private val _stagedNodesToMove = MutableStateFlow<List<StorageNode>?>(null)
    val stagedNodesToMove: StateFlow<List<StorageNode>?> = _stagedNodesToMove.asStateFlow()

    val sortOrder: StateFlow<SortOrder> = displayPreferencesRepository.sortOrder
    val viewMode: StateFlow<ViewMode> = displayPreferencesRepository.viewMode
    val showHiddenFiles: StateFlow<Boolean> = displayPreferencesRepository.showHiddenFiles
    val favorites: StateFlow<List<FavoriteItem>> = favoritesRepository.favorites

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    private val rootNode: StorageNode = storageProvider.getRootNode()

    init {
        viewModelScope.launch {
            operationManager.operationCompletedEvents.collect { completedOp ->
                val current = _uiState.value
                if (current is BrowserUiState.Success) {
                    refresh(hasPermission = true)
                }
                when (completedOp.status) {
                    OperationStatus.CANCELLED -> {
                        _userMessage.emit("${completedOp.title} cancelled.")
                    }
                    OperationStatus.FAILED -> {
                        _userMessage.emit("${completedOp.title} failed: ${completedOp.errorMessage}")
                    }
                    else -> {}
                }
            }
        }
    }

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
        val nextMode = when (displayPreferencesRepository.viewMode.value) {
            ViewMode.LIST -> ViewMode.COMPACT
            ViewMode.COMPACT -> ViewMode.GRID
            ViewMode.GRID -> ViewMode.LIST
        }
        setViewMode(nextMode)
    }

    val isDualPaneMode: StateFlow<Boolean> = displayPreferencesRepository.isDualPaneMode

    fun toggleDualPaneMode() {
        val current = displayPreferencesRepository.isDualPaneMode.value
        displayPreferencesRepository.updateDualPaneMode(!current)
    }

    val isTabsMode: StateFlow<Boolean> = displayPreferencesRepository.isTabsMode

    private val _tabs = MutableStateFlow<List<BrowserTab>>(emptyList())
    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String>("")
    val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

    fun initDefaultTabs(context: Context? = null) {
        if (_tabs.value.isNotEmpty()) return
        val destinations = SmartDestinationResolver.getStorageDestinations(context)
        val defaultTabs = destinations.map { item ->
            val type = when {
                item.name.equals("Internal", ignoreCase = true) -> TabType.INTERNAL
                item.name.equals("SD Card", ignoreCase = true) -> TabType.SD_CARD
                item.name.equals("USB", ignoreCase = true) -> TabType.USB
                else -> TabType.CUSTOM
            }
            BrowserTab(
                id = "tab_${item.name.lowercase().replace(" ", "_")}",
                title = item.name,
                path = item.path,
                type = type,
                isAvailable = item.isAvailable,
                isClosable = type != TabType.INTERNAL
            )
        }
        _tabs.value = defaultTabs
        val currentPath = (_uiState.value as? BrowserUiState.Success)?.currentPath ?: rootNode.path
        val matchingTab = defaultTabs.find { it.path == currentPath } ?: defaultTabs.firstOrNull()
        if (matchingTab != null) {
            _activeTabId.value = matchingTab.id
        }
    }

    fun selectTab(tabId: String, hasPermission: Boolean) {
        val targetTab = _tabs.value.find { it.id == tabId } ?: return
        if (!targetTab.isAvailable) {
            viewModelScope.launch {
                _userMessage.emit("${targetTab.title} is not mounted or not connected")
            }
            return
        }
        _activeTabId.value = tabId
        loadDirectory(targetTab.path, hasPermission)
    }

    fun addTab(path: String, title: String = File(path).name.ifEmpty { "Folder" }, type: TabType = TabType.CUSTOM, hasPermission: Boolean) {
        val newTab = BrowserTab(
            title = title,
            path = path,
            type = type,
            isAvailable = true,
            isClosable = true
        )
        _tabs.value = _tabs.value + newTab
        _activeTabId.value = newTab.id
        loadDirectory(path, hasPermission)
    }

    fun closeTab(tabId: String, hasPermission: Boolean) {
        val currentTabs = _tabs.value
        if (currentTabs.size <= 1) return
        val tabToClose = currentTabs.find { it.id == tabId } ?: return
        if (!tabToClose.isClosable) return

        val remainingTabs = currentTabs.filter { it.id != tabId }
        _tabs.value = remainingTabs

        if (_activeTabId.value == tabId) {
            val nextTab = remainingTabs.firstOrNull { it.isAvailable } ?: remainingTabs.first()
            _activeTabId.value = nextTab.id
            loadDirectory(nextTab.path, hasPermission)
        }
    }

    fun toggleTabsMode(context: Context? = null) {
        val current = displayPreferencesRepository.isTabsMode.value
        val next = !current
        displayPreferencesRepository.updateTabsMode(next)
        if (next && _tabs.value.isEmpty()) {
            initDefaultTabs(context)
        }
    }

    fun toggleSelection(nodeId: String) {
        val current = _selectedNodeIds.value
        _selectedNodeIds.value = if (current.contains(nodeId)) {
            current - nodeId
        } else {
            current + nodeId
        }
    }

    fun selectAll(items: List<StorageNode>? = null) {
        val targetItems = items ?: (_uiState.value as? BrowserUiState.Success)?.items ?: return
        val allIds = targetItems.map { it.id }.toSet()
        _selectedNodeIds.value = if (_selectedNodeIds.value == allIds) {
            emptySet()
        } else {
            allIds
        }
    }

    fun clearSelection() {
        _selectedNodeIds.value = emptySet()
    }

    fun navigateToBreadcrumb(breadcrumb: BreadcrumbItem, hasPermission: Boolean) {
        loadDirectory(breadcrumb.path, hasPermission)
    }

    fun initialize(initialPath: String? = null, hasPermission: Boolean, context: Context? = null) {
        initBrowser(hasPermission, initialPath, context)
    }

    fun initBrowser(hasPermission: Boolean, initialPath: String? = null, context: Context? = null) {
        if (displayPreferencesRepository.isTabsMode.value) {
            initDefaultTabs(context)
        }
        val targetPath = initialPath ?: rootNode.path
        loadDirectory(targetPath, hasPermission)
    }

    fun startCopy(
        sources: List<StorageNode>,
        destination: StorageNode,
        conflictResolution: ConflictResolution = ConflictResolution.RENAME_AUTO,
        verifyIntegrity: Boolean = false,
        hasPermission: Boolean
    ) {
        if (!hasPermission) {
            _uiState.value = BrowserUiState.PermissionRequired()
            return
        }

        clearSelection()
        val title = if (sources.size == 1) "Copying ${sources.first().name}" else "Copying ${sources.size} items"
        operationManager.enqueue(title = title, type = OperationType.COPY) { onProgress ->
            val result = operationEngine.copy(
                sources = sources,
                destinationDir = destination,
                conflictResolution = conflictResolution,
                verifyIntegrity = verifyIntegrity,
                onProgress = onProgress
            )
            val opResult = result.getOrThrow()
            val verifiedSuffix = if (verifyIntegrity) " (Verified ✓)" else ""
            _userMessage.emit("Copied ${opResult.filesProcessed} items (${FileFormatter.formatFileSize(opResult.bytesProcessed)})$verifiedSuffix")
        }
    }

    fun stageNodesForMove(nodes: List<StorageNode>) {
        if (nodes.isEmpty()) return
        clearSelection()
        _stagedNodesToMove.value = nodes
        viewModelScope.launch {
            val countText = if (nodes.size == 1) "1 item" else "${nodes.size} items"
            _userMessage.emit("Staged $countText to move. Navigate to destination folder and tap 'Move here'.")
        }
    }

    fun clearStagedMove() {
        _stagedNodesToMove.value = null
    }

    fun commitMoveHere(destination: StorageNode, hasPermission: Boolean) {
        val staged = _stagedNodesToMove.value ?: return
        clearStagedMove()
        startMove(
            sources = staged,
            destination = destination,
            hasPermission = hasPermission
        )
    }

    fun handleDroppedPayload(
        payload: DragDropPayload,
        destinationFolder: StorageNode,
        hasPermission: Boolean,
        context: Context? = null
    ) {
        if (!hasPermission) {
            _uiState.value = BrowserUiState.PermissionRequired()
            return
        }
        if (!destinationFolder.isDirectory) return

        if (!DragDropManager.canDrop(payload, destinationFolder)) {
            viewModelScope.launch {
                _userMessage.emit("Cannot drop into this folder (invalid destination)")
            }
            return
        }

        when (payload) {
            is DragDropPayload.StorageNodes -> {
                val nodesToMove = payload.paths.mapNotNull { path ->
                    val file = File(path)
                    if (file.exists()) {
                        StorageNode(
                            id = file.absolutePath,
                            name = file.name,
                            path = file.absolutePath,
                            size = if (file.isDirectory) 0L else file.length(),
                            lastModified = file.lastModified(),
                            isDirectory = file.isDirectory
                        )
                    } else null
                }
                if (nodesToMove.isNotEmpty()) {
                    startMove(
                        sources = nodesToMove,
                        destination = destinationFolder,
                        hasPermission = hasPermission
                    )
                }
            }
            is DragDropPayload.ArchiveEntry -> {
                val archiveFile = File(payload.archivePath)
                if (!archiveFile.exists()) {
                    viewModelScope.launch {
                        _userMessage.emit("Source archive not found")
                    }
                    return
                }
                val destDir = File(destinationFolder.path)
                val entryName = payload.entryPath.trimEnd('/').substringAfterLast('/')

                operationManager.enqueue(
                    title = "Extracting $entryName to ${destinationFolder.name}",
                    type = OperationType.EXTRACT
                ) { onProgress ->
                    val lowerName = archiveFile.name.lowercase()
                    val result: Result<Int> = when {
                        lowerName.endsWith(".iso") -> {
                            isoEngine.extractEntries(
                                isoFile = archiveFile,
                                entryPaths = setOf(payload.entryPath),
                                destinationDir = destDir,
                                onProgress = onProgress
                            )
                        }
                        lowerName.endsWith(".rar") -> {
                            rarEngine.extractEntries(
                                rarFile = archiveFile,
                                entryPaths = setOf(payload.entryPath),
                                destinationDir = destDir,
                                onProgress = onProgress
                            )
                        }
                        lowerName.endsWith(".7z") || FileTypeResolver.is7z(lowerName) -> {
                            sevenZEngine.extractEntries(
                                sevenZFile = archiveFile,
                                entryPaths = setOf(payload.entryPath),
                                destinationDir = destDir,
                                onProgress = onProgress
                            )
                        }
                        lowerName.endsWith(".arj") || lowerName.endsWith(".cpio") || lowerName.endsWith(".deb") ||
                        lowerName.endsWith(".ar") || lowerName.endsWith(".a") -> {
                            extendedArchiveEngine.extractEntries(
                                archiveFile = archiveFile,
                                entryPaths = setOf(payload.entryPath),
                                destinationDir = destDir,
                                onProgress = onProgress
                            )
                        }
                        lowerName.endsWith(".tar") || lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tgz") ||
                        lowerName.endsWith(".tar.xz") || lowerName.endsWith(".txz") ||
                        lowerName.endsWith(".tar.bz2") || lowerName.endsWith(".tbz2") ||
                        lowerName.endsWith(".tar.z") || lowerName.endsWith(".taz") -> {
                            tarEngine.extractEntries(
                                tarFile = archiveFile,
                                entryPaths = setOf(payload.entryPath),
                                destinationDir = destDir,
                                onProgress = onProgress
                            )
                        }
                        else -> {
                            zipEngine.extractEntries(
                                zipFile = archiveFile,
                                entryPaths = setOf(payload.entryPath),
                                destinationDir = destDir,
                                onProgress = onProgress
                            )
                        }
                    }
                    result.onSuccess {
                        _userMessage.emit("Extracted $entryName to ${destinationFolder.name}")
                        refresh(hasPermission = true)
                    }.onFailure { error ->
                        _userMessage.emit("Failed extracting $entryName: ${error.localizedMessage}")
                    }
                }
            }
            is DragDropPayload.ExternalUris -> {
                if (context == null) return
                viewModelScope.launch(ioDispatcher) {
                    val destDir = File(destinationFolder.path)
                    var copiedCount = 0
                    for (uri in payload.uris) {
                        try {
                            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "dropped_file_${System.currentTimeMillis()}"
                            val destFile = File(destDir, fileName)
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                destFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            copiedCount++
                        } catch (_: Exception) {
                        }
                    }
                    if (copiedCount > 0) {
                        _userMessage.emit("Copied $copiedCount items to ${destinationFolder.name}")
                        refresh(hasPermission = true)
                    }
                }
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
        lastDestinationRepository.recordMoveDestination(destination)

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
        val title = if (targets.size == 1) "Deleting ${targets.first().name}" else "Deleting ${targets.size} items"
        operationManager.enqueue(title = title, type = OperationType.DELETE) { onProgress ->
            val result = operationEngine.delete(
                targets = targets,
                onProgress = onProgress
            )
            val opResult = result.getOrThrow()
            _userMessage.emit("Deleted ${opResult.filesProcessed} items.")
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
        profile: CompressionProfile,
        archiveName: String,
        password: CharArray? = null,
        hasPermission: Boolean
    ) {
        val currentState = _uiState.value as? BrowserUiState.Success ?: return
        val destDir = File(currentState.currentPath)
        val destFile = File(destDir, archiveName)

        clearSelection()
        val splitSizeBytes = profile.customSplitSizeBytes ?: profile.splitSize.sizeBytes
        val opTitle = if (splitSizeBytes != null && splitSizeBytes > 0) {
            "Compressing split archive $archiveName"
        } else {
            "Compressing $archiveName"
        }
        operationManager.enqueue(
            title = opTitle,
            type = OperationType.COMPRESS
        ) { onProgress ->
            try {
                val sourceFiles = sources.map { File(it.path) }
                val lowerName = archiveName.lowercase()

                if (password != null && password.isNotEmpty()) {
                    val isSevenZ = profile.format == ArchiveFormatOption.SEVEN_Z || lowerName.endsWith(".7z")
                    if (!isSevenZ) {
                        _userMessage.emit("Archive encryption (AES-256) is only supported for 7Z format in ARCA.")
                        return@enqueue
                    }
                }
                if (lowerName.endsWith(".iso")) {
                    isoEngine.createIso(sourceFiles, destFile)
                }
                if (lowerName.endsWith(".rar")) {
                    rarEngine.createRar(sourceFiles, destFile)
                }
                if (lowerName.endsWith(".arj")) {
                    extendedArchiveEngine.createArj(sourceFiles, destFile)
                }
                if (lowerName.endsWith(".deb") || lowerName.endsWith(".ar") || lowerName.endsWith(".a")) {
                    extendedArchiveEngine.createAr(sourceFiles, destFile)
                }
                if (lowerName.endsWith(".cab")) {
                    extendedArchiveEngine.rejectCab()
                }
                if (lowerName.endsWith(".lzh") || lowerName.endsWith(".lha")) {
                    extendedArchiveEngine.rejectLzh()
                }
                if (lowerName.endsWith(".zst")) {
                    extendedArchiveEngine.rejectZstd()
                }

                if (splitSizeBytes != null && splitSizeBytes > 0) {
                    if (lowerName.endsWith(".7z") || lowerName.contains(".7z.") || profile.format == ArchiveFormatOption.SEVEN_Z) {
                        val sevenZMethod = when (profile.method) {
                            CompressionMethodOption.COPY -> SevenZMethod.COPY
                            CompressionMethodOption.DEFLATE -> SevenZMethod.DEFLATE
                            CompressionMethodOption.BZIP2 -> SevenZMethod.BZIP2
                            CompressionMethodOption.LZMA -> SevenZMethod.LZMA
                            else -> SevenZMethod.LZMA2
                        }
                        val createdParts = sevenZEngine.createSplit7z(
                            sources = sourceFiles,
                            destinationBase7z = destFile,
                            splitSizeBytes = splitSizeBytes,
                            method = sevenZMethod,
                            compressionLevel = profile.compressionLevel,
                            dictionarySizeMb = profile.dictionarySizeMb,
                            isSolid = profile.isSolid,
                            threads = profile.threads,
                            password = password,
                            onProgress = onProgress
                        ).getOrThrow()
                        val totalVolSize = createdParts.sumOf { it.length() }
                        val encSuffix = if (password != null && password.isNotEmpty()) " (Encrypted 🔒)" else ""
                        _userMessage.emit("Created split archive: ${createdParts.size} volumes (${FileFormatter.formatFileSize(totalVolSize)})$encSuffix")
                        return@enqueue
                    } else {
                        _userMessage.emit("Split archive creation is supported for 7Z format.")
                        return@enqueue
                    }
                }

                val size = when {
                    lowerName.endsWith(".cpio") -> {
                        extendedArchiveEngine.createCpio(
                            sources = sourceFiles,
                            destinationCpio = destFile,
                            onProgress = onProgress
                        ).getOrThrow()
                    }
                    lowerName.endsWith(".7z") || profile.format == ArchiveFormatOption.SEVEN_Z -> {
                        val sevenZMethod = when (profile.method) {
                            CompressionMethodOption.COPY -> SevenZMethod.COPY
                            CompressionMethodOption.DEFLATE -> SevenZMethod.DEFLATE
                            CompressionMethodOption.BZIP2 -> SevenZMethod.BZIP2
                            CompressionMethodOption.LZMA -> SevenZMethod.LZMA
                            else -> SevenZMethod.LZMA2
                        }
                        sevenZEngine.create7z(
                            sources = sourceFiles,
                            destination7z = destFile,
                            method = sevenZMethod,
                            compressionLevel = profile.compressionLevel,
                            dictionarySizeMb = profile.dictionarySizeMb,
                            isSolid = profile.isSolid,
                            threads = profile.threads,
                            password = password,
                            onProgress = onProgress
                        ).getOrThrow()
                    }
                    lowerName.endsWith(".tar") || lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tgz") ||
                    lowerName.endsWith(".tar.xz") || lowerName.endsWith(".txz") ||
                    lowerName.endsWith(".tar.bz2") || lowerName.endsWith(".tbz2") ||
                    lowerName.endsWith(".tar.z") || lowerName.endsWith(".taz") ||
                    profile.format.isTarFamily -> {
                        tarEngine.createTar(
                            sources = sourceFiles,
                            destinationTar = destFile,
                            onProgress = onProgress
                        ).getOrThrow()
                    }
                    else -> {
                        zipEngine.createZip(
                            sources = sourceFiles,
                            destinationZip = destFile,
                            compressionLevel = profile.compressionLevel,
                            onProgress = onProgress
                        ).getOrThrow()
                    }
                }
                val encSuffix = if (password != null && password.isNotEmpty()) " (Encrypted 🔒)" else ""
                _userMessage.emit("Created $archiveName (${FileFormatter.formatFileSize(size)})$encSuffix")
            } finally {
                if (password != null) {
                    java.util.Arrays.fill(password, '\u0000')
                }
            }
        }
    }

    fun compressFiles(
        sources: List<StorageNode>,
        archiveName: String,
        compressionLevel: Int = java.util.zip.Deflater.DEFAULT_COMPRESSION,
        password: CharArray? = null,
        splitSizeBytes: Long? = null,
        hasPermission: Boolean
    ) {
        val format = when {
            archiveName.endsWith(".7z", ignoreCase = true) || archiveName.contains(".7z.", ignoreCase = true) -> ArchiveFormatOption.SEVEN_Z
            archiveName.endsWith(".tar.gz", ignoreCase = true) || archiveName.endsWith(".tgz", ignoreCase = true) -> ArchiveFormatOption.TAR_GZ
            archiveName.endsWith(".tar.xz", ignoreCase = true) || archiveName.endsWith(".txz", ignoreCase = true) -> ArchiveFormatOption.TAR_XZ
            archiveName.endsWith(".tar.bz2", ignoreCase = true) || archiveName.endsWith(".tbz2", ignoreCase = true) -> ArchiveFormatOption.TAR_BZ2
            archiveName.endsWith(".tar", ignoreCase = true) -> ArchiveFormatOption.TAR
            else -> ArchiveFormatOption.ZIP
        }
        val profile = CompressionProfile(
            format = format,
            compressionLevel = compressionLevel,
            customSplitSizeBytes = splitSizeBytes
        )
        compressFiles(sources, profile, archiveName, password, hasPermission)
    }

    fun extractArchive(
        archiveNode: StorageNode,
        destinationDir: File,
        conflictResolution: ConflictResolution = ConflictResolution.RENAME_AUTO,
        password: CharArray? = null,
        verifyIntegrity: Boolean = false,
        hasPermission: Boolean
    ) {
        if (!hasPermission) {
            _uiState.value = BrowserUiState.PermissionRequired()
            return
        }

        clearSelection()
        operationManager.enqueue(
            title = "Extracting ${archiveNode.name}",
            type = OperationType.EXTRACT
        ) { onProgress ->
            try {
                val archiveFile = File(archiveNode.path)
                val lowerName = archiveFile.name.lowercase()
                val count = when {
                    lowerName.endsWith(".iso") -> {
                        isoEngine.extractAll(
                            isoFile = archiveFile,
                            destinationDir = destinationDir,
                            conflictResolution = conflictResolution,
                            verifyIntegrity = verifyIntegrity,
                            onProgress = onProgress
                        ).getOrThrow()
                    }
                    lowerName.endsWith(".rar") -> {
                        rarEngine.extractAll(
                            rarFile = archiveFile,
                            destinationDir = destinationDir,
                            conflictResolution = conflictResolution,
                            password = password,
                            verifyIntegrity = verifyIntegrity,
                            onProgress = onProgress
                        ).getOrThrow()
                    }
                    lowerName.endsWith(".7z") || FileTypeResolver.is7z(lowerName) -> {
                        sevenZEngine.extractAll(
                            sevenZFile = archiveFile,
                            destinationDir = destinationDir,
                            conflictResolution = conflictResolution,
                            password = password,
                            verifyIntegrity = verifyIntegrity,
                            onProgress = onProgress
                        ).getOrThrow()
                    }
                    lowerName.endsWith(".arj") || lowerName.endsWith(".cpio") || lowerName.endsWith(".deb") ||
                    lowerName.endsWith(".ar") || lowerName.endsWith(".a") -> {
                        extendedArchiveEngine.extractAll(
                            archiveFile = archiveFile,
                            destinationDir = destinationDir,
                            conflictResolution = conflictResolution,
                            verifyIntegrity = verifyIntegrity,
                            onProgress = onProgress
                        ).getOrThrow()
                    }
                    lowerName.endsWith(".tar") || lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tgz") ||
                    lowerName.endsWith(".tar.xz") || lowerName.endsWith(".txz") ||
                    lowerName.endsWith(".tar.bz2") || lowerName.endsWith(".tbz2") ||
                    lowerName.endsWith(".tar.z") || lowerName.endsWith(".taz") -> {
                        tarEngine.extractAll(
                            tarFile = archiveFile,
                            destinationDir = destinationDir,
                            conflictResolution = conflictResolution,
                            verifyIntegrity = verifyIntegrity,
                            onProgress = onProgress
                        ).getOrThrow()
                    }
                    else -> {
                        zipEngine.extractAll(
                            zipFile = archiveFile,
                            destinationDir = destinationDir,
                            conflictResolution = conflictResolution,
                            verifyIntegrity = verifyIntegrity,
                            onProgress = onProgress
                        ).getOrThrow()
                    }
                }
                val verifiedSuffix = if (verifyIntegrity) " (Verified ✓)" else ""
                _userMessage.emit("Extracted $count items to ${destinationDir.name}$verifiedSuffix")
            } finally {
                if (password != null) {
                    java.util.Arrays.fill(password, '\u0000')
                }
            }
        }
    }

    fun cancelActiveOperation() {
        operationManager.cancelActive()
    }

    fun cancelOperation(operationId: String) {
        operationManager.cancel(operationId)
    }

    fun cancelAllOperations() {
        operationManager.cancelAll()
    }

    fun clearCompletedOperations() {
        operationManager.clearCompleted()
    }

    fun testArchive(node: StorageNode) {
        val file = File(node.path)
        if (!file.exists() || !file.isFile) return

        _testingArchiveName.value = node.name
        _isTestingArchive.value = true
        _testProgress.value = OperationProgress(totalFiles = 0)
        _testReport.value = null

        archiveTestJob = viewModelScope.launch {
            val lowerName = file.name.lowercase()
            val result = when {
                lowerName.endsWith(".iso") -> {
                    isoEngine.testArchive(file) { _testProgress.value = it }
                }
                lowerName.endsWith(".rar") -> {
                    rarEngine.testArchive(file) { _testProgress.value = it }
                }
                lowerName.endsWith(".7z") || FileTypeResolver.is7z(lowerName) -> {
                    sevenZEngine.testArchive(file) { _testProgress.value = it }
                }
                lowerName.endsWith(".arj") || lowerName.endsWith(".cpio") || lowerName.endsWith(".deb") ||
                lowerName.endsWith(".ar") || lowerName.endsWith(".a") -> {
                    extendedArchiveEngine.testArchive(file) { _testProgress.value = it }
                }
                lowerName.endsWith(".tar") || lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tgz") ||
                lowerName.endsWith(".tar.xz") || lowerName.endsWith(".txz") ||
                lowerName.endsWith(".tar.bz2") || lowerName.endsWith(".tbz2") ||
                lowerName.endsWith(".tar.z") || lowerName.endsWith(".taz") -> {
                    tarEngine.testArchive(file) { _testProgress.value = it }
                }
                else -> {
                    zipEngine.testArchive(file) { _testProgress.value = it }
                }
            }
            _isTestingArchive.value = false
            _testProgress.value = null
            result.onSuccess { report ->
                _testReport.value = report
            }.onFailure { error ->
                _userMessage.emit("Integrity test failed: ${error.localizedMessage}")
            }
        }
    }

    fun cancelArchiveTest() {
        archiveTestJob?.cancel()
        archiveTestJob = null
        _isTestingArchive.value = false
        _testProgress.value = null
    }

    fun dismissArchiveTest() {
        _testReport.value = null
        _isTestingArchive.value = false
        _testProgress.value = null
    }

    fun loadDirectory(path: String, hasPermission: Boolean) {
        if (!hasPermission) {
            _uiState.value = BrowserUiState.PermissionRequired()
            return
        }

        clearSelection()
        viewModelScope.launch {
            _uiState.value = BrowserUiState.Loading

            val targetNode = if (path == rootNode.path) {
                rootNode
            } else {
                StorageNode(
                    id = path,
                    name = File(path).name.ifEmpty { "Internal Storage" },
                    path = path,
                    isDirectory = true
                )
            }

            // Record visited folder to Recents
            recentsRepository.recordAccess(targetNode)

            val rawItems = storageProvider.listChildren(targetNode.id).getOrDefault(emptyList())
            val filteredItems = if (displayPreferencesRepository.showHiddenFiles.value) {
                rawItems
            } else {
                rawItems.filterNot { it.name.startsWith(".") }
            }

            val sortedItems = NodeSorter.sort(filteredItems, displayPreferencesRepository.sortOrder.value)
            val breadcrumbs = generateBreadcrumbs(path)

            _uiState.value = BrowserUiState.Success(
                currentPath = path,
                currentTitle = targetNode.name.ifEmpty { "Internal Storage" },
                breadcrumbs = breadcrumbs,
                items = sortedItems
            )

            // Update active tab path and title if tabs are initialized
            val currentActiveId = _activeTabId.value
            if (currentActiveId.isNotEmpty()) {
                _tabs.value = _tabs.value.map { tab ->
                    if (tab.id == currentActiveId) {
                        tab.copy(
                            path = path,
                            title = if (path == rootNode.path && tab.type == TabType.INTERNAL) "Internal" else targetNode.name.ifEmpty { tab.title }
                        )
                    } else {
                        tab
                    }
                }
            }
        }
    }

    fun navigateInto(node: StorageNode, hasPermission: Boolean) {
        if (!node.isDirectory) return
        loadDirectory(node.path, hasPermission)
    }

    fun navigateUp(hasPermission: Boolean): Boolean {
        val currentState = _uiState.value
        if (currentState is BrowserUiState.Success) {
            val currentFile = File(currentState.currentPath)
            val parentFile = currentFile.parentFile

            if (parentFile != null && parentFile.canRead() && currentState.currentPath != rootNode.path) {
                loadDirectory(parentFile.absolutePath, hasPermission)
                return true
            }
        }
        return false
    }

    fun refresh(hasPermission: Boolean = true) {
        val currentState = _uiState.value
        if (currentState is BrowserUiState.Success) {
            loadDirectory(currentState.currentPath, hasPermission)
        } else {
            initBrowser(hasPermission)
        }
    }

    fun addFilesToArchive(archiveFile: File, sources: List<File>) {
        if (sources.isEmpty()) return
        val lowerName = archiveFile.name.lowercase()
        if (lowerName.endsWith(".7z") || lowerName.endsWith(".tar") || lowerName.contains(".tar.")) {
            viewModelScope.launch {
                _userMessage.emit("Appending entries is supported for ZIP archives. To add to 7z/TAR, create a new archive.")
            }
            return
        }

        operationManager.enqueue(
            title = "Adding to ${archiveFile.name}",
            type = OperationType.COMPRESS
        ) { progressEmitter ->
            val result = zipEngine.addEntries(
                zipFile = archiveFile,
                newSources = sources,
                targetVirtualPath = "",
                onProgress = progressEmitter
            )
            result.getOrThrow()
        }
    }

    fun encryptIntoVault(context: android.content.Context, sources: List<File>, deleteOriginal: Boolean = false) {
        if (sources.isEmpty()) return
        val vaultDir = File(context.filesDir, "vault")
        val vaultManager = com.Hrshd1eux.filemanager.core.vault.VaultManager(vaultDir = vaultDir)

        if (!vaultManager.isConfigured()) {
            viewModelScope.launch {
                _userMessage.emit("Private Vault is not set up yet. Open Private Vault from Home to initialize.")
            }
            return
        }
        if (!vaultManager.isUnlocked()) {
            viewModelScope.launch {
                _userMessage.emit("Private Vault is locked. Unlock Private Vault first to import files.")
            }
            return
        }

        val title = if (sources.size == 1) "Encrypting ${sources.first().name} into Vault" else "Encrypting ${sources.size} items into Vault"

        operationManager.enqueue(
            title = title,
            type = OperationType.COPY
        ) { progressEmitter ->
            val result = vaultManager.importFiles(
                sources = sources,
                targetVirtualPath = "",
                deleteOriginals = deleteOriginal,
                onProgress = { bytesRead ->
                    progressEmitter(
                        OperationProgress(
                            bytesProcessed = bytesRead,
                            totalFiles = sources.size,
                            currentFileName = if (sources.size == 1) sources.first().name else "${sources.size} files"
                        )
                    )
                }
            )
            result.getOrThrow()
        }
    }

    fun encryptIntoVault(context: android.content.Context, source: File, deleteOriginal: Boolean = false) {
        encryptIntoVault(context, listOf(source), deleteOriginal)
    }

    private fun generateBreadcrumbs(currentPath: String): List<BreadcrumbItem> {
        return buildBreadcrumbs(
            currentPath = currentPath,
            rootPath = rootNode.path,
            rootDisplayName = rootNode.name.ifEmpty { "Internal Storage" }
        )
    }

    companion object {
        fun buildBreadcrumbs(
            currentPath: String,
            rootPath: String,
            rootDisplayName: String = "Internal Storage"
        ): List<BreadcrumbItem> {
            if (currentPath == rootPath) {
                return listOf(BreadcrumbItem(name = rootDisplayName, path = rootPath, isCurrent = true))
            }

            val breadcrumbs = mutableListOf<BreadcrumbItem>()
            var current: File? = File(currentPath)

            while (current != null && current.path.startsWith(rootPath)) {
                val isRoot = current.path == rootPath
                val name = if (isRoot) rootDisplayName else current.name
                val isCurrent = current.path == currentPath
                breadcrumbs.add(0, BreadcrumbItem(name = name, path = current.path, isCurrent = isCurrent))
                if (isRoot) break
                current = current.parentFile
            }

            return if (breadcrumbs.isEmpty()) {
                listOf(BreadcrumbItem(name = rootDisplayName, path = rootPath, isCurrent = true))
            } else {
                breadcrumbs
            }
        }
    }
}
